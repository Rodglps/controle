package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.DistributionType;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing a layout configuration for EDI files.
 * Maps to the 'layout' table in Oracle database.
 * 
 * A layout defines the structure and format of an EDI file from a specific acquirer,
 * including file type, encoding, transaction type, and distribution pattern.
 */
@Entity
@Table(name = "layout")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Layout {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "layout_seq_gen")
    @SequenceGenerator(name = "layout_seq_gen", sequenceName = "layout_seq", allocationSize = 1)
    @Column(name = "idt_layout")
    private Long idtLayout;

    @Column(name = "cod_layout", nullable = false, length = 100)
    private String codLayout;

    @Column(name = "idt_acquirer", nullable = false)
    private Long idtAcquirer;

    @Column(name = "des_version", length = 30)
    private String desVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_file_type", nullable = false, length = 10)
    private FileType desFileType;

    @Column(name = "des_column_separator", length = 2)
    private String desColumnSeparator;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_transaction_type", nullable = false, length = 20)
    private TransactionType desTransactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_distribution_type", nullable = false, length = 20)
    private DistributionType desDistributionType;

    @Column(name = "des_encoding", length = 10)
    private String desEncoding;

    @Temporal(TemporalType.DATE)
    @Column(name = "dat_creation", nullable = false)
    private Date datCreation;

    @Temporal(TemporalType.DATE)
    @Column(name = "dat_update")
    private Date datUpdate;

    @Column(name = "nam_change_agent", nullable = false, length = 50)
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
