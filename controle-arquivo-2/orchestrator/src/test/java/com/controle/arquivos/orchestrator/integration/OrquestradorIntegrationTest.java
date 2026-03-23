package com.controle.arquivos.orchestrator.integration;

import com.controle.arquivos.common.domain.entity.*;
import com.controle.arquivos.common.domain.enums.*;
import com.controle.arquivos.common.repository.*;
import com.controle.arquivos.orchestrator.dto.MensagemProcessamento;
import com.controle.arquivos.orchestrator.service.OrquestradorService;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Testes de integração para o Orquestrador usando Testcontainers.
 * 
 * Testa o fluxo completo:
 * 1. Carregar configurações do banco de dados
 * 2. Listar arquivos em servidor SFTP
 * 3. Registrar arquivos no banco de dados
 * 4. Publicar mensagens no RabbitMQ
 * 5. Controlar concorrência com múltiplas execuções
 * 
 * **Valida: Requisitos 1.1, 2.1, 3.1, 4.1, 5.1**
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(TestConfiguration.class)
class OrquestradorIntegrationTest {

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
        
        // Vault configuration (disabled for tests)
        registry.add("app.vault.enabled", () -> "false");
        
        // SFTP configuration
        registry.add("app.sftp.timeout", () -> "30000");
        registry.add("app.sftp.session-timeout", () -> "30000");
        registry.add("app.sftp.channel-timeout", () -> "30000");
        registry.add("app.sftp.strict-host-key-checking", () -> "false");
    }

    @Autowired
    private OrquestradorService orquestradorService;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private SeverPathsRepository severPathsRepository;

    @Autowired
    private SeverPathsInOutRepository severPathsInOutRepository;

    @Autowired
    private FileOriginRepository fileOriginRepository;

    @Autowired
    private JobConcurrencyControlRepository jobConcurrencyControlRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static Session sftpSession;
    private static ChannelSftp sftpChannel;

    @BeforeAll
    static void setupSFTP() throws Exception {
        // Wait for SFTP container to be ready
        TimeUnit.SECONDS.sleep(3);
        
        // Connect to SFTP and create test files
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
        
        // Create test directory and files
        try {
            sftpChannel.mkdir("/upload/test");
        } catch (SftpException e) {
            // Directory might already exist
        }
        
        // Upload test files
        uploadTestFile("/upload/test/CIELO_20240115_001.txt", "Test file content 1");
        uploadTestFile("/upload/test/CIELO_20240115_002.txt", "Test file content 2");
        uploadTestFile("/upload/test/REDE_20240115_001.txt", "Test file content 3");
    }

    @AfterAll
    static void teardownSFTP() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (sftpSession != null && sftpSession.isConnected()) {
            sftpSession.disconnect();
        }
    }

    private static void uploadTestFile(String remotePath, String content) throws Exception {
        byte[] bytes = content.getBytes();
        sftpChannel.put(new ByteArrayInputStream(bytes), remotePath);
    }

    @BeforeEach
    void setup() {
        // Clean database before each test
        fileOriginRepository.deleteAll();
        severPathsInOutRepository.deleteAll();
        severPathsRepository.deleteAll();
        serverRepository.deleteAll();
        jobConcurrencyControlRepository.deleteAll();
    }

    /**
     * Testa o fluxo completo de orquestração:
     * 1. Carregar configurações do banco
     * 2. Listar arquivos no SFTP
     * 3. Registrar arquivos no banco
     * 4. Publicar mensagens no RabbitMQ
     * 
     * **Valida: Requisitos 1.1, 2.1, 3.1, 4.1**
     */
    @Test
    @Order(1)
    void deveExecutarFluxoCompletoDeOrquestracao() throws Exception {
        // Arrange: Setup database configuration
        Server servidorOrigem = criarServidorSFTP();
        Server servidorDestino = criarServidorS3();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        SeverPathsInOut mapeamento = criarMapeamento(caminhoOrigem.getId(), servidorDestino.getId());

        // Act: Execute collection cycle
        orquestradorService.executarCicloColeta();

        // Assert: Verify files were registered in database
        List<FileOrigin> arquivosRegistrados = fileOriginRepository.findAll();
        assertThat(arquivosRegistrados).hasSize(3);
        
        // Verify file details
        FileOrigin arquivo1 = arquivosRegistrados.stream()
                .filter(f -> f.getFileName().equals("CIELO_20240115_001.txt"))
                .findFirst()
                .orElseThrow();
        
        assertThat(arquivo1.getFileName()).isEqualTo("CIELO_20240115_001.txt");
        assertThat(arquivo1.getFileSize()).isGreaterThan(0);
        assertThat(arquivo1.getFileTimestamp()).isNotNull();
        assertThat(arquivo1.getAcquirerId()).isEqualTo(1L);
        assertThat(arquivo1.getSeverPathsInOutId()).isEqualTo(mapeamento.getId());
        assertThat(arquivo1.getActive()).isTrue();

        // Assert: Verify messages were published to RabbitMQ
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Try to receive messages from queue
            Object message = rabbitTemplate.receiveAndConvert("controle-arquivos-queue", 1000);
            assertThat(message).isNotNull();
        });

        // Assert: Verify job concurrency control
        List<JobConcurrencyControl> controles = jobConcurrencyControlRepository.findAll();
        assertThat(controles).hasSize(1);
        
        JobConcurrencyControl controle = controles.get(0);
        assertThat(controle.getJobName()).isEqualTo("ORCHESTRATOR_FILE_COLLECTION");
        assertThat(controle.getStatus()).isEqualTo("COMPLETED");
        assertThat(controle.getLastExecution()).isNotNull();
    }

    /**
     * Testa deduplicação de arquivos:
     * - Arquivos já registrados não devem ser processados novamente
     * 
     * **Valida: Requisito 2.3**
     */
    @Test
    @Order(2)
    void deveIgnorarArquivosDuplicados() {
        // Arrange: Setup configuration and register file manually
        Server servidorOrigem = criarServidorSFTP();
        Server servidorDestino = criarServidorS3();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        SeverPathsInOut mapeamento = criarMapeamento(caminhoOrigem.getId(), servidorDestino.getId());

        // Pre-register one file
        FileOrigin arquivoExistente = FileOrigin.builder()
                .fileName("CIELO_20240115_001.txt")
                .fileSize(100L)
                .fileTimestamp(Instant.now())
                .acquirerId(1L)
                .severPathsInOutId(mapeamento.getId())
                .active(true)
                .build();
        fileOriginRepository.save(arquivoExistente);

        // Act: Execute collection cycle
        orquestradorService.executarCicloColeta();

        // Assert: Only 2 new files should be registered (3 total - 1 duplicate)
        List<FileOrigin> arquivos = fileOriginRepository.findAll();
        assertThat(arquivos).hasSize(3); // 1 pre-existing + 2 new
        
        // Verify the pre-existing file was not duplicated
        long countCielo001 = arquivos.stream()
                .filter(f -> f.getFileName().equals("CIELO_20240115_001.txt"))
                .count();
        assertThat(countCielo001).isEqualTo(1);
    }

    /**
     * Testa controle de concorrência:
     * - Múltiplas execuções não devem processar simultaneamente
     * - Execução RUNNING deve bloquear novas execuções
     * 
     * **Valida: Requisitos 5.1, 5.2, 5.3**
     */
    @Test
    @Order(3)
    void deveControlarConcorrenciaDeExecucoes() {
        // Arrange: Setup configuration
        Server servidorOrigem = criarServidorSFTP();
        Server servidorDestino = criarServidorS3();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        criarMapeamento(caminhoOrigem.getId(), servidorDestino.getId());

        // Create a RUNNING job manually to simulate concurrent execution
        JobConcurrencyControl controleAtivo = JobConcurrencyControl.builder()
                .jobName("ORCHESTRATOR_FILE_COLLECTION")
                .status("RUNNING")
                .lastExecution(Instant.now())
                .active(true)
                .build();
        jobConcurrencyControlRepository.save(controleAtivo);

        // Act: Try to execute collection cycle (should be blocked)
        orquestradorService.executarCicloColeta();

        // Assert: No files should be registered (execution was blocked)
        List<FileOrigin> arquivos = fileOriginRepository.findAll();
        assertThat(arquivos).isEmpty();

        // Assert: Job control should still be RUNNING (not changed)
        List<JobConcurrencyControl> controles = jobConcurrencyControlRepository.findAll();
        assertThat(controles).hasSize(1);
        assertThat(controles.get(0).getStatus()).isEqualTo("RUNNING");
    }

    /**
     * Testa validação de configurações:
     * - Configurações inválidas devem ser ignoradas
     * - Configurações válidas devem ser processadas
     * 
     * **Valida: Requisitos 1.2, 1.3**
     */
    @Test
    @Order(4)
    void deveValidarConfiguracoes() {
        // Arrange: Create invalid configuration (missing destination server)
        Server servidorOrigem = criarServidorSFTP();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        
        SeverPathsInOut mapeamentoInvalido = SeverPathsInOut.builder()
                .severPathOriginId(caminhoOrigem.getId())
                .severDestinationId(999L) // Non-existent server
                .linkType(TipoLink.PRINCIPAL)
                .active(true)
                .build();
        severPathsInOutRepository.save(mapeamentoInvalido);

        // Act: Execute collection cycle
        orquestradorService.executarCicloColeta();

        // Assert: No files should be registered (invalid configuration)
        List<FileOrigin> arquivos = fileOriginRepository.findAll();
        assertThat(arquivos).isEmpty();

        // Assert: Job should complete successfully even with invalid config
        List<JobConcurrencyControl> controles = jobConcurrencyControlRepository.findAll();
        assertThat(controles).hasSize(1);
        assertThat(controles.get(0).getStatus()).isEqualTo("COMPLETED");
    }

    /**
     * Testa tratamento de erro de conexão SFTP:
     * - Erro de conexão não deve interromper processamento de outros servidores
     * 
     * **Valida: Requisito 2.5**
     */
    @Test
    @Order(5)
    void deveContinuarAposErroDeConexaoSFTP() {
        // Arrange: Create configuration with invalid SFTP server
        Server servidorInvalido = Server.builder()
                .serverCode("invalid-host:22")
                .vaultCode("vault-code")
                .vaultSecret("secret/path")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .active(true)
                .build();
        serverRepository.save(servidorInvalido);

        SeverPaths caminhoInvalido = SeverPaths.builder()
                .serverId(servidorInvalido.getId())
                .acquirerId(1L)
                .path("/invalid/path")
                .pathType(TipoCaminho.ORIGIN)
                .active(true)
                .build();
        severPathsRepository.save(caminhoInvalido);

        Server servidorDestino = criarServidorS3();
        
        SeverPathsInOut mapeamento = SeverPathsInOut.builder()
                .severPathOriginId(caminhoInvalido.getId())
                .severDestinationId(servidorDestino.getId())
                .linkType(TipoLink.PRINCIPAL)
                .active(true)
                .build();
        severPathsInOutRepository.save(mapeamento);

        // Act: Execute collection cycle (should handle error gracefully)
        orquestradorService.executarCicloColeta();

        // Assert: Job should complete (error was handled)
        List<JobConcurrencyControl> controles = jobConcurrencyControlRepository.findAll();
        assertThat(controles).hasSize(1);
        assertThat(controles.get(0).getStatus()).isEqualTo("COMPLETED");
    }

    // Helper methods to create test data

    private Server criarServidorSFTP() {
        String sftpHost = sftpContainer.getHost() + ":" + sftpContainer.getMappedPort(22);
        
        Server servidor = Server.builder()
                .serverCode(sftpHost)
                .vaultCode("sftp-vault")
                .vaultSecret("secret/sftp")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .active(true)
                .build();
        
        return serverRepository.save(servidor);
    }

    private Server criarServidorS3() {
        Server servidor = Server.builder()
                .serverCode("s3-bucket")
                .vaultCode("s3-vault")
                .vaultSecret("secret/s3")
                .serverType(TipoServidor.S3)
                .serverOrigin(OrigemServidor.INTERNO)
                .active(true)
                .build();
        
        return serverRepository.save(servidor);
    }

    private SeverPaths criarCaminhoOrigem(Long serverId, Long acquirerId) {
        SeverPaths caminho = SeverPaths.builder()
                .serverId(serverId)
                .acquirerId(acquirerId)
                .path("/upload/test")
                .pathType(TipoCaminho.ORIGIN)
                .active(true)
                .build();
        
        return severPathsRepository.save(caminho);
    }

    private SeverPathsInOut criarMapeamento(Long originPathId, Long destinationServerId) {
        SeverPathsInOut mapeamento = SeverPathsInOut.builder()
                .severPathOriginId(originPathId)
                .severDestinationId(destinationServerId)
                .linkType(TipoLink.PRINCIPAL)
                .active(true)
                .build();
        
        return severPathsInOutRepository.save(mapeamento);
    }
}
