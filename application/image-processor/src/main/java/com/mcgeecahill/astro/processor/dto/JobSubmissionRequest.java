package com.mcgeecahill.astro.processor.dto;

import com.mcgeecahill.astro.processor.entity.ProcessingJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for job submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSubmissionRequest {

    @NotBlank(message = "Input bucket is required")
    private String inputBucket;

    @NotBlank(message = "Input object key is required")
    private String inputObjectKey;

    private String outputBucket;

    private String outputObjectKey;

    @NotNull(message = "Processing type is required")
    private ProcessingJob.ProcessingType processingType;

    @Builder.Default
    private Integer priority = 5;

    private String userId;

    private String description;

    private Map<String, Object> parameters;

    @Builder.Default
    private boolean enableDarkSubtraction = true;

    @Builder.Default
    private boolean enableFlatCorrection = true;

    @Builder.Default
    private boolean enableCosmicRayRemoval = true;

    @Builder.Default
    private boolean enableQualityAssessment = true;

    private Double cosmicRayThreshold;

    private String calibrationFramePath;

    private Integer maxRetries;

    private Long timeoutSeconds;
}