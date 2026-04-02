package com.concil.edi.consumer.config;

import com.concil.edi.consumer.dto.CredentialsDTO;
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
 * Configuration for Spring Integration SFTP in Consumer.
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
     * @param codVault Vault code for credentials (includes host, port, user, password)
     * @param vaultSecret Vault secret path for credentials
     * @return Cached or newly created SessionFactory instance
     */
    public SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> getOrCreateSessionFactory(
            String codVault, 
            String vaultSecret) {
        
        String cacheKey = generateCacheKey(codVault, vaultSecret);
        
        return sessionFactoryCache.computeIfAbsent(cacheKey, key -> {
            log.info("Creating new SessionFactory for: {}", cacheKey);
            return createSessionFactoryInternal(codVault, vaultSecret);
        });
    }
    
    /**
     * Generates a unique cache key for a server configuration.
     */
    private String generateCacheKey(String codVault, String vaultSecret) {
        return String.format("%s:%s", codVault, vaultSecret);
    }
    
    /**
     * Creates a new session factory for SFTP connections.
     */
    private SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> createSessionFactoryInternal(
            String codVault, 
            String vaultSecret) {
        
        // Get credentials from Vault (includes host, port, user, password)
        CredentialsDTO credentials = vaultConfig.getCredentials(codVault, vaultSecret);
        
        String host = credentials.getHost();
        int port = credentials.getPort() != null ? Integer.parseInt(credentials.getPort()) : 22;
        
        log.debug("Creating SFTP session factory for host: {}:{}", host, port);
        
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
