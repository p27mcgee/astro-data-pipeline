package org.stsci.astro.processor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "processing_jobs", indexes = {
    @Index(name = "idx_processing_jobs_status", columnList = "status"),
    @Index(name = "idx_processing_jobs_created_at", columnList = "created_at"),
    @Index(name = "idx_processing_jobs_object_key", columnList = "input_object_key")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", unique = true, nullable = false, length = 36)
    private String jobId;

    @Column(name = "input_bucket", nullable = false)
    private String inputBucket;

    @Column(name = "input_object_key", nullable = false)
    private String inputObjectKey;

    @Column(name = "output_bucket")
    private String outputBucket;

    @Column(name = "output_object_key")
    private String outputObjectKey;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_type", nullable = false)
    private ProcessingType processingType;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 5; // 1=highest, 10=lowest

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @Column(name = "input_file_size_bytes")
    private Long inputFileSizeBytes;

    @Column(name = "output_file_size_bytes")
    private Long outputFileSizeBytes;

    // Metadata as JSON
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "processing_job_metadata", 
                    joinColumns = @JoinColumn(name = "job_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", columnDefinition = "TEXT")
    private Map<String, String> metadata;

    // Processing steps completed
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "processing_job_steps", 
                    joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "step_name")
    @Enumerated(EnumType.STRING)
    private List<ProcessingStep> completedSteps;

    // Performance metrics
    @Embedded
    private ProcessingMetrics metrics;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProcessingMetrics {
        
        @Column(name = "cpu_time_ms")
        private Long cpuTimeMs;
        
        @Column(name = "memory_peak_mb")
        private Long memoryPeakMb;
        
        @Column(name = "io_read_bytes")
        private Long ioReadBytes;
        
        @Column(name = "io_write_bytes")
        private Long ioWriteBytes;
        
        @Column(name = "network_download_bytes")
        private Long networkDownloadBytes;
        
        @Column(name = "network_upload_bytes")
        private Long networkUploadBytes;
        
        @Column(name = "processing_fps")
        private Double processingFps; // Frames per second for image processing
        
        @Column(name = "cosmic_rays_detected")
        private Integer cosmicRaysDetected;
        
        @Column(name = "image_quality_score")
        private Double imageQualityScore;
    }

    public enum ProcessingStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        RETRY
    }

    public enum ProcessingType {
        FULL_CALIBRATION,
        DARK_SUBTRACTION_ONLY,
        FLAT_CORRECTION_ONLY,
        COSMIC_RAY_REMOVAL_ONLY,
        IMAGE_STACKING,
        QUICK_LOOK,
        THUMBNAIL_GENERATION
    }

    public enum ProcessingStep {
        DOWNLOAD_INPUT,
        VALIDATE_FITS,
        DARK_SUBTRACTION,
        FLAT_CORRECTION,
        COSMIC_RAY_REMOVAL,
        IMAGE_REGISTRATION,
        IMAGE_STACKING,
        QUALITY_ASSESSMENT,
        GENERATE_THUMBNAIL,
        EXTRACT_METADATA,
        UPLOAD_OUTPUT,
        UPDATE_CATALOG,
        CLEANUP
    }

    // Convenience methods
    public boolean isCompleted() {
        return status == ProcessingStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == ProcessingStatus.FAILED;
    }

    public boolean isRunning() {
        return status == ProcessingStatus.RUNNING;
    }

    public boolean canRetry() {
        return retryCount < maxRetries && (status == ProcessingStatus.FAILED || status == ProcessingStatus.RETRY);
    }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    public void markAsStarted() {
        this.status = ProcessingStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        this.status = ProcessingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.processingDurationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }

    public void markAsFailed(String errorMessage, String stackTrace) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.completedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.processingDurationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
}