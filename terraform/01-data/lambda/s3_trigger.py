import json
import urllib3
import boto3
from datetime import datetime

def handler(event, context):
    """
    Lambda function to trigger Airflow DAGs when new FITS files are uploaded to S3
    """
    
    # Parse S3 event
    for record in event['Records']:
        bucket_name = record['s3']['bucket']['name']
        object_key = record['s3']['object']['key']
        
        print(f"Processing S3 event: bucket={bucket_name}, key={object_key}")
        
        # Only process FITS files
        if not object_key.endswith('.fits'):
            print(f"Skipping non-FITS file: {object_key}")
            continue
            
        # Trigger Airflow DAG
        trigger_airflow_dag(bucket_name, object_key)
        
    return {
        'statusCode': 200,
        'body': json.dumps('Successfully processed S3 event')
    }

def trigger_airflow_dag(bucket_name, object_key):
    """
    Trigger Airflow DAG via REST API
    """
    
    # Airflow configuration
    airflow_endpoint = "${airflow_endpoint}"
    dag_id = "telescope_data_processing"
    
    # Create DAG run configuration
    dag_run_id = f"s3_trigger_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    
    conf = {
        "bucket_name": bucket_name,
        "object_key": object_key,
        "trigger_source": "s3_event"
    }
    
    # Prepare API request
    url = f"{airflow_endpoint}/api/v1/dags/{dag_id}/dagRuns"
    
    payload = {
        "dag_run_id": dag_run_id,
        "conf": conf
    }
    
    headers = {
        'Content-Type': 'application/json',
        # Note: In production, use proper authentication
        # 'Authorization': 'Bearer <token>'
    }
    
    try:
        http = urllib3.PoolManager()
        
        response = http.request(
            'POST',
            url,
            body=json.dumps(payload).encode('utf-8'),
            headers=headers
        )
        
        if response.status == 200:
            print(f"Successfully triggered DAG {dag_id} for {object_key}")
        else:
            print(f"Failed to trigger DAG. Status: {response.status}, Response: {response.data}")
            
    except Exception as e:
        print(f"Error triggering Airflow DAG: {str(e)}")
        
    return True