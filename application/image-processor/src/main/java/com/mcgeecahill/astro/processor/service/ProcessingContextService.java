package com.mcgeecahill.astro.processor.service;

import com.mcgeecahill.astro.processor.model.ProcessingContext;
import com.mcgeecahill.astro.processor.model.WorkflowVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processing Context Service
 * <p>
 * Manages processing contexts, tracks experiments, and provides processing ID
 * generation and lookup functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingContextService {

    // In-memory storage for demonstration - would be replaced with database persistence
    private final Map<String, ProcessingContext> processingContexts = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToProcessingIdMap = new ConcurrentHashMap<>();

    private final WorkflowVersionService workflowVersionService;

    /**
     * Create a new production processing context
     */
    public ProcessingContext createProductionContext(String sessionId,
                                                     String observationId,
                                                     String instrumentId,
                                                     String telescopeId,
                                                     String programId) {
        ProcessingContext context = ProcessingContext.builder()
                .processingId(ProcessingContext.generateProcessingId(ProcessingContext.ProcessingType.PRODUCTION))
                .processingType(ProcessingContext.ProcessingType.PRODUCTION)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .pipelineVersion("1.0.0")
                .productionContext(ProcessingContext.ProductionContext.builder()
                        .observationId(observationId)
                        .instrumentId(instrumentId)
                        .telescopeId(telescopeId)
                        .programId(programId)
                        .priority(1)
                        .dataReleaseVersion("DR1")
                        .calibrationFrameVersions(new HashMap<>())
                        .build())
                .dataLineage(ProcessingContext.DataLineage.builder()
                        .processingDepth(0)
                        .build())
                .build();

        // Store context
        processingContexts.put(context.getProcessingId(), context);
        sessionToProcessingIdMap.put(sessionId, context.getProcessingId());

        log.info("Created production processing context: {} for session: {}",
                context.getProcessingId(), sessionId);

        return context;
    }

    /**
     * Create a new experimental processing context
     */
    public ProcessingContext createExperimentalContext(String sessionId,
                                                       String experimentName,
                                                       String experimentDescription,
                                                       String researcherId,
                                                       String researcherEmail,
                                                       String projectId,
                                                       Map<String, Object> experimentParameters) {
        ProcessingContext context = ProcessingContext.builder()
                .processingId(ProcessingContext.generateProcessingId(ProcessingContext.ProcessingType.EXPERIMENTAL))
                .processingType(ProcessingContext.ProcessingType.EXPERIMENTAL)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .pipelineVersion("1.0.0")
                .processingParameters(experimentParameters)
                .experimentContext(ProcessingContext.ExperimentContext.builder()
                        .experimentName(experimentName)
                        .experimentDescription(experimentDescription)
                        .researcherId(researcherId)
                        .researcherEmail(researcherEmail)
                        .projectId(projectId)
                        .experimentParameters(experimentParameters)
                        .experimentStartTime(LocalDateTime.now())
                        .build())
                .dataLineage(ProcessingContext.DataLineage.builder()
                        .processingDepth(0)
                        .build())
                .build();

        // Store context
        processingContexts.put(context.getProcessingId(), context);
        sessionToProcessingIdMap.put(sessionId, context.getProcessingId());

        log.info("Created experimental processing context: {} for experiment: {} by researcher: {}",
                context.getProcessingId(), experimentName, researcherId);

        return context;
    }

    /**
     * Create a derived processing context for chained processing
     */
    public ProcessingContext createDerivedContext(String parentProcessingId, String newSessionId) {
        ProcessingContext parentContext = getProcessingContext(parentProcessingId)
                .orElseThrow(() -> new RuntimeException("Parent processing context not found: " + parentProcessingId));

        ProcessingContext derivedContext = parentContext.createDerivedContext(newSessionId);

        // Store derived context
        processingContexts.put(derivedContext.getProcessingId(), derivedContext);
        sessionToProcessingIdMap.put(newSessionId, derivedContext.getProcessingId());

        log.info("Created derived processing context: {} from parent: {}",
                derivedContext.getProcessingId(), parentProcessingId);

        return derivedContext;
    }

    /**
     * Get processing context by processing ID
     */
    public Optional<ProcessingContext> getProcessingContext(String processingId) {
        return Optional.ofNullable(processingContexts.get(processingId));
    }

    /**
     * Get processing context by session ID
     */
    public Optional<ProcessingContext> getProcessingContextBySession(String sessionId) {
        String processingId = sessionToProcessingIdMap.get(sessionId);
        return processingId != null ? getProcessingContext(processingId) : Optional.empty();
    }

    /**
     * Update data lineage information
     */
    public void updateDataLineage(String processingId, String inputImagePath,
                                  String inputImageChecksum, Map<String, String> calibrationFrames) {
        ProcessingContext context = getProcessingContext(processingId)
                .orElseThrow(() -> new RuntimeException("Processing context not found: " + processingId));

        if (context.getDataLineage() == null) {
            context.setDataLineage(ProcessingContext.DataLineage.builder().build());
        }

        context.getDataLineage().setInputImagePath(inputImagePath);
        context.getDataLineage().setInputImageChecksum(inputImageChecksum);
        context.getDataLineage().setCalibrationFrames(calibrationFrames);

        log.debug("Updated data lineage for processing context: {}", processingId);
    }

    /**
     * Get S3 key prefix for storing processed data
     */
    public String getS3KeyPrefix(String processingId) {
        return getProcessingContext(processingId)
                .map(ProcessingContext::getS3KeyPrefix)
                .orElseThrow(() -> new RuntimeException("Processing context not found: " + processingId));
    }

    /**
     * Get database partition key for organizing data
     */
    public String getPartitionKey(String processingId) {
        return getProcessingContext(processingId)
                .map(ProcessingContext::getPartitionKey)
                .orElseThrow(() -> new RuntimeException("Processing context not found: " + processingId));
    }

    /**
     * Check if processing is experimental
     */
    public boolean isExperimental(String processingId) {
        return getProcessingContext(processingId)
                .map(ProcessingContext::isExperimental)
                .orElse(false);
    }

    /**
     * Check if processing is production
     */
    public boolean isProduction(String processingId) {
        return getProcessingContext(processingId)
                .map(ProcessingContext::isProduction)
                .orElse(false);
    }

    /**
     * Get experiment information for experimental processing
     */
    public Optional<ProcessingContext.ExperimentContext> getExperimentContext(String processingId) {
        return getProcessingContext(processingId)
                .map(ProcessingContext::getExperimentContext);
    }

    /**
     * Get production information for production processing
     */
    public Optional<ProcessingContext.ProductionContext> getProductionContext(String processingId) {
        return getProcessingContext(processingId)
                .map(ProcessingContext::getProductionContext);
    }

    /**
     * List all experimental processing contexts by researcher
     */
    public Map<String, ProcessingContext> getExperimentalContextsByResearcher(String researcherId) {
        Map<String, ProcessingContext> result = new HashMap<>();

        processingContexts.entrySet().stream()
                .filter(entry -> entry.getValue().isExperimental())
                .filter(entry -> {
                    ProcessingContext.ExperimentContext expContext = entry.getValue().getExperimentContext();
                    return expContext != null && researcherId.equals(expContext.getResearcherId());
                })
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));

        return result;
    }

    /**
     * Generate experiment summary for a processing context
     */
    public Map<String, Object> generateExperimentSummary(String processingId) {
        ProcessingContext context = getProcessingContext(processingId)
                .orElseThrow(() -> new RuntimeException("Processing context not found: " + processingId));

        Map<String, Object> summary = new HashMap<>();
        summary.put("processingId", context.getProcessingId());
        summary.put("processingType", context.getProcessingType());
        summary.put("sessionId", context.getSessionId());
        summary.put("createdAt", context.getCreatedAt());
        summary.put("pipelineVersion", context.getPipelineVersion());

        if (context.isExperimental() && context.getExperimentContext() != null) {
            ProcessingContext.ExperimentContext expContext = context.getExperimentContext();
            summary.put("experimentName", expContext.getExperimentName());
            summary.put("experimentDescription", expContext.getExperimentDescription());
            summary.put("researcherId", expContext.getResearcherId());
            summary.put("projectId", expContext.getProjectId());
            summary.put("experimentStartTime", expContext.getExperimentStartTime());
        }

        if (context.getDataLineage() != null) {
            summary.put("processingDepth", context.getDataLineage().getProcessingDepth());
            summary.put("rootProcessingId", context.getDataLineage().getRootProcessingId());
        }

        summary.put("s3KeyPrefix", context.getS3KeyPrefix());
        summary.put("partitionKey", context.getPartitionKey());

        return summary;
    }

    /**
     * Validate processing ID format
     */
    public boolean isValidProcessingId(String processingId) {
        try {
            ProcessingContext.parseProcessingType(processingId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get processing type from processing ID
     */
    public ProcessingContext.ProcessingType getProcessingType(String processingId) {
        return ProcessingContext.parseProcessingType(processingId);
    }

    // =====================================================
    // Workflow-aware context creation methods
    // =====================================================

    /**
     * Create processing context using active workflow version
     */
    public ProcessingContext createContextWithActiveWorkflow(String workflowName, String sessionId,
                                                             ProcessingContext.ProcessingType processingType) {
        // Get active workflow version for this workflow type
        Optional<WorkflowVersion> activeWorkflow = workflowVersionService.getActiveWorkflowForProcessing(
                workflowName, processingType, sessionId);

        if (activeWorkflow.isEmpty()) {
            log.warn("No active workflow found for {} {}, using default", workflowName, processingType);
            return createDefaultContext(sessionId, processingType);
        }

        return createContextFromWorkflowVersion(activeWorkflow.get(), sessionId);
    }

    /**
     * Create processing context from a specific workflow version
     */
    public ProcessingContext createContextFromWorkflowVersion(WorkflowVersion workflowVersion, String sessionId) {
        String processingId = ProcessingContext.generateProcessingId(
                workflowVersion.getProcessingType(),
                workflowVersion.getWorkflowVersion(),
                workflowVersion.getWorkflowName()
        );

        ProcessingContext.ProcessingContextBuilder contextBuilder = ProcessingContext.builder()
                .processingId(processingId)
                .processingType(workflowVersion.getProcessingType())
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .pipelineVersion("1.0.0")
                .workflowName(workflowVersion.getWorkflowName())
                .workflowVersion(workflowVersion.getWorkflowVersion())
                .isActive(workflowVersion.isCurrentlyActive())
                .isDefault(workflowVersion.isDefaultVersion())
                .activatedAt(workflowVersion.getActivatedAt())
                .activatedBy(workflowVersion.getActivatedBy())
                .activationReason(workflowVersion.getActivationReason())
                .trafficSplitPercentage(workflowVersion.getTrafficSplitPercentage())
                .processingParameters(workflowVersion.getParameterOverrides())
                .dataLineage(ProcessingContext.DataLineage.builder()
                        .processingDepth(0)
                        .build());

        // Add type-specific context
        if (workflowVersion.getProcessingType() == ProcessingContext.ProcessingType.EXPERIMENTAL) {
            contextBuilder.experimentContext(ProcessingContext.ExperimentContext.builder()
                    .experimentName(workflowVersion.getWorkflowName())
                    .researcherId(workflowVersion.getActivatedBy())
                    .experimentStartTime(workflowVersion.getActivatedAt())
                    .experimentParameters(workflowVersion.getAlgorithmConfiguration())
                    .build());
        } else if (workflowVersion.getProcessingType() == ProcessingContext.ProcessingType.PRODUCTION) {
            contextBuilder.productionContext(ProcessingContext.ProductionContext.builder()
                    .priority(1)
                    .dataReleaseVersion("DR1")
                    .calibrationFrameVersions(new HashMap<>())
                    .build());
        }

        ProcessingContext context = contextBuilder.build();

        // Store context
        processingContexts.put(context.getProcessingId(), context);
        sessionToProcessingIdMap.put(sessionId, context.getProcessingId());

        // Update workflow usage statistics
        workflowVersionService.updateWorkflowUsage(workflowVersion.getWorkflowName(),
                workflowVersion.getWorkflowVersion(), workflowVersion.getProcessingType());

        log.info("Created processing context from workflow {} {}: {} for session: {}",
                workflowVersion.getWorkflowName(), workflowVersion.getWorkflowVersion(),
                context.getProcessingId(), sessionId);

        return context;
    }

    /**
     * Get or create processing context for a workflow step
     */
    public ProcessingContext getOrCreateWorkflowContext(String workflowName, String sessionId,
                                                        ProcessingContext.ProcessingType processingType,
                                                        String workflowVersion) {
        // Check if context already exists for this session
        Optional<ProcessingContext> existingContext = getProcessingContextBySession(sessionId);
        if (existingContext.isPresent()) {
            return existingContext.get();
        }

        // Create new context with specified workflow version or active version
        if (workflowVersion != null) {
            Optional<WorkflowVersion> specificWorkflow = workflowVersionService.getWorkflowVersion(
                    workflowName, workflowVersion, processingType);
            if (specificWorkflow.isPresent()) {
                return createContextFromWorkflowVersion(specificWorkflow.get(), sessionId);
            }
        }

        // Fall back to active workflow
        return createContextWithActiveWorkflow(workflowName, sessionId, processingType);
    }

    /**
     * Create default context when no workflow is specified
     */
    private ProcessingContext createDefaultContext(String sessionId, ProcessingContext.ProcessingType processingType) {
        ProcessingContext.ProcessingContextBuilder contextBuilder = ProcessingContext.builder()
                .processingId(ProcessingContext.generateProcessingId(processingType))
                .processingType(processingType)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .pipelineVersion("1.0.0")
                .workflowName("default-workflow")
                .workflowVersion("v1.0")
                .isActive(true)
                .isDefault(true)
                .trafficSplitPercentage(100.0)
                .dataLineage(ProcessingContext.DataLineage.builder()
                        .processingDepth(0)
                        .build());

        if (processingType == ProcessingContext.ProcessingType.PRODUCTION) {
            contextBuilder.productionContext(ProcessingContext.ProductionContext.builder()
                    .priority(1)
                    .dataReleaseVersion("DR1")
                    .calibrationFrameVersions(new HashMap<>())
                    .build());
        }

        ProcessingContext context = contextBuilder.build();

        // Store context
        processingContexts.put(context.getProcessingId(), context);
        sessionToProcessingIdMap.put(sessionId, context.getProcessingId());

        return context;
    }

    /**
     * Update processing context with workflow performance metrics
     */
    public void updateWorkflowMetrics(String processingId, Map<String, Object> performanceMetrics,
                                      Map<String, Object> qualityMetrics) {
        ProcessingContext context = getProcessingContext(processingId)
                .orElseThrow(() -> new RuntimeException("Processing context not found: " + processingId));

        if (context.getWorkflowName() != null && context.getWorkflowVersion() != null) {
            workflowVersionService.updateWorkflowMetrics(
                    context.getWorkflowName(),
                    context.getWorkflowVersion(),
                    context.getProcessingType(),
                    performanceMetrics,
                    qualityMetrics
            );

            log.debug("Updated workflow metrics for {} {} {}",
                    context.getWorkflowName(), context.getWorkflowVersion(), context.getProcessingType());
        }
    }

    /**
     * Get recommended workflow version for a processing type
     */
    public Optional<WorkflowVersion> getRecommendedWorkflow(String workflowName,
                                                            ProcessingContext.ProcessingType processingType) {
        return workflowVersionService.getActiveWorkflowForProcessing(workflowName, processingType, "default");
    }
}