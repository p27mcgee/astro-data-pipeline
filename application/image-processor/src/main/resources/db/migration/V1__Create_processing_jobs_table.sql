-- Create processing_jobs table with all core fields
-- Based on ProcessingJob entity in org.stsci.astro.processor.entity.ProcessingJob

CREATE TABLE processing_jobs (
    id                      BIGSERIAL PRIMARY KEY,
    job_id                  VARCHAR(36) UNIQUE NOT NULL,
    input_bucket            VARCHAR(255) NOT NULL,
    input_object_key        VARCHAR(255) NOT NULL,
    output_bucket           VARCHAR(255),
    output_object_key       VARCHAR(255),
    user_id                 VARCHAR(255),
    status                  VARCHAR(50) NOT NULL,
    processing_type         VARCHAR(50) NOT NULL,
    priority                INTEGER DEFAULT 5,
    retry_count             INTEGER DEFAULT 0,
    max_retries             INTEGER DEFAULT 3,
    error_message           TEXT,
    stack_trace             TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    processing_duration_ms  BIGINT,
    input_file_size_bytes   BIGINT,
    output_file_size_bytes  BIGINT,

    -- Processing Metrics (embedded class fields)
    cpu_time_ms             BIGINT,
    memory_peak_mb          BIGINT,
    io_read_bytes           BIGINT,
    io_write_bytes          BIGINT,
    network_download_bytes  BIGINT,
    network_upload_bytes    BIGINT,
    processing_fps          DOUBLE PRECISION,
    cosmic_rays_detected    INTEGER,
    image_quality_score     DOUBLE PRECISION
);

-- Create indexes for common query patterns
CREATE INDEX idx_processing_jobs_status ON processing_jobs(status);
CREATE INDEX idx_processing_jobs_created_at ON processing_jobs(created_at);
CREATE INDEX idx_processing_jobs_object_key ON processing_jobs(input_object_key);
CREATE INDEX idx_processing_jobs_user_id ON processing_jobs(user_id);
CREATE INDEX idx_processing_jobs_priority ON processing_jobs(priority);
CREATE INDEX idx_processing_jobs_type ON processing_jobs(processing_type);

-- Comments for documentation
COMMENT ON TABLE processing_jobs IS 'Main table for tracking astronomical image processing jobs';
COMMENT ON COLUMN processing_jobs.job_id IS 'Unique identifier for each processing job (UUID format)';
COMMENT ON COLUMN processing_jobs.status IS 'Current status: QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, RETRY';
COMMENT ON COLUMN processing_jobs.processing_type IS 'Type of processing: FULL_CALIBRATION, DARK_SUBTRACTION_ONLY, etc.';
COMMENT ON COLUMN processing_jobs.priority IS 'Job priority (1=highest, 10=lowest)';
COMMENT ON COLUMN processing_jobs.processing_fps IS 'Processing speed in frames per second';
COMMENT ON COLUMN processing_jobs.cosmic_rays_detected IS 'Number of cosmic ray hits detected and removed';
COMMENT ON COLUMN processing_jobs.image_quality_score IS 'Overall quality score of processed image (0.0-1.0)';