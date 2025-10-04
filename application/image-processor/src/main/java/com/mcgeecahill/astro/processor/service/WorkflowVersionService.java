package com.mcgeecahill.astro.processor.service;

import com.mcgeecahill.astro.processor.controller.WorkflowVersionController;
import com.mcgeecahill.astro.processor.model.ProcessingContext;
import com.mcgeecahill.astro.processor.model.WorkflowVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing workflow versions, activation states, and promotion.
 * <p>
 * Provides comprehensive workflow lifecycle management including activation,
 * deactivation, A/B testing, and experimental-to-production promotion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowVersionService {

    // In-memory storage for demonstration - would be replaced with database persistence
    private final Map<String, WorkflowVersion> workflowVersions = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> activationHistory = new ConcurrentHashMap<>();

    /**
     * Get all active workflow versions
     */
    public List<WorkflowVersion> getActiveWorkflows(ProcessingContext.ProcessingType processingType) {
        return workflowVersions.values().stream()
                .filter(WorkflowVersion::isCurrentlyActive)
                .filter(wv -> processingType == null || wv.getProcessingType() == processingType)
                .sorted(Comparator.comparing(WorkflowVersion::getWorkflowName)
                        .thenComparing(wv -> wv.getTrafficSplitPercentage(), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * Get all versions for a specific workflow
     */
    public List<WorkflowVersion> getWorkflowVersions(String workflowName, ProcessingContext.ProcessingType processingType) {
        return workflowVersions.values().stream()
                .filter(wv -> workflowName.equals(wv.getWorkflowName()))
                .filter(wv -> processingType == null || wv.getProcessingType() == processingType)
                .sorted(Comparator.comparing(WorkflowVersion::getCreatedAt, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * Get specific workflow version
     */
    public Optional<WorkflowVersion> getWorkflowVersion(String workflowName, String version,
                                                        ProcessingContext.ProcessingType processingType) {
        String key = buildKey(workflowName, version, processingType);
        return Optional.ofNullable(workflowVersions.get(key));
    }

    /**
     * Activate a workflow version
     */
    @Transactional
    public WorkflowVersion activateWorkflow(String workflowName, String version,
                                            ProcessingContext.ProcessingType processingType,
                                            WorkflowVersion.ActivationRequest request) {
        String key = buildKey(workflowName, version, processingType);
        WorkflowVersion workflowVersion = workflowVersions.get(key);

        if (workflowVersion == null) {
            throw new RuntimeException("Workflow version not found: " + key);
        }

        // For production workflows, enforce single active version (no traffic splitting)
        if (processingType == ProcessingContext.ProcessingType.PRODUCTION) {
            // Production workflows must be 100% or 0% - no traffic splitting allowed
            if (request.getTrafficSplitPercentage() != null &&
                    request.getTrafficSplitPercentage() != 0.0 &&
                    request.getTrafficSplitPercentage() != 100.0) {
                throw new IllegalArgumentException("Production workflows do not support traffic splitting. Use 100% or deactivate.");
            }
            // Automatically deactivate other production versions for single active constraint
            deactivateOtherVersions(workflowName, processingType, version);
        } else {
            // All workflows require deterministic 100% activation for scientific reproducibility
            if (request.getTrafficSplitPercentage() != null &&
                    request.getTrafficSplitPercentage() != 0.0 &&
                    request.getTrafficSplitPercentage() != 100.0) {
                throw new IllegalArgumentException("All workflows require deterministic processing. Use 100% activation or deactivate.");
            }

            // Deactivate other versions if requested for clean experimental comparison
            if (Boolean.TRUE.equals(request.getDeactivateOthers())) {
                deactivateOtherVersions(workflowName, processingType, version);
            }
        }

        // Activate the workflow
        workflowVersion.setIsActive(true);
        workflowVersion.setActivatedAt(LocalDateTime.now());
        workflowVersion.setActivatedBy(request.getActivatedBy());
        workflowVersion.setActivationReason(request.getReason());
        workflowVersion.setTrafficSplitPercentage(
                request.getTrafficSplitPercentage() != null ? request.getTrafficSplitPercentage() : 100.0);

        if (Boolean.TRUE.equals(request.getSetAsDefault())) {
            setAsDefault(workflowName, processingType, version);
        }

        // Log activation
        logActivation(workflowName, version, processingType, "activate", request.getActivatedBy(), request.getReason());

        log.info("Activated workflow version: {} {} {} with {}% traffic",
                workflowName, version, processingType, workflowVersion.getTrafficSplitPercentage());

        return workflowVersion;
    }

    /**
     * Deactivate a workflow version
     */
    @Transactional
    public WorkflowVersion deactivateWorkflow(String workflowName, String version,
                                              ProcessingContext.ProcessingType processingType,
                                              WorkflowVersion.DeactivationRequest request) {
        String key = buildKey(workflowName, version, processingType);
        WorkflowVersion workflowVersion = workflowVersions.get(key);

        if (workflowVersion == null) {
            throw new RuntimeException("Workflow version not found: " + key);
        }

        // Deactivate the workflow
        workflowVersion.setIsActive(false);
        workflowVersion.setDeactivatedAt(LocalDateTime.now());
        workflowVersion.setDeactivatedBy(request.getDeactivatedBy());
        workflowVersion.setDeactivationReason(request.getReason());
        workflowVersion.setTrafficSplitPercentage(0.0);

        // Log deactivation
        logActivation(workflowName, version, processingType, "deactivate", request.getDeactivatedBy(), request.getReason());

        log.info("Deactivated workflow version: {} {} {}", workflowName, version, processingType);

        return workflowVersion;
    }

    /**
     * Promote experimental workflow to production
     */
    @Transactional
    public WorkflowVersion promoteExperimentalToProduction(String experimentName,
                                                           WorkflowVersion.PromotionRequest request) {
        // Find the experimental workflow
        WorkflowVersion experimentalVersion = workflowVersions.values().stream()
                .filter(wv -> wv.getProcessingType() == ProcessingContext.ProcessingType.EXPERIMENTAL)
                .filter(wv -> experimentName.equals(wv.getWorkflowName()) ||
                        (wv.getWorkflowVersion() != null && wv.getWorkflowVersion().contains(experimentName)))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Experimental workflow not found: " + experimentName));

        // Create new production version based on experimental
        WorkflowVersion productionVersion = WorkflowVersion.builder()
                .workflowName(experimentalVersion.getWorkflowName())
                .workflowVersion(request.getNewProductionVersion())
                .processingType(ProcessingContext.ProcessingType.PRODUCTION)
                .isActive(true)
                .isDefault(Boolean.TRUE.equals(request.getSetAsDefault()))
                .trafficSplitPercentage(100.0)
                .createdAt(LocalDateTime.now())
                .activatedAt(LocalDateTime.now())
                .activatedBy(request.getActivatedBy())
                .activationReason(request.getReason())
                .algorithmConfiguration(experimentalVersion.getAlgorithmConfiguration())
                .parameterOverrides(experimentalVersion.getParameterOverrides())
                .performanceMetrics(request.getPerformanceMetrics())
                .build();

        String productionKey = buildKey(productionVersion.getWorkflowName(),
                productionVersion.getWorkflowVersion(), ProcessingContext.ProcessingType.PRODUCTION);

        workflowVersions.put(productionKey, productionVersion);

        // Log promotion
        logActivation(productionVersion.getWorkflowName(), productionVersion.getWorkflowVersion(),
                ProcessingContext.ProcessingType.PRODUCTION, "promote", request.getActivatedBy(),
                "Promoted from experimental: " + experimentName + ". " + request.getReason());

        log.info("Promoted experimental workflow {} to production version {} by {}",
                experimentName, request.getNewProductionVersion(), request.getActivatedBy());

        return productionVersion;
    }

    /**
     * Duplicate production processing with experimental workflow for comparison
     */
    @Transactional
    public Map<String, Object> duplicateProductionWithExperimental(String workflowName, String experimentalVersion,
                                                                   WorkflowVersionController.ExperimentalDuplicationRequest request) {
        log.info("Starting experimental duplication: {} {} for researcher {}",
                workflowName, experimentalVersion, request.getResearcherId());

        // Validate experimental workflow exists and is active
        Optional<WorkflowVersion> experimentalWorkflow = getWorkflowVersion(
                workflowName, experimentalVersion, ProcessingContext.ProcessingType.EXPERIMENTAL);

        if (!experimentalWorkflow.isPresent()) {
            throw new IllegalArgumentException("Experimental workflow not found: " + workflowName + " " + experimentalVersion);
        }

        if (!experimentalWorkflow.get().isCurrentlyActive()) {
            throw new IllegalArgumentException("Experimental workflow is not active: " + workflowName + " " + experimentalVersion);
        }

        // Get active production workflow for comparison
        Optional<WorkflowVersion> productionWorkflow = getActiveWorkflowForProcessing(
                workflowName, ProcessingContext.ProcessingType.PRODUCTION, "duplication-session");

        if (!productionWorkflow.isPresent()) {
            throw new IllegalArgumentException("No active production workflow found for: " + workflowName);
        }

        // Create duplication plan
        String duplicationId = ProcessingContext.generateProcessingId(
                ProcessingContext.ProcessingType.EXPERIMENTAL, experimentalVersion, "duplication");

        Map<String, Object> duplicationPlan = Map.of(
                "duplicationId", duplicationId,
                "workflowName", workflowName,
                "experimentalVersion", experimentalVersion,
                "productionVersion", productionWorkflow.get().getWorkflowVersion(),
                "researcherId", request.getResearcherId(),
                "hypothesis", request.getHypothesis(),
                "datasetCount", request.getProductionDatasetIds() != null ? request.getProductionDatasetIds().size() : 0,
                "priority", request.getPriority(),
                "startedAt", LocalDateTime.now(),
                "status", "INITIATED"
        );

        // Log duplication start
        logActivation(workflowName, experimentalVersion, ProcessingContext.ProcessingType.EXPERIMENTAL,
                "duplicate", request.getResearcherId(),
                "Experimental duplication: " + request.getHypothesis());

        log.info("Experimental duplication plan created: {} datasets to process with priority {}",
                duplicationPlan.get("datasetCount"), request.getPriority());

        return duplicationPlan;
    }

    /**
     * Compare performance between two workflow versions
     */
    public WorkflowVersion.ComparisonResult compareWorkflowVersions(String workflowName, String baselineVersion,
                                                                    String comparisonVersion,
                                                                    ProcessingContext.ProcessingType processingType) {
        WorkflowVersion baseline = getWorkflowVersion(workflowName, baselineVersion, processingType)
                .orElseThrow(() -> new RuntimeException("Baseline version not found: " + baselineVersion));

        WorkflowVersion comparison = getWorkflowVersion(workflowName, comparisonVersion, processingType)
                .orElseThrow(() -> new RuntimeException("Comparison version not found: " + comparisonVersion));

        Map<String, Object> performanceDelta = calculateMetricsDelta(
                baseline.getPerformanceMetrics(), comparison.getPerformanceMetrics());

        Map<String, Object> qualityDelta = calculateMetricsDelta(
                baseline.getQualityMetrics(), comparison.getQualityMetrics());

        String recommendation = generateRecommendation(performanceDelta, qualityDelta);

        return WorkflowVersion.ComparisonResult.builder()
                .baselineVersion(baseline)
                .comparisonVersion(comparison)
                .performanceDelta(performanceDelta)
                .qualityDelta(qualityDelta)
                .recommendation(recommendation)
                .build();
    }

    /**
     * Rollback to a previous workflow version
     */
    @Transactional
    public WorkflowVersion rollbackWorkflow(String workflowName, ProcessingContext.ProcessingType processingType,
                                            WorkflowVersionController.RollbackRequest request) {
        WorkflowVersion targetVersion = getWorkflowVersion(workflowName, request.getTargetVersion(), processingType)
                .orElseThrow(() -> new RuntimeException("Target version not found: " + request.getTargetVersion()));

        // Deactivate all other versions
        deactivateOtherVersions(workflowName, processingType, request.getTargetVersion());

        // Activate target version
        targetVersion.setIsActive(true);
        targetVersion.setActivatedAt(LocalDateTime.now());
        targetVersion.setActivatedBy(request.getPerformedBy());
        targetVersion.setActivationReason("Rollback: " + request.getReason());
        targetVersion.setTrafficSplitPercentage(100.0);

        // Log rollback
        logActivation(workflowName, request.getTargetVersion(), processingType, "rollback",
                request.getPerformedBy(), request.getReason());

        log.info("Rolled back workflow {} to version {} by {}", workflowName, request.getTargetVersion(), request.getPerformedBy());

        return targetVersion;
    }

    /**
     * Get workflow activation history
     */
    public List<Map<String, Object>> getWorkflowHistory(String workflowName,
                                                        ProcessingContext.ProcessingType processingType,
                                                        int limit) {
        String historyKey = workflowName + (processingType != null ? "_" + processingType : "");
        return activationHistory.getOrDefault(historyKey, Collections.emptyList())
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get active workflow for processing
     */
    public Optional<WorkflowVersion> getActiveWorkflowForProcessing(String workflowName,
                                                                    ProcessingContext.ProcessingType processingType,
                                                                    String sessionId) {
        List<WorkflowVersion> activeVersions = getActiveWorkflows(processingType).stream()
                .filter(wv -> workflowName.equals(wv.getWorkflowName()))
                .collect(Collectors.toList());

        if (activeVersions.isEmpty()) {
            return Optional.empty();
        }

        if (activeVersions.size() == 1) {
            return Optional.of(activeVersions.get(0));
        }

        // Production workflows should only have one active version
        if (processingType == ProcessingContext.ProcessingType.PRODUCTION) {
            log.warn("Multiple active production workflows found for {}: {}. This violates single active production constraint.",
                    workflowName, activeVersions.stream().map(WorkflowVersion::getWorkflowVersion).collect(Collectors.toList()));
            // Return the most recently activated version
            return activeVersions.stream()
                    .max(Comparator.comparing(wv -> wv.getActivatedAt() != null ? wv.getActivatedAt() : LocalDateTime.MIN));
        }

        // Multiple active experimental versions should not occur in deterministic processing
        log.warn("Multiple active experimental workflows found for {}: {}. Selecting most recently activated for deterministic processing.",
                workflowName, activeVersions.stream().map(WorkflowVersion::getWorkflowVersion).collect(Collectors.toList()));

        // Return the most recently activated version for deterministic behavior
        return activeVersions.stream()
                .max(Comparator.comparing(wv -> wv.getActivatedAt() != null ? wv.getActivatedAt() : LocalDateTime.MIN));
    }

    // Private helper methods

    private String buildKey(String workflowName, String version, ProcessingContext.ProcessingType type) {
        return String.format("%s_%s_%s", workflowName, version, type);
    }

    private void deactivateOtherVersions(String workflowName, ProcessingContext.ProcessingType processingType,
                                         String... excludeVersions) {
        Set<String> excludeSet = Set.of(excludeVersions);

        workflowVersions.values().stream()
                .filter(wv -> workflowName.equals(wv.getWorkflowName()))
                .filter(wv -> wv.getProcessingType() == processingType)
                .filter(wv -> !excludeSet.contains(wv.getWorkflowVersion()))
                .filter(WorkflowVersion::isCurrentlyActive)
                .forEach(wv -> {
                    wv.setIsActive(false);
                    wv.setDeactivatedAt(LocalDateTime.now());
                    wv.setTrafficSplitPercentage(0.0);
                });
    }


    private void setAsDefault(String workflowName, ProcessingContext.ProcessingType processingType, String version) {
        // Remove default flag from other versions
        workflowVersions.values().stream()
                .filter(wv -> workflowName.equals(wv.getWorkflowName()))
                .filter(wv -> wv.getProcessingType() == processingType)
                .filter(wv -> !version.equals(wv.getWorkflowVersion()))
                .forEach(wv -> wv.setIsDefault(false));

        // Set current version as default
        String key = buildKey(workflowName, version, processingType);
        WorkflowVersion workflowVersion = workflowVersions.get(key);
        if (workflowVersion != null) {
            workflowVersion.setIsDefault(true);
        }
    }

    private void logActivation(String workflowName, String version, ProcessingContext.ProcessingType processingType,
                               String action, String performedBy, String reason) {
        String historyKey = workflowName + "_" + processingType;
        List<Map<String, Object>> history = activationHistory.computeIfAbsent(historyKey, k -> new ArrayList<>());

        Map<String, Object> entry = new HashMap<>();
        entry.put("workflowName", workflowName);
        entry.put("workflowVersion", version);
        entry.put("processingType", processingType);
        entry.put("action", action);
        entry.put("performedAt", LocalDateTime.now());
        entry.put("performedBy", performedBy);
        entry.put("reason", reason);

        history.add(0, entry); // Add to beginning for reverse chronological order

        // Keep only last 100 entries
        if (history.size() > 100) {
            history.subList(100, history.size()).clear();
        }
    }


    private Map<String, Object> calculateMetricsDelta(Map<String, Object> baseline, Map<String, Object> comparison) {
        Map<String, Object> delta = new HashMap<>();

        if (baseline == null || comparison == null) {
            return delta;
        }

        baseline.forEach((key, baseValue) -> {
            Object compValue = comparison.get(key);
            if (baseValue instanceof Number && compValue instanceof Number) {
                double baseNum = ((Number) baseValue).doubleValue();
                double compNum = ((Number) compValue).doubleValue();
                double change = compNum - baseNum;
                double percentChange = baseNum != 0 ? (change / baseNum) * 100 : 0;

                Map<String, Object> metricDelta = new HashMap<>();
                metricDelta.put("baseline", baseNum);
                metricDelta.put("comparison", compNum);
                metricDelta.put("absoluteChange", change);
                metricDelta.put("percentChange", percentChange);

                delta.put(key, metricDelta);
            }
        });

        return delta;
    }

    private String generateRecommendation(Map<String, Object> performanceDelta, Map<String, Object> qualityDelta) {
        // Simple recommendation logic - can be enhanced with more sophisticated analysis
        double performanceImprovement = 0.0;
        double qualityImprovement = 0.0;

        // Calculate average performance improvement
        if (!performanceDelta.isEmpty()) {
            performanceImprovement = performanceDelta.values().stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(m -> m.containsKey("percentChange"))
                    .mapToDouble(m -> ((Number) m.get("percentChange")).doubleValue())
                    .average()
                    .orElse(0.0);
        }

        // Calculate average quality improvement
        if (!qualityDelta.isEmpty()) {
            qualityImprovement = qualityDelta.values().stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(m -> m.containsKey("percentChange"))
                    .mapToDouble(m -> ((Number) m.get("percentChange")).doubleValue())
                    .average()
                    .orElse(0.0);
        }

        if (performanceImprovement > 5 && qualityImprovement > 5) {
            return "RECOMMEND: Significant improvements in both performance and quality";
        } else if (performanceImprovement > 10) {
            return "RECOMMEND: Significant performance improvement";
        } else if (qualityImprovement > 10) {
            return "RECOMMEND: Significant quality improvement";
        } else if (performanceImprovement < -10 || qualityImprovement < -10) {
            return "NOT RECOMMENDED: Performance or quality degradation detected";
        } else {
            return "NEUTRAL: Minor differences detected, consider business requirements";
        }
    }

    // Initialize with some sample data for demonstration
    public void initializeSampleData() {
        if (!workflowVersions.isEmpty()) {
            return; // Already initialized
        }

        // Create sample workflow versions
        WorkflowVersion prodCosmicRayV11 = WorkflowVersion.builder()
                .workflowName("cosmic-ray-removal")
                .workflowVersion("v1.1")
                .processingType(ProcessingContext.ProcessingType.PRODUCTION)
                .isActive(true)
                .isDefault(true)
                .trafficSplitPercentage(100.0)
                .createdAt(LocalDateTime.now().minusDays(30))
                .activatedAt(LocalDateTime.now().minusDays(30))
                .activatedBy("ops-team")
                .activationReason("Initial production version")
                .algorithmConfiguration(Map.of("algorithm", "lacosmic", "parameters", Map.of("sigclip", 4.0, "niter", 4)))
                .performanceMetrics(Map.of("avg_processing_time_ms", 2300, "cosmic_rays_detected_avg", 150))
                .qualityMetrics(Map.of("star_preservation_rate", 0.92, "quality_score", 85.0))
                .usageStatistics(Map.of("usage_count", 1250L))
                .build();

        WorkflowVersion expCosmicRayV20 = WorkflowVersion.builder()
                .workflowName("cosmic-ray-removal")
                .workflowVersion("v2.0-experimental")
                .processingType(ProcessingContext.ProcessingType.EXPERIMENTAL)
                .isActive(true)
                .isDefault(false)
                .trafficSplitPercentage(100.0)
                .createdAt(LocalDateTime.now().minusDays(7))
                .activatedAt(LocalDateTime.now().minusDays(7))
                .activatedBy("astronomer123")
                .activationReason("Testing ML-enhanced cosmic ray detection")
                .algorithmConfiguration(Map.of("algorithm", "ml-enhanced-lacosmic",
                        "parameters", Map.of("ml_model", "cosmic_ray_v2", "confidence_threshold", 0.85)))
                .performanceMetrics(Map.of("avg_processing_time_ms", 1800, "cosmic_rays_detected_avg", 180))
                .qualityMetrics(Map.of("star_preservation_rate", 0.97, "quality_score", 92.0))
                .usageStatistics(Map.of("usage_count", 45L))
                .build();

        workflowVersions.put(buildKey("cosmic-ray-removal", "v1.1", ProcessingContext.ProcessingType.PRODUCTION), prodCosmicRayV11);
        workflowVersions.put(buildKey("cosmic-ray-removal", "v2.0-experimental", ProcessingContext.ProcessingType.EXPERIMENTAL), expCosmicRayV20);

        log.info("Initialized sample workflow version data");
    }

    /**
     * Update workflow usage statistics
     */
    public void updateWorkflowUsage(String workflowName, String version, ProcessingContext.ProcessingType processingType) {
        try {
            String key = buildKey(workflowName, version, processingType);
            WorkflowVersion workflowVersion = workflowVersions.get(key);

            if (workflowVersion != null) {
                Map<String, Object> currentStats = workflowVersion.getUsageStatistics();
                Map<String, Object> updatedStats = new HashMap<>(currentStats);

                // Increment usage count
                Long currentCount = (Long) updatedStats.getOrDefault("usage_count", 0L);
                updatedStats.put("usage_count", currentCount + 1);
                updatedStats.put("last_used_at", LocalDateTime.now());

                // Update the workflow version
                workflowVersion.setUsageStatistics(updatedStats);
                workflowVersion.setLastUsedAt(LocalDateTime.now());

                log.debug("Updated usage statistics for workflow: {} {} {}", workflowName, version, processingType);
            }
        } catch (Exception e) {
            log.error("Failed to update workflow usage: {} {} {}", workflowName, version, processingType, e);
        }
    }

    /**
     * Update workflow performance and quality metrics
     */
    public void updateWorkflowMetrics(String workflowName, String version, ProcessingContext.ProcessingType processingType,
                                      Map<String, Object> performanceMetrics, Map<String, Object> qualityMetrics) {
        try {
            String key = buildKey(workflowName, version, processingType);
            WorkflowVersion workflowVersion = workflowVersions.get(key);

            if (workflowVersion != null) {
                // Update performance metrics
                Map<String, Object> updatedPerformanceMetrics = new HashMap<>(workflowVersion.getPerformanceMetrics());
                if (performanceMetrics != null) {
                    updatedPerformanceMetrics.putAll(performanceMetrics);
                }

                // Update quality metrics
                Map<String, Object> updatedQualityMetrics = new HashMap<>(workflowVersion.getQualityMetrics());
                if (qualityMetrics != null) {
                    updatedQualityMetrics.putAll(qualityMetrics);
                }

                // Update the workflow version
                workflowVersion.setPerformanceMetrics(updatedPerformanceMetrics);
                workflowVersion.setQualityMetrics(updatedQualityMetrics);
                workflowVersion.setLastUsedAt(LocalDateTime.now());

                log.debug("Updated metrics for workflow: {} {} {}", workflowName, version, processingType);
            }
        } catch (Exception e) {
            log.error("Failed to update workflow metrics: {} {} {}", workflowName, version, processingType, e);
        }
    }
}