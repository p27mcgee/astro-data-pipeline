"""
Batch Processing DAG

Handles large-scale batch processing of historical astronomical data.
Designed for processing archived observations and reprocessing campaigns.

Author: STScI Demo Project
"""

from datetime import datetime, timedelta
from typing import List, Dict, Any

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator
from airflow.providers.kubernetes.operators.kubernetes_pod import KubernetesPodOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from airflow.sensors.filesystem import FileSensor
from airflow.utils.dates import days_ago
from airflow.utils.task_group import TaskGroup
from airflow.models import Variable, XCom
from airflow.exceptions import AirflowException
from airflow.operators.email import EmailOperator

import boto3
import json
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed

# Default arguments
default_args = {
    'owner': 'astro-batch-processing',
    'depends_on_past': False,
    'start_date': days_ago(1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=10),
    'execution_timeout': timedelta(hours=8),  # Long timeout for batch jobs
}

# DAG definition
dag = DAG(
    'batch_processing_pipeline',
    default_args=default_args,
    description='Batch processing of large astronomical datasets',
    schedule_interval=None,  # Manual trigger only
    catchup=False,
    max_active_runs=1,  # Only one batch job at a time
    tags=['astronomy', 'batch-processing', 'historical-data'],
)

# Configuration
BATCH_SIZE = int(Variable.get("batch_processing_size", "100"))
MAX_PARALLEL_JOBS = int(Variable.get("max_parallel_jobs", "10"))
S3_BUCKET_RAW = Variable.get("s3_bucket_raw", "astro-data-pipeline-raw-data-dev")
S3_BUCKET_PROCESSED = Variable.get("s3_bucket_processed", "astro-data-pipeline-processed-data-dev")
PROCESSING_NAMESPACE = Variable.get("k8s_namespace", "astro-pipeline")

def discover_batch_files(**context) -> Dict[str, Any]:
    """
    Discover files for batch processing based on date range or pattern.
    """
    logging.info("Discovering files for batch processing")
    
    # Get configuration from DAG run parameters
    dag_run = context['dag_run']
    conf = dag_run.conf or {}
    
    start_date = conf.get('start_date')
    end_date = conf.get('end_date')
    file_pattern = conf.get('file_pattern', '*.fits')
    s3_prefix = conf.get('s3_prefix', 'fits/')
    max_files = conf.get('max_files', 1000)
    
    logging.info(f"Batch processing configuration: start_date={start_date}, "
                f"end_date={end_date}, pattern={file_pattern}, max_files={max_files}")
    
    s3_client = boto3.client('s3')
    
    try:
        # List objects in S3 with filtering
        paginator = s3_client.get_paginator('list_objects_v2')
        page_iterator = paginator.paginate(
            Bucket=S3_BUCKET_RAW,
            Prefix=s3_prefix
        )
        
        batch_files = []
        total_size = 0
        
        for page in page_iterator:
            if 'Contents' not in page:
                continue
                
            for obj in page['Contents']:
                # Apply date filtering if specified
                if start_date and obj['LastModified'].date() < datetime.fromisoformat(start_date).date():
                    continue
                if end_date and obj['LastModified'].date() > datetime.fromisoformat(end_date).date():
                    continue
                
                # Apply file pattern filtering
                if not obj['Key'].endswith('.fits'):
                    continue
                
                batch_files.append({
                    'bucket': S3_BUCKET_RAW,
                    'key': obj['Key'],
                    'size': obj['Size'],
                    'last_modified': obj['LastModified'].isoformat()
                })
                
                total_size += obj['Size']
                
                # Limit number of files
                if len(batch_files) >= max_files:
                    break
            
            if len(batch_files) >= max_files:
                break
        
        logging.info(f"Found {len(batch_files)} files for batch processing "
                    f"(total size: {total_size / (1024**3):.2f} GB)")
        
        # Create processing batches
        batches = []
        for i in range(0, len(batch_files), BATCH_SIZE):
            batch = batch_files[i:i + BATCH_SIZE]
            batches.append({
                'batch_id': f"batch_{i // BATCH_SIZE + 1:04d}",
                'files': batch,
                'total_files': len(batch),
                'total_size': sum(f['size'] for f in batch)
            })
        
        result = {
            'total_files': len(batch_files),
            'total_size': total_size,
            'num_batches': len(batches),
            'batches': batches
        }
        
        # Store for downstream tasks
        context['task_instance'].xcom_push(key='batch_discovery', value=result)
        
        return result
        
    except Exception as e:
        logging.error(f"Error discovering batch files: {e}")
        raise AirflowException(f"Failed to discover batch files: {e}")

def create_batch_jobs(**context) -> List[Dict]:
    """
    Create Kubernetes batch jobs for parallel processing.
    """
    batch_discovery = context['task_instance'].xcom_pull(key='batch_discovery')
    batches = batch_discovery['batches']
    
    logging.info(f"Creating {len(batches)} batch jobs for parallel processing")
    
    batch_jobs = []
    
    for batch in batches:
        job_spec = {
            'batch_id': batch['batch_id'],
            'files': batch['files'],
            'job_name': f"astro-batch-{batch['batch_id']}-{context['ds_nodash']}",
            'status': 'CREATED'
        }
        batch_jobs.append(job_spec)
    
    context['task_instance'].xcom_push(key='batch_jobs', value=batch_jobs)
    
    return batch_jobs

def process_batch_parallel(**context) -> Dict[str, Any]:
    """
    Process batches in parallel using ThreadPoolExecutor.
    """
    batch_jobs = context['task_instance'].xcom_pull(key='batch_jobs')
    
    logging.info(f"Starting parallel processing of {len(batch_jobs)} batches")
    
    def process_single_batch(batch_job):
        """Process a single batch of files."""
        batch_id = batch_job['batch_id']
        files = batch_job['files']
        
        logging.info(f"Processing batch {batch_id} with {len(files)} files")
        
        import requests
        
        processed_files = []
        failed_files = []
        
        for file_info in files:
            try:
                # Submit processing job
                job_request = {
                    'inputBucket': file_info['bucket'],
                    'inputObjectKey': file_info['key'],
                    'outputBucket': S3_BUCKET_PROCESSED,
                    'processingType': 'FULL_CALIBRATION',
                    'priority': 3  # Lower priority for batch jobs
                }
                
                response = requests.post(
                    f"{Variable.get('image_processor_url')}/api/v1/processing/jobs/s3",
                    json=job_request,
                    timeout=30
                )
                
                if response.status_code == 202:
                    job_data = response.json()
                    processed_files.append({
                        'file_info': file_info,
                        'job_id': job_data['jobId'],
                        'status': 'SUBMITTED'
                    })
                else:
                    failed_files.append({
                        'file_info': file_info,
                        'error': f"HTTP {response.status_code}"
                    })
                    
            except Exception as e:
                failed_files.append({
                    'file_info': file_info,
                    'error': str(e)
                })
        
        return {
            'batch_id': batch_id,
            'processed_files': processed_files,
            'failed_files': failed_files,
            'success_count': len(processed_files),
            'failure_count': len(failed_files)
        }
    
    # Process batches in parallel
    results = []
    with ThreadPoolExecutor(max_workers=MAX_PARALLEL_JOBS) as executor:
        future_to_batch = {
            executor.submit(process_single_batch, batch): batch
            for batch in batch_jobs
        }
        
        for future in as_completed(future_to_batch):
            batch = future_to_batch[future]
            try:
                result = future.result()
                results.append(result)
                logging.info(f"Completed batch {result['batch_id']}: "
                           f"{result['success_count']} success, {result['failure_count']} failed")
            except Exception as e:
                logging.error(f"Batch {batch['batch_id']} failed: {e}")
                results.append({
                    'batch_id': batch['batch_id'],
                    'error': str(e),
                    'success_count': 0,
                    'failure_count': len(batch['files'])
                })
    
    # Aggregate results
    total_success = sum(r['success_count'] for r in results)
    total_failures = sum(r['failure_count'] for r in results)
    
    summary = {
        'total_batches': len(batch_jobs),
        'completed_batches': len(results),
        'total_files_processed': total_success,
        'total_files_failed': total_failures,
        'success_rate': total_success / (total_success + total_failures) if (total_success + total_failures) > 0 else 0,
        'batch_results': results
    }
    
    logging.info(f"Batch processing complete: {total_success} successful, {total_failures} failed")
    
    context['task_instance'].xcom_push(key='processing_summary', value=summary)
    
    return summary

def monitor_batch_completion(**context) -> Dict[str, Any]:
    """
    Monitor all batch jobs until completion.
    """
    processing_summary = context['task_instance'].xcom_pull(key='processing_summary')
    
    logging.info("Monitoring batch job completion")
    
    import requests
    import time
    
    all_job_ids = []
    for batch_result in processing_summary['batch_results']:
        if 'processed_files' in batch_result:
            for file_result in batch_result['processed_files']:
                all_job_ids.append(file_result['job_id'])
    
    if not all_job_ids:
        logging.info("No jobs to monitor")
        return {'completed_jobs': 0, 'failed_jobs': 0}
    
    logging.info(f"Monitoring {len(all_job_ids)} batch processing jobs")
    
    completed_jobs = 0
    failed_jobs = 0
    max_wait_time = 14400  # 4 hours maximum wait
    check_interval = 60    # Check every minute
    start_time = time.time()
    
    while time.time() - start_time < max_wait_time:
        pending_jobs = []
        
        for job_id in all_job_ids:
            try:
                response = requests.get(
                    f"{Variable.get('image_processor_url')}/api/v1/processing/jobs/{job_id}",
                    timeout=10
                )
                
                if response.status_code == 200:
                    job_status = response.json()
                    status = job_status['status']
                    
                    if status == 'COMPLETED':
                        completed_jobs += 1
                        all_job_ids.remove(job_id)
                    elif status == 'FAILED':
                        failed_jobs += 1
                        all_job_ids.remove(job_id)
                    else:
                        pending_jobs.append(job_id)
                else:
                    pending_jobs.append(job_id)
                    
            except Exception as e:
                logging.warning(f"Error checking job {job_id}: {e}")
                pending_jobs.append(job_id)
        
        if not pending_jobs:
            break
            
        logging.info(f"Job status: {completed_jobs} completed, {failed_jobs} failed, "
                    f"{len(pending_jobs)} pending")
        time.sleep(check_interval)
    
    result = {
        'completed_jobs': completed_jobs,
        'failed_jobs': failed_jobs,
        'pending_jobs': len(all_job_ids)
    }
    
    logging.info(f"Monitoring complete: {completed_jobs} completed, "
                f"{failed_jobs} failed, {len(all_job_ids)} still pending")
    
    return result

def generate_batch_report(**context) -> str:
    """
    Generate a comprehensive report of batch processing results.
    """
    batch_discovery = context['task_instance'].xcom_pull(key='batch_discovery')
    processing_summary = context['task_instance'].xcom_pull(key='processing_summary')
    completion_result = context['task_instance'].xcom_pull(key='monitoring_result')
    
    report = f"""
    Batch Processing Report
    ======================
    
    Execution Date: {context['ds']}
    DAG Run ID: {context['dag_run'].run_id}
    
    Data Discovery:
    - Total files found: {batch_discovery['total_files']}
    - Total data size: {batch_discovery['total_size'] / (1024**3):.2f} GB
    - Number of batches: {batch_discovery['num_batches']}
    - Batch size: {BATCH_SIZE} files per batch
    
    Processing Results:
    - Files submitted: {processing_summary['total_files_processed']}
    - Files failed submission: {processing_summary['total_files_failed']}
    - Success rate: {processing_summary['success_rate']:.2%}
    
    Job Completion:
    - Completed jobs: {completion_result['completed_jobs']}
    - Failed jobs: {completion_result['failed_jobs']}
    - Pending jobs: {completion_result['pending_jobs']}
    
    Batch Details:
    """
    
    for batch_result in processing_summary['batch_results']:
        batch_id = batch_result['batch_id']
        success_count = batch_result['success_count']
        failure_count = batch_result['failure_count']
        
        report += f"\n    {batch_id}: {success_count} success, {failure_count} failed"
    
    logging.info(f"Generated batch processing report:\n{report}")
    
    # Save report to S3
    try:
        s3_client = boto3.client('s3')
        report_key = f"reports/batch_processing/{context['ds']}/{context['dag_run'].run_id}_report.txt"
        
        s3_client.put_object(
            Bucket=S3_BUCKET_PROCESSED,
            Key=report_key,
            Body=report.encode('utf-8'),
            ContentType='text/plain'
        )
        
        logging.info(f"Saved report to s3://{S3_BUCKET_PROCESSED}/{report_key}")
        
    except Exception as e:
        logging.warning(f"Failed to save report to S3: {e}")
    
    return report

# Task definitions

discover_task = PythonOperator(
    task_id='discover_batch_files',
    python_callable=discover_batch_files,
    dag=dag,
)

create_jobs_task = PythonOperator(
    task_id='create_batch_jobs',
    python_callable=create_batch_jobs,
    dag=dag,
)

# Parallel processing group
with TaskGroup('parallel_processing', dag=dag) as processing_group:
    
    process_task = PythonOperator(
        task_id='process_batches_parallel',
        python_callable=process_batch_parallel,
        pool='batch_processing_pool',  # Use dedicated resource pool
    )
    
    monitor_task = PythonOperator(
        task_id='monitor_batch_completion',
        python_callable=monitor_batch_completion,
    )
    
    process_task >> monitor_task

# Cleanup and validation
cleanup_task = PostgresOperator(
    task_id='cleanup_old_jobs',
    postgres_conn_id='astro_processing_db',
    sql="""
    UPDATE processing_jobs 
    SET status = 'EXPIRED'
    WHERE status IN ('QUEUED', 'RUNNING') 
    AND created_at < NOW() - INTERVAL '24 hours';
    """,
    dag=dag,
)

validation_task = PostgresOperator(
    task_id='validate_batch_results',
    postgres_conn_id='astro_catalog_db',
    sql="""
    SELECT 
        COUNT(*) as objects_added_today,
        COUNT(DISTINCT object_type) as object_types,
        MIN(created_at) as first_object,
        MAX(created_at) as last_object
    FROM astronomical_objects 
    WHERE DATE(created_at) = CURRENT_DATE;
    """,
    dag=dag,
)

report_task = PythonOperator(
    task_id='generate_batch_report',
    python_callable=generate_batch_report,
    dag=dag,
)

# Notification
success_notification = EmailOperator(
    task_id='send_success_notification',
    to=['astro-batch-processing@example.com'],
    subject='Batch Processing Completed Successfully',
    html_content="""
    <h3>Batch Processing Pipeline Completed</h3>
    <p>The batch processing pipeline has completed successfully.</p>
    <p><strong>DAG:</strong> {{ dag.dag_id }}</p>
    <p><strong>Execution Date:</strong> {{ ds }}</p>
    <p><strong>Duration:</strong> {{ dag_run.end_date - dag_run.start_date }}</p>
    <p>Check the detailed report in the processed data bucket.</p>
    """,
    dag=dag,
    trigger_rule='all_success',
)

failure_notification = EmailOperator(
    task_id='send_failure_notification',
    to=['astro-batch-processing@example.com'],
    subject='Batch Processing Failed',
    html_content="""
    <h3>Batch Processing Pipeline Failed</h3>
    <p>The batch processing pipeline has encountered a failure.</p>
    <p><strong>DAG:</strong> {{ dag.dag_id }}</p>
    <p><strong>Execution Date:</strong> {{ ds }}</p>
    <p><strong>Failed Task:</strong> {{ task_instance.task_id }}</p>
    <p>Please check the Airflow logs for more details.</p>
    """,
    dag=dag,
    trigger_rule='one_failed',
)

# Define task dependencies
discover_task >> create_jobs_task >> processing_group
processing_group >> [cleanup_task, validation_task] >> report_task
report_task >> success_notification

# Add failure notification to all tasks
all_tasks = [discover_task, create_jobs_task, processing_group, 
             cleanup_task, validation_task, report_task]
for task in all_tasks:
    task >> failure_notification
