package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.FileType;
import com.concil.edi.producer.config.SftpConfig;
import com.concil.edi.producer.config.VaultConfig;
import com.concil.edi.producer.dto.CredentialsDTO;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for SFTP operations using Spring Integration SFTP.
 * Provides high-level abstractions for SFTP file operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SftpService {
    
    private final SftpConfig sftpConfig;
    private final VaultConfig vaultConfig;
    
    private static final List<String> ALLOWED_FILE_TYPES = List.of("CSV", "JSON", "TXT", "XML", "OFX");
    
    /**
     * List files in SFTP directory using Spring Integration SFTP.
     * 
     * @param config Server configuration containing host, port, path, and credentials
     * @return List of file metadata for valid files
     */
    public List<FileMetadataDTO> listFiles(ServerConfigurationDTO config) {
        log.debug("Listing files for config: {}", config.getCodServer());
        
        List<FileMetadataDTO> fileMetadataList = new ArrayList<>();
        
        // Parse host and port from codServer
        String host = parseHost(config.getCodServer());
        int port = parsePort(config.getCodServer());
        
        // Create session factory for this configuration
        SessionFactory<SftpClient.DirEntry> sessionFactory = 
            sftpConfig.getOrCreateSessionFactory(host, port, config.getCodVault(), config.getDesVaultSecret());
        
        Session<SftpClient.DirEntry> session = null;
        
        try {
            // Get session from factory
            session = sessionFactory.getSession();
            
            // List files in directory using Spring Integration API
            SftpClient.DirEntry[] files = session.list(config.getOriginPath());
            
            for (SftpClient.DirEntry entry : files) {
                if (entry.getAttributes().isDirectory()) {
                    continue; // Skip directories
                }
                
                String filename = entry.getFilename();
                
                // Skip hidden files and special entries
                if (filename.startsWith(".")) {
                    continue;
                }
                
                // Extract file extension and validate type
                String extension = getFileExtension(filename);
                if (!isValidFileType(extension)) {
                    log.debug("Skipping file with invalid type: {}", filename);
                    continue;
                }
                
                FileType fileType = FileType.valueOf(extension.toUpperCase());
                long fileSize = entry.getAttributes().getSize();
                long mTime = entry.getAttributes().getModifyTime().toMillis();
                Timestamp timestamp = new Timestamp(mTime);
                
                FileMetadataDTO metadata = new FileMetadataDTO(filename, fileSize, timestamp, fileType);
                fileMetadataList.add(metadata);
            }
            
            log.info("Found {} valid files in {}", fileMetadataList.size(), config.getOriginPath());
            
        } catch (Exception e) {
            log.error("Error listing files from SFTP: {}", config.getCodServer(), e);
            throw new RuntimeException("Failed to list files from SFTP: " + config.getCodServer(), e);
        } finally {
            // Close session (will be returned to pool by CachingSessionFactory)
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
        
        return fileMetadataList;
    }
    
    /**
     * Get credentials from Vault configuration.
     * 
     * @param codVault Vault code
     * @param vaultSecret Vault secret path
     * @return Credentials DTO
     */
    public CredentialsDTO getCredentials(String codVault, String vaultSecret) {
        return vaultConfig.getCredentials(codVault, vaultSecret);
    }
    
    /**
     * Parse host from codServer.
     * For MVP, assumes format like "sftp-origin" or "host:port".
     * In production, this would parse actual host from configuration.
     */
    private String parseHost(String codServer) {
        if (codServer.contains(":")) {
            return codServer.split(":")[0];
        }
        // For MVP, use environment variable or default
        return System.getenv("SFTP_HOST") != null ? System.getenv("SFTP_HOST") : "localhost";
    }
    
    /**
     * Parse port from codServer.
     * For MVP, assumes format like "host:port" or defaults to 22.
     */
    private int parsePort(String codServer) {
        if (codServer.contains(":")) {
            return Integer.parseInt(codServer.split(":")[1]);
        }
        // Default SFTP port
        return 22;
    }
    
    /**
     * Extract file extension from filename.
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toUpperCase();
        }
        return "";
    }
    
    /**
     * Validate if file type is allowed (CSV, JSON, TXT, XML, OFX).
     */
    private boolean isValidFileType(String extension) {
        return ALLOWED_FILE_TYPES.contains(extension.toUpperCase());
    }
}
