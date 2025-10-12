package com.mcgeecahill.astro.processor.constants;

/**
 * System Health and Performance Metrics Constants
 *
 * <p>This class contains constants used for system health monitoring, performance assessment, and
 * quality evaluation thresholds throughout the image processing system.
 *
 * <p>These thresholds are based on operational experience and system capacity planning to ensure
 * reliable performance indicators and automated health checks.
 */
public final class MetricsConstants {

    private MetricsConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // System Health Status Thresholds
    // ========================================

    /** Minimum success rate (%) for HEALTHY system status */
    public static final double HEALTHY_SUCCESS_RATE_THRESHOLD = 95.0;

    /** Minimum success rate (%) for DEGRADED system status */
    public static final double DEGRADED_SUCCESS_RATE_THRESHOLD = 85.0;

    /** Maximum queued jobs for HEALTHY system status */
    public static final int HEALTHY_QUEUE_SIZE_THRESHOLD = 100;

    /** Maximum queued jobs for DEGRADED system status */
    public static final int DEGRADED_QUEUE_SIZE_THRESHOLD = 500;

    // ========================================
    // Image Quality Assessment Thresholds
    // ========================================

    /** Minimum quality score for EXCELLENT rating (0-100 scale) */
    public static final double EXCELLENT_QUALITY_THRESHOLD = 80.0;

    /** Minimum quality score for GOOD rating (0-100 scale) */
    public static final double GOOD_QUALITY_THRESHOLD = 60.0;

    /** Minimum quality score for FAIR rating (0-100 scale) */
    public static final double FAIR_QUALITY_THRESHOLD = 40.0;

    // ========================================
    // Efficiency Calculation Constants
    // ========================================

    /** Number of efficiency metrics to average (CPU, Memory, I/O) */
    public static final double EFFICIENCY_METRICS_COUNT = 3.0;

    /** Multiplier to convert fractional values to percentages */
    public static final double PERCENTAGE_MULTIPLIER = 100.0;

    // ========================================
    // System Health Thresholds
    // ========================================

    /** Queue size threshold for degraded system health status */
    public static final int HEALTH_DEGRADED_QUEUE_THRESHOLD = 1000;

    /** Failed jobs threshold for degraded system health status */
    public static final int HEALTH_DEGRADED_FAILED_THRESHOLD = 100;

    /** Queue size threshold for critical system health status */
    public static final int HEALTH_CRITICAL_QUEUE_THRESHOLD = 5000;

    /** Failed jobs threshold for critical system health status */
    public static final int HEALTH_CRITICAL_FAILED_THRESHOLD = 500;
}
