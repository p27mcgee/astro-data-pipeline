package com.mcgeecahill.astro.processor.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Utility class for astronomical calculations and coordinate transformations */
@Slf4j
@Component
public class AstronomicalUtils {

    // Conversion constants
    private static final double DEGREES_PER_HOUR = 15.0;
    private static final double ARCSECONDS_PER_DEGREE = 3600.0;
    private static final double DEGREES_PER_CIRCLE = 360.0;
    private static final double MIN_DECLINATION = -90.0;
    private static final double MAX_DECLINATION = 90.0;
    private static final double ZENITH_ANGLE_AT_HORIZON = 90.0;

    // Galactic coordinate system constants (J2000)
    private static final double GALACTIC_NORTH_POLE_RA_DEG = 192.859508;
    private static final double GALACTIC_NORTH_POLE_DEC_DEG = 27.128336;
    private static final double GALACTIC_LONGITUDE_ASCENDING_NODE_DEG = 32.932;

    // Julian date calculation constants
    private static final int JULIAN_YEAR_OFFSET = 4716;
    private static final double JULIAN_DAY_OFFSET = 1524.5;
    private static final double DAYS_PER_YEAR = 365.25;
    private static final double DAYS_PER_MONTH = 30.6001;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int CENTURY_DIVISOR = 100;
    private static final int MONTH_BOUNDARY = 2;

    /** Convert right ascension from degrees to hours */
    public double degreesToHours(double degrees) {
        return degrees / DEGREES_PER_HOUR;
    }

    /** Convert right ascension from hours to degrees */
    public double hoursToDegrees(double hours) {
        return hours * DEGREES_PER_HOUR;
    }

    /** Convert degrees to arcseconds */
    public double degreesToArcseconds(double degrees) {
        return degrees * ARCSECONDS_PER_DEGREE;
    }

    /** Convert arcseconds to degrees */
    public double arcsecondsToDegrees(double arcseconds) {
        return arcseconds / ARCSECONDS_PER_DEGREE;
    }

    /**
     * Calculate angular distance between two celestial coordinates
     *
     * @param ra1 Right ascension of first object (degrees)
     * @param dec1 Declination of first object (degrees)
     * @param ra2 Right ascension of second object (degrees)
     * @param dec2 Declination of second object (degrees)
     * @return Angular distance in degrees
     */
    public double angularDistance(double ra1, double dec1, double ra2, double dec2) {
        double ra1Rad = Math.toRadians(ra1);
        double dec1Rad = Math.toRadians(dec1);
        double ra2Rad = Math.toRadians(ra2);
        double dec2Rad = Math.toRadians(dec2);

        double deltaRA = ra2Rad - ra1Rad;

        double cosDistance =
                Math.sin(dec1Rad) * Math.sin(dec2Rad)
                        + Math.cos(dec1Rad) * Math.cos(dec2Rad) * Math.cos(deltaRA);

        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosDistance))));
    }

    /**
     * Convert equatorial coordinates to galactic coordinates
     *
     * @param ra Right ascension (degrees)
     * @param dec Declination (degrees)
     * @return Galactic coordinates [longitude, latitude] in degrees
     */
    public double[] equatorialToGalactic(double ra, double dec) {
        // Simplified conversion - in reality would need proper epoch handling
        double raRad = Math.toRadians(ra);
        double decRad = Math.toRadians(dec);

        // North galactic pole coordinates (J2000)
        double ngpRA = Math.toRadians(GALACTIC_NORTH_POLE_RA_DEG);
        double ngpDec = Math.toRadians(GALACTIC_NORTH_POLE_DEC_DEG);
        double galLonAscNode = Math.toRadians(GALACTIC_LONGITUDE_ASCENDING_NODE_DEG);

        double deltaRA = raRad - ngpRA;

        double sinB =
                Math.sin(decRad) * Math.sin(ngpDec)
                        + Math.cos(decRad) * Math.cos(ngpDec) * Math.cos(deltaRA);
        double galLat = Math.asin(sinB);

        double y = Math.cos(decRad) * Math.sin(deltaRA);
        double x =
                Math.sin(decRad) * Math.cos(ngpDec)
                        - Math.cos(decRad) * Math.sin(ngpDec) * Math.cos(deltaRA);

        double galLon = galLonAscNode - Math.atan2(y, x);

        if (galLon < 0) {
            galLon += 2 * Math.PI;
        }
        if (galLon >= 2 * Math.PI) {
            galLon -= 2 * Math.PI;
        }

        return new double[] {Math.toDegrees(galLon), Math.toDegrees(galLat)};
    }

    /**
     * Calculate air mass for given altitude
     *
     * @param altitude Altitude in degrees
     * @return Air mass
     */
    public double calculateAirmass(double altitude) {
        if (altitude <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        double zenithAngle = ZENITH_ANGLE_AT_HORIZON - altitude;
        double zenithRad = Math.toRadians(zenithAngle);

        // Simplified plane-parallel atmosphere model
        return 1.0 / Math.cos(zenithRad);
    }

    /** Calculate Julian Date from year, month, day */
    public double calculateJulianDate(int year, int month, int day) {
        if (month <= MONTH_BOUNDARY) {
            year -= 1;
            month += MONTHS_PER_YEAR;
        }

        int a = year / CENTURY_DIVISOR;
        int b = 2 - a + (a / 4);

        return Math.floor(DAYS_PER_YEAR * (year + JULIAN_YEAR_OFFSET))
                + Math.floor(DAYS_PER_MONTH * (month + 1))
                + day
                + b
                - JULIAN_DAY_OFFSET;
    }

    /** Validate astronomical coordinates */
    public boolean isValidCoordinate(double ra, double dec) {
        return ra >= 0
                && ra <= DEGREES_PER_CIRCLE
                && dec >= MIN_DECLINATION
                && dec <= MAX_DECLINATION;
    }

    /** Normalize right ascension to 0-360 degrees */
    public double normalizeRA(double ra) {
        while (ra < 0) {
            ra += DEGREES_PER_CIRCLE;
        }
        while (ra >= DEGREES_PER_CIRCLE) {
            ra -= DEGREES_PER_CIRCLE;
        }
        return ra;
    }

    /** Clamp declination to valid range */
    public double clampDeclination(double dec) {
        return Math.max(MIN_DECLINATION, Math.min(MAX_DECLINATION, dec));
    }
}
