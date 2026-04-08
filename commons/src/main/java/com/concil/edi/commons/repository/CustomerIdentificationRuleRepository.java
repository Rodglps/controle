package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.CustomerIdentificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for CustomerIdentificationRule entity.
 * Provides queries for finding active rules for customer identifications.
 */
@Repository
public interface CustomerIdentificationRuleRepository extends JpaRepository<CustomerIdentificationRule, Long> {
    
    /**
     * Finds active rules for a customer identification.
     * 
     * @param identificationId ID of the customer identification
     * @param flgActive Active flag (1 for active)
     * @return List of active rules
     */
    List<CustomerIdentificationRule> findByIdtIdentificationAndFlgActive(
            Long identificationId, Integer flgActive);
}
