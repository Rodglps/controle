# Docker Infrastructure Setup

## Overview

This document describes the Docker Compose infrastructure for the EDI File Control System.

## Architecture

The system consists of 8 containers:

1. **oracle** - Oracle XE 21 database
2. **rabbitmq** - RabbitMQ 3.12 message broker with management UI
3. **localstack** - LocalStack for S3 simulation
4. **sftp-origin** - SFTP server simulating external acquirer
5. **sftp-destination** - SFTP server for internal destination
6. **sftp-client** - SFTP server for manual testing
7. **producer** - Java application for file collection
8. **consumer** - Java application for file transfer

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- At least 4GB RAM available for Docker
- At least 10GB disk space

## Quick Start

### 1. Build the Applications

```bash
# Build all modules
mvn clean package -DskipTests

# Or build with tests
mvn clean install
```

### 2. Start Infrastructure Only

```bash
# Start only infrastructure services (no producer/consumer)
docker-compose up -d oracle rabbitmq localstack sftp-origin sftp-destination sftp-client

# Wait for services to be healthy
docker-compose ps
```

### 3. Start Complete System

```bash
# Start all services including producer and consumer
docker-compose up -d

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f producer
docker-compose logs -f consumer
```

## Service Details

### Oracle Database

- **Image**: `gvenzl/oracle-xe:21-slim`
- **Port**: 1521
- **Credentials**:
  - System: `system/oracle`
  - Application: `edi_user/edi_pass`
- **Database**: `XEPDB1`
- **Connection**: `jdbc:oracle:thin:@localhost:1521/XEPDB1`
- **Initialization**: Automatic via `/docker-entrypoint-initdb.d/startup`
- **Health Check**: Built-in `healthcheck.sh`

### RabbitMQ

- **Image**: `rabbitmq:3.12-management`
- **Ports**:
  - AMQP: 5672
  - Management UI: 15672
- **Credentials**: `admin/admin`
- **Management UI**: http://localhost:15672
- **Queue Type**: Quorum Queue (durable, replicated)

### LocalStack (S3)

- **Image**: `localstack/localstack:latest`
- **Port**: 4566
- **Service**: S3 only
- **Region**: `us-east-1`
- **Bucket**: `edi-files` (created automatically)
- **Versioning**: Enabled
- **Endpoint**: http://localhost:4566
- **Persistence**: Enabled (data persists across restarts)
- **Volume**: `/var/lib/localstack`
- **Init Scripts**: `/etc/localstack/init/ready.d`
- **Note**: Requires `LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT=1` to run without authentication until April 2026

### SFTP Servers

#### SFTP Origin (External Acquirer)
- **Image**: `atmoz/sftp:latest`
- **Port**: 2222
- **Credentials**: `cielo/admin-1-2-3`
- **Directory**: `/home/cielo/upload`
- **Purpose**: Simulates external acquirer SFTP

#### SFTP Destination (Internal)
- **Image**: `atmoz/sftp:latest`
- **Port**: 2223
- **Credentials**: `internal/internal-pass`
- **Directory**: `/home/internal/destination`
- **Purpose**: Internal SFTP destination

#### SFTP Client (Testing)
- **Image**: `atmoz/sftp:latest`
- **Port**: 2224
- **Credentials**: `client/client-pass`
- **Directory**: `/home/client/files`
- **Purpose**: Manual testing and file uploads

### Producer Application

- **Build Context**: `./producer`
- **Port**: 8080
- **Profile**: `docker`
- **Health Endpoint**: http://localhost:8080/actuator/health
- **Dependencies**: oracle, rabbitmq, sftp-origin

### Consumer Application

- **Build Context**: `./consumer`
- **Port**: 8081
- **Profile**: `docker`
- **Health Endpoint**: http://localhost:8081/actuator/health
- **Dependencies**: oracle, rabbitmq, localstack, sftp-origin, sftp-destination

## Environment Variables

### Producer

```bash
SPRING_PROFILES_ACTIVE=docker
DB_URL=jdbc:oracle:thin:@oracle:1521/XEPDB1
DB_USERNAME=edi_user
DB_PASSWORD=edi_pass
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=admin
SFTP_CIELO_VAULT={"user":"cielo","password":"admin-1-2-3"}
```

### Consumer

```bash
SPRING_PROFILES_ACTIVE=docker
DB_URL=jdbc:oracle:thin:@oracle:1521/XEPDB1
DB_USERNAME=edi_user
DB_PASSWORD=edi_pass
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=admin
AWS_ENDPOINT=http://localstack:4566
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
SFTP_CIELO_VAULT={"user":"cielo","password":"admin-1-2-3"}
SFTP_INTERNAL_VAULT={"user":"internal","password":"internal-pass"}
```

## Network

All services are connected via a bridge network named `edi-network`.

Services can communicate using container names as hostnames:
- `oracle:1521`
- `rabbitmq:5672`
- `localstack:4566`
- `sftp-origin:22`
- `sftp-destination:22`

## Volumes

Persistent volumes for data storage:

- `oracle-data` - Oracle database files
- `rabbitmq-data` - RabbitMQ data and configuration
- `localstack-data` - LocalStack S3 data
- `sftp-origin-data` - SFTP origin files
- `sftp-destination-data` - SFTP destination files
- `sftp-client-data` - SFTP client files

## Common Operations

### View Service Status

```bash
docker-compose ps
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f producer
docker-compose logs -f consumer
docker-compose logs -f oracle
```

### Restart Services

```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart producer
docker-compose restart consumer
```

### Stop Services

```bash
# Stop all
docker-compose stop

# Stop specific service
docker-compose stop producer
```

### Remove Everything

```bash
# Stop and remove containers (keeps volumes)
docker-compose down

# Stop and remove containers and volumes (complete cleanup)
docker-compose down -v
```

### Rebuild Applications

```bash
# Rebuild and restart producer
mvn clean package -DskipTests -pl producer
docker-compose up -d --build producer

# Rebuild and restart consumer
mvn clean package -DskipTests -pl consumer
docker-compose up -d --build consumer
```

## Testing the System

### 1. Upload Test File to SFTP Origin

```bash
# Create test file
echo "test,data,content" > test-file.csv

# Upload via SFTP client
sftp -P 2222 cielo@localhost
# Password: admin-1-2-3
put test-file.csv upload/test-file.csv
exit
```

### 2. Monitor Producer Logs

```bash
docker-compose logs -f producer
```

The producer should:
- Detect the file in the next scheduler cycle (max 2 minutes)
- Register it in the database
- Publish a message to RabbitMQ

### 3. Monitor Consumer Logs

```bash
docker-compose logs -f consumer
```

The consumer should:
- Consume the message
- Download the file from SFTP origin
- Upload to S3 (LocalStack)
- Update the database status

### 4. Verify File in S3

```bash
# Install awslocal (if not installed)
pip install awscli-local

# List files in S3
awslocal --endpoint-url=http://localhost:4566 s3 ls s3://edi-files/
```

### 5. Check Database

```bash
# Connect to Oracle
docker exec -it edi-oracle sqlplus edi_user/edi_pass@XEPDB1

# Query file_origin table
SELECT idt_file_origin, des_file_name, des_step, des_status 
FROM file_origin 
ORDER BY dat_creation DESC;
```

## Troubleshooting

### Oracle Not Starting

```bash
# Check logs
docker logs edi-oracle

# Common issues:
# - Insufficient disk space (needs ~2GB)
# - Port 1521 already in use
# - Wait for initialization (2-3 minutes on first start)
```

### RabbitMQ Connection Refused

```bash
# Check if RabbitMQ is healthy
docker-compose ps rabbitmq

# Check logs
docker logs edi-rabbitmq

# Verify management UI
curl http://localhost:15672
```

### Producer/Consumer Not Starting

```bash
# Check if dependencies are healthy
docker-compose ps

# Rebuild application
mvn clean package -DskipTests
docker-compose up -d --build producer

# Check application logs
docker-compose logs producer
```

### SFTP Connection Issues

```bash
# Test SFTP connection manually
sftp -P 2222 cielo@localhost
# Password: admin-1-2-3

# Check SFTP container logs
docker logs edi-sftp-origin
```

### LocalStack S3 Issues

```bash
# Check LocalStack health
curl http://localhost:4566/_localstack/health

# Verify bucket exists
awslocal --endpoint-url=http://localhost:4566 s3 ls

# Check LocalStack logs
docker logs edi-localstack

# Common issues:
# - "Device or resource busy" error: Fixed by using /var/lib/localstack volume
# - "No credentials found": Add LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT=1
# - Bucket not created: Check init script in /etc/localstack/init/ready.d
```

**LocalStack Authentication Issue (2026+)**:
LocalStack now requires authentication or acknowledgment. The docker-compose includes:
```yaml
LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT: 1
```
This allows running without authentication until April 6, 2026. After that, you'll need:
- A LocalStack account and auth token, OR
- Update the acknowledgment date, OR
- Use an older LocalStack version

**Re-run initialization script**:
```bash
# If bucket wasn't created automatically
docker exec edi-localstack sh -c "cd /etc/localstack/init/ready.d && ./init-s3.sh"
```

## Health Checks

All infrastructure services have health checks:

```bash
# Check health status
docker-compose ps

# Services should show "healthy" status:
# - oracle: healthcheck.sh
# - rabbitmq: rabbitmq-diagnostics ping
# - localstack: awslocal s3 ls
```

## Performance Tuning

### Java Applications

Adjust JVM memory in Dockerfiles:

```dockerfile
ENV JAVA_OPTS="-Xmx512m -Xms256m"
```

### Oracle Database

For production, consider:
- Increasing SGA/PGA memory
- Using persistent volumes on fast storage
- Tuning connection pool sizes

### RabbitMQ

For high throughput:
- Increase prefetch count
- Use multiple consumers
- Monitor queue depth

## Security Notes

**WARNING**: This configuration is for development only!

Production considerations:
- Use strong passwords
- Enable TLS/SSL for all connections
- Use secrets management (Vault)
- Restrict network access
- Enable authentication on all services
- Use read-only file systems where possible

## Additional Resources

- [Oracle XE Documentation](https://hub.docker.com/r/gvenzl/oracle-xe)
- [RabbitMQ Docker Guide](https://hub.docker.com/_/rabbitmq)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [SFTP Server Documentation](https://hub.docker.com/r/atmoz/sftp)

