package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.PathType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing a directory path within a server.
 * Maps to the 'sever_paths' table in Oracle database.
 */
@Entity
@Table(name = "sever_paths")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerPath {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sever_paths_seq_gen")
    @SequenceGenerator(name = "sever_paths_seq_gen", sequenceName = "sever_paths_seq", allocationSize = 1)
    @Column(name = "idt_sever_path")
    private Long idtSeverPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_server", nullable = false)
    private Server server;

    @Column(name = "idt_acquirer", nullable = false)
    private Long idtAcquirer;

    @Column(name = "des_path", nullable = false, length = 255)
    private String desPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_path_type", nullable = false, length = 50)
    private PathType desPathType;

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
