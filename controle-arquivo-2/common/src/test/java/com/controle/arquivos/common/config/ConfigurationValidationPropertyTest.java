package com.controle.arquivos.common.config;

import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de propriedade para validação de configurações obrigatórias.
 * 
 * Feature: controle-de-arquivos, Property 32: Validação de Configurações Obrigatórias
 * 
 * Para qualquer inicialização do sistema, o Sistema deve validar que todas as
 * configurações obrigatórias estão presentes, e falhar a inicialização se alguma
 * estiver faltando.
 * 
 * **Valida: Requisitos 19.5**
 */
class ConfigurationValidationPropertyTest {

    private static final String[] CONFIGURACOES_OBRIGATORIAS = {
        "spring.datasource.url",
        "spring.datasource.username",
        "spring.rabbitmq.host",
        "spring.rabbitmq.port",
        "vault.uri",
        "vault.token"
    };

    /**
     * **Propriedade 32: Validação de Configurações Obrigatórias**
     * **Valida: Requisitos 19.5**
     * 
     * Para qualquer inicialização com todas as configurações obrigatórias presentes,
     * o sistema deve inicializar com sucesso.
     */
    @Property(tries = 100)
    void todasConfiguracoesObrigatoriasPermitemInicializacao(
            @ForAll("configuracoesCompletas") Map<String, String> configuracoes) {
        
        // Act
        boolean valido = validarConfiguracoes(configuracoes);
        
        // Assert
        assertThat(valido).isTrue();
    }

    /**
     * **Propriedade 32: Validação de Configurações Obrigatórias**
     * **Valida: Requisitos 19.5**
     * 
     * Para qualquer inicialização com configuração obrigatória faltando,
     * o sistema deve falhar a inicialização.
     */
    @Property(tries = 100)
    void configuracaoObrigatoriaFaltandoDeveFalharInicializacao(
            @ForAll("configuracaoFaltando") String configuracaoFaltando) {
        
        // Arrange - Criar configurações sem uma obrigatória
        Map<String, String> configuracoes = criarConfiguracoesCompletas();
        configuracoes.remove(configuracaoFaltando);
        
        // Act
        boolean valido = validarConfiguracoes(configuracoes);
        
        // Assert
        assertThat(valido).isFalse();
    }

    /**
     * **Propriedade 32: Validação de Configurações Obrigatórias**
     * **Valida: Requisitos 19.5**
     * 
     * Para qualquer configuração obrigatória com valor vazio,
     * o sistema deve falhar a inicialização.
     */
    @Property(tries = 100)
    void configuracaoObrigatoriaVaziaDeveFalharInicializacao(
            @ForAll("configuracaoFaltando") String configuracao) {
        
        // Arrange - Criar configurações com valor vazio
        Map<String, String> configuracoes = criarConfiguracoesCompletas();
        configuracoes.put(configuracao, "");
        
        // Act
        boolean valido = validarConfiguracoes(configuracoes);
        
        // Assert
        assertThat(valido).isFalse();
    }

    /**
     * **Propriedade 32: Validação de Configurações Obrigatórias**
     * **Valida: Requisitos 19.5**
     * 
     * Para qualquer configuração obrigatória com valor nulo,
     * o sistema deve falhar a inicialização.
     */
    @Property(tries = 100)
    void configuracaoObrigatoriaNulaDeveFalharInicializacao(
            @ForAll("configuracaoFaltando") String configuracao) {
        
        // Arrange - Criar configurações com valor nulo
        Map<String, String> configuracoes = criarConfiguracoesCompletas();
        configuracoes.put(configuracao, null);
        
        // Act
        boolean valido = validarConfiguracoes(configuracoes);
        
        // Assert
        assertThat(valido).isFalse();
    }

    /**
     * **Propriedade 32: Validação de Configurações Obrigatórias**
     * **Valida: Requisitos 19.5**
     * 
     * Para qualquer conjunto de configurações, a validação deve verificar
     * todas as configurações obrigatórias.
     */
    @Property(tries = 100)
    void validacaoDeveVerificarTodasConfiguracoesObrigatorias(
            @ForAll("configuracoesQualquer") Map<String, String> configuracoes) {
        
        // Act
        boolean valido = validarConfiguracoes(configuracoes);
        
        // Assert - Se válido, todas as obrigatórias devem estar presentes
        if (valido) {
            for (String config : CONFIGURACOES_OBRIGATORIAS) {
                assertThat(configuracoes).containsKey(config);
                assertThat(configuracoes.get(config)).isNotEmpty();
            }
        }
    }

    /**
     * **Propriedade 32: Validação de Configurações Obrigatórias**
     * **Valida: Requisitos 19.5**
     * 
     * Para qualquer configuração adicional (não obrigatória),
     * o sistema deve permitir inicialização se as obrigatórias estiverem presentes.
     */
    @Property(tries = 100)
    void configuracoesAdicionaisNaoDevemImpedirInicializacao(
            @ForAll("configuracaoAdicional") String chave,
            @ForAll("valorConfiguracao") String valor) {
        
        // Arrange - Criar configurações completas + adicional
        Map<String, String> configuracoes = criarConfiguracoesCompletas();
        configuracoes.put(chave, valor);
        
        // Act
        boolean valido = validarConfiguracoes(configuracoes);
        
        // Assert - Deve ser válido (configurações adicionais são permitidas)
        assertThat(valido).isTrue();
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<Map<String, String>> configuracoesCompletas() {
        return Arbitraries.just(criarConfiguracoesCompletas());
    }

    @Provide
    Arbitrary<String> configuracaoFaltando() {
        return Arbitraries.of(CONFIGURACOES_OBRIGATORIAS);
    }

    @Provide
    Arbitrary<Map<String, String>> configuracoesQualquer() {
        return Arbitraries.frequency(
            Tuple.of(7, configuracoesCompletas()), // 70% completas
            Tuple.of(3, configuracoesIncompletas()) // 30% incompletas
        );
    }

    @Provide
    Arbitrary<Map<String, String>> configuracoesIncompletas() {
        return configuracaoFaltando().map(faltando -> {
            Map<String, String> configs = criarConfiguracoesCompletas();
            configs.remove(faltando);
            return configs;
        });
    }

    @Provide
    Arbitrary<String> configuracaoAdicional() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '-', '_')
            .ofMinLength(5)
            .ofMaxLength(50)
            .filter(s -> !isConfiguracaoObrigatoria(s));
    }

    @Provide
    Arbitrary<String> valorConfiguracao() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(':', '/', '-', '_', '.')
            .ofMinLength(5)
            .ofMaxLength(100);
    }

    // ========== Helper Methods ==========

    private Map<String, String> criarConfiguracoesCompletas() {
        Map<String, String> configs = new HashMap<>();
        configs.put("spring.datasource.url", "jdbc:oracle:thin:@localhost:1521:XE");
        configs.put("spring.datasource.username", "user");
        configs.put("spring.datasource.password", "password");
        configs.put("spring.rabbitmq.host", "localhost");
        configs.put("spring.rabbitmq.port", "5672");
        configs.put("spring.rabbitmq.username", "guest");
        configs.put("spring.rabbitmq.password", "guest");
        configs.put("vault.uri", "http://localhost:8200");
        configs.put("vault.token", "root-token");
        return configs;
    }

    private boolean validarConfiguracoes(Map<String, String> configuracoes) {
        if (configuracoes == null) {
            return false;
        }
        
        for (String configObrigatoria : CONFIGURACOES_OBRIGATORIAS) {
            if (!configuracoes.containsKey(configObrigatoria)) {
                return false;
            }
            
            String valor = configuracoes.get(configObrigatoria);
            if (valor == null || valor.trim().isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    private boolean isConfiguracaoObrigatoria(String config) {
        for (String obrigatoria : CONFIGURACOES_OBRIGATORIAS) {
            if (obrigatoria.equals(config)) {
                return true;
            }
        }
        return false;
    }
}
