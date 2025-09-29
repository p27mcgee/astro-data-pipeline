-- Remove A/B testing functionality and traffic splitting for experimental workflows
-- A/B testing is not appropriate for deterministic astronomical data processing

-- =====================================================
-- Remove A/B testing function
-- =====================================================

-- Drop the A/B testing function as it's not suitable for deterministic data processing
DROP FUNCTION IF EXISTS setup_ab_test(VARCHAR, VARCHAR, VARCHAR, VARCHAR, DECIMAL, DECIMAL, VARCHAR, TEXT);

-- =====================================================
-- Update activation function to remove experimental traffic splitting
-- =====================================================

-- Replace the existing activation function to enforce 100% activation for all workflows
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

    -- All workflows must be 100% or 0% - no traffic splitting for deterministic processing
    IF p_traffic_split != 0.0 AND p_traffic_split != 100.0 THEN
        RAISE EXCEPTION 'Astronomical data processing requires deterministic results. Use 100%% or deactivate (0%%).';
    END IF;

    -- Production workflows: enforce single active version
    IF p_processing_type = 'prod' THEN
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
        -- Experimental workflows: deactivate others if requested for clean comparison
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
            'deterministic_processing_enforced', true
        )
    );

    RETURN true;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Add function for experimental workflow duplication
-- =====================================================

CREATE OR REPLACE FUNCTION start_experimental_duplication(
    p_workflow_name VARCHAR,
    p_experimental_version VARCHAR,
    p_researcher_id VARCHAR,
    p_hypothesis TEXT,
    p_production_dataset_ids TEXT[], -- Array of dataset IDs
    p_priority VARCHAR DEFAULT 'NORMAL'
)
RETURNS TABLE(
    duplication_id VARCHAR,
    status VARCHAR,
    estimated_completion TIMESTAMP WITH TIME ZONE
) AS $$
DECLARE
    exp_workflow_active BOOLEAN;
    prod_workflow_exists BOOLEAN;
    duplication_id VARCHAR;
    estimated_duration INTERVAL;
BEGIN
    -- Generate unique duplication ID
    duplication_id := 'exp-dup-' || extract(epoch from current_timestamp)::text || '-' || substring(md5(random()::text), 1, 8);

    -- Validate experimental workflow is active
    SELECT is_active INTO exp_workflow_active
    FROM workflow_versions
    WHERE workflow_name = p_workflow_name
    AND workflow_version = p_experimental_version
    AND processing_type = 'exp'
    LIMIT 1;

    IF NOT FOUND OR NOT exp_workflow_active THEN
        RAISE EXCEPTION 'Experimental workflow not found or not active: % %', p_workflow_name, p_experimental_version;
    END IF;

    -- Validate production workflow exists
    SELECT EXISTS(
        SELECT 1 FROM workflow_versions
        WHERE workflow_name = p_workflow_name
        AND processing_type = 'prod'
        AND is_active = true
    ) INTO prod_workflow_exists;

    IF NOT prod_workflow_exists THEN
        RAISE EXCEPTION 'No active production workflow found for comparison: %', p_workflow_name;
    END IF;

    -- Estimate processing duration (15 minutes per dataset)
    estimated_duration := INTERVAL '15 minutes' * array_length(p_production_dataset_ids, 1);

    -- Log the duplication request
    INSERT INTO workflow_activation_history (
        workflow_name, workflow_version, processing_type, action,
        performed_by, reason, metadata
    ) VALUES (
        p_workflow_name, p_experimental_version, 'exp', 'duplicate',
        p_researcher_id, p_hypothesis,
        jsonb_build_object(
            'duplication_id', duplication_id,
            'dataset_count', array_length(p_production_dataset_ids, 1),
            'priority', p_priority,
            'production_dataset_ids', to_jsonb(p_production_dataset_ids)
        )
    );

    -- Return duplication details
    RETURN QUERY SELECT
        duplication_id::VARCHAR,
        'INITIATED'::VARCHAR,
        (CURRENT_TIMESTAMP + estimated_duration)::TIMESTAMP WITH TIME ZONE;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Update workflow summary view to reflect deterministic processing
-- =====================================================

CREATE OR REPLACE VIEW workflow_summary AS
SELECT
    wv.workflow_name,
    wv.processing_type,
    COUNT(*) as total_versions,
    COUNT(*) FILTER (WHERE wv.is_active = true) as active_versions,
    MAX(wv.created_at) as latest_version_date,
    STRING_AGG(
        CASE WHEN wv.is_active THEN wv.workflow_version END,
        ', ' ORDER BY wv.activated_at DESC
    ) as active_versions_list,
    CASE
        WHEN wv.processing_type = 'prod' AND COUNT(*) FILTER (WHERE wv.is_active = true) > 1
        THEN 'WARNING: Multiple active production versions'
        WHEN COUNT(*) FILTER (WHERE wv.is_active = true AND wv.traffic_split_percentage != 100.0) > 0
        THEN 'WARNING: Non-deterministic traffic splitting detected'
        ELSE 'OK'
    END as status
FROM workflow_versions wv
GROUP BY wv.workflow_name, wv.processing_type
ORDER BY wv.workflow_name, wv.processing_type;

-- =====================================================
-- Clean up existing traffic splitting data
-- =====================================================

-- Set all active workflows to 100% traffic (deterministic processing)
UPDATE workflow_versions
SET traffic_split_percentage = 100.0,
    activation_reason = COALESCE(activation_reason, '') || ' (Updated for deterministic processing)'
WHERE is_active = true
AND traffic_split_percentage != 100.0;

-- Set all inactive workflows to 0% traffic
UPDATE workflow_versions
SET traffic_split_percentage = 0.0
WHERE is_active = false
AND traffic_split_percentage != 0.0;

-- =====================================================
-- Add monitoring for deterministic processing compliance
-- =====================================================

-- View to detect any non-deterministic configurations
CREATE VIEW deterministic_processing_violations AS
SELECT
    workflow_name,
    workflow_version,
    processing_type,
    traffic_split_percentage,
    is_active,
    'Non-deterministic traffic splitting' as violation_type
FROM workflow_versions
WHERE is_active = true
AND traffic_split_percentage != 100.0

UNION ALL

SELECT
    workflow_name,
    'N/A' as workflow_version,
    processing_type,
    0 as traffic_split_percentage,
    true as is_active,
    'Multiple active production versions' as violation_type
FROM (
    SELECT workflow_name, processing_type, COUNT(*) as active_count
    FROM workflow_versions
    WHERE processing_type = 'prod' AND is_active = true
    GROUP BY workflow_name, processing_type
    HAVING COUNT(*) > 1
) violations;

-- =====================================================
-- Update comments and documentation
-- =====================================================

COMMENT ON FUNCTION activate_workflow_version(VARCHAR, VARCHAR, VARCHAR, DECIMAL, VARCHAR, TEXT, BOOLEAN)
IS 'Activates workflow versions with deterministic 100% processing - no traffic splitting allowed for astronomical data processing';

COMMENT ON FUNCTION start_experimental_duplication(VARCHAR, VARCHAR, VARCHAR, TEXT, TEXT[], VARCHAR)
IS 'Starts experimental workflow duplication on production datasets for comprehensive side-by-side comparison';

COMMENT ON VIEW deterministic_processing_violations
IS 'Monitoring view to detect violations of deterministic processing requirements (should always be empty)';

-- Add history entry for A/B testing removal
INSERT INTO workflow_activation_history (
    workflow_name, workflow_version, processing_type, action,
    performed_by, reason, new_state
) VALUES (
    'system', 'ab-testing-removal', 'system', 'deactivate',
    'system-migration', 'Removed A/B testing functionality - not appropriate for deterministic astronomical data processing',
    jsonb_build_object(
        'ab_testing_removed', true,
        'traffic_splitting_removed', true,
        'deterministic_processing_enforced', true,
        'migration_version', 'V2_4'
    )
);