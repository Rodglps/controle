package br.com.concil.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
@Entity
@Table(name = "layout")
public class Layout {

    @Id
    @Column(name = "idt_layout")
    private Long id;

    @Column(name = "cod_layout", nullable = false)
    private String codLayout;

    @Column(name = "idt_acquirer", nullable = false)
    private Long idtAcquirer;

    @Column(name = "des_version")
    private String desVersion;

    @Column(name = "des_file_type", nullable = false)
    private String desFileType;

    @Column(name = "des_transaction_type", nullable = false)
    private String desTransactionType;

    @Column(name = "des_distribution_type", nullable = false)
    private String desDistributionType;

    @Column(name = "dat_creation", nullable = false)
    private LocalDate datCreation;

    @Column(name = "dat_update")
    private LocalDate datUpdate;

    @Column(name = "nam_change_agent", nullable = false)
    private String namChangeAgent;

    @Column(name = "flg_active", nullable = false)
    private Integer flgActive;

    @OneToMany(mappedBy = "layout", fetch = FetchType.LAZY)
    private List<LayoutIdentificationRule> identificationRules;
}
