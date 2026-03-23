package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "customer_identification_rule")
public class CustomerIdentificationRule {

    @Id
    @Column(name = "idt_rule")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idt_customer_identification", nullable = false)
    private CustomerIdentification customerIdentification;

    @Column(name = "des_rule", nullable = false)
    private String desRule;

    @Column(name = "des_criterion_type_enum", nullable = false)
    private String desCriterionType; // COMECA-COM | TERMINA-COM | CONTEM | IGUAL

    @Column(name = "num_starting_position")
    private Integer numStartingPosition;

    @Column(name = "num_ending_position")
    private Integer numEndingPosition;

    @Column(name = "des_value", nullable = false)
    private String desValue;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent", nullable = false)
    private String namChangeAgent;

    @Column(name = "flg_active")
    private Integer flgActive;
}
