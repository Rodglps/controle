package com.controle.arquivos.common.repository;

import com.controle.arquivos.common.domain.entity.JobConcurrencyControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório JPA para JobConcurrencyControl.
 * Gerencia controle de concorrência de execução de jobs.
 */
@Repository
public interface JobConcurrencyControlRepository extends JpaRepository<JobConcurrencyControl, Long> {
    
    /**
     * Busca um registro ativo de controle de concorrência por nome do job e status.
     * 
     * @param jobName Nome do job
     * @param status Status do job
     * @param active Flag de ativo
     * @return Optional contendo o registro se encontrado
     */
    Optional<JobConcurrencyControl> findByJobNameAndStatusAndActive(String jobName, String status, Boolean active);
    
    /**
     * Busca um registro ativo de controle de concorrência por nome do job.
     * 
     * @param jobName Nome do job
     * @param active Flag de ativo
     * @return Optional contendo o registro se encontrado
     */
    Optional<JobConcurrencyControl> findByJobNameAndActive(String jobName, Boolean active);
}
