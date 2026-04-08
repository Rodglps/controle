package com.concil.edi.commons.enums;

/**
 * Enum representing the type of transaction contained in a file.
 * Maps to VARCHAR2 column des_transaction_type in the file_origin table and layout table.
 * 
 * Values:
 * - COMPLETO: File contains both capture and financial transactions
 * - CAPTURA: File contains capture transactions (sales and adjustments)
 * - FINANCEIRO: File contains financial transactions (payments and settlements)
 * - AUXILIAR: File contains auxiliary transactions (balances, summaries, etc.)
 */
public enum TransactionType {
    COMPLETO,
    CAPTURA,
    FINANCEIRO,
    AUXILIAR
}
