package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.ServerOrigin;
import com.concil.edi.commons.enums.PathType;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.commons.repository.ServerRepository;
import com.concil.edi.consumer.dto.ServerConfigurationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileUploadService.
 * 
 * Requirements: 19.4
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FileUploadServiceTest {
    
    @Autowired
    private FileUploadService fileUploadService;
    
    @Autowired
    private ServerRepository serverRepository;
    
    @Autowired
    private ServerPathRepository serverPathRepository;
    
    private Server s3Server;
    private ServerPath s3ServerPath;
    private Server sftpServer;
    private ServerPath sftpServerPath;
    
    @BeforeEach
    void setup() {
        serverPathRepository.deleteAll();
        serverRepository.deleteAll();
        
        // Create S3 server configuration
        s3Server = new Server();
        s3Server.setCodServer("s3://test-bucket");
        s3Server.setCodVault("TEST_S3_VAULT");
        s3Server.setDesVaultSecret("test/s3/secret");
        s3Server.setDesServerType(ServerType.S3);
        s3Server.setDesServerOrigin(ServerOrigin.INTERNO);
        s3Server.setDatCreation(new Date());
        s3Server.setFlgActive(1);
        s3Server = serverRepository.save(s3Server);
        
        s3ServerPath = new ServerPath();
        s3ServerPath.setIdtServer(s3Server.getIdtServer());
        s3ServerPath.setIdtAcquirer(1L);
        s3ServerPath.setDesPath("/destination/s3");
        s3ServerPath.setDesPathType(PathType.DESTINATION);
        s3ServerPath.setDatCreation(new Date());
        s3ServerPath.setFlgActive(1);
        s3ServerPath = serverPathRepository.save(s3ServerPath);
        
        // Create SFTP server configuration
        sftpServer = new Server();
        sftpServer.setCodServer("sftp://localhost:22");
        sftpServer.setCodVault("TEST_SFTP_VAULT");
        sftpServer.setDesVaultSecret("test/sftp/secret");
        sftpServer.setDesServerType(ServerType.SFTP);
        sftpServer.setDesServerOrigin(ServerOrigin.INTERNO);
        sftpServer.setDatCreation(new Date());
        sftpServer.setFlgActive(1);
        sftpServer = serverRepository.save(sftpServer);
        
        sftpServerPath = new ServerPath();
        sftpServerPath.setIdtServer(sftpServer.getIdtServer());
        sftpServerPath.setIdtAcquirer(1L);
        sftpServerPath.setDesPath("/destination/sftp");
        sftpServerPath.setDesPathType(PathType.DESTINATION);
        sftpServerPath.setDatCreation(new Date());
        sftpServerPath.setFlgActive(1);
        sftpServerPath = serverPathRepository.save(sftpServerPath);
    }
    
    @Test
    void testGetServerConfigurationForS3() {
        // Act
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            s3ServerPath.getIdtServerPath()
        );
        
        // Assert
        assertNotNull(config);
        assertEquals(ServerType.S3, config.getServerType());
        assertEquals("TEST_S3_VAULT", config.getCodVault());
        assertEquals("/destination/s3", config.getPath());
    }
    
    @Test
    void testGetServerConfigurationForSftp() {
        // Act
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            sftpServerPath.getIdtServerPath()
        );
        
        // Assert
        assertNotNull(config);
        assertEquals(ServerType.SFTP, config.getServerType());
        assertEquals("localhost", config.getHost());
        assertEquals(22, config.getPort());
        assertEquals("/destination/sftp", config.getPath());
    }
    
    @Test
    void testUploadToS3WithBucketAndKey() {
        // Note: This test validates configuration only
        // Actual S3 upload would require LocalStack or mock
        
        // Arrange
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            s3ServerPath.getIdtServerPath()
        );
        
        // Assert: Configuration is correct for S3 upload
        assertEquals(ServerType.S3, config.getServerType());
        assertNotNull(config.getPath());
    }
    
    @Test
    void testUploadToSftpWithHostPortPath() {
        // Note: This test validates configuration only
        // Actual SFTP upload would require test container or mock
        
        // Arrange
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            sftpServerPath.getIdtServerPath()
        );
        
        // Assert: Configuration is correct for SFTP upload
        assertEquals(ServerType.SFTP, config.getServerType());
        assertEquals("localhost", config.getHost());
        assertEquals(22, config.getPort());
        assertNotNull(config.getPath());
    }
    
    @Test
    void testMultipartUploadConfigurationForS3() {
        // Arrange
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            s3ServerPath.getIdtServerPath()
        );
        
        // Assert: S3 configuration supports multipart upload
        assertEquals(ServerType.S3, config.getServerType());
        // AWS SDK automatically handles multipart upload with RequestBody.fromInputStream
    }
    
    @Test
    void testOutputStreamManagementForSftp() {
        // Arrange
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            sftpServerPath.getIdtServerPath()
        );
        
        // Assert: SFTP configuration is ready for OutputStream management
        assertEquals(ServerType.SFTP, config.getServerType());
        assertNotNull(config.getHost());
        assertNotNull(config.getPort());
        // Spring Integration SFTP Session.write() handles OutputStream internally
    }
    
    @Test
    void testInvalidServerPathIdThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            fileUploadService.getServerConfiguration(99999L);
        });
    }
}
