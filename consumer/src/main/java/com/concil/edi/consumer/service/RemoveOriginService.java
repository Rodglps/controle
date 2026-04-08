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

/**
 * Service responsible for removing files from the origin SFTP server.
 * Called after a successful transfer and integrity validation.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoveOriginService {

    private final ServerPathRepository serverPathRepository;
    private final SftpConfig sftpConfig;

    /**
     * Removes a file from the origin SFTP server.
     * Propagates any exception so the caller can handle error status updates.
     *
     * @param serverPathOriginId ID of the origin server path (sever_paths table)
     * @param filename           Name of the file to remove
     * @throws IllegalArgumentException if serverPathOriginId is not found
     * @throws RuntimeException         if the SFTP remove operation fails
     */
    public void removeFile(Long serverPathOriginId, String filename) {
        log.info("Removing file: {} from serverPathId: {}", filename, serverPathOriginId);

        // Requirement 5.2: receive serverPathOriginId and filename as parameters
        ServerPath serverPath = serverPathRepository.findWithServerByIdtSeverPath(serverPathOriginId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ServerPath not found for id: " + serverPathOriginId));

        Server server = serverPath.getServer();

        // Requirement 5.3: use existing SFTP connection
        SessionFactory<org.apache.sshd.sftp.client.SftpClient.DirEntry> sessionFactory =
                sftpConfig.getOrCreateSessionFactory(server.getCodVault(), server.getDesVaultSecret());

        Session<org.apache.sshd.sftp.client.SftpClient.DirEntry> session = sessionFactory.getSession();
        try {
            String remotePath = serverPath.getDesPath() + "/" + filename;
            log.debug("Removing remote file: {}", remotePath);
            try {
                session.remove(remotePath);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to remove file via SFTP: " + e.getMessage(), e);
            }
            log.info("Successfully removed file: {} from {}", filename, remotePath);
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
        // Requirement 5.4: exceptions propagate to caller — no catch block here
    }
}
