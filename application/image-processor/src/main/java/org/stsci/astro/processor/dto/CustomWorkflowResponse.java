package org.stsci.astro.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from custom workflow execution")
public class CustomWorkflowResponse {

    @Schema(description = "Overall workflow status", example = "SUCCESS")
    @JsonProperty("status")
    private String status;

    @Schema(description = "S3 path to final processed image", example = "processed-data/experiment-001/final/image001.fits")
    @JsonProperty("finalOutputPath")
    private String finalOutputPath;

    @Schema(description = "Processing session identifier", example = "experiment-20240101-123456")
    @JsonProperty("sessionId")
    private String sessionId;

    @Schema(description = "Workflow execution identifier", example = "workflow-exec-789")
    @JsonProperty("workflowId")
    private String workflowId;

    @Schema(description = "Workflow start time")
    @JsonProperty("startTime")
    private LocalDateTime startTime;

    @Schema(description = "Workflow completion time")
    @JsonProperty("endTime")
    private LocalDateTime endTime;

    @Schema(description = "Total workflow duration in milliseconds", example = "15432")
    @JsonProperty("totalProcessingTimeMs")
    private Long totalProcessingTimeMs;

    @Schema(description = "Results from each step execution")
    @JsonProperty("stepResults")
    private List<StepResult> stepResults;

    @Schema(description = "Paths to intermediate files created during workflow")
    @JsonProperty("intermediateFiles")
    private List<String> intermediateFiles;

    @Schema(description = "Overall workflow metrics and statistics")
    @JsonProperty("workflowMetrics")
    private Map<String, Object> workflowMetrics;

    @Schema(description = "Workflow execution warnings")
    @JsonProperty("warnings")
    private List<String> warnings;

    @Schema(description = "Error message if workflow failed")
    @JsonProperty("errorMessage")
    private String errorMessage;

    @Schema(description = "Step that caused workflow failure")
    @JsonProperty("failedStep")
    private String failedStep;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual step execution result")
    public static class StepResult {

        @Schema(description = "Step type executed", example = "dark-subtraction")
        @JsonProperty("stepType")
        private String stepType;

        @Schema(description = "Step execution status", example = "SUCCESS")
        @JsonProperty("status")
        private String status;

        @Schema(description = "Algorithm used", example = "lacosmic-v2")
        @JsonProperty("algorithm")
        private String algorithm;

        @Schema(description = "Step start time")
        @JsonProperty("startTime")
        private LocalDateTime startTime;

        @Schema(description = "Step completion time")
        @JsonProperty("endTime")
        private LocalDateTime endTime;

        @Schema(description = "Step processing duration in milliseconds", example = "3421")
        @JsonProperty("processingTimeMs")
        private Long processingTimeMs;

        @Schema(description = "Output path for this step", example = "intermediate-data/session-123/step-2/image001.fits")
        @JsonProperty("outputPath")
        private String outputPath;

        @Schema(description = "Step-specific metrics")
        @JsonProperty("metrics")
        private Map<String, Object> metrics;

        @Schema(description = "Step warnings")
        @JsonProperty("warnings")
        private List<String> warnings;

        @Schema(description = "Error message if step failed")
        @JsonProperty("errorMessage")
        private String errorMessage;
    }
}