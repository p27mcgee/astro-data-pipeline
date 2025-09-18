package org.stsci.astro.catalog.util;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for astronomical calculations
 */
@Component
@Slf4j
public class AstronomicalCalculations {

    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;
    private static final double ARCSEC_TO_DEG = 1.0 / 3600.0;

    /**
     * Calculate angular distance between two points on the celestial sphere
     * using the spherical law of cosines
     * 
     * @param ra1 Right ascension of first point (degrees)
     * @param dec1 Declination of first point (degrees)
     * @param ra2 Right ascension of second point (degrees)
     * @param dec2 Declination of second point (degrees)
     * @return Angular distance in arcseconds
     */
    public double angularDistance(double ra1, double dec1, double ra2, double dec2) {
        // Convert to radians
        double ra1Rad = ra1 * DEG_TO_RAD;
        double dec1Rad = dec1 * DEG_TO_RAD;
        double ra2Rad = ra2 * DEG_TO_RAD;
        double dec2Rad = dec2 * DEG_TO_RAD;

        // Spherical law of cosines
        double cosDistance = Math.sin(dec1Rad) * Math.sin(dec2Rad) +
                           Math.cos(dec1Rad) * Math.cos(dec2Rad) * Math.cos(ra1Rad - ra2Rad);

        // Handle numerical precision issues
        cosDistance = Math.max(-1.0, Math.min(1.0, cosDistance));

        // Convert back to degrees, then to arcseconds
        return Math.acos(cosDistance) * RAD_TO_DEG * 3600.0;
    }

    /**
     * Calculate angular distance using the haversine formula
     * More numerically stable for small distances
     */
    public double angularDistanceHaversine(double ra1, double dec1, double ra2, double dec2) {
        double ra1Rad = ra1 * DEG_TO_RAD;
        double dec1Rad = dec1 * DEG_TO_RAD;
        double ra2Rad = ra2 * DEG_TO_RAD;
        double dec2Rad = dec2 * DEG_TO_RAD;

        double deltaRa = ra2Rad - ra1Rad;
        double deltaDec = dec2Rad - dec1Rad;

        double a = Math.sin(deltaDec / 2) * Math.sin(deltaDec / 2) +
                  Math.cos(dec1Rad) * Math.cos(dec2Rad) *
                  Math.sin(deltaRa / 2) * Math.sin(deltaRa / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return c * RAD_TO_DEG * 3600.0; // Convert to arcseconds
    }

    /**
     * Convert equatorial coordinates to galactic coordinates
     * 
     * @param ra Right ascension (degrees)
     * @param dec Declination (degrees)
     * @return Array containing [galactic longitude, galactic latitude] in degrees
     */
    public double[] equatorialToGalactic(double ra, double dec) {
        // J2000.0 equatorial coordinates of the galactic north pole
        double raGNP = 192.859508; // degrees
        double decGNP = 27.128336; // degrees
        double lGCP = 32.932; // galactic longitude of celestial pole

        double raRad = ra * DEG_TO_RAD;
        double decRad = dec * DEG_TO_RAD;
        double raGNPRad = raGNP * DEG_TO_RAD;
        double decGNPRad = decGNP * DEG_TO_RAD;

        double sinB = Math.sin(decRad) * Math.sin(decGNPRad) +
                     Math.cos(decRad) * Math.cos(decGNPRad) * Math.cos(raRad - raGNPRad);

        double b = Math.asin(sinB) * RAD_TO_DEG;

        double y = Math.sin(raRad - raGNPRad);
        double x = Math.cos(raRad - raGNPRad) * Math.sin(decGNPRad) -
                  Math.tan(decRad) * Math.cos(decGNPRad);

        double l = (lGCP - Math.atan2(y, x) * RAD_TO_DEG) % 360.0;
        if (l < 0) l += 360.0;

        return new double[]{l, b};
    }

    /**
     * Convert galactic coordinates to equatorial coordinates
     */
    public double[] galacticToEquatorial(double l, double b) {
        // Inverse transformation of equatorialToGalactic
        double raGNP = 192.859508;
        double decGNP = 27.128336;
        double lGCP = 32.932;

        double lRad = l * DEG_TO_RAD;
        double bRad = b * DEG_TO_RAD;
        double lGCPRad = lGCP * DEG_TO_RAD;
        double decGNPRad = decGNP * DEG_TO_RAD;

        double sinDec = Math.sin(bRad) * Math.sin(decGNPRad) +
                       Math.cos(bRad) * Math.cos(decGNPRad) * Math.cos(lGCPRad - lRad);

        double dec = Math.asin(sinDec) * RAD_TO_DEG;

        double y = Math.sin(lGCPRad - lRad);
        double x = Math.cos(lGCPRad - lRad) * Math.sin(decGNPRad) -
                  Math.tan(bRad) * Math.cos(decGNPRad);

        double ra = (raGNP + Math.atan2(y, x) * RAD_TO_DEG) % 360.0;
        if (ra < 0) ra += 360.0;

        return new double[]{ra, dec};
    }

    /**
     * Calculate proper motion corrected position
     * 
     * @param ra0 Initial RA (degrees)
     * @param dec0 Initial declination (degrees)
     * @param pmRa Proper motion in RA (mas/year), includes cos(dec) factor
     * @param pmDec Proper motion in Dec (mas/year)
     * @param epochDiff Time difference in years
     * @return Corrected [RA, Dec] in degrees
     */
    public double[] applyProperMotion(double ra0, double dec0, double pmRa, double pmDec, double epochDiff) {
        // Convert proper motion from mas/year to degrees/year
        double pmRaDeg = pmRa / (3600.0 * 1000.0) * epochDiff;
        double pmDecDeg = pmDec / (3600.0 * 1000.0) * epochDiff;

        double ra = ra0 + pmRaDeg;
        double dec = dec0 + pmDecDeg;

        // Normalize RA to [0, 360)
        ra = ra % 360.0;
        if (ra < 0) ra += 360.0;

        // Clamp declination to [-90, 90]
        dec = Math.max(-90.0, Math.min(90.0, dec));

        return new double[]{ra, dec};
    }

    /**
     * Calculate airmass for given altitude
     * 
     * @param altitude Altitude above horizon (degrees)
     * @return Airmass
     */
    public double calculateAirmass(double altitude) {
        if (altitude <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        double zenithAngle = 90.0 - altitude;
        double zenithRad = zenithAngle * DEG_TO_RAD;

        // Simplified plane-parallel atmosphere model
        return 1.0 / Math.cos(zenithRad);
    }

    /**
     * Calculate more accurate airmass using Kasten-Young formula
     */
    public double calculateAirmassKastenYoung(double altitude) {
        if (altitude <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        return 1.0 / (Math.sin(altitude * DEG_TO_RAD) + 
                      0.50572 * Math.pow(altitude + 6.07995, -1.6364));
    }

    /**
     * Convert flux to magnitude
     * 
     * @param flux Flux measurement
     * @param zeroPoint Photometric zero point
     * @return Magnitude
     */
    public double fluxToMagnitude(double flux, double zeroPoint) {
        if (flux <= 0) {
            return Double.NaN;
        }
        return zeroPoint - 2.5 * Math.log10(flux);
    }

    /**
     * Convert magnitude to flux
     */
    public double magnitudeToFlux(double magnitude, double zeroPoint) {
        return Math.pow(10.0, (zeroPoint - magnitude) / 2.5);
    }

    /**
     * Calculate distance modulus
     * 
     * @param distance Distance in parsecs
     * @return Distance modulus in magnitudes
     */
    public double distanceModulus(double distance) {
        if (distance <= 0) {
            return Double.NaN;
        }
        return 5.0 * Math.log10(distance) - 5.0;
    }

    /**
     * Calculate absolute magnitude from apparent magnitude and distance
     */
    public double absoluteMagnitude(double apparentMagnitude, double distance) {
        return apparentMagnitude - distanceModulus(distance);
    }

    /**
     * Validate astronomical coordinates
     */
    public boolean isValidCoordinates(double ra, double dec) {
        return ra >= 0.0 && ra < 360.0 && dec >= -90.0 && dec <= 90.0;
    }

    /**
     * Normalize RA to [0, 360) range
     */
    public double normalizeRa(double ra) {
        ra = ra % 360.0;
        return ra < 0 ? ra + 360.0 : ra;
    }

    /**
     * Clamp declination to [-90, 90] range
     */
    public double clampDec(double dec) {
        return Math.max(-90.0, Math.min(90.0, dec));
    }
}