package com.controle.arquivos.common.client;

import com.controle.arquivos.common.config.VaultProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para VaultClient.
 * 
 * **Valida: Requisitos 11.1, 11.2, 11.3, 11.4, 11.5**
 */
@ExtendWith(MockitoExtension.class)
class VaultClientTest {

    @Mock
    private RestTemplate restTemplate;

    private VaultProperties vaultProperties;
    private VaultClient vaultClient;

    @BeforeEach
    void setUp() {
        vaultProperties = new VaultProperties();
        vaultProperties.setEnabled(true);
        vaultProperties.setUri("https://vault.example.com");
        vaultProperties.setToken("test-token");
        vaultProperties.getKv().setBackend("secret");
        vaultProperties.getKv().setEnabled(true);

        vaultClient = new VaultClient(vaultProperties);
        
        // Inject mocked RestTemplate via reflection
        try {
            var field = VaultClient.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(vaultClient, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void obterCredenciais_deveRetornarCredenciaisDoVault() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
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

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        assertNotNull(credenciais);
        assertEquals("testuser", credenciais.getUsername());
        assertEquals("testpass", credenciais.getPassword());
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveUsarCacheNaSegundaChamada() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
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

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        VaultClient.Credenciais credenciais1 = vaultClient.obterCredenciais(codVault, secretPath);
        VaultClient.Credenciais credenciais2 = vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        assertNotNull(credenciais1);
        assertNotNull(credenciais2);
        assertEquals(credenciais1.getUsername(), credenciais2.getUsername());
        assertEquals(credenciais1.getPassword(), credenciais2.getPassword());
        
        // Deve chamar o Vault apenas uma vez (segunda chamada usa cache)
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveLancarExcecaoQuandoVaultRetornaErro() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert
        assertThrows(VaultClient.VaultException.class, () -> {
            vaultClient.obterCredenciais(codVault, secretPath);
        });
    }

    @Test
    void obterCredenciais_deveLancarExcecaoQuandoCredenciaisNaoEncontradas() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
        String vaultResponse = """
            {
                "data": {
                    "data": {}
                }
            }
            """;

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act & Assert
        assertThrows(VaultClient.VaultException.class, () -> {
            vaultClient.obterCredenciais(codVault, secretPath);
        });
    }

    @Test
    void obterCredenciais_deveRetornarCredenciaisVaziasQuandoVaultDesabilitado() {
        // Arrange
        vaultProperties.setEnabled(false);
        VaultClient disabledVaultClient = new VaultClient(vaultProperties);
        
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";

        // Act
        VaultClient.Credenciais credenciais = disabledVaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        assertNotNull(credenciais);
        assertEquals("", credenciais.getUsername());
        assertEquals("", credenciais.getPassword());
        
        // Não deve chamar o Vault
        verify(restTemplate, never()).exchange(
            anyString(),
            any(HttpMethod.class),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveSuportarVaultKVv1() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
        // KV v1 não tem data.data, apenas data
        String vaultResponse = """
            {
                "data": {
                    "username": "testuser",
                    "password": "testpass"
                }
            }
            """;

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        assertNotNull(credenciais);
        assertEquals("testuser", credenciais.getUsername());
        assertEquals("testpass", credenciais.getPassword());
    }

    @Test
    void clearCache_deveLimparCacheDeCredenciais() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
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

        when(restTemplate.exchange(
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
        // Deve chamar o Vault duas vezes (cache foi limpo)
        verify(restTemplate, times(2)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void credenciais_toStringNaoDeveExporSenha() {
        // Arrange
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        // Act
        String toString = credenciais.toString();

        // Assert
        assertFalse(toString.contains("user"));
        assertFalse(toString.contains("pass"));
        assertTrue(toString.contains("***"));
    }

    @Test
    void obterCredenciais_deveConstruirURLCorretamente() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
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

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        verify(restTemplate).exchange(
            eq("https://vault.example.com/v1/secret/data/sftp/server1"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveRemoverTrailingSlashDaURL() {
        // Arrange
        vaultProperties.setUri("https://vault.example.com/");
        VaultClient clientWithSlash = new VaultClient(vaultProperties);
        
        try {
            var field = VaultClient.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(clientWithSlash, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
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

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        clientWithSlash.obterCredenciais(codVault, secretPath);

        // Assert
        verify(restTemplate).exchange(
            eq("https://vault.example.com/v1/secret/data/sftp/server1"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveLancarExcecaoQuandoJSONInvalido() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
        String invalidJson = "{ invalid json }";

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(invalidJson, HttpStatus.OK));

        // Act & Assert
        assertThrows(VaultClient.VaultException.class, () -> {
            vaultClient.obterCredenciais(codVault, secretPath);
        });
    }

    @Test
    void obterCredenciais_deveLancarExcecaoQuandoUsernameVazio() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
        String vaultResponse = """
            {
                "data": {
                    "data": {
                        "username": "",
                        "password": "testpass"
                    }
                }
            }
            """;

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act & Assert
        assertThrows(VaultClient.VaultException.class, () -> {
            vaultClient.obterCredenciais(codVault, secretPath);
        });
    }

    @Test
    void obterCredenciais_deveLancarExcecaoQuandoPasswordVazio() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
        String vaultResponse = """
            {
                "data": {
                    "data": {
                        "username": "testuser",
                        "password": ""
                    }
                }
            }
            """;

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act & Assert
        assertThrows(VaultClient.VaultException.class, () -> {
            vaultClient.obterCredenciais(codVault, secretPath);
        });
    }

    @Test
    void obterCredenciais_deveUsarTokenCorretoNoHeader() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";
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

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenAnswer(invocation -> {
            HttpEntity<String> entity = invocation.getArgument(2);
            String token = entity.getHeaders().getFirst("X-Vault-Token");
            assertEquals("test-token", token);
            return new ResponseEntity<>(vaultResponse, HttpStatus.OK);
        });

        // Act
        vaultClient.obterCredenciais(codVault, secretPath);

        // Assert
        verify(restTemplate, times(1)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveCriarChavesCacheDiferentes() {
        // Arrange
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

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>(vaultResponse, HttpStatus.OK));

        // Act
        vaultClient.obterCredenciais("VAULT1", "path1");
        vaultClient.obterCredenciais("VAULT2", "path2");
        vaultClient.obterCredenciais("VAULT1", "path1"); // Cache hit

        // Assert
        // Deve chamar o Vault 2 vezes (uma para cada chave única)
        verify(restTemplate, times(2)).exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void obterCredenciais_deveLancarExcecaoQuandoRestTemplateThrowsException() {
        // Arrange
        String codVault = "SFTP_SERVER_1";
        String secretPath = "sftp/server1";

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Network error"));

        // Act & Assert
        VaultClient.VaultException exception = assertThrows(VaultClient.VaultException.class, () -> {
            vaultClient.obterCredenciais(codVault, secretPath);
        });
        
        assertTrue(exception.getMessage().contains("Falha ao obter credenciais do Vault"));
        assertNotNull(exception.getCause());
    }
}
