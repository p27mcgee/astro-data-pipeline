-- Enforce single active production workflow constraint
-- This migration adds database constraints to prevent multiple active production workflows

-- =====================================================
-- Remove A/B testing sample data for production workflows
-- =====================================================

-- Remove the A/B testing sample data that violates single active production constraint
DELETE FROM workflow_versions
WHERE workflow_name = 'cosmic-ray-removal'
AND processing_type = 'prod'
AND workflow_version = 'v1.2';

-- Update remaining production version to 100% traffic
UPDATE workflow_versions
SET traffic_split_percentage = 100.0,
    activation_reason = 'Single active production version - removed A/B testing'
WHERE workflow_name = 'cosmic-ray-removal'
AND processing_type = 'prod'
AND workflow_version = 'v1.1';

-- =====================================================
-- Add constraint to enforce single active production workflow
-- =====================================================

-- Create unique index to enforce only one active production workflow per workflow name
CREATE UNIQUE INDEX idx_workflow_versions_single_active_production
ON workflow_versions(workflow_name)
WHERE processing_type = 'prod' AND is_active = true;

-- =====================================================
-- Update activation function to enforce production constraints
-- =====================================================

-- Replace the existing activation function with production-aware logic
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

    -- Production workflow constraints
    IF p_processing_type = 'prod' THEN
        -- Production workflows must be 100% or 0% - no traffic splitting
        IF p_traffic_split != 0.0 AND p_traffic_split != 100.0 THEN
            RAISE EXCEPTION 'Production workflows do not support traffic splitting. Use 100%% or deactivate.';
        END IF;

        -- Automatically deactivate other production versions (single active constraint)
        UPDATE workflow_versions
        SET
            is_active = false,
            deactivated_at = CURRENT_TIMESTAMP,
            deactivated_by = p_activated_by,
            deactivation_reason = 'Deactivated by single active production constraint for ' || p_workflow_version,
            traffic_split_percentage = 0.0
        WHERE workflow_name = p_workflow_name
        AND processing_type = 'prod'
        AND workflow_version != p_workflow_version
        AND is_active = true;
    ELSE
        -- Experimental workflows can use traffic splitting
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
            'deactivate_others', p_deactivate_others,
            'production_single_active_enforced', p_processing_type = 'prod'
        )
    );

    RETURN true;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Add function to prevent production A/B testing
-- =====================================================

CREATE OR REPLACE FUNCTION setup_ab_test(
    p_workflow_name VARCHAR,
    p_processing_type VARCHAR,
    p_version_a VARCHAR,
    p_version_b VARCHAR,
    p_traffic_percentage_a DECIMAL,
    p_traffic_percentage_b DECIMAL,
    p_performed_by VARCHAR,
    p_reason TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
BEGIN
    -- A/B testing is only allowed for experimental workflows
    IF p_processing_type = 'prod' THEN
        RAISE EXCEPTION 'A/B testing is not supported for production workflows. Production workflows must have a single active version. Use experimental workflows for algorithm comparison.';
    END IF;

    -- Validate traffic percentages sum to 100%
    IF ABS((p_traffic_percentage_a + p_traffic_percentage_b) - 100.0) > 0.01 THEN
        RAISE EXCEPTION 'Traffic percentages must sum to 100%%';
    END IF;

    -- Deactivate other versions for this workflow
    UPDATE workflow_versions
    SET
        is_active = false,
        deactivated_at = CURRENT_TIMESTAMP,
        deactivated_by = p_performed_by,
        deactivation_reason = 'Deactivated for A/B test setup',
        traffic_split_percentage = 0.0
    WHERE workflow_name = p_workflow_name
    AND processing_type = p_processing_type
    AND workflow_version NOT IN (p_version_a, p_version_b)
    AND is_active = true;

    -- Activate version A
    UPDATE workflow_versions
    SET
        is_active = true,
        activated_at = CURRENT_TIMESTAMP,
        activated_by = p_performed_by,
        activation_reason = 'Experimental A/B test setup: ' || p_reason,
        traffic_split_percentage = p_traffic_percentage_a,
        last_used_at = CURRENT_TIMESTAMP
    WHERE workflow_name = p_workflow_name
    AND workflow_version = p_version_a
    AND processing_type = p_processing_type;

    -- Activate version B
    UPDATE workflow_versions
    SET
        is_active = true,
        activated_at = CURRENT_TIMESTAMP,
        activated_by = p_performed_by,
        activation_reason = 'Experimental A/B test setup: ' || p_reason,
        traffic_split_percentage = p_traffic_percentage_b,
        last_used_at = CURRENT_TIMESTAMP
    WHERE workflow_name = p_workflow_name
    AND workflow_version = p_version_b
    AND processing_type = p_processing_type;

    -- Log the A/B test setup
    INSERT INTO workflow_activation_history (
        workflow_name, workflow_version, processing_type, action,
        performed_by, reason, new_state
    ) VALUES (
        p_workflow_name, 'A/B-test', p_processing_type, 'ab-test',
        p_performed_by, p_reason,
        jsonb_build_object(
            'version_a', p_version_a,
            'version_b', p_version_b,
            'traffic_percentage_a', p_traffic_percentage_a,
            'traffic_percentage_b', p_traffic_percentage_b
        )
    );

    RETURN true;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Enhanced monitoring views
-- =====================================================

-- View to detect production constraint violations (should always be empty)
CREATE VIEW production_constraint_violations AS
SELECT
    workflow_name,
    COUNT(*) as active_production_versions,
    STRING_AGG(workflow_version, ', ') as versions
FROM workflow_versions
WHERE processing_type = 'prod' AND is_active = true
GROUP BY workflow_name
HAVING COUNT(*) > 1;

-- Enhanced active workflows view with constraint validation
CREATE OR REPLACE VIEW active_workflows AS
SELECT
    wv.workflow_name,
    wv.workflow_version,
    wv.processing_type,
    wv.traffic_split_percentage,
    wv.activated_at,
    wv.activated_by,
    wv.activation_reason,
    wv.performance_metrics,
    wv.last_used_at,
    CASE
        WHEN wv.processing_type = 'prod' AND wv.traffic_split_percentage != 100.0
        THEN 'WARNING: Production workflow with non-100% traffic'
        ELSE 'OK'
    END as constraint_status
FROM workflow_versions wv
WHERE wv.is_active = true
ORDER BY wv.workflow_name, wv.processing_type, wv.traffic_split_percentage DESC;

-- =====================================================
-- Update existing sample data to be compliant
-- =====================================================

-- Ensure all production workflows have 100% traffic split
UPDATE workflow_versions
SET traffic_split_percentage = 100.0,
    activation_reason = activation_reason || ' (Updated for single active production constraint)'
WHERE processing_type = 'prod'
AND is_active = true
AND traffic_split_percentage != 100.0;

-- =====================================================
-- Comments and documentation
-- =====================================================

COMMENT ON INDEX idx_workflow_versions_single_active_production
IS 'Enforces single active production workflow constraint - only one active production workflow per workflow name';

COMMENT ON FUNCTION setup_ab_test(VARCHAR, VARCHAR, VARCHAR, VARCHAR, DECIMAL, DECIMAL, VARCHAR, TEXT)
IS 'Sets up A/B testing for experimental workflows only - blocks production A/B testing';

COMMENT ON VIEW production_constraint_violations
IS 'Monitoring view to detect violations of single active production constraint (should always be empty)';

-- Add history entry for this constraint enforcement
INSERT INTO workflow_activation_history (
    workflow_name, workflow_version, processing_type, action,
    performed_by, reason, new_state
) VALUES (
    'system', 'constraint-enforcement', 'prod', 'activate',
    'system-migration', 'Enforced single active production workflow constraint',
    jsonb_build_object(
        'constraint_added', 'idx_workflow_versions_single_active_production',
        'production_ab_testing_blocked', true,
        'migration_version', 'V2_3'
    )
);