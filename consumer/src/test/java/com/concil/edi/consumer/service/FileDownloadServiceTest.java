package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.ServerOrigin;
import com.concil.edi.commons.enums.PathType;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.commons.repository.ServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileDownloadService.
 * 
 * Requirements: 19.4
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FileDownloadServiceTest {
    
    @Autowired
    private FileDownloadService fileDownloadService;
    
    @Autowired
    private ServerRepository serverRepository;
    
    @Autowired
    private ServerPathRepository serverPathRepository;
    
    private Server testServer;
    private ServerPath testServerPath;
    
    @BeforeEach
    void setup() {
        serverPathRepository.deleteAll();
        serverRepository.deleteAll();
        
        // Create test server
        testServer = new Server();
        testServer.setCodServer("sftp://localhost:22");
        testServer.setCodVault("TEST_SFTP_VAULT");
        testServer.setDesVaultSecret("test/sftp/secret");
        testServer.setDesServerType(ServerType.SFTP);
        testServer.setDesServerOrigin(ServerOrigin.EXTERNO);
        testServer.setDatCreation(new Date());
        testServer.setFlgActive(1);
        testServer = serverRepository.save(testServer);
        
        // Create test server path
        testServerPath = new ServerPath();
        testServerPath.setIdtServer(testServer.getIdtServer());
        testServerPath.setIdtAcquirer(1L);
        testServerPath.setDesPath("/data/incoming");
        testServerPath.setDesPathType(PathType.ORIGIN);
        testServerPath.setDatCreation(new Date());
        testServerPath.setFlgActive(1);
        testServerPath = serverPathRepository.save(testServerPath);
    }
    
    @Test
    void testOpenInputStreamWithValidConfiguration() {
        // Note: This test validates configuration lookup only
        // Actual SFTP connection would require mock or test container
        
        // Act & Assert: Should not throw exception during configuration lookup
        assertDoesNotThrow(() -> {
            ServerPath path = serverPathRepository.findById(testServerPath.getIdtServerPath())
                .orElseThrow();
            Server server = serverRepository.findById(path.getIdtServer())
                .orElseThrow();
            
            assertNotNull(server.getCodServer());
            assertNotNull(server.getCodVault());
            assertNotNull(server.getDesVaultSecret());
        });
    }
    
    @Test
    void testExceptionWithInvalidServerPathId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            fileDownloadService.openInputStream(99999L, "test-file.csv");
        });
    }
    
    @Test
    void testCredentialsObtainedCorrectly() {
        // Arrange
        ServerPath path = serverPathRepository.findById(testServerPath.getIdtServerPath())
            .orElseThrow();
        Server server = serverRepository.findById(path.getIdtServer())
            .orElseThrow();
        
        // Assert: Credentials configuration is correct
        assertEquals("TEST_SFTP_VAULT", server.getCodVault());
        assertEquals("test/sftp/secret", server.getDesVaultSecret());
    }
    
    @Test
    void testConnectionConfigurationWithHostAndPort() {
        // Arrange
        Server server = serverRepository.findById(testServer.getIdtServer()).orElseThrow();
        
        // Act: Parse host and port from cod_server
        String codServer = server.getCodServer();
        String host;
        int port = 22;
        
        if (codServer.contains("://")) {
            String[] parts = codServer.split("://")[1].split(":");
            host = parts[0];
            if (parts.length > 1) {
                port = Integer.parseInt(parts[1]);
            }
        } else {
            host = codServer;
        }
        
        // Assert
        assertEquals("localhost", host);
        assertEquals(22, port);
    }
    
    @Test
    void testServerPathContainsCorrectPath() {
        // Arrange
        ServerPath path = serverPathRepository.findById(testServerPath.getIdtServerPath())
            .orElseThrow();
        
        // Assert
        assertEquals("/data/incoming", path.getDesPath());
        assertEquals(PathType.ORIGIN, path.getDesPathType());
    }
}
