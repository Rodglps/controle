package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
@Entity
@Table(name = "file_origin")
public class FileOrigin {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_origin_seq")
    @SequenceGenerator(name = "file_origin_seq", sequenceName = "file_origin_seq", allocationSize = 1)
    @Column(name = "idt_file_origin")
    private Long id;

    @Column(name = "idt_acquirer")
    private Long idtAcquirer;

    @Column(name = "idt_layout")
    private Long idtLayout;

    @Column(name = "des_file_name", nullable = false)
    private String desFileName;

    @Column(name = "num_file_size")
    private Long numFileSize;

    @Column(name = "des_file_mime_type")
    private String desFileMimeType;

    @Column(name = "des_file_type")
    private String desFileType;

    @Column(name = "des_transaction_type", nullable = false)
    private String desTransactionType;

    @Column(name = "dat_timestamp_file", nullable = false)
    private LocalDateTime datTimestampFile;

    @Column(name = "idt_sever_paths_in_out", nullable = false)
    private Long idtSeverPathsInOut;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;
}
