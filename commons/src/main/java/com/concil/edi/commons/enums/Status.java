package com.concil.edi.commons.enums;

/**
 * Enum representing the status of a file in a processing step.
 * Maps to VARCHAR2 column des_status in the file_origin table.
 * 
 * Values:
 * - EM_ESPERA: File is waiting to be processed
 * - PROCESSAMENTO: File is currently being processed
 * - CONCLUIDO: File was processed successfully (layout identified or set to layout 0)
 * - ERRO: File processing failed (will be retried by Producer)
 */
public enum Status {
    EM_ESPERA,
    PROCESSAMENTO,
    CONCLUIDO,
    ERRO
}
