package br.com.concil.processor.transfer;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Faz upload de um arquivo para S3 usando multipart upload (streaming).
 * Nunca carrega o arquivo inteiro em memória.
 */
@Slf4j
public class S3UploadService implements AutoCloseable {

    private static final long PART_SIZE = 5 * 1024 * 1024L; // 5MB mínimo exigido pelo S3

    private final S3Client s3Client;
    private final String bucket;

    public S3UploadService(Map<String, String> creds) {
        this.bucket = creds.get("bucket");
        this.s3Client = S3Client.builder()
                .region(Region.of(creds.getOrDefault("region", "us-east-1")))
                .endpointOverride(URI.create(creds.getOrDefault("endpoint", "https://s3.amazonaws.com")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(creds.get("access-key"), creds.get("secret-key"))))
                .forcePathStyle(true) // necessário para LocalStack
                .build();
    }

    public void upload(InputStream inputStream, String s3Key, long contentLength) {
        log.info("Iniciando upload S3: bucket={} key={}", bucket, s3Key);

        CreateMultipartUploadResponse initResponse = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(bucket).key(s3Key).build());

        String uploadId = initResponse.uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();

        try {
            byte[] buffer = new byte[(int) PART_SIZE];
            int partNumber = 1;
            int bytesRead;

            while ((bytesRead = inputStream.readNBytes(buffer, 0, buffer.length)) > 0) {
                UploadPartResponse partResponse = s3Client.uploadPart(
                        UploadPartRequest.builder()
                                .bucket(bucket).key(s3Key)
                                .uploadId(uploadId).partNumber(partNumber)
                                .contentLength((long) bytesRead)
                                .build(),
                        RequestBody.fromBytes(java.util.Arrays.copyOf(buffer, bytesRead)));

                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber).eTag(partResponse.eTag()).build());
                partNumber++;
            }

            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket).key(s3Key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build());

            log.info("Upload S3 concluído: key={} partes={}", s3Key, completedParts.size());

        } catch (Exception e) {
            log.error("Erro no upload S3, abortando multipart: {}", e.getMessage(), e);
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket).key(s3Key).uploadId(uploadId).build());
            throw new RuntimeException("Falha no upload S3: " + s3Key, e);
        }
    }

    @Override
    public void close() {
        s3Client.close();
    }
}
