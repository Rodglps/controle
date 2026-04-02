#!/bin/bash

set -e

echo "=========================================="
echo "EDI File Control System - Startup Script"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running"
    echo "Please start Docker and try again"
    exit 1
fi

echo "✓ Docker is running"
echo ""

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Error: docker-compose is not installed"
    echo "Please install docker-compose and try again"
    exit 1
fi

echo "✓ docker-compose is available"
echo ""

# Build applications
echo "📦 Building applications..."
echo ""
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo ""
echo "✓ Build successful"
echo ""

# Start infrastructure services first
echo "🚀 Starting infrastructure services..."
echo ""
docker-compose up -d oracle rabbitmq localstack sftp-origin sftp-destination sftp-client

echo ""
echo "⏳ Waiting for services to be healthy..."
echo "   This may take 2-3 minutes on first startup..."
echo ""

# Wait for health checks
MAX_WAIT=180
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $MAX_WAIT ]; do
    ORACLE_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' edi-oracle 2>/dev/null || echo "starting")
    RABBITMQ_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' edi-rabbitmq 2>/dev/null || echo "starting")
    LOCALSTACK_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' edi-localstack 2>/dev/null || echo "starting")
    
    if [ "$ORACLE_HEALTH" = "healthy" ] && [ "$RABBITMQ_HEALTH" = "healthy" ] && [ "$LOCALSTACK_HEALTH" = "healthy" ]; then
        echo "✓ All infrastructure services are healthy"
        break
    fi
    
    echo "   Oracle: $ORACLE_HEALTH | RabbitMQ: $RABBITMQ_HEALTH | LocalStack: $LOCALSTACK_HEALTH"
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo "⚠️  Warning: Services did not become healthy within $MAX_WAIT seconds"
    echo "   Continuing anyway... Check logs if applications fail to start"
fi

echo ""

# Start applications
echo "🚀 Starting Producer and Consumer..."
echo ""
docker-compose up -d producer consumer

echo ""
echo "=========================================="
echo "✅ System started successfully!"
echo "=========================================="
echo ""
echo "Service URLs:"
echo "  - Producer Health:     http://localhost:8080/actuator/health"
echo "  - Consumer Health:     http://localhost:8081/actuator/health"
echo "  - RabbitMQ Management: http://localhost:15672 (admin/admin)"
echo "  - Oracle Database:     localhost:1521/XEPDB1 (edi_user/edi_pass)"
echo "  - LocalStack S3:       http://localhost:4566"
echo ""
echo "SFTP Servers:"
echo "  - Origin:      sftp://cielo@localhost:2222 (password: admin-1-2-3)"
echo "  - Destination: sftp://internal@localhost:2223 (password: internal-pass)"
echo "  - Client:      sftp://client@localhost:2224 (password: client-pass)"
echo ""
echo "Useful commands:"
echo "  - View logs:           docker-compose logs -f"
echo "  - View producer logs:  docker-compose logs -f producer"
echo "  - View consumer logs:  docker-compose logs -f consumer"
echo "  - Stop system:         docker-compose stop"
echo "  - Remove everything:   docker-compose down -v"
echo ""
echo "To test the system, upload a CSV file to SFTP origin:"
echo "  sftp -P 2222 cielo@localhost"
echo "  put your-file.csv upload/your-file.csv"
echo ""
