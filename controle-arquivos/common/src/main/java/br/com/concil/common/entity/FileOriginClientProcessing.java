package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "file_origin_client_processing")
public class FileOriginClientProcessing {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_origin_proc_seq")
    @SequenceGenerator(name = "file_origin_proc_seq", sequenceName = "file_origin_proc_seq", allocationSize = 1)
    @Column(name = "idt_file_origin_processing")
    private Long id;

    @Column(name = "idt_file_origin_client", nullable = false)
    private Long idtFileOriginClient;

    @Column(name = "des_step", nullable = false)
    private String desStep; // COLETA | RAW | STAGING | ORDINATION | PROCESSING | PROCESSED | DELETE

    @Column(name = "des_status")
    private String desStatus; // EM_ESPERA | PROCESSAMENTO | CONCLUIDO | ERRO

    @Column(name = "des_message_error", length = 4000)
    private String desMessageError;

    @Column(name = "des_message_alert", length = 4000)
    private String desMessageAlert;

    @Column(name = "dat_step_start")
    private LocalDate datStepStart;

    @Column(name = "dat_step_end")
    private LocalDate datStepEnd;

    @Column(name = "jsn_additional_info", length = 4000)
    private String jsnAdditionalInfo;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
