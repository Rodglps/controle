package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.consumer.config.SftpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Service for downloading files from SFTP servers using streaming.
 * 
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileDownloadService {
    
    private final ServerPathRepository serverPathRepository;
    private final SftpConfig sftpConfig;
    
    /**
     * Open an InputStream for a file on SFTP server without loading complete content.
     * Uses server configuration from database based on idt_sever_path_origin.
     * 
     * @param serverPathOriginId Server path origin ID from sever_paths table
     * @param filename Name of the file to download
     * @return InputStream for the file (caller must close)
     * @throws IllegalArgumentException if serverPathOriginId is invalid
     * @throws RuntimeException if SFTP connection or file access fails
     */
    public InputStream openInputStream(Long serverPathOriginId, String filename) {
        log.info("Opening InputStream for file: {} from serverPathId: {}", filename, serverPathOriginId);
        
        // Requirement 12.1: Obtain configuration using idt_sever_path_origin
        ServerPath serverPath = serverPathRepository.findWithServerByIdtSeverPath(serverPathOriginId)
            .orElseThrow(() -> new IllegalArgumentException(
                "ServerPath not found for id: " + serverPathOriginId));
        
        Server server = serverPath.getServer();
        if (server == null) {
            throw new IllegalArgumentException(
                "Server not found for ServerPath id: " + serverPathOriginId);
        }
        
        try {
            // Requirement 12.2: Obtain credentials via VaultConfig (includes host and port)
            SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> sessionFactory = 
                sftpConfig.getOrCreateSessionFactory(
                    server.getCodVault(), 
                    server.getDesVaultSecret()
                );
            
            // Requirement 12.3: Open InputStream without loading complete content
            Session<org.apache.sshd.sftp.client.SftpClient.DirEntry> session = sessionFactory.getSession();
            
            String remotePath = serverPath.getDesPath() + "/" + filename;
            
            // Requirement 12.4: Return InputStream without loading file into memory
            InputStream inputStream = session.readRaw(remotePath);
            
            log.info("Successfully opened InputStream for file: {} from {}", filename, remotePath);
            
            return inputStream;
            
        } catch (Exception e) {
            log.error("Failed to open InputStream for file: {} from serverPathId: {}", 
                filename, serverPathOriginId, e);
            throw new RuntimeException("Failed to open SFTP InputStream: " + e.getMessage(), e);
        }
    }
}
