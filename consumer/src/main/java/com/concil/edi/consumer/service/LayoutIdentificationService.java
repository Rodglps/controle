package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.entity.LayoutIdentificationRule;
import com.concil.edi.commons.repository.LayoutIdentificationRuleRepository;
import com.concil.edi.commons.repository.LayoutRepository;
import com.concil.edi.commons.service.CriteriaComparator;
import com.concil.edi.commons.service.EncodingConverter;
import com.concil.edi.commons.service.RuleValidator;
import com.concil.edi.commons.service.extractor.ValueExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service responsible for identifying the layout of EDI files during the transfer process.
 * 
 * The identification process:
 * 1. Reads a buffer from the beginning of the file (configurable limit)
 * 2. Detects and converts encoding if necessary
 * 3. Searches for layouts by acquirer (ordered by idt_layout DESC for first-match wins)
 * 4. For each layout, retrieves active identification rules
 * 5. Applies all rules with AND operator (all must be satisfied)
 * 6. Returns the first matching layout ID
 * 
 * If no layout is identified, returns null, which triggers transfer interruption.
 */
@Service
public class LayoutIdentificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(LayoutIdentificationService.class);
    
    private final LayoutRepository layoutRepository;
    private final LayoutIdentificationRuleRepository ruleRepository;
    private final List<ValueExtractor> extractors;
    private final CriteriaComparator criteriaComparator;
    private final EncodingConverter encodingConverter;
    private final RuleValidator ruleValidator;
    
    @Value("${layout.identification.buffer-limit:7000}")
    private int bufferLimit;
    
    public LayoutIdentificationService(
            LayoutRepository layoutRepository,
            LayoutIdentificationRuleRepository ruleRepository,
            List<ValueExtractor> extractors,
            CriteriaComparator criteriaComparator,
            EncodingConverter encodingConverter,
            RuleValidator ruleValidator) {
        this.layoutRepository = layoutRepository;
        this.ruleRepository = ruleRepository;
        this.extractors = extractors;
        this.criteriaComparator = criteriaComparator;
        this.encodingConverter = encodingConverter;
        this.ruleValidator = ruleValidator;
    }
    
    /**
     * Identifies the layout of a file based on configured rules.
     * 
     * @param inputStream Stream of the file (only the buffer will be read)
     * @param filename Name of the file
     * @param acquirerId ID of the acquirer
     * @return ID of the identified layout, or null if not identified
     */
    public Long identifyLayout(InputStream inputStream, String filename, Long acquirerId) {
        logger.info("Starting layout identification for file: {}, acquirer: {}", filename, acquirerId);
        
        try {
            // Read buffer from the beginning of the file
            byte[] buffer = readBuffer(inputStream);
            logger.debug("Read {} bytes from file for identification", buffer.length);
            
            // Find active layouts for the acquirer, ordered by idt_layout DESC
            List<Layout> layouts = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
            logger.debug("Found {} active layouts for acquirer {}", layouts.size(), acquirerId);
            
            // Try each layout (first-match wins)
            for (Layout layout : layouts) {
                logger.debug("Checking layout: {} (ID: {})", layout.getCodLayout(), layout.getIdtLayout());
                
                if (matchesLayout(buffer, filename, layout)) {
                    logger.info("Layout identified: {} (ID: {}) for file: {}", 
                            layout.getCodLayout(), layout.getIdtLayout(), filename);
                    return layout.getIdtLayout();
                }
            }
            
            logger.warn("No layout identified for file: {}, acquirer: {}", filename, acquirerId);
            return null;
            
        } catch (IOException e) {
            logger.error("Error reading file buffer for identification: {}", filename, e);
            return null;
        }
    }
    
    /**
     * Reads a buffer from the input stream up to the configured limit.
     * 
     * @param inputStream The input stream to read from
     * @return Byte array containing the buffer
     * @throws IOException If an I/O error occurs
     */
    private byte[] readBuffer(InputStream inputStream) throws IOException {
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
        
        // Mark the stream so we can reset it after reading the buffer
        bufferedStream.mark(bufferLimit + 1);
        
        byte[] buffer = new byte[bufferLimit];
        int bytesRead = bufferedStream.read(buffer, 0, bufferLimit);
        
        // Reset the stream to the beginning
        bufferedStream.reset();
        
        // Return only the bytes actually read
        if (bytesRead < bufferLimit) {
            byte[] trimmedBuffer = new byte[bytesRead];
            System.arraycopy(buffer, 0, trimmedBuffer, 0, bytesRead);
            return trimmedBuffer;
        }
        
        return buffer;
    }
    
    /**
     * Checks if a layout matches the file based on all its identification rules.
     * All rules must be satisfied (AND operator).
     * 
     * @param buffer Byte buffer from the file
     * @param filename Name of the file
     * @param layout Layout to check
     * @return true if all rules are satisfied, false otherwise
     */
    private boolean matchesLayout(byte[] buffer, String filename, Layout layout) {
        // Get active rules for this layout
        List<LayoutIdentificationRule> rules = ruleRepository.findByIdtLayoutAndFlgActive(layout.getIdtLayout(), 1);
        
        if (rules.isEmpty()) {
            logger.debug("No active rules found for layout: {}", layout.getCodLayout());
            return false;
        }
        
        logger.debug("Checking {} rules for layout: {}", rules.size(), layout.getCodLayout());
        
        // Convert buffer to string with encoding fallback
        String bufferAsString = encodingConverter.convertWithFallback(buffer, layout.getDesEncoding());
        
        // All rules must be satisfied (AND operator)
        for (LayoutIdentificationRule rule : rules) {
            try {
                // Validate rule configuration
                ruleValidator.validate(rule, layout);
                
                // Extract value based on rule
                String extractedValue = extractValue(buffer, bufferAsString, filename, rule, layout);
                
                if (extractedValue == null) {
                    logger.debug("Rule '{}' failed: could not extract value", rule.getDesRule());
                    return false;
                }
                
                // Compare extracted value with expected value
                boolean matches = criteriaComparator.compare(
                        extractedValue,
                        rule.getDesValue(),
                        rule.getDesCriteriaType(),
                        rule.getDesFunctionOrigin(),
                        rule.getDesFunctionDest()
                );
                
                if (!matches) {
                    logger.debug("Rule '{}' failed: extracted='{}', expected='{}', criteria={}",
                            rule.getDesRule(), extractedValue, rule.getDesValue(), rule.getDesCriteriaType());
                    return false;
                }
                
                logger.debug("Rule '{}' satisfied", rule.getDesRule());
                
            } catch (IllegalArgumentException e) {
                logger.error("Invalid rule configuration for layout {}: {}", layout.getCodLayout(), e.getMessage());
                return false;
            } catch (Exception e) {
                logger.error("Error applying rule '{}' for layout {}: {}", 
                        rule.getDesRule(), layout.getCodLayout(), e.getMessage(), e);
                return false;
            }
        }
        
        // All rules satisfied
        return true;
    }
    
    /**
     * Extracts value from the file based on the identification rule.
     * Uses the appropriate ValueExtractor strategy based on value origin and file type.
     * 
     * @param buffer Byte buffer from the file
     * @param bufferAsString Buffer converted to string (with encoding handling)
     * @param filename Name of the file
     * @param rule Identification rule
     * @param layout Layout configuration
     * @return Extracted value, or null if extraction failed
     */
    private String extractValue(byte[] buffer, String bufferAsString, String filename, 
                                LayoutIdentificationRule rule, Layout layout) {
        // Find the appropriate extractor
        ValueExtractor extractor = extractors.stream()
                .filter(e -> e.supports(rule.getDesValueOrigin(), layout.getDesFileType()))
                .findFirst()
                .orElse(null);
        
        if (extractor == null) {
            logger.warn("No extractor found for value origin: {}, file type: {}", 
                    rule.getDesValueOrigin(), layout.getDesFileType());
            return null;
        }
        
        // For extractors that need the string buffer, we pass the converted buffer
        // The extractor will use either buffer or bufferAsString as needed
        return extractor.extractValue(buffer, filename, rule, layout);
    }
}
