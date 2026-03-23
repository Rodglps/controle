package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.JobConcurrencyControl;
import com.controle.arquivos.common.repository.JobConcurrencyControlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Serviço responsável por controlar a concorrência de execução de jobs.
 * Gerencia o controle de concorrência através da tabela job_concurrency_control.
 * 
 * **Valida: Requisitos 5.1, 5.2, 5.3, 5.4, 5.5**
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobConcurrencyService {

    private final JobConcurrencyControlRepository repository;

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_PENDING = "PENDING";

    /**
     * Inicia uma execução de job criando registro com status RUNNING.
     * 
     * @param jobName Nome do job a ser executado
     * @return ID do registro de controle criado
     * @throws IllegalArgumentException se jobName for nulo ou vazio
     */
    @Transactional
    public Long iniciarExecucao(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("jobName não pode ser nulo ou vazio");
        }

        JobConcurrencyControl control = JobConcurrencyControl.builder()
            .jobName(jobName)
            .status(STATUS_RUNNING)
            .active(true)
            .build();

        JobConcurrencyControl saved = repository.save(control);
        
        log.info("Execução iniciada para job={}, control_id={}, status={}", 
            jobName, saved.getId(), STATUS_RUNNING);

        return saved.getId();
    }

    /**
     * Verifica se existe uma execução ativa (RUNNING) para o job especificado.
     * 
     * @param jobName Nome do job a ser verificado
     * @return true se existe execução RUNNING, false caso contrário
     * @throws IllegalArgumentException se jobName for nulo ou vazio
     */
    @Transactional(readOnly = true)
    public boolean verificarExecucaoAtiva(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("jobName não pode ser nulo ou vazio");
        }

        boolean hasActiveExecution = repository
            .findByJobNameAndStatusAndActive(jobName, STATUS_RUNNING, true)
            .isPresent();

        log.debug("Verificação de execução ativa para job={}: {}", jobName, hasActiveExecution);

        return hasActiveExecution;
    }

    /**
     * Finaliza uma execução de job atualizando o status para COMPLETED ou PENDING.
     * 
     * @param jobName Nome do job a ser finalizado
     * @param sucesso true para COMPLETED, false para PENDING
     * @throws IllegalArgumentException se jobName for nulo ou vazio
     * @throws IllegalStateException se não houver execução RUNNING para o job
     */
    @Transactional
    public void finalizarExecucao(String jobName, boolean sucesso) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("jobName não pode ser nulo ou vazio");
        }

        JobConcurrencyControl control = repository
            .findByJobNameAndStatusAndActive(jobName, STATUS_RUNNING, true)
            .orElseThrow(() -> new IllegalStateException(
                "Nenhuma execução RUNNING encontrada para job: " + jobName));

        String novoStatus = sucesso ? STATUS_COMPLETED : STATUS_PENDING;
        control.setStatus(novoStatus);

        repository.save(control);
        
        log.info("Execução finalizada para job={}, control_id={}, status={}", 
            jobName, control.getId(), novoStatus);
    }

    /**
     * Registra a data de última execução para o job especificado.
     * 
     * @param jobName Nome do job
     * @throws IllegalArgumentException se jobName for nulo ou vazio
     * @throws IllegalStateException se não houver registro ativo para o job
     */
    @Transactional
    public void registrarDataExecucao(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("jobName não pode ser nulo ou vazio");
        }

        JobConcurrencyControl control = repository
            .findByJobNameAndActive(jobName, true)
            .orElseThrow(() -> new IllegalStateException(
                "Nenhum registro ativo encontrado para job: " + jobName));

        control.setLastExecution(Instant.now());

        repository.save(control);
        
        log.info("Data de execução registrada para job={}, control_id={}, dat_last_execution={}", 
            jobName, control.getId(), control.getLastExecution());
    }
}
