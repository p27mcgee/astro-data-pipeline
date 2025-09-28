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

    @Operation(summary = "Set up A/B testing between workflow versions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A/B test configured successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid A/B test configuration")
    })
    @PostMapping("/{workflowName}/ab-test")
    public ResponseEntity<Map<String, Object>> setupABTest(
            @Parameter(description = "Workflow name") @PathVariable String workflowName,
            @Parameter(description = "Processing type") @RequestParam String processingType,
            @Parameter(description = "A/B test configuration") @Valid @RequestBody ABTestRequest request) {

        log.info("Setting up A/B test for workflow: {} with versions {} and {}",
                workflowName, request.getVersionA(), request.getVersionB());

        ProcessingContext.ProcessingType type = ProcessingContext.ProcessingType.valueOf(processingType.toUpperCase());

        try {
            Map<String, Object> abTestResult = workflowVersionService.setupABTest(
                    workflowName, type, request);
            return ResponseEntity.ok(abTestResult);
        } catch (IllegalArgumentException e) {
            log.error("Failed to setup A/B test: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
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

    @Schema(description = "A/B test configuration request")
    public static class ABTestRequest {
        @Schema(description = "First version for A/B test")
        private String versionA;

        @Schema(description = "Second version for A/B test")
        private String versionB;

        @Schema(description = "Traffic percentage for version A (0-100)")
        private Double trafficPercentageA;

        @Schema(description = "Traffic percentage for version B (0-100)")
        private Double trafficPercentageB;

        @Schema(description = "User performing the A/B test setup")
        private String performedBy;

        @Schema(description = "Reason for A/B test")
        private String reason;

        // Getters and setters
        public String getVersionA() {
            return versionA;
        }

        public void setVersionA(String versionA) {
            this.versionA = versionA;
        }

        public String getVersionB() {
            return versionB;
        }

        public void setVersionB(String versionB) {
            this.versionB = versionB;
        }

        public Double getTrafficPercentageA() {
            return trafficPercentageA;
        }

        public void setTrafficPercentageA(Double trafficPercentageA) {
            this.trafficPercentageA = trafficPercentageA;
        }

        public Double getTrafficPercentageB() {
            return trafficPercentageB;
        }

        public void setTrafficPercentageB(Double trafficPercentageB) {
            this.trafficPercentageB = trafficPercentageB;
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