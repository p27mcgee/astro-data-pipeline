package org.stsci.astro.processor.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.stsci.astro.processor.entity.ProcessingJob;
import org.stsci.astro.processor.repository.ProcessingJobRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ProcessingJobRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProcessingJobRepository jobRepository;

    private ProcessingJob sampleJob1;
    private ProcessingJob sampleJob2;
    private ProcessingJob sampleJob3;

    @BeforeEach
    void setUp() {
        // Clear any existing data
        jobRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        LocalDateTime now = LocalDateTime.now();

        sampleJob1 = ProcessingJob.builder()
                .jobId("job_001")
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file1.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("output1.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(now.minusHours(2))
                .updatedAt(now.minusHours(2))
                .build();

        sampleJob2 = ProcessingJob.builder()
                .jobId("job_002")
                .status(ProcessingJob.ProcessingStatus.RUNNING)
                .processingType(ProcessingJob.ProcessingType.DARK_SUBTRACTION_ONLY)
                .priority(3)
                .inputBucket("test-bucket")
                .inputObjectKey("file2.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("output2.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(now.minusHours(1))
                .updatedAt(now.minusMinutes(30))
                .startedAt(now.minusMinutes(30))
                .build();

        sampleJob3 = ProcessingJob.builder()
                .jobId("job_003")
                .status(ProcessingJob.ProcessingStatus.COMPLETED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(7)
                .inputBucket("test-bucket")
                .inputObjectKey("file3.fits")
                .outputBucket("output-bucket")
                .outputObjectKey("output3.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusDays(1))
                .startedAt(now.minusDays(1).plusHours(1))
                .completedAt(now.minusDays(1).plusHours(2))
                .processingDurationMs(3600000L) // 1 hour
                .build();

        // Persist test data
        entityManager.persistAndFlush(sampleJob1);
        entityManager.persistAndFlush(sampleJob2);
        entityManager.persistAndFlush(sampleJob3);
        entityManager.clear();
    }

    @Test
    void findByJobId_ExistingJob_ShouldReturnJob() {
        // When
        Optional<ProcessingJob> result = jobRepository.findByJobId("job_001");

        // Then
        assertTrue(result.isPresent());
        assertEquals("job_001", result.get().getJobId());
        assertEquals(ProcessingJob.ProcessingStatus.QUEUED, result.get().getStatus());
    }

    @Test
    void findByJobId_NonExistingJob_ShouldReturnEmpty() {
        // When
        Optional<ProcessingJob> result = jobRepository.findByJobId("non_existing");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void findByStatus_ShouldReturnJobsWithMatchingStatus() {
        // When
        Page<ProcessingJob> result = jobRepository.findByStatus(
                ProcessingJob.ProcessingStatus.QUEUED, 
                PageRequest.of(0, 10));

        // Then
        assertEquals(1, result.getTotalElements());
        assertEquals("job_001", result.getContent().get(0).getJobId());
    }

    @Test
    void countByStatus_ShouldReturnCorrectCount() {
        // When
        long queuedCount = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.QUEUED);
        long runningCount = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.RUNNING);
        long completedCount = jobRepository.countByStatus(ProcessingJob.ProcessingStatus.COMPLETED);

        // Then
        assertEquals(1, queuedCount);
        assertEquals(1, runningCount);
        assertEquals(1, completedCount);
    }

    @Test
    void findByStatusAndPriorityOrderByCreatedAt_ShouldReturnOrderedResults() {
        // Given - Add another queued job with different priority
        ProcessingJob highPriorityJob = ProcessingJob.builder()
                .jobId("job_high_priority")
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(1) // Higher priority (lower number)
                .inputBucket("test-bucket")
                .inputObjectKey("file_high.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .build();
        
        entityManager.persistAndFlush(highPriorityJob);

        // When
        List<ProcessingJob> normalPriorityJobs = jobRepository.findByStatusAndPriorityOrderByCreatedAt(
                ProcessingJob.ProcessingStatus.QUEUED, 5);
        List<ProcessingJob> highPriorityJobs = jobRepository.findByStatusAndPriorityOrderByCreatedAt(
                ProcessingJob.ProcessingStatus.QUEUED, 1);

        // Then
        assertEquals(1, normalPriorityJobs.size());
        assertEquals("job_001", normalPriorityJobs.get(0).getJobId());
        
        assertEquals(1, highPriorityJobs.size());
        assertEquals("job_high_priority", highPriorityJobs.get(0).getJobId());
    }

    @Test
    void findCompletedJobs_ShouldReturnOnlyCompletedJobs() {
        // When
        List<ProcessingJob> completedJobs = jobRepository.findCompletedJobs();

        // Then
        assertEquals(1, completedJobs.size());
        assertEquals("job_003", completedJobs.get(0).getJobId());
        assertEquals(ProcessingJob.ProcessingStatus.COMPLETED, completedJobs.get(0).getStatus());
    }

    @Test
    void findCompletedJobsOlderThan_ShouldReturnJobsOlderThanDate() {
        // Given
        LocalDateTime cutoffDate = LocalDateTime.now().minusHours(12);

        // When
        List<ProcessingJob> oldJobs = jobRepository.findCompletedJobsOlderThan(cutoffDate);

        // Then
        assertEquals(1, oldJobs.size());
        assertEquals("job_003", oldJobs.get(0).getJobId());
    }

    @Test
    void findByInputBucketAndInputObjectKey_ShouldReturnMatchingJob() {
        // When
        Optional<ProcessingJob> result = jobRepository.findByInputBucketAndInputObjectKey(
                "test-bucket", "file2.fits");

        // Then
        assertTrue(result.isPresent());
        assertEquals("job_002", result.get().getJobId());
    }

    @Test
    void findByCreatedAtBetween_ShouldReturnJobsInTimeRange() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusHours(3);
        LocalDateTime end = LocalDateTime.now().minusMinutes(30);

        // When
        List<ProcessingJob> jobsInRange = jobRepository.findByCreatedAtBetween(start, end);

        // Then
        assertEquals(2, jobsInRange.size());
        assertTrue(jobsInRange.stream().anyMatch(job -> "job_001".equals(job.getJobId())));
        assertTrue(jobsInRange.stream().anyMatch(job -> "job_002".equals(job.getJobId())));
    }

    @Test
    void findByStatusOrderByPriorityAndCreatedAt_ShouldReturnPrioritizedJobs() {
        // Given - Add jobs with different priorities
        ProcessingJob lowPriorityJob = ProcessingJob.builder()
                .jobId("job_low_priority")
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(9)
                .inputBucket("test-bucket")
                .inputObjectKey("file_low.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();

        ProcessingJob highPriorityJob = ProcessingJob.builder()
                .jobId("job_highest_priority")
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(1)
                .inputBucket("test-bucket")
                .inputObjectKey("file_highest.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        entityManager.persistAndFlush(lowPriorityJob);
        entityManager.persistAndFlush(highPriorityJob);

        // When
        List<ProcessingJob> prioritizedJobs = jobRepository.findByStatusOrderByPriorityAndCreatedAt(
                ProcessingJob.ProcessingStatus.QUEUED);

        // Then
        assertEquals(3, prioritizedJobs.size());
        // Should be ordered by priority (ascending), then by created date
        assertEquals("job_highest_priority", prioritizedJobs.get(0).getJobId());
        assertEquals(1, prioritizedJobs.get(0).getPriority());
        assertEquals("job_001", prioritizedJobs.get(1).getJobId());
        assertEquals(5, prioritizedJobs.get(1).getPriority());
        assertEquals("job_low_priority", prioritizedJobs.get(2).getJobId());
        assertEquals(9, prioritizedJobs.get(2).getPriority());
    }

    @Test
    void findJobsExceededMaxRetries_ShouldReturnJobsWithExceededRetries() {
        // Given - Create a job that has exceeded max retries
        ProcessingJob failedJob = ProcessingJob.builder()
                .jobId("job_failed_max_retries")
                .status(ProcessingJob.ProcessingStatus.FAILED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_failed.fits")
                .maxRetries(3)
                .retryCount(3) // Equals max retries
                .errorMessage("Processing failed repeatedly")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        entityManager.persistAndFlush(failedJob);

        // When
        List<ProcessingJob> exceededJobs = jobRepository.findJobsExceededMaxRetries();

        // Then
        assertEquals(1, exceededJobs.size());
        assertEquals("job_failed_max_retries", exceededJobs.get(0).getJobId());
        assertTrue(exceededJobs.get(0).getRetryCount() >= exceededJobs.get(0).getMaxRetries());
    }

    @Test
    void findLongRunningJobs_ShouldReturnJobsRunningTooLong() {
        // Given - Create a job that started a long time ago
        LocalDateTime longAgo = LocalDateTime.now().minusHours(5);
        ProcessingJob longRunningJob = ProcessingJob.builder()
                .jobId("job_long_running")
                .status(ProcessingJob.ProcessingStatus.RUNNING)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_long.fits")
                .maxRetries(3)
                .retryCount(0)
                .createdAt(longAgo)
                .startedAt(longAgo)
                .build();

        entityManager.persistAndFlush(longRunningJob);

        // When
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        List<ProcessingJob> longRunningJobs = jobRepository.findLongRunningJobs(threshold);

        // Then
        assertEquals(1, longRunningJobs.size());
        assertEquals("job_long_running", longRunningJobs.get(0).getJobId());
    }

    @Test
    void findJobsWithErrorPattern_ShouldReturnJobsMatchingErrorPattern() {
        // Given - Create jobs with specific error messages
        ProcessingJob errorJob1 = ProcessingJob.builder()
                .jobId("job_error_1")
                .status(ProcessingJob.ProcessingStatus.FAILED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_error1.fits")
                .maxRetries(3)
                .retryCount(1)
                .errorMessage("File corruption detected")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        ProcessingJob errorJob2 = ProcessingJob.builder()
                .jobId("job_error_2")
                .status(ProcessingJob.ProcessingStatus.FAILED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_error2.fits")
                .maxRetries(3)
                .retryCount(1)
                .errorMessage("Network timeout occurred")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        entityManager.persistAndFlush(errorJob1);
        entityManager.persistAndFlush(errorJob2);

        // When
        List<ProcessingJob> corruptionErrors = jobRepository.findJobsWithErrorPattern("corruption");
        List<ProcessingJob> timeoutErrors = jobRepository.findJobsWithErrorPattern("timeout");

        // Then
        assertEquals(1, corruptionErrors.size());
        assertEquals("job_error_1", corruptionErrors.get(0).getJobId());

        assertEquals(1, timeoutErrors.size());
        assertEquals("job_error_2", timeoutErrors.get(0).getJobId());
    }

    @Test
    void findJobsForRetry_ShouldReturnRetriableJobs() {
        // Given - Create a failed job that can be retried
        ProcessingJob retriableJob = ProcessingJob.builder()
                .jobId("job_retriable")
                .status(ProcessingJob.ProcessingStatus.FAILED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_retriable.fits")
                .maxRetries(3)
                .retryCount(1) // Less than max retries
                .errorMessage("Temporary failure")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        entityManager.persistAndFlush(retriableJob);

        // When
        List<ProcessingJob> retriableJobs = jobRepository.findJobsForRetry();

        // Then
        assertEquals(1, retriableJobs.size());
        assertEquals("job_retriable", retriableJobs.get(0).getJobId());
        assertTrue(retriableJobs.get(0).getRetryCount() < retriableJobs.get(0).getMaxRetries());
    }

    @Test
    void findByPriorityBetween_ShouldReturnJobsInPriorityRange() {
        // When
        List<ProcessingJob> mediumPriorityJobs = jobRepository.findByPriorityBetween(3, 7);

        // Then
        assertEquals(3, mediumPriorityJobs.size());
        assertTrue(mediumPriorityJobs.stream().allMatch(job -> 
            job.getPriority() >= 3 && job.getPriority() <= 7));
    }

    @Test
    void getAverageProcessingTime_ShouldReturnCorrectAverage() {
        // Given - Add more completed jobs with processing times
        ProcessingJob completedJob2 = ProcessingJob.builder()
                .jobId("job_completed_2")
                .status(ProcessingJob.ProcessingStatus.COMPLETED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_completed2.fits")
                .maxRetries(3)
                .retryCount(0)
                .processingDurationMs(1800000L) // 30 minutes
                .createdAt(LocalDateTime.now().minusDays(1))
                .completedAt(LocalDateTime.now().minusDays(1).plusMinutes(30))
                .build();

        entityManager.persistAndFlush(completedJob2);

        // When
        Double averageTime = jobRepository.getAverageProcessingTime();

        // Then
        assertNotNull(averageTime);
        // Average of 3600000L (1 hour) and 1800000L (30 min) = 2700000L (45 min)
        assertEquals(2700000.0, averageTime, 1.0);
    }

    @Test
    void persistenceLifecycle_ShouldHandleEntityLifecycle() {
        // Given
        ProcessingJob newJob = ProcessingJob.builder()
                .jobId("job_lifecycle_test")
                .status(ProcessingJob.ProcessingStatus.QUEUED)
                .processingType(ProcessingJob.ProcessingType.FULL_CALIBRATION)
                .priority(5)
                .inputBucket("test-bucket")
                .inputObjectKey("file_lifecycle.fits")
                .maxRetries(3)
                .retryCount(0)
                .build();

        // When - Save
        ProcessingJob saved = jobRepository.save(newJob);
        entityManager.flush();

        // Then - Verify auto-generated fields
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        // When - Update
        saved.setStatus(ProcessingJob.ProcessingStatus.RUNNING);
        saved.setStartedAt(LocalDateTime.now());
        ProcessingJob updated = jobRepository.save(saved);
        entityManager.flush();

        // Then - Verify update timestamp changed
        assertNotNull(updated.getUpdatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(updated.getCreatedAt()));

        // When - Delete
        jobRepository.delete(updated);
        entityManager.flush();

        // Then - Verify deletion
        Optional<ProcessingJob> deleted = jobRepository.findByJobId("job_lifecycle_test");
        assertFalse(deleted.isPresent());
    }
}