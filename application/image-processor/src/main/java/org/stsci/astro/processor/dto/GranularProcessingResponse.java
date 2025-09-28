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
}