package com.mcgeecahill.astro.processor.dto;

import com.mcgeecahill.astro.processor.constants.MetricsConstants;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for processing metrics */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingMetricsResponse {

    // Overall metrics
    private Long totalJobsProcessed;
    private Long totalJobsSuccessful;
    private Long totalJobsFailed;
    private Double successRate;

    // Performance metrics
    private Double averageProcessingTimeMs;
    private Double medianProcessingTimeMs;
    private Double p95ProcessingTimeMs;
    private Double averageQueueTimeMs;

    // Throughput metrics
    private Double currentThroughputJobsPerHour;
    private Double peakThroughputJobsPerHour;
    private Double averageThroughputJobsPerHour;

    // Resource utilization
    private Double averageCpuUtilization;
    private Double averageMemoryUtilization;
    private Long totalBytesProcessed;
    private Double averageFileSizeMb;

    // Quality metrics
    private Double averageImageQuality;
    private Long totalCosmicRaysRemoved;
    private Double averageCosmicRaysPerImage;

    // Processing step metrics
    private Long darkSubtractionOperations;
    private Long flatCorrectionOperations;
    private Long cosmicRayRemovalOperations;

    // Current system status
    private Integer activeJobs;
    private Integer queuedJobs;
    private Integer availableWorkers;
    private Integer totalWorkers;

    // Time-based metrics
    private LocalDateTime metricsCollectedAt;
    private LocalDateTime windowStartTime;
    private LocalDateTime windowEndTime;

    // Error analysis
    private Map<String, Long> errorsByType;
    private Map<String, Long> jobsByProcessingType;
    private Map<String, Long> jobsByPriority;

    // System health indicators
    private String systemStatus; // HEALTHY, DEGRADED, CRITICAL
    private Double systemLoad;
    private Long uptime;

    // Storage metrics
    private Long totalInputDataMb;
    private Long totalOutputDataMb;
    private Double compressionRatio;

    // Efficiency metrics
    private Double cpuEfficiency;
    private Double memoryEfficiency;
    private Double ioEfficiency;

    // Astronomical-specific metrics
    private Map<String, Long> processingByInstrument;
    private Map<String, Long> processingByFilter;
    private Map<String, Double> qualityByInstrument;

    // Historical comparison
    private Double performanceChangePercent;
    private String performanceTrend; // IMPROVING, STABLE, DEGRADING

    /** Create a simple metrics response with basic information */
    public static ProcessingMetricsResponse createBasic(
            long totalJobs,
            long successfulJobs,
            long failedJobs,
            double avgProcessingTime,
            int activeJobs,
            int queuedJobs) {

        double successRate = totalJobs > 0 ? (double) successfulJobs / totalJobs * 100 : 0.0;

        return ProcessingMetricsResponse.builder()
                .totalJobsProcessed(totalJobs)
                .totalJobsSuccessful(successfulJobs)
                .totalJobsFailed(failedJobs)
                .successRate(successRate)
                .averageProcessingTimeMs(avgProcessingTime)
                .activeJobs(activeJobs)
                .queuedJobs(queuedJobs)
                .metricsCollectedAt(LocalDateTime.now())
                .systemStatus(determineSystemStatus(successRate, activeJobs, queuedJobs))
                .build();
    }

    /** Determine system status based on metrics */
    private static String determineSystemStatus(
            double successRate, int activeJobs, int queuedJobs) {
        if (successRate >= MetricsConstants.HEALTHY_SUCCESS_RATE_THRESHOLD
                && queuedJobs < MetricsConstants.HEALTHY_QUEUE_SIZE_THRESHOLD) {
            return "HEALTHY";
        } else if (successRate >= MetricsConstants.DEGRADED_SUCCESS_RATE_THRESHOLD
                && queuedJobs < MetricsConstants.DEGRADED_QUEUE_SIZE_THRESHOLD) {
            return "DEGRADED";
        } else {
            return "CRITICAL";
        }
    }

    /** Check if system is performing well */
    public boolean isSystemHealthy() {
        return "HEALTHY".equals(systemStatus);
    }

    /** Get overall system efficiency score (0-100) */
    public Double getOverallEfficiencyScore() {
        if (cpuEfficiency == null || memoryEfficiency == null || ioEfficiency == null) {
            return null;
        }

        return (cpuEfficiency + memoryEfficiency + ioEfficiency)
                / MetricsConstants.EFFICIENCY_METRICS_COUNT
                * MetricsConstants.PERCENTAGE_MULTIPLIER;
    }

    /** Get processing capacity utilization percentage */
    public Double getCapacityUtilization() {
        if (totalWorkers == null || totalWorkers == 0) {
            return null;
        }

        int busyWorkers = activeJobs != null ? activeJobs : 0;
        return (double) busyWorkers / totalWorkers * MetricsConstants.PERCENTAGE_MULTIPLIER;
    }

    /** Get quality trend indicator */
    public String getQualityTrend() {
        if (averageImageQuality == null) {
            return "UNKNOWN";
        }

        if (averageImageQuality >= MetricsConstants.EXCELLENT_QUALITY_THRESHOLD) {
            return "EXCELLENT";
        } else if (averageImageQuality >= MetricsConstants.GOOD_QUALITY_THRESHOLD) {
            return "GOOD";
        } else if (averageImageQuality >= MetricsConstants.FAIR_QUALITY_THRESHOLD) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }

    /** Calculate data processing efficiency (output/input ratio) */
    public Double getDataEfficiency() {
        if (totalInputDataMb == null || totalOutputDataMb == null || totalInputDataMb == 0) {
            return null;
        }

        return (double) totalOutputDataMb / totalInputDataMb;
    }
}
