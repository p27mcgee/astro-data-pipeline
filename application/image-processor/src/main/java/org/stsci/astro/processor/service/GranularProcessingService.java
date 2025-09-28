package org.stsci.astro.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.stsci.astro.processor.dto.CustomWorkflowRequest;
import org.stsci.astro.processor.dto.CustomWorkflowResponse;
import org.stsci.astro.processor.dto.GranularProcessingRequest;
import org.stsci.astro.processor.dto.GranularProcessingResponse;
import org.stsci.astro.processor.model.ProcessingContext;
import org.stsci.astro.processor.service.algorithm.AlgorithmRegistryService;
import org.stsci.astro.processor.service.storage.IntermediateStorageService;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GranularProcessingService {

    private final FitsProcessingService fitsProcessingService;
    private final IntermediateStorageService intermediateStorageService;
    private final AlgorithmRegistryService algorithmRegistryService;
    private final S3Service s3Service;
    private final ProcessingContextService processingContextService;

    public GranularProcessingResponse applyDarkSubtraction(GranularProcessingRequest request) {
        log.info("Starting dark subtraction for session: {}, image: {}",
                request.getSessionId(), request.getImagePath());

        LocalDateTime startTime = LocalDateTime.now();

        try {
            // Create or retrieve processing context
            ProcessingContext processingContext = getOrCreateProcessingContext(request);
            // Download input image and calibration frame
            byte[] imageData = s3Service.downloadFile(request.getImagePath());
            byte[] darkFrame = null;
            if (request.getCalibrationPath() != null) {
                darkFrame = s3Service.downloadFile(request.getCalibrationPath());
            }

            // Get algorithm implementation
            String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "default";

            // Apply dark subtraction using existing service method
            byte[] processedData = fitsProcessingService.applyDarkSubtractionGranular(
                    imageData, darkFrame, request.getParameters(), algorithm);

            // Store in intermediate bucket
            String outputPath = intermediateStorageService.storeIntermediateResult(
                    request.getSessionId(),
                    "dark-subtraction",
                    request.getImagePath(),
                    processedData,
                    request.getOutputBucket(),
                    request.getOutputPath()
            );

            LocalDateTime endTime = LocalDateTime.now();
            long processingTime = java.time.Duration.between(startTime, endTime).toMillis();

            // Build response
            return GranularProcessingResponse.builder()
                    .status("SUCCESS")
                    .outputPath(outputPath)
                    .sessionId(request.getSessionId())
                    .stepId("dark-subtraction")
                    .algorithm(algorithm)
                    .startTime(startTime)
                    .endTime(endTime)
                    .processingTimeMs(processingTime)
                    .metrics(buildProcessingMetrics(request, processingTime))
                    .nextSteps(Arrays.asList("flat-correction", "cosmic-ray-removal"))
                    .build();

        } catch (Exception e) {
            log.error("Dark subtraction failed for session: {}", request.getSessionId(), e);

            return GranularProcessingResponse.builder()
                    .status("FAILED")
                    .sessionId(request.getSessionId())
                    .stepId("dark-subtraction")
                    .algorithm(request.getAlgorithm())
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public GranularProcessingResponse applyFlatFieldCorrection(GranularProcessingRequest request) {
        log.info("Starting flat field correction for session: {}, image: {}",
                request.getSessionId(), request.getImagePath());

        LocalDateTime startTime = LocalDateTime.now();

        try {
            byte[] imageData = s3Service.downloadFile(request.getImagePath());
            byte[] flatFrame = null;
            if (request.getCalibrationPath() != null) {
                flatFrame = s3Service.downloadFile(request.getCalibrationPath());
            }

            String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "default";

            byte[] processedData = fitsProcessingService.applyFlatFieldCorrectionGranular(
                    imageData, flatFrame, request.getParameters(), algorithm);

            String outputPath = intermediateStorageService.storeIntermediateResult(
                    request.getSessionId(),
                    "flat-correction",
                    request.getImagePath(),
                    processedData,
                    request.getOutputBucket(),
                    request.getOutputPath()
            );

            LocalDateTime endTime = LocalDateTime.now();
            long processingTime = java.time.Duration.between(startTime, endTime).toMillis();

            return GranularProcessingResponse.builder()
                    .status("SUCCESS")
                    .outputPath(outputPath)
                    .sessionId(request.getSessionId())
                    .stepId("flat-correction")
                    .algorithm(algorithm)
                    .startTime(startTime)
                    .endTime(endTime)
                    .processingTimeMs(processingTime)
                    .metrics(buildProcessingMetrics(request, processingTime))
                    .nextSteps(Arrays.asList("cosmic-ray-removal", "bias-subtraction"))
                    .build();

        } catch (Exception e) {
            log.error("Flat field correction failed for session: {}", request.getSessionId(), e);

            return GranularProcessingResponse.builder()
                    .status("FAILED")
                    .sessionId(request.getSessionId())
                    .stepId("flat-correction")
                    .algorithm(request.getAlgorithm())
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public GranularProcessingResponse removeCosmicRays(GranularProcessingRequest request) {
        log.info("Starting cosmic ray removal for session: {}, image: {}",
                request.getSessionId(), request.getImagePath());

        LocalDateTime startTime = LocalDateTime.now();

        try {
            byte[] imageData = s3Service.downloadFile(request.getImagePath());
            String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "lacosmic";

            byte[] processedData = fitsProcessingService.removeCosmicRaysGranular(
                    imageData, request.getParameters(), algorithm);

            String outputPath = intermediateStorageService.storeIntermediateResult(
                    request.getSessionId(),
                    "cosmic-ray-removal",
                    request.getImagePath(),
                    processedData,
                    request.getOutputBucket(),
                    request.getOutputPath()
            );

            LocalDateTime endTime = LocalDateTime.now();
            long processingTime = java.time.Duration.between(startTime, endTime).toMillis();

            return GranularProcessingResponse.builder()
                    .status("SUCCESS")
                    .outputPath(outputPath)
                    .sessionId(request.getSessionId())
                    .stepId("cosmic-ray-removal")
                    .algorithm(algorithm)
                    .startTime(startTime)
                    .endTime(endTime)
                    .processingTimeMs(processingTime)
                    .metrics(buildProcessingMetrics(request, processingTime))
                    .nextSteps(Arrays.asList("bias-subtraction", "stacking"))
                    .build();

        } catch (Exception e) {
            log.error("Cosmic ray removal failed for session: {}", request.getSessionId(), e);

            return GranularProcessingResponse.builder()
                    .status("FAILED")
                    .sessionId(request.getSessionId())
                    .stepId("cosmic-ray-removal")
                    .algorithm(request.getAlgorithm())
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public GranularProcessingResponse applyBiasSubtraction(GranularProcessingRequest request) {
        log.info("Starting bias subtraction for session: {}, image: {}",
                request.getSessionId(), request.getImagePath());

        LocalDateTime startTime = LocalDateTime.now();

        try {
            byte[] imageData = s3Service.downloadFile(request.getImagePath());
            byte[] biasFrame = null;
            if (request.getCalibrationPath() != null) {
                biasFrame = s3Service.downloadFile(request.getCalibrationPath());
            }

            String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "default";

            byte[] processedData = fitsProcessingService.applyBiasSubtractionGranular(
                    imageData, biasFrame, request.getParameters(), algorithm);

            String outputPath = intermediateStorageService.storeIntermediateResult(
                    request.getSessionId(),
                    "bias-subtraction",
                    request.getImagePath(),
                    processedData,
                    request.getOutputBucket(),
                    request.getOutputPath()
            );

            LocalDateTime endTime = LocalDateTime.now();
            long processingTime = java.time.Duration.between(startTime, endTime).toMillis();

            return GranularProcessingResponse.builder()
                    .status("SUCCESS")
                    .outputPath(outputPath)
                    .sessionId(request.getSessionId())
                    .stepId("bias-subtraction")
                    .algorithm(algorithm)
                    .startTime(startTime)
                    .endTime(endTime)
                    .processingTimeMs(processingTime)
                    .metrics(buildProcessingMetrics(request, processingTime))
                    .nextSteps(Arrays.asList("dark-subtraction", "flat-correction"))
                    .build();

        } catch (Exception e) {
            log.error("Bias subtraction failed for session: {}", request.getSessionId(), e);

            return GranularProcessingResponse.builder()
                    .status("FAILED")
                    .sessionId(request.getSessionId())
                    .stepId("bias-subtraction")
                    .algorithm(request.getAlgorithm())
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public CustomWorkflowResponse executeCustomWorkflow(CustomWorkflowRequest request) {
        log.info("Starting custom workflow for session: {}, {} steps",
                request.getSessionId(), request.getSteps().size());

        LocalDateTime workflowStartTime = LocalDateTime.now();
        String workflowId = generateWorkflowId(request.getSessionId());
        List<CustomWorkflowResponse.StepResult> stepResults = new ArrayList<>();
        List<String> intermediateFiles = new ArrayList<>();
        String currentImagePath = request.getImagePath();

        try {
            for (int i = 0; i < request.getSteps().size(); i++) {
                CustomWorkflowRequest.WorkflowStep step = request.getSteps().get(i);
                log.info("Executing step {}/{}: {}", i + 1, request.getSteps().size(), step.getStepType());

                LocalDateTime stepStartTime = LocalDateTime.now();

                try {
                    // Build granular request for this step
                    GranularProcessingRequest stepRequest = GranularProcessingRequest.builder()
                            .imagePath(currentImagePath)
                            .calibrationPath(step.getCalibrationPath())
                            .sessionId(request.getSessionId())
                            .algorithm(step.getAlgorithm())
                            .parameters(step.getParameters())
                            .outputBucket(request.getFinalOutputBucket())
                            .outputPath(buildStepOutputPath(request, i))
                            .preserveMetadata(true)
                            .enableMetrics(request.getEnableMetrics())
                            .build();

                    // Execute the step
                    GranularProcessingResponse stepResponse = executeWorkflowStep(step.getStepType(), stepRequest);

                    if ("SUCCESS".equals(stepResponse.getStatus())) {
                        currentImagePath = stepResponse.getOutputPath();
                        intermediateFiles.add(stepResponse.getOutputPath());

                        stepResults.add(CustomWorkflowResponse.StepResult.builder()
                                .stepType(step.getStepType())
                                .status("SUCCESS")
                                .algorithm(stepResponse.getAlgorithm())
                                .startTime(stepStartTime)
                                .endTime(LocalDateTime.now())
                                .processingTimeMs(stepResponse.getProcessingTimeMs())
                                .outputPath(stepResponse.getOutputPath())
                                .metrics(stepResponse.getMetrics())
                                .warnings(stepResponse.getWarnings())
                                .build());
                    } else {
                        // Step failed
                        if (step.getOptional()) {
                            log.warn("Optional step {} failed, continuing workflow", step.getStepType());
                            stepResults.add(CustomWorkflowResponse.StepResult.builder()
                                    .stepType(step.getStepType())
                                    .status("SKIPPED")
                                    .algorithm(step.getAlgorithm())
                                    .startTime(stepStartTime)
                                    .endTime(LocalDateTime.now())
                                    .errorMessage("Optional step failed: " + stepResponse.getErrorMessage())
                                    .build());
                        } else {
                            // Required step failed, abort workflow
                            throw new RuntimeException("Required step failed: " + stepResponse.getErrorMessage());
                        }
                    }

                } catch (Exception e) {
                    log.error("Workflow step {} failed", step.getStepType(), e);

                    stepResults.add(CustomWorkflowResponse.StepResult.builder()
                            .stepType(step.getStepType())
                            .status("FAILED")
                            .algorithm(step.getAlgorithm())
                            .startTime(stepStartTime)
                            .endTime(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .build());

                    if (!step.getOptional()) {
                        throw e; // Re-throw for required steps
                    }
                }
            }

            // Move final result to output location if different from last step
            String finalOutputPath = currentImagePath;
            if (request.getFinalOutputPath() != null) {
                finalOutputPath = intermediateStorageService.moveFinalResult(
                        currentImagePath,
                        request.getFinalOutputBucket(),
                        request.getFinalOutputPath()
                );
            }

            // Clean up intermediates if requested
            if (request.getCleanupIntermediates()) {
                intermediateStorageService.cleanupIntermediateFiles(intermediateFiles);
            }

            LocalDateTime workflowEndTime = LocalDateTime.now();
            long totalProcessingTime = java.time.Duration.between(workflowStartTime, workflowEndTime).toMillis();

            return CustomWorkflowResponse.builder()
                    .status("SUCCESS")
                    .finalOutputPath(finalOutputPath)
                    .sessionId(request.getSessionId())
                    .workflowId(workflowId)
                    .startTime(workflowStartTime)
                    .endTime(workflowEndTime)
                    .totalProcessingTimeMs(totalProcessingTime)
                    .stepResults(stepResults)
                    .intermediateFiles(request.getCleanupIntermediates() ? Collections.emptyList() : intermediateFiles)
                    .workflowMetrics(buildWorkflowMetrics(request, stepResults, totalProcessingTime))
                    .build();

        } catch (Exception e) {
            log.error("Custom workflow failed for session: {}", request.getSessionId(), e);

            return CustomWorkflowResponse.builder()
                    .status("FAILED")
                    .sessionId(request.getSessionId())
                    .workflowId(workflowId)
                    .startTime(workflowStartTime)
                    .endTime(LocalDateTime.now())
                    .stepResults(stepResults)
                    .intermediateFiles(intermediateFiles)
                    .errorMessage(e.getMessage())
                    .failedStep(getCurrentFailedStep(stepResults))
                    .build();
        }
    }

    public Object getAvailableAlgorithms(String algorithmType) {
        return algorithmRegistryService.getAvailableAlgorithms(algorithmType);
    }

    public Object getIntermediateResults(String sessionId) {
        return intermediateStorageService.listIntermediateResults(sessionId);
    }

    private GranularProcessingResponse executeWorkflowStep(String stepType, GranularProcessingRequest request) {
        switch (stepType.toLowerCase()) {
            case "bias-subtraction":
                return applyBiasSubtraction(request);
            case "dark-subtraction":
                return applyDarkSubtraction(request);
            case "flat-correction":
                return applyFlatFieldCorrection(request);
            case "cosmic-ray-removal":
                return removeCosmicRays(request);
            default:
                throw new IllegalArgumentException("Unknown step type: " + stepType);
        }
    }

    private Map<String, Object> buildProcessingMetrics(GranularProcessingRequest request, long processingTime) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("processingTimeMs", processingTime);
        metrics.put("algorithm", request.getAlgorithm());
        metrics.put("enabledMetrics", request.getEnableMetrics());
        return metrics;
    }

    private Map<String, Object> buildWorkflowMetrics(CustomWorkflowRequest request,
                                                     List<CustomWorkflowResponse.StepResult> stepResults, long totalTime) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalSteps", request.getSteps().size());
        metrics.put("successfulSteps", stepResults.stream().mapToLong(r -> "SUCCESS".equals(r.getStatus()) ? 1 : 0).sum());
        metrics.put("failedSteps", stepResults.stream().mapToLong(r -> "FAILED".equals(r.getStatus()) ? 1 : 0).sum());
        metrics.put("skippedSteps", stepResults.stream().mapToLong(r -> "SKIPPED".equals(r.getStatus()) ? 1 : 0).sum());
        metrics.put("totalProcessingTimeMs", totalTime);
        metrics.put("averageStepTimeMs", stepResults.isEmpty() ? 0 : totalTime / stepResults.size());
        return metrics;
    }

    private String generateWorkflowId(String sessionId) {
        return String.format("workflow-%s-%d", sessionId, System.currentTimeMillis());
    }

    private String buildStepOutputPath(CustomWorkflowRequest request, int stepIndex) {
        if (request.getFinalOutputPath() != null) {
            return String.format("%s/step-%d/", request.getFinalOutputPath(), stepIndex + 1);
        }
        return String.format("workflow/%s/step-%d/", request.getSessionId(), stepIndex + 1);
    }

    private String getCurrentFailedStep(List<CustomWorkflowResponse.StepResult> stepResults) {
        return stepResults.stream()
                .filter(r -> "FAILED".equals(r.getStatus()))
                .map(CustomWorkflowResponse.StepResult::getStepType)
                .findFirst()
                .orElse("unknown");
    }

    /**
     * Get or create processing context based on request parameters
     */
    private ProcessingContext getOrCreateProcessingContext(GranularProcessingRequest request) {
        // If processing ID is provided, try to retrieve existing context
        if (request.getProcessingId() != null) {
            return processingContextService.getProcessingContext(request.getProcessingId())
                    .orElseThrow(() -> new RuntimeException("Processing context not found: " + request.getProcessingId()));
        }

        // Check if context exists for this session
        Optional<ProcessingContext> existingContext = processingContextService.getProcessingContextBySession(request.getSessionId());
        if (existingContext.isPresent()) {
            return existingContext.get();
        }

        // Create new processing context based on processing type
        String processingType = request.getProcessingType() != null ? request.getProcessingType().toLowerCase() : "production";

        if ("experimental".equals(processingType) && request.getExperimentContext() != null) {
            return processingContextService.createExperimentalContext(
                    request.getSessionId(),
                    request.getExperimentContext().getExperimentName(),
                    request.getExperimentContext().getExperimentDescription(),
                    request.getExperimentContext().getResearcherId(),
                    request.getExperimentContext().getResearcherEmail(),
                    request.getExperimentContext().getProjectId(),
                    request.getParameters()
            );
        } else {
            // Default to production context
            String observationId = null;
            String instrumentId = null;
            String telescopeId = null;
            String programId = null;

            if (request.getProductionContext() != null) {
                observationId = request.getProductionContext().getObservationId();
                instrumentId = request.getProductionContext().getInstrumentId();
                telescopeId = request.getProductionContext().getTelescopeId();
                programId = request.getProductionContext().getProgramId();
            }

            return processingContextService.createProductionContext(
                    request.getSessionId(),
                    observationId != null ? observationId : "AUTO-" + System.currentTimeMillis(),
                    instrumentId != null ? instrumentId : "UNKNOWN",
                    telescopeId != null ? telescopeId : "UNKNOWN",
                    programId != null ? programId : "AUTO"
            );
        }
    }

    /**
     * Build enhanced response with processing context information
     */
    private GranularProcessingResponse buildEnhancedResponse(GranularProcessingRequest request,
                                                             ProcessingContext processingContext,
                                                             String stepId,
                                                             String algorithm,
                                                             String outputPath,
                                                             LocalDateTime startTime,
                                                             LocalDateTime endTime,
                                                             long processingTime,
                                                             List<String> nextSteps) {
        GranularProcessingResponse.GranularProcessingResponseBuilder responseBuilder = GranularProcessingResponse.builder()
                .status("SUCCESS")
                .outputPath(outputPath)
                .sessionId(request.getSessionId())
                .stepId(stepId)
                .algorithm(algorithm)
                .startTime(startTime)
                .endTime(endTime)
                .processingTimeMs(processingTime)
                .metrics(buildProcessingMetrics(request, processingTime))
                .nextSteps(nextSteps)
                .processingId(processingContext.getProcessingId())
                .processingType(processingContext.getProcessingType().name().toLowerCase())
                .s3KeyPrefix(processingContext.getS3KeyPrefix())
                .partitionKey(processingContext.getPartitionKey());

        // Add experiment information if experimental processing
        if (processingContext.isExperimental() && processingContext.getExperimentContext() != null) {
            ProcessingContext.ExperimentContext expContext = processingContext.getExperimentContext();
            responseBuilder.experimentInfo(GranularProcessingResponse.ExperimentInfo.builder()
                    .experimentName(expContext.getExperimentName())
                    .researcherId(expContext.getResearcherId())
                    .projectId(expContext.getProjectId())
                    .parentExperimentId(expContext.getParentExperimentId())
                    .build());
        }

        // Add production information if production processing
        if (processingContext.isProduction() && processingContext.getProductionContext() != null) {
            ProcessingContext.ProductionContext prodContext = processingContext.getProductionContext();
            responseBuilder.productionInfo(GranularProcessingResponse.ProductionInfo.builder()
                    .observationId(prodContext.getObservationId())
                    .instrumentId(prodContext.getInstrumentId())
                    .programId(prodContext.getProgramId())
                    .dataReleaseVersion(prodContext.getDataReleaseVersion())
                    .build());
        }

        // Add data lineage information
        if (processingContext.getDataLineage() != null) {
            ProcessingContext.DataLineage lineage = processingContext.getDataLineage();
            responseBuilder.dataLineage(GranularProcessingResponse.DataLineageInfo.builder()
                    .previousProcessingId(lineage.getPreviousProcessingId())
                    .rootProcessingId(lineage.getRootProcessingId())
                    .processingDepth(lineage.getProcessingDepth())
                    .inputImageChecksum(lineage.getInputImageChecksum())
                    .build());
        }

        return responseBuilder.build();
    }

    /**
     * Update data lineage with current processing information
     */
    private void updateProcessingLineage(ProcessingContext processingContext, GranularProcessingRequest request) {
        // Calculate input image checksum for data integrity
        String inputChecksum = calculateImageChecksum(request.getImagePath());

        // Update lineage information
        Map<String, String> calibrationFrames = new HashMap<>();
        if (request.getCalibrationPath() != null) {
            calibrationFrames.put("calibration_frame", request.getCalibrationPath());
        }

        processingContextService.updateDataLineage(
                processingContext.getProcessingId(),
                request.getImagePath(),
                inputChecksum,
                calibrationFrames
        );
    }

    /**
     * Calculate checksum for data integrity verification
     */
    private String calculateImageChecksum(String imagePath) {
        try {
            // In a real implementation, this would calculate MD5/SHA256 of the image
            // For now, return a simple hash based on path and timestamp
            return "md5:" + Integer.toHexString((imagePath + System.currentTimeMillis()).hashCode());
        } catch (Exception e) {
            log.warn("Failed to calculate checksum for {}: {}", imagePath, e.getMessage());
            return "unknown";
        }
    }
}