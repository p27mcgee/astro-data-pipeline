"""
Data Quality Monitoring DAG

Monitors data quality metrics across the astronomical data pipeline.
Performs regular checks on data integrity, processing performance,
and catalog consistency.

Author: STScI Demo Project
"""

from datetime import datetime, timedelta
from typing import Dict, Any, List

from airflow import DAG
from airflow.operators.python import PythonOperator, BranchPythonOperator
from airflow.operators.bash import BashOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.providers.http.sensors.http import HttpSensor
from airflow.operators.email import EmailOperator
from airflow.operators.dummy import DummyOperator
from airflow.utils.dates import days_ago
from airflow.utils.task_group import TaskGroup
from airflow.models import Variable
from airflow.exceptions import AirflowException

import boto3
import json
import logging
import pandas as pd
from dataclasses import dataclass

# Default arguments
default_args = {
    'owner': 'astro-data-quality',
    'depends_on_past': False,
    'start_date': days_ago(1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
    'execution_timeout': timedelta(minutes=30),
}

# DAG definition
dag = DAG(
    'data_quality_monitoring',
    default_args=default_args,
    description='Monitor data quality across the pipeline',
    schedule_interval='0 */6 * * *',  # Every 6 hours
    catchup=False,
    max_active_runs=1,
    tags=['astronomy', 'data-quality', 'monitoring'],
)

# Configuration
QUALITY_THRESHOLDS = {
    'min_daily_observations': 10,
    'max_processing_time_hours': 2,
    'min_success_rate': 0.85,
    'max_error_rate': 0.15,
    'min_catalog_objects_per_hour': 50,
    'max_duplicate_objects': 0.05
}

@dataclass
class QualityMetric:
    name: str
    value: float
    threshold: float
    status: str  # 'PASS', 'WARNING', 'CRITICAL'
    description: str

def check_service_health(**context) -> Dict[str, bool]:
    """
    Check health status of all pipeline services.
    """
    logging.info("Checking service health status")
    
    import requests
    
    services = {
        'image_processor': Variable.get('image_processor_url', 'http://image-processor-service:8080'),
        'catalog_service': Variable.get('catalog_service_url', 'http://catalog-service:8080'),
    }
    
    health_status = {}
    
    for service_name, base_url in services.items():
        try:
            response = requests.get(
                f"{base_url}/actuator/health",
                timeout=10
            )
            
            if response.status_code == 200:
                health_data = response.json()
                is_healthy = health_data.get('status') == 'UP'
                health_status[service_name] = is_healthy
                
                if is_healthy:
                    logging.info(f"Service {service_name} is healthy")
                else:
                    logging.warning(f"Service {service_name} reports unhealthy status: {health_data}")
            else:
                health_status[service_name] = False
                logging.error(f"Service {service_name} health check failed: HTTP {response.status_code}")
                
        except Exception as e:
            health_status[service_name] = False
            logging.error(f"Service {service_name} health check error: {e}")
    
    context['task_instance'].xcom_push(key='service_health', value=health_status)
    
    # Determine if we should continue with quality checks
    all_healthy = all(health_status.values())
    
    if not all_healthy:
        logging.warning("Some services are unhealthy, quality checks may be affected")
    
    return health_status

def analyze_processing_performance(**context) -> List[QualityMetric]:
    """
    Analyze processing job performance metrics.
    """
    logging.info("Analyzing processing performance metrics")
    
    postgres_hook = PostgresHook(postgres_conn_id='astro_processing_db')
    
    # Query processing job statistics for last 24 hours
    sql = """
    SELECT 
        COUNT(*) as total_jobs,
        COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_jobs,
        COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed_jobs,
        AVG(EXTRACT(EPOCH FROM (completed_at - started_at))/3600.0) as avg_processing_hours,
        MAX(EXTRACT(EPOCH FROM (completed_at - started_at))/3600.0) as max_processing_hours,
        COUNT(CASE WHEN retry_count > 0 THEN 1 END) as jobs_with_retries
    FROM processing_jobs 
    WHERE created_at >= NOW() - INTERVAL '24 hours'
    """
    
    try:
        result = postgres_hook.get_first(sql)
        
        if result and result[0] > 0:  # total_jobs > 0
            total_jobs, completed_jobs, failed_jobs, avg_hours, max_hours, retries = result
            
            success_rate = completed_jobs / total_jobs if total_jobs > 0 else 0
            error_rate = failed_jobs / total_jobs if total_jobs > 0 else 0
            
            metrics = [
                QualityMetric(
                    name='daily_processing_volume',
                    value=total_jobs,
                    threshold=QUALITY_THRESHOLDS['min_daily_observations'],
                    status='PASS' if total_jobs >= QUALITY_THRESHOLDS['min_daily_observations'] else 'WARNING',
                    description=f'{total_jobs} jobs processed in last 24 hours'
                ),
                QualityMetric(
                    name='processing_success_rate',
                    value=success_rate,
                    threshold=QUALITY_THRESHOLDS['min_success_rate'],
                    status='PASS' if success_rate >= QUALITY_THRESHOLDS['min_success_rate'] else 'CRITICAL',
                    description=f'{success_rate:.2%} success rate'
                ),
                QualityMetric(
                    name='processing_error_rate',
                    value=error_rate,
                    threshold=QUALITY_THRESHOLDS['max_error_rate'],
                    status='PASS' if error_rate <= QUALITY_THRESHOLDS['max_error_rate'] else 'WARNING',
                    description=f'{error_rate:.2%} error rate'
                ),
                QualityMetric(
                    name='avg_processing_time',
                    value=avg_hours or 0,
                    threshold=QUALITY_THRESHOLDS['max_processing_time_hours'],
                    status='PASS' if (avg_hours or 0) <= QUALITY_THRESHOLDS['max_processing_time_hours'] else 'WARNING',
                    description=f'{avg_hours:.2f} hours average processing time'
                )
            ]
        else:
            # No jobs processed recently
            metrics = [
                QualityMetric(
                    name='daily_processing_volume',
                    value=0,
                    threshold=QUALITY_THRESHOLDS['min_daily_observations'],
                    status='CRITICAL',
                    description='No jobs processed in last 24 hours'
                )
            ]
            
    except Exception as e:
        logging.error(f"Error analyzing processing performance: {e}")
        metrics = [
            QualityMetric(
                name='processing_analysis_error',
                value=0,
                threshold=1,
                status='CRITICAL',
                description=f'Failed to analyze processing performance: {e}'
            )
        ]
    
    context['task_instance'].xcom_push(key='processing_metrics', value=[
        {'name': m.name, 'value': m.value, 'threshold': m.threshold, 
         'status': m.status, 'description': m.description} for m in metrics
    ])
    
    return metrics

def analyze_catalog_quality(**context) -> List[QualityMetric]:
    """
    Analyze astronomical catalog data quality.
    """
    logging.info("Analyzing catalog data quality")
    
    postgres_hook = PostgresHook(postgres_conn_id='astro_catalog_db')
    
    metrics = []
    
    try:
        # Check catalog growth rate
        growth_sql = """
        SELECT 
            COUNT(*) as total_objects,
            COUNT(CASE WHEN created_at >= NOW() - INTERVAL '1 hour' THEN 1 END) as recent_objects,
            COUNT(DISTINCT object_type) as object_types,
            COUNT(CASE WHEN magnitude IS NULL THEN 1 END) as objects_without_magnitude
        FROM astronomical_objects
        """
        
        result = postgres_hook.get_first(growth_sql)
        if result:
            total_objects, recent_objects, object_types, no_magnitude = result
            
            metrics.append(QualityMetric(
                name='catalog_growth_rate',
                value=recent_objects,
                threshold=QUALITY_THRESHOLDS['min_catalog_objects_per_hour'],
                status='PASS' if recent_objects >= QUALITY_THRESHOLDS['min_catalog_objects_per_hour'] else 'WARNING',
                description=f'{recent_objects} objects added in last hour'
            ))
            
            metrics.append(QualityMetric(
                name='catalog_completeness',
                value=1 - (no_magnitude / total_objects) if total_objects > 0 else 0,
                threshold=0.8,
                status='PASS' if (no_magnitude / total_objects if total_objects > 0 else 1) <= 0.2 else 'WARNING',
                description=f'{((total_objects - no_magnitude) / total_objects * 100) if total_objects > 0 else 0:.1f}% objects have magnitude measurements'
            ))
        
        # Check for duplicate objects
        duplicate_sql = """
        SELECT COUNT(*) as potential_duplicates
        FROM (
            SELECT ra, dec, COUNT(*)
            FROM astronomical_objects
            WHERE ra IS NOT NULL AND dec IS NOT NULL
            GROUP BY ROUND(ra::numeric, 4), ROUND(dec::numeric, 4)
            HAVING COUNT(*) > 1
        ) dup_coords
        """
        
        dup_result = postgres_hook.get_first(duplicate_sql)
        if dup_result:
            potential_duplicates = dup_result[0]
            duplicate_rate = potential_duplicates / total_objects if total_objects > 0 else 0
            
            metrics.append(QualityMetric(
                name='duplicate_object_rate',
                value=duplicate_rate,
                threshold=QUALITY_THRESHOLDS['max_duplicate_objects'],
                status='PASS' if duplicate_rate <= QUALITY_THRESHOLDS['max_duplicate_objects'] else 'WARNING',
                description=f'{duplicate_rate:.2%} potential duplicate objects'
            ))
        
        # Check coordinate validity
        coord_sql = """
        SELECT 
            COUNT(CASE WHEN ra < 0 OR ra >= 360 THEN 1 END) as invalid_ra,
            COUNT(CASE WHEN dec < -90 OR dec > 90 THEN 1 END) as invalid_dec,
            COUNT(*) as total_with_coords
        FROM astronomical_objects 
        WHERE ra IS NOT NULL AND dec IS NOT NULL
        """
        
        coord_result = postgres_hook.get_first(coord_sql)
        if coord_result:
            invalid_ra, invalid_dec, total_coords = coord_result
            invalid_coords = invalid_ra + invalid_dec
            coord_validity = 1 - (invalid_coords / total_coords) if total_coords > 0 else 1
            
            metrics.append(QualityMetric(
                name='coordinate_validity',
                value=coord_validity,
                threshold=0.99,
                status='PASS' if coord_validity >= 0.99 else 'CRITICAL',
                description=f'{coord_validity:.2%} coordinates are valid'
            ))
            
    except Exception as e:
        logging.error(f"Error analyzing catalog quality: {e}")
        metrics.append(QualityMetric(
            name='catalog_analysis_error',
            value=0,
            threshold=1,
            status='CRITICAL',
            description=f'Failed to analyze catalog quality: {e}'
        ))
    
    context['task_instance'].xcom_push(key='catalog_metrics', value=[
        {'name': m.name, 'value': m.value, 'threshold': m.threshold, 
         'status': m.status, 'description': m.description} for m in metrics
    ])
    
    return metrics

def check_data_consistency(**context) -> List[QualityMetric]:
    """
    Check consistency between processing jobs and catalog entries.
    """
    logging.info("Checking data consistency across systems")
    
    processing_hook = PostgresHook(postgres_conn_id='astro_processing_db')
    catalog_hook = PostgresHook(postgres_conn_id='astro_catalog_db')
    
    metrics = []
    
    try:
        # Check if completed jobs have corresponding catalog entries
        consistency_sql = """
        SELECT 
            COUNT(CASE WHEN pj.status = 'COMPLETED' THEN 1 END) as completed_jobs,
            COUNT(CASE WHEN pj.status = 'COMPLETED' AND ao.id IS NOT NULL THEN 1 END) as jobs_with_catalog_data
        FROM processing_jobs pj
        LEFT JOIN detections d ON pj.job_id = d.processing_job_id
        LEFT JOIN astronomical_objects ao ON d.object_id = ao.id
        WHERE pj.completed_at >= NOW() - INTERVAL '24 hours'
        """
        
        # This is a simplified check - in reality, the join would be more complex
        # For demo purposes, we'll check basic consistency
        
        processing_count_sql = "SELECT COUNT(*) FROM processing_jobs WHERE status = 'COMPLETED' AND completed_at >= NOW() - INTERVAL '24 hours'"
        catalog_count_sql = "SELECT COUNT(*) FROM astronomical_objects WHERE created_at >= NOW() - INTERVAL '24 hours'"
        
        processing_count = processing_hook.get_first(processing_count_sql)[0]
        catalog_count = catalog_hook.get_first(catalog_count_sql)[0]
        
        # Expect some ratio of catalog objects to completed jobs
        expected_ratio = 50  # Expect ~50 objects per completed job on average
        expected_objects = processing_count * expected_ratio
        
        consistency_ratio = catalog_count / expected_objects if expected_objects > 0 else 0
        
        metrics.append(QualityMetric(
            name='processing_catalog_consistency',
            value=consistency_ratio,
            threshold=0.5,  # At least 50% of expected objects
            status='PASS' if consistency_ratio >= 0.5 else 'WARNING',
            description=f'{catalog_count} catalog objects from {processing_count} completed jobs (ratio: {consistency_ratio:.2f})'
        ))
        
    except Exception as e:
        logging.error(f"Error checking data consistency: {e}")
        metrics.append(QualityMetric(
            name='consistency_check_error',
            value=0,
            threshold=1,
            status='CRITICAL',
            description=f'Failed to check data consistency: {e}'
        ))
    
    context['task_instance'].xcom_push(key='consistency_metrics', value=[
        {'name': m.name, 'value': m.value, 'threshold': m.threshold, 
         'status': m.status, 'description': m.description} for m in metrics
    ])
    
    return metrics

def evaluate_overall_quality(**context) -> str:
    """
    Evaluate overall pipeline quality and determine alert level.
    """
    logging.info("Evaluating overall pipeline quality")
    
    # Collect all metrics from previous tasks
    processing_metrics = context['task_instance'].xcom_pull(key='processing_metrics') or []
    catalog_metrics = context['task_instance'].xcom_pull(key='catalog_metrics') or []
    consistency_metrics = context['task_instance'].xcom_pull(key='consistency_metrics') or []
    
    all_metrics = processing_metrics + catalog_metrics + consistency_metrics
    
    # Count metrics by status
    critical_count = sum(1 for m in all_metrics if m['status'] == 'CRITICAL')
    warning_count = sum(1 for m in all_metrics if m['status'] == 'WARNING')
    pass_count = sum(1 for m in all_metrics if m['status'] == 'PASS')
    
    total_metrics = len(all_metrics)
    
    # Determine overall status
    if critical_count > 0:
        overall_status = 'CRITICAL'
        next_task = 'send_critical_alert'
    elif warning_count > total_metrics * 0.3:  # More than 30% warnings
        overall_status = 'WARNING'
        next_task = 'send_warning_alert'
    else:
        overall_status = 'HEALTHY'
        next_task = 'log_healthy_status'
    
    quality_summary = {
        'overall_status': overall_status,
        'total_metrics': total_metrics,
        'critical_issues': critical_count,
        'warnings': warning_count,
        'passing_checks': pass_count,
        'metrics': all_metrics
    }
    
    context['task_instance'].xcom_push(key='quality_summary', value=quality_summary)
    
    logging.info(f"Overall quality status: {overall_status} "
                f"({critical_count} critical, {warning_count} warnings, {pass_count} pass)")
    
    return next_task

def generate_quality_report(**context) -> str:
    """
    Generate a comprehensive quality report.
    """
    quality_summary = context['task_instance'].xcom_pull(key='quality_summary')
    service_health = context['task_instance'].xcom_pull(key='service_health')
    
    report = f"""
Data Quality Report - {context['ds']}
{'='*50}

Overall Status: {quality_summary['overall_status']}
Execution Time: {datetime.now().isoformat()}

Service Health:
{'-'*20}
"""
    
    for service, status in service_health.items():
        status_text = "✓ Healthy" if status else "✗ Unhealthy"
        report += f"  {service}: {status_text}\n"
    
    report += f"""
Quality Metrics Summary:
{'-'*25}
  Total Checks: {quality_summary['total_metrics']}
  Critical Issues: {quality_summary['critical_issues']}
  Warnings: {quality_summary['warnings']}
  Passing: {quality_summary['passing_checks']}

Detailed Metrics:
{'-'*20}
"""
    
    for metric in quality_summary['metrics']:
        status_icon = {
            'PASS': '✓',
            'WARNING': '⚠',
            'CRITICAL': '✗'
        }.get(metric['status'], '?')
        
        report += f"  {status_icon} {metric['name']}: {metric['description']}\n"
    
    logging.info(f"Generated quality report:\n{report}")
    
    # Save report to S3
    try:
        s3_client = boto3.client('s3')
        report_key = f"quality-reports/{context['ds']}/quality_report_{context['ts_nodash']}.txt"
        
        s3_client.put_object(
            Bucket=Variable.get('s3_bucket_processed'),
            Key=report_key,
            Body=report.encode('utf-8'),
            ContentType='text/plain'
        )
        
        logging.info(f"Saved quality report to S3: {report_key}")
        
    except Exception as e:
        logging.warning(f"Failed to save report to S3: {e}")
    
    return report

# Task definitions

# Service health checks
health_check_task = PythonOperator(
    task_id='check_service_health',
    python_callable=check_service_health,
    dag=dag,
)

# Quality analysis tasks
with TaskGroup('quality_analysis', dag=dag) as quality_group:
    
    processing_analysis = PythonOperator(
        task_id='analyze_processing_performance',
        python_callable=analyze_processing_performance,
    )
    
    catalog_analysis = PythonOperator(
        task_id='analyze_catalog_quality',
        python_callable=analyze_catalog_quality,
    )
    
    consistency_check = PythonOperator(
        task_id='check_data_consistency',
        python_callable=check_data_consistency,
    )
    
    [processing_analysis, catalog_analysis, consistency_check]

# Overall evaluation
evaluate_task = BranchPythonOperator(
    task_id='evaluate_overall_quality',
    python_callable=evaluate_overall_quality,
    dag=dag,
)

# Conditional alert tasks
critical_alert = EmailOperator(
    task_id='send_critical_alert',
    to=['astro-pipeline-alerts@example.com'],
    subject='CRITICAL: Data Quality Alert',
    html_content="""
    <h2 style="color: red;">CRITICAL Data Quality Alert</h2>
    <p>Critical data quality issues have been detected in the astronomical data pipeline.</p>
    <p><strong>Execution Date:</strong> {{ ds }}</p>
    <p><strong>Issues:</strong> {{ task_instance.xcom_pull(key='quality_summary')['critical_issues'] }} critical issues found</p>
    <p>Please investigate immediately and check the detailed quality report.</p>
    """,
    dag=dag,
)

warning_alert = EmailOperator(
    task_id='send_warning_alert',
    to=['astro-pipeline-monitoring@example.com'],
    subject='WARNING: Data Quality Issues Detected',
    html_content="""
    <h2 style="color: orange;">Data Quality Warning</h2>
    <p>Data quality warnings have been detected in the astronomical data pipeline.</p>
    <p><strong>Execution Date:</strong> {{ ds }}</p>
    <p><strong>Warnings:</strong> {{ task_instance.xcom_pull(key='quality_summary')['warnings'] }} warnings found</p>
    <p>Please review the quality report and consider investigation.</p>
    """,
    dag=dag,
)

healthy_status = DummyOperator(
    task_id='log_healthy_status',
    dag=dag,
)

# Report generation
report_task = PythonOperator(
    task_id='generate_quality_report',
    python_callable=generate_quality_report,
    dag=dag,
    trigger_rule='none_failed_or_skipped',
)

# Define task dependencies
health_check_task >> quality_group >> evaluate_task
evaluate_task >> [critical_alert, warning_alert, healthy_status]
[critical_alert, warning_alert, healthy_status] >> report_task