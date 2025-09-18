package org.stsci.astro.processor.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.stsci.astro.processor.entity.ProcessingJob;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ProcessingJob entities
 */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    /**
     * Find job by job ID
     */
    Optional<ProcessingJob> findByJobId(String jobId);

    /**
     * Find jobs by status
     */
    Page<ProcessingJob> findByStatus(ProcessingJob.ProcessingStatus status, Pageable pageable);

    /**
     * Count jobs by status
     */
    long countByStatus(ProcessingJob.ProcessingStatus status);

    /**
     * Find jobs by user ID
     */
    Page<ProcessingJob> findByUserId(String userId, Pageable pageable);

    /**
     * Find jobs by user ID and status
     */
    Page<ProcessingJob> findByUserIdAndStatus(String userId, ProcessingJob.ProcessingStatus status, Pageable pageable);

    /**
     * Find jobs by user ID, status and processing type
     */
    Page<ProcessingJob> findByUserIdAndStatusAndProcessingType(
            String userId, 
            ProcessingJob.ProcessingStatus status, 
            ProcessingJob.ProcessingType processingType, 
            Pageable pageable);

    /**
     * Find jobs by status and priority ordered by creation time
     */
    List<ProcessingJob> findByStatusAndPriorityOrderByCreatedAt(
            ProcessingJob.ProcessingStatus status, 
            Integer priority);

    /**
     * Find completed jobs
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.status = 'COMPLETED'")
    List<ProcessingJob> findCompletedJobs();

    /**
     * Find completed jobs older than specified date
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.status = 'COMPLETED' AND j.completedAt < :cutoffDate")
    List<ProcessingJob> findCompletedJobsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find jobs by processing type
     */
    Page<ProcessingJob> findByProcessingType(ProcessingJob.ProcessingType processingType, Pageable pageable);

    /**
     * Find jobs by input bucket and object key
     */
    Optional<ProcessingJob> findByInputBucketAndInputObjectKey(String inputBucket, String inputObjectKey);

    /**
     * Find jobs created within a time range
     */
    List<ProcessingJob> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Find jobs by status ordered by priority and creation time
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.status = :status ORDER BY j.priority ASC, j.createdAt ASC")
    List<ProcessingJob> findByStatusOrderByPriorityAndCreatedAt(@Param("status") ProcessingJob.ProcessingStatus status);

    /**
     * Find jobs that have exceeded max retries
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.retryCount >= j.maxRetries AND j.status = 'FAILED'")
    List<ProcessingJob> findJobsExceededMaxRetries();

    /**
     * Find long running jobs
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.status = 'RUNNING' AND j.startedAt < :timeThreshold")
    List<ProcessingJob> findLongRunningJobs(@Param("timeThreshold") LocalDateTime timeThreshold);

    /**
     * Find jobs by error pattern
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.errorMessage LIKE %:errorPattern%")
    List<ProcessingJob> findJobsWithErrorPattern(@Param("errorPattern") String errorPattern);

    /**
     * Get average processing time for completed jobs
     */
    @Query("SELECT AVG(j.processingDurationMs) FROM ProcessingJob j WHERE j.status = 'COMPLETED' AND j.processingDurationMs IS NOT NULL")
    Double getAverageProcessingTime();

    /**
     * Get processing statistics by type
     */
    @Query("SELECT j.processingType, COUNT(j), AVG(j.processingDurationMs) " +
           "FROM ProcessingJob j WHERE j.status = 'COMPLETED' " +
           "GROUP BY j.processingType")
    List<Object[]> getProcessingStatsByType();

    /**
     * Find jobs for retry
     */
    @Query("SELECT j FROM ProcessingJob j WHERE j.status = 'FAILED' AND j.retryCount < j.maxRetries")
    List<ProcessingJob> findJobsForRetry();

    /**
     * Find jobs by priority range
     */
    List<ProcessingJob> findByPriorityBetween(Integer minPriority, Integer maxPriority);

    /**
     * Delete jobs older than specified date
     */
    void deleteByCompletedAtBefore(LocalDateTime cutoffDate);
}