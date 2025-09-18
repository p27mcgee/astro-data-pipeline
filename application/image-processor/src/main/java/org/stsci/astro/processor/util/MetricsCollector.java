package org.stsci.astro.processor.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.stsci.astro.processor.entity.ProcessingJob;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection utility for astronomical image processing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCollector {

    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter processingSuccessCounter;
    private final Counter processingFailureCounter;
    private final Counter cosmicRayDetectionCounter;
    private final Counter darkSubtractionCounter;
    private final Counter flatCorrectionCounter;
    
    // Timers
    private final Timer processingTimer;
    private final Timer ioTimer;
    
    // Gauges
    private final AtomicLong activeJobsGauge = new AtomicLong(0);
    private final AtomicLong queueSizeGauge = new AtomicLong(0);

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.processingSuccessCounter = Counter.builder("fits_processing_success_total")
                .description("Total number of successful FITS processing operations")
                .register(meterRegistry);
                
        this.processingFailureCounter = Counter.builder("fits_processing_errors_total")
                .description("Total number of failed FITS processing operations")
                .register(meterRegistry);
                
        this.cosmicRayDetectionCounter = Counter.builder("cosmic_ray_detection_total")
                .description("Total number of cosmic rays detected and removed")
                .register(meterRegistry);
                
        this.darkSubtractionCounter = Counter.builder("dark_subtraction_operations_total")
                .description("Total number of dark subtraction operations")
                .register(meterRegistry);
                
        this.flatCorrectionCounter = Counter.builder("flat_correction_operations_total")
                .description("Total number of flat correction operations")
                .register(meterRegistry);
        
        // Initialize timers
        this.processingTimer = Timer.builder("fits_processing_duration_seconds")
                .description("Time taken to process FITS images")
                .register(meterRegistry);
                
        this.ioTimer = Timer.builder("fits_io_duration_seconds")
                .description("Time taken for FITS I/O operations")
                .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("fits_processing_active_jobs", activeJobsGauge, AtomicLong::doubleValue)
                .description("Number of currently active processing jobs")
                .register(meterRegistry);
                
        Gauge.builder("fits_processing_queue_size", queueSizeGauge, AtomicLong::doubleValue)
                .description("Number of jobs in processing queue")
                .register(meterRegistry);
    }

    /**
     * Record successful processing operation
     */
    public void recordProcessingSuccess(ProcessingJob job, long processingTimeMs) {
        processingSuccessCounter.increment();
        processingTimer.record(processingTimeMs, TimeUnit.MILLISECONDS);
        
        // Record processing type specific metrics
        String processingType = job.getProcessingType().toString();
        Counter.builder("fits_processing_by_type_total")
                .tag("type", processingType)
                .tag("status", "success")
                .description("Processing operations by type")
                .register(meterRegistry)
                .increment();
                
        log.debug("Recorded processing success metrics for job: {}", job.getJobId());
    }

    /**
     * Record failed processing operation
     */
    public void recordProcessingFailure(ProcessingJob job, Exception error) {
        processingFailureCounter.increment();
        
        String processingType = job.getProcessingType().toString();
        String errorType = error.getClass().getSimpleName();
        
        Counter.builder("fits_processing_by_type_total")
                .tag("type", processingType)
                .tag("status", "failure")
                .tag("error_type", errorType)
                .description("Processing operations by type")
                .register(meterRegistry)
                .increment();
                
        log.debug("Recorded processing failure metrics for job: {}", job.getJobId());
    }

    /**
     * Record image quality metrics
     */
    public void recordImageQuality(double qualityScore) {
        // Use a counter instead since we can't register gauges dynamically
        Counter.builder("fits_image_quality_total")
                .description("Total image quality score")
                .register(meterRegistry)
                .increment(qualityScore);
                
        // Categorize quality
        String qualityCategory;
        if (qualityScore >= 80) {
            qualityCategory = "excellent";
        } else if (qualityScore >= 60) {
            qualityCategory = "good";
        } else if (qualityScore >= 40) {
            qualityCategory = "fair";
        } else {
            qualityCategory = "poor";
        }
        
        Counter.builder("fits_image_quality_category_total")
                .tag("category", qualityCategory)
                .description("Images by quality category")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record cosmic ray detection metrics
     */
    public void recordCosmicRayDetection(int cosmicRaysDetected) {
        cosmicRayDetectionCounter.increment(cosmicRaysDetected);
        
        // Record histogram of cosmic ray counts
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("cosmic_ray_count_distribution")
                .description("Distribution of cosmic ray counts per image")
                .register(meterRegistry));
    }

    /**
     * Record calibration step execution
     */
    public void recordCalibrationStep(String stepName) {
        switch (stepName.toLowerCase()) {
            case "dark_subtraction":
                darkSubtractionCounter.increment();
                break;
            case "flat_correction":
                flatCorrectionCounter.increment();
                break;
            default:
                Counter.builder("calibration_steps_total")
                        .tag("step", stepName)
                        .description("Calibration steps executed")
                        .register(meterRegistry)
                        .increment();
        }
    }

    /**
     * Record I/O metrics
     */
    public void recordIOOperation(String operation, long durationMs, long bytes) {
        ioTimer.record(durationMs, TimeUnit.MILLISECONDS);
        
        Counter.builder("fits_io_bytes_total")
                .tag("operation", operation)
                .description("Total bytes processed in I/O operations")
                .register(meterRegistry)
                .increment(bytes);
                
        Counter.builder("fits_io_operations_total")
                .tag("operation", operation)
                .description("Total number of I/O operations")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record memory usage metrics
     */
    public void recordMemoryUsage(long memoryUsageMb) {
        Counter.builder("fits_processing_memory_usage_total_mb")
                .description("Total memory usage during FITS processing")
                .register(meterRegistry)
                .increment(memoryUsageMb);
    }

    /**
     * Record processing throughput
     */
    public void recordThroughput(double pixelsPerSecond) {
        Counter.builder("fits_processing_throughput_total_pixels")
                .description("Total pixels processed")
                .register(meterRegistry)
                .increment(pixelsPerSecond);
    }

    /**
     * Update active jobs count
     */
    public void updateActiveJobs(long count) {
        activeJobsGauge.set(count);
    }

    /**
     * Update queue size
     */
    public void updateQueueSize(long size) {
        queueSizeGauge.set(size);
    }

    /**
     * Record file size metrics
     */
    public void recordFileSize(String fileType, long sizeBytes) {
        Counter.builder("fits_file_size_bytes_total")
                .tag("type", fileType)
                .description("Total file size processed")
                .register(meterRegistry)
                .increment(sizeBytes);
                
        // Record file size distribution
        if (sizeBytes < 1024 * 1024) { // < 1MB
            Counter.builder("fits_file_size_category_total")
                    .tag("category", "small")
                    .register(meterRegistry)
                    .increment();
        } else if (sizeBytes < 100 * 1024 * 1024) { // < 100MB
            Counter.builder("fits_file_size_category_total")
                    .tag("category", "medium")
                    .register(meterRegistry)
                    .increment();
        } else {
            Counter.builder("fits_file_size_category_total")
                    .tag("category", "large")
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Record instrument-specific metrics
     */
    public void recordInstrumentMetrics(String instrument, String filter) {
        Counter.builder("fits_processing_by_instrument_total")
                .tag("instrument", instrument)
                .tag("filter", filter)
                .description("Processing operations by instrument and filter")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record processing efficiency metrics
     */
    public void recordEfficiency(double cpuEfficiency, double memoryEfficiency) {
        Counter.builder("fits_processing_cpu_efficiency_total")
                .description("Total CPU efficiency score")
                .register(meterRegistry)
                .increment(cpuEfficiency);
                
        Counter.builder("fits_processing_memory_efficiency_total")
                .description("Total memory efficiency score")
                .register(meterRegistry)
                .increment(memoryEfficiency);
    }
}