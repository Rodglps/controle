package com.concil.edi.producer.scheduler;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.ServerPathInOut;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.ServerPathInOutRepository;
import com.concil.edi.producer.service.ConfigurationService;
import com.concil.edi.producer.service.FileRegistrationService;
import com.concil.edi.producer.service.FileValidator;
import com.concil.edi.producer.service.MessagePublisherService;
import com.concil.edi.producer.service.SftpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler component that periodically collects files from SFTP servers.
 * Executes every 2 minutes to:
 * 1. Retry failed message publications (des_step=COLETA, des_status=ERRO, num_retry < max_retry)
 * 2. Process new files from configured SFTP servers
 * 
 * Implements error isolation: if one file fails, processing continues with the next file.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileCollectionScheduler {
    
    private final ConfigurationService configurationService;
    private final SftpService sftpService;
    private final FileValidator fileValidator;
    private final FileRegistrationService fileRegistrationService;
    private final MessagePublisherService messagePublisherService;
    private final FileOriginRepository fileOriginRepository;
    private final ServerPathInOutRepository serverPathInOutRepository;
    
    /**
     * Main scheduled method. Default: every 2 minutes (120000ms).
     * Configurable via PRODUCER_DELAY environment variable (in milliseconds).
     * Processes failed publications first, then collects new files.
     */
    @Scheduled(fixedDelayString = "${scheduler.file-collection.fixed-delay}")
    public void collectFiles() {
        log.info("Starting file collection cycle");
        
        try {
            // Step 1: Retry failed message publications
            retryFailedPublications();
            
            // Step 2: Process new files
            processNewFiles();
            
            log.info("File collection cycle completed");
        } catch (Exception e) {
            log.error("Unexpected error during file collection cycle", e);
        }
    }
    
    /**
     * Retry failed message publications.
     * Finds files with des_step=COLETA, des_status=ERRO, num_retry < max_retry
     * and attempts to republish them.
     */
    private void retryFailedPublications() {
        log.debug("Checking for failed publications to retry");
        
        try {
            List<FileOrigin> failedPublications = fileOriginRepository.findFailedPublications(
                Step.COLETA, 
                Status.ERRO
            );
            
            if (failedPublications.isEmpty()) {
                log.debug("No failed publications found for retry");
                return;
            }
            
            log.info("Found {} failed publications to retry", failedPublications.size());
            
            for (FileOrigin fileOrigin : failedPublications) {
                try {
                    log.info("Retrying publication for file_origin: {}, filename: {}, retry: {}/{}", 
                        fileOrigin.getIdtFileOrigin(),
                        fileOrigin.getDesFileName(),
                        fileOrigin.getNumRetry(),
                        fileOrigin.getMaxRetry());
                    
                    // Build message from existing file_origin record
                    FileTransferMessageDTO message = buildMessageFromFileOrigin(fileOrigin);
                    
                    // Attempt to publish (has @Retryable with exponential backoff)
                    messagePublisherService.publishFileTransferMessage(message);
                    
                    // Success: update status back to EM_ESPERA
                    fileOrigin.setDesStatus(Status.EM_ESPERA);
                    fileOrigin.setDesMessageError(null); // Clear previous error
                    fileOriginRepository.save(fileOrigin);
                    
                    log.info("Successfully retried publication for file_origin: {}", 
                        fileOrigin.getIdtFileOrigin());
                    
                } catch (Exception e) {
                    log.error("Failed to retry publication for file_origin: {}, will retry in next cycle", 
                        fileOrigin.getIdtFileOrigin(), e);
                    // Continue with next file - error isolation
                }
            }
        } catch (Exception e) {
            log.error("Error during retry of failed publications", e);
            // Continue with new file processing
        }
    }
    
    /**
     * Process new files from configured SFTP servers.
     * Loads active configurations, lists files, validates, registers, and publishes messages.
     */
    private void processNewFiles() {
        log.debug("Loading active configurations");
        
        List<ServerConfigurationDTO> configurations;
        try {
            configurations = configurationService.loadActiveConfigurations();
        } catch (Exception e) {
            log.error("Failed to load active configurations", e);
            return;
        }
        
        if (configurations.isEmpty()) {
            log.warn("No active configurations found");
            return;
        }
        
        log.info("Processing {} active configurations", configurations.size());
        
        for (ServerConfigurationDTO config : configurations) {
            try {
                processConfiguration(config);
            } catch (Exception e) {
                log.error("Error processing configuration: {}, continuing with next configuration", 
                    config.getCodServer(), e);
                // Continue with next configuration - error isolation
            }
        }
    }
    
    /**
     * Process a single server configuration.
     * Lists files from SFTP, applies hybrid validation, checks for duplicates, registers, and publishes.
     */
    private void processConfiguration(ServerConfigurationDTO config) {
        log.debug("Processing configuration: {}, path: {}", 
            config.getCodServer(), config.getOriginPath());
        
        List<FileMetadataDTO> rawFiles;
        try {
            rawFiles = sftpService.listFiles(config);
        } catch (Exception e) {
            log.error("Failed to list files from SFTP for config: {}", config.getCodServer(), e);
            return;
        }
        
        if (rawFiles.isEmpty()) {
            log.debug("No files found for configuration: {}", config.getCodServer());
            return;
        }
        
        log.info("Found {} raw files for configuration: {}", rawFiles.size(), config.getCodServer());
        
        // Apply hybrid validation
        List<FileMetadataDTO> eligibleFiles;
        try {
            eligibleFiles = fileValidator.validateFiles(rawFiles, config);
        } catch (Exception e) {
            log.error("Failed to validate files for config: {}", config.getCodServer(), e);
            return;
        }
        
        if (eligibleFiles.isEmpty()) {
            log.debug("No eligible files after validation for configuration: {}", 
                config.getCodServer());
            return;
        }
        
        log.info("Found {} eligible files after validation for configuration: {}", 
            eligibleFiles.size(), config.getCodServer());
        
        for (FileMetadataDTO file : eligibleFiles) {
            try {
                processFile(file, config);
            } catch (Exception e) {
                log.error("Error processing file: {}, continuing with next file", 
                    file.getFilename(), e);
                // Continue with next file - error isolation
            }
        }
    }
    
    /**
     * Process a single file.
     * Checks for duplicates, registers in database, and publishes message.
     */
    private void processFile(FileMetadataDTO file, ServerConfigurationDTO config) {
        log.debug("Processing file: {}", file.getFilename());
        
        // Check if file already exists (duplicate detection)
        if (fileRegistrationService.fileExists(
            file.getFilename(), 
            config.getAcquirerId(), 
            file.getTimestamp())) {
            
            log.debug("File already exists, skipping: {}", file.getFilename());
            return;
        }
        
        // Register file in database (has @Retryable with exponential backoff)
        FileOrigin registered;
        try {
            registered = fileRegistrationService.registerFile(file, config.getServerPathInOutId());
        } catch (Exception e) {
            log.error("Failed to register file: {}", file.getFilename(), e);
            throw e; // Propagate to trigger error isolation
        }
        
        // Build message for RabbitMQ
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            registered.getIdtFileOrigin(),
            file.getFilename(),
            config.getServerPathOriginId(),
            config.getServerPathDestinationId(),
            file.getFileSize()
        );
        
        // Publish message (has @Retryable with exponential backoff)
        // If all retries fail, @Recover method updates file_origin with ERRO status
        try {
            messagePublisherService.publishFileTransferMessage(message);
            log.info("Successfully processed file: {}, file_origin_id: {}", 
                file.getFilename(), registered.getIdtFileOrigin());
        } catch (Exception e) {
            log.error("Failed to publish message for file: {}, file_origin_id: {}", 
                file.getFilename(), registered.getIdtFileOrigin(), e);
            throw e; // Propagate to trigger error isolation
        }
    }
    
    /**
     * Build FileTransferMessage from existing FileOrigin record.
     * Used for retrying failed publications.
     */
    private FileTransferMessageDTO buildMessageFromFileOrigin(FileOrigin fileOrigin) {
        // Retrieve the server path mapping using idtSeverPathsInOut
        ServerPathInOut mapping = serverPathInOutRepository.findById(fileOrigin.getIdtSeverPathsInOut())
            .orElseThrow(() -> new IllegalStateException(
                "ServerPathInOut not found for id: " + fileOrigin.getIdtSeverPathsInOut()));
        
        return new FileTransferMessageDTO(
            fileOrigin.getIdtFileOrigin(),
            fileOrigin.getDesFileName(),
            mapping.getSeverPathOrigin().getIdtSeverPath(),
            mapping.getSeverPathDestination().getIdtSeverPath(),
            fileOrigin.getNumFileSize()
        );
    }
}
