package org.stsci.astro.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from individual processing step operation")
public class GranularProcessingResponse {

    @Schema(description = "Processing operation status", example = "SUCCESS")
    @JsonProperty("status")
    private String status;

    @Schema(description = "S3 path to the processed output image", example = "intermediate-data/session-123/dark-subtracted/image001.fits")
    @JsonProperty("outputPath")
    private String outputPath;

    @Schema(description = "Processing session identifier", example = "session-20240101-123456")
    @JsonProperty("sessionId")
    private String sessionId;

    @Schema(description = "Processing step identifier", example = "dark-subtraction")
    @JsonProperty("stepId")
    private String stepId;

    @Schema(description = "Algorithm used for processing", example = "lacosmic-v2")
    @JsonProperty("algorithm")
    private String algorithm;

    @Schema(description = "Processing start time")
    @JsonProperty("startTime")
    private LocalDateTime startTime;

    @Schema(description = "Processing completion time")
    @JsonProperty("endTime")
    private LocalDateTime endTime;

    @Schema(description = "Processing duration in milliseconds", example = "1234")
    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    @Schema(description = "Processing metrics and statistics")
    @JsonProperty("metrics")
    private Map<String, Object> metrics;

    @Schema(description = "Warning messages from processing")
    @JsonProperty("warnings")
    private java.util.List<String> warnings;

    @Schema(description = "Error message if processing failed")
    @JsonProperty("errorMessage")
    private String errorMessage;

    @Schema(description = "Input image metadata preserved")
    @JsonProperty("inputMetadata")
    private Map<String, Object> inputMetadata;

    @Schema(description = "Output image metadata")
    @JsonProperty("outputMetadata")
    private Map<String, Object> outputMetadata;

    @Schema(description = "Compatible with next processing steps")
    @JsonProperty("nextSteps")
    private java.util.List<String> nextSteps;

    @Schema(description = "Processing context ID for tracking production vs experimental processing",
            example = "exp-20240928-a1b2c3d4-5e6f-7890-abcd-ef1234567890")
    @JsonProperty("processingId")
    private String processingId;

    @Schema(description = "Processing type: production, experimental, test",
            example = "experimental")
    @JsonProperty("processingType")
    private String processingType;

    @Schema(description = "S3 key prefix for organizing results by processing context",
            example = "experimental/cosmic-ray-comparison/2024-09-28/exp-20240928-a1b2c3d4")
    @JsonProperty("s3KeyPrefix")
    private String s3KeyPrefix;

    @Schema(description = "Database partition key for efficient querying",
            example = "exp_202409")
    @JsonProperty("partitionKey")
    private String partitionKey;

    @Schema(description = "Experiment context for experimental processing")
    @JsonProperty("experimentContext")
    private ExperimentInfo experimentInfo;

    @Schema(description = "Production context for production processing")
    @JsonProperty("productionContext")
    private ProductionInfo productionInfo;

    @Schema(description = "Data lineage information")
    @JsonProperty("dataLineage")
    private DataLineageInfo dataLineage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Experiment information for research processing")
    public static class ExperimentInfo {
        @Schema(description = "Name of the experiment")
        private String experimentName;

        @Schema(description = "Researcher ID")
        private String researcherId;

        @Schema(description = "Project ID")
        private String projectId;

        @Schema(description = "Parent experiment ID for follow-up experiments")
        private String parentExperimentId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Production information for operational processing")
    public static class ProductionInfo {
        @Schema(description = "Observation ID")
        private String observationId;

        @Schema(description = "Instrument ID")
        private String instrumentId;

        @Schema(description = "Program ID")
        private String programId;

        @Schema(description = "Data release version")
        private String dataReleaseVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Data lineage tracking information")
    public static class DataLineageInfo {
        @Schema(description = "Previous processing ID in the chain")
        private String previousProcessingId;

        @Schema(description = "Root processing ID (original processing)")
        private String rootProcessingId;

        @Schema(description = "Processing depth (number of steps from original)")
        private Integer processingDepth;

        @Schema(description = "Input image checksum for verification")
        private String inputImageChecksum;
    }
}