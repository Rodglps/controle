#!/bin/bash
echo "Criando bucket S3 no LocalStack..."
awslocal s3 mb s3://controle-arquivos
awslocal s3api put-bucket-versioning \
  --bucket controle-arquivos \
  --versioning-configuration Status=Enabled
echo "Bucket criado com sucesso."
