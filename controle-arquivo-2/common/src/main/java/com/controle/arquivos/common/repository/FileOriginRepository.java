package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.FileOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repositório JPA para FileOrigin.
 * Fornece query customizada para busca por nome, adquirente e timestamp.
 */
@Repository
public interface FileOriginRepository extends JpaRepository<FileOrigin, Long> {

    /**
     * Busca arquivo por nome, adquirente e timestamp.
     * Usado para deduplicação de arquivos durante coleta.
     *
     * @param fileName nome do arquivo
     * @param acquirerId ID do adquirente
     * @param fileTimestamp timestamp do arquivo
     * @return Optional contendo o arquivo se encontrado
     */
    @Query("SELECT f FROM FileOrigin f WHERE f.fileName = :fileName " +
           "AND f.acquirerId = :acquirerId " +
           "AND f.fileTimestamp = :fileTimestamp " +
           "AND f.active = true")
    Optional<FileOrigin> findByFileNameAndAcquirerIdAndFileTimestamp(
        @Param("fileName") String fileName,
        @Param("acquirerId") Long acquirerId,
        @Param("fileTimestamp") Instant fileTimestamp
    );
}
