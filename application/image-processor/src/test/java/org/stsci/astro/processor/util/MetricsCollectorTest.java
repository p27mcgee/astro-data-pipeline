package org.stsci.astro.processor.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stsci.astro.processor.entity.ProcessingJob;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private MetricsCollector metricsCollector;
    private ProcessingJob sampleJob;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new MetricsCollector(meterRegistry);
        
        sampleJob = ProcessingJob.builder()
                .id(1L)
                .jobId("job_123456789")
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .build();
    }

    @Test
    void constructor_ShouldInitializeAllMeters() {
        // When - Constructor is called (in setUp)
        
        // Then - Verify that gauges are registered
        assertNotNull(meterRegistry.find("fits_processing_active_jobs").gauge());
        assertNotNull(meterRegistry.find("fits_processing_queue_size").gauge());
        
        // Verify that counters are registered
        assertNotNull(meterRegistry.find("fits_processing_success_total").counter());
        assertNotNull(meterRegistry.find("fits_processing_errors_total").counter());
        assertNotNull(meterRegistry.find("cosmic_ray_detection_total").counter());
        assertNotNull(meterRegistry.find("dark_subtraction_operations_total").counter());
        assertNotNull(meterRegistry.find("flat_correction_operations_total").counter());
        
        // Verify that timers are registered
        assertNotNull(meterRegistry.find("fits_processing_duration_seconds").timer());
        assertNotNull(meterRegistry.find("fits_io_duration_seconds").timer());
    }

    @Test
    void recordProcessingSuccess_ShouldIncrementCountersAndRecordTime() {
        // Given
        long processingTimeMs = 5000L;

        // When
        metricsCollector.recordProcessingSuccess(sampleJob, processingTimeMs);

        // Then
        Counter successCounter = meterRegistry.find("fits_processing_success_total").counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count());

        Timer processingTimer = meterRegistry.find("fits_processing_duration_seconds").timer();
        assertNotNull(processingTimer);
        assertEquals(1L, processingTimer.count());

        // Check that processing type specific counter is incremented
        Counter typeCounter = meterRegistry.find("fits_processing_by_type_total")
                .tag("type", "FULL_CALIBRATION")
                .tag("status", "success")
                .counter();
        assertNotNull(typeCounter);
        assertEquals(1.0, typeCounter.count());
    }

    @Test
    void recordProcessingFailure_ShouldIncrementErrorCounters() {
        // Given
        Exception error = new RuntimeException("Test error");

        // When
        metricsCollector.recordProcessingFailure(sampleJob, error);

        // Then
        Counter failureCounter = meterRegistry.find("fits_processing_errors_total").counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());

        // Check that processing type specific counter is incremented
        Counter typeCounter = meterRegistry.find("fits_processing_by_type_total")
                .tag("type", "FULL_CALIBRATION")
                .tag("status", "failure")
                .tag("error_type", "RuntimeException")
                .counter();
        assertNotNull(typeCounter);
        assertEquals(1.0, typeCounter.count());
    }

    @Test
    void recordImageQuality_ShouldIncrementCounterAndCategorize() {
        // Given
        double excellentQuality = 85.0;
        double poorQuality = 30.0;

        // When
        metricsCollector.recordImageQuality(excellentQuality);
        metricsCollector.recordImageQuality(poorQuality);

        // Then
        Counter excellentCounter = meterRegistry.find("fits_image_quality_category_total")
                .tag("category", "excellent")
                .counter();
        assertNotNull(excellentCounter);
        assertEquals(1.0, excellentCounter.count());

        Counter poorCounter = meterRegistry.find("fits_image_quality_category_total")
                .tag("category", "poor")
                .counter();
        assertNotNull(poorCounter);
        assertEquals(1.0, poorCounter.count());
    }

    @Test
    void recordCosmicRayDetection_ShouldIncrementByCount() {
        // Given
        int cosmicRaysDetected = 15;

        // When
        metricsCollector.recordCosmicRayDetection(cosmicRaysDetected);

        // Then
        Counter cosmicRayCounter = meterRegistry.find("cosmic_ray_detection_total").counter();
        assertNotNull(cosmicRayCounter);
        assertEquals(15.0, cosmicRayCounter.count());
    }

    @Test
    void recordCalibrationStep_DarkSubtraction_ShouldIncrementCorrectCounter() {
        // When
        metricsCollector.recordCalibrationStep("dark_subtraction");

        // Then
        Counter darkCounter = meterRegistry.find("dark_subtraction_operations_total").counter();
        assertNotNull(darkCounter);
        assertEquals(1.0, darkCounter.count());
    }

    @Test
    void recordCalibrationStep_FlatCorrection_ShouldIncrementCorrectCounter() {
        // When
        metricsCollector.recordCalibrationStep("flat_correction");

        // Then
        Counter flatCounter = meterRegistry.find("flat_correction_operations_total").counter();
        assertNotNull(flatCounter);
        assertEquals(1.0, flatCounter.count());
    }

    @Test
    void recordCalibrationStep_CustomStep_ShouldIncrementGenericCounter() {
        // When
        metricsCollector.recordCalibrationStep("custom_step");

        // Then
        Counter customCounter = meterRegistry.find("calibration_steps_total")
                .tag("step", "custom_step")
                .counter();
        assertNotNull(customCounter);
        assertEquals(1.0, customCounter.count());
    }

    @Test
    void recordIOOperation_ShouldIncrementCountersAndRecordTime() {
        // Given
        String operation = "read";
        long durationMs = 1500L;
        long bytes = 1048576L; // 1 MB

        // When
        metricsCollector.recordIOOperation(operation, durationMs, bytes);

        // Then
        Timer ioTimer = meterRegistry.find("fits_io_duration_seconds").timer();
        assertNotNull(ioTimer);
        assertEquals(1L, ioTimer.count());

        Counter bytesCounter = meterRegistry.find("fits_io_bytes_total")
                .tag("operation", operation)
                .counter();
        assertNotNull(bytesCounter);
        assertEquals(1048576.0, bytesCounter.count());

        Counter operationsCounter = meterRegistry.find("fits_io_operations_total")
                .tag("operation", operation)
                .counter();
        assertNotNull(operationsCounter);
        assertEquals(1.0, operationsCounter.count());
    }

    @Test
    void recordMemoryUsage_ShouldIncrementCounter() {
        // Given
        long memoryUsageMb = 512L;

        // When
        metricsCollector.recordMemoryUsage(memoryUsageMb);

        // Then
        Counter memoryCounter = meterRegistry.find("fits_processing_memory_usage_total_mb").counter();
        assertNotNull(memoryCounter);
        assertEquals(512.0, memoryCounter.count());
    }

    @Test
    void recordThroughput_ShouldIncrementCounter() {
        // Given
        double pixelsPerSecond = 1000000.0;

        // When
        metricsCollector.recordThroughput(pixelsPerSecond);

        // Then
        Counter throughputCounter = meterRegistry.find("fits_processing_throughput_total_pixels").counter();
        assertNotNull(throughputCounter);
        assertEquals(1000000.0, throughputCounter.count());
    }

    @Test
    void updateActiveJobs_ShouldUpdateGauge() {
        // Given
        long activeJobs = 5L;

        // When
        metricsCollector.updateActiveJobs(activeJobs);

        // Then
        assertEquals(5.0, meterRegistry.find("fits_processing_active_jobs").gauge().value());
    }

    @Test
    void updateQueueSize_ShouldUpdateGauge() {
        // Given
        long queueSize = 10L;

        // When
        metricsCollector.updateQueueSize(queueSize);

        // Then
        assertEquals(10.0, meterRegistry.find("fits_processing_queue_size").gauge().value());
    }

    @Test
    void recordFileSize_SmallFile_ShouldCategorizeCorrectly() {
        // Given
        String fileType = "input";
        long sizeBytes = 512 * 1024; // 512 KB

        // When
        metricsCollector.recordFileSize(fileType, sizeBytes);

        // Then
        Counter sizeCounter = meterRegistry.find("fits_file_size_bytes_total")
                .tag("type", fileType)
                .counter();
        assertNotNull(sizeCounter);
        assertEquals(sizeBytes, sizeCounter.count());

        Counter categoryCounter = meterRegistry.find("fits_file_size_category_total")
                .tag("category", "small")
                .counter();
        assertNotNull(categoryCounter);
        assertEquals(1.0, categoryCounter.count());
    }

    @Test
    void recordFileSize_MediumFile_ShouldCategorizeCorrectly() {
        // Given
        String fileType = "output";
        long sizeBytes = 50 * 1024 * 1024; // 50 MB

        // When
        metricsCollector.recordFileSize(fileType, sizeBytes);

        // Then
        Counter categoryCounter = meterRegistry.find("fits_file_size_category_total")
                .tag("category", "medium")
                .counter();
        assertNotNull(categoryCounter);
        assertEquals(1.0, categoryCounter.count());
    }

    @Test
    void recordFileSize_LargeFile_ShouldCategorizeCorrectly() {
        // Given
        String fileType = "archive";
        long sizeBytes = 200L * 1024 * 1024; // 200 MB

        // When
        metricsCollector.recordFileSize(fileType, sizeBytes);

        // Then
        Counter categoryCounter = meterRegistry.find("fits_file_size_category_total")
                .tag("category", "large")
                .counter();
        assertNotNull(categoryCounter);
        assertEquals(1.0, categoryCounter.count());
    }

    @Test
    void recordInstrumentMetrics_ShouldIncrementWithCorrectTags() {
        // Given
        String instrument = "HST";
        String filter = "F814W";

        // When
        metricsCollector.recordInstrumentMetrics(instrument, filter);

        // Then
        Counter instrumentCounter = meterRegistry.find("fits_processing_by_instrument_total")
                .tag("instrument", instrument)
                .tag("filter", filter)
                .counter();
        assertNotNull(instrumentCounter);
        assertEquals(1.0, instrumentCounter.count());
    }

    @Test
    void recordEfficiency_ShouldIncrementEfficiencyCounters() {
        // Given
        double cpuEfficiency = 0.85;
        double memoryEfficiency = 0.92;

        // When
        metricsCollector.recordEfficiency(cpuEfficiency, memoryEfficiency);

        // Then
        Counter cpuCounter = meterRegistry.find("fits_processing_cpu_efficiency_total").counter();
        assertNotNull(cpuCounter);
        assertEquals(0.85, cpuCounter.count());

        Counter memoryCounter = meterRegistry.find("fits_processing_memory_efficiency_total").counter();
        assertNotNull(memoryCounter);
        assertEquals(0.92, memoryCounter.count());
    }

    @Test
    void multipleOperations_ShouldAccumulateCorrectly() {
        // Given
        long processingTime1 = 1000L;
        long processingTime2 = 2000L;

        // When
        metricsCollector.recordProcessingSuccess(sampleJob, processingTime1);
        metricsCollector.recordProcessingSuccess(sampleJob, processingTime2);

        // Then
        Counter successCounter = meterRegistry.find("fits_processing_success_total").counter();
        assertNotNull(successCounter);
        assertEquals(2.0, successCounter.count());

        Timer processingTimer = meterRegistry.find("fits_processing_duration_seconds").timer();
        assertNotNull(processingTimer);
        assertEquals(2L, processingTimer.count());
    }
}