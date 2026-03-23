package com.controle.arquivos.common.domain.enums;

/**
 * Enum que representa as etapas do processamento de arquivos.
 */
public enum EtapaProcessamento {
    COLETA,
    RAW,
    STAGING,
    ORDINATION,
    PROCESSING,
    PROCESSED,
    DELETE
}
