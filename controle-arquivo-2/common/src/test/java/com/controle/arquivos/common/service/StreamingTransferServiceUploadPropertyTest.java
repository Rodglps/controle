package com.controle.arquivos.common.service;

import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para upload com streaming.
 * 
 * Feature: controle-de-arquivos, Properties 20, 21, 22
 * 
 * Valida upload S3 com multipart, upload SFTP com streaming, e validação de tamanho.
 * 
 * **Valida: Requisitos 10.2, 10.3, 10.5, 10.6**
 */
class StreamingTransferServiceUploadPropertyTest {

    /**
     * **Propriedade 20: Upload para S3 com Multipart**
     * **Valida: Requisitos 10.2**
     * 
     * Para qualquer arquivo cujo destino é S3, o sistema deve usar
     * multipart upload com InputStream encadeado.
     */
    @Property(tries = 50)
    void uploadS3DeveUsarMultipartComStreaming(
            @ForAll("bucket") String bucket,
            @ForAll("key") String key,
            @ForAll("tamanhoArquivo") long tamanho) throws Exception {
        
        // Arrange
        S3Client s3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(s3Client);
        InputStream source = new ByteArrayInputStream(new byte[(int) tamanho]);
        
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
        
        // Assert - Verificar que multipart upload foi usado
        verify(s3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client, atLeastOnce()).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
        verify(s3Client, times(1)).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
        
        // Verificar que abort NÃO foi chamado (upload bem-sucedido)
        verify(s3Client, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    /**
     * **Propriedade 21: Upload para SFTP com Streaming**
     * **Valida: Requisitos 10.3**
     * 
     * Para qualquer arquivo cujo destino é SFTP, o sistema deve usar
     * OutputStream encadeado diretamente do InputStream de origem.
     */
    @Property(tries = 50)
    void uploadSFTPDeveUsarOutputStreamEncadeado(
            @ForAll("caminho") String caminho,
            @ForAll("tamanhoArquivo") long tamanho) throws Exception {
        
        // Arrange
        S3Client s3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(s3Client);
        byte[] dados = new byte[(int) tamanho];
        InputStream source = new ByteArrayInputStream(dados);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        
        // Act
        service.transferirSFTPparaSFTP(source, destino, caminho, tamanho);
        
        // Assert - Verificar que dados foram transferidos via streaming
        assertThat(destino.toByteArray()).hasSize((int) tamanho);
        
        // Verificar que flush foi chamado (dados foram escritos)
        assertThat(destino.size()).isEqualTo((int) tamanho);
    }

    /**
     * **Propriedade 22: Validação de Tamanho após Upload**
     * **Valida: Requisitos 10.5, 10.6**
     * 
     * Para qualquer upload concluído, o sistema deve validar que o tamanho
     * do arquivo no destino corresponde ao tamanho original.
     */
    @Property(tries = 50)
    void uploadDeveValidarTamanhoAposTransferencia(
            @ForAll("caminho") String caminho,
            @ForAll("tamanhoEsperado") long tamanhoEsperado,
            @ForAll("tamanhoReal") long tamanhoReal) throws Exception {
        
        // Arrange
        S3Client s3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(s3Client);
        InputStream source = new ByteArrayInputStream(new byte[(int) tamanhoReal]);
        OutputStream destino = new ByteArrayOutputStream();
        
        // Act & Assert
        if (tamanhoEsperado != tamanhoReal) {
            // Tamanho não corresponde - deve lançar exceção
            assertThatThrownBy(() -> 
                service.transferirSFTPparaSFTP(source, destino, caminho, tamanhoEsperado))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tamanho do arquivo não corresponde");
        } else {
            // Tamanho corresponde - deve completar com sucesso
            service.transferirSFTPparaSFTP(source, destino, caminho, tamanhoEsperado);
            assertThat(destino.toString().length()).isEqualTo((int) tamanhoReal);
        }
    }

    /**
     * **Propriedade 20: Upload para S3 com Multipart**
     * **Valida: Requisitos 10.2**
     * 
     * Para qualquer falha durante upload S3, o sistema deve abortar
     * o multipart upload para evitar cobranças.
     */
    @Property(tries = 50)
    void falhaUploadS3DeveAbortarMultipart(
            @ForAll("bucket") String bucket,
            @ForAll("key") String key,
            @ForAll("tamanhoArquivo") long tamanho) throws Exception {
        
        // Arrange
        S3Client s3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(s3Client);
        InputStream source = new ByteArrayInputStream(new byte[(int) tamanho]);
        
        CreateMultipartUploadResponse createResponse = CreateMultipartUploadResponse.builder()
            .uploadId("test-upload-id")
            .build();
        
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
            .thenReturn(createResponse);
        
        // Simular falha no upload de parte
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
            .thenThrow(new RuntimeException("Falha simulada"));
        
        // Act & Assert
        assertThatThrownBy(() -> 
            service.transferirSFTPparaS3(source, bucket, key, tamanho))
            .isInstanceOf(RuntimeException.class);
        
        // Verificar que abort foi chamado
        verify(s3Client, times(1)).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    /**
     * **Propriedade 22: Validação de Tamanho após Upload**
     * **Valida: Requisitos 10.5**
     * 
     * Para qualquer arquivo de tamanho variado, a validação de tamanho
     * deve funcionar corretamente (round-trip).
     */
    @Property(tries = 50)
    void validacaoTamanhoDeveSerPrecisa(
            @ForAll("tamanhoArquivoVariado") long tamanho) throws Exception {
        
        // Arrange
        S3Client s3Client = mock(S3Client.class);
        StreamingTransferService service = new StreamingTransferService(s3Client);
        byte[] dados = new byte[(int) tamanho];
        InputStream source = new ByteArrayInputStream(dados);
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        
        // Act
        service.transferirSFTPparaSFTP(source, destino, "/test/file.txt", tamanho);
        
        // Assert - Tamanho deve corresponder exatamente
        assertThat(destino.toByteArray()).hasSize((int) tamanho);
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<String> bucket() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-')
            .ofMinLength(3)
            .ofMaxLength(63)
            .map(s -> s.toLowerCase());
    }

    @Provide
    Arbitrary<String> key() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('/', '-', '_', '.')
            .ofMinLength(5)
            .ofMaxLength(100);
    }

    @Provide
    Arbitrary<String> caminho() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('/', '-', '_', '.')
            .ofMinLength(5)
            .ofMaxLength(100)
            .map(s -> "/sftp/" + s);
    }

    @Provide
    Arbitrary<Long> tamanhoArquivo() {
        // Tamanhos pequenos para testes rápidos
        return Arbitraries.longs().between(1L, 1024 * 1024L); // 1B - 1MB
    }

    @Provide
    Arbitrary<Long> tamanhoArquivoVariado() {
        return Arbitraries.frequency(
            Tuple.of(4, Arbitraries.longs().between(1L, 1024L)), // Pequenos: 1B - 1KB
            Tuple.of(3, Arbitraries.longs().between(1024L, 100 * 1024L)), // Médios: 1KB - 100KB
            Tuple.of(2, Arbitraries.longs().between(100 * 1024L, 1024 * 1024L)) // Grandes: 100KB - 1MB
        );
    }

    @Provide
    Arbitrary<Long> tamanhoEsperado() {
        return Arbitraries.longs().between(1L, 10 * 1024L);
    }

    @Provide
    Arbitrary<Long> tamanhoReal() {
        return Arbitraries.longs().between(1L, 10 * 1024L);
    }
}
