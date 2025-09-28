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
}