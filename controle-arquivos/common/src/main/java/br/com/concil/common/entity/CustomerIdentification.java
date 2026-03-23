package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "customer_identification")
public class CustomerIdentification {

    @Id
    @Column(name = "idt_customer_identification")
    private Long id;

    @Column(name = "idt_client", nullable = false)
    private Long idtClient;

    @Column(name = "idt_acquirer", nullable = false)
    private Long idtAcquirer;

    @Column(name = "idt_merchant")
    private Long idtMerchant;

    @Column(name = "dat_start")
    private LocalDate datStart;

    @Column(name = "dat_end")
    private LocalDate datEnd;

    @Column(name = "idt_plan")
    private Long idtPlan;

    @Column(name = "flg_is_prioritary")
    private Integer flgIsPrioritary;

    @Column(name = "num_processing_weight")
    private Integer numProcessingWeight;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent")
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;

    @OneToMany(mappedBy = "customerIdentification", fetch = FetchType.LAZY)
    private List<CustomerIdentificationRule> rules;
}
