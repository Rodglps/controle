package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.CustomerIdentificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para CustomerIdentificationRule.
 * Fornece query para buscar regras ativas por adquirente.
 */
@Repository
public interface CustomerIdentificationRuleRepository extends JpaRepository<CustomerIdentificationRule, Long> {

    /**
     * Busca todas as regras ativas de identificação de cliente para um adquirente.
     * Usado durante o processo de identificação de cliente.
     *
     * @param acquirerId ID do adquirente
     * @return Lista de regras ativas ordenadas por customer_identification_id
     */
    @Query("SELECT r FROM CustomerIdentificationRule r " +
           "WHERE r.acquirerId = :acquirerId " +
           "AND r.active = true " +
           "ORDER BY r.customerIdentificationId")
    List<CustomerIdentificationRule> findActiveByAcquirerId(
        @Param("acquirerId") Long acquirerId
    );
}
