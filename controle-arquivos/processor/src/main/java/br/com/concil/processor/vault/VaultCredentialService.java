package br.com.concil.processor.vault;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
public class VaultCredentialService {

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    public Map<String, String> getCredentials(String codVault, String desVaultSecret) {
        if (vaultEnabled) {
            throw new UnsupportedOperationException("Vault não configurado para produção");
        }
        String prefix = desVaultSecret.toUpperCase().replace("/", "_").replace("-", "_");
        log.debug("Buscando credenciais via env para: {}", prefix);
        return Map.of(
                "host",       getEnv(prefix + "_HOST", "localhost"),
                "port",       getEnv(prefix + "_PORT", "22"),
                "username",   getEnv(prefix + "_USER", "user"),
                "password",   getEnv(prefix + "_PASSWORD", "password"),
                "bucket",     getEnv(prefix + "_BUCKET", "controle-arquivos"),
                "region",     getEnv(prefix + "_REGION", "us-east-1"),
                "endpoint",   getEnv(prefix + "_ENDPOINT", "http://localhost:4566"),
                "access-key", getEnv(prefix + "_ACCESS_KEY", "test"),
                "secret-key", getEnv(prefix + "_SECRET_KEY", "test")
        );
    }

    private String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
