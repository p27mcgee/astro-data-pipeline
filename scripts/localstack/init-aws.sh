#!/bin/bash

# LocalStack initialization script
# This script runs automatically when LocalStack reaches "ready" state
# Creates S3 buckets required for the astronomical data pipeline

echo "🪣 Initializing S3 buckets in LocalStack..."

# Create S3 buckets
awslocal s3 mb s3://astro-raw-data 2>/dev/null || echo "  ✓ astro-raw-data already exists"
awslocal s3 mb s3://astro-processed-data 2>/dev/null || echo "  ✓ astro-processed-data already exists"
awslocal s3 mb s3://astro-intermediate-data 2>/dev/null || echo "  ✓ astro-intermediate-data already exists"
awslocal s3 mb s3://astro-archive 2>/dev/null || echo "  ✓ astro-archive already exists"

echo "✅ S3 bucket initialization complete"

# List buckets to confirm
echo "📋 Available buckets:"
awslocal s3 ls