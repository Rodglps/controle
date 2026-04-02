package com.concil.edi.producer.scheduler;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.ServerPathInOut;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.ServerPathInOutRepository;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import com.concil.edi.producer.service.ConfigurationService;
import com.concil.edi.producer.service.FileRegistrationService;
import com.concil.edi.producer.service.FileValidator;
import com.concil.edi.producer.service.MessagePublisherService;
import com.concil.edi.producer.service.SftpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileCollectionScheduler.
 * Tests complete collection cycle, retry logic, and error handling.
 * 
 * **Validates: Requirements 19.3**
 */
@ExtendWith(MockitoExtension.class)
class FileCollectionSchedulerTest {
    
    @Mock
    private ConfigurationService configurationService;
    
    @Mock
    private SftpService sftpService;
    
    @Mock
    private FileValidator fileValidator;
    
    @Mock
    private FileRegistrationService fileRegistrationService;
    
    @Mock
    private MessagePublisherService messagePublisherService;
    
    @Mock
    private FileOriginRepository fileOriginRepository;
    
    @Mock
    private ServerPathInOutRepository serverPathInOutRepository;
    
    private FileCollectionScheduler scheduler;
    
    @BeforeEach
    void setUp() {
        scheduler = new FileCollectionScheduler(
            configurationService,
            sftpService,
            fileValidator,
            fileRegistrationService,
            messagePublisherService,
            fileOriginRepository,
            serverPathInOutRepository
        );
    }
    
    /**
     * Test complete collection cycle with multiple files.
     * Validates that all files are processed correctly from SFTP listing to message publishing.
     */
    @Test
    void collectFiles_WithMultipleFiles_ProcessesAllSuccessfully() {
        // Arrange
        ServerConfigurationDTO config = createTestConfiguration();
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        List<FileMetadataDTO> files = List.of(
            createFileMetadata("file1.csv", 1000L, 1),
            createFileMetadata("file2.json", 2000L, 2),
            createFileMetadata("file3.txt", 3000L, 3)
        );
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // No duplicates
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // Mock successful registration
        when(fileRegistrationService.registerFile(any(), any()))
            .thenReturn(createFileOrigin(1L, "file1.csv"))
            .thenReturn(createFileOrigin(2L, "file2.json"))
            .thenReturn(createFileOrigin(3L, "file3.txt"));
        
        // Mock successful publishing
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert
        verify(configurationService, times(1)).loadActiveConfigurations();
        verify(sftpService, times(1)).listFiles(eq(config));
        verify(fileValidator, times(1)).validateFiles(eq(files), eq(config));
        verify(fileRegistrationService, times(3)).fileExists(any(), any(), any());
        verify(fileRegistrationService, times(3)).registerFile(any(), any());
        verify(messagePublisherService, times(3)).publishFileTransferMessage(any());
    }
    
    /**
     * Test retry of failed publications.
     * Validates that files with ERRO status and num_retry < max_retry are retried.
     */
    @Test
    void collectFiles_WithFailedPublications_RetriesSuccessfully() {
        // Arrange
        FileOrigin failedFile1 = createFailedFileOrigin(1L, "failed1.csv", 1, 5);
        FileOrigin failedFile2 = createFailedFileOrigin(2L, "failed2.csv", 2, 5);
        
        when(fileOriginRepository.findFailedPublications(Step.COLETA, Status.ERRO))
            .thenReturn(List.of(failedFile1, failedFile2));
        
        // Mock ServerPathInOut for building messages
        ServerPathInOut mapping = createServerPathInOutMapping(100L, 200L);
        when(serverPathInOutRepository.findById(any())).thenReturn(Optional.of(mapping));
        
        // Mock successful publishing on retry
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No new files to process
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - Both failed publications should be retried
        verify(messagePublisherService, times(2)).publishFileTransferMessage(any());
        
        // Assert - Both files should be updated to EM_ESPERA status
        verify(fileOriginRepository, times(2)).save(argThat(fileOrigin -> 
            fileOrigin.getDesStatus() == Status.EM_ESPERA
        ));
    }
    
    /**
     * Test continuation after error in one file.
     * Validates that processing continues with next file when one file fails.
     */
    @Test
    void collectFiles_WhenOneFileFails_ContinuesWithNextFile() {
        // Arrange
        ServerConfigurationDTO config = createTestConfiguration();
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        List<FileMetadataDTO> files = List.of(
            createFileMetadata("file1.csv", 1000L, 1),
            createFileMetadata("file2.csv", 2000L, 2),
            createFileMetadata("file3.csv", 3000L, 3)
        );
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // No duplicates
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // First file registration fails, others succeed
        when(fileRegistrationService.registerFile(any(), any()))
            .thenThrow(new RuntimeException("Registration failed for file1"))
            .thenReturn(createFileOrigin(2L, "file2.csv"))
            .thenReturn(createFileOrigin(3L, "file3.csv"));
        
        // Mock successful publishing
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - All files should be attempted
        verify(fileRegistrationService, times(3)).registerFile(any(), any());
        
        // Assert - Only 2 messages should be published (file1 failed)
        verify(messagePublisherService, times(2)).publishFileTransferMessage(any());
    }
    
    /**
     * Test behavior when num_retry < max_retry.
     * Validates that files are retried when they haven't reached max retry limit.
     */
    @Test
    void retryFailedPublications_WhenNumRetryLessThanMaxRetry_RetriesFile() {
        // Arrange
        FileOrigin failedFile = createFailedFileOrigin(1L, "failed.csv", 2, 5);
        
        when(fileOriginRepository.findFailedPublications(Step.COLETA, Status.ERRO))
            .thenReturn(List.of(failedFile));
        
        // Mock ServerPathInOut for building messages
        ServerPathInOut mapping = createServerPathInOutMapping(100L, 200L);
        when(serverPathInOutRepository.findById(any())).thenReturn(Optional.of(mapping));
        
        // Mock successful publishing on retry
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No new files to process
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - File should be retried
        ArgumentCaptor<FileTransferMessageDTO> messageCaptor = 
            ArgumentCaptor.forClass(FileTransferMessageDTO.class);
        verify(messagePublisherService).publishFileTransferMessage(messageCaptor.capture());
        
        FileTransferMessageDTO capturedMessage = messageCaptor.getValue();
        assertEquals(1L, capturedMessage.getIdtFileOrigin());
        assertEquals("failed.csv", capturedMessage.getFilename());
        
        // Assert - File status should be updated to EM_ESPERA
        ArgumentCaptor<FileOrigin> fileOriginCaptor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(fileOriginCaptor.capture());
        
        FileOrigin savedFile = fileOriginCaptor.getValue();
        assertEquals(Status.EM_ESPERA, savedFile.getDesStatus());
        assertNull(savedFile.getDesMessageError());
    }
    
    /**
     * Test that files at max retry are not retried.
     * Validates that files with num_retry >= max_retry are not included in retry logic.
     */
    @Test
    void retryFailedPublications_WhenNumRetryEqualsMaxRetry_DoesNotRetry() {
        // Arrange
        FileOrigin maxRetriedFile = createFailedFileOrigin(1L, "maxed.csv", 5, 5);
        
        // Repository should not return files at max retry
        when(fileOriginRepository.findFailedPublications(Step.COLETA, Status.ERRO))
            .thenReturn(List.of()); // Empty because num_retry >= max_retry
        
        // No new files to process
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - No retry attempts should be made
        verify(messagePublisherService, never()).publishFileTransferMessage(any());
        verify(fileOriginRepository, never()).save(any());
    }
    
    /**
     * Test handling of duplicate files.
     * Validates that duplicate files are skipped and not registered again.
     */
    @Test
    void collectFiles_WithDuplicateFile_SkipsRegistration() {
        // Arrange
        ServerConfigurationDTO config = createTestConfiguration();
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        List<FileMetadataDTO> files = List.of(
            createFileMetadata("duplicate.csv", 1000L, 1),
            createFileMetadata("new.csv", 2000L, 2)
        );
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // First file is duplicate, second is new
        when(fileRegistrationService.fileExists(eq("duplicate.csv"), any(), any())).thenReturn(true);
        when(fileRegistrationService.fileExists(eq("new.csv"), any(), any())).thenReturn(false);
        
        // Mock successful registration for new file
        when(fileRegistrationService.registerFile(any(), any()))
            .thenReturn(createFileOrigin(2L, "new.csv"));
        
        // Mock successful publishing
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - Only one file should be registered (duplicate skipped)
        verify(fileRegistrationService, times(1)).registerFile(any(), any());
        verify(messagePublisherService, times(1)).publishFileTransferMessage(any());
    }
    
    /**
     * Test handling of empty file list.
     * Validates that scheduler handles empty SFTP directories gracefully.
     */
    @Test
    void collectFiles_WithNoFiles_CompletesWithoutError() {
        // Arrange
        ServerConfigurationDTO config = createTestConfiguration();
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        // Empty file list
        when(sftpService.listFiles(eq(config))).thenReturn(List.of());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - No registration or publishing should occur
        verify(fileRegistrationService, never()).registerFile(any(), any());
        verify(messagePublisherService, never()).publishFileTransferMessage(any());
    }
    
    /**
     * Test handling of SFTP connection failure.
     * Validates that scheduler continues with other configurations when SFTP fails.
     */
    @Test
    void collectFiles_WhenSftpFails_ContinuesWithNextConfiguration() {
        // Arrange
        ServerConfigurationDTO config1 = createTestConfiguration();
        config1.setCodServer("SFTP_CONFIG_1");
        
        ServerConfigurationDTO config2 = createTestConfiguration();
        config2.setCodServer("SFTP_CONFIG_2");
        
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config1, config2));
        
        // First config fails, second succeeds
        when(sftpService.listFiles(eq(config1)))
            .thenThrow(new RuntimeException("SFTP connection failed"));
        
        List<FileMetadataDTO> files = List.of(createFileMetadata("file.csv", 1000L, 1));
        when(sftpService.listFiles(eq(config2))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config2))).thenReturn(files);
        
        // No duplicates
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // Mock successful registration
        when(fileRegistrationService.registerFile(any(), any()))
            .thenReturn(createFileOrigin(1L, "file.csv"));
        
        // Mock successful publishing
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - Both configurations should be attempted
        verify(sftpService, times(1)).listFiles(eq(config1));
        verify(sftpService, times(1)).listFiles(eq(config2));
        
        // Assert - Second configuration should process successfully
        verify(fileRegistrationService, times(1)).registerFile(any(), any());
        verify(messagePublisherService, times(1)).publishFileTransferMessage(any());
    }
    
    /**
     * Test message publishing failure handling.
     * Validates that publishing failures are handled and don't stop processing.
     */
    @Test
    void collectFiles_WhenPublishingFails_ContinuesWithNextFile() {
        // Arrange
        ServerConfigurationDTO config = createTestConfiguration();
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        List<FileMetadataDTO> files = List.of(
            createFileMetadata("file1.csv", 1000L, 1),
            createFileMetadata("file2.csv", 2000L, 2)
        );
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // No duplicates
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // Mock successful registration
        when(fileRegistrationService.registerFile(any(), any()))
            .thenReturn(createFileOrigin(1L, "file1.csv"))
            .thenReturn(createFileOrigin(2L, "file2.csv"));
        
        // First publish fails, second succeeds
        doThrow(new RuntimeException("Publishing failed"))
            .doNothing()
            .when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        // Act
        scheduler.collectFiles();
        
        // Assert - Both files should be registered
        verify(fileRegistrationService, times(2)).registerFile(any(), any());
        
        // Assert - Both publishing attempts should be made
        verify(messagePublisherService, times(2)).publishFileTransferMessage(any());
    }
    
    // Helper methods
    
    private ServerConfigurationDTO createTestConfiguration() {
        ServerConfigurationDTO config = new ServerConfigurationDTO();
        config.setServerId(1L);
        config.setCodServer("SFTP_TEST");
        config.setCodVault("test_vault");
        config.setDesVaultSecret("test_secret");
        config.setServerPathOriginId(100L);
        config.setServerPathDestinationId(200L);
        config.setServerPathInOutId(300L);
        config.setOriginPath("/test/origin");
        config.setAcquirerId(1L);
        return config;
    }
    
    private FileMetadataDTO createFileMetadata(String filename, Long size, int timeOffset) {
        return new FileMetadataDTO(
            filename,
            size,
            new Timestamp(System.currentTimeMillis() + timeOffset * 1000L),
            FileType.csv
        );
    }
    
    private FileOrigin createFileOrigin(Long id, String filename) {
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(id);
        fileOrigin.setDesFileName(filename);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.EM_ESPERA);
        fileOrigin.setNumRetry(0);
        fileOrigin.setMaxRetry(5);
        fileOrigin.setFlgActive(1);
        fileOrigin.setIdtSeverPathsInOut(300L);
        return fileOrigin;
    }
    
    private FileOrigin createFailedFileOrigin(Long id, String filename, int numRetry, int maxRetry) {
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(id);
        fileOrigin.setDesFileName(filename);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setNumRetry(numRetry);
        fileOrigin.setMaxRetry(maxRetry);
        fileOrigin.setFlgActive(1);
        fileOrigin.setIdtSeverPathsInOut(300L);
        fileOrigin.setDesMessageError("Previous error");
        return fileOrigin;
    }
    
    private ServerPathInOut createServerPathInOutMapping(Long originId, Long destinationId) {
        ServerPathInOut mapping = new ServerPathInOut();
        mapping.setIdtSeverPathsInOut(300L);
        
        ServerPath originPath = new ServerPath();
        originPath.setIdtSeverPath(originId);
        mapping.setSeverPathOrigin(originPath);
        
        ServerPath destPath = new ServerPath();
        destPath.setIdtSeverPath(destinationId);
        mapping.setSeverPathDestination(destPath);
        
        return mapping;
    }
}
