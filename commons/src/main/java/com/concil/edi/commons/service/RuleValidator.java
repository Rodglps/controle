package com.concil.edi.commons.service;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.service.extractor.IdentificationRule;
import org.springframework.stereotype.Component;

/**
 * Component responsible for validating identification rule configurations.
 * Shared between layout identification and customer identification.
 * 
 * Validates that rules have all required fields based on their value origin and file type:
 * - FILENAME: requires des_value
 * - HEADER TXT: requires num_start_position
 * - HEADER CSV: requires num_start_position and des_column_separator in layout
 * - TAG: requires des_tag
 * - KEY: requires des_key
 * 
 * Also validates that positions are non-negative.
 */
@Component
public class RuleValidator {
    
    /**
     * Validates an identification rule configuration.
     * 
     * @param rule The rule to validate (LayoutIdentificationRule or CustomerIdentificationRule)
     * @param layout The layout configuration (needed for CSV validation)
     * @throws IllegalArgumentException if the rule configuration is invalid
     */
    public void validate(IdentificationRule rule, Layout layout) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }
        
        if (layout == null) {
            throw new IllegalArgumentException("Layout cannot be null");
        }
        
        // Validate based on value origin
        switch (rule.getDesValueOrigin()) {
            case FILENAME -> validateFilenameRule(rule);
            case HEADER -> validateHeaderRule(rule, layout);
            case TAG -> validateTagRule(rule);
            case KEY -> validateKeyRule(rule);
        }
        
        // Validate positions are non-negative
        validatePositions(rule);
    }
    
    /**
     * Validates a FILENAME rule.
     * Requires: des_value
     */
    private void validateFilenameRule(IdentificationRule rule) {
        if (rule.getDesValue() == null || rule.getDesValue().isEmpty()) {
            throw new IllegalArgumentException(
                    "des_value is required for FILENAME rules (rule: " + rule.getDesRule() + ")");
        }
    }
    
    /**
     * Validates a HEADER rule (TXT or CSV).
     * TXT requires: num_start_position
     * CSV requires: num_start_position, des_column_separator in layout
     */
    private void validateHeaderRule(IdentificationRule rule, Layout layout) {
        if (rule.getNumStartPosition() == null) {
            throw new IllegalArgumentException(
                    "num_start_position is required for HEADER rules (rule: " + rule.getDesRule() + ")");
        }
        
        // CSV-specific validation
        if (layout.getDesFileType() == FileType.CSV) {
            if (layout.getDesColumnSeparator() == null || layout.getDesColumnSeparator().isEmpty()) {
                throw new IllegalArgumentException(
                        "des_column_separator is required in layout for HEADER CSV rules (layout: " + 
                        layout.getCodLayout() + ")");
            }
        }
    }
    
    /**
     * Validates a TAG rule (XML).
     * Requires: des_tag
     */
    private void validateTagRule(IdentificationRule rule) {
        if (rule.getDesTag() == null || rule.getDesTag().isEmpty()) {
            throw new IllegalArgumentException(
                    "des_tag is required for TAG rules (rule: " + rule.getDesRule() + ")");
        }
    }
    
    /**
     * Validates a KEY rule (JSON).
     * Requires: des_key
     */
    private void validateKeyRule(IdentificationRule rule) {
        if (rule.getDesKey() == null || rule.getDesKey().isEmpty()) {
            throw new IllegalArgumentException(
                    "des_key is required for KEY rules (rule: " + rule.getDesRule() + ")");
        }
    }
    
    /**
     * Validates that positions are non-negative.
     */
    private void validatePositions(IdentificationRule rule) {
        if (rule.getNumStartPosition() != null && rule.getNumStartPosition() < 0) {
            throw new IllegalArgumentException(
                    "num_start_position cannot be negative (rule: " + rule.getDesRule() + 
                    ", value: " + rule.getNumStartPosition() + ")");
        }
        
        if (rule.getNumEndPosition() != null && rule.getNumEndPosition() < 0) {
            throw new IllegalArgumentException(
                    "num_end_position cannot be negative (rule: " + rule.getDesRule() + 
                    ", value: " + rule.getNumEndPosition() + ")");
        }
    }
}
