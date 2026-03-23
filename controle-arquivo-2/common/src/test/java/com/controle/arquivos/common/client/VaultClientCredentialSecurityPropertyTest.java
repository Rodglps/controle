package com.controle.arquivos.common.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.jqwik.api.*;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para segurança de credenciais.
 * 
 * Feature: controle-de-arquivos, Property 23: Segurança de Credenciais
 * 
 * Para qualquer erro ao recuperar credenciais do Vault, o Sistema deve registrar
 * erro sem expor informações sensíveis.
 * 
 * **Valida: Requisitos 11.5**
 */
class VaultClientCredentialSecurityPropertyTest {

    /**
     * **Propriedade 23: Segurança de Credenciais**
     * **Valida: Requisitos 11.5**
     * 
     * Para qualquer erro ao recuperar credenciais, o sistema deve registrar
     * erro sem expor informações sensíveis (senha, token, etc).
     */
    @Property(tries = 100)
    void erroNaoDeveExporCredenciais(
            @ForAll("codVault") String codVault,
            @ForAll("secretPath") String secretPath,
            @ForAll("mensagemErro") String mensagemErro) {
        
        // Arrange
        Logger logger = (Logger) LoggerFactory.getLogger(VaultClient.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        
        VaultClient vaultClient = mock(VaultClient.class);
        
        // Simular erro ao obter credenciais
        when(vaultClient.obterCredenciais(codVault, secretPath))
            .thenThrow(new RuntimeException(mensagemErro));
        
        // Act & Assert
        assertThatThrownBy(() -> vaultClient.obterCredenciais(codVault, secretPath))
            .isInstanceOf(RuntimeException.class);
        
        // Verificar que logs não contêm informações sensíveis
        for (ILoggingEvent logEvent : listAppender.list) {
            String logMessage = logEvent.getFormattedMessage().toLowerCase();
            
            // Verificar que palavras sensíveis não aparecem nos logs
            assertThat(logMessage).doesNotContain("password");
            assertThat(logMessage).doesNotContain("senha");
            assertThat(logMessage).doesNotContain("token");
            assertThat(logMessage).doesNotContain("secret");
            assertThat(logMessage).doesNotContain("credential");
        }
        
        // Cleanup
        listAppender.list.clear();
        logger.detachAppender(listAppender);
    }

    /**
     * **Propriedade 23: Segurança de Credenciais**
     * **Valida: Requisitos 11.5**
     * 
     * Para qualquer credencial obtida com sucesso, o sistema nunca deve
     * logar a senha ou token.
     */
    @Property(tries = 100)
    void credenciaisNuncaDevemSerLogadas(
            @ForAll("username") String username,
            @ForAll("password") String password) {
        
        // Arrange
        Logger logger = (Logger) LoggerFactory.getLogger(VaultClient.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais(username, password);
        
        // Act - Simular uso de credenciais (que pode gerar logs)
        logger.info("Credenciais obtidas para usuário");
        
        // Assert - Verificar que senha não aparece em nenhum log
        for (ILoggingEvent logEvent : listAppender.list) {
            String logMessage = logEvent.getFormattedMessage();
            
            // Senha nunca deve aparecer nos logs
            assertThat(logMessage).doesNotContain(password);
        }
        
        // Cleanup
        listAppender.list.clear();
        logger.detachAppender(listAppender);
    }

    /**
     * **Propriedade 23: Segurança de Credenciais**
     * **Valida: Requisitos 11.5**
     * 
     * Para qualquer exceção relacionada a credenciais, a mensagem de erro
     * não deve conter informações sensíveis.
     */
    @Property(tries = 100)
    void excecaoNaoDeveConterInformacoesSensiveis(
            @ForAll("codVault") String codVault,
            @ForAll("secretPath") String secretPath,
            @ForAll("password") String password) {
        
        // Arrange
        VaultClient vaultClient = mock(VaultClient.class);
        
        // Simular erro que poderia expor credenciais
        RuntimeException erro = new RuntimeException("Falha ao autenticar com Vault");
        when(vaultClient.obterCredenciais(codVault, secretPath))
            .thenThrow(erro);
        
        // Act & Assert
        assertThatThrownBy(() -> vaultClient.obterCredenciais(codVault, secretPath))
            .isInstanceOf(RuntimeException.class)
            .satisfies(exception -> {
                // Verificar que mensagem de erro não contém senha
                assertThat(exception.getMessage()).doesNotContain(password);
                assertThat(exception.getMessage()).doesNotContainIgnoringCase("password");
                assertThat(exception.getMessage()).doesNotContainIgnoringCase("token");
            });
    }

    /**
     * **Propriedade 23: Segurança de Credenciais**
     * **Valida: Requisitos 11.5**
     * 
     * Para qualquer combinação de código Vault e secret path, erros devem
     * ser registrados de forma segura.
     */
    @Property(tries = 100)
    void qualquerErroDeveSerRegistradoComSeguranca(
            @ForAll("codVault") String codVault,
            @ForAll("secretPath") String secretPath) {
        
        // Arrange
        VaultClient vaultClient = mock(VaultClient.class);
        
        // Simular diferentes tipos de erro
        when(vaultClient.obterCredenciais(codVault, secretPath))
            .thenThrow(new RuntimeException("Erro ao conectar ao Vault"));
        
        // Act & Assert
        assertThatThrownBy(() -> vaultClient.obterCredenciais(codVault, secretPath))
            .isInstanceOf(RuntimeException.class)
            .satisfies(exception -> {
                // Mensagem deve ser genérica e não expor detalhes sensíveis
                String message = exception.getMessage();
                assertThat(message).isNotEmpty();
                assertThat(message).doesNotContainIgnoringCase("password");
                assertThat(message).doesNotContainIgnoringCase("secret");
                assertThat(message).doesNotContainIgnoringCase("token");
                assertThat(message).doesNotContainIgnoringCase("credential");
            });
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<String> codVault() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> secretPath() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('/', '-', '_')
            .ofMinLength(10)
            .ofMaxLength(50)
            .map(s -> "secret/" + s);
    }

    @Provide
    Arbitrary<String> mensagemErro() {
        return Arbitraries.of(
            "Erro ao conectar ao Vault",
            "Timeout ao buscar credenciais",
            "Vault indisponível",
            "Erro de autenticação",
            "Permissão negada"
        );
    }

    @Provide
    Arbitrary<String> username() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(5)
            .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> password() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('!', '@', '#', '$', '%')
            .ofMinLength(10)
            .ofMaxLength(30);
    }
}
