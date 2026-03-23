package com.controle.arquivos.common.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para arquivos coletados de servidores SFTP.
 * Mapeia a tabela file_origin.
 */
@Entity
@Table(name = "file_origin", indexes = {
    @Index(name = "idx_file_origin_unique", columnList = "des_file_name,idt_acquirer,dat_timestamp_file,flg_active", unique = true),
    @Index(name = "idx_file_origin_sever_paths", columnList = "idt_sever_paths_in_out")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOrigin {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file_origin")
    @SequenceGenerator(name = "seq_file_origin", sequenceName = "seq_file_origin", allocationSize = 1)
    @Column(name = "idt_file_origin")
    private Long id;

    @Column(name = "idt_acquirer", nullable = false)
    private Long acquirerId;

    @Column(name = "idt_layout")
    private Long layoutId;

    @Column(name = "des_file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "num_file_size", nullable = false)
    private Long fileSize;

    @Column(name = "des_file_type", length = 50)
    private String fileType;

    @Column(name = "des_transaction_type", length = 50)
    private String transactionType;

    @Column(name = "dat_timestamp_file", nullable = false)
    private Instant fileTimestamp;

    @Column(name = "idt_sever_paths_in_out", nullable = false)
    private Long severPathsInOutId;

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
