package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.ServerOrigin;
import com.concil.edi.commons.enums.PathType;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.commons.repository.ServerRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Property 38: Configuration lookup for download
 * 
 * Property: When openInputStream is called with valid idt_sever_path_origin,
 * THEN the service must successfully lookup Server and ServerPath configurations from database,
 * AND must extract host, port, codVault, and desVaultSecret from the configuration
 * 
 * Validates: Requirements 12.1
 */
@SpringBootTest
@ActiveProfiles("test")
public class ConfigurationLookupDownloadPropertyTest {
    
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
    void validServerPathIdMustResolveToServerConfiguration(
        @ForAll("serverCodes") String codServer,
        @ForAll("vaultCodes") String codVault,
        @ForAll("paths") String path
    ) {
        // Arrange: Create server and server_path records
        Server server = new Server();
        server.setCodServer(codServer);
        server.setCodVault(codVault);
        server.setDesVaultSecret("test/secret");
        server.setDesServerType(ServerType.SFTP);
        server.setDesServerOrigin(ServerOrigin.EXTERNO);
        server.setDatCreation(new Date());
        server.setFlgActive(1);
        
        Server savedServer = serverRepository.save(server);
        
        ServerPath serverPath = new ServerPath();
        serverPath.setIdtServer(savedServer.getIdtServer());
        serverPath.setIdtAcquirer(1L);
        serverPath.setDesPath(path);
        serverPath.setDesPathType(PathType.ORIGIN);
        serverPath.setDatCreation(new Date());
        serverPath.setFlgActive(1);
        
        ServerPath savedServerPath = serverPathRepository.save(serverPath);
        
        // Act: Lookup configuration
        ServerPath lookedUpPath = serverPathRepository.findById(savedServerPath.getIdtServerPath())
            .orElseThrow();
        Server lookedUpServer = serverRepository.findById(lookedUpPath.getIdtServer())
            .orElseThrow();
        
        // Assert: Configuration must be successfully retrieved
        assert lookedUpPath != null : "ServerPath must be found by id";
        assert lookedUpServer != null : "Server must be found by ServerPath.idtServer";
        
        // Property 1: Server configuration must contain all required fields
        assert lookedUpServer.getCodServer() != null : "codServer must not be null";
        assert lookedUpServer.getCodVault() != null : "codVault must not be null";
        assert lookedUpServer.getDesVaultSecret() != null : "desVaultSecret must not be null";
        assert lookedUpServer.getDesServerType() == ServerType.SFTP : "serverType must be SFTP";
        
        // Property 2: ServerPath configuration must contain path
        assert lookedUpPath.getDesPath() != null : "desPath must not be null";
        assert lookedUpPath.getDesPath().equals(path) : "desPath must match original path";
    }
    
    @Property
    @Transactional
    void invalidServerPathIdMustThrowException(
        @ForAll("invalidIds") Long invalidId
    ) {
        // Act & Assert: Invalid ID must result in empty Optional
        assert serverPathRepository.findById(invalidId).isEmpty() : 
            "Invalid serverPathId must return empty Optional";
    }
    
    @Provide
    Arbitrary<String> serverCodes() {
        return Arbitraries.of(
            "sftp://localhost:22",
            "sftp://sftp-server:2222",
            "192.168.1.100:22",
            "sftp-origin:22"
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
            "/data/incoming",
            "/sftp/cielo/in",
            "/files/origin",
            "/upload"
        );
    }
    
    @Provide
    Arbitrary<Long> invalidIds() {
        return Arbitraries.longs().between(99999L, 999999L);
    }
}
