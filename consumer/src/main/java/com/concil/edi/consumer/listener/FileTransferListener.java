package com.concil.edi.consumer.listener;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.consumer.dto.ServerConfigurationDTO;
import com.concil.edi.consumer.service.FileDownloadService;
import com.concil.edi.consumer.service.FileUploadService;
import com.concil.edi.consumer.service.StatusUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * RabbitMQ listener for consuming file transfer messages.
 * Handles downloading files from SFTP origin and uploading to configured destinations.
 * 
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 14.7, 14.8
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileTransferListener {

    private final FileDownloadService fileDownloadService;
    private final FileUploadService fileUploadService;
    private final StatusUpdateService statusUpdateService;
    private final FileOriginRepository fileOriginRepository;
    private final ServerPathRepository serverPathRepository;
    
    @org.springframework.beans.factory.annotation.Value("${queue.delay:0}")
    private int queueDelaySeconds;

    /**
     * Handle file transfer messages from RabbitMQ queue.
     * 
     * @param message File transfer message containing file metadata
     */
    @RabbitListener(queues = "${rabbitmq.queue.file-transfer}")
    public void handleFileTransfer(FileTransferMessageDTO message) {
        Long fileOriginId = message.getIdtFileOrigin();
        
        log.info("Received file transfer message: fileOriginId={}, filename={}, origin={}, destination={}", 
            fileOriginId, message.getFilename(), message.getIdtServerPathOrigin(), message.getIdtServerPathDestination());
        
        // E2E test delay: allows test to validate initial state before processing
        if (queueDelaySeconds > 0) {
            log.info("E2E test delay: waiting {} seconds before processing...", queueDelaySeconds);
            try {
                Thread.sleep(queueDelaySeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("E2E test delay interrupted", e);
            }
        }
        
        try {
            // Requirement 11.6: Update status to PROCESSAMENTO when starting
            statusUpdateService.updateStatus(fileOriginId, Status.PROCESSAMENTO);
            
            // Requirement 11.2, 11.3, 11.4, 11.5: Extract fields from message
            String filename = message.getFilename();
            Long serverPathOriginId = message.getIdtServerPathOrigin();
            Long serverPathDestinationId = message.getIdtServerPathDestination();
            Long fileSize = message.getFileSize(); // Get file size from message
            
            // Requirement 12.1, 12.2, 12.3, 12.4: Open InputStream from SFTP origin
            InputStream inputStream = fileDownloadService.openInputStream(serverPathOriginId, filename);
            
            // Requirement 13.1: Get destination configuration
            ServerConfigurationDTO destConfig = getDestinationConfig(serverPathDestinationId);
            
            // Requirement 13.2, 13.3, 13.4: Upload to destination based on server type
            if (destConfig.getServerType() == ServerType.S3) {
                // For S3, des_path format is "bucket-name/prefix"
                // Extract bucket name (first part before /)
                String desPath = destConfig.getPath();
                String bucketName;
                String keyPrefix;
                
                if (desPath.contains("/")) {
                    String[] parts = desPath.split("/", 2);
                    bucketName = parts[0];
                    keyPrefix = parts.length > 1 ? parts[1] : "";
                } else {
                    bucketName = desPath;
                    keyPrefix = "";
                }
                
                String key = keyPrefix.isEmpty() ? filename : keyPrefix + "/" + filename;
                fileUploadService.uploadToS3(inputStream, bucketName, key, fileSize);
            } else if (destConfig.getServerType() == ServerType.SFTP) {
                String remotePath = destConfig.getPath() + "/" + filename;
                fileUploadService.uploadToSftp(inputStream, destConfig, remotePath);
            } else {
                throw new UnsupportedOperationException("Unsupported destination server type: " + destConfig.getServerType());
            }
            
            // Requirement 14.1, 14.2, 14.3: Update status to CONCLUIDO on success
            statusUpdateService.updateStatus(fileOriginId, Status.CONCLUIDO);
            
            log.info("File transfer completed successfully: fileOriginId={}, filename={}", fileOriginId, filename);
            
        } catch (Exception e) {
            log.error("Error processing file transfer: fileOriginId={}, filename={}", 
                fileOriginId, message.getFilename(), e);
            handleError(fileOriginId, e);
        }
    }

    /**
     * Handle errors during file transfer processing.
     * Implements retry logic with NACK/ACK based on retry count.
     * 
     * Requirements: 14.4, 14.5, 14.6, 14.7, 14.8
     */
    private void handleError(Long fileOriginId, Exception e) {
        try {
            FileOrigin fileOrigin = fileOriginRepository.findById(fileOriginId)
                .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
            
            // Requirement 14.4, 14.5: Update status to ERRO with error message
            statusUpdateService.updateStatusWithError(fileOriginId, Status.ERRO, e.getMessage());
            
            // Requirement 14.6: Increment retry counter
            statusUpdateService.incrementRetry(fileOriginId);
            
            // Reload to get updated retry count
            fileOrigin = fileOriginRepository.findById(fileOriginId).orElseThrow();
            
            // Requirement 14.7: NACK if num_retry < max_retry (message will be requeued)
            if (fileOrigin.getNumRetry() < fileOrigin.getMaxRetry()) {
                log.warn("File transfer failed, will retry: fileOriginId={}, retry={}/{}", 
                    fileOriginId, fileOrigin.getNumRetry(), fileOrigin.getMaxRetry());
                throw new ListenerExecutionFailedException("Retry limit not reached, requeuing message", e);
            } else {
                // Requirement 14.8: ACK if num_retry >= max_retry (message will be removed from queue)
                log.error("File transfer failed, max retry reached: fileOriginId={}, retry={}/{}", 
                    fileOriginId, fileOrigin.getNumRetry(), fileOrigin.getMaxRetry());
                // Message will be ACKed automatically (no exception thrown)
            }
        } catch (ListenerExecutionFailedException ex) {
            // Re-throw to trigger NACK
            throw ex;
        } catch (Exception ex) {
            log.error("Error handling file transfer error: fileOriginId={}", fileOriginId, ex);
            // ACK the message to prevent infinite loop
        }
    }

    /**
     * Get destination server configuration from database.
     * 
     * @param serverPathDestinationId Server path destination ID
     * @return Server configuration DTO
     */
    private ServerConfigurationDTO getDestinationConfig(Long serverPathDestinationId) {
        ServerPath serverPath = serverPathRepository.findWithServerByIdtSeverPath(serverPathDestinationId)
            .orElseThrow(() -> new IllegalArgumentException("ServerPath not found: " + serverPathDestinationId));
        
        ServerConfigurationDTO config = new ServerConfigurationDTO();
        config.setServerPathId(serverPath.getIdtSeverPath());
        config.setPath(serverPath.getDesPath());
        config.setAcquirerId(serverPath.getIdtAcquirer());
        config.setServerId(serverPath.getServer().getIdtServer());
        config.setCodServer(serverPath.getServer().getCodServer());
        config.setCodVault(serverPath.getServer().getCodVault());
        config.setDesVaultSecret(serverPath.getServer().getDesVaultSecret());
        config.setServerType(serverPath.getServer().getDesServerType());
        config.setHost(serverPath.getServer().getCodServer()); // For SFTP, host is in cod_server
        config.setPort(22); // Default SFTP port
        
        return config;
    }
}
