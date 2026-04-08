package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.Layout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Layout entity.
 * Provides data access methods for layout identification configuration.
 */
@Repository
public interface LayoutRepository extends JpaRepository<Layout, Long> {
    
    /**
     * Finds active layouts for a specific acquirer, ordered by layout ID in descending order.
     * This implements the first-match wins strategy where newer layouts (higher IDs) are checked first.
     * 
     * @param idtAcquirer The acquirer ID to filter by
     * @param flgActive The active flag (1 for active, 0 for inactive)
     * @return List of layouts ordered by idt_layout DESC
     */
    List<Layout> findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(Long idtAcquirer, Integer flgActive);
}
