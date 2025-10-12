package com.mcgeecahill.astro.processor.constants;

/**
 * Time Conversion and Duration Constants
 *
 * <p>This class contains constants for time unit conversions, timeouts, retention policies, and
 * duration calculations used throughout the system.
 *
 * <p>All time-based operations should use these constants to ensure consistency and
 * maintainability.
 */
public final class TimeConstants {

    private TimeConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Basic Time Unit Conversions
    // ========================================

    /** Milliseconds per second */
    public static final long MILLISECONDS_PER_SECOND = 1000L;

    /** Seconds per minute */
    public static final int SECONDS_PER_MINUTE = 60;

    /** Minutes per hour */
    public static final int MINUTES_PER_HOUR = 60;

    // ========================================
    // Common Duration Conversions
    // ========================================

    /** Milliseconds per minute (60,000) */
    public static final long MILLISECONDS_PER_MINUTE = 60000L;

    /** Milliseconds per 2 minutes (120,000) - Common timeout value */
    public static final long MILLISECONDS_PER_2_MINUTES = 120000L;

    /** Milliseconds per 30 minutes (1,800,000) - Half hour duration */
    public static final long MILLISECONDS_PER_30_MINUTES = 1800000L;

    /** Milliseconds per hour (3,600,000) */
    public static final long MILLISECONDS_PER_HOUR = 3600000L;

    // ========================================
    // Retention and Cleanup Policies
    // ========================================

    /** Days to retain workflow versions and historical data */
    public static final int DAYS_FOR_WORKFLOW_RETENTION = 30;

    /** Days to retain experimental versions for comparison (absolute value used) */
    public static final int VERSION_COMPARISON_RETENTION_DAYS = 7;
}
