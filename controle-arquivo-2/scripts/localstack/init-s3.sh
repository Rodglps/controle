#!/bin/bash

# Wait for LocalStack to be ready
echo "Waiting for LocalStack to be ready..."
sleep 5

# Create S3 buckets for testing
echo "Creating S3 buckets..."
awslocal s3 mb s3://controle-arquivos-dev
awslocal s3 mb s3://controle-arquivos-staging
awslocal s3 mb s3://controle-arquivos-prod

# List created buckets
echo "S3 buckets created:"
awslocal s3 ls

echo "LocalStack S3 initialization complete!"
