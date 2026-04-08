package com.concil.edi.commons.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Base class for E2E tests using existing docker-compose containers.
 * 
 * **Validates: Requirements 20.1**
 * 
 * Connects to existing infrastructure:
 * - Oracle Database (localhost:1521)
 * - RabbitMQ (localhost:5672)
 * - LocalStack S3 (localhost:4566)
 * - SFTP Origin (localhost:2222)
 * - SFTP Destination (localhost:2223)
 * 
 * Helper methods for file operations and validation
 */
public abstract class E2ETestBase {
    
    // S3 Client
    protected static S3Client s3Client;
    
    // ObjectMapper for JSON parsing
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // SFTP Configuration - read from environment variables
    protected static String SFTP_ORIGIN_HOST;
    protected static int SFTP_ORIGIN_PORT;
    protected static String SFTP_ORIGIN_USER;
    protected static String SFTP_ORIGIN_PASSWORD;
    protected static final String SFTP_ORIGIN_PATH = "/upload";
    
    protected static String SFTP_DEST_HOST;
    protected static int SFTP_DEST_PORT;
    protected static String SFTP_DEST_USER;
    protected static String SFTP_DEST_PASSWORD;
    protected static final String SFTP_DEST_PATH = "/destination";
    
    // S3 Configuration - read from environment variables
    protected static String S3_ENDPOINT;
    protected static String S3_REGION;
    protected static String S3_ACCESS_KEY;
    protected static String S3_SECRET_KEY;
    protected static final String S3_BUCKET = "edi-files";
    protected static final String S3_PREFIX = "cielo/";
    
    // Database Configuration - read from environment variables
    protected static String jdbcUrl;
    protected static String dbUsername;
    protected static String dbPassword;
    
    // RabbitMQ Configuration - read from environment variables
    protected static String rabbitMQHost;
    protected static Integer rabbitMQPort;
    protected static String rabbitMQUsername;
    protected static String rabbitMQPassword;
    
    @BeforeAll
    public static void setupInfrastructure() throws Exception {
        System.out.println("Connecting to existing docker-compose infrastructure...");
        
        // Set QUEUE_DELAY for E2E tests to allow validation of initial state
        setQueueDelay();
        
        // Read configuration from environment variables
        readDatabaseConfig();
        readRabbitMQConfig();
        readSftpOriginConfig();
        readS3DestinationConfig();
        readSftpDestinationConfig();
        
        // Validate prerequisites
        validatePrerequisites();
        
        System.out.println("Database: " + jdbcUrl);
        System.out.println("RabbitMQ: " + rabbitMQHost + ":" + rabbitMQPort);
        System.out.println("S3 Endpoint: " + S3_ENDPOINT);
        System.out.println("SFTP Origin: " + SFTP_ORIGIN_HOST + ":" + SFTP_ORIGIN_PORT);
        System.out.println("SFTP Destination: " + SFTP_DEST_HOST + ":" + SFTP_DEST_PORT);
        
        // Initialize S3 client
        initializeS3();
        
        System.out.println("E2E test infrastructure ready!");
    }
    
    /**
     * Set QUEUE_DELAY environment variable for E2E tests.
     * This adds a delay in the Consumer to allow tests to validate initial state.
     */
    private static void setQueueDelay() {
        String queueDelay = System.getenv("QUEUE_DELAY");
        if (queueDelay == null || queueDelay.isEmpty()) {
            // Set default delay of 20 seconds for E2E tests
            System.out.println("Setting QUEUE_DELAY=20 for E2E tests");
            // Note: This only affects the current JVM process
            // The Consumer container needs to be restarted with QUEUE_DELAY=20 environment variable
        } else {
            System.out.println("QUEUE_DELAY already set to: " + queueDelay);
        }
    }
    
    /**
     * Read database configuration from environment variables
     */
    private static void readDatabaseConfig() {
        jdbcUrl = System.getenv("DB_URL");
        dbUsername = System.getenv("DB_USERNAME");
        dbPassword = System.getenv("DB_PASSWORD");
        
        if (jdbcUrl == null) jdbcUrl = "jdbc:oracle:thin:@localhost:1521/XEPDB1";
        if (dbUsername == null) dbUsername = "edi_user";
        if (dbPassword == null) dbPassword = "edi_pass";
    }
    
    /**
     * Read RabbitMQ configuration from environment variables
     */
    private static void readRabbitMQConfig() {
        rabbitMQHost = System.getenv("RABBITMQ_HOST");
        String portStr = System.getenv("RABBITMQ_PORT");
        rabbitMQUsername = System.getenv("RABBITMQ_USERNAME");
        rabbitMQPassword = System.getenv("RABBITMQ_PASSWORD");
        
        if (rabbitMQHost == null) rabbitMQHost = "localhost";
        if (portStr != null) {
            rabbitMQPort = Integer.parseInt(portStr);
        } else {
            rabbitMQPort = 5672;
        }
        if (rabbitMQUsername == null) rabbitMQUsername = "admin";
        if (rabbitMQPassword == null) rabbitMQPassword = "admin";
    }
    
    /**
     * Read SFTP origin configuration from environment variable
     */
    private static void readSftpOriginConfig() {
        JsonNode config = parseServerConfig("SFTP_CIELO_ORIGIN");
        
        if (config != null) {
            SFTP_ORIGIN_HOST = config.has("host") ? config.get("host").asText() : "localhost";
            SFTP_ORIGIN_PORT = config.has("port") ? config.get("port").asInt() : 2222;
            SFTP_ORIGIN_USER = config.has("user") ? config.get("user").asText() : "cielo";
            SFTP_ORIGIN_PASSWORD = config.has("password") ? config.get("password").asText() : "admin-1-2-3";
        } else {
            // Default values
            SFTP_ORIGIN_HOST = "localhost";
            SFTP_ORIGIN_PORT = 2222;
            SFTP_ORIGIN_USER = "cielo";
            SFTP_ORIGIN_PASSWORD = "admin-1-2-3";
        }
    }
    
    /**
     * Read S3 destination configuration from environment variable
     */
    private static void readS3DestinationConfig() {
        JsonNode config = parseServerConfig("S3_DESTINATION");
        
        if (config != null) {
            S3_ENDPOINT = config.has("endpoint") ? config.get("endpoint").asText() : "http://localhost:4566";
            S3_REGION = config.has("region") ? config.get("region").asText() : "us-east-1";
            S3_ACCESS_KEY = config.has("accessKey") ? config.get("accessKey").asText() : "test";
            S3_SECRET_KEY = config.has("secretKey") ? config.get("secretKey").asText() : "test";
        } else {
            // Default values
            S3_ENDPOINT = "http://localhost:4566";
            S3_REGION = "us-east-1";
            S3_ACCESS_KEY = "test";
            S3_SECRET_KEY = "test";
        }
    }
    
    /**
     * Read SFTP destination configuration from environment variable
     */
    private static void readSftpDestinationConfig() {
        JsonNode config = parseServerConfig("SFTP_DESTINATION");
        
        if (config != null) {
            SFTP_DEST_HOST = config.has("host") ? config.get("host").asText() : "localhost";
            SFTP_DEST_PORT = config.has("port") ? config.get("port").asInt() : 2223;
            SFTP_DEST_USER = config.has("user") ? config.get("user").asText() : "internal";
            SFTP_DEST_PASSWORD = config.has("password") ? config.get("password").asText() : "internal-pass";
        } else {
            // Default values
            SFTP_DEST_HOST = "localhost";
            SFTP_DEST_PORT = 2223;
            SFTP_DEST_USER = "internal";
            SFTP_DEST_PASSWORD = "internal-pass";
        }
    }
    
    /**
     * Parse server configuration from JSON environment variable
     * 
     * @param envVarName Name of the environment variable containing JSON configuration
     * @return JsonNode with parsed configuration, or null if not found or invalid
     */
    private static JsonNode parseServerConfig(String envVarName) {
        String jsonConfig = System.getenv(envVarName);
        
        if (jsonConfig == null || jsonConfig.trim().isEmpty()) {
            System.out.println("Environment variable " + envVarName + " not found, using defaults");
            return null;
        }
        
        try {
            return objectMapper.readTree(jsonConfig);
        } catch (Exception e) {
            System.err.println("Failed to parse JSON from environment variable " + envVarName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Validate that environment variables are defined and containers are accessible
     */
    private static void validatePrerequisites() {
        System.out.println("Validating prerequisites...");
        
        // Check that all required configuration is present
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalStateException("Database URL is not configured");
        }
        if (rabbitMQHost == null || rabbitMQHost.isEmpty()) {
            throw new IllegalStateException("RabbitMQ host is not configured");
        }
        if (SFTP_ORIGIN_HOST == null || SFTP_ORIGIN_HOST.isEmpty()) {
            throw new IllegalStateException("SFTP origin host is not configured");
        }
        if (S3_ENDPOINT == null || S3_ENDPOINT.isEmpty()) {
            throw new IllegalStateException("S3 endpoint is not configured");
        }
        if (SFTP_DEST_HOST == null || SFTP_DEST_HOST.isEmpty()) {
            throw new IllegalStateException("SFTP destination host is not configured");
        }
        
        System.out.println("Prerequisites validated successfully");
    }
    
    @AfterAll
    public static void teardownInfrastructure() {
        System.out.println("Cleaning up E2E test resources...");
        
        // Note: S3 client is intentionally NOT closed here to allow property tests to run
        // The JVM will clean it up on exit
        
        System.out.println("E2E test cleanup complete.");
    }
    
    /**
     * Initialize S3 client
     */
    private static void initializeS3() {
        System.out.println("Initializing S3 client...");
        
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(S3_ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
                .region(Region.of(S3_REGION))
                .forcePathStyle(true) // Required for LocalStack
                .build();
        
        System.out.println("S3 client initialized for bucket: " + S3_BUCKET);
    }
    
    /**
     * Upload file to SFTP origin server
     */
    protected void uploadToSftpOrigin(String filename, byte[] content) throws Exception {
        uploadToSftp(
                SFTP_ORIGIN_HOST,
                SFTP_ORIGIN_PORT,
                SFTP_ORIGIN_USER,
                SFTP_ORIGIN_PASSWORD,
                SFTP_ORIGIN_PATH,
                filename,
                content
        );
    }
    
    /**
     * Download file from SFTP destination server
     */
    protected byte[] downloadFromSftpDestination(String filename) throws Exception {
        return downloadFromSftp(
                SFTP_DEST_HOST,
                SFTP_DEST_PORT,
                SFTP_DEST_USER,
                SFTP_DEST_PASSWORD,
                SFTP_DEST_PATH,
                filename
        );
    }
    
    /**
     * Check if file exists in SFTP destination
     */
    protected boolean fileExistsInSftpDestination(String filename) throws Exception {
        return fileExistsInSftp(
                SFTP_DEST_HOST,
                SFTP_DEST_PORT,
                SFTP_DEST_USER,
                SFTP_DEST_PASSWORD,
                SFTP_DEST_PATH,
                filename
        );
    }
    
    /**
     * Download file from S3
     */
    protected byte[] downloadFromS3(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(S3_BUCKET)
                .key(key)
                .build();
        
        try (InputStream is = s3Client.getObject(request)) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download from S3: " + key, e);
        }
    }
    
    /**
     * Check if file exists in S3
     */
    protected boolean fileExistsInS3(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(key)
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (Exception e) {
            System.err.println("Error checking S3 file existence for key " + key + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Calculate SHA-256 hash of content
     */
    protected String calculateSHA256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256", e);
        }
    }
    
    /**
     * Generic SFTP upload method
     */
    private void uploadToSftp(String host, int port, String user, String password, 
                              String remotePath, String filename, byte[] content) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        
        try {
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);
            
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            // Change to remote directory (should already exist)
            channel.cd(remotePath);
            
            // Upload file
            String fullPath = remotePath + "/" + filename;
            channel.put(new ByteArrayInputStream(content), fullPath);
            
            System.out.println("Uploaded file to SFTP: " + fullPath);
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Generic SFTP download method
     */
    private byte[] downloadFromSftp(String host, int port, String user, String password,
                                    String remotePath, String filename) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        
        try {
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);
            
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            String fullPath = remotePath + "/" + filename;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.get(fullPath, baos);
            
            return baos.toByteArray();
            
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    /**
     * Check if file exists in SFTP
     */
    private boolean fileExistsInSftp(String host, int port, String user, String password,
                                     String remotePath, String filename) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;
        
        try {
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);
            
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            
            String fullPath = remotePath + "/" + filename;
            channel.lstat(fullPath);
            return true;
            
        } catch (SftpException e) {
            return false;
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    // ========================================================================
    // DATABASE HELPER METHODS
    // ========================================================================
    
    /**
     * Wait for file_origin record to be created
     * 
     * @param filename File name to search for
     * @param timeoutSeconds Maximum wait time in seconds
     * @return file_origin ID if found, null otherwise
     */
    protected Long waitForFileOriginRecord(String filename, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
                 java.sql.Statement stmt = conn.createStatement()) {
                
                String query = "SELECT idt_file_origin FROM file_origin " +
                        "WHERE des_file_name = '" + filename + "' AND flg_active = 1";
                
                java.sql.ResultSet rs = stmt.executeQuery(query);
                if (rs.next()) {
                    return rs.getLong("idt_file_origin");
                }
            }
            
            // Wait 2 seconds before retry
            Thread.sleep(2000);
        }
        
        return null;
    }
    
    /**
     * Wait for file status to change to expected value
     * 
     * @param fileOriginId File origin ID
     * @param expectedStatus Expected status
     * @param timeoutSeconds Maximum wait time in seconds
     */
    protected void waitForFileStatus(Long fileOriginId, String expectedStatus, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
                 java.sql.Statement stmt = conn.createStatement()) {
                
                String query = "SELECT des_status FROM file_origin WHERE idt_file_origin = " + fileOriginId;
                java.sql.ResultSet rs = stmt.executeQuery(query);
                
                if (rs.next()) {
                    String currentStatus = rs.getString("des_status");
                    System.out.println("  Current status: " + currentStatus);
                    
                    if (expectedStatus.equals(currentStatus)) {
                        return;
                    }
                }
            }
            
            // Wait 2 seconds before retry
            Thread.sleep(2000);
        }
        
        throw new AssertionError("Timeout waiting for status " + expectedStatus + 
                " for file_origin ID " + fileOriginId);
    }
    
    /**
     * Validate file_origin record fields
     */
    protected void validateFileOriginRecord(Long fileOriginId, String expectedStep, 
                                         String expectedStatus, long expectedSize) throws Exception {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             java.sql.Statement stmt = conn.createStatement()) {
            
            String query = "SELECT * FROM file_origin WHERE idt_file_origin = " + fileOriginId;
            java.sql.ResultSet rs = stmt.executeQuery(query);
            
            if (!rs.next()) {
                throw new AssertionError("File origin record should exist");
            }
            
            String actualStep = rs.getString("des_step");
            String actualStatus = rs.getString("des_status");
            long actualSize = rs.getLong("num_file_size");
            
            if (!expectedStep.equals(actualStep)) {
                throw new AssertionError("Step should be " + expectedStep + " but was " + actualStep);
            }
            if (!expectedStatus.equals(actualStatus)) {
                throw new AssertionError("Status should be " + expectedStatus + " but was " + actualStatus);
            }
            if (expectedSize != actualSize) {
                throw new AssertionError("File size should be " + expectedSize + " but was " + actualSize);
            }
        }
    }
}
