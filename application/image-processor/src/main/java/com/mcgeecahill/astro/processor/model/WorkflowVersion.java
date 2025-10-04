package com.mcgeecahill.astro.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Workflow Version - Manages different versions of processing workflows
 * <p>
 * Enables workflow versioning, activation states, and traffic splitting for
 * production vs experimental processing workflows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersion {

    /**
     * Unique identifier for this workflow version
     */
    private Long id;

    /**
     * Name of the workflow (e.g., "cosmic-ray-removal", "bias-subtraction")
     */
    private String workflowName;

    /**
     * Version identifier (e.g., "v1.2", "v2.0-experimental")
     */
    private String workflowVersion;

    /**
     * Processing type for this workflow version
     */
    private ProcessingContext.ProcessingType processingType;

    /**
     * Activation state - whether this version is currently active
     */
    private Boolean isActive;

    /**
     * Default flag - whether this is the default version for the workflow type
     */
    private Boolean isDefault;

    /**
     * Traffic split percentage (0.0 to 100.0) for A/B testing
     */
    private Double trafficSplitPercentage;

    /**
     * Lifecycle timestamps
     */
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private LocalDateTime deactivatedAt;
    private LocalDateTime lastUsedAt;

    /**
     * Management metadata
     */
    private String activatedBy;
    private String deactivatedBy;
    private String activationReason;
    private String deactivationReason;

    /**
     * Performance and quality metrics
     */
    private Map<String, Object> performanceMetrics;
    private Map<String, Object> qualityMetrics;
    private Map<String, Object> usageStatistics;

    /**
     * Configuration for this workflow version
     */
    private Map<String, Object> algorithmConfiguration;
    private Map<String, Object> parameterOverrides;

    /**
     * Helper methods
     */

    /**
     * Check if this workflow version is currently active
     */
    public boolean isCurrentlyActive() {
        return Boolean.TRUE.equals(isActive);
    }

    /**
     * Check if this is the default version for the workflow
     */
    public boolean isDefaultVersion() {
        return Boolean.TRUE.equals(isDefault);
    }

    /**
     * Get the effective traffic percentage (0.0 if not active)
     */
    public double getEffectiveTrafficPercentage() {
        return isCurrentlyActive() ? (trafficSplitPercentage != null ? trafficSplitPercentage : 0.0) : 0.0;
    }

    /**
     * Check if this version has been used recently (within last 30 days)
     */
    public boolean isRecentlyUsed() {
        return lastUsedAt != null && lastUsedAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * Get algorithm name from configuration
     */
    public String getAlgorithmName() {
        if (algorithmConfiguration != null && algorithmConfiguration.containsKey("algorithm")) {
            return algorithmConfiguration.get("algorithm").toString();
        }
        return "default";
    }

    /**
     * Get processing time metric if available
     */
    public Double getAverageProcessingTime() {
        if (performanceMetrics != null && performanceMetrics.containsKey("avg_processing_time_ms")) {
            Object value = performanceMetrics.get("avg_processing_time_ms");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return null;
    }

    /**
     * Get quality score if available
     */
    public Double getQualityScore() {
        if (qualityMetrics != null && qualityMetrics.containsKey("quality_score")) {
            Object value = qualityMetrics.get("quality_score");
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        return null;
    }

    /**
     * Get usage count if available
     */
    public Long getUsageCount() {
        if (usageStatistics != null && usageStatistics.containsKey("usage_count")) {
            Object value = usageStatistics.get("usage_count");
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        return 0L;
    }

    /**
     * Create a new active workflow version
     */
    public static WorkflowVersion createActive(String workflowName, String workflowVersion,
                                               ProcessingContext.ProcessingType processingType,
                                               String activatedBy, String reason) {
        return WorkflowVersion.builder()
                .workflowName(workflowName)
                .workflowVersion(workflowVersion)
                .processingType(processingType)
                .isActive(true)
                .isDefault(false)
                .trafficSplitPercentage(100.0)
                .createdAt(LocalDateTime.now())
                .activatedAt(LocalDateTime.now())
                .activatedBy(activatedBy)
                .activationReason(reason)
                .usageStatistics(Map.of("usage_count", 0L))
                .build();
    }

    /**
     * Create a new experimental workflow version
     */
    public static WorkflowVersion createExperimental(String workflowName, String workflowVersion,
                                                     String researcherId, String hypothesis,
                                                     Map<String, Object> algorithmConfig) {
        return WorkflowVersion.builder()
                .workflowName(workflowName)
                .workflowVersion(workflowVersion)
                .processingType(ProcessingContext.ProcessingType.EXPERIMENTAL)
                .isActive(true)
                .isDefault(false)
                .trafficSplitPercentage(100.0)
                .createdAt(LocalDateTime.now())
                .activatedAt(LocalDateTime.now())
                .activatedBy(researcherId)
                .activationReason(hypothesis)
                .algorithmConfiguration(algorithmConfig)
                .usageStatistics(Map.of("usage_count", 0L))
                .build();
    }

    /**
     * Workflow activation request DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationRequest {
        private String activatedBy;
        private String reason;
        private Double trafficSplitPercentage;
        private Boolean deactivateOthers;
        private Boolean setAsDefault;
    }

    /**
     * Workflow deactivation request DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeactivationRequest {
        private String deactivatedBy;
        private String reason;
    }

    /**
     * Workflow promotion request DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionRequest {
        private String newProductionVersion;
        private String activatedBy;
        private String reason;
        private Map<String, Object> performanceMetrics;
        private Boolean setAsDefault;
    }

    /**
     * Workflow comparison result DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonResult {
        private WorkflowVersion baselineVersion;
        private WorkflowVersion comparisonVersion;
        private Map<String, Object> performanceDelta;
        private Map<String, Object> qualityDelta;
        private String recommendation;
    }
}