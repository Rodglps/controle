package com.concil.edi.commons.enums;

/**
 * Enum representing the status of a file in a processing step.
 * Maps to VARCHAR2 column des_status in the file_origin table.
 */
public enum Status {
    EM_ESPERA,
    PROCESSAMENTO,
    CONCLUIDO,
    ERRO
}
