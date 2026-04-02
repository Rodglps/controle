package com.concil.edi.producer.scheduler;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.entity.FileOrigin;
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
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Assertions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for FileCollectionScheduler.
 * Validates error isolation during file collection.
 * 
 * **Validates: Requirements 10.7**
 * 
 * Property 35: Error isolation
 */
class FileCollectionSchedulerPropertyTest {
    
    /**
     * Property 35: Error isolation
     * 
     * GIVEN a collection of N files where some files cause exceptions during processing
     * WHEN the scheduler processes all files
     * THEN the scheduler MUST continue processing remaining files after encountering errors
     * AND the scheduler MUST process all non-failing files successfully
     * 
     * This validates Requirement 10.7:
     * - WHEN erro é tratado, THE Producer SHALL continuar processamento de outros arquivos
     * 
     * Error isolation ensures that a failure in processing one file does not prevent
     * the system from processing other files in the same collection cycle.
     */
    @Property
    @Label("Property 35: Error isolation - failures in one file do not stop processing of other files")
    void errorIsolation_FailureInOneFile_DoesNotStopOtherFiles(
        @ForAll @IntRange(min = 3, max = 10) int totalFiles,
        @ForAll @IntRange(min = 1, max = 5) int numberOfFailingFiles
    ) {
        Assume.that(numberOfFailingFiles < totalFiles);
        
        // Arrange
        ConfigurationService configurationService = mock(ConfigurationService.class);
        SftpService sftpService = mock(SftpService.class);
        FileValidator fileValidator = mock(FileValidator.class);
        FileRegistrationService fileRegistrationService = mock(FileRegistrationService.class);
        MessagePublisherService messagePublisherService = mock(MessagePublisherService.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        ServerPathInOutRepository serverPathInOutRepository = mock(ServerPathInOutRepository.class);
        
        // Create a single configuration
        ServerConfigurationDTO config = createMockConfiguration(1L);
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        // Create file metadata list
        List<FileMetadataDTO> files = new ArrayList<>();
        for (int i = 0; i < totalFiles; i++) {
            files.add(createMockFileMetadata("file" + i + ".csv", i));
        }
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // Mock fileExists to return false (no duplicates)
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // Track which files were processed
        AtomicInteger registrationAttempts = new AtomicInteger(0);
        AtomicInteger successfulRegistrations = new AtomicInteger(0);
        
        // Mock registerFile: first N files fail, rest succeed
        when(fileRegistrationService.registerFile(any(), any())).thenAnswer(invocation -> {
            int attempt = registrationAttempts.getAndIncrement();
            if (attempt < numberOfFailingFiles) {
                throw new RuntimeException("Simulated registration failure for file " + attempt);
            }
            successfulRegistrations.incrementAndGet();
            return createMockFileOrigin((long) attempt, "file" + attempt + ".csv");
        });
        
        // Mock message publisher (should only be called for successful registrations)
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        FileCollectionScheduler scheduler = new FileCollectionScheduler(
            configurationService,
            sftpService,
            fileValidator,
            fileRegistrationService,
            messagePublisherService,
            fileOriginRepository,
            serverPathInOutRepository
        );
        
        // Act
        scheduler.collectFiles();
        
        // Assert - All files should have been attempted
        Assertions.assertEquals(totalFiles, registrationAttempts.get(),
            "Scheduler MUST attempt to process all files despite failures");
        
        // Assert - Only non-failing files should have been successfully registered
        int expectedSuccessful = totalFiles - numberOfFailingFiles;
        Assertions.assertEquals(expectedSuccessful, successfulRegistrations.get(),
            "Scheduler MUST successfully process all non-failing files");
        
        // Assert - Message publisher should only be called for successful registrations
        verify(messagePublisherService, times(expectedSuccessful))
            .publishFileTransferMessage(any(FileTransferMessageDTO.class));
    }
    
    /**
     * Property 35.1: Error isolation across configurations
     * 
     * GIVEN multiple configurations where some configurations cause exceptions
     * WHEN the scheduler processes all configurations
     * THEN the scheduler MUST continue processing remaining configurations after encountering errors
     * 
     * This validates that error isolation works at the configuration level as well.
     */
    @Property
    @Label("Property 35.1: Error isolation - failures in one configuration do not stop other configurations")
    void errorIsolation_FailureInOneConfiguration_DoesNotStopOtherConfigurations(
        @ForAll @IntRange(min = 3, max = 8) int totalConfigurations,
        @ForAll @IntRange(min = 1, max = 3) int numberOfFailingConfigurations
    ) {
        Assume.that(numberOfFailingConfigurations < totalConfigurations);
        
        // Arrange
        ConfigurationService configurationService = mock(ConfigurationService.class);
        SftpService sftpService = mock(SftpService.class);
        FileValidator fileValidator = mock(FileValidator.class);
        FileRegistrationService fileRegistrationService = mock(FileRegistrationService.class);
        MessagePublisherService messagePublisherService = mock(MessagePublisherService.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        ServerPathInOutRepository serverPathInOutRepository = mock(ServerPathInOutRepository.class);
        
        // Create multiple configurations
        List<ServerConfigurationDTO> configurations = new ArrayList<>();
        for (int i = 0; i < totalConfigurations; i++) {
            configurations.add(createMockConfiguration((long) i));
        }
        when(configurationService.loadActiveConfigurations()).thenReturn(configurations);
        
        // Track SFTP service calls
        AtomicInteger sftpCallCount = new AtomicInteger(0);
        
        // Mock SFTP service: first N configurations fail, rest succeed
        when(sftpService.listFiles(any())).thenAnswer(invocation -> {
            int callIndex = sftpCallCount.getAndIncrement();
            if (callIndex < numberOfFailingConfigurations) {
                throw new RuntimeException("Simulated SFTP failure for configuration " + callIndex);
            }
            // Return one file for successful configurations
            return List.of(createMockFileMetadata("file_config" + callIndex + ".csv", callIndex));
        });
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock other services for successful processing
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        when(fileRegistrationService.registerFile(any(), any()))
            .thenAnswer(inv -> createMockFileOrigin(1L, "test.csv"));
        doNothing().when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        FileCollectionScheduler scheduler = new FileCollectionScheduler(
            configurationService,
            sftpService,
            fileValidator,
            fileRegistrationService,
            messagePublisherService,
            fileOriginRepository,
            serverPathInOutRepository
        );
        
        // Act
        scheduler.collectFiles();
        
        // Assert - All configurations should have been attempted
        Assertions.assertEquals(totalConfigurations, sftpCallCount.get(),
            "Scheduler MUST attempt to process all configurations despite failures");
        
        // Assert - Message publisher should only be called for successful configurations
        int expectedSuccessful = totalConfigurations - numberOfFailingConfigurations;
        verify(messagePublisherService, times(expectedSuccessful))
            .publishFileTransferMessage(any(FileTransferMessageDTO.class));
    }
    
    /**
     * Property 35.2: Error isolation with message publishing failures
     * 
     * GIVEN a collection of files where some files fail during message publishing
     * WHEN the scheduler processes all files
     * THEN the scheduler MUST continue processing remaining files after publishing failures
     * 
     * This validates error isolation at the message publishing stage.
     */
    @Property
    @Label("Property 35.2: Error isolation - message publishing failures do not stop other files")
    void errorIsolation_MessagePublishingFailure_DoesNotStopOtherFiles(
        @ForAll @IntRange(min = 3, max = 10) int totalFiles,
        @ForAll @IntRange(min = 1, max = 5) int numberOfPublishingFailures
    ) {
        Assume.that(numberOfPublishingFailures < totalFiles);
        
        // Arrange
        ConfigurationService configurationService = mock(ConfigurationService.class);
        SftpService sftpService = mock(SftpService.class);
        FileValidator fileValidator = mock(FileValidator.class);
        FileRegistrationService fileRegistrationService = mock(FileRegistrationService.class);
        MessagePublisherService messagePublisherService = mock(MessagePublisherService.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        ServerPathInOutRepository serverPathInOutRepository = mock(ServerPathInOutRepository.class);
        
        // Create a single configuration
        ServerConfigurationDTO config = createMockConfiguration(1L);
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        // Create file metadata list
        List<FileMetadataDTO> files = new ArrayList<>();
        for (int i = 0; i < totalFiles; i++) {
            files.add(createMockFileMetadata("file" + i + ".csv", i));
        }
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // Mock fileExists to return false (no duplicates)
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // Mock registerFile to always succeed
        when(fileRegistrationService.registerFile(any(), any()))
            .thenAnswer(inv -> {
                FileMetadataDTO metadata = inv.getArgument(0);
                return createMockFileOrigin(1L, metadata.getFilename());
            });
        
        // Track publishing attempts
        AtomicInteger publishingAttempts = new AtomicInteger(0);
        
        // Mock message publisher: first N files fail, rest succeed
        doAnswer(invocation -> {
            int attempt = publishingAttempts.getAndIncrement();
            if (attempt < numberOfPublishingFailures) {
                throw new RuntimeException("Simulated publishing failure for file " + attempt);
            }
            return null;
        }).when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        FileCollectionScheduler scheduler = new FileCollectionScheduler(
            configurationService,
            sftpService,
            fileValidator,
            fileRegistrationService,
            messagePublisherService,
            fileOriginRepository,
            serverPathInOutRepository
        );
        
        // Act
        scheduler.collectFiles();
        
        // Assert - All files should have been attempted for publishing
        Assertions.assertEquals(totalFiles, publishingAttempts.get(),
            "Scheduler MUST attempt to publish messages for all files despite failures");
        
        // Assert - All files should have been registered (registration happens before publishing)
        verify(fileRegistrationService, times(totalFiles)).registerFile(any(), any());
    }
    
    /**
     * Property 35.3: Error isolation is complete - no partial processing
     * 
     * GIVEN any number of files with any number of failures
     * WHEN the scheduler processes all files
     * THEN the total number of processing attempts MUST equal the total number of files
     * 
     * This validates that error isolation doesn't skip files or cause partial processing.
     */
    @Property
    @Label("Property 35.3: Error isolation ensures all files are attempted")
    void errorIsolation_AllFilesAreAttempted(
        @ForAll @IntRange(min = 1, max = 15) int totalFiles
    ) {
        // Arrange
        ConfigurationService configurationService = mock(ConfigurationService.class);
        SftpService sftpService = mock(SftpService.class);
        FileValidator fileValidator = mock(FileValidator.class);
        FileRegistrationService fileRegistrationService = mock(FileRegistrationService.class);
        MessagePublisherService messagePublisherService = mock(MessagePublisherService.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        ServerPathInOutRepository serverPathInOutRepository = mock(ServerPathInOutRepository.class);
        
        // Create a single configuration
        ServerConfigurationDTO config = createMockConfiguration(1L);
        when(configurationService.loadActiveConfigurations()).thenReturn(List.of(config));
        
        // Create file metadata list
        List<FileMetadataDTO> files = new ArrayList<>();
        for (int i = 0; i < totalFiles; i++) {
            files.add(createMockFileMetadata("file" + i + ".csv", i));
        }
        when(sftpService.listFiles(eq(config))).thenReturn(files);
        
        // Mock FileValidator to return all files as eligible
        when(fileValidator.validateFiles(eq(files), eq(config))).thenReturn(files);
        
        // Mock fileExists to return false (no duplicates)
        when(fileRegistrationService.fileExists(any(), any(), any())).thenReturn(false);
        
        // Track duplicate check attempts (one per file)
        AtomicInteger duplicateCheckCount = new AtomicInteger(0);
        when(fileRegistrationService.fileExists(any(), any(), any())).thenAnswer(inv -> {
            duplicateCheckCount.incrementAndGet();
            return false;
        });
        
        // Mock registerFile to randomly succeed or fail
        when(fileRegistrationService.registerFile(any(), any()))
            .thenAnswer(inv -> {
                // Randomly fail some registrations
                if (Math.random() < 0.3) {
                    throw new RuntimeException("Random registration failure");
                }
                return createMockFileOrigin(1L, "test.csv");
            });
        
        // Mock message publisher
        doAnswer(inv -> {
            // Randomly fail some publications
            if (Math.random() < 0.3) {
                throw new RuntimeException("Random publishing failure");
            }
            return null;
        }).when(messagePublisherService).publishFileTransferMessage(any());
        
        // No failed publications to retry
        when(fileOriginRepository.findFailedPublications(any(), any())).thenReturn(List.of());
        
        FileCollectionScheduler scheduler = new FileCollectionScheduler(
            configurationService,
            sftpService,
            fileValidator,
            fileRegistrationService,
            messagePublisherService,
            fileOriginRepository,
            serverPathInOutRepository
        );
        
        // Act
        scheduler.collectFiles();
        
        // Assert - All files should have been checked for duplicates
        Assertions.assertEquals(totalFiles, duplicateCheckCount.get(),
            "Scheduler MUST check all files for duplicates, regardless of failures");
    }
    
    // Helper methods
    
    private ServerConfigurationDTO createMockConfiguration(Long id) {
        ServerConfigurationDTO config = new ServerConfigurationDTO();
        config.setServerId(id);
        config.setCodServer("SFTP_TEST_" + id);
        config.setCodVault("test_vault");
        config.setDesVaultSecret("test_secret");
        config.setServerPathOriginId(id);
        config.setServerPathDestinationId(id + 100);
        config.setServerPathInOutId(id + 200);
        config.setOriginPath("/test/path");
        config.setAcquirerId(1L);
        return config;
    }
    
    private FileMetadataDTO createMockFileMetadata(String filename, long index) {
        return new FileMetadataDTO(
            filename,
            1000L + index,
            new Timestamp(System.currentTimeMillis() + index),
            FileType.csv
        );
    }
    
    private FileOrigin createMockFileOrigin(Long id, String filename) {
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(id);
        fileOrigin.setDesFileName(filename);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.EM_ESPERA);
        fileOrigin.setNumRetry(0);
        fileOrigin.setMaxRetry(5);
        fileOrigin.setFlgActive(1);
        return fileOrigin;
    }
}
