package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.TransactionType;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.commons.repository.FileOriginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Service for file registration and duplicate detection.
 * Handles file duplicate detection and registration with retry logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileRegistrationService {
    
    private final FileOriginRepository fileOriginRepository;
    
    // MVP constants
    private static final Long MVP_ACQUIRER_ID = 1L;
    private static final Long MVP_LAYOUT_ID = 1L;
    private static final Integer INITIAL_NUM_RETRY = 0;
    private static final Integer MAX_RETRY = 5;
    private static final Integer ACTIVE_FLAG = 1;
    
    public boolean fileExists(String filename, Long acquirerId, Timestamp fileTimestamp) {
        log.debug("Checking if file exists: filename={}, acquirerId={}, timestamp={}", 
            filename, acquirerId, fileTimestamp);
        
        return fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            filename, 
            acquirerId, 
            fileTimestamp, 
            ACTIVE_FLAG
        ).isPresent();
    }
    
    @Retryable(
        maxAttempts = 5,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public FileOrigin registerFile(FileMetadataDTO metadata, Long serverPathInOutId) {
        log.info("Registering file: {}", metadata.getFilename());
        
        FileOrigin fileOrigin = new FileOrigin();
        
        // MVP values
        fileOrigin.setIdtAcquirer(MVP_ACQUIRER_ID);
        fileOrigin.setIdtLayout(MVP_LAYOUT_ID);
        
        // File metadata
        fileOrigin.setDesFileName(metadata.getFilename());
        fileOrigin.setNumFileSize(metadata.getFileSize());
        fileOrigin.setDesFileType(metadata.getFileType());
        fileOrigin.setDatTimestampFile(metadata.getTimestamp());
        
        // Initial state
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.EM_ESPERA);
        
        // Retry configuration
        fileOrigin.setNumRetry(INITIAL_NUM_RETRY);
        fileOrigin.setMaxRetry(MAX_RETRY);
        
        // Server path mapping (PRINCIPAL)
        fileOrigin.setIdtSeverPathsInOut(serverPathInOutId);
        
        // Transaction type (MVP default)
        fileOrigin.setDesTransactionType(TransactionType.COMPLETO);
        
        // Active flag
        fileOrigin.setFlgActive(ACTIVE_FLAG);
        
        // Creation date (trigger will set if null, but we set it explicitly)
        fileOrigin.setDatCreation(new Date());
        
        // Change agent
        fileOrigin.setNamChangeAgent("PRODUCER");
        
        FileOrigin saved = fileOriginRepository.save(fileOrigin);
        
        log.info("File registered successfully: id={}, filename={}", 
            saved.getIdtFileOrigin(), saved.getDesFileName());
        
        return saved;
    }
}
