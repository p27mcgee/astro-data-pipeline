package com.mcgeecahill.astro.processor.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcgeecahill.astro.processor.entity.ProcessingJob;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobStatusResponseTest {

    private ProcessingJob sampleJob;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        try {
            now = LocalDateTime.now();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("instrument", "HST");
            metadata.put("filter", "F814W");

            sampleJob =
                    ProcessingJob.builder()
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
                            .completedSteps(
                                    Arrays.asList(
                                            ProcessingJob.ProcessingStep.DOWNLOAD_INPUT,
                                            ProcessingJob.ProcessingStep.VALIDATE_FITS,
                                            ProcessingJob.ProcessingStep.DARK_SUBTRACTION,
                                            ProcessingJob.ProcessingStep.FLAT_CORRECTION,
                                            ProcessingJob.ProcessingStep.UPLOAD_OUTPUT))
                            .metadata(metadata)
                            .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test: " + e.getMessage(), e);
        }
    }

    @Test
    void fromEntityValidProcessingJobShouldCreateCorrectResponse() {
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
    void isTerminalCompletedJobShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    void isTerminalFailedJobShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.FAILED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    void isTerminalCancelledJobShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.CANCELLED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertTrue(isTerminal);
    }

    @Test
    void isTerminalRunningJobShouldReturnFalse() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertFalse(isTerminal);
    }

    @Test
    void isTerminalQueuedJobShouldReturnFalse() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isTerminal = response.isTerminal();

        // Then
        assertFalse(isTerminal);
    }

    @Test
    void isRunningRunningJobShouldReturnTrue() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isRunning = response.isRunning();

        // Then
        assertTrue(isRunning);
    }

    @Test
    void isRunningCompletedJobShouldReturnFalse() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        boolean isRunning = response.isRunning();

        // Then
        assertFalse(isRunning);
    }

    @Test
    void getStatusDescriptionQueuedJobShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Job submitted and waiting to be processed", description);
    }

    @Test
    void getStatusDescriptionRunningJobShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Processing in progress", description);
    }

    @Test
    void getStatusDescriptionCompletedJobShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Processing completed successfully", description);
    }

    @Test
    void getStatusDescriptionFailedJobWithErrorShouldIncludeErrorMessage() {
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
    void getStatusDescriptionFailedJobWithoutErrorShouldReturnGenericMessage() {
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
    void getStatusDescriptionCancelledJobShouldReturnCorrectDescription() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.CANCELLED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        String description = response.getStatusDescription();

        // Then
        assertEquals("Job was cancelled", description);
    }

    @Test
    void getEstimatedTimeRemainingMsRunningJobWithStartTimeShouldReturnEstimate() {
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
    void getEstimatedTimeRemainingMsRunningJobWithoutStartTimeShouldReturnNull() {
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
    void getEstimatedTimeRemainingMsCompletedJobShouldReturnNull() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNull(remaining);
    }

    @Test
    void getEstimatedTimeRemainingMsQueuedJobShouldReturnNull() {
        // Given
        sampleJob.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        JobStatusResponse response = JobStatusResponse.fromEntity(sampleJob);

        // When
        Long remaining = response.getEstimatedTimeRemainingMs();

        // Then
        assertNull(remaining);
    }

    @Test
    void getEstimatedTimeRemainingMsLongRunningJobShouldReturnZero() {
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
    void builderShouldCreateValidResponse() {
        // When
        JobStatusResponse response =
                JobStatusResponse.builder()
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
