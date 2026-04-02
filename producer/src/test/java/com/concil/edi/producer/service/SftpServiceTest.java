package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.producer.config.SftpConfig;
import com.concil.edi.producer.config.VaultConfig;
import com.concil.edi.producer.dto.CredentialsDTO;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SftpService.
 * Tests file type validation and credential retrieval.
 */
@ExtendWith(MockitoExtension.class)
class SftpServiceTest {
    
    @Mock
    private SftpConfig sftpConfig;
    
    @Mock
    private VaultConfig vaultConfig;
    
    private SftpService sftpService;
    
    @BeforeEach
    void setUp() {
        sftpService = new SftpService(sftpConfig, vaultConfig);
    }
    
    /**
     * Test: getCredentials should return credentials from VaultConfig
     */
    @Test
    void testGetCredentials_Success() {
        // Arrange
        String codVault = "test_vault";
        String vaultSecret = "test/secret";
        CredentialsDTO expectedCredentials = new CredentialsDTO("testuser", "testpass");
        
        when(vaultConfig.getCredentials(codVault, vaultSecret)).thenReturn(expectedCredentials);
        
        // Act
        CredentialsDTO result = sftpService.getCredentials(codVault, vaultSecret);
        
        // Assert
        assertNotNull(result);
        assertEquals("testuser", result.getUser());
        assertEquals("testpass", result.getPassword());
    }
    
    /**
     * Test: getCredentials should throw exception when VaultConfig fails
     */
    @Test
    void testGetCredentials_ThrowsException() {
        // Arrange
        String codVault = "invalid_vault";
        String vaultSecret = "invalid/secret";
        
        when(vaultConfig.getCredentials(codVault, vaultSecret))
            .thenThrow(new IllegalArgumentException("Environment variable not found"));
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            sftpService.getCredentials(codVault, vaultSecret);
        });
    }
    
    /**
     * Note: Testing listFiles with Spring Integration SFTP requires actual SFTP connection 
     * or complex mocking of SessionFactory and Session.
     * For MVP, we focus on integration tests with TestContainers for SFTP functionality.
     * The file type validation logic is tested through the integration tests.
     */
}
