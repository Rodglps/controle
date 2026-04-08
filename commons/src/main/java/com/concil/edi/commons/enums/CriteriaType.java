package com.concil.edi.commons.enums;

/**
 * Enum representing the type of comparison criteria for layout identification.
 * Defines how the extracted value should be compared with the expected value.
 */
public enum CriteriaType {
    /**
     * Origin value starts with expected value
     */
    COMECA_COM,
    
    /**
     * Origin value ends with expected value
     */
    TERMINA_COM,
    
    /**
     * Origin value contains expected value at any position
     */
    CONTEM,
    
    /**
     * Origin value is contained within expected value
     */
    CONTIDO,
    
    /**
     * Origin value is exactly equal to expected value
     */
    IGUAL
}
