package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.TipoCriterio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade CustomerIdentificationRule.
 * Valida campos obrigatórios, relacionamentos JPA e conversão de enums.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class CustomerIdentificationRuleTest {

    @Test
    void deveCriarCustomerIdentificationRuleComCamposObrigatorios() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .build();

        assertThat(rule.getCustomerIdentificationId()).isEqualTo(1L);
        assertThat(rule.getAcquirerId()).isEqualTo(2L);
        assertThat(rule.getCriterionType()).isEqualTo("COMECA-COM");
        assertThat(rule.getValue()).isEqualTo("CIELO");
    }

    @Test
    void devePermitirStartingPositionOpcional() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .startingPosition(0)
                .build();

        assertThat(rule.getStartingPosition()).isEqualTo(0);
    }

    @Test
    void devePermitirEndingPositionOpcional() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .endingPosition(10)
                .build();

        assertThat(rule.getEndingPosition()).isEqualTo(10);
    }

    @Test
    void deveConverterCriterionTypeParaEnum() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .build();

        assertThat(rule.getCriterionTypeEnum()).isEqualTo(TipoCriterio.COMECA_COM);
    }

    @Test
    void deveDefinirCriterionTypeAPartirDeEnum() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .value("CIELO")
                .build();

        rule.setCriterionTypeEnum(TipoCriterio.TERMINA_COM);

        assertThat(rule.getCriterionType()).isEqualTo("TERMINA-COM");
        assertThat(rule.getCriterionTypeEnum()).isEqualTo(TipoCriterio.TERMINA_COM);
    }

    @Test
    void deveSuportarTodosTiposCriterio() {
        for (TipoCriterio tipo : TipoCriterio.values()) {
            CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                    .customerIdentificationId(1L)
                    .acquirerId(2L)
                    .value("TEST")
                    .build();

            rule.setCriterionTypeEnum(tipo);

            assertThat(rule.getCriterionTypeEnum()).isEqualTo(tipo);
        }
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .build();

        rule.onCreate();

        assertThat(rule.getActive()).isTrue();
        assertThat(rule.getCreatedAt()).isNotNull();
        assertThat(rule.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .active(false)
                .build();

        rule.onCreate();

        assertThat(rule.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .build();

        rule.onCreate();
        var createdAt = rule.getCreatedAt();
        var updatedAt = rule.getUpdatedAt();

        Thread.sleep(10);
        rule.onUpdate();

        assertThat(rule.getCreatedAt()).isEqualTo(createdAt);
        assertThat(rule.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarValorLongo() {
        String valorLongo = "A".repeat(500);
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("CONTEM")
                .value(valorLongo)
                .build();

        assertThat(rule.getValue()).hasSize(500);
    }

    @Test
    void deveSuportarPosicoesNegativas() {
        CustomerIdentificationRule rule = CustomerIdentificationRule.builder()
                .customerIdentificationId(1L)
                .acquirerId(2L)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .startingPosition(-1)
                .endingPosition(-1)
                .build();

        assertThat(rule.getStartingPosition()).isEqualTo(-1);
        assertThat(rule.getEndingPosition()).isEqualTo(-1);
    }
}
