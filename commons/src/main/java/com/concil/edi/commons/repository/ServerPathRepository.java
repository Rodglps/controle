package com.concil.edi.commons.repository;

import com.concil.edi.commons.enums.PathType;
import com.concil.edi.commons.entity.ServerPath;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ServerPath entity operations.
 */
@Repository
public interface ServerPathRepository extends JpaRepository<ServerPath, Long> {
    
    /**
     * Find all active server paths by type.
     * @param flgActive Active flag (1 for active)
     * @param desPathType Path type (ORIGIN or DESTINATION)
     * @return List of server paths matching criteria
     */
    List<ServerPath> findByFlgActiveAndDesPathType(Integer flgActive, PathType desPathType);
    
    /**
     * Find server path by ID with Server eagerly fetched.
     * This prevents LazyInitializationException when accessing server properties.
     * @param idtSeverPath Server path ID
     * @return Optional containing server path with server loaded
     */
    @EntityGraph(attributePaths = {"server"})
    Optional<ServerPath> findWithServerByIdtSeverPath(Long idtSeverPath);
}
