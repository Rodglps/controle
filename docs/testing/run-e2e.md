# Quick Guide: Running E2E Tests

## Prerequisites

- Docker installed and running
- Maven 3.6+ with Java 21
- At least 4GB RAM available for Docker

## Quick Start

### Step 1: Build the Project

```bash
mvn clean package -DskipTests
```

### Step 2: Start Infrastructure and Applications

```bash
docker-compose up -d
```

Wait for all services to be healthy (about 60-90 seconds):

```bash
# Check service health
docker-compose ps

# Watch logs
docker-compose logs -f producer consumer
```

### Step 3: Run E2E Tests

```bash
mvn test -pl commons -Dtest=FileTransferE2ETest
```

### Step 4: View Results

The tests will output detailed progress:

```
=== Starting E2E Test: SFTP to S3 Transfer ===

Test file: test-file-s3-1234567890.txt
Content size: 1048576 bytes
Expected SHA-256: a1b2c3d4...

[Step 1] Uploading file to SFTP origin...
✓ File uploaded to SFTP origin

[Step 2] Waiting for Producer to detect file...
✓ File registered in file_origin with ID: 1

...

=== E2E Test PASSED: SFTP to S3 Transfer ===
```

### Step 5: Cleanup

```bash
docker-compose down -v
```

## Troubleshooting

### Tests Fail with Timeout

**Problem**: Tests timeout waiting for file detection or processing.

**Solution**:
1. Check Producer is running: `docker logs edi-producer`
2. Check Consumer is running: `docker logs edi-consumer`
3. Verify scheduler is running (should see logs every 2 minutes)
4. Check RabbitMQ is accessible: `docker logs edi-rabbitmq`

### Database Connection Errors

**Problem**: Cannot connect to Oracle database.

**Solution**:
1. Wait longer for Oracle to initialize (can take 60+ seconds)
2. Check Oracle health: `docker exec edi-oracle healthcheck.sh`
3. Verify database is ready: `docker logs edi-oracle | grep "DATABASE IS READY"`

### SFTP Connection Errors

**Problem**: Cannot upload files to SFTP origin.

**Solution**:
1. Check SFTP container is running: `docker ps | grep sftp-origin`
2. Test SFTP connection manually:
   ```bash
   sftp -P 2222 cielo@localhost
   # password: admin-1-2-3
   ```

### S3 Upload Errors

**Problem**: Files not appearing in S3.

**Solution**:
1. Check LocalStack is running: `docker logs edi-localstack`
2. Verify S3 bucket exists:
   ```bash
   aws --endpoint-url=http://localhost:4566 s3 ls
   ```
3. Check Consumer has correct AWS credentials

## Manual Verification

### Check File in S3

```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://edi-files/cielo/
```

### Check File in SFTP Destination

```bash
sftp -P 2223 internal@localhost
# password: internal-pass
ls /destination/cielo/
```

### Check Database Records

```bash
docker exec -it edi-oracle sqlplus edi_user/edi_pass@XEPDB1

SQL> SELECT idt_file_origin, des_file_name, des_step, des_status 
     FROM file_origin 
     ORDER BY dat_creation DESC;
```

### Check RabbitMQ Messages

Open browser: http://localhost:15672
- Username: admin
- Password: admin

Navigate to Queues → edi.file.transfer.queue

## Test Scenarios

### Scenario 1: SFTP to S3 (testSftpToS3Transfer)

- **File**: 1MB text file
- **Source**: SFTP origin (/upload/cielo/)
- **Destination**: S3 (s3://edi-files/cielo/)
- **Duration**: ~2-3 minutes
- **Validates**: Streaming transfer to S3, file integrity

### Scenario 2: SFTP to SFTP (testSftpToSftpTransfer)

- **File**: 500KB CSV file
- **Source**: SFTP origin (/upload/cielo/)
- **Destination**: SFTP destination (/destination/cielo/)
- **Duration**: ~2-3 minutes
- **Validates**: Streaming transfer to SFTP, file integrity

## Expected Test Duration

- **Infrastructure startup**: 60-90 seconds
- **Each test scenario**: 2-3 minutes
- **Total**: ~5-7 minutes

## CI/CD Integration

For GitHub Actions:

```yaml
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
      
      - name: Build
        run: mvn clean package -DskipTests
      
      - name: Start services
        run: docker-compose up -d
      
      - name: Wait for services
        run: sleep 90
      
      - name: Run E2E tests
        run: mvn test -pl commons -Dtest=FileTransferE2ETest
      
      - name: Cleanup
        if: always()
        run: docker-compose down -v
```

## Support

For issues or questions:
1. Check logs: `docker-compose logs`
2. Review README: `commons/src/test/java/com/concil/edi/commons/e2e/README.md`
3. Check implementation: `E2E_TEST_IMPLEMENTATION.md`
