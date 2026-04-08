package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.CriteriaType;
import com.concil.edi.commons.enums.FunctionType;
import com.concil.edi.commons.enums.ValueOrigin;
import com.concil.edi.commons.service.extractor.IdentificationRule;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing a rule for customer identification.
 * Maps to the 'customer_identification_rule' table in Oracle database.
 * 
 * Rules define how to extract values from files (by filename, header position, XML tag, JSON key)
 * and compare them against expected values using various criteria (starts with, contains, equals, etc.).
 * Multiple rules for the same customer identification are combined with AND logic.
 * 
 * Implements IdentificationRule interface to allow shared components (ValueExtractor, 
 * CriteriaComparator, etc.) to work with both layout identification and customer 
 * identification rules without adapters.
 */
@Entity
@Table(name = "customer_identification_rule")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerIdentificationRule implements IdentificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_identification_rule_seq_gen")
    @SequenceGenerator(name = "customer_identification_rule_seq_gen", 
                       sequenceName = "customer_identification_rule_seq", 
                       allocationSize = 1)
    @Column(name = "idt_rule")
    private Long idtRule;

    @Column(name = "idt_identification", nullable = false)
    private Long idtIdentification;

    @Column(name = "des_rule", nullable = false, length = 255)
    private String desRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_value_origin", nullable = false, length = 10)
    private ValueOrigin desValueOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_criteria_type", nullable = false, length = 15)
    private CriteriaType desCriteriaType;

    @Column(name = "num_start_position")
    private Integer numStartPosition;

    @Column(name = "num_end_position")
    private Integer numEndPosition;

    @Column(name = "des_value", length = 255)
    private String desValue;

    @Column(name = "des_tag", length = 255)
    private String desTag;

    @Column(name = "des_key", length = 255)
    private String desKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_function_origin", length = 10)
    private FunctionType desFunctionOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "des_function_dest", length = 10)
    private FunctionType desFunctionDest;

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
}
