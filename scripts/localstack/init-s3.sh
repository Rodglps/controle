#!/bin/bash

# Wait for LocalStack to be ready
echo "Waiting for LocalStack to be ready..."
sleep 5

# Create S3 bucket for EDI files
echo "Creating S3 bucket: edi-files"
awslocal s3 mb s3://edi-files

# Enable versioning on the bucket
echo "Enabling versioning on bucket: edi-files"
awslocal s3api put-bucket-versioning \
  --bucket edi-files \
  --versioning-configuration Status=Enabled

# Verify bucket creation
echo "Verifying bucket creation..."
awslocal s3 ls

echo "LocalStack S3 initialization complete!"
