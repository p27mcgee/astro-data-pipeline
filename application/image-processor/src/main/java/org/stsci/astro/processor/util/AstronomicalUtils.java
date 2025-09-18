package org.stsci.astro.processor.util;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for astronomical calculations and coordinate transformations
 */
@Slf4j
@Component
public class AstronomicalUtils {

    /**
     * Convert right ascension from degrees to hours
     */
    public double degreesToHours(double degrees) {
        return degrees / 15.0;
    }

    /**
     * Convert right ascension from hours to degrees
     */
    public double hoursToDegrees(double hours) {
        return hours * 15.0;
    }

    /**
     * Convert degrees to arcseconds
     */
    public double degreesToArcseconds(double degrees) {
        return degrees * 3600.0;
    }

    /**
     * Convert arcseconds to degrees
     */
    public double arcsecondsToDegrees(double arcseconds) {
        return arcseconds / 3600.0;
    }

    /**
     * Calculate angular distance between two celestial coordinates
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
        
        double cosDistance = Math.sin(dec1Rad) * Math.sin(dec2Rad) + 
                           Math.cos(dec1Rad) * Math.cos(dec2Rad) * Math.cos(deltaRA);
        
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosDistance))));
    }

    /**
     * Convert equatorial coordinates to galactic coordinates
     * @param ra Right ascension (degrees)
     * @param dec Declination (degrees)
     * @return Galactic coordinates [longitude, latitude] in degrees
     */
    public double[] equatorialToGalactic(double ra, double dec) {
        // Simplified conversion - in reality would need proper epoch handling
        double raRad = Math.toRadians(ra);
        double decRad = Math.toRadians(dec);
        
        // North galactic pole coordinates (J2000)
        double ngpRA = Math.toRadians(192.859508);
        double ngpDec = Math.toRadians(27.128336);
        double galLonAscNode = Math.toRadians(32.932);
        
        double deltaRA = raRad - ngpRA;
        
        double sinB = Math.sin(decRad) * Math.sin(ngpDec) + 
                     Math.cos(decRad) * Math.cos(ngpDec) * Math.cos(deltaRA);
        double galLat = Math.asin(sinB);
        
        double y = Math.cos(decRad) * Math.sin(deltaRA);
        double x = Math.sin(decRad) * Math.cos(ngpDec) - 
                  Math.cos(decRad) * Math.sin(ngpDec) * Math.cos(deltaRA);
        
        double galLon = galLonAscNode - Math.atan2(y, x);
        
        if (galLon < 0) galLon += 2 * Math.PI;
        if (galLon >= 2 * Math.PI) galLon -= 2 * Math.PI;
        
        return new double[]{Math.toDegrees(galLon), Math.toDegrees(galLat)};
    }

    /**
     * Calculate air mass for given altitude
     * @param altitude Altitude in degrees
     * @return Air mass
     */
    public double calculateAirmass(double altitude) {
        if (altitude <= 0) return Double.POSITIVE_INFINITY;
        
        double zenithAngle = 90.0 - altitude;
        double zenithRad = Math.toRadians(zenithAngle);
        
        // Simplified plane-parallel atmosphere model
        return 1.0 / Math.cos(zenithRad);
    }

    /**
     * Calculate Julian Date from year, month, day
     */
    public double calculateJulianDate(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        
        int a = year / 100;
        int b = 2 - a + (a / 4);
        
        return Math.floor(365.25 * (year + 4716)) + 
               Math.floor(30.6001 * (month + 1)) + 
               day + b - 1524.5;
    }

    /**
     * Validate astronomical coordinates
     */
    public boolean isValidCoordinate(double ra, double dec) {
        return ra >= 0 && ra <= 360 && dec >= -90 && dec <= 90;
    }

    /**
     * Normalize right ascension to 0-360 degrees
     */
    public double normalizeRA(double ra) {
        while (ra < 0) ra += 360;
        while (ra >= 360) ra -= 360;
        return ra;
    }

    /**
     * Clamp declination to valid range
     */
    public double clampDeclination(double dec) {
        return Math.max(-90.0, Math.min(90.0, dec));
    }
}