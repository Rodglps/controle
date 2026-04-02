package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.FileType;
import com.concil.edi.producer.config.SftpConfig;
import com.concil.edi.producer.config.VaultConfig;
import com.concil.edi.producer.dto.CredentialsDTO;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SFTP Service preservation requirements.
 * These tests verify that SFTP operations behavior remains unchanged after the SessionFactory caching fix.
 * 
 * IMPORTANT: These tests run on UNFIXED code to establish baseline behavior.
 * They must PASS on unfixed code to confirm what behavior needs to be preserved.
 */
class SftpServicePreservationTest {
    
    private SftpConfig sftpConfig;
    private VaultConfig vaultConfig;
    private SftpService sftpService;
    
    @BeforeEach
    void setUp() {
        sftpConfig = mock(SftpConfig.class);
        vaultConfig = mock(VaultConfig.class);
        sftpService = new SftpService(sftpConfig, vaultConfig);
    }
    
    /**
     * Preservation Requirement 3.1: Session objects are closed in finally blocks after use
     */
    @Test
    void sessionsAreAlwaysClosedAfterOperations() {
        // Setup
        ServerConfigurationDTO config = createServerConfig();
        SessionFactory<SftpClient.DirEntry> sessionFactory = mock(SessionFactory.class);
        Session<SftpClient.DirEntry> session = mock(Session.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        when(sftpConfig.getOrCreateSessionFactory(anyString(), anyInt(), anyString(), anyString()))
            .thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        
        try {
            when(session.list(anyString())).thenReturn(new SftpClient.DirEntry[0]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Execute
        sftpService.listFiles(config);
        
        // Verify: Session.close() was called
        verify(session, times(1)).close();
    }
    
    /**
     * Preservation Requirement 3.1: Sessions are closed even when exceptions occur
     */
    @Test
    void sessionsAreClosedOnException() {
        // Setup
        ServerConfigurationDTO config = createServerConfig();
        SessionFactory<SftpClient.DirEntry> sessionFactory = mock(SessionFactory.class);
        Session<SftpClient.DirEntry> session = mock(Session.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        when(sftpConfig.getOrCreateSessionFactory(anyString(), anyInt(), anyString(), anyString()))
            .thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        
        try {
            when(session.list(anyString())).thenThrow(new RuntimeException("SFTP error"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Execute and expect exception
        assertThatThrownBy(() -> sftpService.listFiles(config))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to list files from SFTP");
        
        // Verify: Session.close() was called despite exception
        verify(session, times(1)).close();
    }
    
    /**
     * Preservation Requirement 3.2: File metadata results unchanged
     */
    @Test
    void fileListingReturnsConsistentMetadata() {
        // Setup
        ServerConfigurationDTO config = createServerConfig();
        SessionFactory<SftpClient.DirEntry> sessionFactory = mock(SessionFactory.class);
        Session<SftpClient.DirEntry> session = mock(Session.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        
        SftpClient.DirEntry[] files = createMockFiles();
        
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        when(sftpConfig.getOrCreateSessionFactory(anyString(), anyInt(), anyString(), anyString()))
            .thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        
        try {
            when(session.list(anyString())).thenReturn(files);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Execute twice with same configuration
        List<FileMetadataDTO> result1 = sftpService.listFiles(config);
        List<FileMetadataDTO> result2 = sftpService.listFiles(config);
        
        // Verify: Same configuration produces same results
        assertThat(result1).hasSize(result2.size());
        for (int i = 0; i < result1.size(); i++) {
            assertThat(result1.get(i).getFilename()).isEqualTo(result2.get(i).getFilename());
            assertThat(result1.get(i).getFileSize()).isEqualTo(result2.get(i).getFileSize());
            assertThat(result1.get(i).getFileType()).isEqualTo(result2.get(i).getFileType());
        }
    }
    
    /**
     * Preservation Requirement 3.3: Credentials retrieval unchanged
     */
    @Test
    void credentialsRetrievedWithCorrectParameters() {
        // Setup
        ServerConfigurationDTO config = createServerConfig();
        SessionFactory<SftpClient.DirEntry> sessionFactory = mock(SessionFactory.class);
        Session<SftpClient.DirEntry> session = mock(Session.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        
        // SftpService calls sftpConfig.getOrCreateSessionFactory(), which internally calls vaultConfig.getCredentials()
        // We need to verify that sftpConfig.getOrCreateSessionFactory() is called with correct parameters
        when(sftpConfig.getOrCreateSessionFactory(anyString(), anyInt(), eq(config.getCodVault()), eq(config.getDesVaultSecret())))
            .thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenReturn(session);
        when(session.isOpen()).thenReturn(true);
        
        try {
            when(session.list(anyString())).thenReturn(new SftpClient.DirEntry[0]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        // Execute
        sftpService.listFiles(config);
        
        // Verify: SftpConfig.getOrCreateSessionFactory() called with correct vault parameters
        // This indirectly verifies that credentials will be retrieved with correct parameters
        verify(sftpConfig, times(1)).getOrCreateSessionFactory(
            anyString(),  // host
            anyInt(),     // port
            eq(config.getCodVault()), 
            eq(config.getDesVaultSecret())
        );
    }
    
    /**
     * Preservation Requirement 3.6: Error handling unchanged
     */
    @Test
    void runtimeExceptionThrownForConnectionFailures() {
        // Setup
        ServerConfigurationDTO config = createServerConfig();
        SessionFactory<SftpClient.DirEntry> sessionFactory = mock(SessionFactory.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        when(sftpConfig.getOrCreateSessionFactory(anyString(), anyInt(), anyString(), anyString()))
            .thenReturn(sessionFactory);
        when(sessionFactory.getSession()).thenThrow(new RuntimeException("Connection failed"));
        
        // Execute and verify exception
        assertThatThrownBy(() -> sftpService.listFiles(config))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to list files from SFTP");
    }
    
    // Helper methods
    
    private ServerConfigurationDTO createServerConfig() {
        ServerConfigurationDTO config = new ServerConfigurationDTO();
        config.setCodServer("localhost:22");
        config.setOriginPath("/upload");
        config.setCodVault("TEST_VAULT");
        config.setDesVaultSecret("test/credentials");
        return config;
    }
    
    private SftpClient.DirEntry[] createMockFiles() {
        SftpClient.DirEntry entry1 = mock(SftpClient.DirEntry.class);
        SftpClient.Attributes attrs1 = mock(SftpClient.Attributes.class);
        
        when(entry1.getFilename()).thenReturn("file1.csv");
        when(entry1.getAttributes()).thenReturn(attrs1);
        when(attrs1.isDirectory()).thenReturn(false);
        when(attrs1.getSize()).thenReturn(1024L);
        when(attrs1.getModifyTime()).thenReturn(java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        
        return new SftpClient.DirEntry[]{entry1};
    }
}
