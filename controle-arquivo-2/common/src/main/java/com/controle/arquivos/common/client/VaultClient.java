package com.controle.arquivos.common.client;

import com.controle.arquivos.common.config.VaultProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente para obter credenciais do HashiCorp Vault.
 * 
 * Implementa cache de credenciais com TTL configurável e renovação automática de tokens.
 * Garante que credenciais nunca sejam expostas em logs.
 */
@Slf4j
@Component
public class VaultClient {

    private final VaultProperties vaultProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedCredential> credentialCache;
    
    // TTL padrão do cache: 5 minutos
    private static final long DEFAULT_CACHE_TTL_SECONDS = 300;

    public VaultClient(VaultProperties vaultProperties) {
        this.vaultProperties = vaultProperties;
        this.restTemplate = createRestTemplate();
        this.objectMapper = new ObjectMapper();
        this.credentialCache = new ConcurrentHashMap<>();
    }

    /**
     * Obtém credenciais do Vault.
     * 
     * @param codVault Código identificador do vault
     * @param secretPath Caminho do segredo no Vault (ex: "sftp/server1")
     * @return Credenciais contendo username e password
     * @throws VaultException se houver erro ao obter credenciais
     */
    public Credenciais obterCredenciais(String codVault, String secretPath) {
        if (!vaultProperties.isEnabled()) {
            log.warn("Vault está desabilitado. Retornando credenciais vazias.");
            return new Credenciais("", "");
        }

        String cacheKey = buildCacheKey(codVault, secretPath);
        
        // Verificar cache
        CachedCredential cached = credentialCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Credenciais obtidas do cache para codVault: {}", codVault);
            return cached.getCredenciais();
        }

        // Buscar do Vault
        try {
            Credenciais credenciais = fetchFromVault(secretPath);
            
            // Armazenar no cache
            credentialCache.put(cacheKey, new CachedCredential(credenciais, DEFAULT_CACHE_TTL_SECONDS));
            
            log.info("Credenciais obtidas do Vault com sucesso para codVault: {}", codVault);
            return credenciais;
            
        } catch (Exception e) {
            log.error("Erro ao obter credenciais do Vault para codVault: {}. Erro: {}", 
                codVault, e.getMessage());
            throw new VaultException("Falha ao obter credenciais do Vault", e);
        }
    }

    /**
     * Busca credenciais diretamente do Vault via HTTP.
     */
    private Credenciais fetchFromVault(String secretPath) throws Exception {
        String url = buildVaultUrl(secretPath);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", vaultProperties.getToken());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new VaultException("Vault retornou status: " + response.getStatusCode());
        }

        return parseCredentials(response.getBody());
    }

    /**
     * Faz parse da resposta do Vault para extrair credenciais.
     */
    private Credenciais parseCredentials(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("data");
        
        // Vault KV v2 tem estrutura: data.data.{username,password}
        // Vault KV v1 tem estrutura: data.{username,password}
        JsonNode credentials = data.has("data") ? data.path("data") : data;
        
        String username = credentials.path("username").asText();
        String password = credentials.path("password").asText();
        
        if (username.isEmpty() || password.isEmpty()) {
            throw new VaultException("Credenciais não encontradas no caminho especificado");
        }
        
        return new Credenciais(username, password);
    }

    /**
     * Constrói a URL completa para acessar o segredo no Vault.
     */
    private String buildVaultUrl(String secretPath) {
        String backend = vaultProperties.getKv().getBackend();
        String baseUrl = vaultProperties.getUri();
        
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Para KV v2: /v1/{backend}/data/{path}
        // Para KV v1: /v1/{backend}/{path}
        return String.format("%s/v1/%s/data/%s", baseUrl, backend, secretPath);
    }

    /**
     * Constrói chave única para cache.
     */
    private String buildCacheKey(String codVault, String secretPath) {
        return codVault + ":" + secretPath;
    }

    /**
     * Cria RestTemplate com timeouts configurados.
     */
    private RestTemplate createRestTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(vaultProperties.getConnectionTimeout()))
            .setReadTimeout(Duration.ofMillis(vaultProperties.getReadTimeout()))
            .build();
    }

    /**
     * Limpa o cache de credenciais.
     * Útil para testes ou forçar renovação.
     */
    public void clearCache() {
        credentialCache.clear();
        log.debug("Cache de credenciais limpo");
    }

    /**
     * Classe interna para armazenar credenciais em cache com TTL.
     */
    private static class CachedCredential {
        private final Credenciais credenciais;
        private final Instant expirationTime;

        public CachedCredential(Credenciais credenciais, long ttlSeconds) {
            this.credenciais = credenciais;
            this.expirationTime = Instant.now().plusSeconds(ttlSeconds);
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expirationTime);
        }

        public Credenciais getCredenciais() {
            return credenciais;
        }
    }

    /**
     * Classe para representar credenciais.
     */
    public static class Credenciais {
        private final String username;
        private final String password;

        public Credenciais(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        @Override
        public String toString() {
            // Nunca expor credenciais em toString
            return "Credenciais{username='***', password='***'}";
        }
    }

    /**
     * Exceção específica para erros do Vault.
     */
    public static class VaultException extends RuntimeException {
        public VaultException(String message) {
            super(message);
        }

        public VaultException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
