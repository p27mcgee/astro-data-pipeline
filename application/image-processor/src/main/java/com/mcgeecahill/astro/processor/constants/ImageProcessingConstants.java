package com.mcgeecahill.astro.processor.constants;

/**
 * Image Processing Constants for FITS Data Calibration
 *
 * <p>This class contains constants used throughout the FITS image processing pipeline, including
 * calibration parameters, algorithm thresholds, and quality metrics.
 *
 * <p>Based on STScI best practices and published astronomical image processing algorithms: -
 * L.A.Cosmic algorithm (van Dokkum 2001, PASP, 113, 1420) - HST/JWST calibration pipeline standards
 * - Professional astronomical photometry practices
 */
public final class ImageProcessingConstants {

    private ImageProcessingConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // General Processing Constants
    // ========================================

    /** Bytes per float pixel in FITS image */
    public static final int BYTES_PER_FLOAT = 4;

    /** Processing overhead memory multiplier */
    public static final int PROCESSING_MEMORY_OVERHEAD_MULTIPLIER = 3;

    /** Kilo-pixels conversion factor */
    public static final double KILO_PIXELS_CONVERSION = 1000.0;

    // ========================================
    // Overscan and Bias Estimation
    // ========================================

    /** Default overscan region size (pixels) */
    public static final int DEFAULT_OVERSCAN_SIZE_PIXELS = 50;

    /** Default bias level for instruments without overscan (ADU) */
    public static final double DEFAULT_BIAS_LEVEL_ADU = 100.0;

    // ========================================
    // Flat Field Correction Constants
    // ========================================

    /** Mode to median difference threshold for flat field normalization */
    public static final double FLAT_MODE_MEDIAN_THRESHOLD = 0.3;

    /** Sigma threshold for bad pixel detection in flat fields */
    public static final double FLAT_BAD_PIXEL_SIGMA_THRESHOLD = 5.0;

    // ========================================
    // L.A.Cosmic Algorithm Parameters
    // van Dokkum 2001, PASP, 113, 1420
    // ========================================

    /** Default cosmic ray detection threshold (sigma units) */
    public static final double LACOSMIC_DEFAULT_SIGCLIP = 5.0;

    /** Object limit for distinguishing cosmic rays from stars */
    public static final double LACOSMIC_DEFAULT_OBJLIM = 5.0;

    /** Fraction of sigma for fine-scale detection */
    public static final double LACOSMIC_DEFAULT_SIGFRAC = 0.3;

    /** Maximum iterations for cosmic ray detection */
    public static final int LACOSMIC_DEFAULT_NITER = 4;

    /** Median filter radius for fine structure detection */
    public static final int LACOSMIC_MEDIAN_FILTER_RADIUS = 5;

    /** Laplacian kernel center weight */
    public static final float LACOSMIC_LAPLACIAN_CENTER = 4.0f;

    /** Laplacian kernel edge weight */
    public static final float LACOSMIC_LAPLACIAN_EDGE = -1.0f;

    /** Laplacian kernel corner weight */
    public static final float LACOSMIC_LAPLACIAN_CORNER = 0.0f;

    /** Neighborhood size for cosmic ray detection (pixels) */
    public static final int LACOSMIC_NEIGHBORHOOD_SIZE = 8;

    /** Median index for 8-value neighborhood array */
    public static final int LACOSMIC_MEDIAN_INDEX_8 = 4;

    // ========================================
    // Enhanced L.A.Cosmic Parameters
    // ========================================

    /** Enhanced cosmic ray detection threshold (sigma units) */
    public static final double LACOSMIC_V2_DEFAULT_SIGCLIP = 4.5;

    /** Object limit modifier for star preservation */
    public static final double LACOSMIC_V2_STAR_PRESERVATION_FACTOR = 0.5;

    // ========================================
    // Image Quality Assessment
    // ========================================

    /** Minimum FWHM estimate (arcseconds) */
    public static final double MIN_FWHM_ARCSEC = 0.5;

    /** Maximum FWHM estimate baseline (arcseconds) */
    public static final double MAX_FWHM_BASELINE_ARCSEC = 3.0;

    /** FWHM edge strength scaling factor */
    public static final double FWHM_EDGE_STRENGTH_SCALE = 1000.0;

    /** Sigma threshold for detection in quality assessment */
    public static final double QUALITY_SIGMA_THRESHOLD = 5.0;

    /** Circular aperture area factor (π) */
    public static final double APERTURE_AREA_FACTOR = Math.PI;

    /** Baseline limiting magnitude (no stars detected) */
    public static final double BASELINE_LIMITING_MAGNITUDE = 25.0;

    /** Minimum limiting magnitude (bright limit) */
    public static final double MIN_LIMITING_MAGNITUDE = 15.0;

    /** Maximum limiting magnitude (faint limit) */
    public static final double MAX_LIMITING_MAGNITUDE = 30.0;

    // ========================================
    // Particle Analysis (Source Detection)
    // Following SExtractor and STScI photutils methodology
    // ========================================

    /** Minimum source area (pixels) - typical seeing disk minimum */
    public static final int MIN_SOURCE_AREA_PIXELS = 3;

    /** Maximum source area (pixels) - exclude large extended sources */
    public static final int MAX_SOURCE_AREA_PIXELS = 1000;

    /** Minimum circularity (allows moderate ellipticity) */
    public static final double MIN_CIRCULARITY = 0.3;

    /** Maximum circularity (perfect circles) */
    public static final double MAX_CIRCULARITY = 1.0;

    /** Background detection sigma threshold */
    public static final double BACKGROUND_SIGMA_THRESHOLD = 3.0;

    /** Number of magnitude bins for completeness analysis */
    public static final int MAGNITUDE_BINS = 20;

    // ========================================
    // Quality Score Weights and Factors
    // ========================================

    /** SNR contribution to quality score (max) */
    public static final double QUALITY_SNR_WEIGHT = 20.0;

    /** SNR scaling factor */
    public static final double QUALITY_SNR_SCALE = 2.0;

    /** Seeing contribution to quality score (max) */
    public static final double QUALITY_SEEING_WEIGHT = 20.0;

    /** Seeing penalty factor */
    public static final double QUALITY_SEEING_PENALTY = 5.0;

    /** Limiting magnitude contribution to quality score (max) */
    public static final double QUALITY_LIMITING_MAG_WEIGHT = 30.0;

    /** Limiting magnitude scaling factor */
    public static final double QUALITY_LIMITING_MAG_SCALE = 2.0;

    /** Stellarity contribution to quality score (max) */
    public static final double QUALITY_STELLARITY_WEIGHT = 15.0;

    /** Dynamic range contribution to quality score (max) */
    public static final double QUALITY_DYNAMIC_RANGE_WEIGHT = 15.0;

    /** Dynamic range logarithmic scaling factor */
    public static final double QUALITY_DYNAMIC_RANGE_SCALE = 5.0;

    // ========================================
    // Compactness and Shape Metrics
    // ========================================

    /** Compactness calculation factor (4π) */
    public static final double COMPACTNESS_FACTOR = 4.0 * Math.PI;

    /** Aspect ratio area factor (π/4 for circle) */
    public static final double ASPECT_RATIO_CIRCLE_FACTOR = Math.PI / 4.0;

    /** Square root weight for source area in stellarity calculation */
    public static final double STELLARITY_AREA_WEIGHT_POWER = 0.5;

    // ========================================
    // Astronomical Quality Metrics
    // ========================================

    /** Minimum FWHM value for seeing estimation (arcseconds) */
    public static final double MIN_SEEING_FWHM = 0.5;

    /** Magnitude calculation factor for standard photometry formula (2.5 * log10) */
    public static final double MAGNITUDE_CALCULATION_FACTOR_2_5 = 2.5;

    // ========================================
    // Statistical Processing
    // ========================================

    /** MAD to sigma conversion factor for normal distribution */
    public static final double MAD_TO_SIGMA = 1.4826;

    /** Minimum catalog size for statistical completeness */
    public static final int MIN_CATALOG_SIZE_STATISTICAL = 100;

    /** Incompleteness assumption factor (20% typically missing) */
    public static final double INCOMPLETENESS_FACTOR = 1.2;

    // ========================================
    // Batch Processing
    // ========================================

    /** Default batch size for bulk operations */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** Batch progress logging interval (number of batches) */
    public static final int BATCH_LOG_INTERVAL = 10;

    // ========================================
    // Data Type Conversions
    // ========================================

    /** Minimum pixel value after calibration */
    public static final float MIN_PIXEL_VALUE = 0.0f;

    /** Maximum quality score (percentage) */
    public static final double MAX_QUALITY_SCORE = 100.0;

    /** Minimum quality score (percentage) */
    public static final double MIN_QUALITY_SCORE = 0.0;

    /** Bytes per kilobyte conversion factor */
    public static final int BYTES_PER_KILOBYTE = 1024;

    // ========================================
    // L.A.Cosmic Noise Model Parameters
    // ========================================

    /** Gain factor for L.A.Cosmic noise model */
    public static final double LACOSMIC_NOISE_GAIN_FACTOR = 1.5;

    /** Read noise adjustment factor for L.A.Cosmic */
    public static final double LACOSMIC_READ_NOISE_FACTOR = 1.2;

    // ========================================
    // Photometric Calculation Constants
    // ========================================

    /** Magnitude calculation factor (2.5 * log10) for flux to magnitude conversion */
    public static final double MAGNITUDE_CALCULATION_FACTOR = 2.5;

    /** Half-value factor for median and center calculations */
    public static final double HALF_VALUE_FACTOR = 0.5;

    // ========================================
    // Algorithm-Specific Scale Factors
    // ========================================

    /** Minimum scale factor for dark subtraction */
    public static final double MIN_DARK_SCALE_FACTOR = 0.1;

    /** Maximum scale factor for dark subtraction */
    public static final double MAX_DARK_SCALE_FACTOR = 10.0;

    /** Default scale factor for dark subtraction */
    public static final double DEFAULT_DARK_SCALE_FACTOR = 1.0;

    /** Default adaptive window size for dark subtraction (pixels) */
    public static final int DEFAULT_ADAPTIVE_WINDOW_SIZE = 64;

    /** Default adaptive threshold (sigma units) */
    public static final double DEFAULT_ADAPTIVE_THRESHOLD = 3.0;

    /** Default polynomial degree for illumination correction */
    public static final int DEFAULT_POLYNOMIAL_DEGREE = 3;

    // ========================================
    // Median Filter Cosmic Ray Removal
    // ========================================

    /** Default median filter kernel size */
    public static final int DEFAULT_MEDIAN_KERNEL_SIZE = 5;

    /** Default median filter threshold (sigma units) */
    public static final double DEFAULT_MEDIAN_THRESHOLD = 5.0;

    /** Default median filter iterations */
    public static final int DEFAULT_MEDIAN_ITERATIONS = 1;

    // ========================================
    // Robust Statistics
    // ========================================

    /** Outlier rejection threshold (sigma units) */
    public static final double OUTLIER_REJECTION_THRESHOLD = 3.0;

    /** Minimum sigma for robust calculations */
    public static final double MIN_SIGMA_ROBUST = 0.1;

    /** Minimum error floor (magnitudes) */
    public static final double MIN_ERROR_FLOOR = 0.001;

    // ========================================
    // Workflow and Session Management
    // ========================================

    /** Timestamp generation for unique IDs */
    public static final String WORKFLOW_ID_FORMAT = "workflow-%s-%d";

    /** Step output path format */
    public static final String STEP_OUTPUT_PATH_FORMAT = "%s/step-%d/";

    /** Workflow path format without final output */
    public static final String WORKFLOW_PATH_FORMAT = "workflow/%s/step-%d/";

    /** Auto-generated observation ID prefix */
    public static final String AUTO_OBSERVATION_ID_PREFIX = "AUTO-";

    /** Unknown instrument identifier */
    public static final String UNKNOWN_INSTRUMENT = "UNKNOWN";

    /** Auto-generated program ID */
    public static final String AUTO_PROGRAM_ID = "AUTO";

    /** MD5 checksum prefix */
    public static final String CHECKSUM_MD5_PREFIX = "md5:";

    /** Unknown checksum value */
    public static final String CHECKSUM_UNKNOWN = "unknown";
}
