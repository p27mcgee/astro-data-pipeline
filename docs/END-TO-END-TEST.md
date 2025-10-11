# End-to-End System Test Guide

This guide demonstrates a complete production-like workflow from FITS file generation through processing to catalog
population.

## Architecture Flow

```
FITS Generation → S3 Upload → Airflow DAG Trigger → Image Processing →
Catalog Population → Query Results
```

## Prerequisites

- Docker Desktop running
- Python 3.11+ with astropy installed
- AWS CLI for LocalStack S3 access
- `jq` for JSON parsing (optional but recommended)
- Gradle 8.x (for building Java services)
- Make (for building Airflow image)

## Authentication

**All API endpoints require Basic Authentication:**
- Username: `admin`
- Password: `admin`

These credentials are configured in `application.yml` for both services:
- Image Processor (port 8082)
- Catalog Service (port 8081)

## Complete End-to-End Test

### Step 1: Build Custom Airflow Docker Image

**IMPORTANT:** Build the custom Airflow image before starting docker-compose:

```bash
# From project root
cd application/airflow

# Build custom Airflow image with required providers (includes Python validation)
make build

# This creates: astro-airflow:0.6.5-alpha.0
# Includes: AWS, Kubernetes, PostgreSQL, HTTP providers + astropy
# Validates Python code with flake8 and black before building

# Return to project root
cd ../..
```

### Step 2: Build Spring Boot Service Images

Build the application services:

```bash
# Still in application directory
./gradlew clean build

# Return to project root
cd ..
```

### Step 2: Start Complete System

Start all services with docker-compose:

```bash
# From project root
docker-compose up -d

# This starts:
# - PostgreSQL (main database)
# - Redis (caching)
# - LocalStack (S3, Lambda, CloudWatch, Secrets Manager)
# - Airflow (webserver, scheduler, init)
# - Prometheus (metrics)
# - Grafana (dashboards)
# - Image Processor Service (port 8082)
# - Catalog Service (port 8081)
```

**Wait for all services to be healthy (takes ~60-90 seconds):**

```bash
# Watch services starting up
docker-compose ps

# Wait until all show "healthy" or "running"
watch -n 2 'docker-compose ps'
```

### Step 3: Verify Services are Ready

Check all service health endpoints:

```bash
# Infrastructure
curl http://localhost:4566/_localstack/health  # LocalStack
curl http://localhost:5432  # PostgreSQL (will fail but proves port is open)
docker exec -it astro-postgres pg_isready -U astro_user  # Better check

# Application Services
curl http://localhost:8082/actuator/health  # Image Processor
curl http://localhost:8081/actuator/health  # Catalog Service

# Web Interfaces
curl -I http://localhost:8080  # Airflow UI
curl -I http://localhost:3000  # Grafana
curl -I http://localhost:9090  # Prometheus
```

**Expected results:** All should return HTTP 200 or healthy status.

### Step 4: Generate Test FITS File

Create a realistic astronomical FITS file:

```bash
cd application/data-simulator

# Install dependencies (if not already done)
pip install -r requirements.txt

# Generate a single test FITS file
python fits_generator.py \
  --output-dir ../../test-data \
  --count 1 \
  --telescope HST \
  --instrument ACS \
  --skip-upload

cd ../..
```

**Output:** Creates `test-data/hst_acs_f814w_YYYYMMDD_HHMMSS.fits`

### Step 5: Configure AWS CLI for LocalStack

Set up AWS credentials for LocalStack:

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Install awscli-local if not already installed
pip install awscli-local
```

### Step 6: Verify S3 Buckets (Auto-Created)

S3 buckets are automatically created by the LocalStack init script on startup. Verify they exist:

```bash
# Verify buckets were auto-created
aws --endpoint-url=http://localhost:4566 s3 ls

# Check LocalStack initialization logs
docker compose logs localstack | grep "Creating S3 buckets"
```

**Expected output:**

```
2025-10-10 astro-archive
2025-10-10 astro-intermediate-data
2025-10-10 astro-processed-data
2025-10-10 astro-raw-data
```

**Note:** Buckets are created automatically via `scripts/localstack/init-aws.sh` which is mounted to `/etc/localstack/init/ready.d/` in the LocalStack container.

### Step 7: Upload FITS File to S3

Upload the test FITS file to trigger processing:

```bash
# Get the FITS filename
FITS_FILE=$(ls test-data/*.fits | head -1)
echo "Uploading: $FITS_FILE"

# Upload to S3 raw bucket
awslocal s3 cp "$FITS_FILE" s3://astro-raw-data/raw/

# Verify upload
awslocal s3 ls s3://astro-raw-data/raw/
```

### Step 8: Trigger Processing via Airflow (Recommended)

**Option A: Using Airflow DAG (Production Workflow)**

```bash
# List available DAGs
docker compose exec airflow-scheduler airflow dags list

# Unpause the research processing workflow
docker compose exec airflow-scheduler airflow dags unpause research_processing_workflow

# Trigger DAG with S3 file location
docker compose exec airflow-scheduler airflow dags trigger research_processing_workflow \
  --conf '{"input_s3_bucket": "astro-raw-data", "input_s3_key": "fits/test-observation.fits", "session_id": "e2e-test-001"}'

# Monitor DAG execution in Airflow UI
open http://localhost:8080  # Login: admin/admin
```

**Option B: Direct API Call (Alternative - requires authentication)**

```bash
# Extract just the filename
FITS_FILENAME=$(basename "$FITS_FILE")

# Submit processing job with authentication
curl -u admin:admin -X POST http://localhost:8082/api/v1/processing/jobs/s3 \
  -H "Content-Type: application/json" \
  -d "{
    \"inputBucket\": \"astro-raw-data\",
    \"inputObjectKey\": \"raw/$FITS_FILENAME\",
    \"outputBucket\": \"astro-processed-data\",
    \"outputObjectKey\": \"processed/calibrated_$FITS_FILENAME\",
    \"processingType\": \"FULL_CALIBRATION\",
    \"priority\": 1
  }" | jq

# Save the job ID from the response
```

**Note:** Direct API currently returns 405 Method Not Allowed for S3 endpoints. Use Airflow DAG workflow instead.

**Expected response:**

```json
{
  "jobId": "job_abc123def456",
  "status": "QUEUED",
  "inputBucket": "astro-raw-data",
  "inputObjectKey": "raw/hst_acs_f814w_20241005_143022.fits",
  "processingType": "FULL_CALIBRATION",
  "priority": 1,
  "createdAt": "2024-10-05T14:30:22Z"
}
```

### Step 9: Monitor Processing Status

Check job progress (with authentication):

```bash
# List all jobs with authentication
curl -u admin:admin -s http://localhost:8082/api/v1/processing/jobs | jq '.content[] | {jobId, status, processingType}'

# Get job ID from response
JOB_ID=$(curl -u admin:admin -s http://localhost:8082/api/v1/processing/jobs | jq -r '.content[0].jobId')

# Monitor job status (repeat until COMPLETED)
watch -n 5 "curl -u admin:admin -s http://localhost:8082/api/v1/processing/jobs/$JOB_ID | jq '.status'"

# Get full job details with authentication
curl -u admin:admin http://localhost:8082/api/v1/processing/jobs/$JOB_ID | jq
```

**Expected statuses:**

- `QUEUED` → `PROCESSING` → `COMPLETED`

### Step 10: Verify Processed Output

Check that the processed FITS file was created:

```bash
# List processed files
awslocal s3 ls s3://astro-processed-data/processed/

# Download processed file
awslocal s3 cp s3://astro-processed-data/processed/calibrated_$FITS_FILENAME ./processed_result.fits

# Verify FITS file integrity
python -c "
from astropy.io import fits
hdul = fits.open('processed_result.fits')
hdul.info()
print('\nCalibrated FITS file is valid!')
hdul.close()
"
```

### Step 11: Query Catalog Service

Check that astronomical objects were detected and cataloged:

```bash
# Get catalog statistics
curl http://localhost:8081/api/v1/catalog/statistics | jq

# Perform cone search (adjust coordinates based on your FITS file)
curl -X POST http://localhost:8081/api/v1/catalog/cone-search \
  -H "Content-Type: application/json" \
  -d '{
    "centerRa": 150.0,
    "centerDec": 2.0,
    "radiusArcsec": 300.0
  }' | jq

# Find high proper motion objects
curl "http://localhost:8081/api/v1/catalog/high-proper-motion?minProperMotion=50.0" | jq
```

**Expected output:** List of detected astronomical objects with coordinates, magnitudes, etc.

### Step 12: Verify Database Population

Connect to PostgreSQL and verify data:

```bash
# Connect to database
docker exec -it astro-postgres psql -U astro_user -d astro_catalog

# Run queries
SELECT COUNT(*) FROM processing_jobs;
SELECT job_id, status, processing_type, created_at FROM processing_jobs ORDER BY created_at DESC LIMIT 5;

SELECT COUNT(*) FROM astronomical_objects;
SELECT object_id, object_type, ra, dec, magnitude FROM astronomical_objects ORDER BY magnitude ASC LIMIT 10;

# Exit psql
\q
```

### Step 13: Check Airflow DAG Execution (Optional)

If you configured Airflow DAGs:

```bash
# Open Airflow UI
open http://localhost:8080

# Login: admin / admin

# Check DAG runs
curl "http://localhost:8080/api/v1/dags/telescope_data_processing/dagRuns" \
  -H "Content-Type: application/json" \
  --user "admin:admin" | jq
```

### Step 14: View Metrics and Dashboards

Access monitoring dashboards:

```bash
# Prometheus metrics
open http://localhost:9090

# Grafana dashboards
open http://localhost:3000
# Login: admin / admin

# Swagger API docs
open http://localhost:8082/swagger-ui.html  # Image Processor
open http://localhost:8081/swagger-ui.html  # Catalog Service
```

## Complete Automated Test Script

Save this as `test-end-to-end.sh`:

```bash
#!/bin/bash
set -e

echo "🚀 Starting End-to-End System Test"

# Build Airflow
echo "📦 Building Airflow..."
cd application/airflow && make build && cd ../..

# Build Java services
echo "📦 Building Java services..."
cd application && ./gradlew clean build && cd ..

# Start system
echo "🐳 Starting Docker Compose..."
docker-compose up -d

# Wait for services
echo "⏳ Waiting for services to be healthy (90s)..."
sleep 90

# Configure AWS
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Create buckets
echo "🪣 Creating S3 buckets..."
awslocal s3 mb s3://astro-raw-data 2>/dev/null || true
awslocal s3 mb s3://astro-processed-data 2>/dev/null || true
awslocal s3 mb s3://astro-intermediate-data 2>/dev/null || true
awslocal s3 mb s3://astro-archive 2>/dev/null || true

# Generate FITS
echo "📸 Generating test FITS file..."
cd application/data-simulator
python fits_generator.py --output-dir ../../test-data --count 1 --skip-upload
cd ../..

# Upload FITS
FITS_FILE=$(ls test-data/*.fits | head -1)
FITS_FILENAME=$(basename "$FITS_FILE")
echo "☁️  Uploading $FITS_FILENAME to S3..."
awslocal s3 cp "$FITS_FILE" s3://astro-raw-data/raw/

# Submit job
echo "🔬 Submitting processing job..."
JOB_RESPONSE=$(curl -s -X POST http://localhost:8082/api/v1/processing/jobs/s3 \
  -H "Content-Type: application/json" \
  -d "{
    \"inputBucket\": \"astro-raw-data\",
    \"inputObjectKey\": \"raw/$FITS_FILENAME\",
    \"outputBucket\": \"astro-processed-data\",
    \"outputObjectKey\": \"processed/calibrated_$FITS_FILENAME\",
    \"processingType\": \"FULL_CALIBRATION\",
    \"priority\": 1
  }")

JOB_ID=$(echo "$JOB_RESPONSE" | jq -r '.jobId')
echo "📋 Job ID: $JOB_ID"

# Monitor job
echo "⏳ Monitoring job status..."
for i in {1..30}; do
  STATUS=$(curl -s http://localhost:8082/api/v1/processing/jobs/$JOB_ID | jq -r '.status')
  echo "  Status: $STATUS"

  if [ "$STATUS" = "COMPLETED" ]; then
    echo "✅ Processing completed successfully!"
    break
  elif [ "$STATUS" = "FAILED" ]; then
    echo "❌ Processing failed!"
    exit 1
  fi

  sleep 10
done

# Verify results
echo "🔍 Verifying results..."
awslocal s3 ls s3://astro-processed-data/processed/ || echo "No processed files found"

# Query catalog
echo "📊 Querying catalog..."
curl -s http://localhost:8081/api/v1/catalog/statistics | jq

echo "✨ End-to-end test complete!"
echo "📍 Services available at:"
echo "  - Image Processor: http://localhost:8082"
echo "  - Catalog Service: http://localhost:8081"
echo "  - Airflow: http://localhost:8080 (admin/admin)"
echo "  - Grafana: http://localhost:3000 (admin/admin)"
echo "  - Prometheus: http://localhost:9090"
```

Make it executable:

```bash
chmod +x test-end-to-end.sh
./test-end-to-end.sh
```

## Cleanup

Stop and remove all services:

```bash
# Stop services
docker-compose down

# Stop and remove volumes (deletes all data)
docker-compose down -v

# Remove generated test data
rm -rf test-data/*.fits
rm -f processed_result.fits
```

## Troubleshooting

### Services Not Starting

```bash
# View logs
docker-compose logs image-processor
docker-compose logs catalog-service
docker-compose logs localstack

# Restart a specific service
docker-compose restart image-processor
```

### Processing Job Stuck

```bash
# Check service logs
docker-compose logs -f image-processor

# Verify S3 connectivity from container
docker exec -it astro-image-processor curl http://localstack:4566/_localstack/health
```

### Catalog Not Populating

```bash
# Check catalog service logs
docker-compose logs -f catalog-service

# Verify database connection
docker exec -it astro-catalog-service curl http://localhost:8081/actuator/health
```

### Airflow DAG Import Errors

Some production DAGs have import errors due to provider compatibility issues with Airflow 2.7.1:

```bash
# Check for DAG import errors
docker compose exec airflow-scheduler airflow dags list-import-errors

# View specific errors
docker compose logs airflow-scheduler | grep -i error

# Known issues:
# - telescope_data_processing.py: S3DeleteObjectOperator import error
# - batch_processing_dag.py: Kubernetes provider missing
# - data_quality_monitoring.py: Missing start_date parameter
# - research_workflow_templates.py: Plugin import error

# Working DAG:
# - research_processing_workflow: ✅ Fully functional
```

**Workaround:** Use the `research_processing_workflow` DAG which is fully operational.

### Authentication Issues

If you get 401 Unauthorized errors:

```bash
# All API calls require Basic Auth
curl -u admin:admin http://localhost:8082/api/v1/processing/jobs

# Verify credentials in application.yml:
# spring.security.user.name=admin
# spring.security.user.password=admin
```

## Success Criteria

✅ All services start healthy
✅ FITS file uploads to S3
✅ Processing job completes successfully
✅ Processed FITS file appears in processed bucket
✅ Astronomical objects appear in catalog
✅ Database contains processing jobs and objects
✅ All health endpoints return 200 OK

## Next Steps

- Configure Airflow DAGs for automated processing
- Set up Grafana dashboards for monitoring
- Add Lambda triggers for S3 event-driven processing
- Implement custom research workflows with granular processing