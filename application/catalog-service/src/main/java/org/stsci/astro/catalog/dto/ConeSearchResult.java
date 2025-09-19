package org.stsci.astro.catalog.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.stsci.astro.catalog.entity.AstronomicalObject;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result DTO for cone search operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConeSearchResult {

    private ConeSearchRequest searchRequest;

    private List<AstronomicalObject> objects;

    private List<ObjectMatch> matches;

    private Integer totalResults;

    private Integer returnedResults;

    private Boolean hasMoreResults;

    private LocalDateTime searchTimestamp;

    private Long searchDurationMs;

    private String searchId;

    private List<String> warnings;

    /**
     * Check if search was successful
     */
    public boolean isSuccessful() {
        return objects != null && !objects.isEmpty();
    }

    /**
     * Get search efficiency (results per second)
     */
    public Double getSearchEfficiency() {
        if (searchDurationMs != null && searchDurationMs > 0 && returnedResults != null) {
            return (double) returnedResults / (searchDurationMs / 1000.0);
        }
        return null;
    }

    /**
     * Get brightest object in results
     */
    public AstronomicalObject getBrightestObject() {
        if (objects == null || objects.isEmpty()) {
            return null;
        }
        
        return objects.stream()
                .filter(obj -> obj.getMagnitude() != null)
                .min((a, b) -> Double.compare(a.getMagnitude(), b.getMagnitude()))
                .orElse(null);
    }

    /**
     * Get faintest object in results
     */
    public AstronomicalObject getFaintestObject() {
        if (objects == null || objects.isEmpty()) {
            return null;
        }
        
        return objects.stream()
                .filter(obj -> obj.getMagnitude() != null)
                .max((a, b) -> Double.compare(a.getMagnitude(), b.getMagnitude()))
                .orElse(null);
    }

    /**
     * Get magnitude range of results
     */
    public String getMagnitudeRange() {
        AstronomicalObject brightest = getBrightestObject();
        AstronomicalObject faintest = getFaintestObject();
        
        if (brightest != null && faintest != null) {
            return String.format("%.2f - %.2f mag", 
                    brightest.getMagnitude(), faintest.getMagnitude());
        }
        return "N/A";
    }

    /**
     * Get summary statistics
     */
    public String getSummary() {
        if (returnedResults == null || returnedResults == 0) {
            return "No objects found";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Found %d objects", returnedResults));
        
        if (totalResults != null && !totalResults.equals(returnedResults)) {
            summary.append(String.format(" (of %d total)", totalResults));
        }
        
        String magRange = getMagnitudeRange();
        if (!"N/A".equals(magRange)) {
            summary.append(String.format(", magnitude range: %s", magRange));
        }
        
        if (searchDurationMs != null) {
            summary.append(String.format(", search time: %d ms", searchDurationMs));
        }
        
        return summary.toString();
    }

    // Compatibility getters for service layer
    public Double getCenterRa() {
        return searchRequest != null ? searchRequest.getCenterRa() : null;
    }

    public Double getCenterDec() {
        return searchRequest != null ? searchRequest.getCenterDec() : null;
    }

    // Custom builder methods for compatibility
    public static class ConeSearchResultBuilder {
        public ConeSearchResultBuilder centerRa(Double centerRa) {
            // This is handled by the searchRequest
            return this;
        }

        public ConeSearchResultBuilder centerDec(Double centerDec) {
            // This is handled by the searchRequest
            return this;
        }

        public ConeSearchResultBuilder searchRadiusArcsec(Double searchRadiusArcsec) {
            // This is handled by the searchRequest
            return this;
        }

        public ConeSearchResultBuilder totalMatches(int totalMatches) {
            this.totalResults = totalMatches;
            return this;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ObjectMatch {
        private AstronomicalObject object;
        private Double separationArcsec;
        private Double separationDegrees;
        private String matchQuality;

        public AstronomicalObject getObject() {
            return object;
        }

        public Double getSeparationArcsec() {
            return separationArcsec;
        }
    }
}