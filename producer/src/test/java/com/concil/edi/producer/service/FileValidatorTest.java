package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileValidator.
 * Tests backward compatibility, primary filter, secondary validation, edge cases, error handling, and logging.
 */
@ExtendWith(MockitoExtension.class)
class FileValidatorTest {
    
    @Mock
    private SftpService sftpService;
    
    private FileValidator fileValidator;
    
    @BeforeEach
    void setUp() {
        fileValidator = new FileValidator(sftpService);
    }
    
    // ========== 9.1 Test backward compatibility scenarios ==========
    
    /**
     * Test: Both parameters = 0 processes all files (backward compatibility)
     * Requirements: 1.7, 4.1, 4.2, 4.3
     */
    @Test
    void testBothParametersZero_ProcessesAllFiles() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 0, 0);
        List<FileMetadataDTO> files = createFileList(3);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(3, result.size(), "All files should be processed when both parameters are 0");
        assertEquals(files, result, "Should return the same file list");
        verifyNoInteractions(sftpService); // No SFTP re-listing should occur
    }
    
    /**
     * Test: Null parameters treated as 0 (backward compatibility)
     * Requirements: 4.1, 4.2, 4.3
     */
    @Test
    void testNullParameters_TreatedAsZero() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", null, null);
        List<FileMetadataDTO> files = createFileList(2);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(2, result.size(), "All files should be processed when parameters are null");
        verifyNoInteractions(sftpService);
    }
    
    // ========== 9.2 Test primary filter behavior ==========
    
    /**
     * Test: Files younger than threshold are excluded
     * Requirements: 2.3, 2.4
     */
    @Test
    void testPrimaryFilter_ExcludesYoungFiles() {
        // Arrange
        int ageThresholdSeconds = 120; // 2 minutes
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, 0);
        
        long currentTime = System.currentTimeMillis();
        long youngFileTime = currentTime - (60 * 1000); // 1 minute ago (younger than threshold)
        long oldFileTime = currentTime - (180 * 1000); // 3 minutes ago (older than threshold)
        
        List<FileMetadataDTO> files = List.of(
            createFile("young_file.csv", 1000L, new Timestamp(youngFileTime)),
            createFile("old_file.csv", 2000L, new Timestamp(oldFileTime))
        );
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(1, result.size(), "Only old file should pass");
        assertEquals("old_file.csv", result.get(0).getFilename());
    }
    
    /**
     * Test: Files older than threshold pass to secondary validation
     * Requirements: 2.4, 2.5
     */
    @Test
    void testPrimaryFilter_PassesOldFiles() {
        // Arrange
        int ageThresholdSeconds = 60; // 1 minute
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, 0);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000); // 2 minutes ago
        
        List<FileMetadataDTO> files = List.of(
            createFile("old_file1.csv", 1000L, new Timestamp(oldFileTime)),
            createFile("old_file2.csv", 2000L, new Timestamp(oldFileTime))
        );
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(2, result.size(), "Both old files should pass");
    }
    
    /**
     * Test: Age threshold = 0 skips primary filter
     * Requirements: 2.5
     */
    @Test
    void testPrimaryFilter_SkipsWhenThresholdIsZero() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 0, 10);
        
        long currentTime = System.currentTimeMillis();
        long veryRecentTime = currentTime - 1000; // 1 second ago
        
        List<FileMetadataDTO> files = List.of(
            createFile("recent_file.csv", 1000L, new Timestamp(veryRecentTime))
        );
        
        // Mock SFTP re-listing for secondary validation
        when(sftpService.listFiles(any())).thenReturn(files);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(1, result.size(), "File should pass when age threshold is 0");
        verify(sftpService).listFiles(any()); // Secondary validation should occur
    }
    
    // ========== 9.3 Test secondary validation behavior ==========
    
    /**
     * Test: Files with unchanged metadata are eligible
     * Requirements: 3.4, 3.6
     */
    @Test
    void testSecondaryValidation_PassesUnchangedFiles() throws Exception {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1; // Short wait for testing
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000); // 2 minutes ago
        Timestamp timestamp = new Timestamp(oldFileTime);
        
        FileMetadataDTO file = createFile("stable_file.csv", 1000L, timestamp);
        List<FileMetadataDTO> files = List.of(file);
        
        // Mock SFTP re-listing returns same file with unchanged metadata
        when(sftpService.listFiles(any())).thenReturn(files);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(1, result.size(), "File with unchanged metadata should be eligible");
        assertEquals("stable_file.csv", result.get(0).getFilename());
        verify(sftpService).listFiles(any());
    }
    
    /**
     * Test: Files with changed lastModified are excluded
     * Requirements: 3.4, 3.5
     */
    @Test
    void testSecondaryValidation_ExcludesFilesWithChangedTimestamp() throws Exception {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        Timestamp originalTimestamp = new Timestamp(oldFileTime);
        Timestamp changedTimestamp = new Timestamp(oldFileTime + 5000); // Changed by 5 seconds
        
        FileMetadataDTO originalFile = createFile("changing_file.csv", 1000L, originalTimestamp);
        FileMetadataDTO changedFile = createFile("changing_file.csv", 1000L, changedTimestamp);
        
        List<FileMetadataDTO> files = List.of(originalFile);
        
        // Mock SFTP re-listing returns file with changed timestamp
        when(sftpService.listFiles(any())).thenReturn(List.of(changedFile));
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "File with changed timestamp should be excluded");
        verify(sftpService).listFiles(any());
    }
    
    /**
     * Test: Files with changed size are excluded
     * Requirements: 3.4, 3.5
     */
    @Test
    void testSecondaryValidation_ExcludesFilesWithChangedSize() throws Exception {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        Timestamp timestamp = new Timestamp(oldFileTime);
        
        FileMetadataDTO originalFile = createFile("growing_file.csv", 1000L, timestamp);
        FileMetadataDTO changedFile = createFile("growing_file.csv", 2000L, timestamp); // Size changed
        
        List<FileMetadataDTO> files = List.of(originalFile);
        
        // Mock SFTP re-listing returns file with changed size
        when(sftpService.listFiles(any())).thenReturn(List.of(changedFile));
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "File with changed size should be excluded");
        verify(sftpService).listFiles(any());
    }
    
    /**
     * Test: Disappeared files are excluded
     * Requirements: 3.7
     */
    @Test
    void testSecondaryValidation_ExcludesDisappearedFiles() throws Exception {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        
        FileMetadataDTO file = createFile("disappearing_file.csv", 1000L, new Timestamp(oldFileTime));
        List<FileMetadataDTO> files = List.of(file);
        
        // Mock SFTP re-listing returns empty list (file disappeared)
        when(sftpService.listFiles(any())).thenReturn(new ArrayList<>());
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "Disappeared file should be excluded");
        verify(sftpService).listFiles(any());
    }
    
    /**
     * Test: Double-check wait = 0 skips secondary validation
     * Requirements: 3.8
     */
    @Test
    void testSecondaryValidation_SkipsWhenWaitIsZero() {
        // Arrange
        int ageThresholdSeconds = 60;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, 0);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        
        List<FileMetadataDTO> files = List.of(
            createFile("file.csv", 1000L, new Timestamp(oldFileTime))
        );
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(1, result.size(), "File should pass when double-check wait is 0");
        verifyNoInteractions(sftpService); // No SFTP re-listing should occur
    }
    
    // ========== 9.4 Test edge case handling with UTC normalization ==========
    
    /**
     * Test: Null timestamp exclusion
     * Requirements: 5.4
     */
    @Test
    void testEdgeCase_ExcludesNullTimestamp() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 60, 0);
        
        FileMetadataDTO fileWithNullTimestamp = createFile("null_timestamp.csv", 1000L, null);
        List<FileMetadataDTO> files = List.of(fileWithNullTimestamp);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "File with null timestamp should be excluded");
    }
    
    /**
     * Test: Future timestamp within 24h tolerance treated as age 0
     * Requirements: 6.1
     */
    @Test
    void testEdgeCase_FutureTimestampWithinTolerance_TreatedAsAgeZero() {
        // Arrange
        int ageThresholdSeconds = 60;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, 0);
        
        long currentTime = System.currentTimeMillis();
        long futureTime = currentTime + (2 * 60 * 60 * 1000); // 2 hours in the future (within 24h)
        
        FileMetadataDTO futureFile = createFile("future_file.csv", 1000L, new Timestamp(futureTime));
        List<FileMetadataDTO> files = List.of(futureFile);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "Future file within tolerance should be treated as age 0 and excluded by threshold");
    }
    
    /**
     * Test: Future timestamp beyond 24h tolerance excluded (age -1)
     * Requirements: 6.2
     */
    @Test
    void testEdgeCase_FutureTimestampBeyondTolerance_Excluded() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 60, 0);
        
        long currentTime = System.currentTimeMillis();
        long futureTime = currentTime + (30 * 60 * 60 * 1000); // 30 hours in the future (beyond 24h)
        
        FileMetadataDTO futureFile = createFile("far_future_file.csv", 1000L, new Timestamp(futureTime));
        List<FileMetadataDTO> files = List.of(futureFile);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "File with future timestamp beyond 24h should be excluded");
    }
    
    /**
     * Test: Negative size exclusion
     * Requirements: 5.5
     */
    @Test
    void testEdgeCase_ExcludesNegativeSize() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 60, 0);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        
        FileMetadataDTO fileWithNegativeSize = createFile("negative_size.csv", -100L, new Timestamp(oldFileTime));
        List<FileMetadataDTO> files = List.of(fileWithNegativeSize);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "File with negative size should be excluded");
    }
    
    /**
     * Test: Negative age threshold normalized to 0
     * Requirements: 5.2
     */
    @Test
    void testEdgeCase_NegativeAgeThreshold_NormalizedToZero() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", -100, 0);
        List<FileMetadataDTO> files = createFileList(2);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(2, result.size(), "Negative age threshold should be normalized to 0 and process all files");
    }
    
    /**
     * Test: Negative double-check wait normalized to 0
     * Requirements: 5.3
     */
    @Test
    void testEdgeCase_NegativeDoubleCheckWait_NormalizedToZero() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 0, -50);
        List<FileMetadataDTO> files = createFileList(2);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(2, result.size(), "Negative double-check wait should be normalized to 0");
        verifyNoInteractions(sftpService); // No re-listing should occur
    }
    
    /**
     * Test: UTC normalization with different timezone timestamps
     * Requirements: 5.1, 5.2, 5.3
     */
    @Test
    void testEdgeCase_UTCNormalization_WithDifferentTimezones() {
        // Arrange
        int ageThresholdSeconds = 60;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, 0);
        
        // Create timestamps in different timezones but representing the same instant
        long currentTime = System.currentTimeMillis();
        long oldTime = currentTime - (120 * 1000); // 2 minutes ago
        
        // Create timestamp from different timezone (America/Sao_Paulo)
        ZonedDateTime saoPauloTime = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(oldTime), 
            ZoneId.of("America/Sao_Paulo")
        );
        Timestamp timestamp = Timestamp.from(saoPauloTime.toInstant());
        
        FileMetadataDTO file = createFile("timezone_file.csv", 1000L, timestamp);
        List<FileMetadataDTO> files = List.of(file);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(1, result.size(), "File should pass regardless of timezone (UTC normalization)");
    }
    
    // ========== 9.5 Test error handling ==========
    
    /**
     * Test: SFTP connection error during re-check excludes all files
     * Requirements: 5.7
     */
    @Test
    void testErrorHandling_SFTPConnectionError_ExcludesAllFiles() {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        
        List<FileMetadataDTO> files = List.of(
            createFile("file1.csv", 1000L, new Timestamp(oldFileTime)),
            createFile("file2.csv", 2000L, new Timestamp(oldFileTime))
        );
        
        // Mock SFTP re-listing throws exception
        when(sftpService.listFiles(any())).thenThrow(new RuntimeException("SFTP connection failed"));
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "All files should be excluded on SFTP error");
        verify(sftpService).listFiles(any());
    }
    
    /**
     * Test: Exception in validation excludes all files
     * Requirements: 5.7
     */
    @Test
    void testErrorHandling_ValidationException_ExcludesAllFiles() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 60, 1);
        
        // Create a file that will cause issues during validation
        List<FileMetadataDTO> files = null; // This will cause NullPointerException
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "All files should be excluded on validation exception");
    }
    
    // ========== 9.6 Test logging behavior ==========
    // Note: These tests verify the behavior that would produce logs, not the logs themselves
    // In a real scenario, you might use a logging framework test appender to verify actual log messages
    
    /**
     * Test: Verify DEBUG logs for primary filter exclusions
     * Requirements: 6.1, 6.6
     */
    @Test
    void testLogging_PrimaryFilterExclusions() {
        // Arrange
        int ageThresholdSeconds = 120;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, 0);
        
        long currentTime = System.currentTimeMillis();
        long youngFileTime = currentTime - (30 * 1000); // 30 seconds ago (younger than threshold)
        
        List<FileMetadataDTO> files = List.of(
            createFile("young_file.csv", 1000L, new Timestamp(youngFileTime))
        );
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "Young file should be excluded (DEBUG log should be generated)");
    }
    
    /**
     * Test: Verify INFO logs for secondary validation results
     * Requirements: 6.2, 6.4
     */
    @Test
    void testLogging_SecondaryValidationResults() throws Exception {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        Timestamp timestamp = new Timestamp(oldFileTime);
        
        FileMetadataDTO file = createFile("stable_file.csv", 1000L, timestamp);
        List<FileMetadataDTO> files = List.of(file);
        
        // Mock SFTP re-listing returns same file
        when(sftpService.listFiles(any())).thenReturn(files);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(1, result.size(), "File should pass (INFO log should be generated for results)");
        verify(sftpService).listFiles(any());
    }
    
    /**
     * Test: Verify WARN logs for edge cases
     * Requirements: 6.3
     */
    @Test
    void testLogging_WarnForEdgeCases() {
        // Arrange
        ServerConfigurationDTO config = createConfig("SERVER1", 60, 0);
        
        // Create files with edge cases that should generate WARN logs
        FileMetadataDTO nullTimestampFile = createFile("null_timestamp.csv", 1000L, null);
        FileMetadataDTO negativeSize = createFile("negative_size.csv", -100L, new Timestamp(System.currentTimeMillis()));
        
        long currentTime = System.currentTimeMillis();
        long farFutureTime = currentTime + (30 * 60 * 60 * 1000); // 30 hours in future
        FileMetadataDTO farFutureFile = createFile("far_future.csv", 1000L, new Timestamp(farFutureTime));
        
        List<FileMetadataDTO> files = List.of(nullTimestampFile, negativeSize, farFutureFile);
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "All edge case files should be excluded (WARN logs should be generated)");
    }
    
    /**
     * Test: Verify ERROR logs for SFTP failures
     * Requirements: 6.4
     */
    @Test
    void testLogging_ErrorForSFTPFailures() {
        // Arrange
        int ageThresholdSeconds = 60;
        int doubleCheckWaitSeconds = 1;
        ServerConfigurationDTO config = createConfig("SERVER1", ageThresholdSeconds, doubleCheckWaitSeconds);
        
        long currentTime = System.currentTimeMillis();
        long oldFileTime = currentTime - (120 * 1000);
        
        List<FileMetadataDTO> files = List.of(
            createFile("file.csv", 1000L, new Timestamp(oldFileTime))
        );
        
        // Mock SFTP failure
        when(sftpService.listFiles(any())).thenThrow(new RuntimeException("SFTP connection failed"));
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "Files should be excluded (ERROR log should be generated)");
    }
    
    /**
     * Test: Verify server code included in all log messages
     * Requirements: 6.5, 6.6
     */
    @Test
    void testLogging_ServerCodeIncludedInLogs() {
        // Arrange
        String serverCode = "TEST_SERVER_123";
        ServerConfigurationDTO config = createConfig(serverCode, 60, 0);
        
        long currentTime = System.currentTimeMillis();
        long youngFileTime = currentTime - (30 * 1000);
        
        List<FileMetadataDTO> files = List.of(
            createFile("file.csv", 1000L, new Timestamp(youngFileTime))
        );
        
        // Act
        List<FileMetadataDTO> result = fileValidator.validateFiles(files, config);
        
        // Assert
        assertEquals(0, result.size(), "File should be excluded (logs should include server code: " + serverCode + ")");
    }
    
    // ========== Helper methods ==========
    
    /**
     * Create a ServerConfigurationDTO for testing
     */
    private ServerConfigurationDTO createConfig(String codServer, Integer minAgeSeconds, Integer doubleCheckWaitSeconds) {
        ServerConfigurationDTO config = new ServerConfigurationDTO();
        config.setCodServer(codServer);
        config.setMinAgeSeconds(minAgeSeconds);
        config.setDoubleCheckWaitSeconds(doubleCheckWaitSeconds);
        config.setServerType(ServerType.SFTP);
        config.setServerId(1L);
        config.setOriginPath("/test/path");
        config.setServerPathOriginId(1L);
        config.setServerPathDestinationId(2L);
        config.setServerPathInOutId(1L);
        config.setAcquirerId(1L);
        return config;
    }
    
    /**
     * Create a FileMetadataDTO for testing
     */
    private FileMetadataDTO createFile(String filename, Long size, Timestamp timestamp) {
        return new FileMetadataDTO(filename, size, timestamp, FileType.csv);
    }
    
    /**
     * Create a list of test files with old timestamps
     */
    private List<FileMetadataDTO> createFileList(int count) {
        List<FileMetadataDTO> files = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        long oldTime = currentTime - (300 * 1000); // 5 minutes ago
        
        for (int i = 0; i < count; i++) {
            files.add(createFile("file" + i + ".csv", 1000L + i, new Timestamp(oldTime)));
        }
        
        return files;
    }
}
