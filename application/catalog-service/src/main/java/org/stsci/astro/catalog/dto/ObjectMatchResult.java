package org.stsci.astro.catalog.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.stsci.astro.catalog.entity.AstronomicalObject;

/**
 * Result DTO for object matching operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMatchResult {

    private AstronomicalObject matchedObject;

    private Double separationArcsec;

    private Double matchConfidence;

    private String matchMethod;

    private Double queryRa;

    private Double queryDec;

    /**
     * Check if this is a high-confidence match
     */
    public boolean isHighConfidenceMatch() {
        return matchConfidence != null && matchConfidence > 0.8;
    }

    /**
     * Check if this is a close match (within 1 arcsecond)
     */
    public boolean isCloseMatch() {
        return separationArcsec != null && separationArcsec <= 1.0;
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
     * Get match quality description
     */
    public String getMatchQuality() {
        if (matchConfidence == null) {
            return "Unknown";
        }
        
        if (matchConfidence >= 0.95) {
            return "Excellent";
        } else if (matchConfidence >= 0.85) {
            return "Good";
        } else if (matchConfidence >= 0.7) {
            return "Fair";
        } else if (matchConfidence >= 0.5) {
            return "Poor";
        } else {
            return "Very Poor";
        }
    }

    /**
     * Calculate position difference in arcseconds
     */
    public Double calculatePositionDifference() {
        if (matchedObject != null && queryRa != null && queryDec != null) {
            double deltaRa = (matchedObject.getRa() - queryRa) * Math.cos(Math.toRadians(queryDec));
            double deltaDec = matchedObject.getDecl() - queryDec;
            return Math.sqrt(deltaRa * deltaRa + deltaDec * deltaDec) * 3600.0;
        }
        return separationArcsec;
    }

    /**
     * Get summary of match result
     */
    public String getMatchSummary() {
        if (matchedObject == null) {
            return "No match found";
        }
        
        return String.format("Match: %s, separation: %s, confidence: %.1f%%, quality: %s",
                matchedObject.getName() != null ? matchedObject.getName() : "Object " + matchedObject.getId(),
                getFormattedSeparation(),
                matchConfidence != null ? matchConfidence * 100 : 0.0,
                getMatchQuality());
    }

    // Custom builder method for compatibility
    public static class ObjectMatchResultBuilder {
        public ObjectMatchResultBuilder object(AstronomicalObject object) {
            this.matchedObject = object;
            return this;
        }
    }
}