package org.stsci.astro.processor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.stsci.astro.processor.dto.JobSubmissionRequest;
import org.stsci.astro.processor.dto.JobStatusResponse;
import org.stsci.astro.processor.dto.ProcessingMetricsResponse;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.exception.FitsProcessingException;
import org.stsci.astro.processor.repository.ProcessingJobRepository;
import org.stsci.astro.processor.util.MetricsCollector;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing FITS processing jobs
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessingJobService {

    private final ProcessingJobRepository jobRepository;
    private final FitsProcessingService fitsProcessingService;
    private final MetricsCollector metricsCollector;

    /**
     * Submit a new processing job
     */
    public JobStatusResponse submitJob(JobSubmissionRequest request) {
        log.info("Submitting new processing job for input: {}/{}", 
                request.getInputBucket(), request.getInputObjectKey());

        ProcessingJob job = ProcessingJob.builder()
                .jobId(generateJobId())
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(request.getProcessingType())
                .priority(request.getPriority() != null ? request.getPriority() : 5)
                .inputBucket(request.getInputBucket())
                .inputObjectKey(request.getInputObjectKey())
                .outputBucket(request.getOutputBucket())
                .outputObjectKey(request.getOutputObjectKey())
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .retryCount(0)
                .build();

        // Set processing parameters
        if (request.getParameters() != null) {
            Map<String, String> stringMetadata = new java.util.HashMap<>();
            request.getParameters().forEach((key, value) -> 
                stringMetadata.put(key, value != null ? value.toString() : null));
            job.setMetadata(stringMetadata);
        }

        job = jobRepository.save(job);
        metricsCollector.updateQueueSize(jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED));

        log.info("Created processing job with ID: {}", job.getJobId());
        return JobStatusResponse.fromEntity(job);
    }

    /**
     * Get job status by ID
     */
    @Transactional(readOnly = true)
    public Optional<JobStatusResponse> getJobStatus(String jobId) {
        return jobRepository.findByJobId(jobId)
                .map(JobStatusResponse::fromEntity);
    }

    /**
     * List jobs with pagination and filtering
     */
    @Transactional(readOnly = true)
    public Page<JobStatusResponse> listJobs(String userId, ProcessingJob.ProcessingStatus status, 
                                           ProcessingJob.ProcessingType processingType,
                                           int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProcessingJob> jobs;
        
        if (userId != null && status != null && processingType != null) {
            jobs = jobRepository.findByUserIdAndStatusAndProcessingType(
                    userId, status, processingType, pageable);
        } else if (userId != null && status != null) {
            jobs = jobRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (userId != null) {
            jobs = jobRepository.findByUserId(userId, pageable);
        } else if (status != null) {
            jobs = jobRepository.findByStatus(status, pageable);
        } else {
            jobs = jobRepository.findAll(pageable);
        }

        return jobs.map(JobStatusResponse::fromEntity);
    }

    /**
     * Cancel a job
     */
    public JobStatusResponse cancelJob(String jobId) {
        ProcessingJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new FitsProcessingException("Job not found: " + jobId));

        if (job.getStatus() == ProcessingJob.ProcessingStatus.COMPLETED) {
            throw new FitsProcessingException("Cannot cancel completed job: " + jobId);
        }

        if (job.getStatus() == ProcessingJob.ProcessingStatus.CANCELLED) {
            log.warn("Job {} is already cancelled", jobId);
            return JobStatusResponse.fromEntity(job);
        }

        job.setStatus(ProcessingJob.ProcessingStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage("Job cancelled by user");

        job = jobRepository.save(job);
        log.info("Cancelled job: {}", jobId);

        return JobStatusResponse.fromEntity(job);
    }

    /**
     * Retry a failed job
     */
    public JobStatusResponse retryJob(String jobId) {
        ProcessingJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new FitsProcessingException("Job not found: " + jobId));

        if (job.getStatus() != ProcessingJob.ProcessingStatus.FAILED) {
            throw new FitsProcessingException("Can only retry failed jobs. Current status: " + job.getStatus());
        }

        if (job.getRetryCount() >= job.getMaxRetries()) {
            throw new FitsProcessingException("Job has exceeded maximum retry attempts");
        }

        job.setStatus(ProcessingJob.ProcessingStatus.QUEUED);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);

        job = jobRepository.save(job);
        log.info("Retrying job: {} (attempt {})", jobId, job.getRetryCount());

        return JobStatusResponse.fromEntity(job);
    }

    /**
     * Process next job in queue
     */
    @Async
    public CompletableFuture<Void> processNextJob() {
        Optional<ProcessingJob> nextJob = findNextJobToProcess();
        
        if (nextJob.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        ProcessingJob job = nextJob.get();
        return processJob(job);
    }

    /**
     * Process a specific job
     */
    @Async
    public CompletableFuture<Void> processJob(ProcessingJob job) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting processing for job: {}", job.getJobId());
                
                // Update job status
                job.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
                job.setStartedAt(LocalDateTime.now());
                jobRepository.save(job);
                
                metricsCollector.updateActiveJobs(
                    jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING));

                // Here you would implement the actual processing logic
                // For now, we'll simulate processing
                simulateProcessing(job);

                // Mark as completed
                job.setStatus(ProcessingJob.ProcessingStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                
                if (job.getStartedAt() != null) {
                    long processingTime = java.time.Duration.between(
                        job.getStartedAt(), job.getCompletedAt()).toMillis();
                    job.setProcessingDurationMs(processingTime);
                }
                
                jobRepository.save(job);
                metricsCollector.recordProcessingSuccess(job, job.getProcessingDurationMs());
                
                log.info("Successfully completed processing for job: {}", job.getJobId());
                
            } catch (Exception e) {
                log.error("Failed to process job: {}", job.getJobId(), e);
                
                job.setStatus(ProcessingJob.ProcessingStatus.FAILED);
                job.setCompletedAt(LocalDateTime.now());
                job.setErrorMessage(e.getMessage());
                jobRepository.save(job);
                
                metricsCollector.recordProcessingFailure(job, e);
            } finally {
                metricsCollector.updateActiveJobs(
                    jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING));
            }
        });
    }

    /**
     * Get processing metrics
     */
    @Transactional(readOnly = true)
    public ProcessingMetricsResponse getProcessingMetrics() {
        long totalJobs = jobRepository.count();
        long successfulJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.COMPLETED);
        long failedJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.FAILED);
        long activeJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING);
        long queuedJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED);

        // Calculate average processing time
        Double avgProcessingTime = jobRepository.findCompletedJobs()
                .stream()
                .filter(job -> job.getProcessingDurationMs() != null)
                .mapToLong(ProcessingJob::getProcessingDurationMs)
                .average()
                .orElse(0.0);

        return ProcessingMetricsResponse.createBasic(
                totalJobs, successfulJobs, failedJobs, avgProcessingTime,
                (int) activeJobs, (int) queuedJobs);
    }

    /**
     * Submit batch of jobs
     */
    public Map<String, String> submitBatchJobs(Map<String, JobSubmissionRequest> requests) {
        log.info("Submitting batch of {} jobs", requests.size());
        
        Map<String, String> jobIds = new java.util.HashMap<>();
        
        requests.entrySet().forEach(entry -> {
            JobSubmissionRequest request = entry.getValue();
            if (request.getDescription() == null) {
                request.setDescription("Batch job: " + entry.getKey());
            }
            JobStatusResponse response = submitJob(request);
            jobIds.put(entry.getKey(), response.getJobId());
        });
        
        return jobIds;
    }

    /**
     * Find next job to process based on priority and submission time
     */
    private Optional<ProcessingJob> findNextJobToProcess() {
        // First try high priority jobs (1-3)
        List<ProcessingJob> highPriorityJobs = jobRepository.findByStatusAndPriorityOrderByCreatedAt(
                ProcessingJob.ProcessingStatus.QUEUED, 1);
        
        if (!highPriorityJobs.isEmpty()) {
            return Optional.of(highPriorityJobs.get(0));
        }

        // Then normal priority jobs (4-6)
        List<ProcessingJob> normalPriorityJobs = jobRepository.findByStatusAndPriorityOrderByCreatedAt(
                ProcessingJob.ProcessingStatus.QUEUED, 5);
        
        if (!normalPriorityJobs.isEmpty()) {
            return Optional.of(normalPriorityJobs.get(0));
        }

        // Finally low priority jobs (7-10)
        List<ProcessingJob> lowPriorityJobs = jobRepository.findByStatusAndPriorityOrderByCreatedAt(
                ProcessingJob.ProcessingStatus.QUEUED, 10);
        
        return lowPriorityJobs.isEmpty() ? Optional.empty() : Optional.of(lowPriorityJobs.get(0));
    }

    /**
     * Simulate processing for demo purposes
     */
    private void simulateProcessing(ProcessingJob job) throws InterruptedException {
        String[] steps = {"LOADING", "DARK_SUBTRACTION", "FLAT_CORRECTION", 
                         "COSMIC_RAY_REMOVAL", "QUALITY_ASSESSMENT", "SAVING"};
        
        for (int i = 0; i < steps.length; i++) {
            // Simulate processing time
            Thread.sleep(1000 + (long)(Math.random() * 2000));
        }
    }

    /**
     * Generate unique job ID
     */
    private String generateJobId() {
        return "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Clean up old completed jobs
     */
    @Transactional
    public void cleanupOldJobs(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<ProcessingJob> oldJobs = jobRepository.findCompletedJobsOlderThan(cutoffDate);
        
        if (!oldJobs.isEmpty()) {
            jobRepository.deleteAll(oldJobs);
            log.info("Cleaned up {} old jobs older than {} days", oldJobs.size(), daysOld);
        }
    }
    
    /**
     * Clean up jobs with optional status filter
     */
    @Transactional
    public Map<String, Object> cleanupJobs(int olderThanDays, ProcessingJob.ProcessingStatus status) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(olderThanDays);
        List<ProcessingJob> jobsToDelete;
        
        if (status != null) {
            // Filter by status and date
            jobsToDelete = jobRepository.findCompletedJobsOlderThan(cutoffDate)
                    .stream()
                    .filter(job -> job.getStatus() == status)
                    .collect(Collectors.toList());
        } else {
            jobsToDelete = jobRepository.findCompletedJobsOlderThan(cutoffDate);
        }
        
        int deletedCount = jobsToDelete.size();
        if (!jobsToDelete.isEmpty()) {
            jobRepository.deleteAll(jobsToDelete);
            log.info("Cleaned up {} jobs older than {} days with status {}", deletedCount, olderThanDays, status);
        }
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffDate", cutoffDate.toString());
        result.put("status", status != null ? status.toString() : "ALL");
        
        return result;
    }
    
    /**
     * Get system health status
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new java.util.HashMap<>();
        
        long totalJobs = jobRepository.count();
        long queuedJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED);
        long runningJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING);
        long failedJobs = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.FAILED);
        
        health.put("totalJobs", totalJobs);
        health.put("queuedJobs", queuedJobs);
        health.put("runningJobs", runningJobs);
        health.put("failedJobs", failedJobs);
        health.put("systemLoad", queuedJobs + runningJobs);
        health.put("timestamp", LocalDateTime.now().toString());
        
        // Determine overall health
        String status = "HEALTHY";
        if (queuedJobs > 1000 || failedJobs > 100) {
            status = "DEGRADED";
        }
        if (queuedJobs > 5000 || failedJobs > 500) {
            status = "CRITICAL";
        }
        health.put("status", status);
        
        return health;
    }
}