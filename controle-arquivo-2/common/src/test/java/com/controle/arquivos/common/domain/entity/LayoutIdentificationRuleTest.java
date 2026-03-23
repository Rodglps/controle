package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.OrigemValor;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade LayoutIdentificationRule.
 * Valida campos obrigatórios, relacionamentos JPA e conversão de enums.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class LayoutIdentificationRuleTest {

    @Test
    void deveCriarLayoutIdentificationRuleComCamposObrigatorios() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.FILENAME)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .build();

        assertThat(rule.getLayoutId()).isEqualTo(1L);
        assertThat(rule.getClientId()).isEqualTo(2L);
        assertThat(rule.getAcquirerId()).isEqualTo(3L);
        assertThat(rule.getValueOrigin()).isEqualTo(OrigemValor.FILENAME);
        assertThat(rule.getCriterionType()).isEqualTo("COMECA-COM");
        assertThat(rule.getValue()).isEqualTo("CIELO");
    }

    @Test
    void devePermitirStartingPositionOpcional() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.HEADER)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .startingPosition(0)
                .build();

        assertThat(rule.getStartingPosition()).isEqualTo(0);
    }

    @Test
    void devePermitirEndingPositionOpcional() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.HEADER)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .endingPosition(100)
                .build();

        assertThat(rule.getEndingPosition()).isEqualTo(100);
    }

    @Test
    void devePermitirTagOpcional() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.TAG)
                .criterionType("IGUAL")
                .value("CIELO")
                .tag("acquirer")
                .build();

        assertThat(rule.getTag()).isEqualTo("acquirer");
    }

    @Test
    void devePermitirKeyOpcional() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.KEY)
                .criterionType("IGUAL")
                .value("CIELO")
                .key("acquirer_code")
                .build();

        assertThat(rule.getKey()).isEqualTo("acquirer_code");
    }

    @Test
    void deveConverterCriterionTypeParaEnum() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.FILENAME)
                .criterionType("TERMINA-COM")
                .value(".csv")
                .build();

        assertThat(rule.getCriterionTypeEnum()).isEqualTo(TipoCriterio.TERMINA_COM);
    }

    @Test
    void deveDefinirCriterionTypeAPartirDeEnum() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.FILENAME)
                .value("CIELO")
                .build();

        rule.setCriterionTypeEnum(TipoCriterio.CONTEM);

        assertThat(rule.getCriterionType()).isEqualTo("CONTEM");
        assertThat(rule.getCriterionTypeEnum()).isEqualTo(TipoCriterio.CONTEM);
    }

    @Test
    void deveSuportarTodasOrigensValor() {
        for (OrigemValor origem : OrigemValor.values()) {
            LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                    .layoutId(1L)
                    .clientId(2L)
                    .acquirerId(3L)
                    .valueOrigin(origem)
                    .criterionType("IGUAL")
                    .value("TEST")
                    .build();

            assertThat(rule.getValueOrigin()).isEqualTo(origem);
        }
    }

    @Test
    void deveSuportarTodosTiposCriterio() {
        for (TipoCriterio tipo : TipoCriterio.values()) {
            LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                    .layoutId(1L)
                    .clientId(2L)
                    .acquirerId(3L)
                    .valueOrigin(OrigemValor.FILENAME)
                    .value("TEST")
                    .build();

            rule.setCriterionTypeEnum(tipo);

            assertThat(rule.getCriterionTypeEnum()).isEqualTo(tipo);
        }
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.FILENAME)
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
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.FILENAME)
                .criterionType("COMECA-COM")
                .value("CIELO")
                .active(false)
                .build();

        rule.onCreate();

        assertThat(rule.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.FILENAME)
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
        LayoutIdentificationRule rule = LayoutIdentificationRule.builder()
                .layoutId(1L)
                .clientId(2L)
                .acquirerId(3L)
                .valueOrigin(OrigemValor.HEADER)
                .criterionType("CONTEM")
                .value(valorLongo)
                .build();

        assertThat(rule.getValue()).hasSize(500);
    }
}
