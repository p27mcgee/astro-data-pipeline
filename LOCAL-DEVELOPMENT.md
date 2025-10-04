# Local Development Guide

This guide explains how to run the Astronomical Data Pipeline locally using Docker Compose and test it with FITS files.

## Prerequisites

- **Docker Desktop** (or Docker Engine + Docker Compose)
- **Java 17** (for running Spring Boot applications)
- **Python 3.11+** (for FITS file generation)
- **Gradle** (included via wrapper)
- **Git**

## Quick Start

### 1. Start Infrastructure Services

Start PostgreSQL, Redis, LocalStack, and Airflow:

```bash
# From project root
docker-compose up -d
```

This starts:

- **PostgreSQL 15**: Database on `localhost:5432`
- **Redis**: Cache on `localhost:6379`
- **LocalStack**: AWS services emulator (S3, Lambda, Secrets Manager) on `localhost:4566`
- **Apache Airflow**: Workflow orchestration on `localhost:8080`
- **Prometheus**: Metrics on `localhost:9090`
- **Grafana**: Dashboards on `localhost:3000`

Verify services are running:

```bash
docker-compose ps

# Check LocalStack health
curl http://localhost:4566/_localstack/health
```

### 2. Build Applications

```bash
cd application
./gradlew clean build
```

### 3. Run Services

**Option A: Run from JAR files**

```bash
# Terminal 1: Image Processor Service
java -jar image-processor/build/libs/image-processor-*.jar

# Terminal 2: Catalog Service
java -jar catalog-service/build/libs/catalog-service-*.jar
```

**Option B: Run with Gradle (hot reload)**

```bash
# Terminal 1: Image Processor Service
cd image-processor
./gradlew bootRun

# Terminal 2: Catalog Service
cd catalog-service
./gradlew bootRun
```

### 4. Verify Services

Check health endpoints:

```bash
# Image Processor
curl http://localhost:8080/actuator/health

# Catalog Service
curl http://localhost:8081/actuator/health
```

## Generating Test FITS Files

The project includes a realistic FITS file generator that creates authentic astronomical data.

### Install Python Dependencies

```bash
cd application/data-simulator
pip install -r requirements.txt
```

### Generate a Single FITS File

```bash
python fits_generator.py \
  --output-dir ./test-data \
  --count 1 \
  --telescope HST \
  --instrument ACS
```

**Parameters:**

- `--output-dir`: Directory to save FITS files (default: `./generated_fits`)
- `--count`: Number of files to generate (default: 10)
- `--telescope`: Telescope type (`HST`, `JWST`, `VLT`) (default: `HST`)
- `--instrument`: Instrument name (depends on telescope)
- `--skip-upload`: Don't upload to S3 (for local testing)

### Generate Multiple Files

```bash
# Generate 5 files with different objects
python fits_generator.py \
  --output-dir ./test-data \
  --count 5 \
  --skip-upload
```

### Example FITS Files Generated

The generator creates realistic FITS files with:

- **Primary HDU**: Image data (2048x2048 or 4096x4096 pixels)
- **WCS Headers**: World Coordinate System for celestial coordinates
- **Observation Metadata**: Telescope, instrument, filters, exposure time
- **Astronomical Objects**: Stars, galaxies, cosmic rays, noise
- **Calibration Data**: Dark frames, flat fields, bias frames

**File naming:** `telescope_instrument_filter_YYYYMMDD_HHMMSS.fits`

Example: `hst_acs_f814w_20241005_143022.fits`

## Submitting Test FITS Files for Processing

### Option 1: Upload to LocalStack S3

Upload FITS file to local S3:

```bash
# Install AWS CLI (if not already installed)
pip install awscli-local

# Configure for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Create bucket
awslocal s3 mb s3://astro-raw-data

# Upload FITS file
awslocal s3 cp test-data/hst_acs_f814w_20241005_143022.fits \
  s3://astro-raw-data/raw/
```

### Option 2: Submit via REST API (Direct File Upload)

```bash
# Submit processing job with S3 path
curl -X POST http://localhost:8080/api/v1/processing/jobs/s3 \
  -H "Content-Type: application/json" \
  -d '{
    "inputBucket": "astro-raw-data",
    "inputObjectKey": "raw/hst_acs_f814w_20241005_143022.fits",
    "outputBucket": "astro-processed-data",
    "outputObjectKey": "processed/hst_acs_f814w_20241005_143022_calibrated.fits",
    "processingType": "FULL_CALIBRATION",
    "priority": 1
  }'
```

**Response:**

```json
{
  "jobId": "job_abc123def456",
  "status": "QUEUED",
  "inputBucket": "astro-raw-data",
  "inputObjectKey": "raw/hst_acs_f814w_20241005_143022.fits",
  "processingType": "FULL_CALIBRATION",
  "createdAt": "2024-10-05T14:30:22Z"
}
```

### Option 3: Granular Processing (Step-by-Step)

Process FITS file one step at a time for research:

```bash
# Step 1: Bias Subtraction
curl -X POST http://localhost:8080/api/v1/processing/steps/bias-subtract \
  -H "Content-Type: application/json" \
  -d '{
    "imagePath": "s3://astro-raw-data/raw/test_image.fits",
    "sessionId": "research-session-001",
    "algorithm": "default",
    "parameters": {
      "overscanCorrection": "true"
    }
  }'

# Step 2: Dark Subtraction
curl -X POST http://localhost:8080/api/v1/processing/steps/dark-subtract \
  -H "Content-Type: application/json" \
  -d '{
    "imagePath": "s3://intermediate/research-session-001/bias-subtraction/result.fits",
    "sessionId": "research-session-001",
    "algorithm": "scaled-dark",
    "parameters": {
      "scaleFactor": "1.2",
      "temperatureCorrection": "true"
    }
  }'

# Step 3: Flat Field Correction
curl -X POST http://localhost:8080/api/v1/processing/steps/flat-correct \
  -H "Content-Type: application/json" \
  -d '{
    "imagePath": "s3://intermediate/research-session-001/dark-subtraction/result.fits",
    "sessionId": "research-session-001",
    "algorithm": "illumination-corrected"
  }'

# Step 4: Cosmic Ray Removal
curl -X POST http://localhost:8080/api/v1/processing/steps/cosmic-ray-remove \
  -H "Content-Type: application/json" \
  -d '{
    "imagePath": "s3://intermediate/research-session-001/flat-correction/result.fits",
    "sessionId": "research-session-001",
    "algorithm": "lacosmic-v2",
    "parameters": {
      "sigclip": "4.5",
      "starPreservation": "true"
    }
  }'
```

## Monitoring Processing Jobs

### Check Job Status

```bash
curl http://localhost:8080/api/v1/processing/jobs/{jobId}
```

### List All Jobs

```bash
curl http://localhost:8080/api/v1/processing/jobs
```

### Filter by Status

```bash
curl "http://localhost:8080/api/v1/processing/jobs?status=COMPLETED"
```

### Get Processing Metrics

```bash
curl http://localhost:8080/api/v1/processing/metrics
```

## Querying the Catalog Service

After processing, astronomical objects are stored in the catalog.

### Cone Search (Find Objects Near Coordinates)

```bash
curl -X POST http://localhost:8081/api/v1/catalog/cone-search \
  -H "Content-Type: application/json" \
  -d '{
    "centerRa": 150.5,
    "centerDec": 2.3,
    "radiusArcsec": 60.0
  }'
```

### Cross-Match Coordinates

```bash
curl -X POST http://localhost:8081/api/v1/catalog/cross-match \
  -H "Content-Type: application/json" \
  -d '[
    {"centerRa": 150.5, "centerDec": 2.3, "radiusArcsec": 5.0},
    {"centerRa": 151.2, "centerDec": 2.8, "radiusArcsec": 5.0}
  ]'
```

### Get Catalog Statistics

```bash
curl http://localhost:8081/api/v1/catalog/statistics
```

### Find High Proper Motion Objects

```bash
curl "http://localhost:8081/api/v1/catalog/high-proper-motion?minProperMotion=100.0"
```

## Accessing Web Interfaces

### Swagger UI (API Documentation)

- **Image Processor**: http://localhost:8080/swagger-ui.html
- **Catalog Service**: http://localhost:8081/swagger-ui.html

### Actuator Endpoints

- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Info**: http://localhost:8080/actuator/info

### Database Access

Connect to PostgreSQL:

```bash
docker exec -it astro-postgres psql -U astro_user -d astro_catalog
```

Useful queries:

```sql
-- View processing jobs
SELECT job_id, status, processing_type, created_at
FROM processing_jobs
ORDER BY created_at DESC
LIMIT 10;

-- View astronomical objects
SELECT object_id, object_type, ra, dec, magnitude
FROM astronomical_objects
ORDER BY magnitude ASC
LIMIT 10;

-- Count objects by type
SELECT object_type, COUNT(*)
FROM astronomical_objects
GROUP BY object_type;
```

### LocalStack S3 Browser

List buckets and objects:

```bash
awslocal s3 ls
awslocal s3 ls s3://astro-raw-data/
awslocal s3 ls s3://astro-processed-data/
```

Download processed file:

```bash
awslocal s3 cp s3://astro-processed-data/processed/result.fits ./downloaded.fits
```

## Complete Example Workflow

Here's a complete end-to-end example:

```bash
# 1. Start infrastructure
cd application
docker-compose up -d

# 2. Generate test FITS file
cd data-simulator
python fits_generator.py --output-dir ../test-data --count 1 --skip-upload
cd ..

# 3. Upload to LocalStack S3
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

awslocal s3 mb s3://astro-raw-data
awslocal s3 cp test-data/*.fits s3://astro-raw-data/raw/

# 4. Start services (in separate terminals)
cd image-processor && ./gradlew bootRun
cd catalog-service && ./gradlew bootRun

# 5. Submit processing job
FITS_FILE=$(ls test-data/*.fits | head -1 | xargs basename)
curl -X POST http://localhost:8080/api/v1/processing/jobs/s3 \
  -H "Content-Type: application/json" \
  -d "{
    \"inputBucket\": \"astro-raw-data\",
    \"inputObjectKey\": \"raw/$FITS_FILE\",
    \"outputBucket\": \"astro-processed-data\",
    \"outputObjectKey\": \"processed/calibrated_$FITS_FILE\",
    \"processingType\": \"FULL_CALIBRATION\",
    \"priority\": 1
  }" | jq

# 6. Check job status
JOB_ID=$(curl -s http://localhost:8080/api/v1/processing/jobs | jq -r '.[0].jobId')
curl http://localhost:8080/api/v1/processing/jobs/$JOB_ID | jq

# 7. Query catalog for detected objects
curl -X POST http://localhost:8081/api/v1/catalog/cone-search \
  -H "Content-Type: application/json" \
  -d '{
    "centerRa": 150.0,
    "centerDec": 2.0,
    "radiusArcsec": 300.0
  }' | jq

# 8. Download processed file
awslocal s3 cp s3://astro-processed-data/processed/calibrated_$FITS_FILE ./result.fits
```

## Docker Compose Configuration

The `docker-compose.yml` in the project root includes:

```yaml
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: astro_catalog
      POSTGRES_USER: astro_user
      POSTGRES_PASSWORD: astro_password
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  localstack:
    image: localstack/localstack:latest
    environment:
      SERVICES: s3,lambda,secretsmanager,cloudwatch
      LAMBDA_EXECUTOR: docker-reuse
    ports:
      - "4566:4566"
    volumes:
      - ./terraform/lambda:/opt/lambda

  airflow-webserver:
    image: apache/airflow:2.7.1
    ports:
      - "8080:8080"
    # ... (simplified for brevity)

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
```

**Note**: The actual docker-compose.yml contains the full Airflow setup with scheduler, webserver, and dedicated
PostgreSQL instance.

## Environment Variables

For local development, you can override defaults:

```bash
# Database
export DATABASE_URL=jdbc:postgresql://localhost:5432/astro_catalog
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres

# AWS LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
export S3_ENDPOINT=http://localhost:4566

# Service Ports
export SERVER_PORT=8080  # Image Processor
# export SERVER_PORT=8081  # Catalog Service

# Enable debug logging
export LOG_LEVEL_APP=DEBUG
export SPRING_PROFILES_ACTIVE=dev
```

## Troubleshooting

### Port Conflicts

**Before starting docker-compose**, ensure no services are already using the required ports:

**Port 5432 (PostgreSQL):**

```bash
# Check what's using port 5432
lsof -i :5432

# If it's a running PostgreSQL instance:
# Docker container
docker ps | grep postgres
docker stop <container-id>

# Local PostgreSQL service (macOS)
brew services stop postgresql@15

# Local PostgreSQL service (Linux)
sudo systemctl stop postgresql

# Verify port is free
lsof -i :5432  # Should return nothing
```

**Port 8080 (Airflow Webserver vs Image Processor):**

The docker-compose includes Airflow on port 8080, which conflicts with the Image Processor service. Choose one approach:

**Option 1: Use different port for Image Processor (Recommended)**

```bash
# Run Image Processor on port 8082
export SERVER_PORT=8082
cd application/image-processor
./gradlew bootRun

# Airflow remains on :8080
# Image Processor now on :8082
curl http://localhost:8082/actuator/health
```

**Option 2: Stop Airflow if not needed**

```bash
# Stop Airflow webserver
docker-compose stop airflow-webserver airflow-scheduler

# Run Image Processor on default :8080
cd application/image-processor
./gradlew bootRun
```

**Option 3: Run only essential services**

```bash
# Start only Postgres, Redis, and LocalStack
docker-compose up -d postgres redis localstack

# This leaves :8080 free for Image Processor
./gradlew bootRun
```

**Port 8081 (Catalog Service):**

```bash
# Usually free, but check if needed
lsof -i :8081

# Run catalog service
cd application/catalog-service
./gradlew bootRun
```

**Quick check all ports:**

```bash
# Check if ports are available
for port in 5432 6379 4566 8080 8081 3000 9090; do
  echo -n "Port $port: "
  lsof -i :$port > /dev/null 2>&1 && echo "IN USE" || echo "FREE"
done
```

### Services Won't Start

**PostgreSQL connection refused:**

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# View logs
docker-compose logs postgres

# Restart
docker-compose restart postgres
```

**LocalStack S3 not accessible:**

```bash
# Check LocalStack status
docker-compose logs localstack

# Test connection
curl http://localhost:4566/_localstack/health
```

### Application Errors

**Database schema issues:**

```sql
-- Drop and recreate schema (development only!)
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
```

**Clear Redis cache:**

```bash
docker exec -it astro-redis redis-cli FLUSHALL
```

### FITS File Issues

**Invalid FITS file:**

```bash
# Verify FITS file integrity with Python
python -c "
from astropy.io import fits
hdul = fits.open('test-data/test.fits')
hdul.info()
hdul.close()
"
```

**File too large:**

- Adjust `--image-size` parameter in fits_generator.py
- Default is 2048x2048, try 1024x1024 for faster processing

## Performance Tips

### Speed Up Local Development

1. **Use H2 for faster iteration:**
   ```bash
   export SPRING_PROFILES_ACTIVE=test
   ./gradlew bootRun
   ```

2. **Reduce image size:**
   ```python
   python fits_generator.py --image-size 512 --count 1
   ```

3. **Disable metrics collection:**
   ```bash
   export MONITORING_ENABLE_METRICS=false
   ```

4. **Use parallel processing:**
   ```bash
   export PROCESSING_MAX_CONCURRENT_JOBS=8
   ```

### Memory Configuration

For large FITS files:

```bash
export PROCESSING_MAX_HEAP_SIZE=4294967296  # 4GB
export PROCESSING_MAX_IMAGE_SIZE=1073741824  # 1GB
```

## Stopping Services

### Stop Docker Services

```bash
docker-compose down
```

### Stop and Remove Data

```bash
docker-compose down -v
```

### Stop Applications

Press `Ctrl+C` in terminals running Gradle/Java processes

## Next Steps

- Read [DATABASE-MIGRATION-STRATEGY.md](DATABASE-MIGRATION-STRATEGY.md) for production deployment
- Check [API-DOCUMENTATION.md](API-DOCUMENTATION.md) for complete API reference
- See [DEPLOYMENT.md](docs/DEPLOYMENT.md) for cloud deployment guide
- Review [ARCHITECTURE.md](docs/ARCHITECTURE.md) for system design details

## Additional Resources

- **FITS Format**: https://fits.gsfc.nasa.gov/fits_standard.html
- **PostGIS Documentation**: https://postgis.net/documentation/
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html
- **LocalStack Documentation**: https://docs.localstack.cloud/