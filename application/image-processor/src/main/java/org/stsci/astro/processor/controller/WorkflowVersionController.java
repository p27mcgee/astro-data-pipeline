package org.stsci.astro.processor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.stsci.astro.processor.model.ProcessingContext;
import org.stsci.astro.processor.model.WorkflowVersion;
import org.stsci.astro.processor.service.WorkflowVersionService;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Workflow Version Management
 * <p>
 * Provides endpoints for managing workflow versions, activation states,
 * and experimental-to-production promotion.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workflow Management", description = "Workflow versioning and activation management APIs")
public class WorkflowVersionController {

    private final WorkflowVersionService workflowVersionService;

    @Operation(summary = "Get all active workflows")
    @ApiResponse(responseCode = "200", description = "List of active workflows")
    @GetMapping("/active")
    public ResponseEntity<List<WorkflowVersion>> getActiveWorkflows(
            @Parameter(description = "Filter by processing type")
            @RequestParam(required = false) String processingType) {

        log.info("Getting active workflows, processing type filter: {}", processingType);

        ProcessingContext.ProcessingType type = null;
        if (processingType != null) {
            type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());
        }

        List<WorkflowVersion> activeWorkflows = workflowVersionService.getActiveWorkflows(type);
        return ResponseEntity.ok(activeWorkflows);
    }

    @Operation(summary = "Get workflow versions by name")
    @ApiResponse(responseCode = "200", description = "List of workflow versions")
    @GetMapping("/{workflowName}/versions")
    public ResponseEntity<List<WorkflowVersion>> getWorkflowVersions(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Processing type filter") @RequestParam(required = false) String processingType) {

        log.info("Getting versions for workflow: {}, type: {}", workflowName, processingType);

        ProcessingContext.ProcessingType type = null;
        if (processingType != null) {
            type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());
        }

        List<WorkflowVersion> versions = workflowVersionService.getWorkflowVersions(workflowName, type);
        return ResponseEntity.ok(versions);
    }

    @Operation(summary = "Get specific workflow version details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow version details"),
            @ApiResponse(responseCode = "404", description = "Workflow version not found")
    })
    @GetMapping("/{workflowName}/versions/{version}")
    public ResponseEntity<WorkflowVersion> getWorkflowVersion(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Version identifier") @PathVariable String version,
            @Parameter(description = "Processing type") @RequestParam String processingType) {

        log.info("Getting workflow version: {} {} {}", workflowName, version, processingType);

        ProcessingContext.ProcessingType type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());

        return workflowVersionService.getWorkflowVersion(workflowName, version, type)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Activate a workflow version")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow activated successfully"),
            @ApiResponse(responseCode = "404", description = "Workflow version not found"),
            @ApiResponse(responseCode = "400", description = "Invalid activation request")
    })
    @PostMapping("/{workflowName}/versions/{version}/activate")
    public ResponseEntity<WorkflowVersion> activateWorkflow(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Version identifier") @PathVariable String version,
            @Parameter(description = "Processing type") @RequestParam String processingType,
            @Parameter(description = "Activation request") @Valid @RequestBody WorkflowVersion.ActivationRequest request) {

        log.info("Activating workflow: {} {} {} by {}", workflowName, version, processingType, request.getActivatedBy());

        ProcessingContext.ProcessingType type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());

        try {
            WorkflowVersion activatedVersion = workflowVersionService.activateWorkflow(
                    workflowName, version, type, request);
            return ResponseEntity.ok(activatedVersion);
        } catch (IllegalArgumentException e) {
            log.error("Failed to activate workflow: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Workflow not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Deactivate a workflow version")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Workflow version not found")
    })
    @PostMapping("/{workflowName}/versions/{version}/deactivate")
    public ResponseEntity<WorkflowVersion> deactivateWorkflow(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Version identifier") @PathVariable String version,
            @Parameter(description = "Processing type") @RequestParam String processingType,
            @Parameter(description = "Deactivation request") @Valid @RequestBody WorkflowVersion.DeactivationRequest request) {

        log.info("Deactivating workflow: {} {} {} by {}", workflowName, version, processingType, request.getDeactivatedBy());

        ProcessingContext.ProcessingType type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());

        try {
            WorkflowVersion deactivatedVersion = workflowVersionService.deactivateWorkflow(
                    workflowName, version, type, request);
            return ResponseEntity.ok(deactivatedVersion);
        } catch (RuntimeException e) {
            log.error("Workflow not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Promote experimental workflow to production")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workflow promoted successfully"),
            @ApiResponse(responseCode = "404", description = "Experimental workflow not found"),
            @ApiResponse(responseCode = "400", description = "Invalid promotion request")
    })
    @PostMapping("/experimental/{experimentName}/promote")
    public ResponseEntity<WorkflowVersion> promoteToProduction(
            @Parameter(description = "Experiment name") @PathVariable String experimentName,
            @Parameter(description = "Promotion request") @Valid @RequestBody WorkflowVersion.PromotionRequest request) {

        log.info("Promoting experimental workflow {} to production version {} by {}",
                experimentName, request.getNewProductionVersion(), request.getActivatedBy());

        try {
            WorkflowVersion promotedVersion = workflowVersionService.promoteExperimentalToProduction(
                    experimentName, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(promotedVersion);
        } catch (IllegalArgumentException e) {
            log.error("Failed to promote workflow: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Experimental workflow not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Run experimental workflow on production dataset for comparison")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Experimental processing started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid experimental processing request"),
            @ApiResponse(responseCode = "404", description = "Workflow version not found")
    })
    @PostMapping("/{workflowName}/experimental/{version}/duplicate-production")
    public ResponseEntity<Map<String, Object>> duplicateProductionWithExperimental(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Experimental version") @PathVariable String version,
            @Parameter(description = "Experimental duplication request") @Valid @RequestBody ExperimentalDuplicationRequest request) {

        log.info("Starting experimental duplication for workflow: {} version {} by researcher {}",
                workflowName, version, request.getResearcherId());

        try {
            Map<String, Object> duplicationResult = workflowVersionService.duplicateProductionWithExperimental(
                    workflowName, version, request);
            return ResponseEntity.ok(duplicationResult);
        } catch (IllegalArgumentException e) {
            log.error("Failed to start experimental duplication: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Experimental workflow not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get workflow performance comparison")
    @ApiResponse(responseCode = "200", description = "Performance comparison results")
    @GetMapping("/{workflowName}/compare")
    public ResponseEntity<WorkflowVersion.ComparisonResult> compareWorkflowVersions(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Baseline version") @RequestParam String baselineVersion,
            @Parameter(description = "Comparison version") @RequestParam String comparisonVersion,
            @Parameter(description = "Processing type") @RequestParam String processingType) {

        log.info("Comparing workflow versions: {} vs {} for {}", baselineVersion, comparisonVersion, workflowName);

        ProcessingContext.ProcessingType type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());

        WorkflowVersion.ComparisonResult comparison = workflowVersionService.compareWorkflowVersions(
                workflowName, baselineVersion, comparisonVersion, type);

        return ResponseEntity.ok(comparison);
    }

    @Operation(summary = "Rollback to previous workflow version")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rollback successful"),
            @ApiResponse(responseCode = "404", description = "Previous version not found")
    })
    @PostMapping("/{workflowName}/rollback")
    public ResponseEntity<WorkflowVersion> rollbackWorkflow(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Processing type") @RequestParam String processingType,
            @Parameter(description = "Rollback request") @Valid @RequestBody RollbackRequest request) {

        log.info("Rolling back workflow {} to version {} by {}", workflowName, request.getTargetVersion(), request.getPerformedBy());

        ProcessingContext.ProcessingType type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());

        try {
            WorkflowVersion rolledBackVersion = workflowVersionService.rollbackWorkflow(
                    workflowName, type, request);
            return ResponseEntity.ok(rolledBackVersion);
        } catch (RuntimeException e) {
            log.error("Rollback failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Get workflow activation history")
    @ApiResponse(responseCode = "200", description = "Activation history")
    @GetMapping("/{workflowName}/history")
    public ResponseEntity<List<Map<String, Object>>> getWorkflowHistory(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Processing type filter") @RequestParam(required = false) String processingType,
            @Parameter(description = "Limit results") @RequestParam(defaultValue = "50") int limit) {

        log.info("Getting activation history for workflow: {}", workflowName);

        ProcessingContext.ProcessingType type = null;
        if (processingType != null) {
            type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());
        }

        List<Map<String, Object>> history = workflowVersionService.getWorkflowHistory(workflowName, type, limit);
        return ResponseEntity.ok(history);
    }

    // DTO classes for request bodies

    @Schema(description = "Experimental workflow duplication request")
    public static class ExperimentalDuplicationRequest {
        @Schema(description = "Researcher ID performing the experiment")
        private String researcherId;

        @Schema(description = "Research hypothesis being tested")
        private String hypothesis;

        @Schema(description = "Production dataset IDs to duplicate")
        private List<String> productionDatasetIds;

        @Schema(description = "Date range for production data selection")
        private String dateRangeStart;
        private String dateRangeEnd;

        @Schema(description = "Maximum number of datasets to process (optional)")
        private Integer maxDatasets;

        @Schema(description = "Priority level for processing")
        private String priority = "NORMAL";

        // Getters and setters
        public String getResearcherId() {
            return researcherId;
        }

        public void setResearcherId(String researcherId) {
            this.researcherId = researcherId;
        }

        public String getHypothesis() {
            return hypothesis;
        }

        public void setHypothesis(String hypothesis) {
            this.hypothesis = hypothesis;
        }

        public List<String> getProductionDatasetIds() {
            return productionDatasetIds;
        }

        public void setProductionDatasetIds(List<String> productionDatasetIds) {
            this.productionDatasetIds = productionDatasetIds;
        }

        public String getDateRangeStart() {
            return dateRangeStart;
        }

        public void setDateRangeStart(String dateRangeStart) {
            this.dateRangeStart = dateRangeStart;
        }

        public String getDateRangeEnd() {
            return dateRangeEnd;
        }

        public void setDateRangeEnd(String dateRangeEnd) {
            this.dateRangeEnd = dateRangeEnd;
        }

        public Integer getMaxDatasets() {
            return maxDatasets;
        }

        public void setMaxDatasets(Integer maxDatasets) {
            this.maxDatasets = maxDatasets;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }
    }

    @Schema(description = "Workflow rollback request")
    public static class RollbackRequest {
        @Schema(description = "Target version to rollback to")
        private String targetVersion;

        @Schema(description = "User performing the rollback")
        private String performedBy;

        @Schema(description = "Reason for rollback")
        private String reason;

        // Getters and setters
        public String getTargetVersion() {
            return targetVersion;
        }

        public void setTargetVersion(String targetVersion) {
            this.targetVersion = targetVersion;
        }

        public String getPerformedBy() {
            return performedBy;
        }

        public void setPerformedBy(String performedBy) {
            this.performedBy = performedBy;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}