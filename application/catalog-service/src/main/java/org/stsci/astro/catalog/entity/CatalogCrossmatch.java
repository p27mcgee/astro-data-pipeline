package org.stsci.astro.catalog.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a cross-match with external astronomical catalogs
 */
@Entity
@Table(name = "catalog_crossmatches", indexes = {
    @Index(name = "idx_crossmatch_object_id", columnList = "object_id"),
    @Index(name = "idx_crossmatch_catalog", columnList = "catalog_name"),
    @Index(name = "idx_crossmatch_external_id", columnList = "external_catalog_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class CatalogCrossmatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id", nullable = false)
    private AstronomicalObject astronomicalObject;

    @Column(name = "catalog_name", nullable = false, length = 100)
    private String catalogName;

    @Column(name = "external_catalog_id", nullable = false, length = 200)
    private String externalCatalogId;

    @Column(name = "separation_arcsec")
    private Double separationArcsec;

    @Column(name = "match_confidence")
    private Double matchConfidence;

    @Column(name = "external_ra")
    private Double externalRa;

    @Column(name = "external_dec")
    private Double externalDec;

    @Column(name = "external_magnitude")
    private Double externalMagnitude;

    @Column(name = "external_magnitude_band", length = 50)
    private String externalMagnitudeBand;

    @Column(name = "external_proper_motion_ra")
    private Double externalProperMotionRa;

    @Column(name = "external_proper_motion_dec")
    private Double externalProperMotionDec;

    @Column(name = "external_parallax")
    private Double externalParallax;

    @Column(name = "external_radial_velocity")
    private Double externalRadialVelocity;

    @Column(name = "match_method", length = 100)
    private String matchMethod;

    @Column(name = "match_version", length = 50)
    private String matchVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified")
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Check if this is a high-confidence match
     */
    public boolean isHighConfidenceMatch() {
        return matchConfidence != null && matchConfidence > 0.8 && 
               separationArcsec != null && separationArcsec < 1.0;
    }

    /**
     * Get formatted separation
     */
    public String getFormattedSeparation() {
        if (separationArcsec != null) {
            if (separationArcsec < 1.0) {
                return String.format("%.2f\"", separationArcsec);
            } else if (separationArcsec < 60.0) {
                return String.format("%.1f\"", separationArcsec);
            } else {
                return String.format("%.1f'", separationArcsec / 60.0);
            }
        }
        return "N/A";
    }

    /**
     * Get display name for external catalog
     */
    public String getCatalogDisplayName() {
        switch (catalogName.toUpperCase()) {
            case "GAIA_DR3":
                return "Gaia DR3";
            case "2MASS":
                return "2MASS";
            case "WISE":
                return "WISE";
            case "SDSS":
                return "SDSS";
            case "PANSTARRS":
                return "Pan-STARRS";
            case "HSC":
                return "HSC";
            case "SIMBAD":
                return "SIMBAD";
            default:
                return catalogName;
        }
    }
}