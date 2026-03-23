package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para FileOriginClientProcessing.
 * Fornece query para buscar registros de processamento por idt_file_origin_client.
 */
@Repository
public interface FileOriginClientProcessingRepository extends JpaRepository<FileOriginClientProcessing, Long> {

    /**
     * Busca todos os registros de processamento para um arquivo-cliente específico.
     * Usado para rastreabilidade completa do processamento.
     *
     * @param fileOriginClientId ID do file_origin_client
     * @return Lista de registros de processamento ordenados por data de criação
     */
    @Query("SELECT p FROM FileOriginClientProcessing p " +
           "WHERE p.fileOriginClientId = :fileOriginClientId " +
           "AND p.active = true " +
           "ORDER BY p.createdAt ASC")
    List<FileOriginClientProcessing> findByFileOriginClientId(
        @Param("fileOriginClientId") Long fileOriginClientId
    );
}
