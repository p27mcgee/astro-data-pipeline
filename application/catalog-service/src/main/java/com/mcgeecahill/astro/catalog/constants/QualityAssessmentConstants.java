package com.mcgeecahill.astro.catalog.constants;

/**
 * Catalog Quality Assessment Constants
 *
 * <p>This class contains constants for catalog quality assessment following STScI MAST archive
 * validation standards. Includes thresholds for: - Positional accuracy tolerances - Photometric
 * quality limits - Statistical analysis parameters - Completeness and reliability metrics
 *
 * <p>Based on STScI catalog validation procedures and IAU standards.
 */
public final class QualityAssessmentConstants {

    private QualityAssessmentConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Match Confidence
    // ========================================

    /** High match lower limit */
    public static final double HIGH_MATCH_CONFIDENCE_THRESHHOLD = 0.8;

    // ========================================
    // Position Tolerance Thresholds
    // ========================================

    /** Position matching tolerance (arcseconds) */
    public static final double POSITION_TOLERANCE_ARCSEC = 1.0;

    /** Default cross-match radius (arcseconds) */
    public static final double DEFAULT_MATCH_RADIUS_ARCSEC = 1.0;

    // ========================================
    // Photometric Quality Thresholds
    // ========================================

    /** Magnitude matching tolerance */
    public static final double MAGNITUDE_TOLERANCE = 0.1;

    /** Large photometric error threshold (magnitudes) */
    public static final double LARGE_PHOTOMETRIC_ERROR_THRESHOLD = 1.0;

    // ========================================
    // Proper Motion Thresholds
    // ========================================

    /** Proper motion matching tolerance (mas/year) */
    public static final double PROPER_MOTION_TOLERANCE_MAS = 10.0;

    // ========================================
    // Classification Confidence
    // ========================================

    /** Low classification confidence threshold */
    public static final double LOW_CLASSIFICATION_CONFIDENCE = 0.5;

    // ========================================
    // Statistical Analysis Parameters
    // ========================================

    /** Minimum observations required for variability analysis */
    public static final int MIN_OBSERVATIONS = 10;

    /** Minimum catalog size for statistical completeness analysis */
    public static final int MIN_CATALOG_SIZE_STATISTICAL = 100;

    /** Incompleteness assumption factor (assumes 20% missing) */
    public static final double INCOMPLETENESS_FACTOR = 1.2;

    // ========================================
    // Magnitude Distribution Analysis
    // ========================================

    /** Number of magnitude bins for completeness analysis */
    public static final int MAGNITUDE_BINS = 20;

    /** Magnitude bin edges for analysis */
    public static final double[] MAGNITUDE_BIN_EDGES = {12.0, 14.0, 16.0, 18.0, 20.0, 22.0};

    // ========================================
    // Completeness Percentiles
    // ========================================

    /** 50th percentile completeness */
    public static final double PERCENTILE_50 = 0.5;

    /** 90th percentile completeness */
    public static final double PERCENTILE_90 = 0.9;

    /** 95th percentile completeness */
    public static final double PERCENTILE_95 = 0.95;

    // ========================================
    // Quality Score Weights
    // Following STScI catalog quality methodology
    // ========================================

    /** Completeness contribution to overall quality score */
    public static final double QUALITY_COMPLETENESS_WEIGHT = 0.3;

    /** Reliability contribution to overall quality score */
    public static final double QUALITY_RELIABILITY_WEIGHT = 0.3;

    /** Astrometric quality contribution to overall quality score */
    public static final double QUALITY_ASTROMETRIC_WEIGHT = 0.2;

    /** Photometric quality contribution to overall quality score */
    public static final double QUALITY_PHOTOMETRIC_WEIGHT = 0.2;

    // ========================================
    // Default Quality Values
    // Used when reference catalog is not available
    // ========================================

    /** Default position accuracy when no reference (arcseconds) */
    public static final double DEFAULT_POSITION_ACCURACY = 0.1;

    /** Default proper motion accuracy when no reference (mas/year) */
    public static final double DEFAULT_PROPER_MOTION_ACCURACY = 0.05;

    /** Default overall astrometric quality (percentage) */
    public static final double DEFAULT_ASTROMETRIC_QUALITY = 95.0;

    /** Default magnitude accuracy when no reference (magnitudes) */
    public static final double DEFAULT_MAGNITUDE_ACCURACY = 0.02;

    /** Default color accuracy when no reference (magnitudes) */
    public static final double DEFAULT_COLOR_ACCURACY = 0.01;

    /** Default overall photometric quality (percentage) */
    public static final double DEFAULT_PHOTOMETRIC_QUALITY = 98.0;

    // ========================================
    // Quality Score Bounds
    // ========================================

    /** Maximum quality score (percentage) */
    public static final double MAX_QUALITY_SCORE = 100.0;

    /** Minimum quality score (percentage) */
    public static final double MIN_QUALITY_SCORE = 0.0;

    // ========================================
    // Variability Analysis Constants
    // ========================================

    /** Minimum period for variability analysis (hours) */
    public static final double MIN_PERIOD_HOURS = 0.1;

    /** Maximum period for variability analysis (days) */
    public static final double MAX_PERIOD_DAYS = 1000.0;

    /** Variability index threshold for variable classification */
    public static final double VARIABILITY_INDEX_THRESHOLD = 1.5;

    /** Periodogram power threshold for periodic variables */
    public static final double PERIODOGRAM_POWER_THRESHOLD = 10.0;

    /** Number of frequency samples for Lomb-Scargle periodogram */
    public static final int LOMB_SCARGLE_FREQUENCY_SAMPLES = 1000;

    /** Number of phase bins for phase-folded analysis */
    public static final int PHASE_BINS = 20;

    // ========================================
    // Variable Star Classification Thresholds
    // Based on period and amplitude characteristics
    // ========================================

    /** Period threshold for RR Lyrae classification (days) */
    public static final double RR_LYRAE_PERIOD_THRESHOLD = 1.0;

    /** Amplitude threshold for RR Lyrae classification (magnitudes) */
    public static final double RR_LYRAE_AMPLITUDE_THRESHOLD = 0.3;

    /** Period lower bound for Cepheid classification (days) */
    public static final double CEPHEID_PERIOD_MIN = 1.0;

    /** Period upper bound for Cepheid classification (days) */
    public static final double CEPHEID_PERIOD_MAX = 50.0;

    /** Amplitude threshold for Cepheid classification (magnitudes) */
    public static final double CEPHEID_AMPLITUDE_THRESHOLD = 0.5;

    /** Period lower bound for long-period variables (days) */
    public static final double LONG_PERIOD_MIN = 80.0;

    /** Period upper bound for long-period variables (days) */
    public static final double LONG_PERIOD_MAX = 400.0;

    /** Amplitude threshold for micro-variable classification (magnitudes) */
    public static final double MICRO_VARIABLE_AMPLITUDE_THRESHOLD = 0.1;

    /** Periodogram power threshold for irregular variables */
    public static final double IRREGULAR_POWER_THRESHOLD = 5.0;

    // ========================================
    // Statistical Moments Defaults
    // ========================================

    /** Default variance for statistical calculations */
    public static final double DEFAULT_VARIANCE = 1.0;

    /** Default kurtosis offset (excess kurtosis) */
    public static final double EXCESS_KURTOSIS_OFFSET = 3.0;

    /** Default autocorrelation timescale (days) */
    public static final double DEFAULT_AUTOCORRELATION_TIMESCALE = 1.0;

    /** Target autocorrelation for timescale estimation (1/e) */
    public static final double AUTOCORRELATION_TARGET = 1.0 / Math.E;

    // ========================================
    // Time-Series Analysis
    // ========================================

    /** Minimum denominator for Lomb-Scargle calculation */
    public static final double LOMB_SCARGLE_MIN_DENOMINATOR = 1e-10;

    /** Half divisor for Lomb-Scargle power calculation */
    public static final double LOMB_SCARGLE_POWER_FACTOR = 0.5;

    // ========================================
    // Catalog Cleaning Thresholds
    // ========================================

    /** Maximum age for transient object cleanup (days) */
    public static final int TRANSIENT_CLEANUP_DAYS = 30;

    /** Maximum age for follow-up observation identification (days) */
    public static final int FOLLOWUP_MAX_DAYS = 7;

    // ========================================
    // Format Strings and Identifiers
    // ========================================

    /** Magnitude bin format string */
    public static final String MAGNITUDE_BIN_FORMAT = "mag_%.0f_%.0f";

    /** Completeness limit key for 50% */
    public static final String COMPLETENESS_50_KEY = "50_percent";

    /** Completeness limit key for 90% */
    public static final String COMPLETENESS_90_KEY = "90_percent";

    /** Completeness limit key for 95% */
    public static final String COMPLETENESS_95_KEY = "95_percent";

    /** Completeness limit key for faintest magnitude */
    public static final String COMPLETENESS_FAINTEST_KEY = "faintest";

    /** Overall completeness key */
    public static final String COMPLETENESS_OVERALL_KEY = "overall";
}
