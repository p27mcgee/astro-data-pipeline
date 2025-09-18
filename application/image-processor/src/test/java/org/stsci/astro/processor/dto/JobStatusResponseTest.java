package org.stsci.astro.processor.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stsci.astro.processor.entity.ProcessingJob;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobStatusResponseTest {

    private ProcessingJob sampleJob;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("instrument", "HST");
        metadata.put("filter", "F814W");

        sampleJob = ProcessingJob.builder()
                .id(1L)
                .jobId("job_123456789")
                .status(ProcessingJob.ProcessingStatus.COMPLETED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("input-bucket")
                .inputObjectKey("input-file.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("output-file.fits")
                .createdAt(now.minusHours(2))
                .updatedAt(now.minusMinutes(30))
                .startedAt(now.minusHours(1))
                .completedAt(now.minusMinutes(30))
                .processingDurationMs(1800000L) // 30 minutes
                .errorMessage(null)
                .stackTrace(null)
                .retryCount(0)
                .maxRetries(3)
                .inputFileSizeBytes(1048576L) // 1 MB
                .outputFileSizeBytes(1024000L) // ~1 MB
                .completedSteps(Arrays.asList(
                    ProcessingJob.ProcessingStep.DOWNLOAD_INPUT,
                    ProcessingJob.ProcessingStep.VALIDATE_FITS,
                    ProcessingJob.ProcessingStep.DARK_SUBTRACTION,
                    ProcessingJob.ProcessingStep.FLAT_CORRECTION,
                    ProcessingJob.ProcessingStep.UPLOAD_OUTPUT
                ))
                .metadata(metadata)
                .build();
    }

    @Test
    void fromEntity_ValidProcessingJob_ShouldCreateCorrectResponse() {
        // When
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // Then
        assertNotNull(response);
        assertEquals(sampleJob.getJobId(), response.getJobId());
        assertEquals(sampleJob.getStatus(), response.getStatus());
        assertEquals(sampleJob.getProcessingType(), response.getProcessingType());
        assertEquals(sampleJob.getPriority(), response.getPriority());
        assertEquals(sampleJob.getInputBucket(), response.getInputBucket());
        assertEquals(sampleJob.getInputObjectKey(), response.getInputObjectKey());
        assertEquals(sampleJob.getOutputBucket(), response.getOutputBucket());
        assertEquals(sampleJob.getOutputObjectKey(), response.getOutputObjectKey());
        assertEquals(sampleJob.getCreatedAt(), response.getCreatedAt());
        assertEquals(sampleJob.getUpdatedAt(), response.getUpdatedAt());
        assertEquals(sampleJob.getStartedAt(), response.getStartedAt());
        assertEquals(sampleJob.getCompletedAt(), response.getCompletedAt());
        assertEquals(sampleJob.getProcessingDurationMs(), response.getProcessingDurationMs());
        assertEquals(sampleJob.getErrorMessage(), response.getErrorMessage());
        assertEquals(sampleJob.getStackTrace(), response.getStackTrace());
        assertEquals(sampleJob.getRetryCount(), response.getRetryCount());
        assertEquals(sampleJob.getMaxRetries(), response.getMaxRetries());
        assertEquals(sampleJob.getInputFileSizeBytes(), response.getInputFileSizeBytes());
        assertEquals(sampleJob.getOutputFileSizeBytes(), response.getOutputFileSizeBytes());
        assertEquals(sampleJob.getCompletedSteps(), response.getCompletedSteps());
        assertEquals(sampleJob.getMetadata(), response.getMetadata());
    }

    @Test
    void isTerminal_CompletedJob_ShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    void isTerminal_FailedJob_ShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.FAILED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    void isTerminal_CancelledJob_ShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.CANCELLED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    void isTerminal_RunningJob_ShouldReturnFalse() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertFalse(isTerminal);
    }

    @Test
    void isTerminal_QueuedJob_ShouldReturnFalse() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertFalse(isTerminal);
    }

    @Test
    void isRunning_RunningJob_ShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isRunning = response.isRunning();

        // Then
        assertTrue(isRunning);
    }

    @Test
    void isRunning_CompletedJob_ShouldReturnFalse() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isRunning = response.isRunning();

        // Then
        assertFalse(isRunning);
    }

    @Test
    void getStatusDescription_QueuedJob_ShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Job submitted and waiting to be processed", description);
    }

    @Test
    void getStatusDescription_RunningJob_ShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Processing in progress", description);
    }

    @Test
    void getStatusDescription_CompletedJob_ShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Processing completed successfully", description);
    }

    @Test
    void getStatusDescription_FailedJobWithError_ShouldIncludeErrorMessage() {
        // Given
        String errorMessage = "FITS file corrupted";
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.FAILED);
        sampleJob.setErrorMessage(errorMessage);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Processing failed: " + errorMessage, description);
    }

    @Test
    void getStatusDescription_FailedJobWithoutError_ShouldReturnGenericMessage() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.FAILED);
        sampleJob.setErrorMessage(null);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Processing failed: Unknown error", description);
    }

    @Test
    void getStatusDescription_CancelledJob_ShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.CANCELLED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Job was cancelled", description);
    }

    @Test
    void getEstimatedTimeRemainingMs_RunningJobWithStartTime_ShouldReturnEstimate() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        sampleJob.setStartedAt(LocalDateTime.now().minusMinutes(2)); // Started 2 minutes ago
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNotNull(remaining);
        // Should estimate based on 5-minute average, so should be around 3 minutes (180000 ms)
        assertTrue(remaining > 100000); // More than 100 seconds
        assertTrue(remaining < 400000); // Less than 400 seconds
    }

    @Test
    void getEstimatedTimeRemainingMs_RunningJobWithoutStartTime_ShouldReturnNull() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        sampleJob.setStartedAt(null);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNull(remaining);
    }

    @Test
    void getEstimatedTimeRemainingMs_CompletedJob_ShouldReturnNull() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNull(remaining);
    }

    @Test
    void getEstimatedTimeRemainingMs_QueuedJob_ShouldReturnNull() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNull(remaining);
    }

    @Test
    void getEstimatedTimeRemainingMs_LongRunningJob_ShouldReturnZero() {
        // Given - Job running for 10 minutes (longer than estimated 5-minute average)
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        sampleJob.setStartedAt(LocalDateTime.now().minusMinutes(10));
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNotNull(remaining);
        assertEquals(0L, remaining);
    }

    @Test
    void builder_ShouldCreateValidResponse() {
        // When
        JobStatusResponse response = JobStatusResponse.builder()
                .jobId("test_job_123")
                .status(ProcessingJob.ProcessingStatus.RUNNING)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(3)
                .inputBucket("test-bucket")
                .inputObjectKey("test-file.fits")
                .build();

        // Then
        assertNotNull(response);
        assertEquals("test_job_123", response.getJobId());
        assertEquals(ProcessingJob.ProcessingStatus.RUNNING, response.getStatus());
        assertEquals(ProcessingJob.ProcessingType.FULL_CALIBRATION, response.getProcessingType());
        assertEquals(3, response.getPriority());
        assertEquals("test-bucket", response.getInputBucket());
        assertEquals("test-file.fits", response.getInputObjectKey());
    }
}