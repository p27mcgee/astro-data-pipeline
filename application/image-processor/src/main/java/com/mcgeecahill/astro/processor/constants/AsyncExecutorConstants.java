package com.mcgeecahill.astro.processor.constants;

/**
 * Asynchronous Executor Thread Pool Configuration Constants
 *
 * <p>This class contains constants for configuring thread pools used for asynchronous processing,
 * batch operations, and parallel execution throughout the system.
 *
 * <p>Thread pool sizes are tuned based on system capacity, workload patterns, and resource
 * availability to balance throughput and resource consumption.
 *
 * <p><strong>Note:</strong> Some configurations may appear inverted (max < core) due to specific
 * executor behaviors where core pool size represents the target concurrency and max represents
 * queue overflow handling capacity.
 */
public final class AsyncExecutorConstants {

    private AsyncExecutorConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Processing Executor Configuration
    // ========================================

    /**
     * Core pool size for real-time processing executor
     *
     * <p>Represents steady-state concurrency for normal processing load
     */
    public static final int PROCESSING_EXECUTOR_CORE_POOL_SIZE = 60;

    /**
     * Maximum pool size for processing executor
     *
     * <p>Note: This value (30) is intentionally less than core size (60) in current configuration.
     * This may be a configuration oversight or specific to the ThreadPoolTaskExecutor behavior
     * where it handles queue saturation differently.
     */
    public static final int PROCESSING_EXECUTOR_MAX_POOL_SIZE = 30;

    // ========================================
    // Batch Processing Executor Configuration
    // ========================================

    /**
     * Core pool size for batch processing executor
     *
     * <p>Higher concurrency for bulk operations and historical data processing
     */
    public static final int BATCH_EXECUTOR_CORE_POOL_SIZE = 120;

    /**
     * Maximum pool size for batch processing executor
     *
     * <p>Note: This value (60) is intentionally less than core size (120). See processing executor
     * note above for similar configuration pattern.
     */
    public static final int BATCH_EXECUTOR_MAX_POOL_SIZE = 60;

    // ========================================
    // Thread Pool Timeout Configuration
    // ========================================

    /**
     * Keep-alive time for idle threads in processing executor (seconds)
     *
     * <p>Idle threads beyond core pool size will be terminated after this duration
     */
    public static final int PROCESSING_EXECUTOR_KEEP_ALIVE_SECONDS = 60;

    /**
     * Graceful shutdown timeout for processing executor (seconds)
     *
     * <p>Maximum time to wait for tasks to complete during shutdown
     */
    public static final int PROCESSING_EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 30;

    /**
     * Keep-alive time for idle threads in batch executor (seconds)
     *
     * <p>Longer keep-alive for batch operations to handle sporadic workloads
     */
    public static final int BATCH_EXECUTOR_KEEP_ALIVE_SECONDS = 120;

    /**
     * Graceful shutdown timeout for batch executor (seconds)
     *
     * <p>Longer timeout for batch operations which may take more time to complete
     */
    public static final int BATCH_EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 60;
}
