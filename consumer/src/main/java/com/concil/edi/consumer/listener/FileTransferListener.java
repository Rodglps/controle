package com.concil.edi.consumer.listener;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.FileOriginClients;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.repository.FileOriginClientsRepository;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.consumer.dto.ServerConfigurationDTO;
import com.concil.edi.consumer.service.CustomerIdentificationService;
import com.concil.edi.consumer.service.FileDownloadService;
import com.concil.edi.consumer.service.FileUploadService;
import com.concil.edi.consumer.service.LayoutIdentificationService;
import com.concil.edi.consumer.service.ProcessingSplitService;
import com.concil.edi.consumer.service.RemoveOriginService;
import com.concil.edi.consumer.service.StatusUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * RabbitMQ listener for consuming file transfer messages.
 * Handles downloading files from SFTP origin and uploading to configured destinations.
 * 
 * Requirements: 3.1, 3.5, 3.6, 3.7, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 14.1, 14.2, 14.3, 14.4, 14.7, 14.8
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileTransferListener {

    // Requirement 3.1: Marker used to identify pending-removal records in des_message_error
    static final String REMOVE_ORIGIN_FILE_ERROR = "REMOVE_ORIGIN_FILE_ERROR";

    private final FileDownloadService fileDownloadService;
    private final FileUploadService fileUploadService;
    private final StatusUpdateService statusUpdateService;
    private final FileOriginRepository fileOriginRepository;
    private final ServerPathRepository serverPathRepository;
    private final LayoutIdentificationService layoutIdentificationService;
    private final CustomerIdentificationService customerIdentificationService;
    private final FileOriginClientsRepository fileOriginClientsRepository;
    private final RemoveOriginService removeOriginService;
    private final ProcessingSplitService processingSplitService;

    @org.springframework.beans.factory.annotation.Value("${queue.delay:0}")
    private int queueDelaySeconds;

    /**
     * Handle file transfer messages from RabbitMQ queue.
     * 
     * @param message File transfer message containing file metadata
     */
    @RabbitListener(queues = "${rabbitmq.queue.file-transfer}")
    public void handleFileTransfer(FileTransferMessageDTO message) {
        Date stepStartTime = new Date();
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
            // Requirement 11.2, 11.3, 11.4, 11.5: Extract fields from message
            String filename = message.getFilename();
            Long serverPathOriginId = message.getIdtServerPathOrigin();
            Long serverPathDestinationId = message.getIdtServerPathDestination();
            Long fileSize = message.getFileSize(); // Get file size from message

            // Requirements 3.1, 3.2, 3.3, 3.4: Check for pending removal before doing anything else
            FileOrigin fileOriginCheck = fileOriginRepository.findById(fileOriginId)
                .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
            if (isPendingRemoval(fileOriginCheck)) {
                log.info("Pending removal detected, skipping transfer: fileOriginId={}, filename={}", fileOriginId, filename);
                executeRemoval(fileOriginId, serverPathOriginId, filename, fileOriginCheck);
                return;
            }

            // Requirement 11.6: Update status to PROCESSAMENTO when starting
            statusUpdateService.updateStatus(fileOriginId, Status.PROCESSAMENTO);
            
            // Requirement 12.1, 12.2, 12.3, 12.4: Open InputStream from SFTP origin
            // Requirement 3.1: Identify layout after opening InputStream
            ServerPath serverPathOrigin = serverPathRepository.findWithServerByIdtSeverPath(serverPathOriginId)
                .orElseThrow(() -> new IllegalArgumentException("ServerPath not found: " + serverPathOriginId));
            Long acquirerId = serverPathOrigin.getIdtAcquirer();
            
            Long layoutId;
            byte[] buffer; // Buffer to be reused for customer identification
            try (InputStream layoutInputStream = fileDownloadService.openInputStream(serverPathOriginId, filename)) {
                // Read buffer for layout identification (will be reused for customer identification)
                buffer = layoutInputStream.readNBytes(7000);
                layoutId = layoutIdentificationService.identifyLayout(
                    new java.io.ByteArrayInputStream(buffer), filename, acquirerId);
            }
            
            // Requirement 3.5, 3.6, 3.7: If layout not identified, use layout 0 (SEM_IDENTIFICACAO)
            if (layoutId == null) {
                layoutId = 0L; // Special layout for unidentified files
                log.warn("Layout not identified for file: fileOriginId={}, filename={}, using layout 0 (SEM_IDENTIFICACAO)", 
                    fileOriginId, filename);
            } else {
                log.info("Layout identified for file: fileOriginId={}, filename={}, layoutId={}", 
                    fileOriginId, filename, layoutId);
            }
            
            // Requirement 14.2: Update file_origin.idt_layout with identified layout (or 0 if not identified)
            statusUpdateService.updateLayoutId(fileOriginId, layoutId);
            
            // Customer Identification: Identify customers after layout identification
            // Requirement 1.1, 11.1, 11.2: Call identifyCustomers with same buffer used for layout identification
            List<Long> identifiedClients = customerIdentificationService.identifyCustomers(
                buffer, filename, acquirerId, layoutId);
            
            // Requirement 2.2, 2.3, 12.1-12.5: Persist identified clients in file_origin_clients
            if (!identifiedClients.isEmpty()) {
                for (Long clientId : identifiedClients) {
                    try {
                        FileOriginClients fileOriginClient = new FileOriginClients();
                        fileOriginClient.setIdtFileOrigin(fileOriginId);
                        fileOriginClient.setIdtClient(clientId);
                        fileOriginClient.setDatCreation(new java.util.Date());
                        
                        fileOriginClientsRepository.save(fileOriginClient);
                        log.info("Persisted customer identification: fileOriginId={}, clientId={}", 
                            fileOriginId, clientId);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        // Requirement 2.3: Handle duplicates gracefully
                        log.warn("Duplicate customer identification ignored: fileOriginId={}, clientId={}", 
                            fileOriginId, clientId);
                    }
                }
            } else {
                // Requirement 1.7: No persistence when no clients identified
                log.info("No customers identified for file: fileOriginId={}, filename={}", 
                    fileOriginId, filename);
            }
            
            // Create processing split records after customer identification
            try {
                processingSplitService.createInitialRecords(fileOriginId, identifiedClients, stepStartTime);
            } catch (Exception e) {
                log.error("Error creating processing split records: fileOriginId={}", fileOriginId, e);
            }
            
            // Reopen InputStream for upload (previous stream was consumed during identification)
            // Requirement 13.1: Get destination configuration
            ServerConfigurationDTO destConfig = getDestinationConfig(serverPathDestinationId);

            // Requirement 13.2, 13.3, 13.4: Upload to destination based on server type
            // Also compute keyOrPath here so it can be reused for integrity check
            String keyOrPath;
            try (InputStream uploadInputStream = fileDownloadService.openInputStream(serverPathOriginId, filename)) {
                if (destConfig.getServerType() == ServerType.S3) {
                    // For S3, des_path format is "bucket-name/prefix"
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

                    keyOrPath = keyPrefix.isEmpty() ? filename : keyPrefix + "/" + filename;
                    fileUploadService.uploadToS3(uploadInputStream, bucketName, keyOrPath, fileSize);
                } else if (destConfig.getServerType() == ServerType.SFTP) {
                    keyOrPath = destConfig.getPath() + "/" + filename;
                    fileUploadService.uploadToSftp(uploadInputStream, destConfig, keyOrPath);
                } else {
                    throw new UnsupportedOperationException("Unsupported destination server type: " + destConfig.getServerType());
                }
            }

            // Requirements 1.1, 1.2, 1.3, 1.4: Integrity check — compare destination file size with expected
            long destinationFileSize = getDestinationFileSize(destConfig, filename, keyOrPath);
            if (destinationFileSize != fileSize) {
                log.error("Integrity check failed: fileOriginId={}, filename={}, expected={}, actual={}",
                        fileOriginId, filename, fileSize, destinationFileSize);
                statusUpdateService.updateStatusWithError(fileOriginId, Status.ERRO,
                        "Erro de integridade: tamanho do arquivo no destino difere do esperado");
                return;
            }

            // Requirements 2.1, 2.2: Integrity OK — remove origin file (executeRemoval handles CONCLUIDO status)
            FileOrigin fileOrigin = fileOriginRepository.findById(fileOriginId)
                .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
            executeRemoval(fileOriginId, serverPathOriginId, filename, fileOrigin);
            
            try {
                processingSplitService.completeRecords(fileOriginId);
            } catch (Exception e) {
                log.error("Error completing processing split records: fileOriginId={}", fileOriginId, e);
            }
            
            log.info("File transfer and removal completed: fileOriginId={}, filename={}, layoutId={}",
                    fileOriginId, filename, layoutId);
            
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
            
            try {
                processingSplitService.failRecords(fileOriginId, e.getMessage());
            } catch (Exception ex) {
                log.error("Error failing processing split records: fileOriginId={}", fileOriginId, ex);
            }
            
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
     * Checks whether a file has a pending removal (was transferred but removal failed).
     * Returns true if status=ERRO and des_message_error contains REMOVE_ORIGIN_FILE_ERROR.
     *
     * Requirements: 3.1
     */
    private boolean isPendingRemoval(FileOrigin fileOrigin) {
        if (fileOrigin.getDesStatus() != Status.ERRO) {
            return false;
        }
        String errorMsg = fileOrigin.getDesMessageError();
        return errorMsg != null && errorMsg.contains(REMOVE_ORIGIN_FILE_ERROR);
    }

    /**
     * Returns the size in bytes of the file at the destination after upload.
     * Supports S3 and SFTP destination types.
     *
     * @param destConfig  Destination server configuration
     * @param filename    File name
     * @param keyOrPath   S3 key or full SFTP remote path
     * @return size in bytes
     * Requirements: 1.1
     */
    private long getDestinationFileSize(ServerConfigurationDTO destConfig, String filename, String keyOrPath) {
        if (destConfig.getServerType() == ServerType.S3) {
            String desPath = destConfig.getPath();
            String bucketName;
            String key;
            if (desPath.contains("/")) {
                String[] parts = desPath.split("/", 2);
                bucketName = parts[0];
                String keyPrefix = parts.length > 1 ? parts[1] : "";
                key = keyPrefix.isEmpty() ? filename : keyPrefix + "/" + filename;
            } else {
                bucketName = desPath;
                key = filename;
            }
            return fileUploadService.getS3ObjectSize(bucketName, key);
        } else if (destConfig.getServerType() == ServerType.SFTP) {
            return fileUploadService.getSftpFileSize(destConfig, keyOrPath);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported destination server type for size check: " + destConfig.getServerType());
        }
    }

    /**
     * Executes the removal of the origin file with retry-limit check, status updates,
     * and NACK re-throw on failure when retries remain.
     *
     * Requirements: 2.1, 2.2, 2.3, 2.4, 4.2, 4.3, 4.4
     */
    private void executeRemoval(Long fileOriginId, Long serverPathOriginId, String filename, FileOrigin fileOrigin) {
        // Requirement 4.3, 4.4: If retry limit reached, record error and stop
        if (fileOrigin.getNumRetry() >= fileOrigin.getMaxRetry()) {
            log.warn("Max retry reached for removal: fileOriginId={}, numRetry={}, maxRetry={}",
                    fileOriginId, fileOrigin.getNumRetry(), fileOrigin.getMaxRetry());
            statusUpdateService.updateStatusWithError(fileOriginId, Status.ERRO,
                    REMOVE_ORIGIN_FILE_ERROR + ". arquivo " + filename + ", motivo: limite de tentativas atingido");
            return;
        }

        try {
            // Requirement 2.1: Remove file from origin via SFTP
            removeOriginService.removeFile(serverPathOriginId, filename);

            // Requirement 2.2: Mark as CONCLUIDO on success
            statusUpdateService.updateStatus(fileOriginId, Status.CONCLUIDO);
            log.info("File removed from origin successfully: fileOriginId={}, filename={}", fileOriginId, filename);

        } catch (Exception e) {
            // Requirement 2.3, 2.4: Record error with REMOVE_ORIGIN_FILE_ERROR marker and step=COLETA
            String errorMessage = REMOVE_ORIGIN_FILE_ERROR + ". arquivo " + filename + ", motivo: " + e.getMessage();
            statusUpdateService.updateStatusWithError(fileOriginId, Status.ERRO, errorMessage);

            // Update desStep to COLETA so Producer can detect and retry
            FileOrigin updated = fileOriginRepository.findById(fileOriginId)
                    .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
            updated.setDesStep(Step.COLETA);
            updated.setDatUpdate(new Date());
            fileOriginRepository.save(updated);

            log.error("Failed to remove origin file: fileOriginId={}, filename={}, error={}",
                    fileOriginId, filename, e.getMessage());

            // Requirement 4.2: NACK to force requeue when retries remain
            throw new ListenerExecutionFailedException(
                    "Failed to remove origin file, requeuing for retry: " + filename, e);
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
