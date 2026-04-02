package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.ServerOrigin;
import com.concil.edi.commons.enums.PathType;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.commons.repository.ServerRepository;
import com.concil.edi.consumer.dto.ServerConfigurationDTO;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Property 41: Configuration lookup for upload
 * 
 * Property: When getServerConfiguration is called with valid idt_sever_path_destination,
 * THEN the service must successfully lookup Server and ServerPath configurations from database,
 * AND must return ServerConfigurationDTO with all required fields populated
 * 
 * Validates: Requirements 13.1
 */
@SpringBootTest
@ActiveProfiles("test")
public class ConfigurationLookupUploadPropertyTest {
    
    @Autowired
    private FileUploadService fileUploadService;
    
    @Autowired
    private ServerRepository serverRepository;
    
    @Autowired
    private ServerPathRepository serverPathRepository;
    
    @BeforeEach
    @Transactional
    public void cleanup() {
        serverPathRepository.deleteAll();
        serverRepository.deleteAll();
    }
    
    @Property
    @Transactional
    void validServerPathIdMustResolveToCompleteConfiguration(
        @ForAll("serverCodes") String codServer,
        @ForAll("vaultCodes") String codVault,
        @ForAll("paths") String path,
        @ForAll("serverTypes") ServerType serverType
    ) {
        // Arrange: Create server and server_path records
        Server server = new Server();
        server.setCodServer(codServer);
        server.setCodVault(codVault);
        server.setDesVaultSecret("test/secret");
        server.setDesServerType(serverType);
        server.setDesServerOrigin(ServerOrigin.INTERNO);
        server.setDatCreation(new Date());
        server.setFlgActive(1);
        
        Server savedServer = serverRepository.save(server);
        
        ServerPath serverPath = new ServerPath();
        serverPath.setIdtServer(savedServer.getIdtServer());
        serverPath.setIdtAcquirer(1L);
        serverPath.setDesPath(path);
        serverPath.setDesPathType(PathType.DESTINATION);
        serverPath.setDatCreation(new Date());
        serverPath.setFlgActive(1);
        
        ServerPath savedServerPath = serverPathRepository.save(serverPath);
        
        // Act: Get server configuration
        ServerConfigurationDTO config = fileUploadService.getServerConfiguration(
            savedServerPath.getIdtServerPath()
        );
        
        // Assert: All required fields must be populated
        assert config != null : "Configuration must not be null";
        
        // Property 1: Server identification fields
        assert config.getServerId() != null : "serverId must be populated";
        assert config.getCodServer() != null : "codServer must be populated";
        assert config.getCodVault() != null : "codVault must be populated";
        assert config.getDesVaultSecret() != null : "desVaultSecret must be populated";
        assert config.getServerType() != null : "serverType must be populated";
        
        // Property 2: Connection fields
        assert config.getHost() != null : "host must be populated";
        assert config.getPort() != null : "port must be populated";
        assert config.getPort() > 0 : "port must be positive";
        
        // Property 3: Path fields
        assert config.getServerPathId() != null : "serverPathId must be populated";
        assert config.getPath() != null : "path must be populated";
        assert config.getPath().equals(path) : "path must match original";
        
        // Property 4: Acquirer field
        assert config.getAcquirerId() != null : "acquirerId must be populated";
    }
    
    @Property
    @Transactional
    void invalidServerPathIdMustThrowException(
        @ForAll("invalidIds") Long invalidId
    ) {
        // Act & Assert: Invalid ID must throw IllegalArgumentException
        try {
            fileUploadService.getServerConfiguration(invalidId);
            assert false : "Should have thrown IllegalArgumentException";
        } catch (IllegalArgumentException e) {
            // Expected exception
            assert e.getMessage().contains("ServerPath not found") : 
                "Exception message must indicate ServerPath not found";
        }
    }
    
    @Provide
    Arbitrary<String> serverCodes() {
        return Arbitraries.of(
            "s3://bucket-name",
            "sftp://sftp-dest:22",
            "192.168.1.200:22",
            "sftp-internal:2222"
        );
    }
    
    @Provide
    Arbitrary<String> vaultCodes() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-')
            .ofMinLength(5)
            .ofMaxLength(30);
    }
    
    @Provide
    Arbitrary<String> paths() {
        return Arbitraries.of(
            "/data/destination",
            "/sftp/cielo/out",
            "/files/processed",
            "/output"
        );
    }
    
    @Provide
    Arbitrary<ServerType> serverTypes() {
        return Arbitraries.of(ServerType.S3, ServerType.SFTP);
    }
    
    @Provide
    Arbitrary<Long> invalidIds() {
        return Arbitraries.longs().between(99999L, 999999L);
    }
}
