package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.TransactionType;
import com.concil.edi.commons.repository.FileOriginRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Property 44: Success status update
 * 
 * Property: When updateStatus is called with CONCLUIDO status,
 * THEN des_status must be CONCLUIDO,
 * AND dat_update must be populated with current timestamp,
 * AND nam_change_agent must be populated with "consumer-service"
 * 
 * Validates: Requirements 14.1, 14.2, 14.3
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@Disabled("Requires Oracle database - run with 'make e2e'")
public class StatusUpdateSuccessPropertyTest {
    
    @Autowired
    private StatusUpdateService statusUpdateService;
    
    @Autowired
    private FileOriginRepository fileOriginRepository;
    
    @BeforeEach
    @Transactional
    public void cleanup() {
        fileOriginRepository.deleteAll();
    }
    
    @Property
    @net.jqwik.api.Disabled("Requires Oracle database - run with 'make e2e'")
    @Transactional
    void successStatusUpdateMustPopulateAllFields(
        @ForAll("fileNames") String fileName,
        @ForAll("acquirerIds") Long acquirerId,
        @ForAll("fileSizes") Long fileSize
    ) {
        // Arrange: Create a file_origin record
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setDesFileName(fileName);
        fileOrigin.setIdtAcquirer(acquirerId);
        fileOrigin.setIdtLayout(1L);
        fileOrigin.setNumFileSize(fileSize);
        fileOrigin.setDesFileType(FileType.CSV);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.PROCESSAMENTO);
        fileOrigin.setDesTransactionType(TransactionType.COMPLETO);
        fileOrigin.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        fileOrigin.setIdtSeverPathsInOut(1L);
        fileOrigin.setDatCreation(new Date());
        fileOrigin.setFlgActive(1);
        fileOrigin.setNumRetry(0);
        fileOrigin.setMaxRetry(5);
        
        FileOrigin saved = fileOriginRepository.save(fileOrigin);
        Long fileOriginId = saved.getIdtFileOrigin();
        
        Date beforeUpdate = new Date();
        
        // Act: Update status to CONCLUIDO
        statusUpdateService.updateStatus(fileOriginId, Status.CONCLUIDO);
        
        // Assert: Verify all fields are correctly updated
        FileOrigin updated = fileOriginRepository.findById(fileOriginId).orElseThrow();
        
        // Property 1: des_status must be CONCLUIDO (Requirement 14.1)
        assert updated.getDesStatus() == Status.CONCLUIDO : 
            "Status must be CONCLUIDO after successful update";
        
        // Property 2: dat_update must be populated (Requirement 14.2)
        assert updated.getDatUpdate() != null : 
            "dat_update must be populated after status update";
        assert !updated.getDatUpdate().before(beforeUpdate) : 
            "dat_update must be current or after the update call";
        
        // Property 3: nam_change_agent must be populated (Requirement 14.3)
        assert updated.getNamChangeAgent() != null : 
            "nam_change_agent must be populated after status update";
        assert updated.getNamChangeAgent().equals("consumer-service") : 
            "nam_change_agent must be 'consumer-service'";
    }
    
    @Provide
    Arbitrary<String> fileNames() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(5)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<Long> acquirerIds() {
        return Arbitraries.longs().between(1L, 100L);
    }
    
    @Provide
    Arbitrary<Long> fileSizes() {
        return Arbitraries.longs().between(1L, 1000000L);
    }
}
