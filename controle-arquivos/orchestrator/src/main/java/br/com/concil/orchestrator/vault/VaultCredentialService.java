package br.com.concil.orchestrator.vault;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Serviço responsável por buscar credenciais no Vault.
 * Em ambiente local (dev), lê de variáveis de ambiente como fallback.
 * Em produção, integrar com HashiCorp Vault ou AWS Secrets Manager.
 */
@Slf4j
@Service
public class VaultCredentialService {

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    /**
     * Retorna as credenciais para um servidor a partir do Vault.
     * Chaves esperadas no secret: host, port, username, password
     */
    public Map<String, String> getCredentials(String codVault, String desVaultSecret) {
        if (vaultEnabled) {
            // TODO: integrar com HashiCorp Vault / AWS Secrets Manager
            throw new UnsupportedOperationException("Vault não configurado para produção");
        }
        // Fallback para variáveis de ambiente em dev
        // Formato esperado: SFTP_HOST, SFTP_PORT, SFTP_USER, SFTP_PASSWORD
        String prefix = desVaultSecret.toUpperCase().replace("/", "_").replace("-", "_");
        log.debug("Buscando credenciais via env para: {}", prefix);
        return Map.of(
                "host",     getEnv(prefix + "_HOST", "localhost"),
                "port",     getEnv(prefix + "_PORT", "22"),
                "username", getEnv(prefix + "_USER", "user"),
                "password", getEnv(prefix + "_PASSWORD", "password")
        );
    }

    private String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
