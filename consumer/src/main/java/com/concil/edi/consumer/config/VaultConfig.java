package com.concil.edi.consumer.config;

import com.concil.edi.consumer.dto.CredentialsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Configuration class for obtaining credentials from Vault or environment variables.
 * For MVP, uses environment variables as fallback with JSON format: {"user":"...", "password":"..."}
 */
@Component
@Slf4j
public class VaultConfig {
    
    private final ObjectMapper objectMapper;
    
    public VaultConfig() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Get credentials for a server.
     * For MVP, reads from environment variable with name equal to codVault.
     * 
     * @param codVault Vault code (used as environment variable name)
     * @param vaultSecret Vault secret path (not used in MVP)
     * @return Credentials object with host, port, user and password
     * @throws IllegalArgumentException if environment variable not found or JSON parsing fails
     */
    public CredentialsDTO getCredentials(String codVault, String vaultSecret) {
        log.debug("Getting credentials for codVault: {}, vaultSecret: {}", codVault, vaultSecret);
        
        // For MVP: read from environment variable
        String envValue = System.getenv(codVault);
        
        if (envValue == null || envValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Environment variable not found for codVault: " + codVault);
        }
        
        try {
            // Parse JSON: {"host":"...", "port":"...", "user":"...", "password":"..."}
            CredentialsDTO credentials = objectMapper.readValue(envValue, CredentialsDTO.class);
            
            if (credentials.getHost() == null || credentials.getUser() == null || credentials.getPassword() == null) {
                throw new IllegalArgumentException("Invalid credentials JSON format for codVault: " + codVault + 
                    ". Expected format: {\"host\":\"...\", \"port\":\"...\", \"user\":\"...\", \"password\":\"...\"}");
            }
            
            log.debug("Successfully retrieved credentials for codVault: {}", codVault);
            return credentials;
            
        } catch (Exception e) {
            log.error("Failed to parse credentials JSON for codVault: {}", codVault, e);
            throw new IllegalArgumentException("Failed to parse credentials JSON for codVault: " + codVault + 
                ". Expected format: {\"host\":\"...\", \"port\":\"...\", \"user\":\"...\", \"password\":\"...\"}", e);
        }
    }
}
