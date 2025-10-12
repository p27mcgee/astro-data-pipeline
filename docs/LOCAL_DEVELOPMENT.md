# Local Development Guide

This guide explains how to run the Astronomical Data Processing Pipeline locally using docker-compose, with MinIO
replacing AWS S3 for object storage.

## Overview

The local development environment provides a complete, self-contained deployment that mirrors the production
architecture without requiring AWS infrastructure:

- **Storage**: MinIO (S3-compatible) instead of AWS S3
- **Database**: PostgreSQL 15 with PostGIS extensions
- **Caching**: Redis
- **Orchestration**: Apache Airflow with LocalExecutor
- **Monitoring**: Prometheus + Grafana
- **Services**: Image Processor + Catalog Service

## Storage Abstraction Architecture

The system uses a **storage provider abstraction** that allows seamless switching between storage backends:

```
┌─────────────────────────────────────┐
│      Application Services           │
│  (Image Processor, Catalog, etc.)   │
└────────────────┬────────────────────┘
                 │
                 ↓
         ┌──────────────┐
         │StorageProvider│ (Interface)
         └──────┬───────┘
                │
      ┌─────────┼─────────┐
      │         │         │
      ↓         ↓         ↓
┌──────────┐ ┌──────┐ ┌────────────┐
│S3Provider│ │MinIO │ │ Filesystem │
│ (AWS S3) │ │      │ │  Provider  │
└──────────┘ └──────┘ └────────────┘
```

**Storage Modes:**

1. **MinIO** (`astro.storage.type=minio`) - S3-compatible local object storage
2. **Filesystem** (`astro.storage.type=filesystem`) - Direct filesystem storage
3. **AWS S3** (`astro.storage.type=s3`) - Production AWS S3 (default)

## Prerequisites

- Docker Desktop (with Docker Compose)
- 8GB RAM minimum (16GB recommended)
- 20GB free disk space
- Ports available: 3000, 5432, 6379, 8080, 8081, 8082, 9000, 9001, 9090

## Quick Start

### 1. Start All Services

```bash
docker-compose up -d
```

This starts:

- PostgreSQL (port 5432)
- Redis (port 6379)
- MinIO (ports 9000/9001)
- Airflow (port 8080)
- Image Processor (port 8081)
- Catalog Service (port 8082)
- Prometheus (port 9090)
- Grafana (port 3000)

### 2. Verify Services

```bash
# Check all containers are running
docker-compose ps

# Check service health
curl http://localhost:8081/actuator/health  # Image Processor
curl http://localhost:8082/actuator/health  # Catalog Service

# Access web interfaces
open http://localhost:9001  # MinIO Console (astro_access_key / astro_secret_key)
open http://localhost:8080  # Airflow UI (admin / admin)
open http://localhost:3000  # Grafana (admin / admin)
```

### 3. Create MinIO Buckets

MinIO requires manual bucket creation on first run:

```bash
# Option 1: Use MinIO Console UI
# - Navigate to http://localhost:9001
# - Login: astro_access_key / astro_secret_key
# - Create buckets: raw-data, processed-data, intermediate-data, archive

# Option 2: Use MinIO CLI
docker exec astro-minio mc alias set local http://localhost:9000 astro_access_key astro_secret_key
docker exec astro-minio mc mb local/raw-data
docker exec astro-minio mc mb local/processed-data
docker exec astro-minio mc mb local/intermediate-data
docker exec astro-minio mc mb local/archive
```

### 4. Test Processing Pipeline

```bash
# Upload test FITS file to MinIO
# (Assuming you have a test file: test.fits)
aws --endpoint-url http://localhost:9000 \
    s3 cp test.fits s3://raw-data/test/test.fits \
    --profile local

# Submit processing job
curl -X POST http://localhost:8081/api/v1/processing/jobs/s3 \
  -H "Content-Type: application/json" \
  -d '{
    "inputBucket": "raw-data",
    "inputObjectKey": "test/test.fits"
  }'

# Check job status
curl http://localhost:8081/api/v1/processing/jobs/{jobId}
```

## Storage Configuration

### Using MinIO (Default for Local Profile)

**Configuration** (`application.yml` profile: `local`):

```yaml
astro:
  storage:
    type: minio
spring:
  cloud:
    aws:
      s3:
        endpoint: http://astro-minio:9000
      credentials:
        access-key: astro_access_key
        secret-key: astro_secret_key
```

**Docker Compose Environment:**

```yaml
environment:
  SPRING_PROFILES_ACTIVE: local
  S3_ENDPOINT: http://astro-minio:9000
  AWS_ACCESS_KEY_ID: astro_access_key
  AWS_SECRET_ACCESS_KEY: astro_secret_key
```

### Using Local Filesystem

To use filesystem storage instead of MinIO:

**Configuration** (`application.yml` profile: `local-filesystem`):

```yaml
astro:
  storage:
    type: filesystem
    filesystem:
      base-path: /app/data
```

**Docker Compose Update:**

```yaml
services:
  image-processor:
    environment:
      SPRING_PROFILES_ACTIVE: local-filesystem
    volumes:
      - ./local-data:/app/data  # Mount local directory
```

**Start with Filesystem Storage:**

```bash
# Create local data directory
mkdir -p local-data/{raw-data,processed-data,intermediate-data,archive}

# Update docker-compose to use local-filesystem profile
# ... then start services
docker-compose up -d image-processor catalog-service
```

## Service Endpoints

| Service         | Port | Endpoint                              | Credentials                         |
|-----------------|------|---------------------------------------|-------------------------------------|
| Image Processor | 8081 | http://localhost:8081/actuator/health | -                                   |
| Catalog Service | 8082 | http://localhost:8082/actuator/health | -                                   |
| Airflow UI      | 8080 | http://localhost:8080                 | admin / admin                       |
| MinIO Console   | 9001 | http://localhost:9001                 | astro_access_key / astro_secret_key |
| MinIO API       | 9000 | http://localhost:9000                 | (S3 API)                            |
| Grafana         | 3000 | http://localhost:3000                 | admin / admin                       |
| Prometheus      | 9090 | http://localhost:9090                 | -                                   |
| PostgreSQL      | 5432 | localhost:5432                        | astro_user / astro_password         |
| Redis           | 6379 | localhost:6379                        | -                                   |

## API Examples

### Image Processing

```bash
# Submit processing job from S3
curl -X POST http://localhost:8081/api/v1/processing/jobs/s3 \
  -H "Content-Type: application/json" \
  -d '{
    "inputBucket": "raw-data",
    "inputObjectKey": "fits/image_001.fits",
    "processingSteps": {
      "darkSubtraction": true,
      "flatCorrection": true,
      "cosmicRayRemoval": true
    }
  }'

# Upload FITS file directly
curl -X POST http://localhost:8081/api/v1/processing/jobs \
  -F "file=@/path/to/image.fits" \
  -F "darkSubtraction=true" \
  -F "flatCorrection=true"

# Check job status
curl http://localhost:8081/api/v1/processing/jobs/{jobId}

# List all jobs
curl http://localhost:8081/api/v1/processing/jobs
```

### Catalog Operations

```bash
# Ingest astronomical objects
curl -X POST http://localhost:8082/api/v1/catalog/objects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "NGC 1234",
    "ra": 123.456,
    "dec": 45.678,
    "magnitude": 12.5,
    "objectType": "GALAXY"
  }'

# Cone search
curl -X POST http://localhost:8082/api/v1/catalog/search/cone \
  -H "Content-Type: application/json" \
  -d '{
    "ra": 123.456,
    "dec": 45.678,
    "radiusDegrees": 0.5
  }'

# Get object by ID
curl http://localhost:8082/api/v1/catalog/objects/{objectId}
```

### Granular Processing (Phase 3)

```bash
# Bias subtraction only
curl -X POST http://localhost:8081/api/v1/processing/steps/bias-subtract \
  -H "Content-Type: application/json" \
  -d '{
    "inputPath": "raw-data/test.fits",
    "algorithm": "adaptive-bias",
    "parameters": {
      "overscanRegion": "[1:10,:]"
    },
    "outputBucket": "intermediate-data",
    "sessionId": "research-session-001"
  }'

# Custom workflow
curl -X POST http://localhost:8081/api/v1/processing/workflow \
  -H "Content-Type: application/json" \
  -d '{
    "inputPath": "raw-data/test.fits",
    "sessionId": "workflow-001",
    "steps": [
      {"type": "bias-subtract", "algorithm": "standard"},
      {"type": "dark-subtract", "algorithm": "scaled-dark"},
      {"type": "cosmic-ray-remove", "algorithm": "lacosmic-v2"}
    ]
  }'
```

## Airflow DAG Examples

### Trigger Telescope Data Processing

```bash
# Trigger real-time processing DAG
curl -X POST http://localhost:8080/api/v1/dags/telescope_data_processing/dagRuns \
  -H "Content-Type: application/json" \
  -u "admin:admin" \
  -d '{
    "conf": {
      "s3_bucket": "raw-data",
      "s3_key": "telescope/observation_001.fits"
    }
  }'
```

### Trigger Research Workflow

```bash
# Algorithm comparison
curl -X POST http://localhost:8080/api/v1/dags/research_processing_dag/dagRuns \
  -H "Content-Type: application/json" \
  -u "admin:admin" \
  -d '{
    "conf": {
      "mode": "algorithm_comparison",
      "input_image": "raw-data/test.fits",
      "processing_step": "cosmic-ray-remove",
      "algorithms": [
        {"name": "lacosmic", "params": {"sigclip": 4.5}},
        {"name": "lacosmic-v2", "params": {"starPreservation": true}}
      ]
    }
  }'
```

## Database Access

### PostgreSQL

```bash
# Connect to PostgreSQL
docker exec -it astro-postgres psql -U astro_user -d astro_catalog

# Run spatial query
SELECT name, ra, dec, magnitude
FROM astronomical_objects
WHERE ST_DWithin(
  position::geography,
  ST_MakePoint(123.456, 45.678)::geography,
  5000  -- 5km radius
);

# Check PostGIS version
SELECT PostGIS_Full_Version();
```

### Redis

```bash
# Connect to Redis
docker exec -it astro-redis redis-cli

# Check keys
KEYS *

# Get cached value
GET processing:job:{jobId}
```

## Monitoring & Observability

### Prometheus Metrics

```bash
# Query processing job metrics
curl 'http://localhost:9090/api/v1/query?query=processing_jobs_total'

# Check service health
curl 'http://localhost:9090/api/v1/query?query=up{job="image-processor"}'
```

### Grafana Dashboards

1. Access: http://localhost:3000 (admin/admin)
2. Add Prometheus data source:
    - URL: http://prometheus:9090
3. Import dashboards from `monitoring/grafana/provisioning/dashboards/`

### Application Logs

```bash
# Image Processor logs
docker logs -f astro-image-processor

# Catalog Service logs
docker logs -f astro-catalog-service

# Airflow scheduler logs
docker logs -f astro-airflow-scheduler

# All services
docker-compose logs -f
```

## Troubleshooting

### Services Won't Start

```bash
# Check service status
docker-compose ps

# View service logs
docker-compose logs image-processor
docker-compose logs catalog-service

# Restart specific service
docker-compose restart image-processor

# Rebuild service
docker-compose up -d --build image-processor
```

### Database Connection Issues

```bash
# Verify PostgreSQL is ready
docker exec astro-postgres pg_isready -U astro_user

# Check database exists
docker exec astro-postgres psql -U astro_user -l

# Restart database
docker-compose restart postgres
```

### MinIO Connection Issues

```bash
# Check MinIO is healthy
curl http://localhost:9000/minio/health/live

# Verify buckets exist
docker exec astro-minio mc ls local/

# Check MinIO logs
docker logs astro-minio
```

### Image Processor Can't Access MinIO

```bash
# Test MinIO from image-processor container
docker exec astro-image-processor curl http://astro-minio:9000/minio/health/live

# Verify environment variables
docker exec astro-image-processor env | grep -E '(S3|AWS)'

# Check network connectivity
docker exec astro-image-processor ping astro-minio
```

## Development Workflow

### 1. Code Changes

```bash
# Make changes to Java source code
vim application/image-processor/src/main/java/...

# Format code (if not using git hooks)
cd application && ./gradlew spotlessApply

# Rebuild service
docker-compose up -d --build image-processor

# Or rebuild without cache
docker-compose build --no-cache image-processor
docker-compose up -d image-processor
```

### 2. Database Migrations

```bash
# Create new migration
# Edit: application/image-processor/src/main/resources/db/migration/V{version}__description.sql

# Apply migrations (automatic on startup with Flyway)
docker-compose restart image-processor

# Or manually run Flyway
./gradlew flywayMigrate
```

### 3. Airflow DAG Development

```bash
# Edit DAG files
vim airflow/dags/telescope_data_processing.py

# Reload Airflow (DAGs auto-reload, but scheduler restart ensures pickup)
docker-compose restart airflow-scheduler
```

## Clean Up

### Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v

# Stop specific service
docker-compose stop image-processor
```

### Clean MinIO Data

```bash
# Remove all objects from bucket
docker exec astro-minio mc rm --recursive --force local/processed-data/

# Recreate bucket
docker exec astro-minio mc rb --force local/processed-data
docker exec astro-minio mc mb local/processed-data
```

### Clean PostgreSQL Data

```bash
# Drop and recreate database (WARNING: deletes all data)
docker exec astro-postgres psql -U astro_user -c "DROP DATABASE astro_catalog;"
docker exec astro-postgres psql -U astro_user -c "CREATE DATABASE astro_catalog;"

# Restart service to run migrations
docker-compose restart image-processor catalog-service
```

## Performance Tuning

### Increase Java Heap Size

```yaml
# docker-compose.yml
services:
  image-processor:
    environment:
      JAVA_OPTS: "-XX:MaxRAMPercentage=75.0 -Xmx4g"
```

### Increase Database Connections

```yaml
# docker-compose.yml
services:
  image-processor:
    environment:
      DATABASE_MAX_POOL_SIZE: 20
```

### Increase Processing Parallelism

```yaml
services:
  image-processor:
    environment:
      PROCESSING_MAX_CONCURRENT_JOBS: 8
      PROCESSING_THREAD_POOL_SIZE: 16
```

## Next Steps

After setting up the local environment:

1. **Test with sample data**: Generate FITS files using `application/data-simulator/`
2. **Experiment with granular processing**: Test Phase 3 research workflows
3. **Develop custom DAGs**: Create new Airflow workflows for your use case
4. **Prepare for Kubernetes**: Use docker-compose as a stepping stone to local K8s testing

## Architecture Differences: Local vs. Production

| Aspect           | Local (docker-compose)        | Production (AWS/EKS)     |
|------------------|-------------------------------|--------------------------|
| Storage          | MinIO                         | AWS S3                   |
| Database         | PostgreSQL container          | RDS Multi-AZ             |
| Airflow Executor | LocalExecutor                 | KubernetesExecutor       |
| Scaling          | Manual (docker-compose scale) | Auto-scaling (HPA)       |
| Monitoring       | Prometheus + Grafana          | CloudWatch + Prometheus  |
| Networking       | Docker network                | VPC with security groups |
| Authentication   | Simple credentials            | IAM + RBAC               |

The storage abstraction layer ensures that **application code is identical** across environments—only configuration
changes.
