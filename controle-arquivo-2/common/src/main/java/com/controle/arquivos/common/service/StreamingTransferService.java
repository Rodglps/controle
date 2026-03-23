package com.controle.arquivos.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável pela transferência de arquivos via streaming.
 * Suporta transferências SFTP para S3 e SFTP para SFTP sem carregar o arquivo completo em memória.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingTransferService {

    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks para multipart upload
    private static final int MIN_PART_SIZE = 5 * 1024 * 1024; // Tamanho mínimo de parte S3 (exceto última)

    private final S3Client s3Client;

    /**
     * Transfere arquivo de SFTP para S3 usando multipart upload com streaming.
     * 
     * @param source InputStream do arquivo de origem (SFTP)
     * @param bucket Nome do bucket S3 de destino
     * @param key Chave (caminho) do arquivo no S3
     * @param tamanho Tamanho esperado do arquivo em bytes
     * @throws IOException Se ocorrer erro durante a transferência
     * @throws RuntimeException Se o upload falhar ou o tamanho não corresponder
     */
    public void transferirSFTPparaS3(InputStream source, String bucket, String key, long tamanho) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("InputStream de origem não pode ser nulo");
        }
        if (bucket == null || bucket.isEmpty()) {
            throw new IllegalArgumentException("Nome do bucket não pode ser vazio");
        }
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Chave do arquivo não pode ser vazia");
        }
        if (tamanho <= 0) {
            throw new IllegalArgumentException("Tamanho do arquivo deve ser maior que zero");
        }

        log.info("Iniciando transferência SFTP para S3: bucket={}, key={}, tamanho={} bytes", bucket, key, tamanho);

        // Iniciar multipart upload
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        String uploadId = createResponse.uploadId();

        log.debug("Multipart upload iniciado: uploadId={}", uploadId);

        try {
            List<CompletedPart> completedParts = new ArrayList<>();
            byte[] buffer = new byte[CHUNK_SIZE];
            int partNumber = 1;
            int bytesRead;
            long totalBytesRead = 0;

            // Ler e fazer upload em chunks
            while ((bytesRead = source.read(buffer)) != -1) {
                totalBytesRead += bytesRead;

                // Criar parte do multipart upload
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();

                // Fazer upload da parte
                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromBytes(java.util.Arrays.copyOf(buffer, bytesRead))
                );

                // Adicionar parte completada à lista
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build();
                completedParts.add(completedPart);

                log.debug("Parte {} enviada: {} bytes, eTag={}", partNumber, bytesRead, uploadPartResponse.eTag());

                partNumber++;
            }

            // Validar tamanho após upload
            if (totalBytesRead != tamanho) {
                log.error("Tamanho do arquivo não corresponde: esperado={}, lido={}", tamanho, totalBytesRead);
                throw new RuntimeException(String.format(
                        "Tamanho do arquivo não corresponde ao esperado. Esperado: %d bytes, Lido: %d bytes",
                        tamanho, totalBytesRead));
            }

            // Completar multipart upload
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();

            CompleteMultipartUploadResponse completeResponse = s3Client.completeMultipartUpload(completeRequest);

            log.info("Transferência SFTP para S3 concluída com sucesso: bucket={}, key={}, eTag={}, partes={}",
                    bucket, key, completeResponse.eTag(), completedParts.size());

        } catch (Exception e) {
            log.error("Erro durante transferência SFTP para S3. Abortando multipart upload: uploadId={}", uploadId, e);

            // Abortar multipart upload em caso de falha
            try {
                AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .build();

                s3Client.abortMultipartUpload(abortRequest);
                log.info("Multipart upload abortado com sucesso: uploadId={}", uploadId);
            } catch (Exception abortException) {
                log.error("Erro ao abortar multipart upload: uploadId={}", uploadId, abortException);
            }

            throw new RuntimeException("Erro na transferência SFTP para S3", e);
        }
    }

    /**
     * Transfere arquivo de SFTP para SFTP usando OutputStream encadeado.
     * 
     * @param source InputStream do arquivo de origem (SFTP)
     * @param destino OutputStream do arquivo de destino (SFTP)
     * @param caminho Caminho do arquivo no SFTP de destino (para logging)
     * @param tamanho Tamanho esperado do arquivo em bytes
     * @throws IOException Se ocorrer erro durante a transferência
     * @throws RuntimeException Se o tamanho não corresponder
     */
    public void transferirSFTPparaSFTP(InputStream source, OutputStream destino, String caminho, long tamanho) 
            throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("InputStream de origem não pode ser nulo");
        }
        if (destino == null) {
            throw new IllegalArgumentException("OutputStream de destino não pode ser nulo");
        }
        if (caminho == null || caminho.isEmpty()) {
            throw new IllegalArgumentException("Caminho do arquivo não pode ser vazio");
        }
        if (tamanho <= 0) {
            throw new IllegalArgumentException("Tamanho do arquivo deve ser maior que zero");
        }

        log.info("Iniciando transferência SFTP para SFTP: caminho={}, tamanho={} bytes", caminho, tamanho);

        try {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            int chunkCount = 0;

            // Ler do source e escrever no destino em chunks
            while ((bytesRead = source.read(buffer)) != -1) {
                destino.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                chunkCount++;

                if (chunkCount % 10 == 0) {
                    log.debug("Transferência em progresso: {} bytes transferidos ({} chunks)", 
                            totalBytesRead, chunkCount);
                }
            }

            // Garantir que todos os dados foram escritos
            destino.flush();

            // Validar tamanho após upload
            if (totalBytesRead != tamanho) {
                log.error("Tamanho do arquivo não corresponde: esperado={}, transferido={}", tamanho, totalBytesRead);
                throw new RuntimeException(String.format(
                        "Tamanho do arquivo não corresponde ao esperado. Esperado: %d bytes, Transferido: %d bytes",
                        tamanho, totalBytesRead));
            }

            log.info("Transferência SFTP para SFTP concluída com sucesso: caminho={}, bytes={}, chunks={}",
                    caminho, totalBytesRead, chunkCount);

        } catch (IOException e) {
            log.error("Erro durante transferência SFTP para SFTP: caminho={}", caminho, e);
            throw new RuntimeException("Erro na transferência SFTP para SFTP", e);
        }
    }
}
