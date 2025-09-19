package org.stsci.astro.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "astronomical_objects", indexes = {
    @Index(name = "idx_objects_radecl", columnList = "ra, decl"),
    @Index(name = "idx_objects_type", columnList = "object_type"),
    @Index(name = "idx_objects_magnitude", columnList = "magnitude"),
    @Index(name = "idx_objects_type_magnitude", columnList = "object_type, magnitude")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"detections", "crossmatches"})
@ToString(exclude = {"detections", "crossmatches"})
public class AstronomicalObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_id", unique = true, nullable = false, length = 64)
    private String objectId;

    // Classification
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false)
    private ObjectType objectType;

    @Column(name = "classification_confidence")
    @Builder.Default
    private Double classificationConfidence = 0.5;

    // Position (J2000 epoch)
    @Column(name = "ra", nullable = false)
    private Double ra; // Right Ascension in degrees

    @Column(name = "decl", nullable = false)
    private Double decl; // Declination in degrees

    @Column(name = "ra_error_mas")
    private Double raErrorMas; // milliarcseconds

    @Column(name = "decl_error_mas")
    private Double declErrorMas;

    @Column(name = "position", columnDefinition = "geometry(Point,4326)")
    private Point position; // PostGIS geometry

    // Proper motion (mas/year)
    @Column(name = "pm_ra")
    @Builder.Default
    private Double pmRa = 0.0;

    @Column(name = "pm_decl")
    @Builder.Default
    private Double pmDecl = 0.0;

    @Column(name = "pm_ra_error")
    private Double pmRaError;

    @Column(name = "pm_decl_error")
    private Double pmDeclError;

    // Parallax
    @Column(name = "parallax_mas")
    private Double parallaxMas;

    @Column(name = "parallax_error_mas")
    private Double parallaxErrorMas;

    @Column(name = "distance_pc")
    private Double distancePc; // Derived from parallax

    // Photometry
    @Column(name = "magnitude")
    private Double magnitude;

    @Column(name = "magnitude_error")
    private Double magnitudeError;

    @Enumerated(EnumType.STRING)
    @Column(name = "photometric_system")
    private PhotometricSystem photometricSystem;

    // Multi-band photometry stored as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photometry")
    private Map<String, Object> photometry;

    // Physical properties
    @Column(name = "effective_temperature")
    private Double effectiveTemperature; // Kelvin

    @Column(name = "surface_gravity")
    private Double surfaceGravity; // log g

    @Column(name = "metallicity")
    private Double metallicity; // [Fe/H]

    // Variability
    @Column(name = "is_variable")
    @Builder.Default
    private Boolean isVariable = false;

    @Column(name = "variability_period_days")
    private Double variabilityPeriodDays;

    @Column(name = "variability_amplitude")
    private Double variabilityAmplitude;

    @Column(name = "variability_type", length = 50)
    private String variabilityType;

    // Cross-matching with catalogs
    @Column(name = "gaia_source_id")
    private Long gaiaSourceId;

    @Column(name = "simbad_name", length = 200)
    private String simbadName;

    @Column(name = "ned_name", length = 200)
    private String nedName;

    @Column(name = "usno_id", length = 50)
    private String usnoId;

    @Column(name = "tycho_id", length = 50)
    private String tychoId;

    @Column(name = "name", length = 200)
    private String name;

    // Detection and measurement details
    @Column(name = "detection_significance")
    private Double detectionSignificance;

    @Column(name = "flux_auto")
    private Double fluxAuto; // Automatic aperture flux

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "flux_aper")
    private double[] fluxAper; // Aperture photometry array

    @Column(name = "flux_psf")
    private Double fluxPsf; // PSF photometry

    // Shape parameters
    @Column(name = "fwhm_arcsec")
    private Double fwhmArcsec;

    @Column(name = "elongation")
    private Double elongation;

    @Column(name = "ellipticity")
    private Double ellipticity;

    @Column(name = "position_angle_deg")
    private Double positionAngleDeg;

    // Quality flags
    @Column(name = "quality_flags")
    @Builder.Default
    private Integer qualityFlags = 0;

    @Column(name = "source_flags")
    @Builder.Default
    private Integer sourceFlags = 0;

    // Metadata
    @Column(name = "first_observed")
    private LocalDateTime firstObserved;

    @Column(name = "last_observed")
    private LocalDateTime lastObserved;

    @Column(name = "observation_count")
    @Builder.Default
    private Integer observationCount = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "astronomicalObject", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Detection> detections;

    @OneToMany(mappedBy = "astronomicalObject", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CatalogCrossmatch> crossmatches;

    // Enums
    public enum ObjectType {
        STAR,
        GALAXY,
        NEBULA,
        QUASAR,
        ASTEROID,
        COMET,
        PLANET,
        SATELLITE,
        COSMIC_RAY,
        ARTIFACT,
        UNKNOWN
    }

    public enum PhotometricSystem {
        JOHNSON_UBV,
        SDSS_ugriz,
        HST_WFC3,
        JWST_NIRCam,
        GAIA_GBP_G_GRP,
        AB_MAGNITUDE,
        VEGA_MAGNITUDE
    }

    // Convenience methods
    public boolean isPointSource() {
        return objectType == ObjectType.STAR || objectType == ObjectType.QUASAR;
    }

    public boolean isExtendedSource() {
        return objectType == ObjectType.GALAXY || objectType == ObjectType.NEBULA;
    }

    public boolean hasPhotometry() {
        return magnitude != null || (photometry != null && !photometry.isEmpty());
    }

    public boolean hasAstrometry() {
        return ra != null && decl != null;
    }

    public boolean hasParallax() {
        return parallaxMas != null && parallaxMas > 0;
    }

    public boolean hasProperMotion() {
        return (pmRa != null && Math.abs(pmRa) > 0) || (pmDecl != null && Math.abs(pmDecl) > 0);
    }

    public Double getApparentMagnitude() {
        return magnitude;
    }

    public Double getAbsoluteMagnitude() {
        if (magnitude != null && distancePc != null && distancePc > 0) {
            return magnitude - 5 * Math.log10(distancePc / 10.0);
        }
        return null;
    }

    public String getFormattedCoordinates() {
        if (ra != null && decl != null) {
            return String.format("RA: %.6f°, Decl: %.6f°", ra, decl);
        }
        return "No coordinates";
    }

    public void updateObservationStats(LocalDateTime observationTime) {
        if (firstObserved == null || observationTime.isBefore(firstObserved)) {
            firstObserved = observationTime;
        }
        if (lastObserved == null || observationTime.isAfter(lastObserved)) {
            lastObserved = observationTime;
        }
        observationCount = (observationCount == null ? 0 : observationCount) + 1;
    }
}