package com.controle.arquivos.orchestrator.integration;

import com.controle.arquivos.common.client.VaultClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for integration tests.
 * Provides mock implementations of external dependencies.
 */
@TestConfiguration
public class TestConfiguration {

    /**
     * Mock VaultClient that returns test credentials.
     * Used when Vault is disabled in tests.
     */
    @Bean
    @Primary
    public VaultClient vaultClient() {
        return new VaultClient(null) {
            @Override
            public Credenciais obterCredenciais(String codVault, String secretPath) {
                // Return test credentials for SFTP
                return new Credenciais("testuser", "testpass");
            }
        };
    }
}
