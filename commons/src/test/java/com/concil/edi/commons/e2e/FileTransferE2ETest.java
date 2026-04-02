package com.concil.edi.commons.e2e;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for complete file transfer flow.
 * 
 * Tests validate the complete flow from file detection to successful transfer
 * with integrity checks, covering both SFTP-to-S3 and SFTP-to-SFTP scenarios.
 * 
 * **Validates: Requirements 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 20.9, 
 *              20.10, 20.11, 20.12, 20.13, 20.14, 20.15**
 */
public class FileTransferE2ETest extends E2ETestBase {

    /**
     * Scenario 1: SFTP to S3 Transfer
     * 
     * This test validates the complete flow:
     * 1. Upload file to SFTP origin
     * 2. Producer detects file and creates file_origin record (COLETA/EM_ESPERA)
     * 3. Producer publishes RabbitMQ message
     * 4. Consumer consumes message and updates status to PROCESSAMENTO
     * 5. Consumer downloads from SFTP via streaming
     * 6. Consumer uploads to S3 via streaming
     * 7. Consumer updates status to CONCLUIDO
     * 8. File exists in S3 with correct size and content
     * 
     * **Validates: Requirements 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 
     *              20.10, 20.11, 20.12, 20.13, 20.14**
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testSftpToS3Transfer() throws Exception {
        System.out.println("\n=== Starting E2E Test: SFTP to S3 Transfer ===\n");
        
        // Generate test file content (1MB)
        String filename = "test-file-s3-" + System.currentTimeMillis() + ".txt";
        byte[] content = generateTestContent(1024 * 1024); // 1MB
        String expectedHash = calculateSHA256(content);
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected SHA-256: " + expectedHash);
        
        // Step 1: Upload file to SFTP origin
        System.out.println("\n[Step 1] Uploading file to SFTP origin...");
        uploadToSftpOrigin(filename, content);
        System.out.println("✓ File uploaded to SFTP origin");
        
        // Step 2: Wait for Producer to detect file (max 2 minutes + processing time)
        System.out.println("\n[Step 2] Waiting for Producer to detect file...");
        Long fileOriginId = waitForFileOriginRecord(filename, 150); // 2.5 minutes
        assertNotNull(fileOriginId, "File should be registered in file_origin table");
        System.out.println("✓ File registered in file_origin with ID: " + fileOriginId);
        
        // Step 3: Validate initial file_origin record (COLETA/EM_ESPERA)
        System.out.println("\n[Step 3] Validating initial file_origin record...");
        validateFileOriginRecord(fileOriginId, "COLETA", "EM_ESPERA", content.length);
        System.out.println("✓ Initial record validated: COLETA/EM_ESPERA");
        
        // Step 4: Wait for Consumer to process (status should change to PROCESSAMENTO then CONCLUIDO)
        System.out.println("\n[Step 4] Waiting for Consumer to process file...");
        waitForFileStatus(fileOriginId, "CONCLUIDO", 120); // 2 minutes
        System.out.println("✓ File processing completed");
        
        // Step 5: Validate final file_origin record
        System.out.println("\n[Step 5] Validating final file_origin record...");
        validateFileOriginRecord(fileOriginId, "COLETA", "CONCLUIDO", content.length);
        validateAuditFields(fileOriginId);
        System.out.println("✓ Final record validated: COLETA/CONCLUIDO");
        
        // Step 6: Validate file exists in S3
        System.out.println("\n[Step 6] Validating file in S3...");
        String s3Key = S3_PREFIX + filename;
        assertTrue(fileExistsInS3(s3Key), "File should exist in S3");
        System.out.println("✓ File exists in S3: " + s3Key);
        
        // Step 7: Validate file integrity (size and content)
        System.out.println("\n[Step 7] Validating file integrity...");
        byte[] s3Content = downloadFromS3(s3Key);
        assertEquals(content.length, s3Content.length, "File size should match");
        System.out.println("✓ File size matches: " + s3Content.length + " bytes");
        
        String actualHash = calculateSHA256(s3Content);
        assertEquals(expectedHash, actualHash, "File content (SHA-256) should match");
        System.out.println("✓ File content matches (SHA-256): " + actualHash);
        
        System.out.println("\n=== E2E Test PASSED: SFTP to S3 Transfer ===\n");
    }
    

    // Helper Methods
    
    /**
     * Generate test content with random data
     */
    private byte[] generateTestContent(int sizeBytes) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        
        // Generate readable content with line breaks
        while (sb.length() < sizeBytes) {
            sb.append("Line ").append(sb.length()).append(": ");
            sb.append("Random data: ").append(random.nextInt(1000000));
            sb.append(" - Timestamp: ").append(System.currentTimeMillis());
            sb.append("\n");
        }
        
        return sb.substring(0, sizeBytes).getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Wait for file_origin record to be created
     * 
     * @param filename File name to search for
     * @param timeoutSeconds Maximum wait time in seconds
     * @return file_origin ID if found, null otherwise
     */
    private Long waitForFileOriginRecord(String filename, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
                 Statement stmt = conn.createStatement()) {
                
                String query = "SELECT idt_file_origin FROM file_origin " +
                        "WHERE des_file_name = '" + filename + "' AND flg_active = 1";
                
                ResultSet rs = stmt.executeQuery(query);
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
    private void waitForFileStatus(Long fileOriginId, String expectedStatus, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
                 Statement stmt = conn.createStatement()) {
                
                String query = "SELECT des_status FROM file_origin WHERE idt_file_origin = " + fileOriginId;
                ResultSet rs = stmt.executeQuery(query);
                
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
    private void validateFileOriginRecord(Long fileOriginId, String expectedStep, 
                                         String expectedStatus, long expectedSize) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            String query = "SELECT * FROM file_origin WHERE idt_file_origin = " + fileOriginId;
            ResultSet rs = stmt.executeQuery(query);
            
            assertTrue(rs.next(), "File origin record should exist");
            
            assertEquals(expectedStep, rs.getString("des_step"), "Step should match");
            assertEquals(expectedStatus, rs.getString("des_status"), "Status should match");
            assertEquals(expectedSize, rs.getLong("num_file_size"), "File size should match");
            assertEquals(1, rs.getInt("idt_acquirer"), "Acquirer should be 1 (MVP)");
            assertEquals(1, rs.getInt("idt_layout"), "Layout should be 1 (MVP)");
            assertEquals(1, rs.getInt("flg_active"), "Active flag should be 1");
            
            // Validate retry configuration
            assertTrue(rs.getInt("num_retry") >= 0, "Retry count should be >= 0");
            assertEquals(5, rs.getInt("max_retry"), "Max retry should be 5");
            
            // Validate timestamps
            assertNotNull(rs.getDate("dat_creation"), "Creation date should be set");
            assertNotNull(rs.getTimestamp("dat_timestamp_file"), "File timestamp should be set");
        }
    }
    
    /**
     * Validate audit fields (dat_update, nam_change_agent)
     */
    private void validateAuditFields(Long fileOriginId) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            String query = "SELECT dat_update, nam_change_agent FROM file_origin " +
                    "WHERE idt_file_origin = " + fileOriginId;
            ResultSet rs = stmt.executeQuery(query);
            
            assertTrue(rs.next(), "File origin record should exist");
            assertNotNull(rs.getDate("dat_update"), "Update date should be set after processing");
            assertNotNull(rs.getString("nam_change_agent"), "Change agent should be set after processing");
            
            System.out.println("  Audit fields validated: dat_update=" + rs.getDate("dat_update") + 
                    ", nam_change_agent=" + rs.getString("nam_change_agent"));
        }
    }
    
    // ========================================================================
    // PRESERVATION PROPERTY TESTS
    // ========================================================================
    
    /**
     * **Property 2: Preservation** - Test Scenarios and Validations Unchanged
     * 
     * These property-based tests validate that the refactoring to use docker-compose
     * containers does NOT break existing functionality. All test scenarios, validations,
     * and helper methods must continue working exactly as before.
     * 
     * **IMPORTANT**: These tests should PASS on UNFIXED code (baseline behavior)
     * and continue to PASS on FIXED code (no regressions).
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10**
     */

    /**
     * Property 2.1: SHA-256 Calculation Preservation
     * 
     * For any file content, the calculateSHA256() method should produce
     * consistent hash values with the same interface and behavior.
     * 
     * **Validates: Requirements 3.5, 3.6**
     */
    @Property(tries = 10)
    @Label("Preservation: calculateSHA256() produces consistent hashes")
    void preservation_calculateSHA256IsConsistent(
            @ForAll("testContents") byte[] content) {
        
        System.out.println("\n--- Testing calculateSHA256 with " + content.length + " bytes ---");
        
        // Calculate hash twice
        String hash1 = calculateSHA256(content);
        String hash2 = calculateSHA256(content);
        
        // Hashes should be identical
        assertEquals(hash1, hash2, "SHA-256 should be deterministic");
        
        // Hash should be 64 hex characters (256 bits = 32 bytes = 64 hex chars)
        assertEquals(64, hash1.length(), "SHA-256 hash should be 64 hex characters");
        
        System.out.println("✓ SHA-256 hash: " + hash1);
    }

    /**
     * Property 2.2: S3 File Existence Check Preservation
     * 
     * For any S3 key, the fileExistsInS3() method should correctly report
     * whether the file exists with the same interface and behavior.
     * 
     * **Validates: Requirements 3.5, 3.6**
     */
    @Property(tries = 5)
    @Label("Preservation: fileExistsInS3() correctly checks file existence")
    void preservation_fileExistsInS3Works(
            @ForAll("s3Keys") String s3Key) {
        
        System.out.println("\n--- Testing fileExistsInS3 with key: " + s3Key + " ---");
        
        // Check if file exists (should return false for non-existent files)
        boolean exists = fileExistsInS3(s3Key);
        
        // For non-existent files, should return false without throwing exception
        assertFalse(exists, "Non-existent file should return false");
        
        System.out.println("✓ fileExistsInS3 returned false for non-existent key");
    }

    /**
     * Property 2.3: Database Connection Preservation
     * 
     * For any database query, the connection using jdbcUrl, dbUsername, dbPassword
     * should work correctly with the same interface and behavior.
     * 
     * **Validates: Requirements 3.3, 3.4, 3.6**
     */
    @Property(tries = 5)
    @Label("Preservation: Database connection and queries work correctly")
    void preservation_databaseConnectionWorks() throws Exception {
        
        System.out.println("\n--- Testing database connection ---");
        
        // Connect to database
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            // Query server table
            String query = "SELECT COUNT(*) as cnt FROM server WHERE flg_active = 1";
            ResultSet rs = stmt.executeQuery(query);
            
            assertTrue(rs.next(), "Query should return results");
            int count = rs.getInt("cnt");
            
            // Should have at least the test servers
            assertTrue(count >= 3, "Should have at least 3 active servers");
            
            System.out.println("✓ Database connection works, found " + count + " active servers");
        }
    }

    /**
     * Property 2.4: File Origin Record Validation Preservation
     * 
     * For any file_origin record query, the validation logic should work
     * correctly with the same interface and behavior.
     * 
     * **Validates: Requirements 3.3, 3.4, 3.6**
     */
    @Property(tries = 5)
    @Label("Preservation: file_origin record validation works correctly")
    void preservation_fileOriginValidationWorks() throws Exception {
        
        System.out.println("\n--- Testing file_origin record validation ---");
        
        // Connect to database
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            // Query file_origin table structure
            String query = "SELECT column_name FROM user_tab_columns " +
                    "WHERE table_name = 'FILE_ORIGIN' " +
                    "ORDER BY column_id";
            ResultSet rs = stmt.executeQuery(query);
            
            // Verify key columns exist
            boolean hasStep = false;
            boolean hasStatus = false;
            boolean hasSize = false;
            boolean hasAudit = false;
            
            while (rs.next()) {
                String columnName = rs.getString("column_name");
                if ("DES_STEP".equals(columnName)) hasStep = true;
                if ("DES_STATUS".equals(columnName)) hasStatus = true;
                if ("NUM_FILE_SIZE".equals(columnName)) hasSize = true;
                if ("DAT_UPDATE".equals(columnName)) hasAudit = true;
            }
            
            assertTrue(hasStep, "file_origin should have des_step column");
            assertTrue(hasStatus, "file_origin should have des_status column");
            assertTrue(hasSize, "file_origin should have num_file_size column");
            assertTrue(hasAudit, "file_origin should have dat_update column");
            
            System.out.println("✓ file_origin table structure validated");
        }
    }

    /**
     * Property 2.5: Test Content Generation Preservation
     * 
     * For any content size, the test content generation should produce
     * files with the correct size and format.
     * 
     * **Validates: Requirements 3.9**
     */
    @Property(tries = 10)
    @Label("Preservation: Test content generation produces correct sizes")
    void preservation_testContentGenerationWorks(
            @ForAll @IntRange(min = 1024, max = 1024 * 1024) int sizeBytes) {
        
        System.out.println("\n--- Testing content generation for " + sizeBytes + " bytes ---");
        
        // Generate content
        byte[] content = generateTestContent(sizeBytes);
        
        // Verify size matches
        assertEquals(sizeBytes, content.length, "Generated content size should match requested size");
        
        // Verify content is not empty
        assertTrue(content.length > 0, "Content should not be empty");
        
        // Verify content contains readable text
        String contentStr = new String(content, StandardCharsets.UTF_8);
        assertTrue(contentStr.contains("Line "), "Content should contain line markers");
        assertTrue(contentStr.contains("Random data:"), "Content should contain random data markers");
        
        System.out.println("✓ Generated " + content.length + " bytes of test content");
    }

    /**
     * Property 2.6: Infrastructure Configuration Preservation
     * 
     * For any infrastructure setup, the configuration values should be
     * correctly initialized and accessible.
     * 
     * **Validates: Requirements 3.6, 3.7, 3.8**
     */
    @Property(tries = 5)
    @Label("Preservation: Infrastructure configuration is correctly initialized")
    void preservation_infrastructureConfigurationWorks() {
        
        System.out.println("\n--- Testing infrastructure configuration ---");
        
        // Verify SFTP configuration
        assertNotNull(SFTP_ORIGIN_USER, "SFTP origin user should be set");
        assertNotNull(SFTP_ORIGIN_PASSWORD, "SFTP origin password should be set");
        assertNotNull(SFTP_ORIGIN_PATH, "SFTP origin path should be set");
        assertTrue(SFTP_ORIGIN_PORT > 0, "SFTP origin port should be set");
        
        assertNotNull(SFTP_DEST_USER, "SFTP destination user should be set");
        assertNotNull(SFTP_DEST_PASSWORD, "SFTP destination password should be set");
        assertNotNull(SFTP_DEST_PATH, "SFTP destination path should be set");
        assertTrue(SFTP_DEST_PORT > 0, "SFTP destination port should be set");
        
        // Verify S3 configuration
        assertNotNull(S3_BUCKET, "S3 bucket should be set");
        assertNotNull(S3_PREFIX, "S3 prefix should be set");
        
        // Verify database configuration
        assertNotNull(jdbcUrl, "JDBC URL should be set");
        assertNotNull(dbUsername, "DB username should be set");
        assertNotNull(dbPassword, "DB password should be set");
        
        // Verify RabbitMQ configuration
        assertNotNull(rabbitMQHost, "RabbitMQ host should be set");
        assertNotNull(rabbitMQPort, "RabbitMQ port should be set");
        assertNotNull(rabbitMQUsername, "RabbitMQ username should be set");
        assertNotNull(rabbitMQPassword, "RabbitMQ password should be set");
        
        System.out.println("✓ All infrastructure configuration values are set");
        System.out.println("  JDBC URL: " + jdbcUrl);
        System.out.println("  RabbitMQ: " + rabbitMQHost + ":" + rabbitMQPort);
        System.out.println("  S3 Bucket: " + S3_BUCKET);
    }

    /**
     * Property 2.7: S3 Client Initialization Preservation
     * 
     * For any S3 operation, the S3 client should be correctly initialized
     * and functional.
     * 
     * **Validates: Requirements 3.5, 3.6**
     */
    @Property(tries = 5)
    @Label("Preservation: S3 client is correctly initialized and functional")
    void preservation_s3ClientWorks() {
        
        System.out.println("\n--- Testing S3 client initialization ---");
        
        // Verify S3 client is initialized
        assertNotNull(s3Client, "S3 client should be initialized");
        
        // Verify bucket exists
        try {
            var response = s3Client.listBuckets();
            boolean bucketExists = response.buckets().stream()
                    .anyMatch(b -> S3_BUCKET.equals(b.name()));
            
            assertTrue(bucketExists, "S3 bucket should exist: " + S3_BUCKET);
            
            System.out.println("✓ S3 client is functional, bucket exists: " + S3_BUCKET);
        } catch (Exception e) {
            fail("S3 client should be functional: " + e.getMessage());
        }
    }

    // ========================================================================
    // ARBITRARIES (GENERATORS) FOR PROPERTY-BASED TESTS
    // ========================================================================

    /**
     * Generate test content of various sizes
     */
    @Provide
    Arbitrary<byte[]> testContents() {
        return Arbitraries.integers()
                .between(100, 10 * 1024) // 100 bytes to 10KB
                .map(this::generateTestContent);
    }

    /**
     * Generate S3 keys
     */
    @Provide
    Arbitrary<String> s3Keys() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> S3_PREFIX + "test-" + s + ".txt");
    }
}
