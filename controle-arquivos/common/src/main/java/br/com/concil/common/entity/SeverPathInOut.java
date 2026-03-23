package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "sever_paths_in_out")
public class SeverPathInOut {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sever_paths_in_out_seq")
    @SequenceGenerator(name = "sever_paths_in_out_seq", sequenceName = "sever_paths_in_out_seq", allocationSize = 1)
    @Column(name = "idt_sever_paths_in_out")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_sever_path_origin", nullable = false)
    private SeverPath origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_sever_destination", nullable = false)
    private SeverPath destination;

    @Column(name = "des_link_type", nullable = false)
    private String desLinkType;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
