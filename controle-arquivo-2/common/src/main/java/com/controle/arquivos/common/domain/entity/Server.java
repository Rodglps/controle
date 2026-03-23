package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.OrigemServidor;
import com.controle.arquivos.common.domain.enums.TipoServidor;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para servidores de origem e destino de arquivos.
 * Mapeia a tabela server.
 */
@Entity
@Table(name = "server")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_server")
    @SequenceGenerator(name = "seq_server", sequenceName = "seq_server", allocationSize = 1)
    @Column(name = "idt_server")
    private Long id;

    @Column(name = "cod_server", nullable = false, length = 50)
    private String serverCode;

    @Column(name = "cod_vault", nullable = false, length = 100)
    private String vaultCode;

    @Column(name = "des_vault_secret", nullable = false, length = 200)
    private String vaultSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_server_type", nullable = false, length = 20)
    private TipoServidor serverType;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_server_origin", nullable = false, length = 20)
    private OrigemServidor serverOrigin;

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
