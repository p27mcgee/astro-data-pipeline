package com.mcgeecahill.astro.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a detection of an astronomical object
 */
@Entity
@Table(name = "detections", indexes = {
        @Index(name = "idx_detections_object_id", columnList = "object_id"),
        @Index(name = "idx_detections_observation_date", columnList = "observation_date"),
        @Index(name = "idx_detections_magnitude", columnList = "magnitude")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Detection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "object_id", nullable = false)
    private AstronomicalObject astronomicalObject;

    @Column(name = "observation_date", nullable = false)
    private LocalDateTime observationDate;

    @Column(name = "ra", nullable = false)
    private Double ra;

    @Column(name = "decl", nullable = false)
    private Double decl;

    @Column(name = "magnitude")
    private Double magnitude;

    @Column(name = "magnitude_error")
    private Double magnitudeError;

    @Column(name = "filter_name", length = 50)
    private String filterName;

    @Column(name = "instrument", length = 100)
    private String instrument;

    @Column(name = "exposure_time")
    private Double exposureTime;

    @Column(name = "flux")
    private Double flux;

    @Column(name = "flux_error")
    private Double fluxError;

    @Column(name = "sky_background")
    private Double skyBackground;

    @Column(name = "seeing")
    private Double seeing;

    @Column(name = "airmass")
    private Double airmass;

    @Column(name = "quality_flag")
    private Integer qualityFlag;

    @Column(name = "processing_version", length = 50)
    private String processingVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "catalog_name", length = 100)
    private String catalogName;

    /**
     * Calculate signal-to-noise ratio
     */
    public Double getSignalToNoise() {
        if (flux != null && fluxError != null && fluxError > 0) {
            return flux / fluxError;
        }
        return null;
    }

    /**
     * Check if detection has valid photometry
     */
    public boolean hasValidPhotometry() {
        return magnitude != null && magnitudeError != null &&
                magnitude > 0 && magnitudeError > 0 && magnitudeError < 1.0;
    }

    /**
     * Get magnitude with uncertainty as formatted string
     */
    public String getFormattedMagnitude() {
        if (magnitude != null && magnitudeError != null) {
            return String.format("%.3f ± %.3f", magnitude, magnitudeError);
        } else if (magnitude != null) {
            return String.format("%.3f", magnitude);
        }
        return "N/A";
    }
}