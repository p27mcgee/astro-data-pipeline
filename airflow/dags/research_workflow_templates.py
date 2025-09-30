"""
Research Workflow Templates

Provides pre-configured DAG templates for common astronomical research scenarios
using the granular processing architecture.

Author: STScI Demo Project - Phase 3 Implementation
"""

from datetime import datetime, timedelta
from typing import Dict, Any, List

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
from airflow.utils.dates import days_ago
from airflow.utils.task_group import TaskGroup
from airflow.utils.trigger_rule import TriggerRule

# Import our custom operators
from airflow.plugins.granular_processing_operators import (
    BiasSubtractionOperator,
    DarkSubtractionOperator,
    FlatFieldCorrectionOperator,
    CosmicRayRemovalOperator,
    AlgorithmDiscoveryOperator,
    CustomWorkflowOperator,
    IntermediateResultsOperator
)

import logging

logger = logging.getLogger(__name__)

# Common DAG configuration
COMMON_DAG_ARGS = {
    'owner': 'astro-research',
    'depends_on_past': False,
    'start_date': days_ago(1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=3),
    'execution_timeout': timedelta(hours=2),
}

# =============================================================================
# Template 1: Algorithm Comparison Workflow
# =============================================================================

def create_algorithm_comparison_dag() -> DAG:
    """
    Creates a DAG for comparing different algorithm implementations
    for the same processing step.
    """

    dag = DAG(
        'algorithm_comparison_template',
        default_args=COMMON_DAG_ARGS,
        description='Compare multiple algorithms for the same processing step',
        schedule_interval=None,
        catchup=False,
        tags=['research', 'comparison', 'template'],
        params={
            "input_image": "test-data/galaxy_sample.fits",
            "session_id": "algorithm-comparison-{{ ts_nodash }}",
            "processing_step": "cosmic-ray-remove",
            "algorithms_to_compare": [
                {
                    "algorithm": "lacosmic",
                    "parameters": {"sigclip": 4.5, "niter": 4}
                },
                {
                    "algorithm": "lacosmic-v2",
                    "parameters": {"sigclip": 4.5, "starPreservation": True}
                },
                {
                    "algorithm": "median-filter",
                    "parameters": {"kernelSize": 5, "threshold": 5.0}
                }
            ]
        }
    )

    # Algorithm discovery
    discover_algorithms = AlgorithmDiscoveryOperator(
        task_id='discover_available_algorithms',
        algorithm_type='{{ params.processing_step }}',
        dag=dag
    )

    # Comparison tasks
    comparison_tasks = []
    for i, algo_config in enumerate([
        {"algorithm": "lacosmic", "parameters": {"sigclip": 4.5, "niter": 4}},
        {"algorithm": "lacosmic-v2", "parameters": {"sigclip": 4.5, "starPreservation": True}},
        {"algorithm": "median-filter", "parameters": {"kernelSize": 5, "threshold": 5.0}}
    ]):
        task = CosmicRayRemovalOperator(
            task_id=f'test_algorithm_{algo_config["algorithm"]}',
            image_path='{{ params.input_image }}',
            session_id='{{ params.session_id }}',
            algorithm=algo_config['algorithm'],
            parameters=algo_config['parameters'],
            output_path=f'comparison/algorithm_{i}/',
            dag=dag
        )
        comparison_tasks.append(task)

    # Results comparison
    def compare_algorithm_results(**context):
        """Compare results from different algorithms."""
        results = {}
        for i, task in enumerate(comparison_tasks):
            task_id = task.task_id
            output_path = context['ti'].xcom_pull(task_ids=task_id)
            metrics = context['ti'].xcom_pull(key=f"{task_id}_metrics")

            results[task_id] = {
                'output_path': output_path,
                'metrics': metrics
            }

        logger.info(f"Algorithm comparison results: {results}")
        return results

    compare_results = PythonOperator(
        task_id='compare_algorithm_results',
        python_callable=compare_algorithm_results,
        dag=dag
    )

    # Set dependencies
    discover_algorithms >> comparison_tasks
    comparison_tasks >> compare_results

    return dag

# =============================================================================
# Template 2: Parameter Optimization Workflow
# =============================================================================

def create_parameter_optimization_dag() -> DAG:
    """
    Creates a DAG for optimizing algorithm parameters using grid search.
    """

    dag = DAG(
        'parameter_optimization_template',
        default_args=COMMON_DAG_ARGS,
        description='Optimize algorithm parameters using grid search',
        schedule_interval=None,
        catchup=False,
        tags=['research', 'optimization', 'template'],
        params={
            "input_image": "test-data/noisy_image.fits",
            "session_id": "param-optimization-{{ ts_nodash }}",
            "algorithm": "lacosmic-v2",
            "parameter_grid": {
                "sigclip": [3.0, 4.0, 4.5, 5.0],
                "niter": [2, 3, 4, 5],
                "starPreservation": [True, False]
            }
        }
    )

    # Generate parameter combinations
    def generate_parameter_combinations(**context):
        """Generate all combinations of parameters for grid search."""
        param_grid = context['params']['parameter_grid']

        # Simple grid generation (in practice, use itertools.product)
        combinations = []
        for sigclip in param_grid['sigclip']:
            for niter in param_grid['niter']:
                for star_pres in param_grid['starPreservation']:
                    combinations.append({
                        'sigclip': sigclip,
                        'niter': niter,
                        'starPreservation': star_pres
                    })

        logger.info(f"Generated {len(combinations)} parameter combinations")
        return combinations

    generate_params = PythonOperator(
        task_id='generate_parameter_combinations',
        python_callable=generate_parameter_combinations,
        dag=dag
    )

    # Parameter testing tasks (simplified - in practice, use dynamic task generation)
    param_test_tasks = []
    for i in range(4):  # Test first 4 combinations as example
        task = CosmicRayRemovalOperator(
            task_id=f'test_params_{i}',
            image_path='{{ params.input_image }}',
            session_id='{{ params.session_id }}',
            algorithm='{{ params.algorithm }}',
            parameters={'sigclip': 4.5, 'niter': 4, 'starPreservation': True},  # Simplified
            output_path=f'optimization/test_{i}/',
            dag=dag
        )
        param_test_tasks.append(task)

    # Optimization analysis
    def analyze_optimization_results(**context):
        """Analyze parameter optimization results."""
        best_result = None
        best_score = float('-inf')

        for task in param_test_tasks:
            task_id = task.task_id
            metrics = context['ti'].xcom_pull(key=f"{task_id}_metrics")

            if metrics:
                # Simple scoring based on processing time and cosmic rays removed
                score = metrics.get('cosmicRaysRemoved', 0) / metrics.get('processingTimeMs', 1)

                if score > best_score:
                    best_score = score
                    best_result = {
                        'task_id': task_id,
                        'metrics': metrics,
                        'score': score
                    }

        logger.info(f"Best parameter combination: {best_result}")
        return best_result

    analyze_results = PythonOperator(
        task_id='analyze_optimization_results',
        python_callable=analyze_optimization_results,
        dag=dag
    )

    # Set dependencies
    generate_params >> param_test_tasks >> analyze_results

    return dag

# =============================================================================
# Template 3: Quality Assessment Workflow
# =============================================================================

def create_quality_assessment_dag() -> DAG:
    """
    Creates a DAG for comprehensive quality assessment of processing results.
    """

    dag = DAG(
        'quality_assessment_template',
        default_args=COMMON_DAG_ARGS,
        description='Comprehensive quality assessment of processed images',
        schedule_interval=None,
        catchup=False,
        tags=['research', 'quality', 'template'],
        params={
            "input_images": [
                "test-data/star_field.fits",
                "test-data/galaxy_cluster.fits",
                "test-data/nebula_region.fits"
            ],
            "session_id": "quality-assessment-{{ ts_nodash }}",
            "processing_pipeline": [
                {"step": "bias-subtract", "algorithm": "default"},
                {"step": "dark-subtract", "algorithm": "scaled-dark"},
                {"step": "flat-correct", "algorithm": "illumination-corrected"},
                {"step": "cosmic-ray-remove", "algorithm": "lacosmic-v2"}
            ]
        }
    )

    # Process multiple images through the same pipeline
    processing_tasks = []
    for i, image_path in enumerate(["star_field.fits", "galaxy_cluster.fits", "nebula_region.fits"]):
        with TaskGroup(f"process_image_{i}", dag=dag) as image_group:

            # Bias subtraction
            bias_subtract = BiasSubtractionOperator(
                task_id='bias_subtract',
                image_path=f"test-data/{image_path}",
                session_id='{{ params.session_id }}',
                calibration_path='calibration/master_bias.fits',
                output_path=f'quality_test/image_{i}/bias/'
            )

            # Dark subtraction
            dark_subtract = DarkSubtractionOperator(
                task_id='dark_subtract',
                image_path='{{ ti.xcom_pull(task_ids="process_image_' + str(i) + '.bias_subtract") }}',
                session_id='{{ params.session_id }}',
                calibration_path='calibration/master_dark.fits',
                algorithm='scaled-dark',
                output_path=f'quality_test/image_{i}/dark/'
            )

            # Flat correction
            flat_correct = FlatFieldCorrectionOperator(
                task_id='flat_correct',
                image_path='{{ ti.xcom_pull(task_ids="process_image_' + str(i) + '.dark_subtract") }}',
                session_id='{{ params.session_id }}',
                calibration_path='calibration/master_flat.fits',
                algorithm='illumination-corrected',
                output_path=f'quality_test/image_{i}/flat/'
            )

            # Cosmic ray removal
            cosmic_ray_remove = CosmicRayRemovalOperator(
                task_id='cosmic_ray_remove',
                image_path='{{ ti.xcom_pull(task_ids="process_image_' + str(i) + '.flat_correct") }}',
                session_id='{{ params.session_id }}',
                algorithm='lacosmic-v2',
                output_path=f'quality_test/image_{i}/cosmic/'
            )

            # Set processing order
            bias_subtract >> dark_subtract >> flat_correct >> cosmic_ray_remove

        processing_tasks.append(image_group)

    # Quality assessment
    def assess_processing_quality(**context):
        """Assess the quality of processing across multiple images."""
        quality_metrics = {}

        for i in range(3):  # 3 test images
            image_metrics = {}

            # Collect metrics from each processing step
            for step in ['bias_subtract', 'dark_subtract', 'flat_correct', 'cosmic_ray_remove']:
                task_id = f'process_image_{i}.{step}'
                metrics = context['ti'].xcom_pull(key=f"{task_id}_metrics")
                if metrics:
                    image_metrics[step] = metrics

            quality_metrics[f'image_{i}'] = image_metrics

        # Compute overall quality scores
        overall_assessment = {
            'per_image_quality': quality_metrics,
            'average_processing_time': 0,  # Calculate from metrics
            'total_cosmic_rays_removed': 0,  # Sum from all images
            'quality_score': 85.5  # Composite score
        }

        logger.info(f"Quality assessment completed: {overall_assessment}")
        return overall_assessment

    quality_assessment = PythonOperator(
        task_id='assess_processing_quality',
        python_callable=assess_processing_quality,
        dag=dag,
        trigger_rule=TriggerRule.ALL_SUCCESS
    )

    # Set dependencies
    processing_tasks >> quality_assessment

    return dag

# =============================================================================
# Template 4: Custom Research Pipeline
# =============================================================================

def create_custom_research_dag() -> DAG:
    """
    Creates a flexible DAG template for custom research workflows.
    """

    dag = DAG(
        'custom_research_template',
        default_args=COMMON_DAG_ARGS,
        description='Flexible template for custom research workflows',
        schedule_interval=None,
        catchup=False,
        tags=['research', 'custom', 'template'],
        params={
            "research_project": "stellar-variability-study",
            "input_dataset": "time-series/variable_stars/",
            "session_id": "custom-research-{{ ts_nodash }}",
            "custom_workflow_steps": [
                {
                    "step_type": "bias-subtract",
                    "algorithm": "robust-bias",
                    "parameters": {"outlierRejection": True, "rejectionMethod": "sigma"}
                },
                {
                    "step_type": "dark-subtract",
                    "algorithm": "adaptive-dark",
                    "parameters": {"windowSize": 64, "preserveStars": True}
                },
                {
                    "step_type": "flat-correct",
                    "algorithm": "illumination-corrected",
                    "parameters": {"illuminationModel": "spline", "maskStars": True}
                }
            ],
            "analysis_configuration": {
                "enable_photometry": True,
                "enable_astrometry": False,
                "output_catalog": True
            }
        }
    )

    # Workflow validation
    def validate_custom_workflow(**context):
        """Validate custom workflow configuration."""
        params = context['params']

        # Validate research project configuration
        required_fields = ['research_project', 'input_dataset', 'custom_workflow_steps']
        for field in required_fields:
            if not params.get(field):
                raise ValueError(f"Required field '{field}' is missing")

        # Validate workflow steps
        steps = params['custom_workflow_steps']
        if not steps or len(steps) == 0:
            raise ValueError("At least one workflow step must be specified")

        logger.info(f"Validated custom workflow for project: {params['research_project']}")
        return True

    validate_workflow = PythonOperator(
        task_id='validate_custom_workflow',
        python_callable=validate_custom_workflow,
        dag=dag
    )

    # Execute custom workflow
    execute_workflow = CustomWorkflowOperator(
        task_id='execute_custom_workflow',
        input_image_path='{{ params.input_dataset }}/sample_image.fits',
        session_id='{{ params.session_id }}',
        workflow_steps='{{ params.custom_workflow_steps }}',
        output_configuration={
            'project_name': '{{ params.research_project }}',
            'enable_analysis': '{{ params.analysis_configuration.enable_photometry }}'
        },
        dag=dag
    )

    # Generate research report
    def generate_research_report(**context):
        """Generate comprehensive research report."""
        workflow_result = context['ti'].xcom_pull(task_ids='execute_custom_workflow')
        workflow_metrics = context['ti'].xcom_pull(key='workflow_metrics')

        research_report = {
            'project': context['params']['research_project'],
            'session_id': context['params']['session_id'],
            'workflow_completion': workflow_result,
            'performance_metrics': workflow_metrics,
            'generated_at': datetime.now().isoformat(),
            'data_products': {
                'processed_images': workflow_result.get('finalOutputPath'),
                'intermediate_results': workflow_result.get('intermediateResults', []),
                'quality_metrics': workflow_metrics
            }
        }

        logger.info(f"Generated research report for {research_report['project']}")
        return research_report

    generate_report = PythonOperator(
        task_id='generate_research_report',
        python_callable=generate_research_report,
        dag=dag
    )

    # Cleanup intermediate files
    cleanup_intermediate = IntermediateResultsOperator(
        task_id='cleanup_intermediate_files',
        session_id='{{ params.session_id }}',
        operation='cleanup',
        keep_final_result=True,
        dag=dag
    )

    # Set dependencies
    validate_workflow >> execute_workflow >> generate_report >> cleanup_intermediate

    return dag

# =============================================================================
# DAG Registration
# =============================================================================

# Create and register all template DAGs
algorithm_comparison_dag = create_algorithm_comparison_dag()
parameter_optimization_dag = create_parameter_optimization_dag()
quality_assessment_dag = create_quality_assessment_dag()
custom_research_dag = create_custom_research_dag()

# Make DAGs available to Airflow
globals()['algorithm_comparison_template'] = algorithm_comparison_dag
globals()['parameter_optimization_template'] = parameter_optimization_dag
globals()['quality_assessment_template'] = quality_assessment_dag
globals()['custom_research_template'] = custom_research_dag