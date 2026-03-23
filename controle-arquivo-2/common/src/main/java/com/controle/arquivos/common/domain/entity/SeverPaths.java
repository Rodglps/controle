package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.TipoCaminho;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para caminhos de diretórios em servidores.
 * Mapeia a tabela sever_paths.
 */
@Entity
@Table(name = "sever_paths")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeverPaths {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_sever_paths")
    @SequenceGenerator(name = "seq_sever_paths", sequenceName = "seq_sever_paths", allocationSize = 1)
    @Column(name = "idt_sever_path")
    private Long id;

    @Column(name = "idt_server", nullable = false)
    private Long serverId;

    @Column(name = "idt_acquirer", nullable = false)
    private Long acquirerId;

    @Column(name = "des_path", nullable = false, length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_path_type", nullable = false, length = 20)
    private TipoCaminho pathType;

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
