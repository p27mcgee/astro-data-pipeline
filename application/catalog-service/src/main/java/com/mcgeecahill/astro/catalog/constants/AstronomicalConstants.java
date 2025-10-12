package com.mcgeecahill.astro.catalog.constants;

/**
 * Physical and Mathematical Constants for Astronomical Calculations
 *
 * <p>This class contains fundamental constants used throughout the astronomical data processing
 * pipeline. All constants are based on IAU standards and STScI best practices.
 *
 * <p>References: - IAU Resolution A1 (2000): Celestial coordinate systems - IAU Resolution B1
 * (2000): Time scales and coordinate systems - USNO Circular 179: Astrometric Standards
 */
public final class AstronomicalConstants {

    private AstronomicalConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Angular Conversions
    // ========================================

    /** Conversion factor from degrees to radians */
    public static final double DEG_TO_RAD = Math.PI / 180.0;

    /** Conversion factor from radians to degrees */
    public static final double RAD_TO_DEG = 180.0 / Math.PI;

    /** Conversion factor from arcseconds to degrees */
    public static final double ARCSEC_TO_DEG = 1.0 / 3600.0;

    /** Conversion factor from arcminutes to arcseconds */
    public static final double ARCSECONDS_PER_ARCMINUTE = 60.0;

    /** Conversion factor from degrees to arcseconds */
    public static final double ARCSECONDS_PER_DEGREE = 3600.0;

    /** Conversion factor from milliarcseconds to degrees */
    public static final double MILLIARCSEC_PER_DEG = 3600000.0;

    /** Full circle in degrees */
    public static final double DEGREES_PER_CIRCLE = 360.0;

    /** Full circle in radians */
    public static final double TWO_PI = 2.0 * Math.PI;

    // ========================================
    // Coordinate System Limits
    // ========================================

    /** Minimum valid declination (degrees) */
    public static final double MIN_DECLINATION = -90.0;

    /** Maximum valid declination (degrees) */
    public static final double MAX_DECLINATION = 90.0;

    /** Zenith angle at horizon (degrees) */
    public static final double ZENITH_ANGLE_AT_HORIZON = 90.0;

    /** Numerical precision clamping minimum value */
    public static final double MIN_COSINE = -1.0;

    /** Numerical precision clamping maximum value */
    public static final double MAX_COSINE = 1.0;

    // ========================================
    // Galactic Coordinate System (J2000)
    // ========================================

    /** Right ascension of galactic north pole (degrees, J2000) */
    public static final double GALACTIC_NORTH_POLE_RA_DEG = 192.859508;

    /** Declination of galactic north pole (degrees, J2000) */
    public static final double GALACTIC_NORTH_POLE_DEC_DEG = 27.128336;

    /** Galactic longitude of celestial pole ascending node (degrees) */
    public static final double GALACTIC_LONGITUDE_ASCENDING_NODE_DEG = 32.932;

    // ========================================
    // Epoch Constants
    // ========================================

    /** Julian epoch J2000.0 */
    public static final double JULIAN_EPOCH_J2000 = 2000.0;

    /** Julian epoch B1950.0 */
    public static final double JULIAN_EPOCH_B1950 = 1950.0;

    /** Current epoch (approximate) */
    public static final double JULIAN_EPOCH_CURRENT = 2024.0;

    /** Years per century */
    public static final double CENTURIES_PER_HUNDRED_YEARS = 100.0;

    // ========================================
    // Physical Constants
    // ========================================

    /** Speed of light (meters per second) */
    public static final double SPEED_OF_LIGHT_M_PER_S = 299792458.0;

    /** Earth equatorial radius (meters) */
    public static final double EARTH_RADIUS_METERS = 6378137.0;

    /** Earth rotation rate (radians per second) */
    public static final double EARTH_ROTATION_RATE_RAD_PER_S = 7.2921159e-5;

    /** Approximate meters per degree at equator (for PostGIS calculations) */
    public static final double DEGREES_TO_METERS = 111319.9;

    /** Hours in a day. */
    public static final double HOURS_PER_DAY = 24.0;

    // ========================================
    // Astrometric Correction Constants
    // ========================================

    /** Annual aberration constant (arcseconds) */
    public static final double ANNUAL_ABERRATION_CONSTANT_ARCSEC = 20.49552;

    /** Einstein gravitational light deflection at solar limb (arcseconds) */
    public static final double EINSTEIN_DEFLECTION_CONSTANT_ARCSEC = 1.75;

    /** Median Absolute Deviation to standard deviation conversion factor */
    public static final double MAD_TO_SIGMA_CONVERSION_FACTOR = 1.4826;

    // ========================================
    // Precession Constants (IAU 2000A model)
    // All values in arcseconds per century
    // ========================================

    public static final double PRECESSION_ZETA_BASE = 2306.2181;
    public static final double PRECESSION_ZETA_T1 = 1.39656;
    public static final double PRECESSION_ZETA_T2 = 0.000139;
    public static final double PRECESSION_ZETA_T3_DT2 = 0.30188;
    public static final double PRECESSION_ZETA_T4_DT2 = 0.000344;
    public static final double PRECESSION_ZETA_T5_DT3 = 0.017998;

    public static final double PRECESSION_Z_T3_DT2 = 1.09468;
    public static final double PRECESSION_Z_T4_DT2 = 0.000066;
    public static final double PRECESSION_Z_T5_DT3 = 0.018203;

    public static final double PRECESSION_THETA_BASE = 2004.3109;
    public static final double PRECESSION_THETA_T1 = 0.85330;
    public static final double PRECESSION_THETA_T2 = 0.000217;
    public static final double PRECESSION_THETA_T3_DT2 = 0.42665;
    public static final double PRECESSION_THETA_T5_DT3 = 0.041833;

    // ========================================
    // Nutation Constants
    // ========================================

    /** Nutation in longitude amplitude (arcseconds) */
    public static final double NUTATION_LONGITUDE_AMPLITUDE_ARCSEC = 17.2;

    /** Nutation in obliquity amplitude (arcseconds) */
    public static final double NUTATION_OBLIQUITY_AMPLITUDE_ARCSEC = 9.2;

    /** Nutation period base angle (degrees) */
    public static final double NUTATION_PERIOD_BASE_DEG = 125.0;

    /** Nutation period rate (degrees per century) */
    public static final double NUTATION_PERIOD_RATE = 1934.1;

    // ========================================
    // Atmospheric Refraction Constants (Bennett 1982 / USNO)
    // ========================================

    /** Basic refraction coefficient 1 */
    public static final double REFRACTION_BASIC_COEFF_1 = 58.1;

    /** Basic refraction coefficient 2 */
    public static final double REFRACTION_BASIC_COEFF_2 = 0.07;

    /** Basic refraction coefficient 3 */
    public static final double REFRACTION_BASIC_COEFF_3 = 0.000086;

    /** Standard atmospheric pressure (millibars) */
    public static final double REFRACTION_STANDARD_PRESSURE_MB = 1013.25;

    /** Standard temperature (Kelvin) */
    public static final double REFRACTION_STANDARD_TEMP_K = 283.0;

    /** Humidity correction factor */
    public static final double REFRACTION_HUMIDITY_FACTOR = 0.0001;

    /** Wavelength dispersion correction factor */
    public static final double REFRACTION_WAVELENGTH_FACTOR = 0.00013;

    /** Reference wavelength (micrometers) */
    public static final double REFRACTION_WAVELENGTH_REF = 0.55;

    /** Minimum reliable altitude for refraction calculation (degrees) */
    public static final double REFRACTION_MIN_ALTITUDE_DEG = 3.0;

    // ========================================
    // Kasten-Young Airmass Formula Constants
    // ========================================

    /** Kasten-Young coefficient 1 */
    public static final double KY_AIRMASS_COEFF_1 = 0.50572;

    /** Kasten-Young coefficient 2 (degrees) */
    public static final double KY_AIRMASS_COEFF_2 = 6.07995;

    /** Kasten-Young exponent */
    public static final double KY_AIRMASS_EXPONENT = 1.6364;

    // ========================================
    // Magnitude and Flux Conversions
    // ========================================

    /** Magnitude to flux conversion factor */
    public static final double MAGNITUDE_FLUX_RATIO = 2.5;

    /** Magnitude distance modulus factor */
    public static final double MAGNITUDE_DISTANCE_FACTOR = 5.0;

    /** Distance modulus offset */
    public static final double DISTANCE_MODULUS_OFFSET = 5.0;

    // ========================================
    // Proper Motion Constants
    // ========================================

    /** Perspective acceleration divisor (conversion factor for km/s to mas/year²) */
    public static final double PERSPECTIVE_ACCEL_DIVISOR = 977813.0;

    /** Minimum parallax for perspective acceleration correction (mas) - corresponds to 100 pc */
    public static final double MIN_PARALLAX_FOR_CORRECTION_MAS = 10.0;

    /** Minimum parallax for proper motion application (mas) - corresponds to 1000 pc */
    public static final double MIN_PARALLAX_FOR_MOTION_MAS = 1.0;

    /** Parsecs to parallax conversion factor (distance in pc = 1000 / parallax in mas) */
    public static final double PARSECS_TO_PARALLAX_CONVERSION = 1000.0;

    // ========================================
    // Cross-matching Constants
    // ========================================

    /** Poisson spatial coincidence weight */
    public static final double POISSON_SPATIAL_WEIGHT = 1.0;

    /** Gaussian magnitude difference sigma (magnitudes) */
    public static final double GAUSSIAN_MAGNITUDE_SIGMA = 0.2;

    /** Systematic positional error factor */
    public static final double SYSTEMATIC_ERROR_FACTOR = 0.1;

    /** Minimum catalog density threshold (objects per square arcminute) */
    public static final double MIN_CATALOG_DENSITY = 0.1;

    /** Magnitude consistency threshold (magnitudes) */
    public static final double MAGNITUDE_CONSISTENCY_THRESHOLD = 0.3;

    /** Default cross-match radius (arcseconds) */
    public static final double DEFAULT_MATCH_RADIUS_ARCSEC = 1.0;

    // ========================================
    // Coordinate System Identifiers
    // ========================================

    /** SRID for WGS84 coordinate system (PostGIS) */
    public static final int SRID_WGS84 = 4326;

    // ========================================
    // Epoch Transformation Offsets
    // ========================================

    /** Average RA offset for B1950 to J2000 transformation (arcseconds) */
    public static final double B1950_TO_J2000_RA_OFFSET_ARCSEC = 0.640;

    /** Average Dec offset for B1950 to J2000 transformation (arcseconds) */
    public static final double B1950_TO_J2000_DEC_OFFSET_ARCSEC = 0.280;

    // ========================================
    // Solar Position Constants
    // ========================================

    /** Solar mean longitude base (degrees) */
    public static final double SOLAR_MEAN_LONGITUDE_BASE = 280.46646;

    /** Solar mean longitude T1 coefficient (degrees per century) */
    public static final double SOLAR_MEAN_LONGITUDE_T1 = 36000.76983;

    /** Solar mean longitude T2 coefficient (degrees per century²) */
    public static final double SOLAR_MEAN_LONGITUDE_T2 = 0.0003032;

    /** Solar mean anomaly base (degrees) */
    public static final double SOLAR_MEAN_ANOMALY_BASE = 357.52911;

    /** Solar mean anomaly T1 coefficient (degrees per century) */
    public static final double SOLAR_MEAN_ANOMALY_T1 = 35999.05029;

    /** Solar mean anomaly T2 coefficient (degrees per century²) */
    public static final double SOLAR_MEAN_ANOMALY_T2 = 0.0001537;

    /** Solar equation of center coefficient 1 */
    public static final double SOLAR_EQUATION_CENTER_1 = 1.914602;

    /** Solar equation of center coefficient 2 */
    public static final double SOLAR_EQUATION_CENTER_2 = 0.019993;

    /** Ecliptic obliquity base (degrees) */
    public static final double ECLIPTIC_OBLIQUITY_BASE = 23.439291;

    /** Ecliptic obliquity rate (degrees per century) */
    public static final double ECLIPTIC_OBLIQUITY_RATE = 0.0130042;

    /** Maximum angular distance for solar deflection calculation (degrees) */
    public static final double SOLAR_DEFLECTION_MAX_DISTANCE_DEG = 90.0;

    // ========================================
    // Statistical Constants
    // ========================================

    /** Euler's number reciprocal (for autocorrelation calculations) */
    public static final double E_RECIPROCAL = 1.0 / Math.E;

    /** Exponential decay factor for match confidence */
    public static final double MATCH_CONFIDENCE_DECAY_FACTOR = 2.0;
}
