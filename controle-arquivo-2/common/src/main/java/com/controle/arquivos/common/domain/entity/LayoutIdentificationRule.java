package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.OrigemValor;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidade JPA para regras de identificação de layout.
 * Mapeia a tabela layout_identification_rule.
 */
@Entity
@Table(name = "layout_identification_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayoutIdentificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_layout_identification_rule")
    @SequenceGenerator(name = "seq_layout_identification_rule", sequenceName = "seq_layout_identification_rule", allocationSize = 1)
    @Column(name = "idt_layout_identification_rule")
    private Long id;

    @Column(name = "idt_layout", nullable = false)
    private Long layoutId;

    @Column(name = "idt_client", nullable = false)
    private Long clientId;

    @Column(name = "idt_acquirer", nullable = false)
    private Long acquirerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_value_origin", nullable = false, length = 20)
    private OrigemValor valueOrigin;

    @Column(name = "des_criterion_type_enum", nullable = false, length = 20)
    private String criterionType;

    @Column(name = "num_starting_position")
    private Integer startingPosition;

    @Column(name = "num_ending_position")
    private Integer endingPosition;

    @Column(name = "des_value", nullable = false, length = 500)
    private String value;

    @Column(name = "des_tag", length = 100)
    private String tag;

    @Column(name = "des_key", length = 100)
    private String key;

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
