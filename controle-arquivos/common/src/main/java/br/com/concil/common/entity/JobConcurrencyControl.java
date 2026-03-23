package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
@Entity
@Table(name = "job_concurrency_control")
public class JobConcurrencyControl {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_concurrency_seq")
    @SequenceGenerator(name = "job_concurrency_seq", sequenceName = "job_concurrency_seq", allocationSize = 1)
    @Column(name = "idt_job_schedules")
    private Long id;

    @Column(name = "des_job_name", nullable = false)
    private String desJobName;

    @Column(name = "des_status", nullable = false)
    private String desStatus; // PENDING | RUNNING | COMPLETED

    @Column(name = "des_description")
    private String desDescription;

    @Column(name = "dat_last_execution")
    private LocalDateTime datLastExecution;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;

    @Column(name = "dat_creation", nullable = false)
    private LocalDateTime datCreation;

    @Column(name = "dat_update")
    private LocalDateTime datUpdate;

    @Column(name = "idt_job_execution")
    private String idtJobExecution;

    @Column(name = "idt_datacenter")
    private String idtDatacenter;
}
