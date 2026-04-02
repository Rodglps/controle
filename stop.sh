#!/bin/bash

set -e

echo "=========================================="
echo "EDI File Control System - Stop Script"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running"
    exit 1
fi

# Check if containers are running
RUNNING=$(docker-compose ps -q 2>/dev/null | wc -l)

if [ "$RUNNING" -eq 0 ]; then
    echo "ℹ️  No containers are running"
    exit 0
fi

echo "🛑 Stopping all services..."
echo ""
docker-compose stop

echo ""
echo "✅ All services stopped"
echo ""
echo "To remove containers (keeps data):"
echo "  docker-compose down"
echo ""
echo "To remove containers and data:"
echo "  docker-compose down -v"
echo ""
echo "To start again:"
echo "  ./start.sh"
echo ""
