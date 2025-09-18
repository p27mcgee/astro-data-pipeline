package org.stsci.astro.processor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.stsci.astro.processor.dto.JobSubmissionRequest;
import org.stsci.astro.processor.dto.JobStatusResponse;
import org.stsci.astro.processor.dto.ProcessingMetricsResponse;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.exception.FitsProcessingException;
import org.stsci.astro.processor.repository.ProcessingJobRepository;
import org.stsci.astro.processor.util.MetricsCollector;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    @Mock
    private ProcessingJobRepository jobRepository;

    @Mock
    private FitsProcessingService fitsProcessingService;

    @Mock
    private MetricsCollector metricsCollector;

    @InjectMocks
    private ProcessingJobService processingJobService;

    private JobSubmissionRequest validRequest;
    private ProcessingJob sampleJob;

    @BeforeEach
    void setUp() {
        validRequest = JobSubmissionRequest.builder()
                .inputBucket("test-bucket")
                .inputObjectKey("test-file.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("output-file.fits")
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .maxRetries(3)
                .build();

        sampleJob = ProcessingJob.builder()
                .id(1L)
                .jobId("job_123456789")
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("test-file.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("output-file.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void submitJob_ShouldCreateAndSaveJob() {
        // Given
        when(jobRepository.save(any(ProcessingJob.class))).thenReturn(sampleJob);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED)).thenReturn(5L);

        // When
        JobStatusResponse response = processingJobService.submitJob(validRequest);

        // Then
        assertNotNull(response);
        assertEquals(sampleJob.getJobId(), response.getJobId());
        assertEquals(ProcessingJob.ProcessingStatus.QUEUED, response.getStatus());
        assertEquals(validRequest.getProcessingType(), response.getProcessingType());
        assertEquals(validRequest.getPriority(), response.getPriority());

        verify(jobRepository).save(any(ProcessingJob.class));
        verify(metricsCollector).updateQueueSize(5L);
    }

    @Test
    void submitJob_WithNullPriority_ShouldUseDefaultPriority() {
        // Given
        validRequest.setPriority(null);
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            assertEquals(5, job.getPriority()); // Default priority
            return sampleJob;
        });
        when(jobRepository.countByStatus(any())).thenReturn(0L);

        // When
        JobStatusResponse response = processingJobService.submitJob(validRequest);

        // Then
        assertNotNull(response);
        verify(jobRepository).save(any(ProcessingJob.class));
    }

    @Test
    void getJobStatus_ExistingJob_ShouldReturnJobStatus() {
        // Given
        String jobId = "job_123456789";
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(sampleJob));

        // When
        Optional<JobStatusResponse> response = processingJobService.getJobStatus(jobId);

        // Then
        assertTrue(response.isPresent());
        assertEquals(jobId, response.get().getJobId());
        assertEquals(sampleJob.getStatus(), response.get().getStatus());
    }

    @Test
    void getJobStatus_NonExistingJob_ShouldReturnEmpty() {
        // Given
        String jobId = "non-existing-job";
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.empty());

        // When
        Optional<JobStatusResponse> response = processingJobService.getJobStatus(jobId);

        // Then
        assertFalse(response.isPresent());
    }

    @Test
    void listJobs_ShouldReturnPagedResults() {
        // Given
        List<ProcessingJob> jobs = Arrays.asList(sampleJob);
        Page<ProcessingJob> jobPage = new PageImpl<>(jobs);
        when(jobRepository.findAll(any(PageRequest.class))).thenReturn(jobPage);

        // When
        Page<JobStatusResponse> response = processingJobService.listJobs(null, null, null, 0, 20);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(sampleJob.getJobId(), response.getContent().get(0).getJobId());
    }

    @Test
    void cancelJob_ValidJob_ShouldCancelSuccessfully() {
        // Given
        String jobId = "job_123456789";
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(sampleJob));
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            assertEquals(ProcessingJob.ProcessingStatus.CANCELLED, job.getStatus());
            return job;
        });

        // When
        JobStatusResponse response = processingJobService.cancelJob(jobId);

        // Then
        assertNotNull(response);
        verify(jobRepository).save(any(ProcessingJob.class));
    }

    @Test
    void cancelJob_CompletedJob_ShouldThrowException() {
        // Given
        String jobId = "job_123456789";
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(sampleJob));

        // When & Then
        FitsProcessingException exception = assertThrows(FitsProcessingException.class, 
            () -> processingJobService.cancelJob(jobId));
        assertEquals("Cannot cancel completed job: " + jobId, exception.getMessage());
    }

    @Test
    void cancelJob_NonExistingJob_ShouldThrowException() {
        // Given
        String jobId = "non-existing-job";
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(FitsProcessingException.class, 
            () -> processingJobService.cancelJob(jobId));
    }

    @Test
    void retryJob_FailedJob_ShouldRetrySuccessfully() {
        // Given
        String jobId = "job_123456789";
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.FAILED);
        sampleJob.setRetryCount(1);
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(sampleJob));
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            assertEquals(ProcessingJob.ProcessingStatus.QUEUED, job.getStatus());
            assertEquals(2, job.getRetryCount());
            return job;
        });

        // When
        JobStatusResponse response = processingJobService.retryJob(jobId);

        // Then
        assertNotNull(response);
        verify(jobRepository).save(any(ProcessingJob.class));
    }

    @Test
    void retryJob_ExceededMaxRetries_ShouldThrowException() {
        // Given
        String jobId = "job_123456789";
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.FAILED);
        sampleJob.setRetryCount(3); // Equal to maxRetries
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(sampleJob));

        // When & Then
        FitsProcessingException exception = assertThrows(FitsProcessingException.class, 
            () -> processingJobService.retryJob(jobId));
        assertEquals("Job has exceeded maximum retry attempts", exception.getMessage());
    }

    @Test
    void retryJob_NotFailedJob_ShouldThrowException() {
        // Given
        String jobId = "job_123456789";
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(sampleJob));

        // When & Then
        assertThrows(FitsProcessingException.class, 
            () -> processingJobService.retryJob(jobId));
    }

    @Test
    void getProcessingMetrics_ShouldReturnCorrectMetrics() {
        // Given
        when(jobRepository.count()).thenReturn(100L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.COMPLETED)).thenReturn(80L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.FAILED)).thenReturn(15L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING)).thenReturn(3L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED)).thenReturn(2L);
        
        List<ProcessingJob> completedJobs = Arrays.asList(
            createJobWithProcessingTime(1000L),
            createJobWithProcessingTime(2000L),
            createJobWithProcessingTime(1500L)
        );
        when(jobRepository.findCompletedJobs()).thenReturn(completedJobs);

        // When
        ProcessingMetricsResponse metrics = processingJobService.getProcessingMetrics();

        // Then
        assertNotNull(metrics);
        assertEquals(100L, metrics.getTotalJobsProcessed());
        assertEquals(80L, metrics.getTotalJobsSuccessful());
        assertEquals(15L, metrics.getTotalJobsFailed());
        assertEquals(3, metrics.getActiveJobs());
        assertEquals(2, metrics.getQueuedJobs());
        assertEquals(80.0, metrics.getSuccessRate());
        assertEquals(1500.0, metrics.getAverageProcessingTimeMs()); // Average of 1000, 2000, 1500
    }

    @Test
    void submitBatchJobs_ShouldReturnJobIds() {
        // Given
        Map<String, JobSubmissionRequest> requests = new HashMap<>();
        requests.put("job1", validRequest);
        requests.put("job2", validRequest);

        when(jobRepository.save(any(ProcessingJob.class)))
            .thenReturn(createJobWithId("job_111"))
            .thenReturn(createJobWithId("job_222"));
        when(jobRepository.countByStatus(any())).thenReturn(0L);

        // When
        Map<String, String> result = processingJobService.submitBatchJobs(requests);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("job1"));
        assertTrue(result.containsKey("job2"));
        verify(jobRepository, times(2)).save(any(ProcessingJob.class));
    }

    @Test
    void cleanupJobs_ShouldDeleteOldJobs() {
        // Given
        int daysOld = 7;
        List<ProcessingJob> oldJobs = Arrays.asList(sampleJob);
        when(jobRepository.findCompletedJobsOlderThan(any(LocalDateTime.class))).thenReturn(oldJobs);

        // When
        Map<String, Object> result = processingJobService.cleanupJobs(daysOld, null);

        // Then
        assertNotNull(result);
        assertEquals(1, result.get("deletedCount"));
        assertEquals("ALL", result.get("status"));
        verify(jobRepository).deleteAll(oldJobs);
    }

    @Test
    void getHealthStatus_ShouldReturnSystemHealth() {
        // Given
        when(jobRepository.count()).thenReturn(100L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED)).thenReturn(10L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING)).thenReturn(5L);
        when(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.FAILED)).thenReturn(2L);

        // When
        Map<String, Object> health = processingJobService.getHealthStatus();

        // Then
        assertNotNull(health);
        assertEquals(100L, health.get("totalJobs"));
        assertEquals(10L, health.get("queuedJobs"));
        assertEquals(5L, health.get("runningJobs"));
        assertEquals(2L, health.get("failedJobs"));
        assertEquals(15L, health.get("systemLoad")); // queued + running
        assertEquals("HEALTHY", health.get("status"));
    }

    // Helper methods
    private ProcessingJob createJobWithProcessingTime(Long processingTime) {
        ProcessingJob job = ProcessingJob.builder()
                .id(1L)
                .jobId("job_test")
                .processingDurationMs(processingTime)
                .build();
        return job;
    }

    private ProcessingJob createJobWithId(String jobId) {
        ProcessingJob job = ProcessingJob.builder()
                .id(1L)
                .jobId(jobId)
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .build();
        return job;
    }
}