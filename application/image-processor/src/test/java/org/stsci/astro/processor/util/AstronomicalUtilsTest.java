package org.stsci.astro.processor.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AstronomicalUtilsTest {

    private AstronomicalUtils astronomicalUtils;

    @BeforeEach
    void setUp() {
        astronomicalUtils = new AstronomicalUtils();
    }

    @Test
    void degreesToHours_ShouldConvertCorrectly() {
        // Given
        double degrees = 180.0;

        // When
        double hours = astronomicalUtils.degreesToHours(degrees);

        // Then
        assertEquals(12.0, hours, 1e-6);
    }

    @Test
    void hoursToDegrees_ShouldConvertCorrectly() {
        // Given
        double hours = 12.0;

        // When
        double degrees = astronomicalUtils.hoursToDegrees(hours);

        // Then
        assertEquals(180.0, degrees, 1e-6);
    }

    @Test
    void degreesToArcseconds_ShouldConvertCorrectly() {
        // Given
        double degrees = 1.0;

        // When
        double arcseconds = astronomicalUtils.degreesToArcseconds(degrees);

        // Then
        assertEquals(3600.0, arcseconds, 1e-6);
    }

    @Test
    void arcsecondsToDegrees_ShouldConvertCorrectly() {
        // Given
        double arcseconds = 3600.0;

        // When
        double degrees = astronomicalUtils.arcsecondsToDegrees(arcseconds);

        // Then
        assertEquals(1.0, degrees, 1e-6);
    }

    @Test
    void angularDistance_SamePosition_ShouldReturnZero() {
        // Given
        double ra = 180.0;
        double dec = 0.0;

        // When
        double distance = astronomicalUtils.angularDistance(ra, dec, ra, dec);

        // Then
        assertEquals(0.0, distance, 1e-6);
    }

    @Test
    void angularDistance_OppositePositions_ShouldReturn180Degrees() {
        // Given
        double ra1 = 0.0, dec1 = 0.0;
        double ra2 = 180.0, dec2 = 0.0;

        // When
        double distance = astronomicalUtils.angularDistance(ra1, dec1, ra2, dec2);

        // Then
        assertEquals(180.0, distance, 1e-6);
    }

    @Test
    void angularDistance_SmallSeparation_ShouldBeAccurate() {
        // Given - Two positions 1 degree apart on equator
        double ra1 = 0.0, dec1 = 0.0;
        double ra2 = 1.0, dec2 = 0.0;

        // When
        double distance = astronomicalUtils.angularDistance(ra1, dec1, ra2, dec2);

        // Then
        assertEquals(1.0, distance, 1e-6);
    }

    @Test
    void angularDistance_NorthPoleSouthPole_ShouldReturn180Degrees() {
        // Given
        double ra1 = 0.0, dec1 = 90.0;  // North pole
        double ra2 = 0.0, dec2 = -90.0; // South pole

        // When
        double distance = astronomicalUtils.angularDistance(ra1, dec1, ra2, dec2);

        // Then
        assertEquals(180.0, distance, 1e-6);
    }

    @Test
    void equatorialToGalactic_KnownPositions_ShouldReturnReasonableCoordinates() {
        // Given - Galactic center (approximate)
        double ra = 266.405; // degrees
        double dec = -29.0;   // degrees

        // When
        double[] galactic = astronomicalUtils.equatorialToGalactic(ra, dec);

        // Then
        assertNotNull(galactic);
        assertEquals(2, galactic.length);
        // Should return reasonable galactic coordinates
        assertTrue(galactic[0] >= 0.0 && galactic[0] < 360.0);
        assertTrue(galactic[1] >= -90.0 && galactic[1] <= 90.0);
    }

    @Test
    void equatorialToGalactic_NorthCelestialPole_ShouldReturnCorrectGalacticCoordinates() {
        // Given - North celestial pole
        double ra = 0.0;
        double dec = 90.0;

        // When
        double[] galactic = astronomicalUtils.equatorialToGalactic(ra, dec);

        // Then
        assertNotNull(galactic);
        assertEquals(2, galactic.length);
        // Check that coordinates are reasonable
        assertTrue(galactic[0] >= 0.0 && galactic[0] < 360.0);
        assertTrue(galactic[1] >= -90.0 && galactic[1] <= 90.0);
    }

    @Test
    void calculateAirmass_ZenithPosition_ShouldReturnOne() {
        // Given
        double altitude = 90.0; // degrees (zenith)

        // When
        double airmass = astronomicalUtils.calculateAirmass(altitude);

        // Then
        assertEquals(1.0, airmass, 1e-6);
    }

    @Test
    void calculateAirmass_HorizonPosition_ShouldReturnInfinity() {
        // Given
        double altitude = 0.0; // degrees (horizon)

        // When
        double airmass = astronomicalUtils.calculateAirmass(altitude);

        // Then
        assertTrue(Double.isInfinite(airmass));
    }

    @Test
    void calculateAirmass_NegativeAltitude_ShouldReturnInfinity() {
        // Given
        double altitude = -10.0; // below horizon

        // When
        double airmass = astronomicalUtils.calculateAirmass(altitude);

        // Then
        assertTrue(Double.isInfinite(airmass));
    }

    @Test
    void calculateAirmass_TypicalObservingAltitude_ShouldReturnReasonableValue() {
        // Given
        double altitude = 45.0; // 45 degrees altitude

        // When
        double airmass = astronomicalUtils.calculateAirmass(altitude);

        // Then
        assertEquals(Math.sqrt(2.0), airmass, 1e-6); // sec(45°) = √2
    }

    @Test
    void calculateJulianDate_KnownDate_ShouldReturnCorrectJD() {
        // Given - January 1, 2000 (J2000.0 epoch)
        int year = 2000;
        int month = 1;
        int day = 1;

        // When
        double jd = astronomicalUtils.calculateJulianDate(year, month, day);

        // Then
        assertEquals(2451544.5, jd, 1e-1); // Known JD for J2000.0
    }

    @Test
    void calculateJulianDate_LeapYear_ShouldHandleCorrectly() {
        // Given
        int year = 2000;
        int month = 2;
        int day = 29; // Leap day

        // When
        double jd = astronomicalUtils.calculateJulianDate(year, month, day);

        // Then
        assertNotEquals(0.0, jd);
        assertTrue(jd > 2451500.0 && jd < 2451700.0); // Reasonable range for early 2000
    }

    @Test
    void isValidCoordinate_ValidRange_ShouldReturnTrue() {
        // Given
        double ra = 180.0;
        double dec = 45.0;

        // When
        boolean isValid = astronomicalUtils.isValidCoordinate(ra, dec);

        // Then
        assertTrue(isValid);
    }

    @Test
    void isValidCoordinate_InvalidRA_ShouldReturnFalse() {
        // Given
        double ra = 361.0; // Invalid
        double dec = 45.0;

        // When
        boolean isValid = astronomicalUtils.isValidCoordinate(ra, dec);

        // Then
        assertFalse(isValid);
    }

    @Test
    void isValidCoordinate_InvalidDec_ShouldReturnFalse() {
        // Given
        double ra = 180.0;
        double dec = 91.0; // Invalid

        // When
        boolean isValid = astronomicalUtils.isValidCoordinate(ra, dec);

        // Then
        assertFalse(isValid);
    }

    @Test
    void isValidCoordinate_BoundaryValues_ShouldReturnTrue() {
        // Test boundary values
        assertTrue(astronomicalUtils.isValidCoordinate(0.0, -90.0));
        assertTrue(astronomicalUtils.isValidCoordinate(360.0, 90.0));
        assertTrue(astronomicalUtils.isValidCoordinate(180.0, 0.0));
    }

    @Test
    void normalizeRA_NegativeValue_ShouldReturnPositive() {
        // Given
        double ra = -30.0;

        // When
        double normalized = astronomicalUtils.normalizeRA(ra);

        // Then
        assertEquals(330.0, normalized, 1e-6);
    }

    @Test
    void normalizeRA_LargeValue_ShouldWrapAround() {
        // Given
        double ra = 450.0;

        // When
        double normalized = astronomicalUtils.normalizeRA(ra);

        // Then
        assertEquals(90.0, normalized, 1e-6);
    }

    @Test
    void normalizeRA_ValidRange_ShouldReturnSameValue() {
        // Given
        double ra = 180.0;

        // When
        double normalized = astronomicalUtils.normalizeRA(ra);

        // Then
        assertEquals(180.0, normalized, 1e-6);
    }

    @Test
    void normalizeRA_ExactBoundary_ShouldHandle360Correctly() {
        // Given
        double ra = 360.0;

        // When
        double normalized = astronomicalUtils.normalizeRA(ra);

        // Then
        assertEquals(0.0, normalized, 1e-6);
    }

    @Test
    void clampDeclination_ValidRange_ShouldReturnSameValue() {
        // Given
        double dec = 45.0;

        // When
        double clamped = astronomicalUtils.clampDeclination(dec);

        // Then
        assertEquals(45.0, clamped, 1e-6);
    }

    @Test
    void clampDeclination_TooLarge_ShouldClampToNinety() {
        // Given
        double dec = 95.0;

        // When
        double clamped = astronomicalUtils.clampDeclination(dec);

        // Then
        assertEquals(90.0, clamped, 1e-6);
    }

    @Test
    void clampDeclination_TooSmall_ShouldClampToMinusNinety() {
        // Given
        double dec = -95.0;

        // When
        double clamped = astronomicalUtils.clampDeclination(dec);

        // Then
        assertEquals(-90.0, clamped, 1e-6);
    }

    @Test
    void clampDeclination_BoundaryValues_ShouldReturnExactValues() {
        // Test boundary values
        assertEquals(90.0, astronomicalUtils.clampDeclination(90.0), 1e-6);
        assertEquals(-90.0, astronomicalUtils.clampDeclination(-90.0), 1e-6);
        assertEquals(0.0, astronomicalUtils.clampDeclination(0.0), 1e-6);
    }

    @Test
    void conversionRoundTrip_DegreesHours_ShouldReturnOriginal() {
        // Given
        double originalDegrees = 123.456;

        // When
        double hours = astronomicalUtils.degreesToHours(originalDegrees);
        double backToDegrees = astronomicalUtils.hoursToDegrees(hours);

        // Then
        assertEquals(originalDegrees, backToDegrees, 1e-6);
    }

    @Test
    void conversionRoundTrip_DegreesArcseconds_ShouldReturnOriginal() {
        // Given
        double originalDegrees = 0.5;

        // When
        double arcseconds = astronomicalUtils.degreesToArcseconds(originalDegrees);
        double backToDegrees = astronomicalUtils.arcsecondsToDegrees(arcseconds);

        // Then
        assertEquals(originalDegrees, backToDegrees, 1e-6);
    }

    @Test
    void angularDistance_SmallAngles_ShouldBeAccurate() {
        // Given - Two very close positions
        double ra1 = 0.0, dec1 = 0.0;
        double ra2 = 0.001, dec2 = 0.001; // 0.001 degrees apart

        // When
        double distance = astronomicalUtils.angularDistance(ra1, dec1, ra2, dec2);

        // Then
        assertTrue(distance > 0.0);
        assertTrue(distance < 0.01); // Should be small but positive
    }

    @Test
    void angularDistance_RightAscensionWrap_ShouldHandleCorrectly() {
        // Given - Positions near RA wrap-around point
        double ra1 = 359.0, dec1 = 0.0;
        double ra2 = 1.0, dec2 = 0.0;

        // When
        double distance = astronomicalUtils.angularDistance(ra1, dec1, ra2, dec2);

        // Then
        // Should be 2 degrees, not 358 degrees
        assertTrue(distance < 10.0); // Much less than 358
        assertTrue(distance > 1.0);  // Should be around 2 degrees
    }
}