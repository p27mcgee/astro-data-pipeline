package com.mcgeecahill.astro.catalog.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Photometric Calibration Constants
 *
 * <p>This class contains constants for photometric calibration including: - Standard photometric
 * zero points for various filter systems - Atmospheric extinction coefficients - PSF
 * characteristics for different instruments - Color correction parameters - Photometric error model
 * constants
 *
 * <p>Based on STScI HST/JWST calibration standards and standard photometric systems: -
 * Johnson-Cousins UBVRI system - 2MASS JHK system - SDSS ugriz system - HST and JWST filter systems
 */
public final class PhotometryConstants {

    private PhotometryConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ========================================
    // Standard Photometric Zero Points
    // ========================================

    /** Default photometric zero point for unknown filters */
    public static final double DEFAULT_ZERO_POINT = 25.0;

    // Johnson-Cousins System Zero Points
    public static final double ZERO_POINT_U = 22.0;
    public static final double ZERO_POINT_B = 22.5;
    public static final double ZERO_POINT_V = 21.1;
    public static final double ZERO_POINT_R = 21.2;
    public static final double ZERO_POINT_I = 20.5;

    // 2MASS System Zero Points
    public static final double ZERO_POINT_J = 16.8;
    public static final double ZERO_POINT_H = 16.4;
    public static final double ZERO_POINT_K = 16.0;

    // SDSS System Zero Points
    public static final double ZERO_POINT_SDSS_U = 22.5;
    public static final double ZERO_POINT_SDSS_G = 23.3;
    public static final double ZERO_POINT_SDSS_R = 22.7;
    public static final double ZERO_POINT_SDSS_I = 22.4;
    public static final double ZERO_POINT_SDSS_Z = 21.3;

    // HST Filter Zero Points
    public static final double ZERO_POINT_F555W = 25.7;
    public static final double ZERO_POINT_F814W = 25.1;
    public static final double ZERO_POINT_F606W = 26.1;

    // JWST Filter Zero Points
    public static final double ZERO_POINT_F090W = 28.1;
    public static final double ZERO_POINT_F150W = 28.2;
    public static final double ZERO_POINT_F200W = 28.5;

    // ========================================
    // Atmospheric Extinction Coefficients
    // (magnitudes per airmass)
    // ========================================

    /** Default extinction coefficient for unknown filters */
    public static final double DEFAULT_EXTINCTION_COEFF = 0.15;

    public static final double EXTINCTION_COEFF_U = 0.60;
    public static final double EXTINCTION_COEFF_B = 0.40;
    public static final double EXTINCTION_COEFF_V = 0.20;
    public static final double EXTINCTION_COEFF_R = 0.10;
    public static final double EXTINCTION_COEFF_I = 0.08;
    public static final double EXTINCTION_COEFF_J = 0.05;
    public static final double EXTINCTION_COEFF_H = 0.03;
    public static final double EXTINCTION_COEFF_K = 0.02;

    /** Reference airmass for extinction correction */
    public static final double REFERENCE_AIRMASS = 1.0;

    // ========================================
    // Aperture Correction Constants
    // Following STScI encircled energy methodology
    // ========================================

    /** Aperture to PSF ratio threshold 1 (very small apertures) */
    public static final double APERTURE_RATIO_THRESHOLD_1 = 0.5;

    /** Aperture to PSF ratio threshold 2 (small apertures) */
    public static final double APERTURE_RATIO_THRESHOLD_2 = 1.0;

    /** Aperture to PSF ratio threshold 3 (medium apertures) */
    public static final double APERTURE_RATIO_THRESHOLD_3 = 2.0;

    /** Aperture to PSF ratio threshold 4 (large apertures) */
    public static final double APERTURE_RATIO_THRESHOLD_4 = 4.0;

    /** Encircled energy for very small apertures (ratio < 0.5) */
    public static final double ENCIRCLED_ENERGY_COEFF_SMALL = 0.1;

    /** Encircled energy for small apertures (ratio < 1.0) */
    public static final double ENCIRCLED_ENERGY_COEFF_MEDIUM = 0.4;

    /** Encircled energy base for medium apertures (ratio 1-2) */
    public static final double ENCIRCLED_ENERGY_BASE_MEDIUM = 0.6;

    /** Encircled energy slope for medium apertures */
    public static final double ENCIRCLED_ENERGY_SLOPE_MEDIUM = 0.3;

    /** Encircled energy base for large apertures (ratio 2-4) */
    public static final double ENCIRCLED_ENERGY_BASE_LARGE = 0.9;

    /** Encircled energy slope for large apertures */
    public static final double ENCIRCLED_ENERGY_SLOPE_LARGE = 0.08;

    /** Maximum encircled energy (very large apertures) */
    public static final double ENCIRCLED_ENERGY_MAX = 0.98;

    /** Magnitude conversion factor for aperture correction */
    public static final double APERTURE_MAG_CONVERSION = -2.5;

    // ========================================
    // PSF FWHM by Instrument Type (pixels)
    // ========================================

    /** HST typical PSF FWHM */
    public static final double PSF_FWHM_HST = 2.5;

    /** JWST typical PSF FWHM */
    public static final double PSF_FWHM_JWST = 3.0;

    /** Ground-based telescope typical PSF FWHM */
    public static final double PSF_FWHM_GROUND = 4.0;

    /** HST filter name pattern length */
    public static final int HST_FILTER_NAME_LENGTH = 5;

    // ========================================
    // Color Correction Thresholds
    // Based on STScI color transformation equations
    // ========================================

    /** Magnitude threshold for bright star color correction */
    public static final double COLOR_BRIGHT_MAG_THRESHOLD = 15.0;

    /** Magnitude threshold for medium brightness color correction */
    public static final double COLOR_MEDIUM_MAG_THRESHOLD = 20.0;

    /** Color correction for bright stars (< 15 mag) */
    public static final double COLOR_CORRECTION_BRIGHT = 0.02;

    /** Color correction for medium stars (15-20 mag) */
    public static final double COLOR_CORRECTION_MEDIUM = 0.01;

    /** Color correction for faint stars (> 20 mag) */
    public static final double COLOR_CORRECTION_FAINT = 0.00;

    // ========================================
    // Photometric Error Model Constants
    // Based on STScI error analysis methodology
    // ========================================

    /** Poisson error exponent (square root for photon statistics) */
    public static final double POISSON_ERROR_EXPONENT = 0.5;

    /** Sky background error coefficient */
    public static final double SKY_ERROR_COEFFICIENT = 0.01;

    /** Atmospheric variability error per airmass unit */
    public static final double ATMOSPHERIC_ERROR_PER_AIRMASS = 0.005;

    /** Typical systematic photometric error */
    public static final double SYSTEMATIC_ERROR_TYPICAL = 0.01;

    /** Minimum photometric error floor */
    public static final double MIN_PHOTOMETRIC_ERROR = 0.001;

    /** Sky area calculation factor (π/4 for circular aperture) */
    public static final double SKY_AREA_CIRCLE_FACTOR = Math.PI / 4.0;

    // ========================================
    // Synthetic Photometry Constants
    // ========================================

    /** Trapezoidal integration factor */
    public static final double TRAPEZOIDAL_FACTOR = 0.5;

    /** Magnitude to synthetic flux conversion factor */
    public static final double SYNTHETIC_MAG_CONVERSION = -2.5;

    // ========================================
    // Filter Transmission Curve Parameters
    // (simplified Gaussian approximations)
    // ========================================

    /** FWHM to sigma conversion for Gaussian PSF */
    public static final double FWHM_TO_SIGMA = 2.35;

    /** Gaussian exponent factor */
    public static final double GAUSSIAN_EXPONENT_FACTOR = -0.5;

    // Central Wavelengths (Angstroms)
    public static final double WAVELENGTH_U = 3600.0;
    public static final double WAVELENGTH_B = 4400.0;
    public static final double WAVELENGTH_V = 5500.0;
    public static final double WAVELENGTH_R = 6400.0;
    public static final double WAVELENGTH_I = 8000.0;
    public static final double WAVELENGTH_J = 12500.0;
    public static final double WAVELENGTH_H = 16500.0;
    public static final double WAVELENGTH_K = 22000.0;

    /** Default central wavelength for unknown filters (Angstroms) */
    public static final double WAVELENGTH_DEFAULT = 5500.0;

    // Bandwidths (Angstroms)
    public static final double BANDWIDTH_U = 600.0;
    public static final double BANDWIDTH_B = 1000.0;
    public static final double BANDWIDTH_V = 900.0;
    public static final double BANDWIDTH_R = 1200.0;
    public static final double BANDWIDTH_I = 1500.0;
    public static final double BANDWIDTH_J = 2500.0;
    public static final double BANDWIDTH_H = 3000.0;
    public static final double BANDWIDTH_K = 4000.0;

    /** Default bandwidth for unknown filters (Angstroms) */
    public static final double BANDWIDTH_DEFAULT = 1000.0;

    // ========================================
    // Instrument-Specific Gain Corrections
    // Based on STScI instrument characteristics
    // ========================================

    /** WFC3/WFPC2 (HST) gain correction factor */
    public static final double GAIN_HST_WFC = 2.0;

    /** NIRCAM/MIRI (JWST) gain correction factor */
    public static final double GAIN_JWST_IR = 1.5;

    /** Generic CCD gain correction factor */
    public static final double GAIN_GENERIC_CCD = 1.0;

    /** Narrowband filter gain multiplier */
    public static final double GAIN_NARROWBAND_MULTIPLIER = 1.2;

    /** Exposure time normalization exponent (square root) */
    public static final double EXPOSURE_TIME_EXPONENT = 0.5;

    // ========================================
    // Helper Methods for Map Initialization
    // ========================================

    /** Get standard photometric zero points map */
    public static Map<String, Double> getStandardZeroPoints() {
        Map<String, Double> zeroPoints = new HashMap<>();

        // Johnson-Cousins
        zeroPoints.put("U", ZERO_POINT_U);
        zeroPoints.put("B", ZERO_POINT_B);
        zeroPoints.put("V", ZERO_POINT_V);
        zeroPoints.put("R", ZERO_POINT_R);
        zeroPoints.put("I", ZERO_POINT_I);

        // 2MASS
        zeroPoints.put("J", ZERO_POINT_J);
        zeroPoints.put("H", ZERO_POINT_H);
        zeroPoints.put("K", ZERO_POINT_K);

        // SDSS
        zeroPoints.put("u", ZERO_POINT_SDSS_U);
        zeroPoints.put("g", ZERO_POINT_SDSS_G);
        zeroPoints.put("r", ZERO_POINT_SDSS_R);
        zeroPoints.put("i", ZERO_POINT_SDSS_I);
        zeroPoints.put("z", ZERO_POINT_SDSS_Z);

        // HST
        zeroPoints.put("F555W", ZERO_POINT_F555W);
        zeroPoints.put("F814W", ZERO_POINT_F814W);
        zeroPoints.put("F606W", ZERO_POINT_F606W);

        // JWST
        zeroPoints.put("F090W", ZERO_POINT_F090W);
        zeroPoints.put("F150W", ZERO_POINT_F150W);
        zeroPoints.put("F200W", ZERO_POINT_F200W);

        return zeroPoints;
    }

    /** Get extinction coefficients map */
    public static Map<String, Double> getExtinctionCoefficients() {
        Map<String, Double> coefficients = new HashMap<>();

        coefficients.put("U", EXTINCTION_COEFF_U);
        coefficients.put("B", EXTINCTION_COEFF_B);
        coefficients.put("V", EXTINCTION_COEFF_V);
        coefficients.put("R", EXTINCTION_COEFF_R);
        coefficients.put("I", EXTINCTION_COEFF_I);
        coefficients.put("J", EXTINCTION_COEFF_J);
        coefficients.put("H", EXTINCTION_COEFF_H);
        coefficients.put("K", EXTINCTION_COEFF_K);

        return coefficients;
    }

    /** Get central wavelengths map (Angstroms) */
    public static Map<String, Double> getCentralWavelengths() {
        Map<String, Double> wavelengths = new HashMap<>();

        wavelengths.put("U", WAVELENGTH_U);
        wavelengths.put("B", WAVELENGTH_B);
        wavelengths.put("V", WAVELENGTH_V);
        wavelengths.put("R", WAVELENGTH_R);
        wavelengths.put("I", WAVELENGTH_I);
        wavelengths.put("J", WAVELENGTH_J);
        wavelengths.put("H", WAVELENGTH_H);
        wavelengths.put("K", WAVELENGTH_K);

        return wavelengths;
    }

    /** Get bandwidths map (Angstroms) */
    public static Map<String, Double> getBandwidths() {
        Map<String, Double> bandwidths = new HashMap<>();

        bandwidths.put("U", BANDWIDTH_U);
        bandwidths.put("B", BANDWIDTH_B);
        bandwidths.put("V", BANDWIDTH_V);
        bandwidths.put("R", BANDWIDTH_R);
        bandwidths.put("I", BANDWIDTH_I);
        bandwidths.put("J", BANDWIDTH_J);
        bandwidths.put("H", BANDWIDTH_H);
        bandwidths.put("K", BANDWIDTH_K);

        return bandwidths;
    }
}
