package com.concil.edi.commons.enums;

/**
 * Enum representing transformation functions applied to values before comparison.
 * Used for both origin values and expected values in layout identification rules.
 */
public enum FunctionType {
    /**
     * Convert all characters to uppercase
     */
    UPPERCASE,
    
    /**
     * Convert all characters to lowercase
     */
    LOWERCASE,
    
    /**
     * Convert first letter to uppercase, remaining to lowercase
     */
    INITCAP,
    
    /**
     * Remove leading and trailing whitespace
     */
    TRIM,
    
    /**
     * No transformation applied
     */
    NONE
}
