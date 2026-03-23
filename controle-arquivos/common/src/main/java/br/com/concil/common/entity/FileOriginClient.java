package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "file_origin_client")
public class FileOriginClient {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_origin_client_seq")
    @SequenceGenerator(name = "file_origin_client_seq", sequenceName = "file_origin_client_seq", allocationSize = 1)
    @Column(name = "idt_file_origin_client")
    private Long id;

    @Column(name = "idt_file_origin", nullable = false)
    private Long idtFileOrigin;

    @Column(name = "idt_client")
    private Long idtClient;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
