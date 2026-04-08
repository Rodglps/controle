package com.concil.edi.commons.enums;

/**
 * Enum representing the origin of the value used for layout identification.
 * Defines where the identification value should be extracted from.
 */
public enum ValueOrigin {
    /**
     * Value extracted from the filename
     */
    FILENAME,
    
    /**
     * Value extracted from file header (TXT or CSV)
     */
    HEADER,
    
    /**
     * Value extracted from XML tag using XPath
     */
    TAG,
    
    /**
     * Value extracted from JSON key using dot notation
     */
    KEY
}
