"""
Custom Airflow Operators for Granular Astronomical Processing

Provides specialized operators for interacting with the granular processing
endpoints implemented in Phase 2 of the flexible processing architecture.

Author: STScI Demo Project - Phase 3 Implementation
"""

from typing import Dict, Any, List, Optional, Sequence
import json
import logging
import requests
from datetime import timedelta

from airflow.models import BaseOperator
from airflow.utils.decorators import apply_defaults
from airflow.exceptions import AirflowException
from airflow.hooks.http_hook import HttpHook
from airflow.providers.http.operators.http import SimpleHttpOperator
from airflow.configuration import conf
from airflow.models import Variable

logger = logging.getLogger(__name__)


class GranularProcessingOperator(BaseOperator):
    """
    Base operator for granular astronomical processing operations.

    Provides common functionality for all granular processing steps including
    error handling, metrics collection, and result management.
    """

    template_fields: Sequence[str] = (
        'image_path', 'calibration_path', 'session_id', 'parameters',
        'output_bucket', 'output_path'
    )

    template_fields_renderers = {
        'parameters': 'json'
    }

    @apply_defaults
    def __init__(
        self,
        image_path: str,
        session_id: str,
        algorithm: str = 'default',
        parameters: Optional[Dict[str, Any]] = None,
        calibration_path: Optional[str] = None,
        output_bucket: Optional[str] = None,
        output_path: Optional[str] = None,
        preserve_metadata: bool = True,
        enable_metrics: bool = True,
        timeout: int = 300,
        retries: int = 1,
        retry_delay: timedelta = timedelta(minutes=3),
        # Processing context parameters
        processing_id: Optional[str] = None,
        processing_type: str = 'production',
        experiment_name: Optional[str] = None,
        researcher_id: Optional[str] = None,
        researcher_email: Optional[str] = None,
        project_id: Optional[str] = None,
        observation_id: Optional[str] = None,
        instrument_id: Optional[str] = None,
        # Workflow versioning parameters
        workflow_name: Optional[str] = None,
        workflow_version: Optional[str] = None,
        use_active_workflow: bool = True,
        force_workflow_version: bool = False,
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.image_path = image_path
        self.session_id = session_id
        self.algorithm = algorithm
        self.parameters = parameters or {}
        self.calibration_path = calibration_path
        self.output_bucket = output_bucket
        self.output_path = output_path
        self.preserve_metadata = preserve_metadata
        self.enable_metrics = enable_metrics
        self.timeout = timeout

        # Processing context attributes
        self.processing_id = processing_id
        self.processing_type = processing_type
        self.experiment_name = experiment_name
        self.researcher_id = researcher_id
        self.researcher_email = researcher_email
        self.project_id = project_id
        self.observation_id = observation_id
        self.instrument_id = instrument_id

        # Workflow versioning attributes
        self.workflow_name = workflow_name
        self.workflow_version = workflow_version
        self.use_active_workflow = use_active_workflow
        self.force_workflow_version = force_workflow_version

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def _build_request_payload(self) -> Dict[str, Any]:
        """Build the request payload for granular processing."""
        payload = {
            'imagePath': self.image_path,
            'sessionId': self.session_id,
            'algorithm': self.algorithm,
            'parameters': self.parameters,
            'preserveMetadata': self.preserve_metadata,
            'enableMetrics': self.enable_metrics,
            'processingType': self.processing_type
        }

        if self.calibration_path:
            payload['calibrationPath'] = self.calibration_path

        if self.output_bucket:
            payload['outputBucket'] = self.output_bucket

        if self.output_path:
            payload['outputPath'] = self.output_path

        if self.processing_id:
            payload['processingId'] = self.processing_id

        # Add experiment context for experimental processing
        if self.processing_type == 'experimental':
            experiment_context = {}
            if self.experiment_name:
                experiment_context['experimentName'] = self.experiment_name
            if self.researcher_id:
                experiment_context['researcherId'] = self.researcher_id
            if self.researcher_email:
                experiment_context['researcherEmail'] = self.researcher_email
            if self.project_id:
                experiment_context['projectId'] = self.project_id

            if experiment_context:
                payload['experimentContext'] = experiment_context

        # Add production context for production processing
        elif self.processing_type == 'production':
            production_context = {}
            if self.observation_id:
                production_context['observationId'] = self.observation_id
            if self.instrument_id:
                production_context['instrumentId'] = self.instrument_id

            if production_context:
                payload['productionContext'] = production_context

        # Add workflow versioning information
        if self.workflow_name and self.workflow_version:
            payload['workflowName'] = self.workflow_name
            payload['workflowVersion'] = self.workflow_version

        return payload

    def _get_active_workflow_info(self, step_type: str) -> Optional[Dict[str, Any]]:
        """Get active workflow information for the processing step."""
        try:
            # Map processing step to workflow name
            workflow_name = self._map_step_to_workflow(step_type)
            if not workflow_name:
                return None

            url = f"{self.base_url}/api/v1/workflows/active"
            params = {'processingType': self.processing_type}

            response = requests.get(url, params=params, timeout=30)
            response.raise_for_status()

            active_workflows = response.json()

            # Find the active workflow for this step
            for workflow in active_workflows:
                if workflow.get('workflowName') == workflow_name:
                    logger.info(f"Using active workflow {workflow_name} version {workflow.get('workflowVersion')} "
                              f"(deterministic processing - always 100%)")

                    return {
                        'workflowName': workflow.get('workflowName'),
                        'workflowVersion': workflow.get('workflowVersion'),
                        'deterministic': True,
                        'activeWorkflowMetadata': {
                            'activatedBy': workflow.get('activatedBy'),
                            'activatedAt': workflow.get('activatedAt'),
                            'algorithmConfiguration': workflow.get('algorithmConfiguration', {})
                        }
                    }

            logger.warning(f"No active workflow found for {workflow_name} in {self.processing_type} mode")
            return None

        except requests.exceptions.RequestException as e:
            logger.warning(f"Failed to get active workflow info: {e}")
            return None

    def _map_step_to_workflow(self, step_type: str) -> Optional[str]:
        """Map processing step type to workflow name."""
        step_workflow_mapping = {
            'bias-subtract': 'bias-subtraction',
            'dark-subtract': 'dark-subtraction',
            'flat-correct': 'flat-field-correction',
            'cosmic-ray-remove': 'cosmic-ray-removal'
        }
        return step_workflow_mapping.get(step_type)

    def _make_request(self, endpoint: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """Make HTTP request to granular processing endpoint."""
        url = f"{self.base_url}/api/v1/processing/steps/{endpoint}"

        try:
            response = requests.post(
                url,
                json=payload,
                headers={'Content-Type': 'application/json'},
                timeout=self.timeout
            )
            response.raise_for_status()

            result = response.json()
            logger.info(f"Processing step {endpoint} completed successfully")

            return result

        except requests.exceptions.Timeout:
            raise AirflowException(f"Processing step {endpoint} timed out after {self.timeout} seconds")
        except requests.exceptions.RequestException as e:
            logger.error(f"Processing step {endpoint} failed: {e}")
            if hasattr(e, 'response') and e.response is not None:
                try:
                    error_detail = e.response.json()
                    logger.error(f"Error details: {error_detail}")
                except:
                    logger.error(f"Response text: {e.response.text}")
            raise AirflowException(f"Failed to execute {endpoint}: {e}")

    def _store_metrics(self, context: Dict, result: Dict[str, Any]) -> None:
        """Store processing metrics in XCom for analysis."""
        if 'processingMetrics' in result:
            metrics = result['processingMetrics']
            context['ti'].xcom_push(
                key=f"{self.task_id}_metrics",
                value=metrics
            )
            logger.info(f"Stored processing metrics for {self.task_id}")

    def execute(self, context: Dict) -> str:
        """Execute the processing step - to be implemented by subclasses."""
        raise NotImplementedError("Subclasses must implement execute method")


class BiasSubtractionOperator(GranularProcessingOperator):
    """
    Operator for bias frame subtraction processing.

    Removes instrumental bias from astronomical images using bias calibration frames.
    """

    @apply_defaults
    def __init__(
        self,
        *args,
        overscan_correction: bool = True,
        fit_method: str = 'median',
        **kwargs
    ) -> None:
        # Set default bias subtraction parameters
        if 'parameters' not in kwargs:
            kwargs['parameters'] = {}

        kwargs['parameters'].update({
            'overscanCorrection': overscan_correction,
            'fitMethod': fit_method
        })

        super().__init__(*args, **kwargs)

    def execute(self, context: Dict) -> str:
        """Execute bias subtraction processing."""
        logger.info(f"Starting bias subtraction for {self.image_path}")

        # Get active workflow info if enabled
        if self.use_active_workflow and not self.force_workflow_version and not self.workflow_name:
            workflow_info = self._get_active_workflow_info('bias-subtract')
            if workflow_info:
                logger.info(f"Using active workflow: {workflow_info}")
                # Store workflow info in XCom for downstream tasks
                context['ti'].xcom_push(key='active_workflow_info', value=workflow_info)

        payload = self._build_request_payload()
        result = self._make_request('bias-subtract', payload)

        # Store metrics
        self._store_metrics(context, result)

        output_path = result['outputPath']
        logger.info(f"Bias subtraction completed: {output_path}")

        return output_path


class DarkSubtractionOperator(GranularProcessingOperator):
    """
    Operator for dark current subtraction processing.

    Removes dark current noise from astronomical images using dark calibration frames.
    """

    @apply_defaults
    def __init__(
        self,
        *args,
        auto_scale: bool = True,
        temperature_correction: bool = False,
        **kwargs
    ) -> None:
        # Set default dark subtraction parameters
        if 'parameters' not in kwargs:
            kwargs['parameters'] = {}

        kwargs['parameters'].update({
            'autoScale': auto_scale,
            'temperatureCorrection': temperature_correction
        })

        super().__init__(*args, **kwargs)

    def execute(self, context: Dict) -> str:
        """Execute dark subtraction processing."""
        logger.info(f"Starting dark subtraction for {self.image_path}")

        payload = self._build_request_payload()
        result = self._make_request('dark-subtract', payload)

        # Store metrics
        self._store_metrics(context, result)

        output_path = result['outputPath']
        logger.info(f"Dark subtraction completed: {output_path}")

        return output_path


class FlatFieldCorrectionOperator(GranularProcessingOperator):
    """
    Operator for flat field correction processing.

    Corrects for variations in detector response using flat field calibration frames.
    """

    @apply_defaults
    def __init__(
        self,
        *args,
        normalization_method: str = 'median',
        illumination_model: str = 'polynomial',
        mask_stars: bool = True,
        **kwargs
    ) -> None:
        # Set default flat correction parameters
        if 'parameters' not in kwargs:
            kwargs['parameters'] = {}

        kwargs['parameters'].update({
            'normalizationMethod': normalization_method,
            'illuminationModel': illumination_model,
            'maskStars': mask_stars
        })

        super().__init__(*args, **kwargs)

    def execute(self, context: Dict) -> str:
        """Execute flat field correction processing."""
        logger.info(f"Starting flat field correction for {self.image_path}")

        payload = self._build_request_payload()
        result = self._make_request('flat-correct', payload)

        # Store metrics
        self._store_metrics(context, result)

        output_path = result['outputPath']
        logger.info(f"Flat field correction completed: {output_path}")

        return output_path


class CosmicRayRemovalOperator(GranularProcessingOperator):
    """
    Operator for cosmic ray detection and removal.

    Identifies and removes cosmic ray hits from astronomical images.
    """

    @apply_defaults
    def __init__(
        self,
        *args,
        sigclip: float = 4.5,
        star_preservation: bool = True,
        niter: int = 4,
        **kwargs
    ) -> None:
        # Set default cosmic ray removal parameters
        if 'parameters' not in kwargs:
            kwargs['parameters'] = {}

        kwargs['parameters'].update({
            'sigclip': sigclip,
            'starPreservation': star_preservation,
            'niter': niter
        })

        super().__init__(*args, **kwargs)

    def execute(self, context: Dict) -> str:
        """Execute cosmic ray removal processing."""
        logger.info(f"Starting cosmic ray removal for {self.image_path}")

        payload = self._build_request_payload()
        result = self._make_request('cosmic-ray-remove', payload)

        # Store metrics
        self._store_metrics(context, result)

        output_path = result['outputPath']
        cosmic_rays_removed = result.get('processingMetrics', {}).get('cosmicRaysRemoved', 0)
        logger.info(f"Cosmic ray removal completed: {output_path} ({cosmic_rays_removed} cosmic rays removed)")

        return output_path


class AlgorithmDiscoveryOperator(BaseOperator):
    """
    Operator for discovering available algorithms for a processing step.

    Queries the algorithm registry to check availability and get algorithm information.
    """

    template_fields: Sequence[str] = ('algorithm_type',)

    @apply_defaults
    def __init__(
        self,
        algorithm_type: str,
        require_supported: bool = True,
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.algorithm_type = algorithm_type
        self.require_supported = require_supported

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def execute(self, context: Dict) -> List[Dict[str, Any]]:
        """Discover available algorithms for the specified type."""
        url = f"{self.base_url}/api/v1/processing/algorithms/{self.algorithm_type}"

        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()

            algorithms = response.json()

            if self.require_supported:
                algorithms = [algo for algo in algorithms if algo.get('supported', False)]

            logger.info(f"Found {len(algorithms)} algorithms for {self.algorithm_type}")

            # Store in XCom for downstream tasks
            context['ti'].xcom_push(
                key=f"{self.algorithm_type}_algorithms",
                value=algorithms
            )

            return algorithms

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to discover algorithms for {self.algorithm_type}: {e}")
            raise AirflowException(f"Algorithm discovery failed: {e}")


class CustomWorkflowOperator(BaseOperator):
    """
    Operator for executing custom processing workflows.

    Orchestrates multiple processing steps in sequence with custom algorithms and parameters.
    """

    template_fields: Sequence[str] = (
        'input_image_path', 'session_id', 'workflow_steps', 'output_configuration'
    )

    template_fields_renderers = {
        'workflow_steps': 'json',
        'output_configuration': 'json'
    }

    @apply_defaults
    def __init__(
        self,
        input_image_path: str,
        session_id: str,
        workflow_steps: List[Dict[str, Any]],
        output_configuration: Optional[Dict[str, Any]] = None,
        timeout: int = 1800,  # 30 minutes for complete workflow
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.input_image_path = input_image_path
        self.session_id = session_id
        self.workflow_steps = workflow_steps
        self.output_configuration = output_configuration or {}
        self.timeout = timeout

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def execute(self, context: Dict) -> Dict[str, Any]:
        """Execute custom workflow with multiple processing steps."""
        url = f"{self.base_url}/api/v1/processing/workflows/custom"

        payload = {
            'inputImagePath': self.input_image_path,
            'sessionId': self.session_id,
            'steps': self.workflow_steps,
            'outputConfiguration': self.output_configuration
        }

        try:
            logger.info(f"Starting custom workflow with {len(self.workflow_steps)} steps")

            response = requests.post(
                url,
                json=payload,
                headers={'Content-Type': 'application/json'},
                timeout=self.timeout
            )
            response.raise_for_status()

            result = response.json()

            # Store comprehensive workflow metrics
            context['ti'].xcom_push(
                key='workflow_metrics',
                value=result.get('workflowMetrics', {})
            )

            logger.info(f"Custom workflow completed: {result['finalOutputPath']}")

            return result

        except requests.exceptions.Timeout:
            raise AirflowException(f"Custom workflow timed out after {self.timeout} seconds")
        except requests.exceptions.RequestException as e:
            logger.error(f"Custom workflow failed: {e}")
            raise AirflowException(f"Failed to execute custom workflow: {e}")


class IntermediateResultsOperator(BaseOperator):
    """
    Operator for managing intermediate processing results.

    Lists, retrieves, and manages intermediate files created during granular processing.
    """

    template_fields: Sequence[str] = ('session_id',)

    @apply_defaults
    def __init__(
        self,
        session_id: str,
        operation: str = 'list',  # 'list', 'cleanup', 'move'
        keep_final_result: bool = True,
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.session_id = session_id
        self.operation = operation
        self.keep_final_result = keep_final_result

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def execute(self, context: Dict) -> Any:
        """Execute intermediate results operation."""
        if self.operation == 'list':
            return self._list_intermediate_results(context)
        elif self.operation == 'cleanup':
            return self._cleanup_intermediate_results(context)
        else:
            raise AirflowException(f"Unsupported operation: {self.operation}")

    def _list_intermediate_results(self, context: Dict) -> List[Dict[str, Any]]:
        """List intermediate results for the session."""
        url = f"{self.base_url}/api/v1/processing/intermediate/{self.session_id}/results"

        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()

            intermediate_files = response.json()
            logger.info(f"Found {len(intermediate_files)} intermediate files for session {self.session_id}")

            # Store in XCom
            context['ti'].xcom_push(
                key='intermediate_files',
                value=intermediate_files
            )

            return intermediate_files

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to list intermediate results: {e}")
            raise AirflowException(f"Failed to list intermediate results: {e}")

    def _cleanup_intermediate_results(self, context: Dict) -> Dict[str, Any]:
        """Cleanup intermediate results for the session."""
        # This would call the IntermediateStorageService.cleanupSessionFiles method
        # For now, we'll simulate the cleanup operation

        logger.info(f"Cleaning up intermediate files for session {self.session_id}")
        logger.info(f"Keep final result: {self.keep_final_result}")

        # In a real implementation, this would call the cleanup endpoint
        cleanup_result = {
            'session_id': self.session_id,
            'files_cleaned': 0,  # Would be actual count
            'final_result_preserved': self.keep_final_result,
            'cleanup_completed': True
        }

        context['ti'].xcom_push(
            key='cleanup_result',
            value=cleanup_result
        )

        return cleanup_result


# =====================================================
# New Workflow-Aware Operators
# =====================================================

class ActiveWorkflowOperator(BaseOperator):
    """
    Operator that automatically selects and uses the active workflow version
    for a specific processing step.

    This operator demonstrates the full workflow versioning capabilities by
    automatically discovering and using active workflow versions.
    """

    template_fields: Sequence[str] = (
        'image_path', 'session_id', 'workflow_type', 'processing_type'
    )

    @apply_defaults
    def __init__(
        self,
        image_path: str,
        session_id: str,
        workflow_type: str,  # e.g., 'cosmic-ray-removal', 'bias-subtraction'
        processing_type: str = 'production',
        fallback_algorithm: str = 'default',
        timeout: int = 300,
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.image_path = image_path
        self.session_id = session_id
        self.workflow_type = workflow_type
        self.processing_type = processing_type
        self.fallback_algorithm = fallback_algorithm
        self.timeout = timeout

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def execute(self, context: Dict) -> Dict[str, Any]:
        """Execute processing using the active workflow version."""
        logger.info(f"Starting active workflow processing: {self.workflow_type} for {self.image_path}")

        # Get active workflow for this type
        active_workflow = self._get_active_workflow()
        if not active_workflow:
            raise AirflowException(f"No active workflow found for {self.workflow_type} in {self.processing_type} mode")

        # Build processing request
        payload = {
            'imagePath': self.image_path,
            'sessionId': self.session_id,
            'workflowName': active_workflow['workflowName'],
            'workflowVersion': active_workflow['workflowVersion'],
            'processingType': self.processing_type,
            'useActiveWorkflow': True
        }

        # Add algorithm configuration from active workflow
        if 'algorithmConfiguration' in active_workflow:
            algorithm_config = active_workflow['algorithmConfiguration']
            payload['algorithm'] = algorithm_config.get('algorithm', self.fallback_algorithm)
            payload['parameters'] = algorithm_config.get('parameters', {})

        # Store workflow selection info in XCom
        context['ti'].xcom_push(
            key='selected_workflow',
            value={
                'workflowName': active_workflow['workflowName'],
                'workflowVersion': active_workflow['workflowVersion'],
                'deterministicProcessing': True,
                'activatedBy': active_workflow.get('activatedBy'),
                'selectionTime': context['ts']
            }
        )

        # Execute processing
        endpoint = self._get_processing_endpoint()
        result = self._make_request(endpoint, payload)

        logger.info(f"Active workflow processing completed: {result.get('outputPath')}")

        return {
            'outputPath': result.get('outputPath'),
            'processingId': result.get('processingId'),
            'workflowUsed': active_workflow,
            'processingMetrics': result.get('processingMetrics', {})
        }

    def _get_active_workflow(self) -> Optional[Dict[str, Any]]:
        """Get the active workflow for the specified type."""
        try:
            url = f"{self.base_url}/api/v1/workflows/active"
            params = {'processingType': self.processing_type}

            response = requests.get(url, params=params, timeout=30)
            response.raise_for_status()

            active_workflows = response.json()

            # Find matching workflow
            for workflow in active_workflows:
                if workflow.get('workflowName') == self.workflow_type:
                    logger.info(f"Selected active workflow: {workflow.get('workflowName')} "
                              f"version {workflow.get('workflowVersion')} "
                              f"(deterministic - always 100%)")
                    return workflow

            logger.warning(f"No active workflow found for {self.workflow_type}")
            return None

        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to get active workflows: {e}")
            return None

    def _get_processing_endpoint(self) -> str:
        """Map workflow type to processing endpoint."""
        endpoint_mapping = {
            'bias-subtraction': 'bias-subtract',
            'dark-subtraction': 'dark-subtract',
            'flat-field-correction': 'flat-correct',
            'cosmic-ray-removal': 'cosmic-ray-remove'
        }
        return endpoint_mapping.get(self.workflow_type, 'process')

    def _make_request(self, endpoint: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """Make HTTP request to processing endpoint."""
        url = f"{self.base_url}/api/v1/processing/steps/{endpoint}"

        try:
            response = requests.post(
                url,
                json=payload,
                headers={'Content-Type': 'application/json'},
                timeout=self.timeout
            )
            response.raise_for_status()
            return response.json()

        except requests.exceptions.RequestException as e:
            logger.error(f"Processing request failed: {e}")
            raise AirflowException(f"Failed to execute {endpoint}: {e}")


class WorkflowComparisonOperator(BaseOperator):
    """
    Operator for comparing performance between different workflow versions.

    Useful for performance evaluation of workflow versions through deterministic testing.
    """

    template_fields: Sequence[str] = (
        'image_path', 'session_id', 'workflow_name', 'baseline_version', 'comparison_version'
    )

    @apply_defaults
    def __init__(
        self,
        image_path: str,
        session_id: str,
        workflow_name: str,
        baseline_version: str,
        comparison_version: str,
        processing_type: str = 'experimental',
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.image_path = image_path
        self.session_id = session_id
        self.workflow_name = workflow_name
        self.baseline_version = baseline_version
        self.comparison_version = comparison_version
        self.processing_type = processing_type

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def execute(self, context: Dict) -> Dict[str, Any]:
        """Execute workflow comparison."""
        logger.info(f"Starting workflow comparison: {self.baseline_version} vs {self.comparison_version}")

        # Get comparison results from API
        url = f"{self.base_url}/api/v1/workflows/{self.workflow_name}/compare"
        params = {
            'baselineVersion': self.baseline_version,
            'comparisonVersion': self.comparison_version,
            'processingType': self.processing_type
        }

        try:
            response = requests.get(url, params=params, timeout=60)
            response.raise_for_status()

            comparison_result = response.json()

            # Store detailed comparison in XCom
            context['ti'].xcom_push(
                key='workflow_comparison',
                value=comparison_result
            )

            # Log key findings
            recommendation = comparison_result.get('recommendation', 'No recommendation available')
            logger.info(f"Workflow comparison completed. Recommendation: {recommendation}")

            return comparison_result

        except requests.exceptions.RequestException as e:
            logger.error(f"Workflow comparison failed: {e}")
            raise AirflowException(f"Failed to compare workflows: {e}")


class WorkflowPromotionOperator(BaseOperator):
    """
    Operator for promoting experimental workflows to production.

    Handles the complete promotion process including validation and activation.
    """

    template_fields: Sequence[str] = (
        'experiment_name', 'new_production_version', 'promoted_by'
    )

    @apply_defaults
    def __init__(
        self,
        experiment_name: str,
        new_production_version: str,
        promoted_by: str,
        promotion_reason: str,
        performance_metrics: Optional[Dict[str, Any]] = None,
        set_as_default: bool = True,
        **kwargs
    ) -> None:
        super().__init__(**kwargs)
        self.experiment_name = experiment_name
        self.new_production_version = new_production_version
        self.promoted_by = promoted_by
        self.promotion_reason = promotion_reason
        self.performance_metrics = performance_metrics or {}
        self.set_as_default = set_as_default

        # Get base URL from Airflow Variables
        self.base_url = Variable.get(
            "image_processor_base_url",
            "http://image-processor-service:8080"
        )

    def execute(self, context: Dict) -> Dict[str, Any]:
        """Execute workflow promotion."""
        logger.info(f"Promoting experimental workflow {self.experiment_name} to production {self.new_production_version}")

        url = f"{self.base_url}/api/v1/workflows/experimental/{self.experiment_name}/promote"

        payload = {
            'newProductionVersion': self.new_production_version,
            'activatedBy': self.promoted_by,
            'reason': self.promotion_reason,
            'performanceMetrics': self.performance_metrics,
            'setAsDefault': self.set_as_default
        }

        try:
            response = requests.post(
                url,
                json=payload,
                headers={'Content-Type': 'application/json'},
                timeout=60
            )
            response.raise_for_status()

            promotion_result = response.json()

            # Store promotion details in XCom
            context['ti'].xcom_push(
                key='workflow_promotion',
                value={
                    'experimentName': self.experiment_name,
                    'newProductionVersion': self.new_production_version,
                    'promotedBy': self.promoted_by,
                    'promotionTime': context['ts'],
                    'result': promotion_result
                }
            )

            logger.info(f"Workflow promotion completed successfully: {promotion_result.get('workflowVersion')}")

            return promotion_result

        except requests.exceptions.RequestException as e:
            logger.error(f"Workflow promotion failed: {e}")
            raise AirflowException(f"Failed to promote workflow: {e}")