package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.LayoutIdentificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para LayoutIdentificationRule.
 * Fornece query para buscar regras ativas por cliente e adquirente.
 */
@Repository
public interface LayoutIdentificationRuleRepository extends JpaRepository<LayoutIdentificationRule, Long> {

    /**
     * Busca todas as regras ativas de identificação de layout para um cliente e adquirente.
     * Usado durante o processo de identificação de layout.
     *
     * @param clientId ID do cliente
     * @param acquirerId ID do adquirente
     * @return Lista de regras ativas ordenadas por layout_id
     */
    @Query("SELECT r FROM LayoutIdentificationRule r " +
           "WHERE r.clientId = :clientId " +
           "AND r.acquirerId = :acquirerId " +
           "AND r.active = true " +
           "ORDER BY r.layoutId")
    List<LayoutIdentificationRule> findActiveByClientIdAndAcquirerId(
        @Param("clientId") Long clientId,
        @Param("acquirerId") Long acquirerId
    );
}
