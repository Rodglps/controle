package com.concil.edi.commons.repository;

import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.entity.FileOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Repository for FileOrigin entity operations.
 */
@Repository
public interface FileOriginRepository extends JpaRepository<FileOrigin, Long> {
    
    /**
     * Check if a file already exists based on unique constraint file_origin_idx_01.
     * @param desFileName File name
     * @param idtAcquirer Acquirer ID
     * @param datTimestampFile File timestamp
     * @param flgActive Active flag (1 for active)
     * @return Optional containing the file if found
     */
    Optional<FileOrigin> findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
        String desFileName,
        Long idtAcquirer,
        Timestamp datTimestampFile,
        Integer flgActive
    );
    
    /**
     * Find failed publications that need retry.
     * Files with des_step=COLETA, des_status=ERRO, num_retry < max_retry.
     * Only retries files with ERRO status (not files with idt_layout=0).
     * @return List of files that failed publication and can be retried
     */
    @Query("SELECT f FROM FileOrigin f WHERE f.desStep = :step AND f.desStatus = :status AND f.numRetry < f.maxRetry AND f.flgActive = 1")
    List<FileOrigin> findFailedPublications(
        @Param("step") Step step,
        @Param("status") Status status
    );

    /**
     * Find files pending origin removal (failed with REMOVE_ORIGIN_FILE_ERROR marker).
     * @param step Processing step
     * @param status File status
     * @param marker Error marker string to search in desMessageError
     * @return List of files pending removal retry
     */
    @Query("SELECT f FROM FileOrigin f WHERE f.desStep = :step AND f.desStatus = :status " +
           "AND f.desMessageError LIKE %:marker% AND f.numRetry < f.maxRetry AND f.flgActive = 1")
    List<FileOrigin> findPendingRemovalFiles(
        @Param("step") Step step,
        @Param("status") Status status,
        @Param("marker") String marker
    );
}
