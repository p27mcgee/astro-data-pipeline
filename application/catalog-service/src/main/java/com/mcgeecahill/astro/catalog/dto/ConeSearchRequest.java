package com.mcgeecahill.astro.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for cone search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConeSearchRequest {

    @NotNull(message = "Right ascension is required")
    @DecimalMin(value = "0.0", message = "RA must be >= 0.0")
    @DecimalMax(value = "360.0", message = "RA must be <= 360.0")
    private Double ra;

    @NotNull(message = "Declination is required")
    @DecimalMin(value = "-90.0", message = "Dec must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Dec must be <= 90.0")
    private Double decl;

    @NotNull(message = "Search radius is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Radius must be > 0.0")
    @DecimalMax(value = "3600.0", message = "Radius must be <= 3600.0 arcseconds")
    private Double radiusArcsec;

    @Min(value = 1, message = "Max results must be at least 1")
    @Max(value = 10000, message = "Max results cannot exceed 10000")
    @Builder.Default
    private Integer maxResults = 1000;

    private Double minMagnitude;

    private Double maxMagnitude;

    private String catalogName;

    private String objectType;

    private List<String> objectTypes;

    private String filterName;

    @Builder.Default
    private Boolean includeDetections = false;

    @Builder.Default
    private Boolean includeCrossmatches = false;

    private org.springframework.data.domain.Pageable pageable;

    /**
     * Convert radius from arcseconds to degrees
     */
    public Double getRadiusDegrees() {
        return radiusArcsec != null ? radiusArcsec / 3600.0 : null;
    }

    /**
     * Validate coordinate ranges
     */
    public boolean isValidCoordinates() {
        return ra != null && decl != null &&
                ra >= 0.0 && ra <= 360.0 &&
                decl >= -90.0 && decl <= 90.0;
    }

    /**
     * Get formatted coordinates string
     */
    public String getFormattedCoordinates() {
        if (ra != null && decl != null) {
            return String.format("RA=%.6f°, Dec=%.6f°", ra, decl);
        }
        return "Invalid coordinates";
    }

    /**
     * Convert RA from degrees to hours
     */
    public Double getRaHours() {
        return ra != null ? ra / 15.0 : null;
    }

    /**
     * Get search area in square degrees
     */
    public Double getSearchAreaSquareDegrees() {
        if (radiusArcsec != null) {
            double radiusDeg = radiusArcsec / 3600.0;
            return Math.PI * radiusDeg * radiusDeg;
        }
        return null;
    }

    // Compatibility getters for service layer
    public Double getCenterRa() {
        return ra;
    }

    public Double getCenterDec() {
        return decl;
    }
}