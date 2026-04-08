package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.LayoutIdentificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LayoutIdentificationRule entity.
 * Provides data access methods for layout identification rules.
 */
@Repository
public interface LayoutIdentificationRuleRepository extends JpaRepository<LayoutIdentificationRule, Long> {
    
    /**
     * Finds active identification rules for a specific layout.
     * All rules for a layout must be satisfied (AND operator) for the layout to be identified.
     * 
     * @param idtLayout The layout ID to filter by
     * @param flgActive The active flag (1 for active, 0 for inactive)
     * @return List of active rules for the layout
     */
    List<LayoutIdentificationRule> findByIdtLayoutAndFlgActive(Long idtLayout, Integer flgActive);
}
