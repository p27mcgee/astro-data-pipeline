package org.stsci.astro.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.stsci.astro.processor.controller.WorkflowVersionController;
import org.stsci.astro.processor.model.ProcessingContext;
import org.stsci.astro.processor.model.WorkflowVersion;

import java.time.LocalDateTime;
import java.util.*;
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

        // Validate traffic split percentage
        if (request.getTrafficSplitPercentage() != null) {
            if (request.getTrafficSplitPercentage() < 0 || request.getTrafficSplitPercentage() > 100) {
                throw new IllegalArgumentException("Traffic split percentage must be between 0 and 100");
            }
        }

        // Deactivate other versions if requested
        if (Boolean.TRUE.equals(request.getDeactivateOthers())) {
            deactivateOtherVersions(workflowName, processingType, version);
        }

        // Validate traffic allocation doesn't exceed 100%
        validateTrafficAllocation(workflowName, processingType, version,
                request.getTrafficSplitPercentage() != null ? request.getTrafficSplitPercentage() : 100.0);

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
     * Setup A/B testing between two workflow versions
     */
    @Transactional
    public Map<String, Object> setupABTest(String workflowName, ProcessingContext.ProcessingType processingType,
                                           WorkflowVersionController.ABTestRequest request) {
        // Validate traffic percentages sum to 100%
        double totalTraffic = request.getTrafficPercentageA() + request.getTrafficPercentageB();
        if (Math.abs(totalTraffic - 100.0) > 0.01) {
            throw new IllegalArgumentException("Traffic percentages must sum to 100%");
        }

        // Get both workflow versions
        WorkflowVersion versionA = getWorkflowVersion(workflowName, request.getVersionA(), processingType)
                .orElseThrow(() -> new RuntimeException("Version A not found: " + request.getVersionA()));

        WorkflowVersion versionB = getWorkflowVersion(workflowName, request.getVersionB(), processingType)
                .orElseThrow(() -> new RuntimeException("Version B not found: " + request.getVersionB()));

        // Deactivate other versions
        deactivateOtherVersions(workflowName, processingType, request.getVersionA(), request.getVersionB());

        // Activate both versions with specified traffic split
        versionA.setIsActive(true);
        versionA.setTrafficSplitPercentage(request.getTrafficPercentageA());
        versionA.setActivatedAt(LocalDateTime.now());
        versionA.setActivatedBy(request.getPerformedBy());
        versionA.setActivationReason("A/B test setup: " + request.getReason());

        versionB.setIsActive(true);
        versionB.setTrafficSplitPercentage(request.getTrafficPercentageB());
        versionB.setActivatedAt(LocalDateTime.now());
        versionB.setActivatedBy(request.getPerformedBy());
        versionB.setActivationReason("A/B test setup: " + request.getReason());

        // Log A/B test setup
        logActivation(workflowName, "A/B-test", processingType, "ab-test", request.getPerformedBy(),
                String.format("A/B test: %s (%s%%) vs %s (%s%%). %s",
                        request.getVersionA(), request.getTrafficPercentageA(),
                        request.getVersionB(), request.getTrafficPercentageB(),
                        request.getReason()));

        Map<String, Object> result = new HashMap<>();
        result.put("workflowName", workflowName);
        result.put("processingType", processingType);
        result.put("versionA", versionA);
        result.put("versionB", versionB);
        result.put("setupBy", request.getPerformedBy());
        result.put("setupAt", LocalDateTime.now());

        log.info("A/B test setup for {}: {} ({}%) vs {} ({}%)",
                workflowName, request.getVersionA(), request.getTrafficPercentageA(),
                request.getVersionB(), request.getTrafficPercentageB());

        return result;
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

        // Multiple active versions - use consistent hashing for traffic splitting
        return selectVersionByTrafficSplit(activeVersions, sessionId);
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

    private void validateTrafficAllocation(String workflowName, ProcessingContext.ProcessingType processingType,
                                           String currentVersion, Double newTrafficPercentage) {
        double totalTraffic = workflowVersions.values().stream()
                .filter(wv -> workflowName.equals(wv.getWorkflowName()))
                .filter(wv -> wv.getProcessingType() == processingType)
                .filter(wv -> !currentVersion.equals(wv.getWorkflowVersion()))
                .filter(WorkflowVersion::isCurrentlyActive)
                .mapToDouble(wv -> wv.getTrafficSplitPercentage() != null ? wv.getTrafficSplitPercentage() : 0.0)
                .sum();

        if (totalTraffic + newTrafficPercentage > 100.01) { // Allow small floating point errors
            throw new IllegalArgumentException(
                    String.format("Total traffic allocation would exceed 100%%: current %.1f%% + new %.1f%% = %.1f%%",
                            totalTraffic, newTrafficPercentage, totalTraffic + newTrafficPercentage));
        }
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

    private Optional<WorkflowVersion> selectVersionByTrafficSplit(List<WorkflowVersion> activeVersions, String sessionId) {
        // Use consistent hashing based on sessionId for stable assignment
        int hash = Math.abs(sessionId.hashCode());
        double normalizedHash = (hash % 10000) / 100.0; // Convert to 0-100 range

        double cumulativePercentage = 0.0;
        for (WorkflowVersion version : activeVersions) {
            cumulativePercentage += version.getEffectiveTrafficPercentage();
            if (normalizedHash <= cumulativePercentage) {
                return Optional.of(version);
            }
        }

        // Fallback to first version if distribution doesn't add up to 100%
        return activeVersions.isEmpty() ? Optional.empty() : Optional.of(activeVersions.get(0));
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
}