package br.com.concil.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RuleMatcherServiceTest {

    private RuleMatcherService service;

    @BeforeEach
    void setUp() {
        service = new RuleMatcherService();
    }

    @Test
    void comecaCom_deveRetornarTrue_quandoNomeComecaComValor() {
        assertThat(service.matches("COMECA-COM", "CIELO_EDI_20240101.txt", "CIELO", null, null)).isTrue();
    }

    @Test
    void comecaCom_deveRetornarFalse_quandoNomeNaoComecaComValor() {
        assertThat(service.matches("COMECA-COM", "REDE_EDI_20240101.txt", "CIELO", null, null)).isFalse();
    }

    @Test
    void terminaCom_deveRetornarTrue_quandoNomeTerminaComValor() {
        assertThat(service.matches("TERMINA-COM", "CIELO_EDI_20240101.txt", ".txt", null, null)).isTrue();
    }

    @Test
    void contem_deveRetornarTrue_quandoNomeContemValor() {
        assertThat(service.matches("CONTEM", "CIELO_EDI_20240101.txt", "EDI", null, null)).isTrue();
    }

    @Test
    void igual_deveRetornarTrue_quandoValorExatoIgual() {
        assertThat(service.matches("IGUAL", "CIELO", "CIELO", null, null)).isTrue();
    }

    @Test
    void comecaCom_comPosicao_deveExtrairSubstringCorretamente() {
        // nome: "CIELO_EDI_20240101.txt", posição 1-5 = "CIELO"
        assertThat(service.matches("COMECA-COM", "CIELO_EDI_20240101.txt", "CIELO", 1, 5)).isTrue();
    }

    @Test
    void deveRetornarFalse_quandoSubjectNulo() {
        assertThat(service.matches("IGUAL", null, "CIELO", null, null)).isFalse();
    }

    @Test
    void deveLancarExcecao_quandoCriterioDesconhecido() {
        assertThatThrownBy(() -> service.matches("INVALIDO", "CIELO", "CIELO", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
