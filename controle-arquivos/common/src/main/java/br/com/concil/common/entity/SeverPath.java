package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "sever_paths")
public class SeverPath {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sever_paths_seq")
    @SequenceGenerator(name = "sever_paths_seq", sequenceName = "sever_paths_seq", allocationSize = 1)
    @Column(name = "idt_sever_path")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_server", nullable = false)
    private Server server;

    @Column(name = "idt_acquirer", nullable = false)
    private Long idtAcquirer;

    @Column(name = "des_path", nullable = false)
    private String desPath;

    @Column(name = "des_path_type", nullable = false)
    private String desPathType;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
