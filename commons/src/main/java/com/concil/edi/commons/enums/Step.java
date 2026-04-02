package com.concil.edi.commons.enums;

/**
 * Enum representing the processing step of a file.
 * Maps to VARCHAR2 column des_step in the file_origin table.
 */
public enum Step {
    COLETA,
    DELETE,
    RAW,
    STAGING,
    ORDINATION,
    PROCESSING,
    PROCESSED
}
