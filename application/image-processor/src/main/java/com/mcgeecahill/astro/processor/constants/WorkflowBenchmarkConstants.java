package com.mcgeecahill.astro.processor.constants;

/**
 * Workflow Performance Benchmark Constants
 *
 * <p>This class contains baseline performance metrics for different workflow versions used for
 * performance comparison, regression detection, and efficiency tracking.
 *
 * <p>These benchmarks represent measured performance characteristics of production workflow
 * versions and are used to calculate performance improvements and detect degradations.
 *
 * <p><strong>Version History:</strong>
 *
 * <ul>
 *   <li>V1.0 - Baseline implementation with standard calibration pipeline
 *   <li>V2.0 - Optimized implementation with improved algorithms and reduced I/O
 * </ul>
 */
public final class WorkflowBenchmarkConstants {

    private WorkflowBenchmarkConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Version 1.0 Baseline Metrics
    // ========================================

    /** Version 1.0 baseline CPU processing time (milliseconds) */
    public static final int V1_BASELINE_CPU_TIME_MS = 2300;

    /** Version 1.0 baseline peak memory usage (megabytes) */
    public static final int V1_BASELINE_MEMORY_MB = 150;

    /** Version 1.0 baseline image quality score (0.0-1.0 scale) */
    public static final double V1_BASELINE_QUALITY_SCORE = 0.92;

    /** Version 1.0 baseline success rate (percentage) */
    public static final double V1_BASELINE_SUCCESS_RATE = 85.0;

    /** Version 1.0 baseline I/O operations count */
    public static final long V1_BASELINE_IO_OPERATIONS = 1250L;

    // ========================================
    // Version 2.0 Improved Metrics
    // ========================================

    /** Version 2.0 improved CPU processing time (milliseconds) - 22% faster than V1 */
    public static final int V2_BASELINE_CPU_TIME_MS = 1800;

    /** Version 2.0 memory usage (megabytes) - slightly higher due to caching optimizations */
    public static final int V2_BASELINE_MEMORY_MB = 180;

    /** Version 2.0 improved image quality score (0.0-1.0 scale) - 5% better than V1 */
    public static final double V2_BASELINE_QUALITY_SCORE = 0.97;

    /** Version 2.0 improved success rate (percentage) - 7% better than V1 */
    public static final double V2_BASELINE_SUCCESS_RATE = 92.0;

    /** Version 2.0 reduced I/O operations - 96% reduction through batching and caching */
    public static final long V2_BASELINE_IO_OPERATIONS = 45L;

    // ========================================
    // Performance Comparison Thresholds
    // ========================================

    /**
     * Minimum performance ratio threshold for considering an improvement significant
     *
     * <p>Performance must be at least 85% of baseline to avoid flagging as regression
     */
    public static final double PERFORMANCE_IMPROVEMENT_THRESHOLD = 0.85;

    // ========================================
    // Workflow Comparison Thresholds
    // ========================================

    /**
     * Threshold for significant performance improvement (percentage points)
     *
     * <p>Improvements above this threshold warrant recommendation
     */
    public static final double SIGNIFICANT_IMPROVEMENT_THRESHOLD = 10.0;

    /**
     * Default ML model confidence threshold for experimental workflows
     *
     * <p>Typically used for ML-enhanced algorithms (85% confidence)
     */
    public static final double ML_CONFIDENCE_THRESHOLD = 0.85;
}
