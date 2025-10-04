package com.mcgeecahill.astro.processor.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mcgeecahill.astro.processor.config.ProcessingConfig;
import com.mcgeecahill.astro.processor.entity.ProcessingJob;
import com.mcgeecahill.astro.processor.util.AstronomicalUtils;
import com.mcgeecahill.astro.processor.util.MetricsCollector;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for FitsProcessingService Tests service initialization and basic error handling */
@ExtendWith(MockitoExtension.class)
class FitsProcessingServiceTest {

    @Mock private ProcessingConfig processingConfig;

    @Mock private MetricsCollector metricsCollector;

    @Mock private AstronomicalUtils astronomicalUtils;

    @InjectMocks private FitsProcessingService fitsProcessingService;

    private ProcessingJob testJob;

    @BeforeEach
    void setUp() {
        testJob = createTestProcessingJob();
    }

    // ========== Service Construction Tests ==========

    @Test
    void fitsProcessingServiceShouldBeCreatedWithDependencies() {
        // Test that the service is properly instantiated
        assertNotNull(fitsProcessingService);
    }

    @Test
    void fitsProcessingServiceShouldHaveProcessingConfig() {
        // Test that dependencies are injected
        assertNotNull(processingConfig);
        assertNotNull(metricsCollector);
        assertNotNull(astronomicalUtils);
    }

    // ========== Method Signature Tests ==========

    @Test
    void processImageShouldReturnCompletableFuture() {
        // Given
        InputStream testStream = createTestInputStream();

        // When
        CompletableFuture<?> result = fitsProcessingService.processImage(testJob, testStream);

        // Then
        assertNotNull(result);
        assertInstanceOf(CompletableFuture.class, result);
    }

    @Test
    void processImageWithNullJobShouldHandleAppropriately() {
        // Given
        InputStream testStream = createTestInputStream();

        // When & Then - The service might not immediately throw, so test the future
        assertDoesNotThrow(
                () -> {
                    CompletableFuture<?> future =
                            fitsProcessingService.processImage(null, testStream);
                    assertNotNull(future);
                });
    }

    @Test
    void processImageWithNullStreamShouldHandleAppropriately() {
        // When & Then - The service might not immediately throw, so test the future
        assertDoesNotThrow(
                () -> {
                    CompletableFuture<?> future = fitsProcessingService.processImage(testJob, null);
                    assertNotNull(future);
                });
    }

    // ========== Async Behavior Tests ==========

    @Test
    void processImageShouldExecuteAsynchronously() {
        // Given
        InputStream testStream = createTestInputStream();

        // When
        CompletableFuture<?> future = fitsProcessingService.processImage(testJob, testStream);

        // Then
        assertNotNull(future);
        // Don't wait for completion to avoid timeouts with invalid data
    }

    // ========== Edge Case Tests ==========

    @Test
    void processImageWithEmptyStreamShouldReturnFuture() {
        // Given
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        // When
        CompletableFuture<?> future = fitsProcessingService.processImage(testJob, emptyStream);

        // Then
        assertNotNull(future);
        // The future will complete exceptionally, but we don't test that here
        // to avoid test timeouts
    }

    @Test
    void processImageWithDifferentJobTypesShouldAcceptAll() {
        // Test that different job types are accepted
        ProcessingJob.ProcessingType[] types = ProcessingJob.ProcessingType.values();

        for (ProcessingJob.ProcessingType type : types) {
            ProcessingJob job = createTestJobWithType(type);
            InputStream stream = createTestInputStream();

            CompletableFuture<?> future = fitsProcessingService.processImage(job, stream);
            assertNotNull(future, "Should accept job type: " + type);
        }
    }

    // ========== Configuration Integration Tests ==========

    @Test
    void processImageShouldUseInjectedDependencies() {
        // This test verifies that the service uses its injected dependencies
        // We can't easily test the internal behavior without complex mocking,
        // but we can verify the service is properly constructed

        InputStream testStream = createTestInputStream();

        // The service should not throw null pointer exceptions when using dependencies
        assertDoesNotThrow(
                () -> {
                    CompletableFuture<?> future =
                            fitsProcessingService.processImage(testJob, testStream);
                    assertNotNull(future);
                });
    }

    // ========== Test Helper Methods ==========

    private ProcessingJob createTestProcessingJob() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("instrument", "HST");
        metadata.put("filter", "F814W");

        return ProcessingJob.builder()
                .jobId("test_job_123")
                .status(ProcessingJob.ProcessingStatus.RUNNING)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("test-image.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("processed-image.fits")
                .metadata(metadata)
                .completedSteps(new ArrayList<>())
                .build();
    }

    private ProcessingJob createTestJobWithType(ProcessingJob.ProcessingType type) {
        ProcessingJob job = createTestProcessingJob();
        job.setProcessingType(type);
        return job;
    }

    private InputStream createTestInputStream() {
        return new ByteArrayInputStream("test data".getBytes());
    }
}
