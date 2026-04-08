package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.consumer.config.SftpConfig;
import com.concil.edi.consumer.dto.ServerConfigurationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * Service for uploading files to destination servers (S3 or SFTP) using streaming.
 * 
 * Requirements: 13.1, 13.2, 13.3, 13.4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {
    
    private final S3Client s3Client;
    private final SftpConfig sftpConfig;
    private final ServerPathRepository serverPathRepository;
    
    /**
     * Upload file to AWS S3 using streaming (multipart upload).
     * Does not load entire file into memory.
     * 
     * @param inputStream Source InputStream
     * @param bucketName S3 bucket name
     * @param key S3 object key (path + filename)
     * @param contentLength File size in bytes
     * @throws RuntimeException if upload fails
     */
    public void uploadToS3(InputStream inputStream, String bucketName, String key, long contentLength) {
        log.info("Uploading file to S3: bucket={}, key={}", bucketName, key);
        
        try {
            // Requirement 13.3: Use multipart upload with InputStream
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
            
            // AWS SDK handles streaming automatically with RequestBody.fromInputStream
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            
            log.info("Successfully uploaded file to S3: {}", key);
            
        } catch (Exception e) {
            log.error("Failed to upload file to S3: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to upload to S3: " + e.getMessage(), e);
        }
    }
    
    /**
     * Upload file to SFTP server using streaming.
     * Does not load entire file into memory.
     * 
     * @param inputStream Source InputStream
     * @param config Server configuration with credentials
     * @param remotePath Remote file path on SFTP server
     * @throws RuntimeException if upload fails
     */
    public void uploadToSftp(InputStream inputStream, ServerConfigurationDTO config, String remotePath) {
        log.info("Uploading file to SFTP: codVault={}, path={}", config.getCodVault(), remotePath);
        
        try {
            // Requirement 13.1: Obtain configuration using idt_sever_path_destination
            SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> sessionFactory = 
                sftpConfig.getOrCreateSessionFactory(
                    config.getCodVault(),
                    config.getDesVaultSecret()
                );
            
            Session<org.apache.sshd.sftp.client.SftpClient.DirEntry> session = sessionFactory.getSession();
            
            // Requirement 13.4: Use OutputStream for streaming transfer
            session.write(inputStream, remotePath);
            
            log.info("Successfully uploaded file to SFTP: {}", remotePath);
            
        } catch (Exception e) {
            log.error("Failed to upload file to SFTP: path={}", remotePath, e);
            throw new RuntimeException("Failed to upload to SFTP: " + e.getMessage(), e);
        }
    }
    
    /**
     * Returns the size in bytes of an object stored in S3.
     * Uses HeadObject to retrieve metadata without downloading the file.
     *
     * @param bucketName S3 bucket name
     * @param key        S3 object key
     * @return size in bytes
     * @throws RuntimeException if the operation fails
     * Requirements: 1.1
     */
    public long getS3ObjectSize(String bucketName, String key) {
        log.info("Getting S3 object size: bucket={}, key={}", bucketName, key);
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            long size = response.contentLength();
            log.debug("S3 object size: bucket={}, key={}, size={}", bucketName, key, size);
            return size;
        } catch (Exception e) {
            log.error("Failed to get S3 object size: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to get S3 object size: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the size in bytes of a file on a remote SFTP server.
     *
     * @param config     SFTP server configuration with credentials
     * @param remotePath Full remote path of the file
     * @return size in bytes
     * @throws RuntimeException if the operation fails
     * Requirements: 1.1
     */
    public long getSftpFileSize(ServerConfigurationDTO config, String remotePath) {
        log.info("Getting SFTP file size: codVault={}, path={}", config.getCodVault(), remotePath);
        SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> sessionFactory =
                sftpConfig.getOrCreateSessionFactory(config.getCodVault(), config.getDesVaultSecret());
        Session<org.apache.sshd.sftp.client.SftpClient.DirEntry> session = sessionFactory.getSession();
        try {
            org.apache.sshd.sftp.client.SftpClient.DirEntry[] entries = session.list(remotePath);
            if (entries == null || entries.length == 0) {
                throw new RuntimeException("File not found on SFTP: " + remotePath);
            }
            long size = entries[0].getAttributes().getSize();
            log.debug("SFTP file size: path={}, size={}", remotePath, size);
            return size;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get SFTP file size: path={}", remotePath, e);
            throw new RuntimeException("Failed to get SFTP file size: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    /**
     * Helper method to get server configuration by serverPathId.
     * Used internally to resolve destination configuration.
     * 
     * @param serverPathId Server path ID
     * @return Server configuration DTO
     */
    public ServerConfigurationDTO getServerConfiguration(Long serverPathId) {
        ServerPath serverPath = serverPathRepository.findById(serverPathId)
            .orElseThrow(() -> new IllegalArgumentException(
                "ServerPath not found for id: " + serverPathId));
        
        Server server = serverPath.getServer();
        if (server == null) {
            throw new IllegalArgumentException(
                "Server not found for ServerPath id: " + serverPathId);
        }
        
        // Parse host and port from cod_server
        String codServer = server.getCodServer();
        String host;
        int port = 22;
        
        if (codServer.contains("://")) {
            String[] parts = codServer.split("://")[1].split(":");
            host = parts[0];
            if (parts.length > 1) {
                port = Integer.parseInt(parts[1]);
            }
        } else if (codServer.contains(":")) {
            String[] parts = codServer.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } else {
            host = codServer;
        }
        
        ServerConfigurationDTO config = new ServerConfigurationDTO();
        config.setServerId(server.getIdtServer());
        config.setCodServer(server.getCodServer());
        config.setCodVault(server.getCodVault());
        config.setDesVaultSecret(server.getDesVaultSecret());
        config.setServerType(server.getDesServerType());
        config.setHost(host);
        config.setPort(port);
        config.setServerPathId(serverPath.getIdtSeverPath());
        config.setPath(serverPath.getDesPath());
        config.setAcquirerId(serverPath.getIdtAcquirer());
        
        return config;
    }
}
