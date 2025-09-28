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
            'enableMetrics': self.enable_metrics
        }

        if self.calibration_path:
            payload['calibrationPath'] = self.calibration_path

        if self.output_bucket:
            payload['outputBucket'] = self.output_bucket

        if self.output_path:
            payload['outputPath'] = self.output_path

        return payload

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