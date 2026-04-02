package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.ServerOrigin;
import com.concil.edi.commons.enums.ServerType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing a server configuration for file storage and transfer.
 * Maps to the 'server' table in Oracle database.
 */
@Entity
@Table(name = "server")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "server_seq_gen")
    @SequenceGenerator(name = "server_seq_gen", sequenceName = "server_seq", allocationSize = 1)
    @Column(name = "idt_server")
    private Long idtServer;

    @Column(name = "cod_server", nullable = false, length = 100)
    private String codServer;

    @Column(name = "cod_vault", nullable = false, length = 100)
    private String codVault;

    @Column(name = "des_vault_secret", nullable = false, length = 255)
    private String desVaultSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_server_type", nullable = false, length = 50)
    private ServerType desServerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_server_origin", nullable = false, length = 50)
    private ServerOrigin desServerOrigin;

    @Temporal(TemporalType.DATE)
    @Column(name = "dat_creation", nullable = false)
    private Date datCreation;

    @Temporal(TemporalType.DATE)
    @Column(name = "dat_update")
    private Date datUpdate;

    @Column(name = "nam_change_agent", length = 50)
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;

    @Column(name = "num_min_age_seconds")
    private Integer numMinAgeSeconds;

    @Column(name = "num_double_check_wait_seconds")
    private Integer numDoubleCheckWaitSeconds;
}
