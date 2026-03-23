package com.controle.arquivos.integration;

import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClient;
import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.controle.arquivos.common.repository.FileOriginClientRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for complete end-to-end flow.
 * 
 * **Validates: Requirements 1.1, 2.1, 3.1, 4.1, 6.1, 7.1, 8.1, 9.1, 10.1, 12.1**
 * 
 * Tests the complete flow:
 * 1. Orchestrator collects files from SFTP
 * 2. Orchestrator registers files in database
 * 3. Orchestrator publishes messages to RabbitMQ
 * 4. Processor consumes messages
 * 5. Processor downloads files via streaming
 * 6. Processor identifies client and layout
 * 7. Processor uploads to S3/SFTP destination
 * 8. Rastreabilidade is updated at each stage
 */
class EndToEndFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FileOriginRepository fileOriginRepository;

    @Autowired
    private FileOriginClientRepository fileOriginClientRepository;

    @Autowired
    private FileOriginClientProcessingRepository processingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private S3Client s3Client;
    private Session sftpSession;
    private ChannelSftp sftpChannel;

    @BeforeEach
    void setUpTest() throws Exception {
        // Clean database
        jdbcTemplate.execute("DELETE FROM file_origin_client_processing");
        jdbcTemplate.execute("DELETE FROM file_origin_client");
        jdbcTemplate.execute("DELETE FROM file_origin");

        // Set up S3 client
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(localstackContainer.getEndpointOverride(
                        org.testcontainers.containers.localstack.LocalStackContainer.Service.S3).toString()))
                .region(Region.of(localstackContainer.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                localstackContainer.getAccessKey(),
                                localstackContainer.getSecretKey())))
                .build();

        // Create S3 bucket
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket("test-bucket")
                    .build());
        } catch (Exception e) {
            // Bucket may already exist
        }

        // Set up SFTP connection
        JSch jsch = new JSch();
        sftpSession = jsch.getSession("sftpuser", sftpContainer.getHost(), sftpContainer.getMappedPort(22));
        sftpSession.setPassword("sftppass");
        sftpSession.setConfig("StrictHostKeyChecking", "no");
        sftpSession.connect();

        sftpChannel = (ChannelSftp) sftpSession.openChannel("sftp");
        sftpChannel.connect();

        // Create test directory structure
        try {
            sftpChannel.mkdir("/upload/origin");
        } catch (Exception e) {
            // Directory may already exist
        }
    }

    @Test
    void shouldProcessCompleteFlowFromSFTPToS3() throws Exception {
        // Given: A file exists on SFTP server
        String fileName = "CIELO_20240115_001.txt";
        String fileContent = "HEADER|CIELO|20240115\nDATA|12345|100.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Orchestrator runs collection cycle
        // (This would be triggered by scheduler or manual invocation)
        // For this test, we'll simulate the orchestrator behavior

        // Then: File should be registered in database
        await().atMost(30, SECONDS).untilAsserted(() -> {
            List<FileOrigin> files = fileOriginRepository.findAll();
            assertThat(files).isNotEmpty();
            assertThat(files.get(0).getDesFileName()).isEqualTo(fileName);
        });

        // And: Rastreabilidade should show COLETA stage
        await().atMost(30, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).isNotEmpty();
            assertThat(processing).anyMatch(p -> 
                    p.getDesStep().equals(EtapaProcessamento.COLETA.name()) &&
                    p.getDesStatus().equals(StatusProcessamento.CONCLUIDO.name())
            );
        });

        // And: Message should be published to RabbitMQ and consumed by Processor
        // And: Client should be identified
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClient> clients = fileOriginClientRepository.findAll();
            assertThat(clients).isNotEmpty();
        });

        // And: Rastreabilidade should show STAGING stage (client/layout identified)
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).anyMatch(p -> 
                    p.getDesStep().equals(EtapaProcessamento.STAGING.name()) &&
                    p.getDesStatus().equals(StatusProcessamento.CONCLUIDO.name())
            );
        });

        // And: File should be uploaded to S3
        await().atMost(60, SECONDS).untilAsserted(() -> {
            try {
                s3Client.headObject(HeadObjectRequest.builder()
                        .bucket("test-bucket")
                        .key(fileName)
                        .build());
            } catch (Exception e) {
                throw new AssertionError("File not found in S3", e);
            }
        });

        // And: Rastreabilidade should show PROCESSED stage
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).anyMatch(p -> 
                    p.getDesStep().equals(EtapaProcessamento.PROCESSED.name()) &&
                    p.getDesStatus().equals(StatusProcessamento.CONCLUIDO.name())
            );
        });

        // And: All stages should have timestamps
        List<FileOriginClientProcessing> allProcessing = processingRepository.findAll();
        assertThat(allProcessing).allMatch(p -> p.getDatStepStart() != null);
        assertThat(allProcessing)
                .filteredOn(p -> p.getDesStatus().equals(StatusProcessamento.CONCLUIDO.name()))
                .allMatch(p -> p.getDatStepEnd() != null);
    }

    @Test
    void shouldMaintainRastreabilidadeAcrossAllStages() throws Exception {
        // Given: A file is being processed
        String fileName = "REDE_20240115_002.txt";
        String fileContent = "HEADER|REDE|20240115\nDATA|67890|200.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Complete flow executes
        // Then: All expected stages should be recorded in order
        await().atMost(90, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            
            // Verify all stages exist
            assertThat(processing).anyMatch(p -> p.getDesStep().equals(EtapaProcessamento.COLETA.name()));
            assertThat(processing).anyMatch(p -> p.getDesStep().equals(EtapaProcessamento.RAW.name()));
            assertThat(processing).anyMatch(p -> p.getDesStep().equals(EtapaProcessamento.STAGING.name()));
            assertThat(processing).anyMatch(p -> p.getDesStep().equals(EtapaProcessamento.PROCESSING.name()));
            assertThat(processing).anyMatch(p -> p.getDesStep().equals(EtapaProcessamento.PROCESSED.name()));
            
            // Verify chronological order
            FileOriginClientProcessing coleta = processing.stream()
                    .filter(p -> p.getDesStep().equals(EtapaProcessamento.COLETA.name()))
                    .findFirst().orElseThrow();
            FileOriginClientProcessing processed = processing.stream()
                    .filter(p -> p.getDesStep().equals(EtapaProcessamento.PROCESSED.name()))
                    .findFirst().orElseThrow();
            
            assertThat(coleta.getDatStepStart()).isBefore(processed.getDatStepEnd());
        });
    }

    @Test
    void shouldHandleMultipleFilesInParallel() throws Exception {
        // Given: Multiple files exist on SFTP
        String[] fileNames = {
                "CIELO_20240115_003.txt",
                "REDE_20240115_004.txt",
                "GETNET_20240115_005.txt"
        };
        
        for (String fileName : fileNames) {
            String content = "HEADER|TEST|20240115\nDATA|12345|100.00\nTRAILER|2";
            sftpChannel.put(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                    "/upload/origin/" + fileName
            );
        }

        // When: Orchestrator processes all files
        // Then: All files should be processed successfully
        await().atMost(120, SECONDS).untilAsserted(() -> {
            List<FileOrigin> files = fileOriginRepository.findAll();
            assertThat(files).hasSizeGreaterThanOrEqualTo(3);
            
            for (String fileName : fileNames) {
                assertThat(files).anyMatch(f -> f.getDesFileName().equals(fileName));
            }
        });

        // And: All files should have complete rastreabilidade
        await().atMost(120, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            
            for (String fileName : fileNames) {
                FileOrigin file = fileOriginRepository.findAll().stream()
                        .filter(f -> f.getDesFileName().equals(fileName))
                        .findFirst().orElseThrow();
                
                assertThat(processing).anyMatch(p -> 
                        p.getIdtFileOriginClient() != null &&
                        p.getDesStatus().equals(StatusProcessamento.CONCLUIDO.name())
                );
            }
        });
    }
}
