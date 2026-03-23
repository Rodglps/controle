package com.controle.arquivos.processor.integration;

import com.controle.arquivos.common.domain.entity.*;
import com.controle.arquivos.common.domain.enums.*;
import com.controle.arquivos.common.repository.*;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Testes de integração para o Processador usando Testcontainers.
 * 
 * Testa o fluxo completo:
 * 1. Consumir mensagem do RabbitMQ
 * 2. Baixar arquivo do SFTP via streaming
 * 3. Identificar cliente e layout
 * 4. Fazer upload para destino (S3 ou SFTP)
 * 5. Registrar rastreabilidade completa
 * 
 * Testa cenários de erro:
 * - Arquivo não encontrado no SFTP
 * - Cliente não identificado
 * - Falha de upload
 * 
 * Testa reprocessamento:
 * - Retry após falha recuperável
 * 
 * **Valida: Requisitos 6.1, 7.1, 8.1, 9.1, 10.1, 12.1, 15.3**
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(TestConfiguration.class)
class ProcessadorIntegrationTest {

    // Oracle Database Container
    @Container
    static OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(false);

    // RabbitMQ Container
    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3.12-management-alpine")
            .withReuse(false);

    // LocalStack Container for S3
    @Container
    static LocalStackContainer localStackContainer = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(S3)
            .withReuse(false);

    // SFTP Container (using atmoz/sftp image)
    @Container
    static GenericContainer<?> sftpContainer = new GenericContainer<>(DockerImageName.parse("atmoz/sftp:alpine"))
            .withExposedPorts(22)
            .withCommand("testuser:testpass:::upload")
            .withReuse(false);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Oracle configuration
        registry.add("spring.datasource.url", oracleContainer::getJdbcUrl);
        registry.add("spring.datasource.username", oracleContainer::getUsername);
        registry.add("spring.datasource.password", oracleContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
        
        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.OracleDialect");
        
        // RabbitMQ configuration
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("rabbitmq.queue.processamento", () -> "test-processamento-queue");
        
        // Vault configuration (disabled for tests)
        registry.add("app.vault.enabled", () -> "false");
        
        // AWS S3 configuration (LocalStack)
        registry.add("aws.s3.endpoint", () -> localStackContainer.getEndpointOverride(S3).toString());
        registry.add("aws.s3.region", () -> localStackContainer.getRegion());
        registry.add("aws.accessKeyId", () -> localStackContainer.getAccessKey());
        registry.add("aws.secretAccessKey", () -> localStackContainer.getSecretKey());
        
        // SFTP configuration
        registry.add("app.sftp.timeout", () -> "30000");
        registry.add("app.sftp.session-timeout", () -> "30000");
        registry.add("app.sftp.channel-timeout", () -> "30000");
        registry.add("app.sftp.strict-host-key-checking", () -> "false");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FileOriginRepository fileOriginRepository;

    @Autowired
    private FileOriginClientRepository fileOriginClientRepository;

    @Autowired
    private FileOriginClientProcessingRepository processingRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private SeverPathsRepository severPathsRepository;

    @Autowired
    private SeverPathsInOutRepository severPathsInOutRepository;

    @Autowired
    private CustomerIdentificationRepository customerIdentificationRepository;

    @Autowired
    private CustomerIdentificationRuleRepository customerIdentificationRuleRepository;

    @Autowired
    private com.controle.arquivos.common.repository.LayoutRepository layoutRepository;

    @Autowired
    private LayoutIdentificationRuleRepository layoutIdentificationRuleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static Session sftpSession;
    private static ChannelSftp sftpChannel;
    private static S3Client s3Client;

    @BeforeAll
    static void setupInfrastructure() throws Exception {
        // Wait for containers to be ready
        TimeUnit.SECONDS.sleep(3);
        
        // Setup SFTP
        setupSFTP();
        
        // Setup S3
        setupS3();
    }

    private static void setupSFTP() throws Exception {
        // Connect to SFTP
        JSch jsch = new JSch();
        sftpSession = jsch.getSession("testuser", sftpContainer.getHost(), sftpContainer.getMappedPort(22));
        sftpSession.setPassword("testpass");
        
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        sftpSession.setConfig(config);
        sftpSession.connect(30000);
        
        Channel channel = sftpSession.openChannel("sftp");
        channel.connect(30000);
        sftpChannel = (ChannelSftp) channel;
        
        // Create test directories
        try {
            sftpChannel.mkdir("/upload/origin");
            sftpChannel.mkdir("/upload/destination");
        } catch (SftpException e) {
            // Directories might already exist
        }
    }

    private static void setupS3() {
        // Create S3 client for LocalStack
        s3Client = S3Client.builder()
                .endpointOverride(localStackContainer.getEndpointOverride(S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                localStackContainer.getAccessKey(),
                                localStackContainer.getSecretKey())))
                .region(Region.of(localStackContainer.getRegion()))
                .build();
        
        // Create test bucket
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket("test-bucket")
                .build());
    }

    @AfterAll
    static void teardownInfrastructure() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (sftpSession != null && sftpSession.isConnected()) {
            sftpSession.disconnect();
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @BeforeEach
    void setup() {
        // Clean database before each test
        processingRepository.deleteAll();
        fileOriginClientRepository.deleteAll();
        fileOriginRepository.deleteAll();
        layoutIdentificationRuleRepository.deleteAll();
        layoutRepository.deleteAll();
        customerIdentificationRuleRepository.deleteAll();
        customerIdentificationRepository.deleteAll();
        severPathsInOutRepository.deleteAll();
        severPathsRepository.deleteAll();
        serverRepository.deleteAll();
    }
