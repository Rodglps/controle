package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.CustomerIdentification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para CustomerIdentification.
 * Fornece queries para buscar clientes ativos.
 */
@Repository
public interface CustomerIdentificationRepository extends JpaRepository<CustomerIdentification, Long> {

    /**
     * Busca um cliente ativo por ID.
     *
     * @param id ID do cliente
     * @return Optional contendo o cliente se encontrado e ativo
     */
    @Query("SELECT c FROM CustomerIdentification c WHERE c.id = :id AND c.active = true")
    Optional<CustomerIdentification> findActiveById(@Param("id") Long id);

    /**
     * Busca múltiplos clientes ativos por IDs.
     *
     * @param ids Lista de IDs dos clientes
     * @return Lista de clientes ativos
     */
    @Query("SELECT c FROM CustomerIdentification c WHERE c.id IN :ids AND c.active = true")
    List<CustomerIdentification> findActiveByIds(@Param("ids") List<Long> ids);
}
