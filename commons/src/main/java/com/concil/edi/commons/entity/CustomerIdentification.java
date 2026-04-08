package com.concil.edi.commons.entity;

import com.concil.edi.commons.service.extractor.IdentificationRule;
import com.concil.edi.commons.enums.CriteriaType;
import com.concil.edi.commons.enums.FunctionType;
import com.concil.edi.commons.enums.ValueOrigin;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing customer identification configuration.
 * Maps to the 'customer_identification' table in Oracle database.
 * 
 * A customer identification defines which customer owns files based on configured rules.
 * Multiple rules for the same identification are combined with AND logic.
 * 
 * Implements IdentificationRule interface to allow shared components (ValueExtractor, 
 * CriteriaComparator, etc.) to work with both layout identification and customer 
 * identification rules. Since this entity doesn't have rule-specific fields, 
 * all rule methods return null.
 */
@Entity
@Table(name = "customer_identification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerIdentification implements IdentificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_identification_seq_gen")
    @SequenceGenerator(name = "customer_identification_seq_gen", 
                       sequenceName = "customer_identification_seq", 
                       allocationSize = 1)
    @Column(name = "idt_identification")
    private Long idtIdentification;

    @Column(name = "idt_client", nullable = false)
    private Long idtClient;

    @Column(name = "idt_acquirer", nullable = false)
    private Long idtAcquirer;

    @Column(name = "idt_layout")
    private Long idtLayout;

    @Column(name = "idt_merchant")
    private Long idtMerchant;

    @Temporal(TemporalType.DATE)
    @Column(name = "dat_start")
    private Date datStart;

    @Temporal(TemporalType.DATE)
    @Column(name = "dat_end")
    private Date datEnd;

    @Column(name = "idt_plan")
    private Long idtPlan;

    @Column(name = "flg_is_priority")
    private Integer flgIsPriority;

    @Column(name = "num_process_weight")
    private Integer numProcessWeight;

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

    // IdentificationRule interface implementation
    // This entity doesn't have rule fields, so all methods return null
    
    @Override
    public ValueOrigin getDesValueOrigin() {
        return null;
    }

    @Override
    public CriteriaType getDesCriteriaType() {
        return null;
    }

    @Override
    public Integer getNumStartPosition() {
        return null;
    }

    @Override
    public Integer getNumEndPosition() {
        return null;
    }

    @Override
    public String getDesValue() {
        return null;
    }

    @Override
    public String getDesTag() {
        return null;
    }

    @Override
    public String getDesKey() {
        return null;
    }

    @Override
    public FunctionType getDesFunctionOrigin() {
        return null;
    }

    @Override
    public FunctionType getDesFunctionDest() {
        return null;
    }

    @Override
    public String getDesRule() {
        return null;
    }
}
