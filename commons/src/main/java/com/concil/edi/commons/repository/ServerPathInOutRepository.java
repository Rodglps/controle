package com.concil.edi.commons.repository;

import com.concil.edi.commons.enums.LinkType;
import com.concil.edi.commons.entity.ServerPathInOut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ServerPathInOut entity operations.
 */
@Repository
public interface ServerPathInOutRepository extends JpaRepository<ServerPathInOut, Long> {
    
    /**
     * Find all active mappings.
     * @param flgActive Active flag (1 for active)
     * @return List of active mappings
     */
    List<ServerPathInOut> findByFlgActive(Integer flgActive);
    
    /**
     * Find principal mapping for a given origin path.
     * @param originPathId Origin path ID
     * @param linkType Link type (PRINCIPAL)
     * @param flgActive Active flag (1 for active)
     * @return Optional containing the principal mapping if found
     */
    Optional<ServerPathInOut> findBySeverPathOrigin_IdtSeverPathAndDesLinkTypeAndFlgActive(
        Long originPathId, 
        LinkType linkType, 
        Integer flgActive
    );
}
