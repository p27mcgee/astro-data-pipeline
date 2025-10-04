package org.stsci.astro.processor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.stsci.astro.processor.annotation.ExternalApi;
import org.stsci.astro.processor.dto.JobStatusResponse;
import org.stsci.astro.processor.dto.JobSubmissionRequest;
import org.stsci.astro.processor.dto.ProcessingMetricsResponse;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.service.ProcessingJobService;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
@Tag(name = "Image Processing (External)", description = "Public API for astronomical image processing job management")
@ExternalApi("Primary interface for submitting and managing FITS image processing jobs")
public class ProcessingController {

    private final ProcessingJobService processingJobService;

    @PostMapping("/jobs")
    @Operation(summary = "Submit a new image processing job")
    @ApiResponse(responseCode = "202", description = "Job submitted successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    public ResponseEntity<JobStatusResponse> submitJob(@Valid @RequestBody JobSubmissionRequest request) {
        try {
            log.info("Received processing job submission: type={}, priority={}", 
                    request.getProcessingType(), request.getPriority());
            
            JobStatusResponse response = processingJobService.submitJob(request);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            log.error("Failed to submit job", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get job status")
    @ApiResponse(responseCode = "200", description = "Job status retrieved")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        Optional<JobStatusResponse> job = processingJobService.getJobStatus(jobId);
        return job.map(ResponseEntity::ok)
                 .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    @Operation(summary = "List jobs with pagination and filtering")
    @ApiResponse(responseCode = "200", description = "Jobs retrieved successfully")
    public ResponseEntity<Page<JobStatusResponse>> listJobs(
            @Parameter(description = "User ID filter") @RequestParam(required = false) String userId,
            @Parameter(description = "Status filter") @RequestParam(required = false) ProcessingJob.ProcessingStatus status,
            @Parameter(description = "Processing type filter") @RequestParam(required = false) ProcessingJob.ProcessingType processingType,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        Page<JobStatusResponse> jobs = processingJobService.listJobs(userId, status, processingType, page, size);
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel a processing job")
    @ApiResponse(responseCode = "200", description = "Job cancelled successfully")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<JobStatusResponse> cancelJob(@PathVariable String jobId) {
        try {
            JobStatusResponse response = processingJobService.cancelJob(jobId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to cancel job: {}", jobId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(summary = "Retry a failed job")
    @ApiResponse(responseCode = "200", description = "Job retry submitted")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<JobStatusResponse> retryJob(@PathVariable String jobId) {
        try {
            JobStatusResponse response = processingJobService.retryJob(jobId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retry job: {}", jobId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get processing metrics")
    @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    public ResponseEntity<ProcessingMetricsResponse> getMetrics() {
        ProcessingMetricsResponse metrics = processingJobService.getProcessingMetrics();
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/jobs/batch")
    @Operation(summary = "Submit multiple processing jobs")
    @ApiResponse(responseCode = "202", description = "Batch jobs submitted successfully")
    public ResponseEntity<Map<String, String>> submitBatchJobs(
            @Valid @RequestBody Map<String, JobSubmissionRequest> requests) {
        
        try {
            Map<String, String> jobIds = processingJobService.submitBatchJobs(requests);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobIds);
        } catch (Exception e) {
            log.error("Failed to submit batch jobs", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/jobs")
    @Operation(summary = "Clean up completed jobs")
    @ApiResponse(responseCode = "200", description = "Cleanup completed")
    public ResponseEntity<Map<String, Object>> cleanupJobs(
            @Parameter(description = "Cleanup jobs older than days") @RequestParam(defaultValue = "7") int olderThanDays,
            @Parameter(description = "Only cleanup specific status") @RequestParam(required = false) ProcessingJob.ProcessingStatus status) {
        
        Map<String, Object> result = processingJobService.cleanupJobs(olderThanDays, status);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health status")
    @ApiResponse(responseCode = "200", description = "Health status retrieved")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> health = processingJobService.getHealthStatus();
        return ResponseEntity.ok(health);
    }
}