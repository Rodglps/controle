package com.controle.arquivos.common.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para layouts de arquivos.
 * Mapeia a tabela layout.
 */
@Entity
@Table(name = "layout")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Layout {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_layout")
    @SequenceGenerator(name = "seq_layout", sequenceName = "seq_layout", allocationSize = 1)
    @Column(name = "idt_layout")
    private Long id;

    @Column(name = "des_layout_name", nullable = false, length = 100)
    private String layoutName;

    @Column(name = "des_layout_type", nullable = false, length = 20)
    private String layoutType;

    @Column(name = "des_description", length = 500)
    private String description;

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
