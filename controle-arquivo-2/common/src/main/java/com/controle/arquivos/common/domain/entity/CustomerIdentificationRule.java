package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.TipoCriterio;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para regras de identificação de cliente.
 * Mapeia a tabela customer_identification_rule.
 */
@Entity
@Table(name = "customer_identification_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerIdentificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_customer_identification_rule")
    @SequenceGenerator(name = "seq_customer_identification_rule", sequenceName = "seq_customer_identification_rule", allocationSize = 1)
    @Column(name = "idt_customer_identification_rule")
    private Long id;

    @Column(name = "idt_customer_identification", nullable = false)
    private Long customerIdentificationId;

    @Column(name = "idt_acquirer", nullable = false)
    private Long acquirerId;

    @Column(name = "des_criterion_type_enum", nullable = false, length = 20)
    private String criterionType;

    @Column(name = "num_starting_position")
    private Integer startingPosition;

    @Column(name = "num_ending_position")
    private Integer endingPosition;

    @Column(name = "des_value", nullable = false, length = 500)
    private String value;

    @Column(name = "dat_created", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "dat_updated", nullable = false)
    private Instant updatedAt;

    @Column(name = "flg_active", nullable = false)
    private Boolean active;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public TipoCriterio getCriterionTypeEnum() {
        return TipoCriterio.fromValor(criterionType);
    }

    public void setCriterionTypeEnum(TipoCriterio tipoCriterio) {
        this.criterionType = tipoCriterio.getValor();
    }
}
