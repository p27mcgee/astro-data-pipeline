#!/bin/bash

# LocalStack S3 Bucket Initialization Script
# This script runs automatically when LocalStack starts up
# Location: /etc/localstack/init/ready.d/init-aws.sh (mounted via docker-compose)

set -e

echo "🚀 Initializing LocalStack AWS resources..."

# Wait for LocalStack to be fully ready
echo "⏳ Waiting for LocalStack S3 service..."
until awslocal s3 ls 2>/dev/null; do
    sleep 1
done

echo "✅ LocalStack S3 is ready"

# Create S3 buckets for the astronomical data pipeline
echo "📦 Creating S3 buckets..."

# Raw data bucket
awslocal s3 mb s3://astro-raw-data 2>/dev/null || echo "Bucket astro-raw-data already exists"

# Processed data bucket
awslocal s3 mb s3://astro-processed-data 2>/dev/null || echo "Bucket astro-processed-data already exists"

# Intermediate data bucket
awslocal s3 mb s3://astro-intermediate-data 2>/dev/null || echo "Bucket astro-intermediate-data already exists"

# Archive bucket
awslocal s3 mb s3://astro-archive 2>/dev/null || echo "Bucket astro-archive already exists"

# Verify buckets were created
echo "🔍 Verifying S3 buckets..."
awslocal s3 ls

echo "✨ LocalStack initialization complete!"
echo ""
echo "📍 Available S3 buckets:"
echo "  - s3://astro-raw-data           (raw FITS files)"
echo "  - s3://astro-processed-data     (calibrated/processed data)"
echo "  - s3://astro-intermediate-data  (intermediate processing files)"
echo "  - s3://astro-archive            (archived data)"
