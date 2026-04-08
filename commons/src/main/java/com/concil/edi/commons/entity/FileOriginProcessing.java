package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing the processing state of a file per client and step.
 * Maps to the 'file_origin_processing' table in Oracle database.
 *
 * Tracks the processing status for each combination of file × client × step,
 * enabling granular visibility into the pipeline. The idt_client field can be
 * NULL when no customer was identified for the file.
 */
@Entity
@Table(name = "file_origin_processing")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileOriginProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_origin_processing_seq_gen")
    @SequenceGenerator(name = "file_origin_processing_seq_gen",
                       sequenceName = "file_origin_processing_seq", allocationSize = 1)
    @Column(name = "idt_file_origin_processing")
    private Long idtFileOriginProcessing;

    @Column(name = "idt_file_origin", nullable = false)
    private Long idtFileOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_step", nullable = false)
    private Step desStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_status", nullable = false)
    private Status desStatus;

    @Column(name = "idt_client")
    private Long idtClient;

    @Column(name = "des_message_error", length = 4000)
    private String desMessageError;

    @Column(name = "des_message_alert", length = 4000)
    private String desMessageAlert;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dat_step_start")
    private Date datStepStart;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dat_step_end")
    private Date datStepEnd;

    @Column(name = "jsn_additional_info", length = 4000)
    private String jsnAdditionalInfo;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dat_creation", nullable = false)
    private Date datCreation;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dat_update")
    private Date datUpdate;

    @Column(name = "nam_change_agent", nullable = false, length = 50)
    private String namChangeAgent;
}
