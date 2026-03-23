package com.controle.arquivos.common.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para associação entre arquivo e cliente identificado.
 * Mapeia a tabela file_origin_client.
 */
@Entity
@Table(name = "file_origin_client")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOriginClient {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file_origin_client")
    @SequenceGenerator(name = "seq_file_origin_client", sequenceName = "seq_file_origin_client", allocationSize = 1)
    @Column(name = "idt_file_origin_client")
    private Long id;

    @Column(name = "idt_file_origin", nullable = false)
    private Long fileOriginId;

    @Column(name = "idt_client", nullable = false)
    private Long clientId;

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
