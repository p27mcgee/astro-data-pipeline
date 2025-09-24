-- Create processing_job_metadata table for storing job metadata as key-value pairs
-- Based on @ElementCollection Map<String, String> metadata in ProcessingJob entity

CREATE TABLE processing_job_metadata (
    job_id          BIGINT NOT NULL,
    metadata_key    VARCHAR(255) NOT NULL,
    metadata_value  TEXT,

    PRIMARY KEY (job_id, metadata_key),
    FOREIGN KEY (job_id) REFERENCES processing_jobs(id) ON DELETE CASCADE
);

-- Index for efficient metadata queries
CREATE INDEX idx_processing_job_metadata_key ON processing_job_metadata(metadata_key);
CREATE INDEX idx_processing_job_metadata_value ON processing_job_metadata(metadata_value);

-- Comments for documentation
COMMENT ON TABLE processing_job_metadata IS 'Key-value metadata storage for processing jobs';
COMMENT ON COLUMN processing_job_metadata.metadata_key IS 'Metadata key (e.g., instrument, filter, telescope)';
COMMENT ON COLUMN processing_job_metadata.metadata_value IS 'Metadata value (stored as text for flexibility)';