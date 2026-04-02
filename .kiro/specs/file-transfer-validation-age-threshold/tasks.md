# Implementation Plan: File Transfer Validation with Age Threshold

## Overview

This implementation adds a hybrid validation approach to prevent processing files that are still being uploaded to SFTP servers. The system combines an age threshold filter (primary) with a double-check validation (secondary) to handle edge cases like timezone issues and clock skew. Implementation follows a layered approach: database schema changes, core validation service, DTO creation, integration with scheduler, and comprehensive testing.

## Tasks

- [x] 1. Update DDL script for Server table
  - Update `scripts/ddl/01_create_table_server.sql` to add `num_min_age_seconds NUMBER(10) DEFAULT 0`
  - Update `scripts/ddl/01_create_table_server.sql` to add `num_double_check_wait_seconds NUMBER(10) DEFAULT 0`
  - Add column comments for documentation
  - Recreate database objects using `make db-init`
  - _Requirements: 1.1, 1.2, 1.5, 1.6, 4.4, 4.5_

- [x] 2. Configure timezone consistency in docker-compose.yml
  - Add `TZ=America/Sao_Paulo` environment variable to Oracle service
  - Add `TZ=America/Sao_Paulo` environment variable to RabbitMQ service
  - Add `TZ=America/Sao_Paulo` environment variable to Producer service
  - Add `TZ=America/Sao_Paulo` environment variable to Consumer service
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 3. Configure timezone in Dockerfiles
  - Add `ENV TZ=America/Sao_Paulo` to Producer Dockerfile
  - Add `ENV TZ=America/Sao_Paulo` to Consumer Dockerfile
  - _Requirements: 8.6, 8.7_

- [x] 4. Update Server entity and ServerConfigurationDTO
  - [x] 4.1 Add validation parameter fields to Server entity
    - Add `numMinAgeSeconds` field with JPA column mapping
    - Add `numDoubleCheckWaitSeconds` field with JPA column mapping
    - _Requirements: 1.1, 1.2_
  
  - [x] 4.2 Add validation parameter fields to ServerConfigurationDTO
    - Add `minAgeSeconds` field
    - Add `doubleCheckWaitSeconds` field
    - _Requirements: 1.1, 1.2_
  
  - [x] 4.3 Update ConfigurationService to load validation parameters
    - Modify query projection to include new fields
    - Map Server fields to ServerConfigurationDTO
    - _Requirements: 1.1, 1.2_

- [x] 5. Create FileMetadataSnapshot DTO
  - [x] 5.1 Implement FileMetadataSnapshot class
    - Add fields: filename, lastModified, size, fileType
    - Implement `matches()` method for metadata comparison
    - _Requirements: 3.1, 3.4, 3.5, 3.6_
  
  - [ ]* 5.2 Write unit tests for FileMetadataSnapshot
    - Test `matches()` returns true for identical metadata
    - Test `matches()` returns false when lastModified differs
    - Test `matches()` returns false when size differs
    - _Requirements: 3.4, 3.5, 3.6_

- [x] 6. Implement FileValidator service
  - [x] 6.1 Create FileValidator class with core structure
    - Add Spring @Service annotation
    - Inject SftpService dependency
    - Add SLF4J logger
    - _Requirements: 2.1, 3.2_
  
  - [x] 6.2 Implement parameter normalization
    - Create `normalizeParameter()` method
    - Handle null values (return 0)
    - Handle negative values (return 0)
    - _Requirements: 4.1, 4.2, 5.2, 5.3_
  
  - [x] 6.3 Implement file age calculation with UTC normalization
    - Create `calculateFileAge()` method
    - Convert lastModified timestamp to UTC using `Instant.toInstant()`
    - Convert current time to UTC using `Instant.ofEpochMilli()`
    - Calculate age as: currentTimeUTC_millis - lastModifiedUTC_millis
    - Handle null timestamps (return -1)
    - Handle future timestamps within 24h tolerance (return 0)
    - Handle future timestamps beyond 24h tolerance (return -1)
    - Use millisecond precision
    - _Requirements: 2.2, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2_
  
  - [x] 6.4 Implement primary filter logic
    - Create `applyPrimaryFilter()` method
    - Calculate file age for each file
    - Exclude files with null/invalid timestamps
    - Exclude files with null/negative size
    - Exclude files younger than age threshold
    - Store metadata snapshots for passing files
    - Add DEBUG logging for exclusions
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 5.4, 5.5, 6.1, 6.3, 6.6_
  
  - [x] 6.5 Implement secondary validation logic
    - Create `applySecondaryValidation()` method
    - Wait for double-check duration
    - Re-list files from SFTP
    - Compare current metadata with snapshots
    - Exclude files with changed metadata
    - Exclude files that disappeared
    - Handle SFTP connection errors
    - Add INFO logging for exclusions and results
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 5.7, 6.2, 6.4_
  
  - [x] 6.6 Implement main validateFiles method
    - Normalize validation parameters
    - Skip validation when both parameters are 0
    - Call applyPrimaryFilter
    - Call applySecondaryValidation
    - Handle exceptions with fail-safe approach
    - Add server code to all log messages
    - _Requirements: 1.7, 4.3, 6.5_

- [x] 7. Checkpoint - Ensure FileValidator tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Integrate FileValidator with FileCollectionScheduler
  - [x] 8.1 Inject FileValidator into FileCollectionScheduler
    - Add FileValidator as dependency
    - _Requirements: 2.1, 3.2_
  
  - [x] 8.2 Modify processConfiguration method
    - Call `fileValidator.validateFiles()` after listing files
    - Skip processing if no eligible files
    - Log eligible file count
    - Pass eligible files to processFile loop
    - _Requirements: 2.1, 3.2_

- [x] 9. Write unit tests for FileValidator
  - [x] 9.1 Test backward compatibility scenarios
    - Test both parameters = 0 processes all files
    - Test null parameters treated as 0
    - _Requirements: 1.7, 4.1, 4.2, 4.3_
  
  - [x] 9.2 Test primary filter behavior
    - Test files younger than threshold are excluded
    - Test files older than threshold pass to secondary validation
    - Test age threshold = 0 skips primary filter
    - _Requirements: 2.3, 2.4, 2.5_
  
  - [x] 9.3 Test secondary validation behavior
    - Test files with unchanged metadata are eligible
    - Test files with changed lastModified are excluded
    - Test files with changed size are excluded
    - Test disappeared files are excluded
    - Test double-check wait = 0 skips secondary validation
    - _Requirements: 3.4, 3.5, 3.6, 3.7, 3.8_
  
  - [x] 9.4 Test edge case handling with UTC normalization
    - Test null timestamp exclusion
    - Test future timestamp within 24h tolerance treated as age 0
    - Test future timestamp beyond 24h tolerance excluded (age -1)
    - Test negative size exclusion
    - Test negative age threshold normalized to 0
    - Test negative double-check wait normalized to 0
    - Test UTC normalization with different timezone timestamps
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2_
  
  - [x] 9.5 Test error handling
    - Test SFTP connection error during re-check excludes all files
    - Test exception in validation excludes all files
    - _Requirements: 5.7_
  
  - [x] 9.6 Test logging behavior
    - Verify DEBUG logs for primary filter exclusions
    - Verify INFO logs for secondary validation results
    - Verify WARN logs for edge cases
    - Verify ERROR logs for SFTP failures
    - Verify server code included in all log messages
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [ ]* 10. Write property-based tests for FileValidator
  - [ ]* 10.1 Property test for validation parameters
    - **Property 1: Validation parameters accept non-negative values**
    - **Validates: Requirements 1.3, 1.4**
    - Generate random non-negative integers for both parameters
    - Verify Server entity accepts values without errors
  
  - [ ]* 10.2 Property test for file age calculation with UTC normalization
    - **Property 2: File age calculation with UTC normalization**
    - **Validates: Requirements 2.2, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2**
    - Generate random timestamps and current times
    - Verify both timestamps converted to UTC before calculation
    - Verify age = currentTimeUTC_millis - lastModifiedUTC_millis
    - Verify future timestamps within 24h tolerance return age 0
    - Verify future timestamps beyond 24h tolerance return age -1
    - Verify null timestamps return age -1
  
  - [ ]* 10.3 Property test for primary filter exclusion
    - **Property 3: Primary filter excludes young files**
    - **Validates: Requirements 2.3**
    - Generate random age thresholds and file ages where fileAge < threshold
    - Verify file is excluded by primary filter
  
  - [ ]* 10.4 Property test for primary filter passing
    - **Property 4: Primary filter passes old files**
    - **Validates: Requirements 2.4, 3.1**
    - Generate random age thresholds and file ages where fileAge >= threshold
    - Verify file passes to secondary validation
    - Verify metadata snapshot is stored
  
  - [ ]* 10.5 Property test for metadata change detection
    - **Property 5: Metadata comparison detects changes**
    - **Validates: Requirements 3.4, 3.5**
    - Generate random file metadata pairs with different timestamp or size
    - Verify `matches()` returns false
  
  - [ ]* 10.6 Property test for metadata unchanged confirmation
    - **Property 6: Metadata comparison confirms unchanged files**
    - **Validates: Requirements 3.4, 3.6**
    - Generate random file metadata and create identical copy
    - Verify `matches()` returns true

- [ ]* 11. Write E2E tests for validation flow
  - [ ]* 11.1 Test full hybrid validation with SFTP mock
    - Set up SFTP mock server with test files
    - Configure age threshold and double-check wait
    - Verify files pass both validation stages
    - _Requirements: 2.1, 3.2_
  
  - [ ]* 11.2 Test file upload in progress detection
    - Create file with initial metadata
    - Modify file during double-check wait period
    - Verify file is excluded by secondary validation
    - _Requirements: 3.4, 3.5_
  
  - [ ]* 11.3 Test multiple servers with different thresholds
    - Configure multiple servers with different validation parameters
    - Verify each server uses its own thresholds
    - _Requirements: 1.1, 1.2_
  
  - [ ]* 11.4 Test backward compatibility with existing configurations
    - Test servers with null validation parameters
    - Verify files are processed without validation
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at reasonable breaks
- Property tests validate universal correctness properties across random inputs
- Unit tests validate specific examples and edge cases
- E2E tests validate full integration with SFTP and scheduler
- Implementation uses Java 21 with Spring Boot 3.4.0
- Database migration should be created as SQL script for Oracle
- All logging uses SLF4J with appropriate levels (DEBUG, INFO, WARN, ERROR)
