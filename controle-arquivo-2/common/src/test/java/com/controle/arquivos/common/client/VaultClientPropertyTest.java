package com.controle.arquivos.common.client;

import com.controle.arquivos.common.config.VaultProperties;
import net.jqwik.api.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para VaultClient.
 * 
 * Feature: controle-de-arquivos, Property 2: Obtenção de Credenciais do Vault
 * 
 * Para qualquer servidor que requer autenticação, o sistema deve obter credenciais
 * usando cod_vault e des_vault_secret da tabela server, e nunca deve registrar
 * essas credenciais em logs ou banco de dados.
 * 
 * **Valida: Requisitos 2.1, 11.1, 11.2, 11.4**
 */
class VaultClientPropertyTest {

    /**
     * Propriedade: Para qualquer combinação válida de codVault e secretPath,
     * o VaultClient deve retornar credenciais válidas quando o Vault responde com sucesso.
     */
    @Property(tries = 50)
    void obterCredenciais_deveRetornarCredenciaisParaQualquerCaminhoValido(
        @ForAll("codVault") String codVault,
        @ForAll("secretPath") String secretPath,
        @ForAll("username") String username,
        @ForAll("password") String password
    ) {
        // Arrange
        VaultProperties vaultProperties = createVaultProperties();
        VaultClient vaultClient = new VaultClient(vaultProperties);
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        injectRestTemplate(vaultClient, mockRestTemplate);

        String vaultResponse = String.format("""
            {
                "data": {
                    "data": {
                        "username": "%s",
                        "password": "%s"
                    }
                }
            }
            """, username, password);

        when(mockRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        assertNotNull(credenciais);
        assertEquals(username, credenciais.getUsername());
        assertEquals(password, credenciais.getPassword());
    }

    /**
     * Propriedade: Para qualquer sequência de chamadas com os mesmos parâmetros,
     * o cache deve ser usado após a primeira chamada (dentro do TTL).
     */
    @Property(tries = 50)
    void obterCredenciais_deveUsarCacheParaChamadasRepetidas(
        @ForAll("codVault") String codVault,
        @ForAll("secretPath") String secretPath,
        @ForAll @IntRange(min = 2, max = 10) int numeroChamadas
    ) {
        // Arrange
        VaultProperties vaultProperties = createVaultProperties();
        VaultClient vaultClient = new VaultClient(vaultProperties);
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        injectRestTemplate(vaultClient, mockRestTemplate);

        String vaultResponse = """
            {
                "data": {
                    "data": {
                        "username": "testuser",
                        "password": "testpass"
                    }
                }
            }
            """;

        when(mockRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        for (int i = 0; i < numeroChamadas; i++) {
            VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(codVault, secretPath);
            assertNotNull(credenciais);
        }

        // Assert
        // Deve chamar o Vault apenas uma vez, independente do número de chamadas
        verify(mockRestTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    /**
     * Propriedade: Para qualquer erro do Vault, o sistema deve lançar VaultException
     * sem expor credenciais na mensagem de erro.
     */
    @Property(tries = 50)
    void obterCredenciais_deveLancarExcecaoSemExporCredenciais(
        @ForAll("codVault") String codVault,
        @ForAll("secretPath") String secretPath
    ) {
        // Arrange
        VaultProperties vaultProperties = createVaultProperties();
        VaultClient vaultClient = new VaultClient(vaultProperties);
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        injectRestTemplate(vaultClient, mockRestTemplate);

        when(mockRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert
        VaultClient.VaultException exception = assertThrows(
            VaultClient.VaultException.class,
            () -> vaultClient.obterCredenciais(codVault, secretPath)
        );

        // A mensagem de erro não deve conter o token ou outras credenciais
        String message = exception.getMessage();
        assertNotNull(message);
        assertFalse(message.contains("test-token"));
    }

    /**
     * Propriedade: toString de Credenciais nunca deve expor username ou password.
     */
    @Property(tries = 100)
    void credenciais_toStringNuncaDeveExporCredenciais(
        @ForAll("username") String username,
        @ForAll("password") String password
    ) {
        // Arrange
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais(username, password);

        // Act
        String toString = credenciais.toString();

        // Assert
        assertFalse(toString.contains(username), 
            "toString não deve conter username");
        assertFalse(toString.contains(password), 
            "toString não deve conter password");
        assertTrue(toString.contains("***"), 
            "toString deve mascarar credenciais com ***");
    }

    /**
     * Propriedade: Após limpar o cache, o Vault deve ser consultado novamente.
     */
    @Property(tries = 50)
    void clearCache_deveForcarNovaConsultaAoVault(
        @ForAll("codVault") String codVault,
        @ForAll("secretPath") String secretPath
    ) {
        // Arrange
        VaultProperties vaultProperties = createVaultProperties();
        VaultClient vaultClient = new VaultClient(vaultProperties);
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        injectRestTemplate(vaultClient, mockRestTemplate);

        String vaultResponse = """
            {
                "data": {
                    "data": {
                        "username": "testuser",
                        "password": "testpass"
                    }
                }
            }
            """;

        when(mockRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        vaultClient.obterCredenciais(codVault, secretPath);
        vaultClient.clearCache();
        vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        verify(mockRestTemplate, times(2)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    /**
     * **Valida: Requisitos 2.1, 11.1, 11.2, 11.4**
     * 
     * Propriedade 2: Obtenção de Credenciais do Vault - Credenciais Nunca São Registradas em Logs
     * 
     * Para qualquer servidor que requer autenticação, o sistema deve obter credenciais
     * usando cod_vault e des_vault_secret da tabela server, e nunca deve registrar
     * essas credenciais em logs.
     * 
     * Este teste verifica que:
     * 1. Credenciais nunca aparecem em logs durante operações bem-sucedidas
     * 2. Credenciais nunca aparecem em logs durante operações com erro
     * 3. O token do Vault nunca aparece em mensagens de erro
     */
    @Property(tries = 100)
    void obterCredenciais_nuncaDeveRegistrarCredenciaisEmLogs(
        @ForAll("codVault") String codVault,
        @ForAll("secretPath") String secretPath,
        @ForAll("username") String username,
        @ForAll("password") String password
    ) {
        // Arrange
        VaultProperties vaultProperties = createVaultProperties();
        VaultClient vaultClient = new VaultClient(vaultProperties);
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        injectRestTemplate(vaultClient, mockRestTemplate);

        String vaultResponse = String.format("""
            {
                "data": {
                    "data": {
                        "username": "%s",
                        "password": "%s"
                    }
                }
            }
            """, username, password);

        when(mockRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Capturar logs usando um ListAppender
        ch.qos.logback.classic.Logger logger = 
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(VaultClient.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = 
            new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // Act - Operação bem-sucedida
            VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(codVault, secretPath);
            
            // Assert - Verificar que credenciais não aparecem nos logs
            for (ch.qos.logback.classic.spi.ILoggingEvent event : listAppender.list) {
                String logMessage = event.getFormattedMessage();
                
                // Credenciais nunca devem aparecer em logs
                assertFalse(logMessage.contains(username), 
                    "Username não deve aparecer em logs: " + logMessage);
                assertFalse(logMessage.contains(password), 
                    "Password não deve aparecer em logs: " + logMessage);
                assertFalse(logMessage.contains(vaultProperties.getToken()), 
                    "Token do Vault não deve aparecer em logs: " + logMessage);
            }
            
            // Limpar logs capturados
            listAppender.list.clear();
            
            // Act - Operação com erro (simular falha do Vault)
            when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
            )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            
            vaultClient.clearCache(); // Limpar cache para forçar nova chamada
            
            try {
                vaultClient.obterCredenciais(codVault, secretPath);
            } catch (VaultClient.VaultException e) {
                // Esperado
            }
            
            // Assert - Verificar que credenciais não aparecem nos logs de erro
            for (ch.qos.logback.classic.spi.ILoggingEvent event : listAppender.list) {
                String logMessage = event.getFormattedMessage();
                
                // Credenciais nunca devem aparecer em logs, mesmo em erros
                assertFalse(logMessage.contains(username), 
                    "Username não deve aparecer em logs de erro: " + logMessage);
                assertFalse(logMessage.contains(password), 
                    "Password não deve aparecer em logs de erro: " + logMessage);
                assertFalse(logMessage.contains(vaultProperties.getToken()), 
                    "Token do Vault não deve aparecer em logs de erro: " + logMessage);
            }
            
        } finally {
            // Cleanup
            logger.detachAppender(listAppender);
        }
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<String> codVault() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-')
            .ofMinLength(3)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> secretPath() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('/', '-', '_')
            .ofMinLength(3)
            .ofMaxLength(100)
            .filter(s -> !s.startsWith("/") && !s.endsWith("/"));
    }

    @Provide
    Arbitrary<String> username() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(3)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> password() {
        return Arbitraries.strings()
            .ascii()
            .ofMinLength(8)
            .ofMaxLength(100);
    }

    // ========== Helper Methods ==========

    private VaultProperties createVaultProperties() {
        VaultProperties properties = new VaultProperties();
        properties.setEnabled(true);
        properties.setUri("https://vault.example.com");
        properties.setToken("test-token");
        properties.getKv().setBackend("secret");
        properties.getKv().setEnabled(true);
        return properties;
    }

    private void injectRestTemplate(VaultClient vaultClient, RestTemplate restTemplate) {
        try {
            var field = VaultClient.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(vaultClient, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject RestTemplate", e);
        }
    }
}
