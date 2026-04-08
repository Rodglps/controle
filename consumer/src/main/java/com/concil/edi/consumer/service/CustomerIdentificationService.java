package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.CustomerIdentification;
import com.concil.edi.commons.entity.CustomerIdentificationRule;
import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.ValueOrigin;
import com.concil.edi.commons.repository.CustomerIdentificationRepository;
import com.concil.edi.commons.repository.CustomerIdentificationRuleRepository;
import com.concil.edi.commons.repository.LayoutRepository;
import com.concil.edi.commons.service.CriteriaComparator;
import com.concil.edi.commons.service.RuleValidator;
import com.concil.edi.commons.service.extractor.ValueExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for identifying customers that own EDI files.
 * 
 * Identification process:
 * 1. Retrieves customer_identification records filtered by acquirer (and layout if identified)
 * 2. For each identification, retrieves active rules
 * 3. Applies all rules with AND operator
 * 4. Returns list of all identified customer IDs
 * 
 * Supports four identification types:
 * - FILENAME: Based on file name
 * - HEADER: Based on file content (positional for TXT, column for CSV)
 * - TAG: Based on XML paths (XPath)
 * - KEY: Based on JSON paths (dot notation)
 * 
 * USES SHARED COMPONENTS FROM COMMONS:
 * - CriteriaComparator: For comparing values with criteria types
 * - TransformationApplier: For applying transformation functions (via CriteriaComparator)
 * - RuleValidator: For validating rule configurations
 * - ValueExtractor implementations: For extracting values from files
 */
@Service
public class CustomerIdentificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomerIdentificationService.class);
    
    private final CustomerIdentificationRepository customerIdentificationRepository;
    private final CustomerIdentificationRuleRepository ruleRepository;
    private final LayoutRepository layoutRepository;
    private final List<ValueExtractor> extractors;
    private final CriteriaComparator criteriaComparator;
    private final RuleValidator ruleValidator;
    
    public CustomerIdentificationService(
            CustomerIdentificationRepository customerIdentificationRepository,
            CustomerIdentificationRuleRepository ruleRepository,
            LayoutRepository layoutRepository,
            List<ValueExtractor> extractors,
            CriteriaComparator criteriaComparator,
            RuleValidator ruleValidator) {
        this.customerIdentificationRepository = customerIdentificationRepository;
        this.ruleRepository = ruleRepository;
        this.layoutRepository = layoutRepository;
        this.extractors = extractors;
        this.criteriaComparator = criteriaComparator;
        this.ruleValidator = ruleValidator;
    }
    
    /**
     * Identifies customers that own the file based on configured rules.
     * 
     * @param buffer First 7000 bytes of the file (reused from layout identification)
     * @param filename Name of the file
     * @param acquirerId ID of the acquirer
     * @param layoutId ID of the identified layout (null if not identified)
     * @return List of customer IDs that own the file (empty if none identified)
     */
    public List<Long> identifyCustomers(byte[] buffer, String filename, Long acquirerId, Long layoutId) {
        logger.info("Starting customer identification for file: {}, acquirer: {}, layout: {}", 
                    filename, acquirerId, layoutId);
        
        // Validate buffer
        if (buffer == null || buffer.length == 0) {
            logger.warn("Empty buffer provided for customer identification");
            return Collections.emptyList();
        }
        
        List<Long> identifiedClients = new ArrayList<>();
        
        // Retrieve customer identifications based on layout status
        List<CustomerIdentification> identifications = retrieveIdentifications(acquirerId, layoutId);
        logger.debug("Found {} customer identifications to check", identifications.size());
        
        // Check each identification
        for (CustomerIdentification identification : identifications) {
            if (matchesIdentification(buffer, filename, identification, layoutId)) {
                identifiedClients.add(identification.getIdtClient());
                logger.info("Customer {} identified for file: {}", 
                           identification.getIdtClient(), filename);
            }
        }
        
        if (identifiedClients.isEmpty()) {
            logger.info("No customers identified for file: {}", filename);
        } else {
            logger.info("Identified {} customer(s) for file: {}", identifiedClients.size(), filename);
        }
        
        return identifiedClients;
    }
    
    /**
     * Retrieves customer identifications based on layout status.
     * 
     * If layoutId is NULL: only FILENAME rules are retrieved
     * If layoutId is not NULL: FILENAME rules + content-based rules (HEADER, TAG, KEY) are retrieved
     * 
     * Results are ordered by num_process_weight DESC (highest weight first).
     * 
     * @param acquirerId ID of the acquirer
     * @param layoutId ID of the identified layout (null if not identified)
     * @return List of customer identifications ordered by weight
     */
    private List<CustomerIdentification> retrieveIdentifications(Long acquirerId, Long layoutId) {
        if (layoutId == null) {
            // Layout not identified: only FILENAME rules
            logger.debug("Layout not identified, retrieving only FILENAME rules for acquirer: {}", acquirerId);
            return customerIdentificationRepository.findByAcquirerAndValueOrigin(
                    acquirerId, ValueOrigin.FILENAME);
        } else {
            // Layout identified: FILENAME + content-based rules
            logger.debug("Layout identified ({}), retrieving FILENAME and content-based rules for acquirer: {}", 
                        layoutId, acquirerId);
            
            // Get FILENAME rules
            List<CustomerIdentification> filenameIdentifications = 
                    customerIdentificationRepository.findByAcquirerAndValueOrigin(
                            acquirerId, ValueOrigin.FILENAME);
            
            // Get content-based rules (HEADER, TAG, KEY)
            List<ValueOrigin> contentOrigins = List.of(
                    ValueOrigin.HEADER, 
                    ValueOrigin.TAG, 
                    ValueOrigin.KEY);
            List<CustomerIdentification> contentIdentifications = 
                    customerIdentificationRepository.findByAcquirerAndLayoutAndValueOrigins(
                            acquirerId, layoutId, contentOrigins);
            
            // Union both lists (using Set to avoid duplicates)
            Set<CustomerIdentification> allIdentifications = new HashSet<>();
            allIdentifications.addAll(filenameIdentifications);
            allIdentifications.addAll(contentIdentifications);
            
            // Convert back to list and sort by weight
            List<CustomerIdentification> result = new ArrayList<>(allIdentifications);
            result.sort((a, b) -> {
                Integer weightA = a.getNumProcessWeight();
                Integer weightB = b.getNumProcessWeight();
                
                // NULL weights go last
                if (weightA == null && weightB == null) return 0;
                if (weightA == null) return 1;
                if (weightB == null) return -1;
                
                // Higher weight first (descending order)
                return weightB.compareTo(weightA);
            });
            
            return result;
        }
    }
    
    /**
     * Checks if all rules of a customer identification are satisfied (AND operator).
     * 
     * @param buffer File buffer
     * @param filename File name
     * @param identification Customer identification to check
     * @param layoutId Layout ID (can be null)
     * @return true if ALL active rules are satisfied, false otherwise
     */
    private boolean matchesIdentification(byte[] buffer, String filename, 
                                         CustomerIdentification identification, Long layoutId) {
        // Retrieve active rules for this identification
        List<CustomerIdentificationRule> rules = ruleRepository.findByIdtIdentificationAndFlgActive(
                identification.getIdtIdentification(), 1);
        
        if (rules.isEmpty()) {
            logger.debug("No active rules found for identification {}, skipping", 
                        identification.getIdtIdentification());
            return false;
        }
        
        logger.debug("Checking {} active rules for identification {}", 
                    rules.size(), identification.getIdtIdentification());
        
        // Apply AND operator: ALL rules must be satisfied
        for (CustomerIdentificationRule rule : rules) {
            try {
                // Extract value based on rule
                String extractedValue = extractValue(buffer, filename, rule, layoutId);
                
                if (extractedValue == null) {
                    logger.debug("Failed to extract value for rule '{}', identification {} not matched", 
                                rule.getDesRule(), identification.getIdtIdentification());
                    return false;
                }
                
                // Apply rule comparison
                boolean matches = applyRuleComparison(extractedValue, rule);
                
                if (!matches) {
                    logger.debug("Rule '{}' not satisfied for identification {}", 
                                rule.getDesRule(), identification.getIdtIdentification());
                    return false;
                }
                
                logger.debug("Rule '{}' satisfied for identification {}", 
                            rule.getDesRule(), identification.getIdtIdentification());
                
            } catch (Exception e) {
                // For TAG/KEY extraction errors, log and return false
                if (rule.getDesValueOrigin() == ValueOrigin.TAG || 
                    rule.getDesValueOrigin() == ValueOrigin.KEY) {
                    logger.error("Failed to extract {} for rule '{}' in identification {}: {}", 
                                rule.getDesValueOrigin(), rule.getDesRule(), 
                                identification.getIdtIdentification(), e.getMessage());
                    return false;
                } else {
                    // For other errors, propagate
                    throw e;
                }
            }
        }
        
        // All rules satisfied
        logger.debug("All rules satisfied for identification {}", identification.getIdtIdentification());
        return true;
    }
    
    /**
     * Extracts value from buffer based on the rule using strategy pattern.
     * 
     * Delegates to appropriate ValueExtractor based on value origin and file type.
     * For TAG/KEY extraction errors, logs and returns null.
     * 
     * @param buffer File buffer
     * @param filename File name
     * @param rule Customer identification rule
     * @param layoutId Layout ID (can be null)
     * @return Extracted value or null if not found/error
     */
    private String extractValue(byte[] buffer, String filename, 
                               CustomerIdentificationRule rule, Long layoutId) {
        // Get layout if needed for content-based rules
        Layout layout = null;
        if (layoutId != null && rule.getDesValueOrigin() != ValueOrigin.FILENAME) {
            layout = layoutRepository.findById(layoutId).orElse(null);
            if (layout == null) {
                logger.warn("Layout {} not found for content-based rule '{}'", 
                           layoutId, rule.getDesRule());
                return null;
            }
            
            // Validate rule configuration
            try {
                ruleValidator.validate(rule, layout);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid rule configuration for rule '{}': {}", 
                            rule.getDesRule(), e.getMessage());
                return null;
            }
        }
        
        // Find appropriate extractor using strategy pattern
        for (ValueExtractor extractor : extractors) {
            if (extractor.supports(rule.getDesValueOrigin(), 
                                  layout != null ? layout.getDesFileType() : null)) {
                try {
                    String value = extractor.extractValue(buffer, filename, rule, layout);
                    logger.debug("Extracted value '{}' using {} for rule '{}'", 
                                value, extractor.getClass().getSimpleName(), rule.getDesRule());
                    return value;
                } catch (Exception e) {
                    // For TAG/KEY, log error and return null (handled by caller)
                    if (rule.getDesValueOrigin() == ValueOrigin.TAG || 
                        rule.getDesValueOrigin() == ValueOrigin.KEY) {
                        logger.error("Failed to extract {} '{}' for rule '{}': {}", 
                                    rule.getDesValueOrigin(), 
                                    rule.getDesValueOrigin() == ValueOrigin.TAG ? 
                                        rule.getDesTag() : rule.getDesKey(),
                                    rule.getDesRule(), e.getMessage());
                        return null;
                    } else {
                        // For other origins, propagate exception
                        throw e;
                    }
                }
            }
        }
        
        // No extractor found
        logger.warn("No extractor found for value origin {} and file type {}", 
                   rule.getDesValueOrigin(), layout != null ? layout.getDesFileType() : "N/A");
        return null;
    }
    
    /**
     * Applies transformation functions and compares values using criteria type.
     * 
     * Uses TransformationApplier (via CriteriaComparator) for des_function_origin and des_function_dest.
     * Uses CriteriaComparator for comparison based on des_criteria_type.
     * 
     * @param extractedValue Value extracted from file
     * @param rule Customer identification rule
     * @return true if comparison satisfies the criteria, false otherwise
     */
    private boolean applyRuleComparison(String extractedValue, CustomerIdentificationRule rule) {
        // Use CriteriaComparator which handles both transformation and comparison
        boolean matches = criteriaComparator.compare(
                extractedValue,
                rule.getDesValue(),
                rule.getDesCriteriaType(),
                rule.getDesFunctionOrigin(),
                rule.getDesFunctionDest()
        );
        
        logger.debug("Comparison result for rule '{}': extracted='{}', expected='{}', criteria={}, result={}", 
                    rule.getDesRule(), extractedValue, rule.getDesValue(), 
                    rule.getDesCriteriaType(), matches);
        
        return matches;
    }
}
