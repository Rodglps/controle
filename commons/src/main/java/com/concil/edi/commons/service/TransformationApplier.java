package com.concil.edi.commons.service;

import com.concil.edi.commons.enums.FunctionType;
import org.springframework.stereotype.Component;

/**
 * Component responsible for applying transformation functions to values.
 * Supports UPPERCASE, LOWERCASE, INITCAP, TRIM, and NONE transformations.
 * 
 * Shared between layout identification and customer identification.
 */
@Component
public class TransformationApplier {

    /**
     * Applies the specified transformation function to a value.
     * 
     * @param value The value to transform (can be null)
     * @param functionType The transformation function to apply
     * @return The transformed value, or null if input is null
     */
    public String applyTransformation(String value, FunctionType functionType) {
        if (value == null) {
            return null;
        }
        
        if (functionType == null) {
            functionType = FunctionType.NONE;
        }
        
        return switch (functionType) {
            case UPPERCASE -> value.toUpperCase();
            case LOWERCASE -> value.toLowerCase();
            case INITCAP -> applyInitCap(value);
            case TRIM -> value.trim();
            case NONE -> value;
        };
    }
    
    /**
     * Applies INITCAP transformation: first letter uppercase, remaining lowercase.
     * Handles empty strings and preserves leading/trailing whitespace.
     * 
     * @param value The value to transform
     * @return The transformed value with first letter capitalized
     */
    private String applyInitCap(String value) {
        if (value.isEmpty()) {
            return value;
        }
        
        // Find the first letter (skip leading whitespace)
        int firstLetterIndex = -1;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                firstLetterIndex = i;
                break;
            }
        }
        
        // If no letter found, return lowercase version
        if (firstLetterIndex == -1) {
            return value.toLowerCase();
        }
        
        // Build result: prefix + uppercase first letter + lowercase rest
        StringBuilder result = new StringBuilder();
        result.append(value.substring(0, firstLetterIndex).toLowerCase());
        result.append(Character.toUpperCase(value.charAt(firstLetterIndex)));
        if (firstLetterIndex + 1 < value.length()) {
            result.append(value.substring(firstLetterIndex + 1).toLowerCase());
        }
        
        return result.toString();
    }
}
