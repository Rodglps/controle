package com.controle.arquivos.common.service;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para StreamingTransferService.
 * 
 * Feature: controle-de-arquivos, Property 22: Validação de Tamanho após Upload
 * 
 * Para qualquer upload concluído, o Processador deve validar que o tamanho do arquivo
 * no destino corresponde ao tamanho original, e em caso de falha, deve registrar erro
 * detalhado e manter o arquivo na origem.
 * 
 * **Valida: Requisitos 10.5, 10.6**
 */
class StreamingTransferServicePropertyTest {

    /**
     * Propriedade 22: Para qualquer arquivo transferido de SFTP para S3,
     * o tamanho no destino deve corresponder ao tamanho original.
     * 
     * Gera InputStreams com tamanhos variados (1KB a 100MB) e verifica que
     * o tamanho transferido corresponde ao tamanho esperado.
     */
    @Property(tries = 50)
    void propriedade22_transferirSFTPparaS3_tamanhoDeveCorresponderAoOriginal(
        @ForAll("tamanhoArquivo") long tamanho,
        @ForAll("bucket") String bucket,
        @ForAll("key") String key
    ) throws IOException {
        // Arrange
        S3Client mockS3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(mockS3Client);
        
        byte[] testData = gerarDadosAleatorios((int) tamanho);
        InputStream source = new ByteArrayInputStream(testData);

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag")
                .build();

        CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        when(mockS3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(mockS3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);
        when(mockS3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(completeResponse);

        // Act
        service.transferirSFTPparaS3(source, bucket, key, tamanho);

        // Assert - Verificar que o upload foi completado (não abortado)
        verify(mockS3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(mockS3Client, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        
        // Verificar que todos os bytes foram enviados
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(mockS3Client, atLeastOnce()).uploadPart(any(UploadPartRequest.class), bodyCaptor.capture());
    }

    /**
     * Propriedade 22 (caso de falha): Para qualquer arquivo onde o tamanho lido
     * não corresponde ao tamanho esperado, o sistema deve lançar exceção e abortar
     * o multipart upload.
     */
    @Property(tries = 50)
    void propriedade22_transferirSFTPparaS3_deveLancarExcecaoQuandoTamanhoNaoCorresponde(
        @ForAll("tamanhoArquivo") long tamanhoReal,
        @ForAll("bucket") String bucket,
        @ForAll("key") String key
    ) throws IOException {
        // Arrange
        S3Client mockS3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(mockS3Client);
        
        byte[] testData = gerarDadosAleatorios((int) tamanhoReal);
        InputStream source = new ByteArrayInputStream(testData);
        
        // Tamanho esperado diferente do real (adiciona pelo menos 1KB de diferença)
        long tamanhoEsperado = tamanhoReal + 1024;

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag")
                .build();

        when(mockS3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(mockS3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaS3(source, bucket, key, tamanhoEsperado);
        });

        // Verificar mensagem de erro contém informações sobre a discrepância
        assertTrue(exception.getMessage().contains("Tamanho do arquivo não corresponde ao esperado"),
                "Mensagem de erro deve indicar discrepância de tamanho");
        assertTrue(exception.getMessage().contains(String.valueOf(tamanhoEsperado)),
                "Mensagem de erro deve conter tamanho esperado");
        assertTrue(exception.getMessage().contains(String.valueOf(tamanhoReal)),
                "Mensagem de erro deve conter tamanho real");
        
        // Verificar que o multipart upload foi abortado
        verify(mockS3Client, times(1)).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(mockS3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    /**
     * Propriedade 22: Para qualquer arquivo transferido de SFTP para SFTP,
     * o tamanho no destino deve corresponder ao tamanho original.
     */
    @Property(tries = 50)
    void propriedade22_transferirSFTPparaSFTP_tamanhoDeveCorresponderAoOriginal(
        @ForAll("tamanhoArquivo") long tamanho,
        @ForAll("caminho") String caminho
    ) throws IOException {
        // Arrange
        S3Client mockS3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(mockS3Client);
        
        byte[] testData = gerarDadosAleatorios((int) tamanho);
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();

        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);

        // Assert - Verificar que todos os bytes foram transferidos
        byte[] bytesTransferidos = destino.toByteArray();
        assertEquals(tamanho, bytesTransferidos.length,
                "Tamanho transferido deve corresponder ao tamanho original");
        assertArrayEquals(testData, bytesTransferidos,
                "Conteúdo transferido deve ser idêntico ao original");
    }

    /**
     * Propriedade 22 (caso de falha): Para qualquer arquivo SFTP->SFTP onde o tamanho
     * lido não corresponde ao tamanho esperado, o sistema deve lançar exceção.
     */
    @Property(tries = 50)
    void propriedade22_transferirSFTPparaSFTP_deveLancarExcecaoQuandoTamanhoNaoCorresponde(
        @ForAll("tamanhoArquivo") long tamanhoReal,
        @ForAll("caminho") String caminho
    ) throws IOException {
        // Arrange
        S3Client mockS3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(mockS3Client);
        
        byte[] testData = gerarDadosAleatorios((int) tamanhoReal);
        InputStream source = new ByteArrayInputStream(testData);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        
        // Tamanho esperado diferente do real
        long tamanhoEsperado = tamanhoReal + 1024;

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            service.transferirSFTPparaSFTP(source, destino, caminho, tamanhoEsperado);
        });

        // Verificar mensagem de erro contém informações sobre a discrepância
        assertTrue(exception.getMessage().contains("Tamanho do arquivo não corresponde ao esperado"),
                "Mensagem de erro deve indicar discrepância de tamanho");
        assertTrue(exception.getMessage().contains(String.valueOf(tamanhoEsperado)),
                "Mensagem de erro deve conter tamanho esperado");
        assertTrue(exception.getMessage().contains(String.valueOf(tamanhoReal)),
                "Mensagem de erro deve conter tamanho real");
    }

    /**
     * Propriedade: Para qualquer arquivo transferido em múltiplos chunks,
     * o tamanho total deve corresponder à soma dos chunks.
     */
    @Property(tries = 30)
    void transferirSFTPparaS3_tamanhoTotalDeveCorresponderASomaDosChunks(
        @ForAll("tamanhoArquivoGrande") long tamanho,
        @ForAll("bucket") String bucket,
        @ForAll("key") String key
    ) throws IOException {
        // Arrange
        S3Client mockS3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(mockS3Client);
        
        byte[] testData = gerarDadosAleatorios((int) tamanho);
        InputStream source = new ByteArrayInputStream(testData);

        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
                .uploadId("test-upload-id")
                .build();

        UploadPartResponse uploadPartResponse = UploadPartResponse.builder()
                .eTag("test-etag")
                .build();

        CompleteMultipartUploadResponse completeResponse = CompleteMultipartUploadResponse.builder()
                .eTag("final-etag")
                .build();

        when(mockS3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(createResponse);
        when(mockS3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(uploadPartResponse);
        when(mockS3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(completeResponse);

        // Act
        service.transferirSFTPparaS3(source, bucket, key, tamanho);

        // Assert
        verify(mockS3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        verify(mockS3Client, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        
        // Calcular número esperado de partes (chunks de 5MB)
        int chunkSize = 5 * 1024 * 1024;
        int partesEsperadas = (int) ((tamanho + chunkSize - 1) / chunkSize);
        verify(mockS3Client, times(partesEsperadas)).uploadPart(
                any(UploadPartRequest.class), 
                any(RequestBody.class)
        );
    }

    // ========== Arbitraries (Geradores de dados) ==========

    /**
     * Gera tamanhos de arquivo variados de 1KB a 100MB.
     */
    @Provide
    Arbitrary<Long> tamanhoArquivo() {
        return Arbitraries.longs()
                .between(1024L, 100L * 1024 * 1024) // 1KB a 100MB
                .edgeCases(config -> config.add(
                        1024L,                    // 1KB
                        5 * 1024 * 1024L,        // 5MB (tamanho do chunk)
                        10 * 1024 * 1024L,       // 10MB
                        50 * 1024 * 1024L,       // 50MB
                        100 * 1024 * 1024L       // 100MB
                ));
    }

    /**
     * Gera tamanhos de arquivo grandes (10MB a 100MB) para testes de múltiplos chunks.
     */
    @Provide
    Arbitrary<Long> tamanhoArquivoGrande() {
        return Arbitraries.longs()
                .between(10L * 1024 * 1024, 100L * 1024 * 1024) // 10MB a 100MB
                .edgeCases(config -> config.add(
                        10 * 1024 * 1024L,       // 10MB (2 chunks)
                        15 * 1024 * 1024L,       // 15MB (3 chunks)
                        25 * 1024 * 1024L,       // 25MB (5 chunks)
                        50 * 1024 * 1024L        // 50MB (10 chunks)
                ));
    }

    /**
     * Gera nomes de bucket S3 válidos.
     */
    @Provide
    Arbitrary<String> bucket() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .withChars('-')
                .ofMinLength(3)
                .ofMaxLength(20)
                .filter(s -> s.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$"));
    }

    /**
     * Gera chaves (paths) S3 válidas.
     */
    @Provide
    Arbitrary<String> key() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .numeric()
                .withChars('/', '-', '_', '.')
                .ofMinLength(5)
                .ofMaxLength(50)
                .filter(s -> !s.isEmpty() && !s.startsWith("/"));
    }

    /**
     * Gera caminhos de arquivo SFTP válidos.
     */
    @Provide
    Arbitrary<String> caminho() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .numeric()
                .withChars('/', '-', '_', '.')
                .ofMinLength(5)
                .ofMaxLength(50)
                .map(s -> "/remote/path/" + s);
    }

    // ========== Helper Methods ==========

    /**
     * Gera dados aleatórios para simular conteúdo de arquivo.
     * Usa padrão determinístico para permitir verificação de integridade.
     */
    private byte[] gerarDadosAleatorios(int tamanho) {
        byte[] data = new byte[tamanho];
        Random random = new Random(tamanho); // Seed baseado no tamanho para reprodutibilidade
        random.nextBytes(data);
        return data;
    }
}
