-- Add processing context and experiment tracking tables
-- This migration adds comprehensive processing ID support for production vs experimental data

-- =====================================================
-- Processing Contexts Table
-- =====================================================

CREATE TABLE processing_contexts (
    processing_id VARCHAR(100) PRIMARY KEY,
    processing_type VARCHAR(20) NOT NULL CHECK (processing_type IN ('prod', 'exp', 'test', 'val', 'repr')),
    session_id VARCHAR(255) NOT NULL,
    pipeline_version VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_parameters JSONB,

    -- Production-specific fields
    observation_id VARCHAR(255),
    instrument_id VARCHAR(100),
    telescope_id VARCHAR(100),
    program_id VARCHAR(100),
    proposal_id VARCHAR(100),
    observation_date DATE,
    priority INTEGER,
    data_release_version VARCHAR(50),
    calibration_frame_versions JSONB,

    -- Experiment-specific fields
    experiment_name VARCHAR(255),
    experiment_description TEXT,
    researcher_id VARCHAR(100),
    researcher_email VARCHAR(255),
    project_id VARCHAR(100),
    hypothesis TEXT,
    experiment_parameters JSONB,
    parent_experiment_id VARCHAR(100),
    experiment_start_time TIMESTAMP WITH TIME ZONE,

    -- Data lineage fields
    input_image_id VARCHAR(255),
    input_image_path VARCHAR(1000),
    input_image_checksum VARCHAR(128),
    calibration_frames JSONB,
    previous_processing_id VARCHAR(100),
    root_processing_id VARCHAR(100),
    processing_depth INTEGER DEFAULT 0,

    -- Partition key for efficient querying
    partition_key VARCHAR(50) GENERATED ALWAYS AS (
        processing_type || '_' || to_char(created_at, 'YYYYMM')
    ) STORED,

    CONSTRAINT fk_previous_processing
        FOREIGN KEY (previous_processing_id) REFERENCES processing_contexts(processing_id),
    CONSTRAINT fk_root_processing
        FOREIGN KEY (root_processing_id) REFERENCES processing_contexts(processing_id)
);

-- Create indexes for efficient querying
CREATE INDEX idx_processing_contexts_type ON processing_contexts(processing_type);
CREATE INDEX idx_processing_contexts_session ON processing_contexts(session_id);
CREATE INDEX idx_processing_contexts_partition ON processing_contexts(partition_key);
CREATE INDEX idx_processing_contexts_created_at ON processing_contexts(created_at);
CREATE INDEX idx_processing_contexts_experiment_name ON processing_contexts(experiment_name)
    WHERE processing_type = 'exp';
CREATE INDEX idx_processing_contexts_researcher ON processing_contexts(researcher_id)
    WHERE processing_type = 'exp';
CREATE INDEX idx_processing_contexts_observation ON processing_contexts(observation_id)
    WHERE processing_type = 'prod';
CREATE INDEX idx_processing_contexts_lineage ON processing_contexts(root_processing_id, processing_depth);

-- =====================================================
-- Add processing_id to existing tables
-- =====================================================

-- Add processing_id to astronomical_objects table
ALTER TABLE astronomical_objects
ADD COLUMN processing_id VARCHAR(100),
ADD COLUMN processing_type VARCHAR(20),
ADD CONSTRAINT fk_astro_objects_processing
    FOREIGN KEY (processing_id) REFERENCES processing_contexts(processing_id);

-- Create index for astronomical objects by processing context
CREATE INDEX idx_astro_objects_processing ON astronomical_objects(processing_id);
CREATE INDEX idx_astro_objects_processing_type ON astronomical_objects(processing_type);

-- Add processing_id to observations table (if it exists)
-- Note: This assumes observations table exists from previous migrations
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'observations') THEN
        ALTER TABLE observations
        ADD COLUMN processing_id VARCHAR(100),
        ADD COLUMN processing_type VARCHAR(20),
        ADD CONSTRAINT fk_observations_processing
            FOREIGN KEY (processing_id) REFERENCES processing_contexts(processing_id);

        CREATE INDEX idx_observations_processing ON observations(processing_id);
        CREATE INDEX idx_observations_processing_type ON observations(processing_type);
    END IF;
END
$$;

-- =====================================================
-- Processing Results Table
-- =====================================================

CREATE TABLE processing_results (
    id BIGSERIAL PRIMARY KEY,
    processing_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    algorithm_id VARCHAR(100) NOT NULL,
    input_path VARCHAR(1000),
    output_path VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('pending', 'running', 'completed', 'failed', 'cancelled')),

    -- Processing metrics
    processing_start_time TIMESTAMP WITH TIME ZONE,
    processing_end_time TIMESTAMP WITH TIME ZONE,
    processing_duration_ms BIGINT,
    memory_usage_mb INTEGER,
    cpu_usage_percent FLOAT,

    -- Algorithm-specific metrics
    algorithm_metrics JSONB,
    quality_metrics JSONB,

    -- Error information
    error_message TEXT,
    error_details JSONB,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_processing_results_context
        FOREIGN KEY (processing_id) REFERENCES processing_contexts(processing_id)
);

-- Create indexes for processing results
CREATE INDEX idx_processing_results_processing_id ON processing_results(processing_id);
CREATE INDEX idx_processing_results_session ON processing_results(session_id);
CREATE INDEX idx_processing_results_status ON processing_results(status);
CREATE INDEX idx_processing_results_step_type ON processing_results(step_type);
CREATE INDEX idx_processing_results_created_at ON processing_results(created_at);

-- =====================================================
-- Experiment Comparisons Table
-- =====================================================

CREATE TABLE experiment_comparisons (
    id BIGSERIAL PRIMARY KEY,
    comparison_name VARCHAR(255) NOT NULL,
    comparison_description TEXT,
    researcher_id VARCHAR(100) NOT NULL,

    -- Processing contexts being compared
    processing_ids VARCHAR(100)[] NOT NULL,
    comparison_type VARCHAR(50) NOT NULL,

    -- Comparison metrics
    comparison_metrics JSONB,
    comparison_results JSONB,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_experiment_comparisons_researcher ON experiment_comparisons(researcher_id);
CREATE INDEX idx_experiment_comparisons_type ON experiment_comparisons(comparison_type);

-- =====================================================
-- Data Partitioning Setup
-- =====================================================

-- Enable partitioning for large tables based on processing type and date
-- This is prepared for future PostgreSQL partitioning implementation

-- Create a view for production data only
CREATE VIEW production_astronomical_objects AS
SELECT * FROM astronomical_objects
WHERE processing_type = 'prod';

-- Create a view for experimental data only
CREATE VIEW experimental_astronomical_objects AS
SELECT * FROM astronomical_objects
WHERE processing_type = 'exp';

-- Create a view for recent processing results (last 30 days)
CREATE VIEW recent_processing_results AS
SELECT * FROM processing_results
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days';

-- =====================================================
-- Functions for processing context management
-- =====================================================

-- Function to generate processing partition key
CREATE OR REPLACE FUNCTION generate_partition_key(p_type VARCHAR, p_date TIMESTAMP)
RETURNS VARCHAR AS $$
BEGIN
    RETURN p_type || '_' || to_char(p_date, 'YYYYMM');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to get S3 key prefix
CREATE OR REPLACE FUNCTION get_s3_key_prefix(p_processing_id VARCHAR)
RETURNS VARCHAR AS $$
DECLARE
    context_record processing_contexts%ROWTYPE;
    date_prefix VARCHAR;
    result_prefix VARCHAR;
BEGIN
    SELECT * INTO context_record
    FROM processing_contexts
    WHERE processing_id = p_processing_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Processing context not found: %', p_processing_id;
    END IF;

    date_prefix := to_char(context_record.created_at, 'YYYY-MM-DD');

    CASE context_record.processing_type
        WHEN 'prod' THEN
            result_prefix := 'production/' || date_prefix || '/' || p_processing_id;
        WHEN 'exp' THEN
            result_prefix := 'experimental/' ||
                           COALESCE(context_record.experiment_name, 'unnamed') || '/' ||
                           date_prefix || '/' || p_processing_id;
        ELSE
            result_prefix := context_record.processing_type || '/' ||
                           date_prefix || '/' || p_processing_id;
    END CASE;

    RETURN result_prefix;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Triggers for updated_at timestamps
-- =====================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER processing_results_updated_at
    BEFORE UPDATE ON processing_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER experiment_comparisons_updated_at
    BEFORE UPDATE ON experiment_comparisons
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- Sample data for testing
-- =====================================================

-- Insert sample production processing context
INSERT INTO processing_contexts (
    processing_id, processing_type, session_id, pipeline_version,
    observation_id, instrument_id, telescope_id, program_id,
    data_release_version, processing_depth
) VALUES (
    'prod-20240928-' || gen_random_uuid(),
    'prod',
    'production-session-001',
    '1.0.0',
    'OBS-2024-001',
    'WFC3',
    'HST',
    'GO-12345',
    'DR1',
    0
);

-- Insert sample experimental processing context
INSERT INTO processing_contexts (
    processing_id, processing_type, session_id, pipeline_version,
    experiment_name, experiment_description, researcher_id, researcher_email,
    project_id, processing_depth
) VALUES (
    'exp-20240928-' || gen_random_uuid(),
    'exp',
    'cosmic-ray-comparison-001',
    '1.0.0',
    'Cosmic Ray Algorithm Comparison',
    'Comparing L.A.Cosmic vs median filter for cosmic ray removal',
    'astronomer123',
    'astronomer@stsci.edu',
    'PROJ-001',
    0
);

-- =====================================================
-- Comments and documentation
-- =====================================================

COMMENT ON TABLE processing_contexts IS 'Tracks all processing workflows with distinction between production and experimental processing';
COMMENT ON COLUMN processing_contexts.processing_id IS 'Unique processing identifier with format: {type}-{date}-{uuid}';
COMMENT ON COLUMN processing_contexts.processing_type IS 'Type of processing: prod, exp, test, val, repr';
COMMENT ON COLUMN processing_contexts.partition_key IS 'Computed partition key for efficient data organization';

COMMENT ON TABLE processing_results IS 'Stores results and metrics for individual processing steps';
COMMENT ON TABLE experiment_comparisons IS 'Tracks comparisons between different experimental processing runs';

COMMENT ON FUNCTION get_s3_key_prefix(VARCHAR) IS 'Generates S3 key prefix based on processing type and context';
COMMENT ON FUNCTION generate_partition_key(VARCHAR, TIMESTAMP) IS 'Generates partition key for data organization';