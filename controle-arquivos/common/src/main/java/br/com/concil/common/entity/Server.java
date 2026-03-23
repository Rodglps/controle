package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "server")
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "server_seq")
    @SequenceGenerator(name = "server_seq", sequenceName = "server_seq", allocationSize = 1)
    @Column(name = "idt_server")
    private Long id;

    @Column(name = "cod_server", nullable = false, unique = true)
    private String codServer;

    @Column(name = "cod_vault", nullable = false)
    private String codVault;

    @Column(name = "des_vault_secret", nullable = false)
    private String desVaultSecret;

    @Column(name = "des_server_type", nullable = false)
    private String desServerType;

    @Column(name = "des_server_origin", nullable = false)
    private String desServerOrigin;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
