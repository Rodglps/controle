package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.TipoLink;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para mapeamento entre caminhos de origem e destino.
 * Mapeia a tabela sever_paths_in_out.
 */
@Entity
@Table(name = "sever_paths_in_out")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeverPathsInOut {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_sever_paths_in_out")
    @SequenceGenerator(name = "seq_sever_paths_in_out", sequenceName = "seq_sever_paths_in_out", allocationSize = 1)
    @Column(name = "idt_sever_paths_in_out")
    private Long id;

    @Column(name = "idt_sever_path_origin", nullable = false)
    private Long severPathOriginId;

    @Column(name = "idt_sever_destination", nullable = false)
    private Long severDestinationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_link_type", nullable = false, length = 20)
    private TipoLink linkType;

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
