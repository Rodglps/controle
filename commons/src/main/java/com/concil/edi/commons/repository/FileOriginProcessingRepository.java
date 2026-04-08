package com.concil.edi.commons.repository;

import com.concil.edi.commons.entity.FileOriginProcessing;
import com.concil.edi.commons.enums.Step;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FileOriginProcessing entity.
 * Provides query methods for managing processing state per file × client × step.
 */
@Repository
public interface FileOriginProcessingRepository extends JpaRepository<FileOriginProcessing, Long> {

    Optional<FileOriginProcessing> findByIdtFileOriginAndIdtClientAndDesStep(
            Long idtFileOrigin, Long idtClient, Step desStep);

    Optional<FileOriginProcessing> findByIdtFileOriginAndIdtClientIsNullAndDesStep(
            Long idtFileOrigin, Step desStep);

    List<FileOriginProcessing> findByIdtFileOriginAndDesStep(Long idtFileOrigin, Step desStep);
}
