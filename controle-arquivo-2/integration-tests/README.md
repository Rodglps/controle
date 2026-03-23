# Integration Tests

Comprehensive end-to-end integration tests for the Controle de Arquivos system using Testcontainers.

## Overview

This module contains integration tests that validate the complete system flow from file collection to destination upload, including:

- **End-to-End Flow**: Complete workflow from SFTP collection to S3/SFTP upload
- **Error Scenarios**: Error handling and classification (recoverable vs non-recoverable)
- **Concurrency Control**: Multiple orchestrator instances with job_concurrency_control
- **Reprocessing**: Retry logic and 5-attempt limit
- **Streaming Performance**: Large file handling (1GB+) with memory validation

## Test Structure

### BaseIntegrationTest

Base class that sets up all required infrastructure using Testcontainers:

- **Oracle XE**: Database for file tracking and rastreabilidade
- **RabbitMQ**: Message broker for orchestrator-processor communication
- **LocalStack**: AWS S3 simulation for file storage
- **SFTP Server**: Source and destination file servers

All containers are shared across tests for performance and reused between test runs.

### Test Classes

#### 1. EndToEndFlowIntegrationTest

**Validates Requirements**: 1.1, 2.1, 3.1, 4.1, 6.1, 7.1, 8.1, 9.1, 10.1, 12.1

Tests the complete flow:
- Orchestrator collects files from SFTP
- Files are registered in database
- Messages are published to RabbitMQ
- Processor consumes messages
- Files are downloaded via streaming
- Client and layout are identified
- Files are uploaded to destination
- Rastreabilidade is maintained throughout

**Key Tests**:
- `shouldProcessCompleteFlowFromSFTPToS3()`: Full workflow validation
- `shouldMaintainRastreabilidadeAcrossAllStages()`: Tracking validation
- `shouldHandleMultipleFilesInParallel()`: Concurrent file processing

#### 2. ErrorScenariosIntegrationTest

**Validates Requirements**: 8.5, 9.6, 15.1, 15.2, 15.3, 15.4

Tests error handling:
- File not found on SFTP
- Client not identified (no matching rules)
- Layout not identified (no matching rules)
- Upload failures
- Error classification (recoverable vs non-recoverable)

**Key Tests**:
- `shouldHandleFileNotFoundError()`: Missing file handling
- `shouldHandleClientNotIdentifiedError()`: Client identification failure
- `shouldHandleLayoutNotIdentifiedError()`: Layout identification failure
- `shouldHandleUploadFailureAsRecoverable()`: Recoverable error classification
- `shouldRecordCompleteErrorContext()`: Error context logging

#### 3. ConcurrencyIntegrationTest

**Validates Requirements**: 5.1, 5.2, 5.3, 5.4, 5.5

Tests concurrency control:
- Multiple orchestrator instances
- Lock acquisition and release
- Job status transitions (RUNNING → COMPLETED/PENDING)
- Stale lock detection

**Key Tests**:
- `shouldAllowOnlyOneInstanceToRunAtATime()`: Mutual exclusion
- `shouldCreateRunningStatusWhenLockAcquired()`: Lock acquisition
- `shouldUpdateToCompletedWhenLockReleased()`: Lock release
- `shouldUpdateToPendingOnFailure()`: Failure handling
- `shouldPreventConcurrentExecutionWithRunningJob()`: Concurrent prevention

#### 4. ReprocessingIntegrationTest

**Validates Requirements**: 15.3, 15.4, 15.6

Tests retry and reprocessing:
- Retry after recoverable failures
- No retry after non-recoverable failures
- Maximum 5 retry attempts
- Permanent error marking after limit

**Key Tests**:
- `shouldRetryAfterRecoverableFailure()`: Retry logic
- `shouldNotRetryAfterNonRecoverableFailure()`: Non-recoverable handling
- `shouldLimitRetriesToFiveAttempts()`: Retry limit enforcement
- `shouldMarkAsPermanentErrorAfterMaxRetries()`: Permanent error marking
- `shouldSucceedAfterTransientFailure()`: Eventual success

#### 5. StreamingPerformanceIntegrationTest

**Validates Requirements**: 7.1, 10.2, 10.3, 10.5

Tests streaming and performance:
- Large file processing (100MB-1GB+)
- Memory usage validation
- File integrity verification
- Multipart upload for S3
- SFTP to S3 streaming

**Key Tests**:
- `shouldStreamLargeFileWithoutExceedingMemory()`: Memory bounds
- `shouldValidateFileSizeAfterStreaming()`: File integrity
- `shouldStreamMultipleFilesSequentially()`: Sequential processing
- `shouldMeasureStreamingThroughput()`: Performance metrics
- `shouldStreamFromSFTPToS3WithoutIntermediateStorage()`: Direct streaming

## Running Tests

### Prerequisites

- Docker installed and running
- Java 17+
- Maven 3.8+
- At least 4GB RAM available for containers

### Run All Integration Tests

```bash
mvn verify
```

### Run Specific Test Class

```bash
mvn verify -Dit.test=EndToEndFlowIntegrationTest
```

### Run with Specific Profile

```bash
mvn verify -Pintegration-tests
```

### Skip Integration Tests

```bash
mvn verify -Dskip.integration.tests=true
```

## Test Configuration

### Container Configuration

Containers are configured in `BaseIntegrationTest`:

- **Oracle**: `gvenzl/oracle-xe:21-slim-faststart`
- **RabbitMQ**: `rabbitmq:3.12-management-alpine`
- **LocalStack**: `localstack/localstack:3.0`
- **SFTP**: `atmoz/sftp:alpine`

### Dynamic Properties

Test properties are dynamically configured from container endpoints:

```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", oracleContainer::getJdbcUrl);
    registry.add("spring.rabbitmq.host", rabbitmqContainer::getHost);
    registry.add("aws.s3.endpoint", () -> localstackContainer.getEndpointOverride(S3).toString());
    registry.add("sftp.host", sftpContainer::getHost);
}
```

## Test Data

### Database Setup

Tests use clean database state for each test:

```java
@BeforeEach
void setUpTest() {
    jdbcTemplate.execute("DELETE FROM file_origin_client_processing");
    jdbcTemplate.execute("DELETE FROM file_origin_client");
    jdbcTemplate.execute("DELETE FROM file_origin");
}
```

### Test Files

Test files are generated dynamically or uploaded to SFTP:

```java
String fileContent = "HEADER|CIELO|20240115\nDATA|12345|100.00\nTRAILER|2";
sftpChannel.put(
    new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
    "/upload/origin/" + fileName
);
```

## Assertions and Waiting

Tests use Awaitility for asynchronous assertions:

```java
await().atMost(60, SECONDS).untilAsserted(() -> {
    List<FileOrigin> files = fileOriginRepository.findAll();
    assertThat(files).isNotEmpty();
});
```

## Performance Considerations

### Container Reuse

Containers are reused between tests for performance:

```java
@Container
protected static final OracleContainer oracleContainer = new OracleContainer(...)
    .withReuse(true);
```

### Memory Management

Performance tests validate memory usage:

```java
Runtime runtime = Runtime.getRuntime();
runtime.gc();
long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
// ... perform operation ...
runtime.gc();
long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
assertThat(memoryAfter - memoryBefore).isLessThan(50 * MB);
```

## Troubleshooting

### Container Startup Issues

If containers fail to start:

1. Check Docker is running: `docker ps`
2. Check available resources: `docker system df`
3. Clean up old containers: `docker system prune`

### Test Timeouts

If tests timeout:

1. Increase Awaitility timeout: `await().atMost(120, SECONDS)`
2. Check container logs: `docker logs <container-id>`
3. Verify network connectivity between containers

### Memory Issues

If tests fail with OutOfMemoryError:

1. Increase JVM heap: `export MAVEN_OPTS="-Xmx4g"`
2. Reduce test file sizes in performance tests
3. Run tests individually instead of all at once

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Run Integration Tests
  run: mvn verify -Pintegration-tests
  env:
    TESTCONTAINERS_RYUK_DISABLED: false
```

### Jenkins Example

```groovy
stage('Integration Tests') {
    steps {
        sh 'mvn verify -Pintegration-tests'
    }
}
```

## Coverage

Integration tests provide coverage for:

- ✅ Complete end-to-end workflows
- ✅ Error handling and recovery
- ✅ Concurrency control
- ✅ Retry and reprocessing logic
- ✅ Streaming and performance
- ✅ Database transactions
- ✅ Message broker integration
- ✅ External service integration (S3, SFTP)

## Future Enhancements

Potential additions:

- [ ] Chaos engineering tests (container failures)
- [ ] Load testing with multiple concurrent files
- [ ] Network latency simulation
- [ ] Database failover testing
- [ ] Message broker failover testing
- [ ] Security testing (authentication, authorization)
- [ ] Monitoring and observability validation
