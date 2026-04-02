package com.concil.edi.commons.entity;

import com.concil.edi.commons.enums.*;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JPA entity mappings and basic functionality.
 */
class EntityMappingTest {

    @Test
    void testServerEntityCreation() {
        Server server = new Server();
        server.setCodServer("SFTP_CIELO_ORIGIN");
        server.setCodVault("SFTP_CIELO_VAULT");
        server.setDesVaultSecret("/sftp_cielo");
        server.setDesServerType(ServerType.SFTP);
        server.setDesServerOrigin(ServerOrigin.EXTERNO);
        server.setDatCreation(new Date());
        server.setFlgActive(1);

        assertNotNull(server);
        assertEquals("SFTP_CIELO_ORIGIN", server.getCodServer());
        assertEquals(ServerType.SFTP, server.getDesServerType());
        assertEquals(ServerOrigin.EXTERNO, server.getDesServerOrigin());
        assertEquals(1, server.getFlgActive());
    }

    @Test
    void testServerPathEntityCreation() {
        Server server = new Server();
        server.setIdtServer(1L);

        ServerPath serverPath = new ServerPath();
        serverPath.setServer(server);
        serverPath.setIdtAcquirer(1L);
        serverPath.setDesPath("/upload/cielo");
        serverPath.setDesPathType(PathType.ORIGIN);
        serverPath.setDatCreation(new Date());
        serverPath.setFlgActive(1);

        assertNotNull(serverPath);
        assertEquals("/upload/cielo", serverPath.getDesPath());
        assertEquals(PathType.ORIGIN, serverPath.getDesPathType());
        assertEquals(1L, serverPath.getIdtAcquirer());
    }

    @Test
    void testServerPathInOutEntityCreation() {
        ServerPath origin = new ServerPath();
        origin.setIdtSeverPath(1L);

        ServerPath destination = new ServerPath();
        destination.setIdtSeverPath(2L);

        ServerPathInOut mapping = new ServerPathInOut();
        mapping.setSeverPathOrigin(origin);
        mapping.setSeverPathDestination(destination);
        mapping.setDesLinkType(LinkType.PRINCIPAL);
        mapping.setDatCreation(new Date());
        mapping.setFlgActive(1);

        assertNotNull(mapping);
        assertEquals(LinkType.PRINCIPAL, mapping.getDesLinkType());
        assertEquals(1L, mapping.getSeverPathOrigin().getIdtSeverPath());
        assertEquals(2L, mapping.getSeverPathDestination().getIdtSeverPath());
    }

    @Test
    void testFileOriginEntityCreation() {
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtAcquirer(1L);
        fileOrigin.setIdtLayout(1L);
        fileOrigin.setDesFileName("test-file.csv");
        fileOrigin.setNumFileSize(1024L);
        fileOrigin.setDesFileMimeType("text/csv");
        fileOrigin.setDesFileType(FileType.csv);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.EM_ESPERA);
        fileOrigin.setDesTransactionType(TransactionType.COMPLETO);
        fileOrigin.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        fileOrigin.setIdtSeverPathsInOut(1L);
        fileOrigin.setDatCreation(new Date());
        fileOrigin.setFlgActive(1);
        fileOrigin.setNumRetry(0);
        fileOrigin.setMaxRetry(5);

        assertNotNull(fileOrigin);
        assertEquals("test-file.csv", fileOrigin.getDesFileName());
        assertEquals(FileType.csv, fileOrigin.getDesFileType());
        assertEquals(Step.COLETA, fileOrigin.getDesStep());
        assertEquals(Status.EM_ESPERA, fileOrigin.getDesStatus());
        assertEquals(0, fileOrigin.getNumRetry());
        assertEquals(5, fileOrigin.getMaxRetry());
    }

    @Test
    void testServerTypeEnumValues() {
        assertEquals("S3", ServerType.S3.getValue());
        assertEquals("Blob-Storage", ServerType.BLOB_STORAGE.getValue());
        assertEquals("Object Storage", ServerType.OBJECT_STORAGE.getValue());
        assertEquals("SFTP", ServerType.SFTP.getValue());
        assertEquals("NFS", ServerType.NFS.getValue());
    }

    @Test
    void testServerTypeFromValue() {
        assertEquals(ServerType.S3, ServerType.fromValue("S3"));
        assertEquals(ServerType.BLOB_STORAGE, ServerType.fromValue("Blob-Storage"));
        assertEquals(ServerType.OBJECT_STORAGE, ServerType.fromValue("Object Storage"));
        assertEquals(ServerType.SFTP, ServerType.fromValue("SFTP"));
        assertEquals(ServerType.NFS, ServerType.fromValue("NFS"));
    }

    @Test
    void testServerTypeFromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            ServerType.fromValue("INVALID");
        });
    }

    @Test
    void testAllEnumValues() {
        // ServerOrigin
        assertNotNull(ServerOrigin.INTERNO);
        assertNotNull(ServerOrigin.EXTERNO);

        // PathType
        assertNotNull(PathType.ORIGIN);
        assertNotNull(PathType.DESTINATION);

        // LinkType
        assertNotNull(LinkType.PRINCIPAL);
        assertNotNull(LinkType.SECUNDARIO);

        // Step
        assertNotNull(Step.COLETA);
        assertNotNull(Step.DELETE);
        assertNotNull(Step.RAW);
        assertNotNull(Step.STAGING);
        assertNotNull(Step.ORDINATION);
        assertNotNull(Step.PROCESSING);
        assertNotNull(Step.PROCESSED);

        // Status
        assertNotNull(Status.EM_ESPERA);
        assertNotNull(Status.PROCESSAMENTO);
        assertNotNull(Status.CONCLUIDO);
        assertNotNull(Status.ERRO);

        // FileType
        assertNotNull(FileType.csv);
        assertNotNull(FileType.json);
        assertNotNull(FileType.txt);
        assertNotNull(FileType.xml);

        // TransactionType
        assertNotNull(TransactionType.COMPLETO);
        assertNotNull(TransactionType.CAPTURA);
        assertNotNull(TransactionType.FINANCEIRO);
    }
}
