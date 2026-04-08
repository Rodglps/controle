package com.concil.edi.commons.enums;

/**
 * Enum representing the distribution frequency of EDI files.
 * Defines when files are expected to be available from acquirers.
 */
public enum DistributionType {
    /**
     * Files distributed daily
     */
    DIARIO,
    
    /**
     * Files distributed on business days only
     */
    DIAS_UTEIS,
    
    /**
     * Files distributed Monday through Friday
     */
    SEGUNDA_A_SEXTA,
    
    /**
     * Files distributed on Sundays
     */
    DOMINGO,
    
    /**
     * Files distributed on Mondays
     */
    SEGUNDA_FEIRA,
    
    /**
     * Files distributed on Tuesdays
     */
    TERCA_FEIRA,
    
    /**
     * Files distributed on Wednesdays
     */
    QUARTA_FEIRA,
    
    /**
     * Files distributed on Thursdays
     */
    QUINTA_FEIRA,
    
    /**
     * Files distributed on Fridays
     */
    SEXTA_FEIRA,
    
    /**
     * Files distributed on Saturdays
     */
    SABADO,
    
    /**
     * Files distributed seasonally or irregularly
     */
    SAZONAL
}
