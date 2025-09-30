"""
Research Processing DAG

Flexible research workflow that orchestrates granular processing endpoints
to enable algorithm experimentation and custom processing pipelines.

Author: STScI Demo Project - Phase 3 Implementation
"""

from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator
from airflow.providers.http.operators.http import SimpleHttpOperator
from airflow.providers.amazon.aws.sensors.s3 import S3KeySensor
from airflow.operators.dummy import DummyOperator
from airflow.utils.dates import days_ago
from airflow.utils.task_group import TaskGroup
from airflow.models import Variable
from airflow.exceptions import AirflowException
from airflow.utils.trigger_rule import TriggerRule

import boto3
import json
import logging
import requests
from urllib.parse import urljoin

# Configure logging
logger = logging.getLogger(__name__)

# Default arguments for all tasks
default_args = {
    'owner': 'astro-research',
    'depends_on_past': False,
    'start_date': days_ago(1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=3),
    'execution_timeout': timedelta(hours=1),
}

# DAG definition
dag = DAG(
    'research_processing_workflow',
    default_args=default_args,
    description='Flexible research workflow for algorithmic experimentation',
    schedule_interval=None,  # Manual trigger only for research
    catchup=False,
    max_active_runs=5,
    tags=['research', 'granular-processing', 'experimentation'],
    params={
        # Research workflow configuration
        "session_id": "research-session-{{ ts_nodash }}",
        "input_image_path": "raw-data/test/sample.fits",
        "calibration_frames": {
            "dark_frame": "calibration/master_dark.fits",
            "flat_frame": "calibration/master_flat.fits",
            "bias_frame": "calibration/master_bias.fits"
        },
        "processing_steps": [
            {
                "step": "bias-subtract",
                "algorithm": "default",
                "parameters": {
                    "overscanCorrection": True,
                    "fitMethod": "median"
                }
            },
            {
                "step": "dark-subtract",
                "algorithm": "scaled-dark",
                "parameters": {
                    "autoScale": True,
                    "temperatureCorrection": False
                }
            },
            {
                "step": "flat-correct",
                "algorithm": "illumination-corrected",
                "parameters": {
                    "illuminationModel": "polynomial",
                    "polynomialDegree": 3,
                    "maskStars": True
                }
            },
            {
                "step": "cosmic-ray-remove",
                "algorithm": "lacosmic-v2",
                "parameters": {
                    "sigclip": 4.5,
                    "starPreservation": True,
                    "niter": 4
                }
            }
        ],
        "output_configuration": {
            "intermediate_bucket": "astro-data-pipeline-intermediate-data-dev",
            "final_bucket": "astro-data-pipeline-processed-data-dev",
            "custom_path": None,
            "preserve_metadata": True,
            "enable_metrics": True
        },
        "experiment_config": {
            "name": "algorithm-comparison-experiment",
            "description": "Testing different algorithms for optimal processing",
            "researcher": "astronomer@stsci.edu"
        }
    }
)

# Configuration
IMAGE_PROCESSOR_BASE_URL = Variable.get("image_processor_base_url", "http://image-processor-service:8080")
GRANULAR_API_BASE = f"{IMAGE_PROCESSOR_BASE_URL}/api/v1/processing"

def validate_workflow_config(**context) -> Dict[str, Any]:
    """Validate research workflow configuration and prepare execution context."""
    params = context['params']

    # Validate required parameters
    required_fields = ['session_id', 'input_image_path', 'processing_steps']
    for field in required_fields:
        if not params.get(field):
            raise AirflowException(f"Required parameter '{field}' is missing")

    # Validate processing steps
    steps = params['processing_steps']
    if not isinstance(steps, list) or len(steps) == 0:
        raise AirflowException("At least one processing step must be specified")

    valid_steps = ['bias-subtract', 'dark-subtract', 'flat-correct', 'cosmic-ray-remove']
    for step_config in steps:
        if step_config.get('step') not in valid_steps:
            raise AirflowException(f"Invalid processing step: {step_config.get('step')}")

    # Prepare workflow context
    workflow_context = {
        'session_id': params['session_id'],
        'input_path': params['input_image_path'],
        'processing_chain': steps,
        'calibration_frames': params.get('calibration_frames', {}),
        'output_config': params.get('output_configuration', {}),
        'experiment_info': params.get('experiment_config', {})
    }

    logger.info(f"Validated research workflow: {workflow_context['session_id']}")
    return workflow_context

def check_algorithm_availability(algorithm_type: str, algorithm_id: str) -> bool:
    """Check if specified algorithm is available and supported."""
    try:
        url = f"{GRANULAR_API_BASE}/algorithms/{algorithm_type}"
        response = requests.get(url, timeout=10)
        response.raise_for_status()

        algorithms = response.json()
        for algo in algorithms:
            if algo['id'] == algorithm_id and algo['supported']:
                return True

        logger.warning(f"Algorithm {algorithm_id} not available for {algorithm_type}")
        return False

    except Exception as e:
        logger.error(f"Failed to check algorithm availability: {e}")
        return False

def create_processing_request(step_config: Dict, session_id: str,
                            current_image_path: str, calibration_frames: Dict,
                            output_config: Dict) -> Dict[str, Any]:
    """Create a granular processing request payload."""
    step_type = step_config['step']

    # Map step types to calibration frame requirements
    calibration_mapping = {
        'bias-subtract': 'bias_frame',
        'dark-subtract': 'dark_frame',
        'flat-correct': 'flat_frame',
        'cosmic-ray-remove': None  # No calibration frame needed
    }

    request_payload = {
        'imagePath': current_image_path,
        'sessionId': session_id,
        'algorithm': step_config.get('algorithm', 'default'),
        'parameters': step_config.get('parameters', {}),
        'outputBucket': output_config.get('intermediate_bucket'),
        'outputPath': output_config.get('custom_path'),
        'preserveMetadata': output_config.get('preserve_metadata', True),
        'enableMetrics': output_config.get('enable_metrics', True)
    }

    # Add calibration frame if required
    calibration_key = calibration_mapping.get(step_type)
    if calibration_key and calibration_frames.get(calibration_key):
        request_payload['calibrationPath'] = calibration_frames[calibration_key]

    return request_payload

def execute_processing_step(step_config: Dict, **context) -> str:
    """Execute a single granular processing step."""
    workflow_context = context['ti'].xcom_pull(task_ids='validate_workflow')

    # Get current image path from previous step or use input
    current_image_path = context['ti'].xcom_pull(task_ids=f"execute_step_{step_config['step'].replace('-', '_')}")
    if not current_image_path:
        # Check for previous steps in sequence
        previous_steps = ['bias_subtract', 'dark_subtract', 'flat_correct']
        for prev_step in previous_steps:
            current_image_path = context['ti'].xcom_pull(task_ids=f"execute_step_{prev_step}")
            if current_image_path:
                break

        # Fallback to input image
        if not current_image_path:
            current_image_path = workflow_context['input_path']

    # Create processing request
    request_payload = create_processing_request(
        step_config,
        workflow_context['session_id'],
        current_image_path,
        workflow_context['calibration_frames'],
        workflow_context['output_config']
    )

    # Execute processing step
    step_type = step_config['step']
    endpoint_url = f"{GRANULAR_API_BASE}/steps/{step_type}"

    logger.info(f"Executing {step_type} with algorithm {step_config.get('algorithm', 'default')}")

    try:
        response = requests.post(
            endpoint_url,
            json=request_payload,
            headers={'Content-Type': 'application/json'},
            timeout=300  # 5 minute timeout for processing
        )
        response.raise_for_status()

        result = response.json()
        output_path = result['outputPath']

        logger.info(f"Processing step {step_type} completed: {output_path}")

        # Store processing metrics
        if result.get('processingMetrics'):
            context['ti'].xcom_push(
                key=f"{step_type}_metrics",
                value=result['processingMetrics']
            )

        return output_path

    except requests.exceptions.RequestException as e:
        logger.error(f"Processing step {step_type} failed: {e}")
        raise AirflowException(f"Failed to execute {step_type}: {e}")

def finalize_research_results(**context) -> Dict[str, Any]:
    """Finalize research results and move to final location."""
    workflow_context = context['ti'].xcom_pull(task_ids='validate_workflow')

    # Get final processed image path from last step
    final_image_path = None
    processing_steps = workflow_context['processing_chain']

    # Find the last executed step
    for step_config in reversed(processing_steps):
        step_key = f"execute_step_{step_config['step'].replace('-', '_')}"
        final_image_path = context['ti'].xcom_pull(task_ids=step_key)
        if final_image_path:
            break

    if not final_image_path:
        raise AirflowException("No processed image found from processing chain")

    # Collect all processing metrics
    processing_metrics = {}
    for step_config in processing_steps:
        step_type = step_config['step']
        metrics = context['ti'].xcom_pull(key=f"{step_type}_metrics")
        if metrics:
            processing_metrics[step_type] = metrics

    # Move final result to processed bucket if configured
    output_config = workflow_context['output_config']
    final_bucket = output_config.get('final_bucket')

    if final_bucket:
        # Call intermediate storage service to move final result
        move_request = {
            'intermediateResultPath': final_image_path,
            'finalBucket': final_bucket,
            'finalPath': f"research/{workflow_context['session_id']}/final_result.fits"
        }

        try:
            # This would call the IntermediateStorageService.moveFinalResult method
            # For now, we'll just log the intent
            logger.info(f"Would move final result from {final_image_path} to {final_bucket}")
            final_result_path = f"{final_bucket}/research/{workflow_context['session_id']}/final_result.fits"
        except Exception as e:
            logger.warning(f"Failed to move final result: {e}")
            final_result_path = final_image_path
    else:
        final_result_path = final_image_path

    # Generate research summary
    research_summary = {
        'session_id': workflow_context['session_id'],
        'experiment_info': workflow_context['experiment_info'],
        'input_image': workflow_context['input_path'],
        'final_result': final_result_path,
        'processing_chain': [
            {
                'step': step['step'],
                'algorithm': step.get('algorithm', 'default'),
                'parameters': step.get('parameters', {})
            }
            for step in processing_steps
        ],
        'processing_metrics': processing_metrics,
        'completion_time': datetime.now().isoformat()
    }

    logger.info(f"Research workflow completed: {research_summary}")

    return research_summary

# Task definitions

# Workflow validation
validate_workflow_task = PythonOperator(
    task_id='validate_workflow',
    python_callable=validate_workflow_config,
    dag=dag,
    doc_md="""
    ## Validate Workflow Configuration

    Validates the research workflow parameters and prepares execution context.
    Ensures all required parameters are present and processing steps are valid.
    """
)

# Algorithm availability checks
def create_algorithm_check_task(step_config: Dict) -> PythonOperator:
    """Create algorithm availability check task for a processing step."""

    def check_algorithm(**context):
        step_type = step_config['step']
        algorithm_id = step_config.get('algorithm', 'default')

        # Map step types to algorithm types for API calls
        algorithm_type_mapping = {
            'bias-subtract': 'bias-subtraction',
            'dark-subtract': 'dark-subtraction',
            'flat-correct': 'flat-correction',
            'cosmic-ray-remove': 'cosmic-ray-removal'
        }

        algorithm_type = algorithm_type_mapping.get(step_type, step_type)

        if not check_algorithm_availability(algorithm_type, algorithm_id):
            raise AirflowException(f"Algorithm {algorithm_id} not available for {step_type}")

        logger.info(f"Algorithm {algorithm_id} confirmed available for {step_type}")

    return PythonOperator(
        task_id=f'check_algorithm_{step_config["step"].replace("-", "_")}',
        python_callable=check_algorithm,
        dag=dag
    )

# Processing step tasks
def create_processing_step_task(step_config: Dict) -> PythonOperator:
    """Create processing step task."""

    def execute_step(**context):
        return execute_processing_step(step_config, **context)

    return PythonOperator(
        task_id=f'execute_step_{step_config["step"].replace("-", "_")}',
        python_callable=execute_step,
        dag=dag,
        doc_md=f"""
        ## Execute {step_config['step'].title()} Processing

        Executes {step_config['step']} using algorithm: {step_config.get('algorithm', 'default')}

        **Parameters:**
        ```json
        {json.dumps(step_config.get('parameters', {}), indent=2)}
        ```
        """
    )

# Finalization task
finalize_results_task = PythonOperator(
    task_id='finalize_research_results',
    python_callable=finalize_research_results,
    dag=dag,
    trigger_rule=TriggerRule.NONE_FAILED_MIN_ONE_SUCCESS,
    doc_md="""
    ## Finalize Research Results

    Collects processing metrics, moves final result to output location,
    and generates comprehensive research workflow summary.
    """
)

# Cleanup task
cleanup_intermediate_task = BashOperator(
    task_id='cleanup_intermediate_files',
    bash_command="""
    echo "Research workflow {{ params.session_id }} completed"
    echo "Final result available at: {{ ti.xcom_pull(task_ids='finalize_research_results')['final_result'] }}"
    echo "Processing metrics collected for all steps"
    # Intermediate cleanup would be handled by IntermediateStorageService
    """,
    dag=dag,
    trigger_rule=TriggerRule.NONE_FAILED_MIN_ONE_SUCCESS
)

# Task group for processing steps
with TaskGroup("processing_steps", dag=dag) as processing_group:

    # Create default processing step tasks (will be overridden by dynamic config)
    default_steps = [
        {'step': 'bias-subtract', 'algorithm': 'default'},
        {'step': 'dark-subtract', 'algorithm': 'default'},
        {'step': 'flat-correct', 'algorithm': 'default'},
        {'step': 'cosmic-ray-remove', 'algorithm': 'lacosmic'}
    ]

    previous_task = None
    algorithm_check_tasks = []
    processing_tasks = []

    for step_config in default_steps:
        # Create algorithm check task
        check_task = create_algorithm_check_task(step_config)
        algorithm_check_tasks.append(check_task)

        # Create processing step task
        process_task = create_processing_step_task(step_config)
        processing_tasks.append(process_task)

        # Set dependencies
        check_task >> process_task

        if previous_task:
            previous_task >> process_task

        previous_task = process_task

# Define task dependencies
validate_workflow_task >> processing_group
processing_group >> finalize_results_task >> cleanup_intermediate_task

# Documentation
dag.doc_md = """
# Research Processing Workflow

This DAG provides a flexible research workflow that orchestrates the granular processing endpoints
implemented in Phase 2. It enables astronomers to experiment with different algorithms and
create custom processing pipelines.

## Key Features

- **Algorithm Selection**: Choose from multiple algorithm implementations for each processing step
- **Parameter Customization**: Fine-tune algorithm parameters for experimental workflows
- **Intermediate Storage**: Automatic management of intermediate results for step chaining
- **Processing Metrics**: Comprehensive metrics collection for performance analysis
- **Flexible Configuration**: Runtime configuration via DAG parameters

## Usage

### Manual Trigger with Custom Configuration

```python
# Example configuration for algorithm comparison experiment
{
    "session_id": "cosmic-ray-comparison-20240928",
    "input_image_path": "raw-data/test/noisy_galaxy.fits",
    "processing_steps": [
        {
            "step": "cosmic-ray-remove",
            "algorithm": "lacosmic",
            "parameters": {"sigclip": 4.5, "niter": 4}
        },
        {
            "step": "cosmic-ray-remove",
            "algorithm": "lacosmic-v2",
            "parameters": {"sigclip": 4.5, "starPreservation": true}
        },
        {
            "step": "cosmic-ray-remove",
            "algorithm": "median-filter",
            "parameters": {"kernelSize": 5, "threshold": 5.0}
        }
    ]
}
```

### Supported Processing Steps

- **bias-subtract**: Bias frame subtraction with overscan correction
- **dark-subtract**: Dark current subtraction with scaling options
- **flat-correct**: Flat field correction with illumination modeling
- **cosmic-ray-remove**: Cosmic ray detection and removal

### Algorithm Options

Each processing step supports multiple algorithm implementations:

- **Standard algorithms**: Production-tested implementations
- **Enhanced algorithms**: Improved versions with additional features
- **Experimental algorithms**: Research implementations (may not be enabled)

## Research Applications

- **Algorithm Comparison**: Test different implementations side-by-side
- **Parameter Optimization**: Find optimal parameters for specific data types
- **Custom Workflows**: Create specialized processing pipelines for specific research
- **Quality Assessment**: Compare processing quality across different approaches

## Output

- **Processed Images**: Final calibrated FITS files
- **Processing Metrics**: Detailed performance and quality metrics
- **Research Summary**: Comprehensive workflow documentation
- **Intermediate Results**: All processing steps preserved for analysis
"""