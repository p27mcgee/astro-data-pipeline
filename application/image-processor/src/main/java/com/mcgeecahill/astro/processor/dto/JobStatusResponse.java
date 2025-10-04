package com.mcgeecahill.astro.processor.dto;

import com.mcgeecahill.astro.processor.entity.ProcessingJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for job status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {

    private String jobId;

    private ProcessingJob.ProcessingStatus status;

    private ProcessingJob.ProcessingType processingType;

    private Integer priority;

    private String inputBucket;

    private String inputObjectKey;

    private String outputBucket;

    private String outputObjectKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private Long processingDurationMs;

    private String errorMessage;

    private String stackTrace;

    private Integer retryCount;

    private Integer maxRetries;

    private Long inputFileSizeBytes;

    private Long outputFileSizeBytes;

    private List<ProcessingJob.ProcessingStep> completedSteps;

    private ProcessingJob.ProcessingMetrics metrics;

    private Map<String, String> metadata;

    /**
     * Create response from ProcessingJob entity
     */
    public static JobStatusResponse fromEntity(ProcessingJob job) {
        return JobStatusResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .processingType(job.getProcessingType())
                .priority(job.getPriority())
                .inputBucket(job.getInputBucket())
                .inputObjectKey(job.getInputObjectKey())
                .outputBucket(job.getOutputBucket())
                .outputObjectKey(job.getOutputObjectKey())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .processingDurationMs(job.getProcessingDurationMs())
                .errorMessage(job.getErrorMessage())
                .stackTrace(job.getStackTrace())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .inputFileSizeBytes(job.getInputFileSizeBytes())
                .outputFileSizeBytes(job.getOutputFileSizeBytes())
                .completedSteps(job.getCompletedSteps())
                .metrics(job.getMetrics())
                .metadata(job.getMetadata())
                .build();
    }

    /**
     * Check if job is in a terminal state
     */
    public boolean isTerminal() {
        return status == ProcessingJob.ProcessingStatus.COMPLETED ||
                status == ProcessingJob.ProcessingStatus.FAILED ||
                status == ProcessingJob.ProcessingStatus.CANCELLED;
    }

    /**
     * Check if job is currently running
     */
    public boolean isRunning() {
        return status == ProcessingJob.ProcessingStatus.RUNNING;
    }

    /**
     * Get human-readable status description
     */
    public String getStatusDescription() {
        switch (status) {
            case QUEUED:
                return "Job submitted and waiting to be processed";
            case RUNNING:
                return "Processing in progress";
            case COMPLETED:
                return "Processing completed successfully";
            case FAILED:
                return "Processing failed: " + (errorMessage != null ? errorMessage : "Unknown error");
            case CANCELLED:
                return "Job was cancelled";
            default:
                return "Unknown status";
        }
    }

    /**
     * Get estimated time remaining (if processing)
     */
    public Long getEstimatedTimeRemainingMs() {
        if (!isRunning() || startedAt == null) {
            return null;
        }

        // Simple estimation based on average processing time
        long elapsedMs = java.time.Duration.between(startedAt, LocalDateTime.now()).toMillis();

        // Assume average processing takes 5 minutes
        long estimatedTotalMs = 5 * 60 * 1000;

        return Math.max(0L, estimatedTotalMs - elapsedMs);
    }
}