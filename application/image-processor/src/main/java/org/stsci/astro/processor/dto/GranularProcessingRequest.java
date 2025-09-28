package org.stsci.astro.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for individual processing step operation")
public class GranularProcessingRequest {

    @NotBlank(message = "Image path is required")
    @Schema(description = "S3 path to the input FITS image", example = "raw-data/session123/image001.fits")
    @JsonProperty("imagePath")
    private String imagePath;

    @Schema(description = "S3 path to calibration frame (dark, flat, bias)", example = "raw-data/calibration/master_dark.fits")
    @JsonProperty("calibrationPath")
    private String calibrationPath;

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Processing session identifier for grouping intermediate results", example = "session-20240101-123456")
    @JsonProperty("sessionId")
    private String sessionId;

    @Schema(description = "Algorithm implementation to use", example = "lacosmic-v2", defaultValue = "default")
    @JsonProperty("algorithm")
    private String algorithm = "default";

    @Schema(description = "Algorithm-specific parameters")
    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    @Schema(description = "Output bucket for intermediate results", example = "intermediate-data", defaultValue = "intermediate-data")
    @JsonProperty("outputBucket")
    private String outputBucket = "intermediate-data";

    @Schema(description = "Custom output path within bucket", example = "experiment-001/step-1/")
    @JsonProperty("outputPath")
    private String outputPath;

    @Schema(description = "Preserve input image metadata", defaultValue = "true")
    @JsonProperty("preserveMetadata")
    private Boolean preserveMetadata = true;

    @Schema(description = "Enable detailed processing metrics", defaultValue = "false")
    @JsonProperty("enableMetrics")
    private Boolean enableMetrics = false;

    @Schema(description = "Processing context ID for tracking production vs experimental processing",
            example = "exp-20240928-a1b2c3d4-5e6f-7890-abcd-ef1234567890")
    @JsonProperty("processingId")
    private String processingId;

    @Schema(description = "Processing type: production, experimental, test",
            example = "experimental", defaultValue = "production")
    @JsonProperty("processingType")
    private String processingType = "production";

    @Schema(description = "Experiment information for experimental processing")
    @JsonProperty("experimentContext")
    private ExperimentContext experimentContext;

    @Schema(description = "Production information for production processing")
    @JsonProperty("productionContext")
    private ProductionContext productionContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Experiment context for research processing")
    public static class ExperimentContext {
        @Schema(description = "Name of the experiment", example = "cosmic-ray-algorithm-comparison")
        private String experimentName;

        @Schema(description = "Description of the experiment")
        private String experimentDescription;

        @Schema(description = "Researcher ID", example = "astronomer123")
        private String researcherId;

        @Schema(description = "Researcher email", example = "astronomer@stsci.edu")
        private String researcherEmail;

        @Schema(description = "Project ID", example = "PROJ-001")
        private String projectId;

        @Schema(description = "Research hypothesis")
        private String hypothesis;

        @Schema(description = "Parent experiment ID for follow-up experiments")
        private String parentExperimentId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Production context for operational processing")
    public static class ProductionContext {
        @Schema(description = "Observation ID", example = "OBS-2024-001")
        private String observationId;

        @Schema(description = "Instrument ID", example = "WFC3")
        private String instrumentId;

        @Schema(description = "Telescope ID", example = "HST")
        private String telescopeId;

        @Schema(description = "Program ID", example = "GO-12345")
        private String programId;

        @Schema(description = "Proposal ID", example = "PROP-001")
        private String proposalId;

        @Schema(description = "Processing priority", example = "1")
        private Integer priority;

        @Schema(description = "Data release version", example = "DR1")
        private String dataReleaseVersion;
    }
}