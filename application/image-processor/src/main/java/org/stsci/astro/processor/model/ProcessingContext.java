package org.stsci.astro.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Processing Context - Tracks processing workflows and experiments
 * <p>
 * Provides a unified way to track production vs experimental processing,
 * enable database partitioning, and maintain experiment lineage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingContext {

    /**
     * Unique processing ID - UUID format for global uniqueness
     * Enhanced Format: {type}-{version}-{timestamp}-{uuid}
     * Examples:
     * prod-v1.2-20240928-14a7f2b3-8c45-4d12-9f3e-abc123def456
     * exp-cosmic-ray-v2.1-20240928-67d8e9f1-2a34-4b56-8c90-def456abc123
     * test-v1.0-20240928-89ab123c-4d56-7e89-0f12-345678901234
     */
    private String processingId;

    /**
     * Processing type - used for partitioning and workflow routing
     */
    private ProcessingType processingType;

    /**
     * Experiment information - populated for experimental processing
     */
    private ExperimentContext experimentContext;

    /**
     * Production metadata - populated for production processing
     */
    private ProductionContext productionContext;

    /**
     * Processing session ID - groups related processing steps
     * For production: typically the observation ID or batch ID
     * For experiments: researcher-defined experiment session
     */
    private String sessionId;

    /**
     * Processing pipeline version
     */
    private String pipelineVersion;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Processing parameters specific to this context
     */
    private Map<String, Object> processingParameters;

    /**
     * Data lineage - tracks input sources and processing steps
     */
    private DataLineage dataLineage;

    /**
     * Workflow versioning and activation fields
     */
    private String workflowName;
    private String workflowVersion;
    private Boolean isActive;
    private Boolean isDefault;
    private LocalDateTime activatedAt;
    private LocalDateTime deactivatedAt;
    private String activatedBy;
    private String activationReason;
    private Double trafficSplitPercentage;

    /**
     * Processing type enumeration
     */
    public enum ProcessingType {
        PRODUCTION("prod", "Production processing pipeline"),
        EXPERIMENTAL("exp", "Research experimental processing"),
        TEST("test", "Development and testing"),
        VALIDATION("val", "Quality validation processing"),
        REPROCESSING("repr", "Historical data reprocessing");

        private final String prefix;
        private final String description;

        ProcessingType(String prefix, String description) {
            this.prefix = prefix;
            this.description = description;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Experiment-specific context information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentContext {
        private String experimentName;
        private String experimentDescription;
        private String researcherId;
        private String researcherEmail;
        private String projectId;
        private String hypothesis;
        private Map<String, Object> experimentParameters;
        private String parentExperimentId;  // For follow-up experiments
        private LocalDateTime experimentStartTime;
    }

    /**
     * Production-specific context information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductionContext {
        private String observationId;
        private String instrumentId;
        private String telescopeId;
        private String programId;
        private String proposalId;
        private String observationDate;
        private Integer priority;
        private String dataReleaseVersion;
        private Map<String, String> calibrationFrameVersions;
    }

    /**
     * Data lineage tracking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataLineage {
        private String inputImageId;
        private String inputImagePath;
        private String inputImageChecksum;
        private Map<String, String> calibrationFrames;
        private String previousProcessingId;  // For chained processing
        private String rootProcessingId;      // Original processing context
        private Integer processingDepth;      // Number of processing steps from original
    }

    /**
     * Generate a new processing ID with the specified type (legacy method)
     */
    public static String generateProcessingId(ProcessingType type) {
        return generateProcessingId(type, "v1.0", null);
    }

    /**
     * Generate a new processing ID with workflow versioning
     */
    public static String generateProcessingId(ProcessingType type, String workflowVersion, String workflowName) {
        String timestamp = LocalDateTime.now().toString().substring(0, 10).replace("-", "");
        String uuid = UUID.randomUUID().toString();

        if (type == ProcessingType.EXPERIMENTAL && workflowName != null) {
            // For experimental: exp-{workflowName}-{version}-{timestamp}-{uuid}
            return String.format("%s-%s-%s-%s-%s",
                    type.getPrefix(), workflowName, workflowVersion, timestamp, uuid);
        } else {
            // For production and others: {type}-{version}-{timestamp}-{uuid}
            return String.format("%s-%s-%s-%s",
                    type.getPrefix(), workflowVersion, timestamp, uuid);
        }
    }

    /**
     * Parse processing type from processing ID
     */
    public static ProcessingType parseProcessingType(String processingId) {
        if (processingId == null || !processingId.contains("-")) {
            throw new IllegalArgumentException("Invalid processing ID format");
        }

        String prefix = processingId.split("-")[0];
        for (ProcessingType type : ProcessingType.values()) {
            if (type.getPrefix().equals(prefix)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown processing type prefix: " + prefix);
    }

    /**
     * Check if this is experimental processing
     */
    public boolean isExperimental() {
        return processingType == ProcessingType.EXPERIMENTAL;
    }

    /**
     * Check if this is production processing
     */
    public boolean isProduction() {
        return processingType == ProcessingType.PRODUCTION;
    }

    /**
     * Get database partition key based on processing type and date
     */
    public String getPartitionKey() {
        String dateKey = createdAt.toString().substring(0, 7).replace("-", ""); // YYYYMM
        return String.format("%s_%s", processingType.getPrefix(), dateKey);
    }

    /**
     * Get S3 key prefix for organizing processed data
     */
    public String getS3KeyPrefix() {
        String datePrefix = createdAt.toString().substring(0, 10); // YYYY-MM-DD

        if (isProduction()) {
            return String.format("production/%s/%s", datePrefix, processingId);
        } else if (isExperimental()) {
            String experimentName = experimentContext != null && experimentContext.getExperimentName() != null
                    ? experimentContext.getExperimentName() : "unnamed";
            return String.format("experimental/%s/%s/%s", experimentName, datePrefix, processingId);
        } else {
            return String.format("%s/%s/%s", processingType.getPrefix(), datePrefix, processingId);
        }
    }

    /**
     * Create a production processing context
     */
    public static ProcessingContext createProductionContext(String sessionId,
                                                            String observationId,
                                                            String instrumentId) {
        return ProcessingContext.builder()
                .processingId(generateProcessingId(ProcessingType.PRODUCTION))
                .processingType(ProcessingType.PRODUCTION)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .productionContext(ProductionContext.builder()
                        .observationId(observationId)
                        .instrumentId(instrumentId)
                        .priority(1)
                        .build())
                .build();
    }

    /**
     * Create an experimental processing context
     */
    public static ProcessingContext createExperimentalContext(String sessionId,
                                                              String experimentName,
                                                              String researcherId,
                                                              String researcherEmail) {
        return ProcessingContext.builder()
                .processingId(generateProcessingId(ProcessingType.EXPERIMENTAL))
                .processingType(ProcessingType.EXPERIMENTAL)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .experimentContext(ExperimentContext.builder()
                        .experimentName(experimentName)
                        .researcherId(researcherId)
                        .researcherEmail(researcherEmail)
                        .experimentStartTime(LocalDateTime.now())
                        .build())
                .build();
    }

    /**
     * Create a derived processing context for chained processing
     */
    public ProcessingContext createDerivedContext(String newSessionId) {
        return ProcessingContext.builder()
                .processingId(generateProcessingId(this.processingType))
                .processingType(this.processingType)
                .sessionId(newSessionId)
                .createdAt(LocalDateTime.now())
                .experimentContext(this.experimentContext)
                .productionContext(this.productionContext)
                .pipelineVersion(this.pipelineVersion)
                .dataLineage(DataLineage.builder()
                        .previousProcessingId(this.processingId)
                        .rootProcessingId(this.dataLineage != null ?
                                this.dataLineage.getRootProcessingId() : this.processingId)
                        .processingDepth((this.dataLineage != null ?
                                this.dataLineage.getProcessingDepth() : 0) + 1)
                        .build())
                .build();
    }
}