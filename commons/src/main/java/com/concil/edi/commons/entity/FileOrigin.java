package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.Date;

/**
 * JPA Entity representing a file being tracked through the EDI processing pipeline.
 * Maps to the 'file_origin' table in Oracle database.
 */
@Entity
@Table(name = "file_origin")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileOrigin {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_origin_seq_gen")
    @SequenceGenerator(name = "file_origin_seq_gen", sequenceName = "file_origin_seq", allocationSize = 1)
    @Column(name = "idt_file_origin")
    private Long idtFileOrigin;

    @Column(name = "idt_acquirer")
    private Long idtAcquirer;

    @Column(name = "idt_layout")
    private Long idtLayout;

    @Column(name = "des_file_name", nullable = false, length = 255)
    private String desFileName;

    @Column(name = "num_file_size")
    private Long numFileSize;

    @Column(name = "des_file_mime_type", length = 100)
    private String desFileMimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_file_type", length = 50)
    private FileType desFileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_step", nullable = false, length = 50)
    private Step desStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_status", length = 50)
    private Status desStatus;

    @Column(name = "des_message_error", length = 4000)
    private String desMessageError;

    @Column(name = "des_message_alert", length = 4000)
    private String desMessageAlert;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_transaction_type", nullable = false, length = 100)
    private TransactionType desTransactionType;

    @Column(name = "dat_timestamp_file", nullable = false)
    private Timestamp datTimestampFile;

    @Column(name = "idt_sever_paths_in_out", nullable = false)
    private Long idtSeverPathsInOut;

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

    @Column(name = "num_retry", nullable = false)
    private Integer numRetry;

    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry;
}
