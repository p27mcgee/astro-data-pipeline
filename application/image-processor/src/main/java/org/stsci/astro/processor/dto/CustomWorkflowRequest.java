package org.stsci.astro.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for custom processing workflow execution")
public class CustomWorkflowRequest {

    @NotBlank(message = "Image path is required")
    @Schema(description = "S3 path to the input FITS image", example = "raw-data/observation123/image001.fits")
    @JsonProperty("imagePath")
    private String imagePath;

    @NotBlank(message = "Session ID is required")
    @Schema(description = "Processing session identifier", example = "experiment-20240101-123456")
    @JsonProperty("sessionId")
    private String sessionId;

    @NotEmpty(message = "At least one processing step is required")
    @Valid
    @Schema(description = "Ordered list of processing steps to execute")
    @JsonProperty("steps")
    private List<WorkflowStep> steps;

    @Schema(description = "Global workflow parameters")
    @JsonProperty("globalParameters")
    private Map<String, Object> globalParameters;

    @Schema(description = "Output bucket for final result", example = "processed-data", defaultValue = "processed-data")
    @JsonProperty("finalOutputBucket")
    private String finalOutputBucket = "processed-data";

    @Schema(description = "Custom output path for final result", example = "experiment-001/final/")
    @JsonProperty("finalOutputPath")
    private String finalOutputPath;

    @Schema(description = "Cleanup intermediate files after completion", defaultValue = "false")
    @JsonProperty("cleanupIntermediates")
    private Boolean cleanupIntermediates = false;

    @Schema(description = "Enable detailed workflow metrics", defaultValue = "true")
    @JsonProperty("enableMetrics")
    private Boolean enableMetrics = true;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual workflow step configuration")
    public static class WorkflowStep {

        @NotBlank(message = "Step type is required")
        @Schema(description = "Processing step type", example = "dark-subtraction",
                allowableValues = {"bias-subtraction", "dark-subtraction", "flat-correction", "cosmic-ray-removal"})
        @JsonProperty("stepType")
        private String stepType;

        @Schema(description = "Algorithm implementation to use", example = "lacosmic-v2")
        @JsonProperty("algorithm")
        private String algorithm;

        @Schema(description = "Step-specific parameters")
        @JsonProperty("parameters")
        private Map<String, Object> parameters;

        @Schema(description = "Calibration frame path for this step", example = "raw-data/calibration/master_dark.fits")
        @JsonProperty("calibrationPath")
        private String calibrationPath;

        @Schema(description = "Skip this step if conditions not met", defaultValue = "false")
        @JsonProperty("optional")
        private Boolean optional = false;
    }
}