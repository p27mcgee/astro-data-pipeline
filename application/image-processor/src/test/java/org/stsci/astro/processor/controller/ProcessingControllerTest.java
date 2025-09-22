package org.stsci.astro.processor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.stsci.astro.processor.dto.JobSubmissionRequest;
import org.stsci.astro.processor.dto.JobStatusResponse;
import org.stsci.astro.processor.dto.ProcessingMetricsResponse;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.exception.FitsProcessingException;
import org.stsci.astro.processor.service.ProcessingJobService;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ProcessingController
 * Focuses on controller logic, validation, and response handling
 */
@ExtendWith(MockitoExtension.class)
class ProcessingControllerTest {

    @Mock
    private ProcessingJobService processingJobService;

    @InjectMocks
    private ProcessingController processingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(processingController).build();
        objectMapper = new ObjectMapper();
    }

    // ========== Job Submission Tests ==========

    @Test
    void submitJob_ValidRequest_ShouldReturn202() throws Exception {
        // Given
        JobSubmissionRequest request = createValidJobRequest();
        JobStatusResponse expectedResponse = createJobStatusResponse("job_123");

        when(processingJobService.submitJob(any(JobSubmissionRequest.class)))
                .thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value("job_123"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(processingJobService, times(1)).submitJob(any(JobSubmissionRequest.class));
    }

    @Test
    void submitJob_ServiceException_ShouldReturn400() throws Exception {
        // Given
        JobSubmissionRequest request = createValidJobRequest();

        when(processingJobService.submitJob(any(JobSubmissionRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(processingJobService, times(1)).submitJob(any(JobSubmissionRequest.class));
    }

    @Test
    void submitJob_InvalidJson_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
                .andExpect(status().isBadRequest());

        verify(processingJobService, never()).submitJob(any());
    }

    // ========== Job Status Tests ==========

    @Test
    void getJobStatus_ExistingJob_ShouldReturnJob() throws Exception {
        // Given
        String jobId = "job_123";
        JobStatusResponse expectedResponse = createJobStatusResponse(jobId);

        when(processingJobService.getJobStatus(jobId))
                .thenReturn(Optional.of(expectedResponse));

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(processingJobService, times(1)).getJobStatus(jobId);
    }

    @Test
    void getJobStatus_NonExistentJob_ShouldReturn404() throws Exception {
        // Given
        String jobId = "nonexistent_job";

        when(processingJobService.getJobStatus(jobId))
                .thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs/{jobId}", jobId))
                .andExpect(status().isNotFound());

        verify(processingJobService, times(1)).getJobStatus(jobId);
    }

    // ========== Job Listing Tests ==========

    @Test
    void listJobs_WithoutFilters_ShouldReturnAllJobs() throws Exception {
        // Given
        List<JobStatusResponse> jobs = Arrays.asList(
                createJobStatusResponse("job_1"),
                createJobStatusResponse("job_2")
        );
        Page<JobStatusResponse> jobPage = new PageImpl<>(jobs);

        when(processingJobService.listJobs(isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(jobPage);

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].jobId").value("job_1"))
                .andExpect(jsonPath("$.content[1].jobId").value("job_2"));

        verify(processingJobService, times(1))
                .listJobs(isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void listJobs_WithFilters_ShouldReturnFilteredJobs() throws Exception {
        // Given
        List<JobStatusResponse> jobs = Arrays.asList(createJobStatusResponse("job_1"));
        Page<JobStatusResponse> jobPage = new PageImpl<>(jobs);

        when(processingJobService.listJobs(eq("user123"), eq(ProcessingJob.ProcessingStatus.RUNNING),
                eq(ProcessingJob.ProcessingType.FULL_CALIBRATION), eq(0), eq(10)))
                .thenReturn(jobPage);

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs")
                .param("userId", "user123")
                .param("status", "RUNNING")
                .param("processingType", "FULL_CALIBRATION")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(1)));

        verify(processingJobService, times(1))
                .listJobs(eq("user123"), eq(ProcessingJob.ProcessingStatus.RUNNING),
                         eq(ProcessingJob.ProcessingType.FULL_CALIBRATION), eq(0), eq(10));
    }

    @Test
    void listJobs_WithPagination_ShouldReturnPagedResults() throws Exception {
        // Given
        List<JobStatusResponse> jobs = Arrays.asList(createJobStatusResponse("job_1"));
        Page<JobStatusResponse> jobPage = new PageImpl<>(jobs);

        when(processingJobService.listJobs(isNull(), isNull(), isNull(), eq(2), eq(5)))
                .thenReturn(jobPage);

        // When & Then
        mockMvc.perform(get("/api/v1/processing/jobs")
                .param("page", "2")
                .param("size", "5"))
                .andExpect(status().isOk());

        verify(processingJobService, times(1))
                .listJobs(isNull(), isNull(), isNull(), eq(2), eq(5));
    }

    // ========== Job Cancellation Tests ==========

    @Test
    void cancelJob_ValidJob_ShouldReturnCancelledJob() throws Exception {
        // Given
        String jobId = "job_123";
        JobStatusResponse cancelledJob = createJobStatusResponse(jobId, ProcessingJob.ProcessingStatus.CANCELLED);

        when(processingJobService.cancelJob(jobId))
                .thenReturn(cancelledJob);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/cancel", jobId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(processingJobService, times(1)).cancelJob(jobId);
    }

    @Test
    void cancelJob_NonExistentJob_ShouldReturn400() throws Exception {
        // Given
        String jobId = "nonexistent_job";

        when(processingJobService.cancelJob(jobId))
                .thenThrow(new FitsProcessingException("Job not found: " + jobId));

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/cancel", jobId))
                .andExpect(status().isBadRequest());

        verify(processingJobService, times(1)).cancelJob(jobId);
    }

    // ========== Job Retry Tests ==========

    @Test
    void retryJob_FailedJob_ShouldReturnQueuedJob() throws Exception {
        // Given
        String jobId = "failed_job";
        JobStatusResponse retriedJob = createJobStatusResponse(jobId, ProcessingJob.ProcessingStatus.QUEUED);

        when(processingJobService.retryJob(jobId))
                .thenReturn(retriedJob);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/retry", jobId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(processingJobService, times(1)).retryJob(jobId);
    }

    @Test
    void retryJob_NonFailedJob_ShouldReturn400() throws Exception {
        // Given
        String jobId = "running_job";

        when(processingJobService.retryJob(jobId))
                .thenThrow(new FitsProcessingException("Can only retry failed jobs"));

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/{jobId}/retry", jobId))
                .andExpect(status().isBadRequest());

        verify(processingJobService, times(1)).retryJob(jobId);
    }

    // ========== Metrics Tests ==========

    @Test
    void getMetrics_ShouldReturnProcessingMetrics() throws Exception {
        // Given
        ProcessingMetricsResponse metrics = ProcessingMetricsResponse.createBasic(
                100L, 85L, 10L, 5000.0, 5, 3);

        when(processingJobService.getProcessingMetrics())
                .thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/processing/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalJobsProcessed").value(100))
                .andExpect(jsonPath("$.totalJobsSuccessful").value(85))
                .andExpect(jsonPath("$.totalJobsFailed").value(10))
                .andExpect(jsonPath("$.activeJobs").value(5))
                .andExpect(jsonPath("$.queuedJobs").value(3))
                .andExpect(jsonPath("$.successRate").value(85.0))
                .andExpect(jsonPath("$.averageProcessingTimeMs").value(5000.0));

        verify(processingJobService, times(1)).getProcessingMetrics();
    }

    // ========== Batch Job Tests ==========

    @Test
    void submitBatchJobs_ValidRequests_ShouldCreateMultipleJobs() throws Exception {
        // Given
        Map<String, JobSubmissionRequest> batchRequest = new HashMap<>();
        batchRequest.put("job1", createValidJobRequest());
        batchRequest.put("job2", createValidJobRequest());

        Map<String, String> expectedJobIds = new HashMap<>();
        expectedJobIds.put("job1", "job_123");
        expectedJobIds.put("job2", "job_456");

        when(processingJobService.submitBatchJobs(any()))
                .thenReturn(expectedJobIds);

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.job1").value("job_123"))
                .andExpect(jsonPath("$.job2").value("job_456"));

        verify(processingJobService, times(1)).submitBatchJobs(any());
    }

    @Test
    void submitBatchJobs_ServiceException_ShouldReturn400() throws Exception {
        // Given
        Map<String, JobSubmissionRequest> batchRequest = new HashMap<>();
        batchRequest.put("job1", createValidJobRequest());

        when(processingJobService.submitBatchJobs(any()))
                .thenThrow(new RuntimeException("Batch submission failed"));

        // When & Then
        mockMvc.perform(post("/api/v1/processing/jobs/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isBadRequest());

        verify(processingJobService, times(1)).submitBatchJobs(any());
    }

    // ========== Job Cleanup Tests ==========

    @Test
    void cleanupJobs_ShouldDeleteOldJobs() throws Exception {
        // Given
        Map<String, Object> cleanupResult = new HashMap<>();
        cleanupResult.put("deletedCount", 5);
        cleanupResult.put("status", "ALL");

        when(processingJobService.cleanupJobs(eq(7), isNull()))
                .thenReturn(cleanupResult);

        // When & Then
        mockMvc.perform(delete("/api/v1/processing/jobs")
                .param("olderThanDays", "7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deletedCount").value(5))
                .andExpect(jsonPath("$.status").value("ALL"));

        verify(processingJobService, times(1)).cleanupJobs(eq(7), isNull());
    }

    @Test
    void cleanupJobs_WithStatusFilter_ShouldDeleteFilteredJobs() throws Exception {
        // Given
        Map<String, Object> cleanupResult = new HashMap<>();
        cleanupResult.put("deletedCount", 3);
        cleanupResult.put("status", "COMPLETED");

        when(processingJobService.cleanupJobs(eq(14), eq(ProcessingJob.ProcessingStatus.COMPLETED)))
                .thenReturn(cleanupResult);

        // When & Then
        mockMvc.perform(delete("/api/v1/processing/jobs")
                .param("olderThanDays", "14")
                .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.deletedCount").value(3))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(processingJobService, times(1))
                .cleanupJobs(eq(14), eq(ProcessingJob.ProcessingStatus.COMPLETED));
    }

    // ========== Health Status Tests ==========

    @Test
    void getHealthStatus_ShouldReturnSystemHealth() throws Exception {
        // Given
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", "HEALTHY");
        healthStatus.put("totalJobs", 100L);
        healthStatus.put("queuedJobs", 5L);
        healthStatus.put("runningJobs", 3L);
        healthStatus.put("failedJobs", 2L);

        when(processingJobService.getHealthStatus())
                .thenReturn(healthStatus);

        // When & Then
        mockMvc.perform(get("/api/v1/processing/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.totalJobs").value(100))
                .andExpect(jsonPath("$.queuedJobs").value(5))
                .andExpect(jsonPath("$.runningJobs").value(3))
                .andExpect(jsonPath("$.failedJobs").value(2));

        verify(processingJobService, times(1)).getHealthStatus();
    }

    // ========== Test Helper Methods ==========

    private JobSubmissionRequest createValidJobRequest() {
        JobSubmissionRequest request = new JobSubmissionRequest();
        request.setProcessingType(ProcessingJob.ProcessingType.FULL_CALIBRATION);
        request.setPriority(5);
        request.setInputBucket("test-bucket");
        request.setInputObjectKey("test-image.fits");
        request.setOutputBucket("output-bucket");
        request.setOutputObjectKey("processed-image.fits");
        request.setMaxRetries(3);
        request.setDescription("Test job submission");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("instrument", "HST");
        parameters.put("filter", "F814W");
        request.setParameters(parameters);

        return request;
    }

    private JobStatusResponse createJobStatusResponse(String jobId) {
        return createJobStatusResponse(jobId, ProcessingJob.ProcessingStatus.QUEUED);
    }

    private JobStatusResponse createJobStatusResponse(String jobId, ProcessingJob.ProcessingStatus status) {
        return JobStatusResponse.builder()
                .jobId(jobId)
                .status(status)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("test-image.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("processed-image.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}