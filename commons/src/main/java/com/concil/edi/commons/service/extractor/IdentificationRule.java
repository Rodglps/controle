package com.concil.edi.commons.service.extractor;

import com.concil.edi.commons.enums.CriteriaType;
import com.concil.edi.commons.enums.FunctionType;
import com.concil.edi.commons.enums.ValueOrigin;

/**
 * Common interface for identification rules.
 * Implemented by both LayoutIdentificationRule and CustomerIdentificationRule.
 * 
 * This interface provides a unified contract for accessing rule properties,
 * allowing shared components (ValueExtractor, CriteriaComparator, etc.) to work
 * with both layout identification and customer identification rules without adapters.
 */
public interface IdentificationRule {
    
    /**
     * Gets the origin of the value to be extracted.
     * @return ValueOrigin enum (FILENAME, HEADER, TAG, KEY)
     */
    ValueOrigin getDesValueOrigin();
    
    /**
     * Gets the criteria type for comparison.
     * @return CriteriaType enum (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL)
     */
    CriteriaType getDesCriteriaType();
    
    /**
     * Gets the start position for extraction (used in HEADER for TXT/CSV).
     * @return Start position (zero-based) or null if not applicable
     */
    Integer getNumStartPosition();
    
    /**
     * Gets the end position for extraction (used in HEADER for TXT).
     * @return End position or null if not applicable
     */
    Integer getNumEndPosition();
    
    /**
     * Gets the expected value for comparison.
     * @return Expected value string or null
     */
    String getDesValue();
    
    /**
     * Gets the XML tag path for extraction (used in TAG).
     * @return XPath string or null if not applicable
     */
    String getDesTag();
    
    /**
     * Gets the JSON key path for extraction (used in KEY).
     * @return JSON path in dot notation or null if not applicable
     */
    String getDesKey();
    
    /**
     * Gets the transformation function to apply to the extracted value.
     * @return FunctionType enum (UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE) or null
     */
    FunctionType getDesFunctionOrigin();
    
    /**
     * Gets the transformation function to apply to the expected value.
     * @return FunctionType enum (UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE) or null
     */
    FunctionType getDesFunctionDest();
    
    /**
     * Gets the rule description.
     * @return Rule description string
     */
    String getDesRule();
}
