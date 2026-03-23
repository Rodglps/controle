package com.controle.arquivos.integration;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Base class for integration tests with Testcontainers.
 * 
 * Sets up:
 * - Oracle XE database
 * - RabbitMQ message broker
 * - LocalStack (S3)
 * - SFTP server
 * 
 * All containers are shared across tests for performance.
 */
@Testcontainers
@SpringBootTest
public abstract class BaseIntegrationTest {

    @Container
    protected static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withDatabaseName("XEPDB1")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(true);

    @Container
    protected static final RabbitMQContainer rabbitmqContainer = new RabbitMQContainer("rabbitmq:3.12-management-alpine")
            .withReuse(true);

    @Container
    protected static final LocalStackContainer localstackContainer = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(S3)
            .withReuse(true);

    @Container
    protected static final GenericContainer<?> sftpContainer = new GenericContainer<>("atmoz/sftp:alpine")
            .withCommand("sftpuser:sftppass:1001:100:upload")
            .withExposedPorts(22)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Oracle configuration
        registry.add("spring.datasource.url", oracleContainer::getJdbcUrl);
        registry.add("spring.datasource.username", oracleContainer::getUsername);
        registry.add("spring.datasource.password", oracleContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");

        // RabbitMQ configuration
        registry.add("spring.rabbitmq.host", rabbitmqContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitmqContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");

        // LocalStack S3 configuration
        registry.add("aws.s3.endpoint", () -> localstackContainer.getEndpointOverride(S3).toString());
        registry.add("aws.s3.region", localstackContainer::getRegion);
        registry.add("aws.accessKeyId", localstackContainer::getAccessKey);
        registry.add("aws.secretAccessKey", localstackContainer::getSecretKey);

        // SFTP configuration
        registry.add("sftp.host", sftpContainer::getHost);
        registry.add("sftp.port", () -> sftpContainer.getMappedPort(22));
        registry.add("sftp.username", () -> "sftpuser");
        registry.add("sftp.password", () -> "sftppass");
    }

    @BeforeAll
    static void setUp() {
        // Containers are started automatically by Testcontainers
        // Additional setup can be done here if needed
    }
}
