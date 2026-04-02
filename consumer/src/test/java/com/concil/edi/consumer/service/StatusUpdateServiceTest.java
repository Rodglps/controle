package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.TransactionType;
import com.concil.edi.commons.repository.FileOriginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatusUpdateService.
 * 
 * Requirements: 19.5
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StatusUpdateServiceTest {
    
    @Autowired
    private StatusUpdateService statusUpdateService;
    
    @Autowired
    private FileOriginRepository fileOriginRepository;
    
    private FileOrigin testFileOrigin;
    
    @BeforeEach
    void setup() {
        fileOriginRepository.deleteAll();
        
        testFileOrigin = new FileOrigin();
        testFileOrigin.setDesFileName("test-file.csv");
        testFileOrigin.setIdtAcquirer(1L);
        testFileOrigin.setIdtLayout(1L);
        testFileOrigin.setNumFileSize(1024L);
        testFileOrigin.setDesFileType(FileType.csv);
        testFileOrigin.setDesStep(Step.COLETA);
        testFileOrigin.setDesStatus(Status.EM_ESPERA);
        testFileOrigin.setDesTransactionType(TransactionType.COMPLETO);
        testFileOrigin.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        testFileOrigin.setIdtServerPathsInOut(1L);
        testFileOrigin.setDatCreation(new Date());
        testFileOrigin.setFlgActive(1);
        testFileOrigin.setNumRetry(0);
        testFileOrigin.setMaxRetry(5);
        
        testFileOrigin = fileOriginRepository.save(testFileOrigin);
    }
    
    @Test
    void testUpdateStatusToConcluido() {
        // Act
        statusUpdateService.updateStatus(testFileOrigin.getIdtFileOrigin(), Status.CONCLUIDO);
        
        // Assert
        FileOrigin updated = fileOriginRepository.findById(testFileOrigin.getIdtFileOrigin()).orElseThrow();
        assertEquals(Status.CONCLUIDO, updated.getDesStatus());
        assertNotNull(updated.getDatUpdate());
        assertEquals("consumer-service", updated.getNamChangeAgent());
    }
    
    @Test
    void testUpdateStatusWithError() {
        // Arrange
        String errorMessage = "Connection timeout to SFTP server";
        
        // Act
        statusUpdateService.updateStatusWithError(
            testFileOrigin.getIdtFileOrigin(), 
            Status.ERRO, 
            errorMessage
        );
        
        // Assert
        FileOrigin updated = fileOriginRepository.findById(testFileOrigin.getIdtFileOrigin()).orElseThrow();
        assertEquals(Status.ERRO, updated.getDesStatus());
        assertEquals(errorMessage, updated.getDesMessageError());
        assertNotNull(updated.getDatUpdate());
        assertEquals("consumer-service", updated.getNamChangeAgent());
    }
    
    @Test
    void testIncrementRetry() {
        // Arrange
        int initialRetry = testFileOrigin.getNumRetry();
        
        // Act
        statusUpdateService.incrementRetry(testFileOrigin.getIdtFileOrigin());
        
        // Assert
        FileOrigin updated = fileOriginRepository.findById(testFileOrigin.getIdtFileOrigin()).orElseThrow();
        assertEquals(initialRetry + 1, updated.getNumRetry());
        assertNotNull(updated.getDatUpdate());
    }
    
    @Test
    void testPopulateDatUpdateAndNamChangeAgent() {
        // Arrange
        Date beforeUpdate = new Date();
        
        // Act
        statusUpdateService.updateStatus(testFileOrigin.getIdtFileOrigin(), Status.PROCESSAMENTO);
        
        // Assert
        FileOrigin updated = fileOriginRepository.findById(testFileOrigin.getIdtFileOrigin()).orElseThrow();
        assertNotNull(updated.getDatUpdate());
        assertFalse(updated.getDatUpdate().before(beforeUpdate));
        assertEquals("consumer-service", updated.getNamChangeAgent());
    }
    
    @Test
    void testUpdateStatusThrowsExceptionForInvalidId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            statusUpdateService.updateStatus(99999L, Status.CONCLUIDO);
        });
    }
}
