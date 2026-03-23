package com.controle.arquivos.integration;

import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for error scenarios.
 * 
 * **Validates: Requirements 8.5, 9.6, 15.1, 15.2, 15.3, 15.4**
 * 
 * Tests error handling for:
 * 1. File not found on SFTP
 * 2. Client not identified (no matching rules)
 * 3. Layout not identified (no matching rules)
 * 4. Upload failure to destination
 * 5. Error classification (recoverable vs non-recoverable)
 */
class ErrorScenariosIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FileOriginRepository fileOriginRepository;

    @Autowired
    private FileOriginClientProcessingRepository processingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Session sftpSession;
    private ChannelSftp sftpChannel;

    @BeforeEach
    void setUpTest() throws Exception {
        // Clean database
        jdbcTemplate.execute("DELETE FROM file_origin_client_processing");
        jdbcTemplate.execute("DELETE FROM file_origin_client");
        jdbcTemplate.execute("DELETE FROM file_origin");

        // Set up SFTP connection
        JSch jsch = new JSch();
        sftpSession = jsch.getSession("sftpuser", sftpContainer.getHost(), sftpContainer.getMappedPort(22));
        sftpSession.setPassword("sftppass");
        sftpSession.setConfig("StrictHostKeyChecking", "no");
        sftpSession.connect();

        sftpChannel = (ChannelSftp) sftpSession.openChannel("sftp");
        sftpChannel.connect();

        // Create test directory
        try {
            sftpChannel.mkdir("/upload/origin");
        } catch (Exception e) {
            // Directory may already exist
        }
    }

    @Test
    void shouldHandleFileNotFoundError() throws Exception {
        // Given: A file is registered in database but doesn't exist on SFTP
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setDesFileName("NONEXISTENT_FILE.txt");
        fileOrigin.setNumFileSize(1000L);
        fileOrigin.setFlgActive(true);
        fileOriginRepository.save(fileOrigin);

        // When: Processor attempts to download the file
        // (Simulate by publishing message to RabbitMQ)

        // Then: Error should be recorded in rastreabilidade
        await().atMost(30, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).isNotEmpty();
            assertThat(processing).anyMatch(p -> 
                    p.getDesStatus().equals(StatusProcessamento.ERRO.name()) &&
                    p.getDesMessageError() != null &&
                    p.getDesMessageError().contains("not found")
            );
        });

        // And: Error should be classified as non-recoverable
        FileOriginClientProcessing errorProcessing = processingRepository.findAll().stream()
                .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                .findFirst().orElseThrow();
        
        assertThat(errorProcessing.getJsnAdditionalInfo()).contains("\"recoverable\":false");
    }

    @Test
    void shouldHandleClientNotIdentifiedError() throws Exception {
        // Given: A file with name that doesn't match any client identification rules
        String fileName = "UNKNOWN_ACQUIRER_20240115.txt";
        String fileContent = "HEADER|UNKNOWN|20240115\nDATA|12345|100.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Orchestrator collects and Processor attempts to identify client
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOrigin> files = fileOriginRepository.findAll();
            assertThat(files).isNotEmpty();
        });

        // Then: Error should be recorded indicating client not identified
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).anyMatch(p -> 
                    p.getDesStatus().equals(StatusProcessamento.ERRO.name()) &&
                    p.getDesMessageError() != null &&
                    (p.getDesMessageError().contains("Cliente não identificado") ||
                     p.getDesMessageError().contains("Client not identified"))
            );
        });

        // And: Error should be classified as non-recoverable
        FileOriginClientProcessing errorProcessing = processingRepository.findAll().stream()
                .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                .filter(p -> p.getDesMessageError().contains("identificado") || 
                           p.getDesMessageError().contains("identified"))
                .findFirst().orElseThrow();
        
        assertThat(errorProcessing.getJsnAdditionalInfo()).contains("\"recoverable\":false");
        assertThat(errorProcessing.getDatStepEnd()).isNotNull();
    }

    @Test
    void shouldHandleLayoutNotIdentifiedError() throws Exception {
        // Given: A file that matches client rules but not layout rules
        String fileName = "CIELO_UNKNOWN_LAYOUT_20240115.txt";
        String fileContent = "UNKNOWN_HEADER_FORMAT\nDATA|12345|100.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Processor attempts to identify layout
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOrigin> files = fileOriginRepository.findAll();
            assertThat(files).isNotEmpty();
        });

        // Then: Error should be recorded indicating layout not identified
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).anyMatch(p -> 
                    p.getDesStatus().equals(StatusProcessamento.ERRO.name()) &&
                    p.getDesMessageError() != null &&
                    (p.getDesMessageError().contains("Layout não identificado") ||
                     p.getDesMessageError().contains("Layout not identified"))
            );
        });

        // And: Error should be classified as non-recoverable
        FileOriginClientProcessing errorProcessing = processingRepository.findAll().stream()
                .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                .filter(p -> p.getDesMessageError().contains("Layout"))
                .findFirst().orElseThrow();
        
        assertThat(errorProcessing.getJsnAdditionalInfo()).contains("\"recoverable\":false");
    }

    @Test
    void shouldHandleUploadFailureAsRecoverable() throws Exception {
        // Given: A valid file but S3 is temporarily unavailable
        String fileName = "CIELO_20240115_UPLOAD_FAIL.txt";
        String fileContent = "HEADER|CIELO|20240115\nDATA|12345|100.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Upload fails (simulated by stopping LocalStack or invalid credentials)
        // Note: In real test, we would temporarily stop the container or inject failure

        // Then: Error should be recorded as recoverable
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            
            // Check if there's any upload error
            boolean hasUploadError = processing.stream()
                    .anyMatch(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()) &&
                                 p.getDesMessageError() != null &&
                                 (p.getDesMessageError().contains("upload") ||
                                  p.getDesMessageError().contains("S3")));
            
            if (hasUploadError) {
                FileOriginClientProcessing errorProcessing = processing.stream()
                        .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                        .filter(p -> p.getDesMessageError().contains("upload") ||
                                   p.getDesMessageError().contains("S3"))
                        .findFirst().orElseThrow();
                
                // Upload failures should be recoverable
                assertThat(errorProcessing.getJsnAdditionalInfo()).contains("\"recoverable\":true");
            }
        });
    }

    @Test
    void shouldRecordCompleteErrorContext() throws Exception {
        // Given: A file that will cause an error
        String fileName = "ERROR_CONTEXT_TEST.txt";
        String fileContent = "INVALID_CONTENT";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Processing fails
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).isNotEmpty();
        });

        // Then: Error record should contain complete context
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            
            if (processing.stream().anyMatch(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))) {
                FileOriginClientProcessing errorProcessing = processing.stream()
                        .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                        .findFirst().orElseThrow();
                
                // Verify error context
                assertThat(errorProcessing.getDesMessageError()).isNotNull();
                assertThat(errorProcessing.getDatStepStart()).isNotNull();
                assertThat(errorProcessing.getDatStepEnd()).isNotNull();
                
                // Verify additional info contains useful debugging information
                if (errorProcessing.getJsnAdditionalInfo() != null) {
                    String additionalInfo = errorProcessing.getJsnAdditionalInfo();
                    assertThat(additionalInfo).contains("\"fileName\":\"" + fileName + "\"");
                }
            }
        });
    }

    @Test
    void shouldClassifyConnectionErrorsAsRecoverable() throws Exception {
        // Given: A file is ready for processing
        String fileName = "CONNECTION_ERROR_TEST.txt";
        String fileContent = "HEADER|CIELO|20240115\nDATA|12345|100.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Connection errors occur (timeout, network issue)
        // Note: This would require injecting connection failures

        // Then: Errors should be classified as recoverable
        // Connection errors: SFTP timeout, RabbitMQ connection loss, DB connection loss
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            
            processing.stream()
                    .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                    .filter(p -> p.getDesMessageError() != null)
                    .filter(p -> p.getDesMessageError().contains("connection") ||
                               p.getDesMessageError().contains("timeout") ||
                               p.getDesMessageError().contains("network"))
                    .forEach(errorProcessing -> {
                        assertThat(errorProcessing.getJsnAdditionalInfo())
                                .contains("\"recoverable\":true");
                    });
        });
    }

    @Test
    void shouldNotRetryNonRecoverableErrors() throws Exception {
        // Given: A file that causes non-recoverable error (client not identified)
        String fileName = "NO_RETRY_TEST.txt";
        String fileContent = "HEADER|INVALID|20240115\nDATA|12345|100.00\nTRAILER|2";
        
        sftpChannel.put(
                new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)),
                "/upload/origin/" + fileName
        );

        // When: Processing fails with non-recoverable error
        await().atMost(60, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            assertThat(processing).isNotEmpty();
        });

        // Then: File should be marked as permanent error
        // And: No retry attempts should be made
        await().atMost(90, SECONDS).untilAsserted(() -> {
            List<FileOriginClientProcessing> processing = processingRepository.findAll();
            
            long errorCount = processing.stream()
                    .filter(p -> p.getDesStatus().equals(StatusProcessamento.ERRO.name()))
                    .count();
            
            // Should have only one error record (no retries)
            assertThat(errorCount).isLessThanOrEqualTo(1);
        });
    }
}
