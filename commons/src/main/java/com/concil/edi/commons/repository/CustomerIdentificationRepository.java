package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.CustomerIdentification;
import com.concil.edi.commons.enums.ValueOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for CustomerIdentification entity.
 * Provides queries for finding customer identifications based on acquirer, layout, and value origin.
 */
@Repository
public interface CustomerIdentificationRepository extends JpaRepository<CustomerIdentification, Long> {
    
    /**
     * Finds active customer identifications for FILENAME rules (layout not required).
     * Used when layout is not identified.
     * 
     * @param acquirerId ID of the acquirer
     * @param valueOrigin Value origin (FILENAME)
     * @return List of customer identifications ordered by num_process_weight DESC
     */
    @Query("SELECT DISTINCT ci FROM CustomerIdentification ci " +
           "JOIN CustomerIdentificationRule cir ON ci.idtIdentification = cir.idtIdentification " +
           "WHERE ci.idtAcquirer = :acquirerId " +
           "AND ci.flgActive = 1 " +
           "AND cir.flgActive = 1 " +
           "AND cir.desValueOrigin = :valueOrigin " +
           "ORDER BY ci.numProcessWeight DESC NULLS LAST")
    List<CustomerIdentification> findByAcquirerAndValueOrigin(
            @Param("acquirerId") Long acquirerId,
            @Param("valueOrigin") ValueOrigin valueOrigin);
    
    /**
     * Finds active customer identifications for content-based rules (HEADER, TAG, KEY).
     * Used when layout is identified.
     * 
     * @param acquirerId ID of the acquirer
     * @param layoutId ID of the layout
     * @param valueOrigins List of value origins (HEADER, TAG, KEY)
     * @return List of customer identifications ordered by num_process_weight DESC
     */
    @Query("SELECT DISTINCT ci FROM CustomerIdentification ci " +
           "JOIN CustomerIdentificationRule cir ON ci.idtIdentification = cir.idtIdentification " +
           "WHERE ci.idtAcquirer = :acquirerId " +
           "AND ci.idtLayout = :layoutId " +
           "AND ci.flgActive = 1 " +
           "AND cir.flgActive = 1 " +
           "AND cir.desValueOrigin IN :valueOrigins " +
           "ORDER BY ci.numProcessWeight DESC NULLS LAST")
    List<CustomerIdentification> findByAcquirerAndLayoutAndValueOrigins(
            @Param("acquirerId") Long acquirerId,
            @Param("layoutId") Long layoutId,
            @Param("valueOrigins") List<ValueOrigin> valueOrigins);
}
