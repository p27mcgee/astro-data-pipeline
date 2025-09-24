package org.stsci.astro.processor.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.stsci.astro.processor.dto.JobSubmissionRequest;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.repository.ProcessingJobRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "spring.profiles.active=${SPRING_PROFILES_ACTIVE:test}"
})
@Transactional
class ProcessingControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // Clean database before each test
        jobRepository.deleteAll();
    }

    @Test
    void submitJob_ValidRequest_ShouldCreateJobAndReturn202() throws Exception {
        // Given
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .inputBucket("test-input-bucket")
                .inputObjectKey("test-file.fits")
                .outputBucket("test-output-bucket")
                .outputObjectKey("processed-file.fits")
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .maxRetries(3)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.processingType").value("FULL_CALIBRATION"))
                .andExpect(jsonPath("$.priority").value(5))
                .andExpect(jsonPath("$.inputBucket").value("test-input-bucket"))
                .andExpect(jsonPath("$.inputObjectKey").value("test-file.fits"))
                .andExpect(jsonPath("$.outputBucket").value("test-output-bucket"))
                .andExpect(jsonPath("$.outputObjectKey").value("processed-file.fits"))
                .andExpect(jsonPath("$.maxRetries").value(3))
                .andExpect(jsonPath("$.retryCount").value(0));
    }

    @Test
    void submitJob_InvalidRequest_ShouldReturn400() throws Exception {
        // Given - Invalid request with missing required fields
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                // Missing inputBucket and inputObjectKey
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitJob_WithNullPriority_ShouldUseDefaultPriority() throws Exception {
        // Given
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .inputBucket("test-input-bucket")
                .inputObjectKey("test-file.fits")
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(null) // Null priority
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.priority").value(5)); // Default priority
    }

    @Test
    void getJobStatus_ExistingJob_ShouldReturnJob() throws Exception {
        // Given - Create a job in database
        ProcessingJob job = createSampleJob("job_123", ProcessingJob.ProcessingStatus.RUNNING);
        jobRepository.save(job);

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs/{jobId}", "job_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value("job_123"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.processingType").value("FULL_CALIBRATION"));
    }

    @Test
    void getJobStatus_NonExistingJob_ShouldReturn404() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs/{jobId}", "non_existing_job"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listJobs_WithoutFilters_ShouldReturnAllJobs() throws Exception {
        // Given - Create multiple jobs
        jobRepository.save(createSampleJob("job_001", ProcessingJob.ProcessingStatus.QUEUED));
        jobRepository.save(createSampleJob("job_002", ProcessingJob.ProcessingStatus.RUNNING));
        jobRepository.save(createSampleJob("job_003", ProcessingJob.ProcessingStatus.COMPLETED));

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.size").value(20)) // Default page size
                .andExpect(jsonPath("$.number").value(0)); // First page
    }

    @Test
    void listJobs_WithStatusFilter_ShouldReturnFilteredJobs() throws Exception {
        // Given - Create jobs with different statuses
        jobRepository.save(createSampleJob("job_queued_1", ProcessingJob.ProcessingStatus.QUEUED));
        jobRepository.save(createSampleJob("job_queued_2", ProcessingJob.ProcessingStatus.QUEUED));
        jobRepository.save(createSampleJob("job_running", ProcessingJob.ProcessingStatus.RUNNING));

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs")
                .param("status", "QUEUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].status").value(everyItem(equalTo("QUEUED"))));
    }

    @Test
    void listJobs_WithPagination_ShouldReturnPagedResults() throws Exception {
        // Given - Create more jobs than page size
        for (int i = 1; i <= 25; i++) {
            jobRepository.save(createSampleJob("job_" + String.format("%03d", i), 
                    ProcessingJob.ProcessingStatus.QUEUED));
        }

        // When & Then - First page
        mockMvc.perform(get("/api/v1/processing/jobs")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        // When & Then - Second page
        mockMvc.perform(get("/api/v1/processing/jobs")
                .param("page", "1")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.number").value(1));

        // When & Then - Last page
        mockMvc.perform(get("/api/v1/processing/jobs")
                .param("page", "2")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.number").value(2));
    }

    @Test
    void cancelJob_ExistingQueuedJob_ShouldCancelSuccessfully() throws Exception {
        // Given
        ProcessingJob job = createSampleJob("job_to_cancel", ProcessingJob.ProcessingStatus.QUEUED);
        jobRepository.save(job);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/cancel", "job_to_cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job_to_cancel"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.errorMessage").value("Job cancelled by user"));
    }

    @Test
    void cancelJob_CompletedJob_ShouldReturn400() throws Exception {
        // Given
        ProcessingJob completedJob = createSampleJob("completed_job", ProcessingJob.ProcessingStatus.COMPLETED);
        jobRepository.save(completedJob);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/cancel", "completed_job"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelJob_NonExistingJob_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/cancel", "non_existing"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryJob_FailedJob_ShouldRetrySuccessfully() throws Exception {
        // Given
        ProcessingJob failedJob = createSampleJob("failed_job", ProcessingJob.ProcessingStatus.FAILED);
        failedJob.setRetryCount(1);
        failedJob.setErrorMessage("Previous processing failed");
        jobRepository.save(failedJob);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/retry", "failed_job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("failed_job"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.retryCount").value(2))
                .andExpect(jsonPath("$.errorMessage").isEmpty());
    }

    @Test
    void retryJob_JobExceededMaxRetries_ShouldReturn400() throws Exception {
        // Given
        ProcessingJob failedJob = createSampleJob("max_retries_job", ProcessingJob.ProcessingStatus.FAILED);
        failedJob.setRetryCount(3); // Equals maxRetries
        failedJob.setMaxRetries(3);
        jobRepository.save(failedJob);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/retry", "max_retries_job"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryJob_NonFailedJob_ShouldReturn400() throws Exception {
        // Given
        ProcessingJob runningJob = createSampleJob("running_job", ProcessingJob.ProcessingStatus.RUNNING);
        jobRepository.save(runningJob);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/retry", "running_job"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMetrics_ShouldReturnProcessingMetrics() throws Exception {
        // Given - Create jobs with various statuses
        jobRepository.save(createCompletedJob("completed_1", 60000L)); // 1 minute
        jobRepository.save(createCompletedJob("completed_2", 120000L)); // 2 minutes
        jobRepository.save(createSampleJob("running_1", ProcessingJob.ProcessingStatus.RUNNING));
        jobRepository.save(createSampleJob("queued_1", ProcessingJob.ProcessingStatus.QUEUED));
        jobRepository.save(createSampleJob("failed_1", ProcessingJob.ProcessingStatus.FAILED));

        // When & Then
        mockMvc.perform(get("/api/v1/processing/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalJobsProcessed").value(5))
                .andExpect(jsonPath("$.totalJobsSuccessful").value(2))
                .andExpect(jsonPath("$.totalJobsFailed").value(1))
                .andExpect(jsonPath("$.activeJobs").value(1))
                .andExpect(jsonPath("$.queuedJobs").value(1))
                .andExpect(jsonPath("$.successRate").value(40.0)) // 2/5 * 100
                .andExpect(jsonPath("$.averageProcessingTimeMs").value(90000.0)) // (60000 + 120000) / 2
                // TODO .andExpect(jsonPath("$.systemStatus").value("HEALTHY"));
                .andExpect(jsonPath("$.systemStatus").value("CRITICAL")); // 40% success rate = critical
    }

    @Test
    void submitBatchJobs_ValidRequests_ShouldCreateMultipleJobs() throws Exception {
        // Given
        Map<String, JobSubmissionRequest> batchRequests = new HashMap<>();
        
        batchRequests.put("batch_job_1", JobSubmissionRequest.builder()
                .inputBucket("batch-bucket")
                .inputObjectKey("file1.fits")
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .build());
                
        batchRequests.put("batch_job_2", JobSubmissionRequest.builder()
                .inputBucket("batch-bucket")
                .inputObjectKey("file2.fits")
                .processingType(ProcessingJob.ProcessingType.DARK_SUBTRACTION_ONLY)
                .build());

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchRequests)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.batch_job_1").isNotEmpty())
                .andExpect(jsonPath("$.batch_job_2").isNotEmpty());
    }

    @Test
    void cleanupJobs_ShouldDeleteOldJobs() throws Exception {
        // Given - Create old completed job
        ProcessingJob oldJob = createCompletedJob("old_job", 60000L);
        oldJob.setCompletedAt(LocalDateTime.now().minusDays(10));
        jobRepository.save(oldJob);

        // Create recent completed job
        ProcessingJob recentJob = createCompletedJob("recent_job", 60000L);
        recentJob.setCompletedAt(LocalDateTime.now().minusHours(1));
        jobRepository.save(recentJob);

        // When & Then
        mockMvc.perform(delete("/api/v1/processing/jobs")
                .param("olderThanDays", "7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deletedCount").value(1))
                .andExpect(jsonPath("$.status").value("ALL"));
    }

    @Test
    void getHealthStatus_ShouldReturnSystemHealth() throws Exception {
        // Given - Create jobs to affect health metrics
        jobRepository.save(createSampleJob("job1", ProcessingJob.ProcessingStatus.QUEUED));
        jobRepository.save(createSampleJob("job2", ProcessingJob.ProcessingStatus.RUNNING));

        // When & Then
        mockMvc.perform(get("/api/v1/processing/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalJobs").value(2))
                .andExpect(jsonPath("$.queuedJobs").value(1))
                .andExpect(jsonPath("$.runningJobs").value(1))
                .andExpect(jsonPath("$.systemLoad").value(2))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void apiResponseHeaders_ShouldIncludeCorrectHeaders() throws Exception {
        // Given
        JobSubmissionRequest request = JobSubmissionRequest.builder()
                .inputBucket("test-bucket")
                .inputObjectKey("test-file.fits")
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Content-Type", containsString("application/json")));
    }

    @Test
    void apiErrorHandling_InvalidJson_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiContentNegotiation_UnsupportedMediaType_ShouldReturn415() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.TEXT_PLAIN)
                .content("some text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // Helper methods
    private ProcessingJob createSampleJob(String jobId, ProcessingJob.ProcessingStatus status) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("instrument", "HST");
        metadata.put("filter", "F814W");

        return ProcessingJob.builder()
                .jobId(jobId)
                .status(status)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey(jobId + ".fits")
                .outputBucket("output-bucket")
                .outputObjectKey(jobId + "_processed.fits")
                .maxRetries(3)
                .retryCount(0)
                .metadata(metadata)
                .completedSteps(new ArrayList<>())
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    private ProcessingJob createCompletedJob(String jobId, Long processingDurationMs) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("instrument", "HST");
        metadata.put("filter", "F814W");

        return ProcessingJob.builder()
                .jobId(jobId)
                .status(ProcessingJob.ProcessingStatus.COMPLETED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey(jobId + ".fits")
                .outputBucket("output-bucket")
                .outputObjectKey(jobId + "_processed.fits")
                .maxRetries(3)
                .retryCount(0)
                .processingDurationMs(processingDurationMs)
                .metadata(metadata)
                .completedSteps(new ArrayList<>())
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .startedAt(LocalDateTime.now().minusDays(1).plusMinutes(5))
                .completedAt(LocalDateTime.now().minusDays(1).plusMinutes(10))
                .build();
    }
}