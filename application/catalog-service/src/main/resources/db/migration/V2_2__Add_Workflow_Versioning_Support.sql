-- Add workflow versioning and activation support
-- This migration adds comprehensive workflow version management for production vs experimental workflows

-- =====================================================
-- Enhance existing processing_contexts table
-- =====================================================

-- Add workflow versioning columns to processing_contexts
ALTER TABLE processing_contexts
ADD COLUMN workflow_name VARCHAR(255),
ADD COLUMN workflow_version VARCHAR(50),
ADD COLUMN is_active BOOLEAN DEFAULT true,
ADD COLUMN is_default BOOLEAN DEFAULT false,
ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN activated_by VARCHAR(100),
ADD COLUMN activation_reason TEXT,
ADD COLUMN traffic_split_percentage DECIMAL(5,2) DEFAULT 100.0;

-- Create indexes for workflow queries
CREATE INDEX idx_processing_contexts_workflow_name ON processing_contexts(workflow_name);
CREATE INDEX idx_processing_contexts_workflow_version ON processing_contexts(workflow_version);
CREATE INDEX idx_processing_contexts_active ON processing_contexts(is_active) WHERE is_active = true;
CREATE INDEX idx_processing_contexts_default ON processing_contexts(is_default) WHERE is_default = true;
CREATE INDEX idx_processing_contexts_workflow_active ON processing_contexts(workflow_name, is_active) WHERE is_active = true;

-- =====================================================
-- Workflow Versions Management Table
-- =====================================================

CREATE TABLE workflow_versions (
    id BIGSERIAL PRIMARY KEY,
    workflow_name VARCHAR(255) NOT NULL,
    workflow_version VARCHAR(50) NOT NULL,
    processing_type VARCHAR(20) NOT NULL CHECK (processing_type IN ('prod', 'exp', 'test', 'val', 'repr')),

    -- Activation state
    is_active BOOLEAN DEFAULT false,
    is_default BOOLEAN DEFAULT false,
    traffic_split_percentage DECIMAL(5,2) DEFAULT 0.0,

    -- Lifecycle tracking
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP WITH TIME ZONE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,

    -- Management metadata
    activated_by VARCHAR(100),
    deactivated_by VARCHAR(100),
    activation_reason TEXT,
    deactivation_reason TEXT,

    -- Performance and quality metrics
    performance_metrics JSONB,
    quality_metrics JSONB,
    usage_statistics JSONB,

    -- Configuration
    algorithm_configuration JSONB,
    parameter_overrides JSONB,

    -- Constraints
    UNIQUE(workflow_name, workflow_version, processing_type),

    -- Ensure traffic split percentages are valid
    CONSTRAINT valid_traffic_split CHECK (traffic_split_percentage >= 0.0 AND traffic_split_percentage <= 100.0)
);

-- Create indexes for workflow versions
CREATE INDEX idx_workflow_versions_name ON workflow_versions(workflow_name);
CREATE INDEX idx_workflow_versions_type ON workflow_versions(processing_type);
CREATE INDEX idx_workflow_versions_active ON workflow_versions(is_active) WHERE is_active = true;
CREATE INDEX idx_workflow_versions_default ON workflow_versions(is_default) WHERE is_default = true;
CREATE INDEX idx_workflow_versions_name_type_active ON workflow_versions(workflow_name, processing_type, is_active) WHERE is_active = true;
CREATE INDEX idx_workflow_versions_created_at ON workflow_versions(created_at);
CREATE INDEX idx_workflow_versions_last_used ON workflow_versions(last_used_at);

-- =====================================================
-- Workflow Activation History Table
-- =====================================================

CREATE TABLE workflow_activation_history (
    id BIGSERIAL PRIMARY KEY,
    workflow_name VARCHAR(255) NOT NULL,
    workflow_version VARCHAR(50) NOT NULL,
    processing_type VARCHAR(20) NOT NULL,

    -- Action details
    action VARCHAR(20) NOT NULL CHECK (action IN ('activate', 'deactivate', 'promote', 'rollback')),
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    performed_by VARCHAR(100) NOT NULL,
    reason TEXT,

    -- State changes
    previous_state JSONB,
    new_state JSONB,

    -- Context
    session_id VARCHAR(255),
    related_processing_id VARCHAR(100),

    -- Additional metadata
    metadata JSONB
);

-- Create indexes for activation history
CREATE INDEX idx_workflow_activation_history_workflow ON workflow_activation_history(workflow_name, workflow_version);
CREATE INDEX idx_workflow_activation_history_action ON workflow_activation_history(action);
CREATE INDEX idx_workflow_activation_history_performed_at ON workflow_activation_history(performed_at);
CREATE INDEX idx_workflow_activation_history_performed_by ON workflow_activation_history(performed_by);

-- =====================================================
-- Workflow Dependencies Table
-- =====================================================

CREATE TABLE workflow_dependencies (
    id BIGSERIAL PRIMARY KEY,
    parent_workflow_name VARCHAR(255) NOT NULL,
    parent_workflow_version VARCHAR(50) NOT NULL,
    child_workflow_name VARCHAR(255) NOT NULL,
    child_workflow_version VARCHAR(50) NOT NULL,
    dependency_type VARCHAR(50) NOT NULL, -- 'requires', 'enhances', 'replaces', 'promotes_to'

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    notes TEXT,

    UNIQUE(parent_workflow_name, parent_workflow_version, child_workflow_name, child_workflow_version, dependency_type)
);

-- Create indexes for workflow dependencies
CREATE INDEX idx_workflow_dependencies_parent ON workflow_dependencies(parent_workflow_name, parent_workflow_version);
CREATE INDEX idx_workflow_dependencies_child ON workflow_dependencies(child_workflow_name, child_workflow_version);
CREATE INDEX idx_workflow_dependencies_type ON workflow_dependencies(dependency_type);

-- =====================================================
-- Update existing processing_contexts with workflow info
-- =====================================================

-- Set default workflow names and versions for existing contexts
UPDATE processing_contexts
SET
    workflow_name = CASE
        WHEN processing_type = 'prod' THEN 'standard-production'
        WHEN processing_type = 'exp' AND experiment_context IS NOT NULL THEN
            COALESCE(experiment_context->>'experimentName', 'unnamed-experiment')
        ELSE processing_type || '-workflow'
    END,
    workflow_version = 'v1.0',
    is_active = true,
    is_default = CASE
        WHEN processing_type = 'prod' THEN true
        ELSE false
    END,
    activated_at = created_at,
    activated_by = 'system-migration'
WHERE workflow_name IS NULL;

-- =====================================================
-- Insert workflow versions for existing contexts
-- =====================================================

INSERT INTO workflow_versions (
    workflow_name, workflow_version, processing_type, is_active, is_default,
    traffic_split_percentage, created_at, activated_at, activated_by, activation_reason
)
SELECT DISTINCT
    workflow_name,
    workflow_version,
    processing_type,
    true as is_active,
    CASE WHEN processing_type = 'prod' THEN true ELSE false END as is_default,
    100.0 as traffic_split_percentage,
    MIN(created_at) as created_at,
    MIN(created_at) as activated_at,
    'system-migration' as activated_by,
    'Initial migration from existing processing contexts' as activation_reason
FROM processing_contexts
WHERE workflow_name IS NOT NULL
GROUP BY workflow_name, workflow_version, processing_type;

-- =====================================================
-- Functions for workflow management
-- =====================================================

-- Function to get active workflows for a given type
CREATE OR REPLACE FUNCTION get_active_workflows(p_workflow_name VARCHAR, p_processing_type VARCHAR)
RETURNS TABLE(
    workflow_version VARCHAR,
    traffic_split_percentage DECIMAL,
    activated_at TIMESTAMP WITH TIME ZONE,
    performance_metrics JSONB
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        wv.workflow_version,
        wv.traffic_split_percentage,
        wv.activated_at,
        wv.performance_metrics
    FROM workflow_versions wv
    WHERE wv.workflow_name = p_workflow_name
    AND wv.processing_type = p_processing_type
    AND wv.is_active = true
    ORDER BY wv.traffic_split_percentage DESC, wv.activated_at DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to get default workflow for a type
CREATE OR REPLACE FUNCTION get_default_workflow(p_workflow_name VARCHAR, p_processing_type VARCHAR)
RETURNS TABLE(
    workflow_version VARCHAR,
    algorithm_configuration JSONB,
    parameter_overrides JSONB
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        wv.workflow_version,
        wv.algorithm_configuration,
        wv.parameter_overrides
    FROM workflow_versions wv
    WHERE wv.workflow_name = p_workflow_name
    AND wv.processing_type = p_processing_type
    AND wv.is_default = true
    LIMIT 1;
END;
$$ LANGUAGE plpgsql;

-- Function to activate a workflow version
CREATE OR REPLACE FUNCTION activate_workflow_version(
    p_workflow_name VARCHAR,
    p_workflow_version VARCHAR,
    p_processing_type VARCHAR,
    p_traffic_split DECIMAL,
    p_activated_by VARCHAR,
    p_reason TEXT DEFAULT NULL,
    p_deactivate_others BOOLEAN DEFAULT false
)
RETURNS BOOLEAN AS $$
DECLARE
    version_exists BOOLEAN;
BEGIN
    -- Check if the version exists
    SELECT EXISTS(
        SELECT 1 FROM workflow_versions
        WHERE workflow_name = p_workflow_name
        AND workflow_version = p_workflow_version
        AND processing_type = p_processing_type
    ) INTO version_exists;

    IF NOT version_exists THEN
        RAISE EXCEPTION 'Workflow version not found: % % %', p_workflow_name, p_workflow_version, p_processing_type;
    END IF;

    -- Deactivate other versions if requested
    IF p_deactivate_others THEN
        UPDATE workflow_versions
        SET
            is_active = false,
            deactivated_at = CURRENT_TIMESTAMP,
            deactivated_by = p_activated_by,
            deactivation_reason = 'Deactivated by activation of ' || p_workflow_version,
            traffic_split_percentage = 0.0
        WHERE workflow_name = p_workflow_name
        AND processing_type = p_processing_type
        AND workflow_version != p_workflow_version
        AND is_active = true;
    END IF;

    -- Activate the specified version
    UPDATE workflow_versions
    SET
        is_active = true,
        activated_at = CURRENT_TIMESTAMP,
        activated_by = p_activated_by,
        activation_reason = p_reason,
        traffic_split_percentage = p_traffic_split,
        last_used_at = CURRENT_TIMESTAMP
    WHERE workflow_name = p_workflow_name
    AND workflow_version = p_workflow_version
    AND processing_type = p_processing_type;

    -- Log the activation
    INSERT INTO workflow_activation_history (
        workflow_name, workflow_version, processing_type, action,
        performed_by, reason, new_state
    ) VALUES (
        p_workflow_name, p_workflow_version, p_processing_type, 'activate',
        p_activated_by, p_reason,
        jsonb_build_object(
            'is_active', true,
            'traffic_split_percentage', p_traffic_split,
            'deactivate_others', p_deactivate_others
        )
    );

    RETURN true;
END;
$$ LANGUAGE plpgsql;

-- Function to deactivate a workflow version
CREATE OR REPLACE FUNCTION deactivate_workflow_version(
    p_workflow_name VARCHAR,
    p_workflow_version VARCHAR,
    p_processing_type VARCHAR,
    p_deactivated_by VARCHAR,
    p_reason TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
BEGIN
    -- Deactivate the version
    UPDATE workflow_versions
    SET
        is_active = false,
        deactivated_at = CURRENT_TIMESTAMP,
        deactivated_by = p_deactivated_by,
        deactivation_reason = p_reason,
        traffic_split_percentage = 0.0
    WHERE workflow_name = p_workflow_name
    AND workflow_version = p_workflow_version
    AND processing_type = p_processing_type;

    -- Log the deactivation
    INSERT INTO workflow_activation_history (
        workflow_name, workflow_version, processing_type, action,
        performed_by, reason, new_state
    ) VALUES (
        p_workflow_name, p_workflow_version, p_processing_type, 'deactivate',
        p_deactivated_by, p_reason,
        jsonb_build_object('is_active', false, 'traffic_split_percentage', 0.0)
    );

    RETURN true;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Views for easy workflow management
-- =====================================================

-- Active workflows view
CREATE VIEW active_workflows AS
SELECT
    wv.workflow_name,
    wv.workflow_version,
    wv.processing_type,
    wv.traffic_split_percentage,
    wv.activated_at,
    wv.activated_by,
    wv.activation_reason,
    wv.performance_metrics,
    wv.last_used_at
FROM workflow_versions wv
WHERE wv.is_active = true
ORDER BY wv.workflow_name, wv.processing_type, wv.traffic_split_percentage DESC;

-- Workflow summary view
CREATE VIEW workflow_summary AS
SELECT
    wv.workflow_name,
    wv.processing_type,
    COUNT(*) as total_versions,
    COUNT(*) FILTER (WHERE wv.is_active = true) as active_versions,
    MAX(wv.created_at) as latest_version_date,
    STRING_AGG(
        CASE WHEN wv.is_active THEN wv.workflow_version END,
        ', ' ORDER BY wv.traffic_split_percentage DESC
    ) as active_versions_list
FROM workflow_versions wv
GROUP BY wv.workflow_name, wv.processing_type
ORDER BY wv.workflow_name, wv.processing_type;

-- Recent workflow changes view
CREATE VIEW recent_workflow_changes AS
SELECT
    wah.workflow_name,
    wah.workflow_version,
    wah.processing_type,
    wah.action,
    wah.performed_at,
    wah.performed_by,
    wah.reason
FROM workflow_activation_history wah
WHERE wah.performed_at >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY wah.performed_at DESC;

-- =====================================================
-- Constraints and triggers
-- =====================================================

-- Trigger to update last_used_at when workflow is used
CREATE OR REPLACE FUNCTION update_workflow_last_used()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE workflow_versions
    SET last_used_at = CURRENT_TIMESTAMP
    WHERE workflow_name = NEW.workflow_name
    AND workflow_version = NEW.workflow_version
    AND processing_type = NEW.processing_type;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_workflow_usage
    AFTER INSERT ON processing_contexts
    FOR EACH ROW
    WHEN (NEW.workflow_name IS NOT NULL AND NEW.workflow_version IS NOT NULL)
    EXECUTE FUNCTION update_workflow_last_used();

-- Constraint to ensure only one default per workflow type
CREATE UNIQUE INDEX idx_workflow_versions_single_default
ON workflow_versions(workflow_name, processing_type)
WHERE is_default = true;

-- =====================================================
-- Sample data and examples
-- =====================================================

-- Insert some example workflow versions for demonstration
INSERT INTO workflow_versions (
    workflow_name, workflow_version, processing_type, is_active, is_default,
    traffic_split_percentage, activated_by, activation_reason,
    algorithm_configuration, performance_metrics
) VALUES
-- Production cosmic ray removal workflows
('cosmic-ray-removal', 'v1.1', 'prod', true, true, 80.0, 'ops-team',
 'Stable production version',
 '{"algorithm": "lacosmic", "parameters": {"sigclip": 4.0, "niter": 4}}',
 '{"avg_processing_time_ms": 2300, "cosmic_rays_detected_avg": 150, "star_preservation_rate": 0.92}'),

('cosmic-ray-removal', 'v1.2', 'prod', true, false, 20.0, 'ops-team',
 'A/B testing new algorithm parameters',
 '{"algorithm": "lacosmic-v2", "parameters": {"sigclip": 4.5, "niter": 4, "starPreservation": true}}',
 '{"avg_processing_time_ms": 2100, "cosmic_rays_detected_avg": 165, "star_preservation_rate": 0.95}'),

-- Experimental versions
('cosmic-ray-removal', 'v2.0-experimental', 'exp', true, false, 100.0, 'astronomer123',
 'Testing advanced cosmic ray detection with ML enhancement',
 '{"algorithm": "ml-enhanced-lacosmic", "parameters": {"ml_model": "cosmic_ray_v2", "confidence_threshold": 0.85}}',
 '{"avg_processing_time_ms": 1800, "cosmic_rays_detected_avg": 180, "star_preservation_rate": 0.97}'),

-- Bias subtraction workflows
('bias-subtraction', 'v1.0', 'prod', true, true, 100.0, 'system-migration',
 'Standard bias subtraction workflow',
 '{"algorithm": "median-overscan", "parameters": {"overscanCorrection": true, "fitMethod": "median"}}',
 '{"avg_processing_time_ms": 800, "bias_correction_accuracy": 0.98}');

-- Create some workflow dependencies
INSERT INTO workflow_dependencies (
    parent_workflow_name, parent_workflow_version,
    child_workflow_name, child_workflow_version,
    dependency_type, created_by, notes
) VALUES
('cosmic-ray-removal', 'v2.0-experimental', 'cosmic-ray-removal', 'v1.3', 'promotes_to', 'astronomer123',
 'Experimental version v2.0 is being prepared for promotion to production v1.3'),

('bias-subtraction', 'v1.0', 'cosmic-ray-removal', 'v1.1', 'requires', 'system',
 'Cosmic ray removal requires bias subtraction to be completed first');

-- =====================================================
-- Comments and documentation
-- =====================================================

COMMENT ON TABLE workflow_versions IS 'Manages different versions of processing workflows with activation states';
COMMENT ON COLUMN workflow_versions.traffic_split_percentage IS 'Percentage of traffic this version should receive (0-100)';
COMMENT ON COLUMN workflow_versions.is_active IS 'Whether this workflow version is currently active for new processing';
COMMENT ON COLUMN workflow_versions.is_default IS 'Whether this is the default version for the workflow type';

COMMENT ON TABLE workflow_activation_history IS 'Audit trail of all workflow activation/deactivation actions';
COMMENT ON TABLE workflow_dependencies IS 'Tracks relationships between different workflow versions';

COMMENT ON VIEW active_workflows IS 'Shows all currently active workflow versions with their traffic allocation';
COMMENT ON VIEW workflow_summary IS 'Summary statistics for each workflow including version counts';

COMMENT ON FUNCTION activate_workflow_version(VARCHAR, VARCHAR, VARCHAR, DECIMAL, VARCHAR, TEXT, BOOLEAN)
IS 'Activates a workflow version with optional traffic splitting and deactivation of others';

COMMENT ON FUNCTION deactivate_workflow_version(VARCHAR, VARCHAR, VARCHAR, VARCHAR, TEXT)
IS 'Deactivates a workflow version and logs the action';