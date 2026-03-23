package com.controle.arquivos.common.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para controle de concorrência de execução de jobs.
 * Mapeia a tabela job_concurrency_control.
 */
@Entity
@Table(name = "job_concurrency_control")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobConcurrencyControl {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_job_concurrency_control")
    @SequenceGenerator(name = "seq_job_concurrency_control", sequenceName = "seq_job_concurrency_control", allocationSize = 1)
    @Column(name = "idt_job_concurrency_control")
    private Long id;

    @Column(name = "des_job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "des_status", nullable = false, length = 20)
    private String status;

    @Column(name = "dat_last_execution")
    private Instant lastExecution;

    @Column(name = "dat_created", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "dat_updated", nullable = false)
    private Instant updatedAt;

    @Column(name = "flg_active", nullable = false)
    private Boolean active;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
