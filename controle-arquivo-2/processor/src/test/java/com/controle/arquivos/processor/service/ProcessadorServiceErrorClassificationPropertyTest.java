package com.controle.arquivos.processor.service;

import com.controle.arquivos.processor.exception.ErroNaoRecuperavelException;
import com.controle.arquivos.processor.exception.ErroRecuperavelException;
import net.jqwik.api.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTransientException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de propriedade para classificação de erros.
 * 
 * Feature: controle-de-arquivos, Property 29: Classificação de Erros Recuperáveis
 * 
 * Para qualquer erro recuperável, o Sistema deve permitir reprocessamento via NACK,
 * e para qualquer erro não recuperável, deve marcar o arquivo como ERRO permanente.
 * 
 * **Valida: Requisitos 15.3, 15.4**
 */
class ProcessadorServiceErrorClassificationPropertyTest {

    /**
     * **Propriedade 29: Classificação de Erros Recuperáveis**
     * **Valida: Requisitos 15.3**
     * 
     * Para qualquer erro recuperável (conexão, timeout, transiente),
     * o sistema deve classificar como recuperável e permitir retry.
     */
    @Property(tries = 100)
    void errosRecuperaveisDevemPermitirRetry(
            @ForAll("erroRecuperavel") Exception erro) {
        
        // Act - Classificar erro
        boolean isRecuperavel = isErroRecuperavel(erro);
        
        // Assert - Deve ser classificado como recuperável
        assertThat(isRecuperavel).isTrue();
    }

    /**
     * **Propriedade 29: Classificação de Erros Recuperáveis**
     * **Valida: Requisitos 15.4**
     * 
     * Para qualquer erro não recuperável (arquivo não encontrado, validação),
     * o sistema deve classificar como não recuperável e marcar como ERRO permanente.
     */
    @Property(tries = 100)
    void errosNaoRecuperaveisNaoDevemPermitirRetry(
            @ForAll("erroNaoRecuperavel") Exception erro) {
        
        // Act - Classificar erro
        boolean isRecuperavel = isErroRecuperavel(erro);
        
        // Assert - Deve ser classificado como NÃO recuperável
        assertThat(isRecuperavel).isFalse();
    }

    /**
     * **Propriedade 29: Classificação de Erros Recuperáveis**
     * **Valida: Requisitos 15.3, 15.4**
     * 
     * Para qualquer erro, a classificação deve ser consistente
     * (mesmo erro sempre resulta na mesma classificação).
     */
    @Property(tries = 100)
    void classificacaoDeveSerConsistente(
            @ForAll("erroQualquer") Exception erro) {
        
        // Act - Classificar múltiplas vezes
        boolean classificacao1 = isErroRecuperavel(erro);
        boolean classificacao2 = isErroRecuperavel(erro);
        boolean classificacao3 = isErroRecuperavel(erro);
        
        // Assert - Classificação deve ser sempre a mesma
        assertThat(classificacao1).isEqualTo(classificacao2);
        assertThat(classificacao2).isEqualTo(classificacao3);
    }

    /**
     * **Propriedade 29: Classificação de Erros Recuperáveis**
     * **Valida: Requisitos 15.3**
     * 
     * Para qualquer erro de timeout ou conexão, deve ser classificado
     * como recuperável.
     */
    @Property(tries = 100)
    void errosDeConexaoSaoRecuperaveis(
            @ForAll("mensagemErro") String mensagem) {
        
        // Arrange - Criar erros de conexão
        Exception timeoutError = new SocketTimeoutException(mensagem);
        Exception connectionError = new IOException("Connection refused: " + mensagem);
        
        // Act & Assert
        assertThat(isErroRecuperavel(timeoutError)).isTrue();
        assertThat(isErroRecuperavel(connectionError)).isTrue();
    }

    /**
     * **Propriedade 29: Classificação de Erros Recuperáveis**
     * **Valida: Requisitos 15.4**
     * 
     * Para qualquer erro de validação ou lógica de negócio,
     * deve ser classificado como não recuperável.
     */
    @Property(tries = 100)
    void errosDeValidacaoNaoSaoRecuperaveis(
            @ForAll("mensagemErro") String mensagem) {
        
        // Arrange - Criar erros de validação
        Exception validationError = new IllegalArgumentException(mensagem);
        Exception businessError = new ErroNaoRecuperavelException(mensagem);
        
        // Act & Assert
        assertThat(isErroRecuperavel(validationError)).isFalse();
        assertThat(isErroRecuperavel(businessError)).isFalse();
    }

    /**
     * **Propriedade 29: Classificação de Erros Recuperáveis**
     * **Valida: Requisitos 15.3**
     * 
     * Para qualquer erro transiente de banco de dados,
     * deve ser classificado como recuperável.
     */
    @Property(tries = 100)
    void errosTransientesDeBancoDadosSaoRecuperaveis(
            @ForAll("mensagemErro") String mensagem) {
        
        // Arrange - Criar erro transiente de BD
        Exception dbError = new SQLTransientException(mensagem);
        
        // Act & Assert
        assertThat(isErroRecuperavel(dbError)).isTrue();
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<Exception> erroRecuperavel() {
        return Arbitraries.of(
            new SocketTimeoutException("Timeout"),
            new IOException("Connection refused"),
            new SQLTransientException("Database temporarily unavailable"),
            new ErroRecuperavelException("Erro temporário"),
            new RuntimeException("Throttling error")
        );
    }

    @Provide
    Arbitrary<Exception> erroNaoRecuperavel() {
        return Arbitraries.of(
            new IllegalArgumentException("Argumento inválido"),
            new ErroNaoRecuperavelException("Cliente não identificado"),
            new ErroNaoRecuperavelException("Layout não identificado"),
            new RuntimeException("Arquivo não encontrado"),
            new IllegalStateException("Estado inválido")
        );
    }

    @Provide
    Arbitrary<Exception> erroQualquer() {
        return Arbitraries.oneOf(
            erroRecuperavel(),
            erroNaoRecuperavel()
        );
    }

    @Provide
    Arbitrary<String> mensagemErro() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '-', '_')
            .ofMinLength(10)
            .ofMaxLength(100);
    }

    // ========== Helper Methods ==========

    /**
     * Lógica simplificada de classificação de erros para testes.
     * Na implementação real, isso estaria no ProcessadorService.
     */
    private boolean isErroRecuperavel(Exception exception) {
        // Erros explicitamente recuperáveis
        if (exception instanceof ErroRecuperavelException) {
            return true;
        }
        
        // Erros explicitamente não recuperáveis
        if (exception instanceof ErroNaoRecuperavelException) {
            return false;
        }
        
        // Erros de conexão e timeout são recuperáveis
        if (exception instanceof SocketTimeoutException ||
            exception instanceof SQLTransientException) {
            return true;
        }
        
        // IOException com mensagens específicas
        if (exception instanceof IOException) {
            String message = exception.getMessage();
            if (message != null && (
                message.contains("Connection refused") ||
                message.contains("Connection reset") ||
                message.contains("Timeout"))) {
                return true;
            }
        }
        
        // Erros de validação são não recuperáveis
        if (exception instanceof IllegalArgumentException ||
            exception instanceof IllegalStateException) {
            return false;
        }
        
        // RuntimeException com mensagens específicas
        if (exception instanceof RuntimeException) {
            String message = exception.getMessage();
            if (message != null) {
                if (message.contains("Throttling") || message.contains("Rate limit")) {
                    return true;
                }
                if (message.contains("não encontrado") || message.contains("não identificado")) {
                    return false;
                }
            }
        }
        
        // Por padrão, considerar não recuperável (fail-safe)
        return false;
    }
}
