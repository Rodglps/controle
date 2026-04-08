package com.concil.edi.consumer.listener;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.TransactionType;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.FileOriginClientsRepository;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.consumer.dto.ServerConfigurationDTO;
import com.concil.edi.consumer.service.CustomerIdentificationService;
import com.concil.edi.consumer.service.FileDownloadService;
import com.concil.edi.consumer.service.FileUploadService;
import com.concil.edi.consumer.service.LayoutIdentificationService;
import com.concil.edi.consumer.service.RemoveOriginService;
import com.concil.edi.consumer.service.StatusUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for FileTransferListener.
 * 
 * Requirements: 19.6
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileTransferListener Unit Tests")
class FileTransferListenerTest {

    @Mock
    private FileDownloadService fileDownloadService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private StatusUpdateService statusUpdateService;

    @Mock
    private FileOriginRepository fileOriginRepository;

    @Mock
    private ServerPathRepository serverPathRepository;

    @Mock
    private LayoutIdentificationService layoutIdentificationService;

    @Mock
    private CustomerIdentificationService customerIdentificationService;

    @Mock
    private FileOriginClientsRepository fileOriginClientsRepository;

    @Mock
    private RemoveOriginService removeOriginService;

    @InjectMocks
    private FileTransferListener listener;

    private FileTransferMessageDTO validMessage;
    private FileOrigin fileOrigin;
    private ServerPath serverPath;
    private Server server;

    @BeforeEach
    void setUp() {
        validMessage = new FileTransferMessageDTO(
            1L,
            "test-file.csv",
            10L,
            20L,
            1024L
        );

        fileOrigin = createFileOrigin(1L, "test-file.csv", 0, 5);
        server = createServer(1L, "S3-BUCKET", ServerType.S3);
        serverPath = createServerPath(20L, server, "S3-BUCKET/destination/path");
        
        // Mock layout identification to return layout ID 1 (lenient to avoid unnecessary stubbing errors)
        lenient().when(layoutIdentificationService.identifyLayout(any(InputStream.class), anyString(), anyLong()))
            .thenReturn(1L);
        
        // Mock customer identification to return empty list (lenient to avoid unnecessary stubbing errors)
        lenient().when(customerIdentificationService.identifyCustomers(any(byte[].class), anyString(), anyLong(), anyLong()))
            .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Should process valid message successfully for S3 destination")
    void shouldProcessValidMessageSuccessfullyForS3() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin)); // executeRemoval fetch
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenReturn(inputStream);
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(serverPath));
        when(fileUploadService.getS3ObjectSize(eq("S3-BUCKET"), eq("destination/path/test-file.csv"))).thenReturn(1024L);

        // Act
        listener.handleFileTransfer(validMessage);

        // Assert
        verify(statusUpdateService).updateStatus(1L, Status.PROCESSAMENTO);
        verify(layoutIdentificationService).identifyLayout(any(InputStream.class), eq("test-file.csv"), eq(1L));
        verify(statusUpdateService).updateLayoutId(1L, 1L);
        verify(customerIdentificationService).identifyCustomers(any(byte[].class), eq("test-file.csv"), eq(1L), eq(1L));
        verify(fileDownloadService, times(2)).openInputStream(10L, "test-file.csv");
        verify(fileUploadService).uploadToS3(any(InputStream.class), eq("S3-BUCKET"), eq("destination/path/test-file.csv"), eq(1024L));
        verify(fileUploadService).getS3ObjectSize("S3-BUCKET", "destination/path/test-file.csv");
        verify(removeOriginService).removeFile(10L, "test-file.csv");
        verify(statusUpdateService).updateStatus(1L, Status.CONCLUIDO);
    }

    @Test
    @DisplayName("Should process valid message successfully for SFTP destination")
    void shouldProcessValidMessageSuccessfullyForSftp() {
        // Arrange
        Server sftpServer = createServer(2L, "sftp-destination", ServerType.SFTP);
        ServerPath sftpPath = createServerPath(20L, sftpServer, "/destination/path");
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin)); // executeRemoval fetch
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenReturn(inputStream);
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(sftpPath));
        when(fileUploadService.getSftpFileSize(any(ServerConfigurationDTO.class), eq("/destination/path/test-file.csv"))).thenReturn(1024L);

        // Act
        listener.handleFileTransfer(validMessage);

        // Assert
        verify(statusUpdateService).updateStatus(1L, Status.PROCESSAMENTO);
        verify(layoutIdentificationService).identifyLayout(any(InputStream.class), eq("test-file.csv"), eq(1L));
        verify(statusUpdateService).updateLayoutId(1L, 1L);
        verify(customerIdentificationService).identifyCustomers(any(byte[].class), eq("test-file.csv"), eq(1L), eq(1L));
        verify(fileDownloadService, times(2)).openInputStream(10L, "test-file.csv");
        verify(fileUploadService).uploadToSftp(any(InputStream.class), any(ServerConfigurationDTO.class), eq("/destination/path/test-file.csv"));
        verify(fileUploadService).getSftpFileSize(any(ServerConfigurationDTO.class), eq("/destination/path/test-file.csv"));
        verify(removeOriginService).removeFile(10L, "test-file.csv");
        verify(statusUpdateService).updateStatus(1L, Status.CONCLUIDO);
    }

    @Test
    @DisplayName("Should handle invalid message structure gracefully")
    void shouldHandleInvalidMessageStructure() {
        // Arrange
        FileTransferMessageDTO invalidMessage = new FileTransferMessageDTO(null, null, null, null, null);
        when(fileOriginRepository.findById(any())).thenReturn(Optional.empty());

        // Act
        listener.handleFileTransfer(invalidMessage);

        // Assert - should not throw exception, error handling should catch it
        verify(statusUpdateService, never()).updateStatus(any(), eq(Status.CONCLUIDO));
    }

    @Test
    @DisplayName("Should update database after exception during download")
    void shouldUpdateDatabaseAfterExceptionDuringDownload() {
        // Arrange
        RuntimeException downloadException = new RuntimeException("SFTP connection failed");
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenThrow(downloadException);
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin))  // handleError first fetch
            .thenReturn(Optional.of(fileOrigin)); // handleError second fetch

        // Act & Assert
        assertThatThrownBy(() -> listener.handleFileTransfer(validMessage))
            .isInstanceOf(ListenerExecutionFailedException.class);

        verify(statusUpdateService).updateStatus(1L, Status.PROCESSAMENTO);
        verify(statusUpdateService).updateStatusWithError(1L, Status.ERRO, "SFTP connection failed");
        verify(statusUpdateService).incrementRetry(1L);
    }

    @Test
    @DisplayName("Should update database after exception during upload")
    void shouldUpdateDatabaseAfterExceptionDuringUpload() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        RuntimeException uploadException = new RuntimeException("S3 upload failed");
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenReturn(inputStream);
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(serverPath));
        doThrow(uploadException).when(fileUploadService).uploadToS3(any(), any(), any(), anyLong());
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin))  // handleError first fetch
            .thenReturn(Optional.of(fileOrigin)); // handleError second fetch

        // Act & Assert
        assertThatThrownBy(() -> listener.handleFileTransfer(validMessage))
            .isInstanceOf(ListenerExecutionFailedException.class);

        verify(statusUpdateService).updateStatusWithError(1L, Status.ERRO, "S3 upload failed");
        verify(statusUpdateService).incrementRetry(1L);
    }

    @Test
    @DisplayName("Should NACK message when retry limit not reached")
    void shouldNackMessageWhenRetryLimitNotReached() {
        // Arrange
        fileOrigin.setNumRetry(2); // Below max_retry of 5
        RuntimeException exception = new RuntimeException("Transfer failed");
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenThrow(exception);
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin))  // handleError first fetch
            .thenReturn(Optional.of(fileOrigin)); // handleError second fetch

        // Act & Assert
        assertThatThrownBy(() -> listener.handleFileTransfer(validMessage))
            .isInstanceOf(ListenerExecutionFailedException.class)
            .hasMessageContaining("Retry limit not reached");

        verify(statusUpdateService).incrementRetry(1L);
    }

    @Test
    @DisplayName("Should ACK message when max retry is reached")
    void shouldAckMessageWhenMaxRetryReached() {
        // Arrange
        fileOrigin.setNumRetry(5); // At max_retry
        RuntimeException exception = new RuntimeException("Transfer failed");
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenThrow(exception);
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin))  // handleError first fetch
            .thenReturn(Optional.of(fileOrigin)); // handleError second fetch

        // Act - should not throw exception (ACK)
        listener.handleFileTransfer(validMessage);

        // Assert
        verify(statusUpdateService).updateStatusWithError(1L, Status.ERRO, "Transfer failed");
        verify(statusUpdateService).incrementRetry(1L);
        // No exception thrown = message will be ACKed
    }

    @Test
    @DisplayName("Should handle missing destination configuration")
    void shouldHandleMissingDestinationConfiguration() {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenReturn(inputStream);
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.empty());
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin))  // handleError first fetch
            .thenReturn(Optional.of(fileOrigin)); // handleError second fetch

        // Act & Assert
        assertThatThrownBy(() -> listener.handleFileTransfer(validMessage))
            .isInstanceOf(ListenerExecutionFailedException.class);

        verify(statusUpdateService).updateStatusWithError(eq(1L), eq(Status.ERRO), contains("ServerPath not found"));
    }

    @Test
    @DisplayName("Should handle unsupported destination server type")
    void shouldHandleUnsupportedDestinationServerType() {
        // Arrange
        server.setDesServerType(ServerType.NFS); // Unsupported type
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        ServerPath originPath = createServerPath(10L, server, "/origin/path");
        when(fileDownloadService.openInputStream(10L, "test-file.csv")).thenReturn(inputStream);
        when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(serverPath));
        when(fileOriginRepository.findById(1L))
            .thenReturn(Optional.of(fileOrigin))  // isPendingRemoval check
            .thenReturn(Optional.of(fileOrigin))  // handleError first fetch
            .thenReturn(Optional.of(fileOrigin)); // handleError second fetch

        // Act & Assert
        assertThatThrownBy(() -> listener.handleFileTransfer(validMessage))
            .isInstanceOf(ListenerExecutionFailedException.class);

        verify(statusUpdateService).updateStatusWithError(eq(1L), eq(Status.ERRO), contains("Unsupported destination server type"));
    }

    // Helper methods

    private FileOrigin createFileOrigin(Long id, String filename, int numRetry, int maxRetry) {
        FileOrigin fo = new FileOrigin();
        fo.setIdtFileOrigin(id);
        fo.setIdtAcquirer(1L);
        fo.setIdtLayout(1L);
        fo.setDesFileName(filename);
        fo.setNumFileSize(1024L);
        fo.setDesFileType(FileType.CSV);
        fo.setDesStep(Step.COLETA);
        fo.setDesStatus(Status.EM_ESPERA);
        fo.setDesTransactionType(TransactionType.COMPLETO);
        fo.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        fo.setIdtSeverPathsInOut(1L);
        fo.setDatCreation(new Date());
        fo.setFlgActive(1);
        fo.setNumRetry(numRetry);
        fo.setMaxRetry(maxRetry);
        return fo;
    }

    private Server createServer(Long id, String codServer, ServerType serverType) {
        Server s = new Server();
        s.setIdtServer(id);
        s.setCodServer(codServer);
        s.setCodVault("vault-key");
        s.setDesVaultSecret("vault-secret");
        s.setDesServerType(serverType);
        s.setFlgActive(1);
        return s;
    }

    private ServerPath createServerPath(Long id, Server server, String path) {
        ServerPath sp = new ServerPath();
        sp.setIdtSeverPath(id);
        sp.setServer(server);
        sp.setDesPath(path);
        sp.setIdtAcquirer(1L);
        sp.setFlgActive(1);
        return sp;
    }
}
