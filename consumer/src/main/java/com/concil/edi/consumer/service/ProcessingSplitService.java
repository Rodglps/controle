package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.FileOriginProcessing;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.repository.FileOriginProcessingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service for creating and updating processing records in file_origin_processing
 * during the COLETA step. Tracks processing state per file × client × step.
 *
 * This service must NOT throw exceptions that would interrupt the main
 * FileTransferListener flow. All errors are logged internally.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 6.1, 6.2, 7.1, 7.2, 8.1, 8.2, 9.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingSplitService {

    private static final String CHANGE_AGENT = "consumer-service";

    private final FileOriginProcessingRepository fileOriginProcessingRepository;

    /**
     * Create initial processing records for the COLETA step.
     * If clientIds is empty, creates a single record with idt_client=NULL.
     * If clientIds is not empty, creates one record per client.
     * On retry, updates existing records instead of creating duplicates.
     *
     * @param fileOriginId  the file origin ID
     * @param clientIds     list of identified client IDs (may be empty)
     * @param stepStartTime timestamp captured at the start of handleFileTransfer
     */
    @Transactional
    public void createInitialRecords(Long fileOriginId, List<Long> clientIds, Date stepStartTime) {
        try {
            List<Long> idsToProcess = (clientIds == null || clientIds.isEmpty())
                    ? Collections.singletonList(null)
                    : clientIds;

            for (Long clientId : idsToProcess) {
                Optional<FileOriginProcessing> existing = findExistingRecord(fileOriginId, clientId);

                if (existing.isPresent()) {
                    FileOriginProcessing record = existing.get();
                    record.setDesStatus(Status.PROCESSAMENTO);
                    record.setDatStepStart(stepStartTime);
                    record.setDatUpdate(new Date());
                    record.setNamChangeAgent(CHANGE_AGENT);
                    fileOriginProcessingRepository.save(record);
                    log.info("Updated existing processing record for file_origin {} client {}", fileOriginId, clientId);
                } else {
                    FileOriginProcessing record = new FileOriginProcessing();
                    record.setIdtFileOrigin(fileOriginId);
                    record.setDesStep(Step.COLETA);
                    record.setDesStatus(Status.PROCESSAMENTO);
                    record.setIdtClient(clientId);
                    record.setDatStepStart(stepStartTime);
                    record.setDatCreation(new Date());
                    record.setNamChangeAgent(CHANGE_AGENT);
                    fileOriginProcessingRepository.save(record);
                    log.info("Created processing record for file_origin {} client {}", fileOriginId, clientId);
                }
            }
        } catch (Exception e) {
            log.error("Error creating initial processing records for file_origin {}: {}", fileOriginId, e.getMessage(), e);
        }
    }

    /**
     * Update all COLETA processing records for a file to CONCLUIDO status.
     *
     * @param fileOriginId the file origin ID
     */
    @Transactional
    public void completeRecords(Long fileOriginId) {
        try {
            List<FileOriginProcessing> records = fileOriginProcessingRepository
                    .findByIdtFileOriginAndDesStep(fileOriginId, Step.COLETA);

            Date now = new Date();
            for (FileOriginProcessing record : records) {
                record.setDesStatus(Status.CONCLUIDO);
                record.setDatStepEnd(now);
                record.setDatUpdate(now);
                record.setDesMessageError(null);
                fileOriginProcessingRepository.save(record);
            }

            log.info("Completed {} processing records for file_origin {}", records.size(), fileOriginId);
        } catch (Exception e) {
            log.error("Error completing processing records for file_origin {}: {}", fileOriginId, e.getMessage(), e);
        }
    }

    /**
     * Update all COLETA processing records for a file to ERRO status.
     *
     * @param fileOriginId the file origin ID
     * @param errorMessage the error message to record
     */
    @Transactional
    public void failRecords(Long fileOriginId, String errorMessage) {
        try {
            List<FileOriginProcessing> records = fileOriginProcessingRepository
                    .findByIdtFileOriginAndDesStep(fileOriginId, Step.COLETA);

            Date now = new Date();
            for (FileOriginProcessing record : records) {
                record.setDesStatus(Status.ERRO);
                record.setDesMessageError(errorMessage);
                record.setDatStepEnd(now);
                record.setDatUpdate(now);
                fileOriginProcessingRepository.save(record);
            }

            log.info("Failed {} processing records for file_origin {} with error: {}", records.size(), fileOriginId, errorMessage);
        } catch (Exception e) {
            log.error("Error failing processing records for file_origin {}: {}", fileOriginId, e.getMessage(), e);
        }
    }

    private Optional<FileOriginProcessing> findExistingRecord(Long fileOriginId, Long clientId) {
        if (clientId == null) {
            return fileOriginProcessingRepository
                    .findByIdtFileOriginAndIdtClientIsNullAndDesStep(fileOriginId, Step.COLETA);
        }
        return fileOriginProcessingRepository
                .findByIdtFileOriginAndIdtClientAndDesStep(fileOriginId, clientId, Step.COLETA);
    }
}
