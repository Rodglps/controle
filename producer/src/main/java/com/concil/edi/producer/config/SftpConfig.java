package com.concil.edi.producer.config;

import com.concil.edi.producer.dto.CredentialsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for Spring Integration SFTP.
 * Provides session factory for SFTP connections with caching to prevent resource leaks.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SftpConfig {
    
    private final VaultConfig vaultConfig;
    
    // Cache for SessionFactory instances to prevent resource leaks
    private final ConcurrentHashMap<String, SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry>> sessionFactoryCache = new ConcurrentHashMap<>();
    
    /**
     * Gets or creates a cached session factory for SFTP connections.
     * Reuses existing SessionFactory instances per server configuration to prevent resource leaks.
     * 
     * @param host SFTP server host
     * @param port SFTP server port
     * @param codVault Vault code for credentials
     * @param vaultSecret Vault secret path for credentials
     * @return Cached or newly created SessionFactory instance
     */
    public SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> getOrCreateSessionFactory(
            String host, 
            int port, 
            String codVault, 
            String vaultSecret) {
        
        String cacheKey = generateCacheKey(host, port, codVault, vaultSecret);
        
        return sessionFactoryCache.computeIfAbsent(cacheKey, key -> {
            log.info("Creating new SessionFactory for: {}", cacheKey);
            return createSessionFactoryInternal(host, port, codVault, vaultSecret);
        });
    }
    
    /**
     * Generates a unique cache key for a server configuration.
     * 
     * @param host SFTP server host
     * @param port SFTP server port
     * @param codVault Vault code for credentials
     * @param vaultSecret Vault secret path for credentials
     * @return Cache key in format "host:port:codVault:vaultSecret"
     */
    private String generateCacheKey(String host, int port, String codVault, String vaultSecret) {
        return String.format("%s:%d:%s:%s", host, port, codVault, vaultSecret);
    }
    
    /**
     * Creates a new session factory for SFTP connections.
     * Internal method used by getOrCreateSessionFactory for actual SessionFactory creation.
     * 
     * @param host SFTP server host
     * @param port SFTP server port
     * @param codVault Vault code for credentials
     * @param vaultSecret Vault secret path for credentials
     * @return Newly created SessionFactory instance
     */
    private SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> createSessionFactoryInternal(
            String host, 
            int port, 
            String codVault, 
            String vaultSecret) {
        
        log.debug("Creating SFTP session factory for host: {}:{}", host, port);
        
        // Get credentials from Vault
        CredentialsDTO credentials = vaultConfig.getCredentials(codVault, vaultSecret);
        
        // Create default SFTP session factory
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(host);
        factory.setPort(port);
        factory.setUser(credentials.getUser());
        factory.setPassword(credentials.getPassword());
        factory.setAllowUnknownKeys(true); // For MVP - disable strict host key checking
        
        // Wrap in caching session factory for connection pooling
        CachingSessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> cachingFactory = 
            new CachingSessionFactory<>(factory, 10); // Pool size of 10
        
        return cachingFactory;
    }
    
    /**
     * Cleanup method called on application shutdown.
     * Closes all cached SessionFactory instances to release connection pools.
     */
    @PreDestroy
    public void destroy() {
        log.info("Closing {} cached SessionFactory instances", sessionFactoryCache.size());
        sessionFactoryCache.values().forEach(factory -> {
            try {
                if (factory instanceof DisposableBean) {
                    ((DisposableBean) factory).destroy();
                }
            } catch (Exception e) {
                log.error("Error closing SessionFactory", e);
            }
        });
        sessionFactoryCache.clear();
    }
}
