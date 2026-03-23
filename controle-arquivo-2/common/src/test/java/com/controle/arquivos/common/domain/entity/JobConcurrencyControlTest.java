package com.controle.arquivos.common.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade JobConcurrencyControl.
 * Valida campos obrigatórios e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class JobConcurrencyControlTest {

    @Test
    void deveCriarJobConcurrencyControlComCamposObrigatorios() {
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("RUNNING")
                .build();

        assertThat(job.getJobName()).isEqualTo("OrquestradorJob");
        assertThat(job.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void devePermitirLastExecutionOpcional() {
        Instant now = Instant.now();
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("COMPLETED")
                .lastExecution(now)
                .build();

        assertThat(job.getLastExecution()).isEqualTo(now);
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("RUNNING")
                .build();

        job.onCreate();

        assertThat(job.getActive()).isTrue();
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(job.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("RUNNING")
                .active(false)
                .build();

        job.onCreate();

        assertThat(job.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("RUNNING")
                .build();

        job.onCreate();
        var createdAt = job.getCreatedAt();
        var updatedAt = job.getUpdatedAt();

        Thread.sleep(10);
        job.onUpdate();

        assertThat(job.getCreatedAt()).isEqualTo(createdAt);
        assertThat(job.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarDiferentesStatus() {
        String[] statusList = {"RUNNING", "COMPLETED", "PENDING", "FAILED"};

        for (String status : statusList) {
            JobConcurrencyControl job = JobConcurrencyControl.builder()
                    .jobName("OrquestradorJob")
                    .status(status)
                    .build();

            assertThat(job.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void deveSuportarNomeJobLongo() {
        String nomeLongo = "A".repeat(100);
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName(nomeLongo)
                .status("RUNNING")
                .build();

        assertThat(job.getJobName()).hasSize(100);
    }

    @Test
    void deveRepresentarCicloVidaJob() {
        JobConcurrencyControl job = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("PENDING")
                .build();

        assertThat(job.getStatus()).isEqualTo("PENDING");

        job.setStatus("RUNNING");
        assertThat(job.getStatus()).isEqualTo("RUNNING");

        Instant executionTime = Instant.now();
        job.setLastExecution(executionTime);
        job.setStatus("COMPLETED");

        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getLastExecution()).isEqualTo(executionTime);
    }

    @Test
    void devePermitirMultiplosJobsComNomesDiferentes() {
        JobConcurrencyControl job1 = JobConcurrencyControl.builder()
                .jobName("OrquestradorJob")
                .status("RUNNING")
                .build();

        JobConcurrencyControl job2 = JobConcurrencyControl.builder()
                .jobName("ProcessadorJob")
                .status("RUNNING")
                .build();

        assertThat(job1.getJobName()).isNotEqualTo(job2.getJobName());
    }
}
