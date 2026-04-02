package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.LinkType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing a mapping between origin and destination paths.
 * Maps to the 'sever_paths_in_out' table in Oracle database.
 */
@Entity
@Table(name = "sever_paths_in_out")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerPathInOut {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sever_paths_in_out_seq_gen")
    @SequenceGenerator(name = "sever_paths_in_out_seq_gen", sequenceName = "sever_paths_in_out_seq", allocationSize = 1)
    @Column(name = "idt_sever_paths_in_out")
    private Long idtSeverPathsInOut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_sever_path_origin", nullable = false)
    private ServerPath severPathOrigin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_sever_destination", nullable = false)
    private ServerPath severPathDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_link_type", nullable = false, length = 50)
    private LinkType desLinkType;

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
}
