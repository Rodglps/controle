package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.FileOriginClients;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for FileOriginClients entity.
 * Provides basic CRUD operations for managing identified customers for files.
 */
@Repository
public interface FileOriginClientsRepository extends JpaRepository<FileOriginClients, Long> {
    // Basic CRUD operations provided by JpaRepository
}
