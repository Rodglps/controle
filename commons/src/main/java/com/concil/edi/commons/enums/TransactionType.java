package com.concil.edi.commons.enums;

/**
 * Enum representing the type of transaction contained in a file.
 * Maps to VARCHAR2 column des_transaction_type in the file_origin table.
 */
public enum TransactionType {
    COMPLETO,
    CAPTURA,
    FINANCEIRO
}
