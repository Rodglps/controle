package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para rastreabilidade de processamento de arquivos.
 * Mapeia a tabela file_origin_client_processing.
 */
@Entity
@Table(name = "file_origin_client_processing", indexes = {
    @Index(name = "idx_file_origin_client_processing", columnList = "idt_file_origin_client")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOriginClientProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_file_origin_client_processing")
    @SequenceGenerator(name = "seq_file_origin_client_processing", sequenceName = "seq_file_origin_client_processing", allocationSize = 1)
    @Column(name = "idt_file_origin_processing")
    private Long id;

    @Column(name = "idt_file_origin_client", nullable = false)
    private Long fileOriginClientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_step", nullable = false, length = 50)
    private EtapaProcessamento step;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_status", nullable = false, length = 50)
    private StatusProcessamento status;

    @Lob
    @Column(name = "des_message_error")
    private String messageError;

    @Lob
    @Column(name = "des_message_alert")
    private String messageAlert;

    @Column(name = "dat_step_start")
    private Instant stepStart;

    @Column(name = "dat_step_end")
    private Instant stepEnd;

    @Lob
    @Column(name = "jsn_additional_info")
    private String additionalInfo;

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
