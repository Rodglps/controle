# E2E Tests for Controle de Arquivos EDI

## Overview

This package contains End-to-End (E2E) tests that validate the complete file transfer flow from SFTP origin to destination (S3 or SFTP).

## Test Structure

### E2ETestBase

Base class that provides:
- **TestContainers infrastructure**: Oracle, RabbitMQ, LocalStack S3, SFTP servers
- **Helper methods**: File upload/download, integrity validation (SHA-256)
- **Database initialization**: DDL scripts and test data
- **S3 client**: Pre-configured for LocalStack

### FileTransferE2ETest

Contains two main test scenarios:

1. **testSftpToS3Transfer**: Validates SFTP → S3 transfer
   - Uploads file to SFTP origin
   - Waits for Producer to detect and register file
   - Waits for Consumer to transfer file to S3
   - Validates file integrity (size and SHA-256 hash)

2. **testSftpToSftpTransfer**: Validates SFTP → SFTP transfer
   - Uploads file to SFTP origin
   - Waits for Producer to detect and register file
   - Waits for Consumer to transfer file to SFTP destination
   - Validates file integrity (size and SHA-256 hash)

## Requirements Validated

These tests validate the following requirements:

- **20.1**: TestContainers infrastructure setup
- **20.2**: File upload to SFTP monitored folder
- **20.3**: Scheduler cycle detection (max 2 minutes)
- **20.4**: file_origin record creation (COLETA/EM_ESPERA)
- **20.5**: RabbitMQ message publication
- **20.6**: Status update to PROCESSAMENTO
- **20.7**: Streaming download from SFTP origin
- **20.8**: Streaming upload to S3
- **20.9**: Streaming upload to SFTP destination
- **20.10**: Status update to CONCLUIDO
- **20.11**: File existence validation
- **20.12**: File size integrity validation
- **20.13**: File content integrity validation (SHA-256)
- **20.14**: S3 transfer validation
- **20.15**: SFTP transfer validation

## Running the Tests

### Prerequisites

1. **Docker**: Must be installed and running
2. **Maven**: Version 3.6+ with Java 21
3. **Producer and Consumer**: Must be built and available as Docker images

### Option 1: Run with Docker Compose (Recommended)

This approach uses the existing docker-compose.yml to start Producer and Consumer:

```bash
# Step 1: Build the applications
mvn clean package -DskipTests

# Step 2: Start infrastructure and applications
docker-compose up -d

# Step 3: Wait for services to be ready (about 60 seconds)
sleep 60

# Step 4: Run E2E tests
mvn test -pl commons -Dtest=FileTransferE2ETest

# Step 5: Stop services
docker-compose down
```

### Option 2: Run Tests Only (Infrastructure Only)

This approach runs only the TestContainers infrastructure without Producer/Consumer:

```bash
# Run tests (TestContainers will start Oracle, RabbitMQ, S3, SFTP)
mvn test -pl commons -Dtest=FileTransferE2ETest
```

**Note**: This option will fail because Producer and Consumer are not running. It's useful for testing the infrastructure setup only.

### Option 3: Manual Testing

For manual testing and debugging:

```bash
# Step 1: Start infrastructure
docker-compose up -d oracle rabbitmq localstack sftp-origin sftp-destination

# Step 2: Run Producer and Consumer locally (in separate terminals)
cd producer
mvn spring-boot:run

cd consumer
mvn spring-boot:run

# Step 3: Upload test file to SFTP
docker exec -it edi-sftp-client sh
sftp -P 2222 cielo@localhost
# password: admin-1-2-3
put test-file.txt /upload/cielo/

# Step 4: Monitor logs
docker-compose logs -f producer consumer

# Step 5: Verify file in S3
aws --endpoint-url=http://localhost:4566 s3 ls s3://edi-files/cielo/

# Step 6: Verify file in SFTP destination
sftp -P 2223 internal@localhost
# password: internal-pass
ls /destination/cielo/
```

## Test Configuration

### Timeouts

- **File detection**: 150 seconds (2.5 minutes) - allows for scheduler cycle
- **File processing**: 120 seconds (2 minutes) - allows for transfer completion
- **Overall test**: 5 minutes maximum

### Test Data

- **SFTP to S3**: 1MB file with random content
- **SFTP to SFTP**: 500KB CSV file with random content

### Database Configuration

Tests use the following test data:

```sql
-- Server: SFTP_CIELO_ORIGIN (ID: 1)
-- Server: S3_DESTINATION (ID: 2)
-- Server: SFTP_DESTINATION (ID: 3)

-- Path: /upload/cielo (ORIGIN, ID: 1)
-- Path: cielo/ (DESTINATION S3, ID: 2)
-- Path: /destination/cielo (DESTINATION SFTP, ID: 3)

-- Mapping: SFTP → S3 (PRINCIPAL, ID: 1)
-- Mapping: SFTP → SFTP (PRINCIPAL, ID: 2)
```

## Troubleshooting

### Tests Timeout

If tests timeout, check:
1. Docker is running and has sufficient resources
2. Producer and Consumer are running and healthy
3. Scheduler is configured correctly (120 seconds cycle)
4. RabbitMQ is accessible and queue is created

### File Not Detected

If Producer doesn't detect files:
1. Check SFTP origin container is running
2. Verify file was uploaded to correct path: `/upload/cielo/`
3. Check Producer logs for SFTP connection errors
4. Verify credentials in environment variables

### File Not Transferred

If Consumer doesn't transfer files:
1. Check RabbitMQ message was published
2. Verify Consumer is consuming messages
3. Check Consumer logs for transfer errors
4. Verify S3/SFTP destination credentials

### Database Errors

If database initialization fails:
1. Check Oracle container is healthy
2. Verify DDL scripts are correct
3. Check database connection string
4. Review Oracle logs: `docker logs edi-oracle`

## Test Output

Successful test output example:

```
=== Starting E2E Test: SFTP to S3 Transfer ===

Test file: test-file-s3-1234567890.txt
Content size: 1048576 bytes
Expected SHA-256: a1b2c3d4...

[Step 1] Uploading file to SFTP origin...
✓ File uploaded to SFTP origin

[Step 2] Waiting for Producer to detect file...
✓ File registered in file_origin with ID: 1

[Step 3] Validating initial file_origin record...
✓ Initial record validated: COLETA/EM_ESPERA

[Step 4] Waiting for Consumer to process file...
  Current status: PROCESSAMENTO
  Current status: CONCLUIDO
✓ File processing completed

[Step 5] Validating final file_origin record...
✓ Final record validated: COLETA/CONCLUIDO

[Step 6] Validating file in S3...
✓ File exists in S3: cielo/test-file-s3-1234567890.txt

[Step 7] Validating file integrity...
✓ File size matches: 1048576 bytes
✓ File content matches (SHA-256): a1b2c3d4...

=== E2E Test PASSED: SFTP to S3 Transfer ===
```

## CI/CD Integration

For continuous integration, use the following approach:

```yaml
# .github/workflows/e2e-tests.yml
name: E2E Tests
on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      
      - name: Build applications
        run: mvn clean package -DskipTests
      
      - name: Start infrastructure
        run: docker-compose up -d
      
      - name: Wait for services
        run: sleep 60
      
      - name: Run E2E tests
        run: mvn test -pl commons -Dtest=FileTransferE2ETest
      
      - name: Stop infrastructure
        run: docker-compose down -v
```

## Future Enhancements

1. **Parallel test execution**: Run multiple scenarios concurrently
2. **Error scenario tests**: Test retry logic, max retry, error handling
3. **Performance tests**: Large file transfers (100MB+), memory validation
4. **Duplicate detection test**: Upload same file twice
5. **Invalid file type test**: Upload unsupported file types
6. **Network failure simulation**: Test resilience with Toxiproxy
