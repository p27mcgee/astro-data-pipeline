"""
Telescope Data Processing DAG

Main workflow for processing astronomical images through the complete
calibration pipeline from raw FITS files to science-ready data products.

Author: STScI Demo Project
"""

from datetime import datetime, timedelta
from typing import Dict, Any

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from airflow.providers.http.operators.http import SimpleHttpOperator
from airflow.providers.amazon.aws.operators.s3 import S3CreateObjectOperator, S3DeleteObjectOperator
from airflow.providers.amazon.aws.sensors.s3 import S3KeySensor
from airflow.providers.kubernetes.operators.kubernetes_pod import KubernetesPodOperator
from airflow.operators.email import EmailOperator
from airflow.utils.dates import days_ago
from airflow.utils.task_group import TaskGroup
from airflow.models import Variable
from airflow.exceptions import AirflowException

import boto3
import json
import logging

# Default arguments for all tasks
default_args = {
    'owner': 'astro-pipeline',
    'depends_on_past': False,
    'start_date': days_ago(1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
    'execution_timeout': timedelta(hours=2),
}

# DAG definition
dag = DAG(
    'telescope_data_processing',
    default_args=default_args,
    description='Complete telescope data processing pipeline',
    schedule_interval='@hourly',  # Process new data every hour
    catchup=False,
    max_active_runs=3,
    tags=['astronomy', 'image-processing', 'production'],
)

# Configuration variables
S3_BUCKET_RAW = Variable.get("s3_bucket_raw", "astro-data-pipeline-raw-data-dev")
S3_BUCKET_PROCESSED = Variable.get("s3_bucket_processed", "astro-data-pipeline-processed-data-dev") 
S3_BUCKET_ARCHIVE = Variable.get("s3_bucket_archive", "astro-data-pipeline-archive-dev")
IMAGE_PROCESSOR_URL = Variable.get("image_processor_url", "http://image-processor-service:8080")
CATALOG_SERVICE_URL = Variable.get("catalog_service_url", "http://catalog-service:8080")
PROCESSING_NAMESPACE = Variable.get("k8s_namespace", "astro-pipeline")

def discover_new_fits_files(**context) -> list:
    """
    Discover new FITS files in S3 that need processing.
    """
    logging.info("Discovering new FITS files for processing")
    
    s3_client = boto3.client('s3')
    execution_date = context['execution_date']
    
    # Look for files uploaded in the last hour
    since_time = execution_date - timedelta(hours=1)
    
    try:
        response = s3_client.list_objects_v2(
            Bucket=S3_BUCKET_RAW,
            Prefix='fits/',
            StartAfter=f"fits/{since_time.strftime('%Y/%m/%d')}"
        )
        
        fits_files = []
        if 'Contents' in response:
            for obj in response['Contents']:
                if obj['Key'].endswith('.fits') and obj['LastModified'] >= since_time:
                    fits_files.append({
                        'bucket': S3_BUCKET_RAW,
                        'key': obj['Key'],
                        'size': obj['Size'],
                        'last_modified': obj['LastModified'].isoformat()
                    })
        
        logging.info(f"Found {len(fits_files)} new FITS files to process")
        
        # Store file list for downstream tasks
        context['task_instance'].xcom_push(key='fits_files', value=fits_files)
        
        return fits_files
        
    except Exception as e:
        logging.error(f"Error discovering FITS files: {e}")
        raise AirflowException(f"Failed to discover new FITS files: {e}")

def validate_fits_files(**context) -> dict:
    """
    Validate FITS files before processing to ensure they meet quality standards.
    """
    fits_files = context['task_instance'].xcom_pull(key='fits_files')
    
    if not fits_files:
        logging.info("No FITS files to validate")
        return {'valid_files': [], 'invalid_files': []}
    
    logging.info(f"Validating {len(fits_files)} FITS files")
    
    valid_files = []
    invalid_files = []
    
    for file_info in fits_files:
        try:
            # Basic validation checks
            file_size = file_info['size']
            file_key = file_info['key']
            
            # Check file size (should be reasonable for astronomical images)
            if file_size < 1024 * 1024:  # Less than 1MB
                invalid_files.append({**file_info, 'reason': 'File too small'})
                continue
                
            if file_size > 500 * 1024 * 1024:  # Greater than 500MB
                invalid_files.append({**file_info, 'reason': 'File too large'})
                continue
            
            # Check file extension
            if not file_key.lower().endswith('.fits'):
                invalid_files.append({**file_info, 'reason': 'Invalid file extension'})
                continue
                
            # TODO: Add FITS header validation using astropy
            # For now, accept all files that pass basic checks
            valid_files.append(file_info)
            
        except Exception as e:
            logging.warning(f"Error validating file {file_info['key']}: {e}")
            invalid_files.append({**file_info, 'reason': f'Validation error: {e}'})
    
    result = {
        'valid_files': valid_files,
        'invalid_files': invalid_files
    }
    
    logging.info(f"Validation complete: {len(valid_files)} valid, {len(invalid_files)} invalid")
    
    # Store results for downstream tasks
    context['task_instance'].xcom_push(key='validation_result', value=result)
    
    return result

def submit_processing_jobs(**context) -> list:
    """
    Submit processing jobs to the image processor service for valid FITS files.
    """
    validation_result = context['task_instance'].xcom_pull(key='validation_result')
    valid_files = validation_result.get('valid_files', [])
    
    if not valid_files:
        logging.info("No valid files to process")
        return []
    
    logging.info(f"Submitting {len(valid_files)} files for processing")
    
    import requests
    
    submitted_jobs = []
    
    for file_info in valid_files:
        try:
            job_request = {
                'inputBucket': file_info['bucket'],
                'inputObjectKey': file_info['key'],
                'outputBucket': S3_BUCKET_PROCESSED,
                'processingType': 'FULL_CALIBRATION',
                'priority': 5
            }
            
            response = requests.post(
                f"{IMAGE_PROCESSOR_URL}/api/v1/processing/jobs/s3",
                json=job_request,
                timeout=30
            )
            
            if response.status_code == 202:
                job_data = response.json()
                submitted_jobs.append({
                    'job_id': job_data['jobId'],
                    'file_info': file_info,
                    'status': 'SUBMITTED'
                })
                logging.info(f"Submitted job {job_data['jobId']} for file {file_info['key']}")
            else:
                logging.error(f"Failed to submit job for {file_info['key']}: {response.status_code}")
                
        except Exception as e:
            logging.error(f"Error submitting job for {file_info['key']}: {e}")
    
    # Store job information for monitoring
    context['task_instance'].xcom_push(key='submitted_jobs', value=submitted_jobs)
    
    return submitted_jobs

def monitor_processing_jobs(**context) -> dict:
    """
    Monitor the status of submitted processing jobs.
    """
    submitted_jobs = context['task_instance'].xcom_pull(key='submitted_jobs')
    
    if not submitted_jobs:
        logging.info("No jobs to monitor")
        return {'completed': [], 'failed': [], 'running': []}
    
    logging.info(f"Monitoring {len(submitted_jobs)} processing jobs")
    
    import requests
    import time
    
    completed_jobs = []
    failed_jobs = []
    running_jobs = []
    
    max_wait_time = 3600  # 1 hour maximum wait
    check_interval = 30   # Check every 30 seconds
    start_time = time.time()
    
    while time.time() - start_time < max_wait_time:
        all_completed = True
        
        for job in submitted_jobs:
            if job.get('final_status'):
                continue  # Already processed
                
            job_id = job['job_id']
            
            try:
                response = requests.get(
                    f"{IMAGE_PROCESSOR_URL}/api/v1/processing/jobs/{job_id}",
                    timeout=10
                )
                
                if response.status_code == 200:
                    job_status = response.json()
                    status = job_status['status']
                    
                    if status == 'COMPLETED':
                        job['final_status'] = 'COMPLETED'
                        job['completed_at'] = job_status.get('completedAt')
                        job['output_path'] = job_status.get('outputPath')
                        completed_jobs.append(job)
                        logging.info(f"Job {job_id} completed successfully")
                        
                    elif status == 'FAILED':
                        job['final_status'] = 'FAILED'
                        job['error_message'] = job_status.get('errorMessage')
                        failed_jobs.append(job)
                        logging.error(f"Job {job_id} failed: {job_status.get('errorMessage')}")
                        
                    else:
                        all_completed = False
                        running_jobs.append(job)
                        
                else:
                    logging.warning(f"Failed to get status for job {job_id}: {response.status_code}")
                    all_completed = False
                    
            except Exception as e:
                logging.error(f"Error checking job {job_id}: {e}")
                all_completed = False
        
        if all_completed:
            break
            
        time.sleep(check_interval)
    
    result = {
        'completed': completed_jobs,
        'failed': failed_jobs, 
        'running': running_jobs
    }
    
    logging.info(f"Job monitoring complete: {len(completed_jobs)} completed, "
                f"{len(failed_jobs)} failed, {len(running_jobs)} still running")
    
    context['task_instance'].xcom_push(key='job_results', value=result)
    
    return result

def update_catalog(**context) -> dict:
    """
    Update the astronomical catalog with processed image results.
    """
    job_results = context['task_instance'].xcom_pull(key='job_results')
    completed_jobs = job_results.get('completed', [])
    
    if not completed_jobs:
        logging.info("No completed jobs to update catalog")
        return {'updated_objects': 0}
    
    logging.info(f"Updating catalog with results from {len(completed_jobs)} processed images")
    
    import requests
    
    updated_objects = 0
    
    for job in completed_jobs:
        try:
            job_id = job['job_id']
            
            # Get processing results with detected objects
            response = requests.get(
                f"{IMAGE_PROCESSOR_URL}/api/v1/processing/jobs/{job_id}/results",
                timeout=30
            )
            
            if response.status_code == 200:
                processing_results = response.json()
                
                # Extract detected astronomical objects
                if 'detectedObjects' in processing_results:
                    objects = processing_results['detectedObjects']
                    
                    # Submit objects to catalog service
                    catalog_response = requests.post(
                        f"{CATALOG_SERVICE_URL}/api/v1/catalog/objects/batch",
                        json={'objects': objects},
                        timeout=60
                    )
                    
                    if catalog_response.status_code == 201:
                        batch_result = catalog_response.json()
                        objects_added = batch_result.get('objectsAdded', 0)
                        updated_objects += objects_added
                        logging.info(f"Added {objects_added} objects from job {job_id} to catalog")
                    else:
                        logging.error(f"Failed to update catalog for job {job_id}: "
                                    f"{catalog_response.status_code}")
                        
            else:
                logging.warning(f"Failed to get results for job {job_id}: {response.status_code}")
                
        except Exception as e:
            logging.error(f"Error updating catalog for job {job['job_id']}: {e}")
    
    result = {'updated_objects': updated_objects}
    logging.info(f"Catalog update complete: {updated_objects} objects added/updated")
    
    return result

def archive_processed_data(**context) -> dict:
    """
    Archive processed data to long-term storage.
    """
    job_results = context['task_instance'].xcom_pull(key='job_results')
    completed_jobs = job_results.get('completed', [])
    
    if not completed_jobs:
        logging.info("No data to archive")
        return {'archived_files': 0}
    
    logging.info(f"Archiving {len(completed_jobs)} processed files")
    
    s3_client = boto3.client('s3')
    archived_files = 0
    
    for job in completed_jobs:
        try:
            if 'output_path' in job:
                # Copy processed file to archive bucket
                source_key = job['output_path']
                archive_key = f"archive/{datetime.now().strftime('%Y/%m/%d')}/{source_key.split('/')[-1]}"
                
                copy_source = {'Bucket': S3_BUCKET_PROCESSED, 'Key': source_key}
                
                s3_client.copy_object(
                    CopySource=copy_source,
                    Bucket=S3_BUCKET_ARCHIVE,
                    Key=archive_key
                )
                
                archived_files += 1
                logging.info(f"Archived {source_key} to {archive_key}")
                
        except Exception as e:
            logging.error(f"Error archiving file for job {job['job_id']}: {e}")
    
    result = {'archived_files': archived_files}
    logging.info(f"Archival complete: {archived_files} files archived")
    
    return result

# Task definitions

# Data discovery and validation
discover_task = PythonOperator(
    task_id='discover_new_fits_files',
    python_callable=discover_new_fits_files,
    dag=dag,
)

validate_task = PythonOperator(
    task_id='validate_fits_files', 
    python_callable=validate_fits_files,
    dag=dag,
)

# Processing job management
with TaskGroup('image_processing', dag=dag) as processing_group:
    
    submit_jobs_task = PythonOperator(
        task_id='submit_processing_jobs',
        python_callable=submit_processing_jobs,
    )
    
    monitor_jobs_task = PythonOperator(
        task_id='monitor_processing_jobs',
        python_callable=monitor_processing_jobs,
    )
    
    submit_jobs_task >> monitor_jobs_task

# Catalog and archival
update_catalog_task = PythonOperator(
    task_id='update_catalog',
    python_callable=update_catalog,
    dag=dag,
)

archive_task = PythonOperator(
    task_id='archive_processed_data',
    python_callable=archive_processed_data,
    dag=dag,
)

# Quality checks and notifications
quality_check_task = PostgresOperator(
    task_id='quality_check',
    postgres_conn_id='astro_catalog_db',
    sql="""
    SELECT 
        COUNT(*) as total_objects,
        COUNT(CASE WHEN object_type = 'STAR' THEN 1 END) as stars,
        COUNT(CASE WHEN object_type = 'GALAXY' THEN 1 END) as galaxies,
        AVG(magnitude) as avg_magnitude
    FROM astronomical_objects 
    WHERE created_at >= NOW() - INTERVAL '1 hour';
    """,
    dag=dag,
)

cleanup_task = BashOperator(
    task_id='cleanup_temp_files',
    bash_command="""
    # Clean up temporary processing files older than 1 day
    find /tmp -name "*.fits" -mtime +1 -delete || true
    find /tmp -name "astro_*" -mtime +1 -delete || true
    echo "Cleanup completed"
    """,
    dag=dag,
)

# Notification task (only runs on failure)
failure_notification = EmailOperator(
    task_id='send_failure_notification',
    to=['astro-pipeline-alerts@example.com'],
    subject='Airflow Alert: Telescope Data Processing Failed',
    html_content="""
    <h3>Telescope Data Processing Pipeline Failed</h3>
    <p>The telescope data processing pipeline has encountered a failure.</p>
    <p><strong>DAG:</strong> {{ dag.dag_id }}</p>
    <p><strong>Execution Date:</strong> {{ ds }}</p>
    <p><strong>Failed Task:</strong> {{ task_instance.task_id }}</p>
    <p>Please check the Airflow logs for more details.</p>
    """,
    dag=dag,
    trigger_rule='one_failed',
)

# Define task dependencies
discover_task >> validate_task >> processing_group
processing_group >> [update_catalog_task, archive_task, quality_check_task]
[update_catalog_task, archive_task, quality_check_task] >> cleanup_task

# Add failure notification to all main tasks
for task in [discover_task, validate_task, processing_group, update_catalog_task, 
             archive_task, quality_check_task]:
    task >> failure_notification