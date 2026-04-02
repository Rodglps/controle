package com.concil.edi.producer.config;

import com.concil.edi.producer.dto.CredentialsDTO;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Assertions;
import org.springframework.integration.file.remote.session.SessionFactory;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Property Test for SftpConfig SessionFactory leak.
 * 
 * **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists.
 * **DO NOT attempt to fix the test or the code when it fails.**
 * 
 * Property 1: Bug Condition - SessionFactory Instance Accumulation
 * 
 * GOAL: Surface counterexamples that demonstrate SessionFactory instances accumulate 
 * instead of being reused when calling getOrCreateSessionFactory() multiple times with 
 * identical server configuration (same host, port, codVault, vaultSecret).
 * 
 * EXPECTED OUTCOME: Test FAILS (this is correct - it proves the bug exists: 
 * multiple SessionFactory instances are created instead of one being reused).
 * 
 * Validates Requirements: 1.1, 1.2, 1.3, 1.4, 1.5
 */
class SftpConfigBugConditionPropertyTest {
    
    /**
     * Property 1: Bug Condition - SessionFactory Caching and Reuse
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
     * 
     * GIVEN a server configuration (host, port, codVault, vaultSecret)
     * WHEN calling getOrCreateSessionFactory() multiple times with identical parameters
     * THEN the system SHOULD return the same cached SessionFactory instance (expected behavior)
     * 
     * **EXPECTED ON UNFIXED CODE**: Test FAILS because multiple distinct SessionFactory 
     * instances are created instead of reusing one cached instance.
     * 
     * **EXPECTED AFTER FIX**: Test PASSES because SessionFactory instances are cached 
     * and reused per server configuration.
     */
    @Property
    @Label("Property 1: SessionFactory instances should be cached and reused per server configuration")
    void sessionFactoryCaching_MultipleCallsWithSameConfig_ShouldReuseSameInstance(
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String host,
        @ForAll @IntRange(min = 22, max = 2222) int port,
        @ForAll @StringLength(min = 5, max = 30) @AlphaChars String codVault,
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String vaultSecret
    ) {
        // Arrange
        VaultConfig vaultConfig = mock(VaultConfig.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        
        SftpConfig sftpConfig = new SftpConfig(vaultConfig);
        
        // Act - Call getOrCreateSessionFactory() multiple times with identical parameters
        SessionFactory<SftpClient.DirEntry> factory1 = 
            sftpConfig.getOrCreateSessionFactory(host, port, codVault, vaultSecret);
        SessionFactory<SftpClient.DirEntry> factory2 = 
            sftpConfig.getOrCreateSessionFactory(host, port, codVault, vaultSecret);
        SessionFactory<SftpClient.DirEntry> factory3 = 
            sftpConfig.getOrCreateSessionFactory(host, port, codVault, vaultSecret);
        
        // Assert - Expected behavior: Same SessionFactory instance should be returned
        // ON UNFIXED CODE: This assertion WILL FAIL because new instances are created each time
        // AFTER FIX: This assertion WILL PASS because instances are cached and reused
        Assertions.assertSame(factory1, factory2,
            String.format("SessionFactory instances should be reused for same config: %s:%d:%s:%s. " +
                "COUNTEREXAMPLE: factory1=%s, factory2=%s",
                host, port, codVault, vaultSecret,
                System.identityHashCode(factory1), System.identityHashCode(factory2)));
        
        Assertions.assertSame(factory2, factory3,
            String.format("SessionFactory instances should be reused for same config: %s:%d:%s:%s. " +
                "COUNTEREXAMPLE: factory2=%s, factory3=%s",
                host, port, codVault, vaultSecret,
                System.identityHashCode(factory2), System.identityHashCode(factory3)));
        
        Assertions.assertSame(factory1, factory3,
            String.format("SessionFactory instances should be reused for same config: %s:%d:%s:%s. " +
                "COUNTEREXAMPLE: factory1=%s, factory3=%s",
                host, port, codVault, vaultSecret,
                System.identityHashCode(factory1), System.identityHashCode(factory3)));
    }
    
    /**
     * Property 1.1: Different server configurations should create different SessionFactory instances
     * 
     * GIVEN two different server configurations
     * WHEN calling getOrCreateSessionFactory() for each configuration
     * THEN the system SHOULD return different SessionFactory instances
     * 
     * This validates that caching is scoped correctly per server configuration.
     */
    @Property
    @Label("Property 1.1: Different server configurations should create different SessionFactory instances")
    void sessionFactoryCaching_DifferentConfigs_ShouldCreateDifferentInstances(
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String host1,
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String host2,
        @ForAll @IntRange(min = 22, max = 2222) int port,
        @ForAll @StringLength(min = 5, max = 30) @AlphaChars String codVault,
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String vaultSecret
    ) {
        Assume.that(!host1.equals(host2));
        
        // Arrange
        VaultConfig vaultConfig = mock(VaultConfig.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        
        SftpConfig sftpConfig = new SftpConfig(vaultConfig);
        
        // Act - Call getOrCreateSessionFactory() with different host configurations
        SessionFactory<SftpClient.DirEntry> factory1 = 
            sftpConfig.getOrCreateSessionFactory(host1, port, codVault, vaultSecret);
        SessionFactory<SftpClient.DirEntry> factory2 = 
            sftpConfig.getOrCreateSessionFactory(host2, port, codVault, vaultSecret);
        
        // Assert - Different configurations should produce different SessionFactory instances
        Assertions.assertNotSame(factory1, factory2,
            String.format("Different server configurations should create different SessionFactory instances. " +
                "Config1: %s:%d, Config2: %s:%d",
                host1, port, host2, port));
    }
    
    /**
     * Property 1.2: SessionFactory instance accumulation over many calls
     * 
     * GIVEN a server configuration
     * WHEN calling getOrCreateSessionFactory() N times with identical parameters
     * THEN the system SHOULD return the same SessionFactory instance N times (expected behavior)
     * 
     * **EXPECTED ON UNFIXED CODE**: Test FAILS because N distinct SessionFactory instances 
     * are created, demonstrating resource leak.
     * 
     * This property uses a fixed number of calls (10) to demonstrate the accumulation pattern.
     */
    @Property(tries = 50)
    @Label("Property 1.2: SessionFactory instances should not accumulate over multiple calls")
    void sessionFactoryCaching_ManyCallsSameConfig_ShouldNotAccumulate(
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String host,
        @ForAll @IntRange(min = 22, max = 2222) int port,
        @ForAll @StringLength(min = 5, max = 30) @AlphaChars String codVault,
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String vaultSecret
    ) {
        // Arrange
        VaultConfig vaultConfig = mock(VaultConfig.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        
        SftpConfig sftpConfig = new SftpConfig(vaultConfig);
        
        // Act - Call getOrCreateSessionFactory() 10 times with identical parameters
        SessionFactory<SftpClient.DirEntry> firstFactory = 
            sftpConfig.getOrCreateSessionFactory(host, port, codVault, vaultSecret);
        
        // Verify all subsequent calls return the same instance
        for (int i = 1; i < 10; i++) {
            SessionFactory<SftpClient.DirEntry> factory = 
                sftpConfig.getOrCreateSessionFactory(host, port, codVault, vaultSecret);
            
            // Assert - Expected behavior: Same instance should be returned
            // ON UNFIXED CODE: This will FAIL, demonstrating that 10 distinct instances are created
            Assertions.assertSame(firstFactory, factory,
                String.format("Call #%d should return the same SessionFactory instance. " +
                    "COUNTEREXAMPLE: 10 calls to getOrCreateSessionFactory('%s', %d, '%s', '%s') " +
                    "created %d distinct SessionFactory instances instead of reusing 1. " +
                    "firstFactory=%s, currentFactory=%s",
                    i + 1, host, port, codVault, vaultSecret, i + 1,
                    System.identityHashCode(firstFactory), System.identityHashCode(factory)));
        }
    }
    
    /**
     * Property 1.3: Cache key uniqueness - different ports should create different instances
     * 
     * GIVEN two configurations with same host but different ports
     * WHEN calling getOrCreateSessionFactory() for each configuration
     * THEN the system SHOULD return different SessionFactory instances
     */
    @Property
    @Label("Property 1.3: Different ports should create different SessionFactory instances")
    void sessionFactoryCaching_DifferentPorts_ShouldCreateDifferentInstances(
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String host,
        @ForAll @IntRange(min = 22, max = 1000) int port1,
        @ForAll @IntRange(min = 1001, max = 2222) int port2,
        @ForAll @StringLength(min = 5, max = 30) @AlphaChars String codVault,
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String vaultSecret
    ) {
        // Arrange
        VaultConfig vaultConfig = mock(VaultConfig.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        
        SftpConfig sftpConfig = new SftpConfig(vaultConfig);
        
        // Act - Call getOrCreateSessionFactory() with different port configurations
        SessionFactory<SftpClient.DirEntry> factory1 = 
            sftpConfig.getOrCreateSessionFactory(host, port1, codVault, vaultSecret);
        SessionFactory<SftpClient.DirEntry> factory2 = 
            sftpConfig.getOrCreateSessionFactory(host, port2, codVault, vaultSecret);
        
        // Assert - Different ports should produce different SessionFactory instances
        Assertions.assertNotSame(factory1, factory2,
            String.format("Different ports should create different SessionFactory instances. " +
                "Config1: %s:%d, Config2: %s:%d",
                host, port1, host, port2));
    }
    
    /**
     * Property 1.4: Cache key uniqueness - different vault configurations should create different instances
     * 
     * GIVEN two configurations with same host/port but different vault settings
     * WHEN calling getOrCreateSessionFactory() for each configuration
     * THEN the system SHOULD return different SessionFactory instances
     */
    @Property
    @Label("Property 1.4: Different vault configurations should create different SessionFactory instances")
    void sessionFactoryCaching_DifferentVaultConfig_ShouldCreateDifferentInstances(
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String host,
        @ForAll @IntRange(min = 22, max = 2222) int port,
        @ForAll @StringLength(min = 5, max = 30) @AlphaChars String codVault1,
        @ForAll @StringLength(min = 5, max = 30) @AlphaChars String codVault2,
        @ForAll @StringLength(min = 5, max = 50) @AlphaChars String vaultSecret
    ) {
        Assume.that(!codVault1.equals(codVault2));
        
        // Arrange
        VaultConfig vaultConfig = mock(VaultConfig.class);
        CredentialsDTO credentials = new CredentialsDTO("testuser", "testpass");
        when(vaultConfig.getCredentials(anyString(), anyString())).thenReturn(credentials);
        
        SftpConfig sftpConfig = new SftpConfig(vaultConfig);
        
        // Act - Call getOrCreateSessionFactory() with different vault configurations
        SessionFactory<SftpClient.DirEntry> factory1 = 
            sftpConfig.getOrCreateSessionFactory(host, port, codVault1, vaultSecret);
        SessionFactory<SftpClient.DirEntry> factory2 = 
            sftpConfig.getOrCreateSessionFactory(host, port, codVault2, vaultSecret);
        
        // Assert - Different vault configs should produce different SessionFactory instances
        Assertions.assertNotSame(factory1, factory2,
            String.format("Different vault configurations should create different SessionFactory instances. " +
                "Config1: codVault=%s, Config2: codVault=%s",
                codVault1, codVault2));
    }
}
