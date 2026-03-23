package com.controle.arquivos.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para StreamingTransferService.
 * 
 * **Valida: Requisitos 10.2, 10.3, 10.5**
 */
@ExtendWith(MockitoExtension.class)
class StreamingTransferServiceTest {

    @Mock
    private S3Client s3Client;

    private StreamingTransferService service;

    @BeforeEach
    void setUp() {
        service = new StreamingTransferService(s3Client);
    }

    // Helper method to create test data
    private byte[] createTestData(int sizeInBytes) {
        byte[] data = new byte[sizeInBytes];
        for (int i = 0; i < sizeInBytes; i++) {
            data[i] = (byte) (i % 256);
        }
        return data;
    }

    // ========== Tests for transferirSFTPparaS3 ==========

    /**
     * Testa transferência SFTP para S3 com arquivo pequeno (menor que 5MB).
     * Deve usar multipart upload com uma única parte.
     */
    @Test
    void transferirSFTPparaS3_deveTransferirArquivoPequeno() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024 * 1024); // 1MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "test-file.txt";
        long tamanho = testData.length;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag-1")
                .build();

        CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(completeResponse);

        // Act
        service.transferirSFTPparaS3(source, bucket, key, tamanho);

        // Assert
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, times(1)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(s3Client, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    /**
     * Testa transferência SFTP para S3 com arquivo grande (maior que 5MB).
     * Deve usar multipart upload com múltiplas partes de 5MB.
     */
    @Test
    void transferirSFTPparaS3_deveTransferirArquivoGrandeEmMultiplasPartes() throws IOException {
        // Arrange
        byte[] testData = createTestData(12 * 1024 * 1024); // 12MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "large-file.txt";
        long tamanho = testData.length;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse1 = UploadPartResponse.builder()
                .eTag("test-etag-1")
                .build();
        UploadPartResponse uploadPartResponse2 = UploadPartResponse.builder()
                .eTag("test-etag-2")
                .build();
        UploadPartResponse uploadPartResponse3 = UploadPartResponse.builder()
                .eTag("test-etag-3")
                .build();

        CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse1, uploadPartResponse2, uploadPartResponse3);
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(completeResponse);

        // Act
        service.transferirSFTPparaS3(source, bucket, key, tamanho);

        // Assert
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, times(3)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(s3Client, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    /**
     * Testa que o multipart upload é completado com as partes corretas.
     */
    @Test
    void transferirSFTPparaS3_deveCompletarMultipartComPartesCorretas() throws IOException {
        // Arrange
        byte[] testData = createTestData(2 * 1024 * 1024); // 2MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "test-file.txt";
        long tamanho = testData.length;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag-1")
                .build();

        CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(completeResponse);

        // Act
        service.transferirSFTPparaS3(source, bucket, key, tamanho);

        // Assert
        ArgumentCaptor<CompleteMultipartUploadRequest> captor = 
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(s3Client).completeMultipartUpload(captor.capture());

        CompleteMultipartUploadRequest request = captor.getValue();
        assertEquals(bucket, request.bucket());
        assertEquals(key, request.key());
        assertEquals("test-upload-id", request.uploadId());
        
        List<CompletedPart> parts = request.multipartUpload().parts();
        assertEquals(1, parts.size());
        assertEquals(1, parts.get(0).partNumber());
        assertEquals("test-etag-1", parts.get(0).eTag());
    }

    /**
     * Testa tratamento de erro com abort de multipart upload quando ocorre falha durante upload.
     */
    @Test
    void transferirSFTPparaS3_deveAbortarMultipartQuandoOcorreErroNoUpload() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024 * 1024); // 1MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "test-file.txt";
        long tamanho = testData.length;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 upload error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaS3(source, bucket, key, tamanho);
        });

        assertEquals("Erro na transferência SFTP para S3", exception.getMessage());
        
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, times(1)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client, times(1)).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(s3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    /**
     * Testa que o abort é chamado com os parâmetros corretos.
     */
    @Test
    void transferirSFTPparaS3_deveAbortarComParametrosCorretos() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024 * 1024); // 1MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "test-file.txt";
        long tamanho = testData.length;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id-123")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 upload error"));

        // Act
        assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaS3(source, bucket, key, tamanho);
        });

        // Assert
        ArgumentCaptor<AbortMultipartUploadRequest> captor = 
                ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
        verify(s3Client).abortMultipartUpload(captor.capture());

        AbortMultipartUploadRequest request = captor.getValue();
        assertEquals(bucket, request.bucket());
        assertEquals(key, request.key());
        assertEquals("test-upload-id-123", request.uploadId());
    }

    /**
     * Testa validação de tamanho após upload - deve lançar exceção se tamanho não corresponder.
     */
    @Test
    void transferirSFTPparaS3_deveLancarExcecaoQuandoTamanhoNaoCorresponde() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024 * 1024); // 1MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "test-file.txt";
        long tamanhoEsperado = 2 * 1024 * 1024; // 2MB (diferente do real)

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag-1")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaS3(source, bucket, key, tamanhoEsperado);
        });

        assertTrue(exception.getMessage().contains("Tamanho do arquivo não corresponde ao esperado"));
        
        // Deve abortar o multipart upload
        verify(s3Client, times(1)).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(s3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    /**
     * Testa validação de parâmetros - InputStream nulo.
     */
    @Test
    void transferirSFTPparaS3_deveLancarExcecaoQuandoInputStreamNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaS3(null, "bucket", "key", 1000);
        });

        assertEquals("InputStream de origem não pode ser nulo", exception.getMessage());
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    /**
     * Testa validação de parâmetros - bucket vazio.
     */
    @Test
    void transferirSFTPparaS3_deveLancarExcecaoQuandoBucketVazio() {
        // Arrange
        InputStream source = new ByteArrayInputStream(new byte[100]);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaS3(source, "", "key", 100);
        });

        assertEquals("Nome do bucket não pode ser vazio", exception.getMessage());
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    /**
     * Testa validação de parâmetros - key vazia.
     */
    @Test
    void transferirSFTPparaS3_deveLancarExcecaoQuandoKeyVazia() {
        // Arrange
        InputStream source = new ByteArrayInputStream(new byte[100]);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaS3(source, "bucket", "", 100);
        });

        assertEquals("Chave do arquivo não pode ser vazia", exception.getMessage());
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    /**
     * Testa validação de parâmetros - tamanho inválido.
     */
    @Test
    void transferirSFTPparaS3_deveLancarExcecaoQuandoTamanhoInvalido() {
        // Arrange
        InputStream source = new ByteArrayInputStream(new byte[100]);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaS3(source, "bucket", "key", 0);
        });

        assertEquals("Tamanho do arquivo deve ser maior que zero", exception.getMessage());
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    // ========== Tests for transferirSFTPparaSFTP ==========

    /**
     * Testa transferência SFTP para SFTP com arquivo pequeno.
     */
    @Test
    void transferirSFTPparaSFTP_deveTransferirArquivoPequeno() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024 * 1024); // 1MB
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        String caminho = "/remote/path/file.txt";
        long tamanho = testData.length;

        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);

        // Assert
        byte[] writtenData = destino.toByteArray();
        assertEquals(testData.length, writtenData.length);
        assertArrayEquals(testData, writtenData);
    }

    /**
     * Testa transferência SFTP para SFTP com arquivo grande.
     */
    @Test
    void transferirSFTPparaSFTP_deveTransferirArquivoGrande() throws IOException {
        // Arrange
        byte[] testData = createTestData(15 * 1024 * 1024); // 15MB
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        String caminho = "/remote/path/large-file.txt";
        long tamanho = testData.length;

        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);

        // Assert
        byte[] writtenData = destino.toByteArray();
        assertEquals(testData.length, writtenData.length);
        assertArrayEquals(testData, writtenData);
    }

    /**
     * Testa que o OutputStream é flushed após a transferência.
     */
    @Test
    void transferirSFTPparaSFTP_deveFlushOutputStream() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024); // 1KB
        InputStream source = new ByteArrayInputStream(testData);
        OutputStream destino = mock(OutputStream.class);
        String caminho = "/remote/path/file.txt";
        long tamanho = testData.length;

        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);

        // Assert
        verify(destino, atLeastOnce()).write(any(byte[].class), anyInt(), anyInt());
        verify(destino, times(1)).flush();
    }

    /**
     * Testa validação de tamanho após upload - deve lançar exceção se tamanho não corresponder.
     */
    @Test
    void transferirSFTPparaSFTP_deveLancarExcecaoQuandoTamanhoNaoCorresponde() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024 * 1024); // 1MB
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        String caminho = "/remote/path/file.txt";
        long tamanhoEsperado = 2 * 1024 * 1024; // 2MB (diferente do real)

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaSFTP(source, destino, caminho, tamanhoEsperado);
        });

        assertTrue(exception.getMessage().contains("Tamanho do arquivo não corresponde ao esperado"));
        assertTrue(exception.getMessage().contains("Esperado: " + tamanhoEsperado));
        assertTrue(exception.getMessage().contains("Transferido: " + testData.length));
    }

    /**
     * Testa tratamento de erro durante transferência SFTP para SFTP.
     */
    @Test
    void transferirSFTPparaSFTP_deveLancarExcecaoQuandoOcorreErroNoWrite() throws IOException {
        // Arrange
        byte[] testData = createTestData(1024); // 1KB
        InputStream source = new ByteArrayInputStream(testData);
        OutputStream destino = mock(OutputStream.class);
        String caminho = "/remote/path/file.txt";
        long tamanho = testData.length;

        doThrow(new IOException("Write error")).when(destino).write(any(byte[].class), anyInt(), anyInt());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);
        });

        assertEquals("Erro na transferência SFTP para SFTP", exception.getMessage());
        assertTrue(exception.getCause() instanceof IOException);
    }

    /**
     * Testa validação de parâmetros - InputStream nulo.
     */
    @Test
    void transferirSFTPparaSFTP_deveLancarExcecaoQuandoInputStreamNulo() {
        // Arrange
        ByteArrayOutputStream destino = new ByteArrayOutputStream();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaSFTP(null, destino, "/path/file.txt", 1000);
        });

        assertEquals("InputStream de origem não pode ser nulo", exception.getMessage());
    }

    /**
     * Testa validação de parâmetros - OutputStream nulo.
     */
    @Test
    void transferirSFTPparaSFTP_deveLancarExcecaoQuandoOutputStreamNulo() {
        // Arrange
        InputStream source = new ByteArrayInputStream(new byte[100]);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaSFTP(source, null, "/path/file.txt", 100);
        });

        assertEquals("OutputStream de destino não pode ser nulo", exception.getMessage());
    }

    /**
     * Testa validação de parâmetros - caminho vazio.
     */
    @Test
    void transferirSFTPparaSFTP_deveLancarExcecaoQuandoCaminhoVazio() {
        // Arrange
        InputStream source = new ByteArrayInputStream(new byte[100]);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaSFTP(source, destino, "", 100);
        });

        assertEquals("Caminho do arquivo não pode ser vazio", exception.getMessage());
    }

    /**
     * Testa validação de parâmetros - tamanho inválido.
     */
    @Test
    void transferirSFTPparaSFTP_deveLancarExcecaoQuandoTamanhoInvalido() {
        // Arrange
        InputStream source = new ByteArrayInputStream(new byte[100]);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.transferirSFTPparaSFTP(source, destino, "/path/file.txt", -1);
        });

        assertEquals("Tamanho do arquivo deve ser maior que zero", exception.getMessage());
    }

    /**
     * Testa transferência com tamanho exato de 5MB (tamanho do chunk).
     */
    @Test
    void transferirSFTPparaSFTP_deveTransferirArquivoComTamanhoExatoDe5MB() throws IOException {
        // Arrange
        byte[] testData = createTestData(5 * 1024 * 1024); // Exatamente 5MB
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        String caminho = "/remote/path/5mb-file.txt";
        long tamanho = testData.length;

        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);

        // Assert
        byte[] writtenData = destino.toByteArray();
        assertEquals(testData.length, writtenData.length);
        assertArrayEquals(testData, writtenData);
    }

    /**
     * Testa transferência com arquivo muito pequeno (menos de 1KB).
     */
    @Test
    void transferirSFTPparaSFTP_deveTransferirArquivoMuitoPequeno() throws IOException {
        // Arrange
        byte[] testData = createTestData(100); // 100 bytes
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        String caminho = "/remote/path/tiny-file.txt";
        long tamanho = testData.length;

        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);

        // Assert
        byte[] writtenData = destino.toByteArray();
        assertEquals(testData.length, writtenData.length);
        assertArrayEquals(testData, writtenData);
    }

    /**
     * Testa que múltiplos chunks são transferidos corretamente para S3.
     */
    @Test
    void transferirSFTPparaS3_deveTransferirMultiplosChunksCorretamente() throws IOException {
        // Arrange
        byte[] testData = createTestData(16 * 1024 * 1024); // 16MB = 4 chunks de 5MB + 1MB
        InputStream source = new ByteArrayInputStream(testData);
        String bucket = "test-bucket";
        String key = "multi-chunk-file.txt";
        long tamanho = testData.length;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag")
                .build();

        CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(completeResponse);

        // Act
        service.transferirSFTPparaS3(source, bucket, key, tamanho);

        // Assert
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, times(4)).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }
}
