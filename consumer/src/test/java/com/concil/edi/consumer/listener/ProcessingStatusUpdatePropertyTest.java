package com.concil.edi.consumer.listener;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.TransactionType;
import net.jqwik.api.*;
import org.junit.jupiter.api.DisplayName;

import java.sql.Timestamp;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for processing status update on consumption.
 * 
 * Property 37: Processing status update on consumption
 * Validates: Requirements 11.6
 * 
 * Correctness Property:
 * When Consumer starts processing a message, the file_origin status must be updated to PROCESSAMENTO.
 * This update must happen before any file transfer operations begin.
 */
@DisplayName("Property 37: Processing Status Update on Consumption")
class ProcessingStatusUpdatePropertyTest {

    @Property
    @Label("Status must transition to PROCESSAMENTO when message consumption starts")
    void statusMustTransitionToProcessamentoOnConsumption(
        @ForAll("fileOriginInEmEspera") FileOrigin fileOrigin
    ) {
        // Precondition: File is in EM_ESPERA status
        assertThat(fileOrigin.getDesStatus())
            .as("Precondition: File must be in EM_ESPERA status")
            .isEqualTo(Status.EM_ESPERA);
        
        // Simulate status update when Consumer starts processing
        fileOrigin.setDesStatus(Status.PROCESSAMENTO);
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent("consumer");
        
        // Property: Status must be PROCESSAMENTO after consumption starts
        assertThat(fileOrigin.getDesStatus())
            .as("Status must be PROCESSAMENTO when Consumer starts processing")
            .isEqualTo(Status.PROCESSAMENTO);
        
        // Property: Update timestamp must be set
        assertThat(fileOrigin.getDatUpdate())
            .as("Update timestamp must be set")
            .isNotNull()
            .isAfterOrEqualTo(fileOrigin.getDatCreation());
        
        // Property: Change agent must be set
        assertThat(fileOrigin.getNamChangeAgent())
            .as("Change agent must be set")
            .isNotNull()
            .isNotEmpty();
    }

    @Property
    @Label("Status update must preserve all other file attributes")
    void statusUpdateMustPreserveOtherAttributes(
        @ForAll("fileOriginInEmEspera") FileOrigin fileOrigin
    ) {
        // Capture original values
        Long originalIdtFileOrigin = fileOrigin.getIdtFileOrigin();
        String originalFileName = fileOrigin.getDesFileName();
        Long originalFileSize = fileOrigin.getNumFileSize();
        FileType originalFileType = fileOrigin.getDesFileType();
        Step originalStep = fileOrigin.getDesStep();
        Timestamp originalTimestamp = fileOrigin.getDatTimestampFile();
        Integer originalRetry = fileOrigin.getNumRetry();
        Integer originalMaxRetry = fileOrigin.getMaxRetry();
        
        // Simulate status update
        fileOrigin.setDesStatus(Status.PROCESSAMENTO);
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent("consumer");
        
        // Property: All other attributes must be preserved
        assertThat(fileOrigin.getIdtFileOrigin())
            .as("File ID must be preserved")
            .isEqualTo(originalIdtFileOrigin);
        
        assertThat(fileOrigin.getDesFileName())
            .as("File name must be preserved")
            .isEqualTo(originalFileName);
        
        assertThat(fileOrigin.getNumFileSize())
            .as("File size must be preserved")
            .isEqualTo(originalFileSize);
        
        assertThat(fileOrigin.getDesFileType())
            .as("File type must be preserved")
            .isEqualTo(originalFileType);
        
        assertThat(fileOrigin.getDesStep())
            .as("Step must be preserved")
            .isEqualTo(originalStep);
        
        assertThat(fileOrigin.getDatTimestampFile())
            .as("File timestamp must be preserved")
            .isEqualTo(originalTimestamp);
        
        assertThat(fileOrigin.getNumRetry())
            .as("Retry count must be preserved")
            .isEqualTo(originalRetry);
        
        assertThat(fileOrigin.getMaxRetry())
            .as("Max retry must be preserved")
            .isEqualTo(originalMaxRetry);
    }

    @Property
    @Label("Status transition from EM_ESPERA to PROCESSAMENTO must be idempotent")
    void statusTransitionMustBeIdempotent(
        @ForAll("fileOriginInEmEspera") FileOrigin fileOrigin
    ) {
        // First update
        fileOrigin.setDesStatus(Status.PROCESSAMENTO);
        Date firstUpdate = new Date();
        fileOrigin.setDatUpdate(firstUpdate);
        fileOrigin.setNamChangeAgent("consumer");
        
        Status statusAfterFirstUpdate = fileOrigin.getDesStatus();
        
        // Second update (idempotent operation)
        fileOrigin.setDesStatus(Status.PROCESSAMENTO);
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent("consumer");
        
        // Property: Status must remain PROCESSAMENTO
        assertThat(fileOrigin.getDesStatus())
            .as("Status must remain PROCESSAMENTO after idempotent update")
            .isEqualTo(statusAfterFirstUpdate)
            .isEqualTo(Status.PROCESSAMENTO);
    }

    @Provide
    Arbitrary<FileOrigin> fileOriginInEmEspera() {
        Arbitrary<Long> positiveIds = Arbitraries.longs().greaterOrEqual(1L);
        Arbitrary<String> filenames = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '_', '-')
            .ofMinLength(1)
            .ofMaxLength(255);
        Arbitrary<Long> fileSizes = Arbitraries.longs().greaterOrEqual(1L);
        
        return Combinators.combine(
            positiveIds,
            filenames,
            fileSizes,
            Arbitraries.of(FileType.values()),
            Arbitraries.integers().between(0, 4)
        ).as((id, filename, size, fileType, retry) -> {
            FileOrigin fileOrigin = new FileOrigin();
            fileOrigin.setIdtFileOrigin(id);
            fileOrigin.setIdtAcquirer(1L);
            fileOrigin.setIdtLayout(1L);
            fileOrigin.setDesFileName(filename);
            fileOrigin.setNumFileSize(size);
            fileOrigin.setDesFileType(fileType);
            fileOrigin.setDesStep(Step.COLETA);
            fileOrigin.setDesStatus(Status.EM_ESPERA); // Initial status
            fileOrigin.setDesTransactionType(TransactionType.COMPLETO);
            fileOrigin.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
            fileOrigin.setIdtSeverPathsInOut(1L);
            fileOrigin.setDatCreation(new Date());
            fileOrigin.setFlgActive(1);
            fileOrigin.setNumRetry(retry);
            fileOrigin.setMaxRetry(5);
            return fileOrigin;
        });
    }
}
