-- Create processing_job_steps table for tracking completed processing steps
-- Based on @ElementCollection List<ProcessingStep> completedSteps in ProcessingJob entity

CREATE TABLE processing_job_steps (
    job_id      BIGINT NOT NULL,
    step_name   VARCHAR(255) NOT NULL,

    PRIMARY KEY (job_id, step_name),
    FOREIGN KEY (job_id) REFERENCES processing_jobs(id) ON DELETE CASCADE
);

-- Index for efficient step queries
CREATE INDEX idx_processing_job_steps_name ON processing_job_steps(step_name);

-- Comments for documentation
COMMENT ON TABLE processing_job_steps IS 'Tracks completed processing steps for each job';
COMMENT ON COLUMN processing_job_steps.step_name IS 'Processing step name (e.g., DOWNLOAD_INPUT, DARK_SUBTRACTION, COSMIC_RAY_REMOVAL)';

-- Valid processing steps (for reference)
/*
Valid step_name values based on ProcessingStep enum:
- DOWNLOAD_INPUT
- VALIDATE_FITS
- DARK_SUBTRACTION
- FLAT_CORRECTION
- COSMIC_RAY_REMOVAL
- IMAGE_REGISTRATION
- IMAGE_STACKING
- QUALITY_ASSESSMENT
- GENERATE_THUMBNAIL
- EXTRACT_METADATA
- UPLOAD_OUTPUT
- UPDATE_CATALOG
- CLEANUP
*/