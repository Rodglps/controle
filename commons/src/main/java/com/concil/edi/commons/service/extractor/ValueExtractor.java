package com.concil.edi.commons.service.extractor;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ValueOrigin;

/**
 * Strategy interface for extracting values from files based on identification rules.
 * Shared between layout identification and customer identification.
 * Different implementations handle different value origins (FILENAME, HEADER, TAG, KEY).
 */
public interface ValueExtractor {
    
    /**
     * Extracts value from buffer based on the rule.
     * 
     * @param buffer Buffer of bytes from the file
     * @param filename Name of the file
     * @param rule Identification rule (can be LayoutIdentificationRule or CustomerIdentificationRule)
     * @param layout Layout configuration (for accessing des_column_separator, etc)
     * @return Extracted value or null if not found
     */
    String extractValue(byte[] buffer, String filename, IdentificationRule rule, Layout layout);
    
    /**
     * Checks if this extractor supports the given value origin and file type.
     * 
     * @param valueOrigin The origin of the value (FILENAME, HEADER, TAG, KEY)
     * @param fileType The type of file (TXT, CSV, XML, JSON, OFX)
     * @return true if this extractor can handle the combination
     */
    boolean supports(ValueOrigin valueOrigin, FileType fileType);
}
