# Requirements Document

## Introduction

This feature adds a hybrid validation approach to prevent processing files that are still being uploaded to SFTP servers. The system combines two validation strategies: (1) a configurable minimum age threshold as a primary filter to quickly exclude recently modified files, and (2) a double-check validation that re-examines files after a short wait period to detect ongoing writes. This hybrid approach minimizes issues with timezone discrepancies, null timestamps, and clock skew while maintaining efficiency by only double-checking files that pass the initial age threshold.

## Glossary

- **File_Validator**: Component responsible for validating file eligibility using hybrid validation approach
- **Server**: JPA entity representing SFTP server configuration (existing)
- **File_Age**: Time elapsed since the file's last modification timestamp (lastModified)
- **Age_Threshold**: Configurable minimum duration (in seconds) that must elapse since lastModified before a file passes the primary filter
- **Double_Check_Wait**: Configurable duration (in seconds) to wait between initial file listing and re-check validation
- **Producer**: Scheduled component that collects files from SFTP servers
- **SFTP_Service**: Service that lists files from SFTP servers using Spring Integration
- **File_Metadata**: DTO containing filename, size, timestamp, and file type
- **Eligible_File**: A file that passes both age threshold validation and double-check validation
- **Primary_Filter**: First validation stage using Age_Threshold to exclude recently modified files
- **Secondary_Validation**: Second validation stage that re-checks file metadata after Double_Check_Wait period
- **Metadata_Snapshot**: Stored file metadata (lastModified, size) captured during initial listing
- **UTC**: Coordinated Universal Time, the timezone-neutral reference for all timestamp comparisons
- **Future_Timestamp_Tolerance**: Maximum acceptable duration (24 hours) for timestamps in the future before treating them as invalid
- **Infrastructure_Timezone**: Standard timezone (America/Sao_Paulo) configured across all local infrastructure components

## Requirements

### Requirement 1: Configure Hybrid Validation Parameters Per Server

**User Story:** As a system administrator, I want to configure both age threshold and double-check wait period for each SFTP server, so that I can prevent processing files that are still being uploaded using an efficient hybrid approach.

#### Acceptance Criteria

1. THE Server entity SHALL include a num_min_age_seconds field to store the Age_Threshold in seconds
2. THE Server entity SHALL include a num_double_check_wait_seconds field to store the Double_Check_Wait in seconds
3. THE num_min_age_seconds field SHALL accept integer values greater than or equal to 0
4. THE num_double_check_wait_seconds field SHALL accept integer values greater than or equal to 0
5. THE num_min_age_seconds field SHALL default to 0 when not explicitly configured
6. THE num_double_check_wait_seconds field SHALL default to 0 when not explicitly configured
7. WHEN both num_min_age_seconds and num_double_check_wait_seconds are 0, THE File_Validator SHALL process all files without validation

### Requirement 2: Apply Primary Filter Using Age Threshold

**User Story:** As a system operator, I want files to be filtered by age threshold as a primary validation step, so that recently modified files are quickly excluded before expensive double-check validation.

#### Acceptance Criteria

1. WHEN the SFTP_Service lists files from a server, THE File_Validator SHALL calculate File_Age for each file
2. THE File_Age calculation SHALL use the formula: current_time_millis minus file_lastModified_millis
3. WHEN File_Age is less than Age_Threshold, THE File_Validator SHALL exclude the file from further processing
4. WHEN File_Age is greater than or equal to Age_Threshold, THE File_Validator SHALL pass the file to Secondary_Validation
5. WHEN num_min_age_seconds is 0, THE File_Validator SHALL skip Primary_Filter and pass all files to Secondary_Validation
6. THE File_Validator SHALL log excluded files at DEBUG level with filename and remaining wait time

### Requirement 3: Apply Secondary Validation Using Double-Check

**User Story:** As a system operator, I want files that pass the age threshold to be double-checked after a wait period, so that files still being written are detected and excluded even when timestamps are unreliable.

#### Acceptance Criteria

1. WHEN a file passes Primary_Filter, THE File_Validator SHALL store a Metadata_Snapshot containing filename, lastModified, and size
2. WHEN num_double_check_wait_seconds is greater than 0, THE File_Validator SHALL wait for Double_Check_Wait duration
3. AFTER the wait period, THE File_Validator SHALL re-list the same files from SFTP_Service
4. THE File_Validator SHALL compare the new File_Metadata with the stored Metadata_Snapshot for each file
5. WHEN lastModified or size has changed, THE File_Validator SHALL exclude the file from processing
6. WHEN both lastModified and size are unchanged, THE File_Validator SHALL include the file as an Eligible_File
7. WHEN a file is no longer present during re-check, THE File_Validator SHALL exclude it from processing
8. WHEN num_double_check_wait_seconds is 0, THE File_Validator SHALL skip Secondary_Validation and treat files passing Primary_Filter as Eligible_Files
9. THE File_Validator SHALL log files excluded by Secondary_Validation at INFO level with change details

**User Story:** As a system maintainer, I want existing server configurations to continue working without modification, so that deployment does not break existing integrations.

#### Acceptance Criteria

1. WHEN num_min_age_seconds is NULL in the database, THE File_Validator SHALL treat it as 0
2. WHEN num_min_age_seconds is 0, THE File_Validator SHALL process files using existing behavior (no age validation)
3. THE Server entity migration SHALL add num_min_age_seconds as a nullable column with default value 0

### Requirement 4: Maintain Backward Compatibility

**User Story:** As a system maintainer, I want existing server configurations to continue working without modification, so that deployment does not break existing integrations.

#### Acceptance Criteria

1. WHEN num_min_age_seconds is NULL in the database, THE File_Validator SHALL treat it as 0
2. WHEN num_double_check_wait_seconds is NULL in the database, THE File_Validator SHALL treat it as 0
3. WHEN both validation parameters are 0, THE File_Validator SHALL process files using existing behavior (no validation)
4. THE Server entity migration SHALL add num_min_age_seconds as a nullable column with default value 0
5. THE Server entity migration SHALL add num_double_check_wait_seconds as a nullable column with default value 0

### Requirement 5: Normalize Timestamps to UTC for Age Calculation

**User Story:** As a system operator, I want all timestamp comparisons to use UTC normalization, so that file age calculations are consistent regardless of timezone differences between SFTP servers and the Producer application.

#### Acceptance Criteria

1. WHEN calculating File_Age, THE File_Validator SHALL convert the file's lastModified timestamp to UTC
2. WHEN calculating File_Age, THE File_Validator SHALL convert the current system time to UTC
3. THE File_Age calculation SHALL use the formula: current_time_utc_millis minus file_lastModified_utc_millis
4. THE File_Validator SHALL use millisecond precision for all time calculations
5. WHEN a file's lastModified timestamp cannot be converted to UTC, THE File_Validator SHALL exclude the file and log a warning

### Requirement 6: Handle Future Timestamps with Tolerance Window

**User Story:** As a system operator, I want files with timestamps slightly in the future to be accepted within a tolerance window, so that minor clock skew between systems does not cause files to be blocked indefinitely.

#### Acceptance Criteria

1. WHEN a file's lastModified timestamp is in the future by less than or equal to 24 hours, THE File_Validator SHALL calculate File_Age as 0
2. WHEN a file's lastModified timestamp is in the future by more than 24 hours, THE File_Validator SHALL exclude the file and log a warning
3. THE Future_Timestamp_Tolerance SHALL be configurable as a constant value of 24 hours (86400 seconds)
4. THE File_Validator SHALL log files with future timestamps at INFO level with the timestamp offset in hours

### Requirement 7: Handle Edge Cases in Hybrid Validation

**User Story:** As a developer, I want the hybrid validation to handle edge cases correctly, so that the system behaves predictably under all conditions.

#### Acceptance Criteria

1. WHEN Age_Threshold is configured with a negative value, THE File_Validator SHALL treat it as 0
2. WHEN Double_Check_Wait is configured with a negative value, THE File_Validator SHALL treat it as 0
3. WHEN File_Metadata timestamp is NULL during Primary_Filter, THE File_Validator SHALL exclude the file and log a warning
4. WHEN File_Metadata size is NULL or negative, THE File_Validator SHALL exclude the file and log a warning
5. WHEN Secondary_Validation encounters an SFTP connection error during re-check, THE File_Validator SHALL exclude all pending files and log an error

### Requirement 8: Configure Infrastructure Timezone Consistency

**User Story:** As a system administrator, I want all local infrastructure components to use the same timezone, so that timestamp handling is consistent across the entire system.

#### Acceptance Criteria

1. THE Oracle database container SHALL be configured with TZ environment variable set to 'America/Sao_Paulo'
2. THE RabbitMQ container SHALL be configured with TZ environment variable set to 'America/Sao_Paulo'
3. THE Producer container SHALL be configured with TZ environment variable set to 'America/Sao_Paulo'
4. THE Consumer container SHALL be configured with TZ environment variable set to 'America/Sao_Paulo'
5. THE docker-compose.yml file SHALL define TZ='America/Sao_Paulo' for all service containers
6. THE Dockerfile for Producer SHALL set TZ environment variable to 'America/Sao_Paulo'
7. THE Dockerfile for Consumer SHALL set TZ environment variable to 'America/Sao_Paulo'

### Requirement 9: Provide Observability for Validation Process

**User Story:** As a system operator, I want to observe how files are being filtered by the hybrid validation, so that I can tune the thresholds appropriately and troubleshoot issues.

#### Acceptance Criteria

1. WHEN a file is excluded by Primary_Filter, THE File_Validator SHALL log the exclusion at DEBUG level with filename and remaining wait time
2. WHEN a file is excluded by Secondary_Validation, THE File_Validator SHALL log the exclusion at INFO level with filename and change details (lastModified or size change)
3. WHEN all files in a directory are excluded by Primary_Filter, THE File_Validator SHALL log a summary at INFO level
4. WHEN Secondary_Validation completes successfully, THE File_Validator SHALL log the count of Eligible_Files at INFO level
5. THE File_Validator SHALL include server codServer in all log messages for traceability
6. THE log message for Primary_Filter exclusions SHALL include File_Age in seconds and Age_Threshold in seconds
