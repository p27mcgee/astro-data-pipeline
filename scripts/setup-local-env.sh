#!/bin/bash

set -e

echo "=================================================="
echo "Astronomical Data Pipeline - Local Setup"
echo "=================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Docker is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker is running${NC}"

# Check required ports
echo ""
echo "Checking required ports..."
REQUIRED_PORTS=(3000 5432 6379 8080 8081 8082 9000 9001 9090)
PORTS_IN_USE=()

for port in "${REQUIRED_PORTS[@]}"; do
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        PORTS_IN_USE+=($port)
    fi
done

if [ ${#PORTS_IN_USE[@]} -gt 0 ]; then
    echo -e "${YELLOW}WARNING: The following ports are already in use: ${PORTS_IN_USE[*]}${NC}"
    echo "You may need to stop other services or change port mappings in docker-compose.yml"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo -e "${GREEN}✓ All required ports are available${NC}"
fi

# Start services
echo ""
echo "Starting services..."
docker-compose up -d

echo ""
echo "Waiting for services to be healthy..."
sleep 10

# Wait for PostgreSQL
echo -n "Waiting for PostgreSQL..."
until docker exec astro-postgres pg_isready -U astro_user -d astro_catalog > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo -e " ${GREEN}✓${NC}"

# Wait for Redis
echo -n "Waiting for Redis..."
until docker exec astro-redis redis-cli ping > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo -e " ${GREEN}✓${NC}"

# Wait for MinIO
echo -n "Waiting for MinIO..."
until curl -sf http://localhost:9000/minio/health/live > /dev/null 2>&1; do
    echo -n "."
    sleep 2
done
echo -e " ${GREEN}✓${NC}"

# Check if buckets exist, create if not
echo ""
echo "Setting up MinIO buckets..."

BUCKETS=("raw-data" "processed-data" "intermediate-data" "archive")
MINIO_ALIAS="astro-local"

# Configure MinIO client alias
docker exec astro-minio mc alias set $MINIO_ALIAS http://localhost:9000 astro_access_key astro_secret_key > /dev/null 2>&1

for bucket in "${BUCKETS[@]}"; do
    if docker exec astro-minio mc ls $MINIO_ALIAS/$bucket > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Bucket '$bucket' exists${NC}"
    else
        docker exec astro-minio mc mb $MINIO_ALIAS/$bucket > /dev/null 2>&1
        echo -e "${GREEN}✓ Created bucket '$bucket'${NC}"
    fi
done

# Wait for Java services
echo ""
echo "Waiting for Java microservices to start..."
echo "(This may take 60-90 seconds on first run)"

echo -n "Waiting for Image Processor..."
MAX_ATTEMPTS=60
ATTEMPT=0
until curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; do
    echo -n "."
    sleep 3
    ATTEMPT=$((ATTEMPT+1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo -e " ${YELLOW}TIMEOUT${NC}"
        echo "Image Processor is taking longer than expected. Check logs:"
        echo "  docker logs astro-image-processor"
        break
    fi
done
if [ $ATTEMPT -lt $MAX_ATTEMPTS ]; then
    echo -e " ${GREEN}✓${NC}"
fi

echo -n "Waiting for Catalog Service..."
ATTEMPT=0
until curl -sf http://localhost:8082/actuator/health > /dev/null 2>&1; do
    echo -n "."
    sleep 3
    ATTEMPT=$((ATTEMPT+1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo -e " ${YELLOW}TIMEOUT${NC}"
        echo "Catalog Service is taking longer than expected. Check logs:"
        echo "  docker logs astro-catalog-service"
        break
    fi
done
if [ $ATTEMPT -lt $MAX_ATTEMPTS ]; then
    echo -e " ${GREEN}✓${NC}"
fi

# Display service status
echo ""
echo "=================================================="
echo "Service Status"
echo "=================================================="
docker-compose ps

echo ""
echo "=================================================="
echo "Local Development Environment Ready!"
echo "=================================================="
echo ""
echo "Service Endpoints:"
echo "  Image Processor:  http://localhost:8081/actuator/health"
echo "  Catalog Service:  http://localhost:8082/actuator/health"
echo "  Airflow UI:       http://localhost:8080 (admin/admin)"
echo "  MinIO Console:    http://localhost:9001 (astro_access_key/astro_secret_key)"
echo "  Grafana:          http://localhost:3000 (admin/admin)"
echo "  Prometheus:       http://localhost:9090"
echo ""
echo "Database:"
echo "  PostgreSQL:       localhost:5432 (astro_user/astro_password)"
echo "  Redis:            localhost:6379"
echo ""
echo "Storage Buckets (MinIO):"
echo "  - raw-data"
echo "  - processed-data"
echo "  - intermediate-data"
echo "  - archive"
echo ""
echo "Next Steps:"
echo "  1. Access MinIO Console: http://localhost:9001"
echo "  2. Upload test FITS files to raw-data bucket"
echo "  3. Submit processing job:"
echo "     curl -X POST http://localhost:8081/api/v1/processing/jobs/s3 \\"
echo "       -H 'Content-Type: application/json' \\"
echo "       -d '{\"inputBucket\": \"raw-data\", \"inputObjectKey\": \"test.fits\"}'"
echo ""
echo "For more details, see: docs/LOCAL_DEVELOPMENT.md"
echo ""
echo "To view logs: docker-compose logs -f [service-name]"
echo "To stop:      docker-compose down"
echo "=================================================="
