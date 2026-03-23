package com.controle.arquivos.common.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para clientes do sistema.
 * Mapeia a tabela customer_identification.
 */
@Entity
@Table(name = "customer_identification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerIdentification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_customer_identification")
    @SequenceGenerator(name = "seq_customer_identification", sequenceName = "seq_customer_identification", allocationSize = 1)
    @Column(name = "idt_customer_identification")
    private Long id;

    @Column(name = "des_customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "num_processing_weight", nullable = false)
    private Integer processingWeight;

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
        if (processingWeight == null) {
            processingWeight = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
