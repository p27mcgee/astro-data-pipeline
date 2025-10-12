package com.mcgeecahill.astro.processor.constants;

/**
 * Job Management and Processing Constants
 *
 * <p>This class contains constants used for job lifecycle management, error handling, cleanup
 * policies, and retry mechanisms throughout the processing job system.
 *
 * <p>These values are tuned for operational efficiency and system resource management.
 */
public final class JobManagementConstants {

    private JobManagementConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Error Handling
    // ========================================

    /** Maximum length for error messages (characters) to prevent database overflow */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    // ========================================
    // Job Cleanup Policies
    // ========================================

    /** Age threshold (hours) for cleaning up completed jobs */
    public static final int JOB_CLEANUP_AGE_HOURS = 12;

    // ========================================
    // Retry Mechanisms
    // ========================================

    /** Initial retry delay for exponential backoff (milliseconds) */
    public static final long INITIAL_RETRY_DELAY_MS = 5000L;

    /** Maximum retry delay cap to prevent excessive wait times (milliseconds) */
    public static final long MAX_RETRY_DELAY_MS = 500L;

    /** Default maximum retry attempts for failed jobs */
    public static final int DEFAULT_MAX_RETRIES = 3;

    // ========================================
    // Job Priority System
    // ========================================

    /** Default job priority level (normal priority) */
    public static final int DEFAULT_PRIORITY = 5;

    /** High priority level for urgent jobs */
    public static final int HIGH_PRIORITY = 1;

    /** Low priority level for background jobs */
    public static final int LOW_PRIORITY = 10;

    // ========================================
    // Job ID Generation
    // ========================================

    /** Length of job ID suffix (from UUID) */
    public static final int JOB_ID_LENGTH = 12;

    // ========================================
    // Processing Simulation
    // ========================================

    /** Base sleep time for processing simulation (milliseconds) */
    public static final long SIMULATION_BASE_SLEEP_MS = 1000L;

    /** Maximum random additional sleep time for simulation (milliseconds) */
    public static final long SIMULATION_RANDOM_SLEEP_MS = 2000L;
}
