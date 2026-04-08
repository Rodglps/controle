package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.repository.FileOriginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Service for updating file_origin status during Consumer processing.
 * 
 * Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatusUpdateService {
    
    private static final String CHANGE_AGENT = "consumer-service";
    
    private final FileOriginRepository fileOriginRepository;
    
    /**
     * Update file status to a new status.
     * Updates des_status, dat_update, and nam_change_agent.
     * 
     * @param fileOriginId File origin ID
     * @param status New status
     */
    @Transactional
    public void updateStatus(Long fileOriginId, Status status) {
        FileOrigin fileOrigin = fileOriginRepository.findById(fileOriginId)
            .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
        
        fileOrigin.setDesStatus(status);
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent(CHANGE_AGENT);
        
        fileOriginRepository.save(fileOrigin);
        
        log.info("Updated file_origin {} to status {}", fileOriginId, status);
    }
    
    /**
     * Update file status with error message.
     * Updates des_status, des_message_error, and dat_update.
     * 
     * @param fileOriginId File origin ID
     * @param status New status (typically ERRO)
     * @param errorMessage Error message to record
     */
    @Transactional
    public void updateStatusWithError(Long fileOriginId, Status status, String errorMessage) {
        FileOrigin fileOrigin = fileOriginRepository.findById(fileOriginId)
            .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
        
        fileOrigin.setDesStatus(status);
        fileOrigin.setDesMessageError(errorMessage);
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent(CHANGE_AGENT);
        
        fileOriginRepository.save(fileOrigin);
        
        log.error("Updated file_origin {} to status {} with error: {}", fileOriginId, status, errorMessage);
    }
    
    /**
     * Increment retry counter for a file.
     * Increments num_retry by 1.
     * 
     * @param fileOriginId File origin ID
     */
    @Transactional
    public void incrementRetry(Long fileOriginId) {
        FileOrigin fileOrigin = fileOriginRepository.findById(fileOriginId)
            .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
        
        fileOrigin.setNumRetry(fileOrigin.getNumRetry() + 1);
        fileOrigin.setDatUpdate(new Date());
        
        fileOriginRepository.save(fileOrigin);
        
        log.info("Incremented retry count for file_origin {} to {}", fileOriginId, fileOrigin.getNumRetry());
    }
    
    /**
     * Update layout ID for a file after successful identification.
     * Updates idt_layout and dat_update.
     * 
     * @param fileOriginId File origin ID
     * @param layoutId Layout ID identified
     */
    @Transactional
    public void updateLayoutId(Long fileOriginId, Long layoutId) {
        FileOrigin fileOrigin = fileOriginRepository.findById(fileOriginId)
            .orElseThrow(() -> new IllegalArgumentException("FileOrigin not found: " + fileOriginId));
        
        fileOrigin.setIdtLayout(layoutId);
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent(CHANGE_AGENT);
        
        fileOriginRepository.save(fileOrigin);
        
        log.info("Updated file_origin {} with layout ID {}", fileOriginId, layoutId);
    }
}
