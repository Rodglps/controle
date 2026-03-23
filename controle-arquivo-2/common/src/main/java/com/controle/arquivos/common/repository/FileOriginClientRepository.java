package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.FileOriginClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório JPA para FileOriginClient.
 * Gerencia associações entre arquivos e clientes identificados.
 */
@Repository
public interface FileOriginClientRepository extends JpaRepository<FileOriginClient, Long> {
    
    /**
     * Busca um FileOriginClient ativo por ID do FileOrigin.
     * 
     * @param fileOriginId ID do FileOrigin
     * @return Optional contendo o FileOriginClient se encontrado
     */
    Optional<FileOriginClient> findByFileOriginIdAndActiveTrue(Long fileOriginId);
}
