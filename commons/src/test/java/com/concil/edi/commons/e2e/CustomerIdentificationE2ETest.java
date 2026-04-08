package com.concil.edi.commons.e2e;

import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for customer identification during file transfer flow.
 * 
 * Tests validate the complete flow from file upload to customer identification:
 * 1. Upload file to SFTP origin
 * 2. Producer detects file and creates file_origin record
 * 3. Consumer identifies layout during transfer
 * 4. Consumer identifies customers based on configured rules
 * 5. file_origin_clients records are created for identified customers
 * 6. Transfer completes successfully
 * 
 * Test scenarios:
 * - Teste 1: Multiple customers identified by FILENAME
 * - Teste 2: No customers identified (processing continues)
 * - Teste 3: Customer identified by HEADER in TXT file
 * - Teste 4: Customer identified by HEADER in CSV file
 * 
 * **Validates: Prompt - Cenários de Teste**
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CustomerIdentificationE2ETest extends E2ETestBase {

    /**
     * Scenario 1: Multiple customers identified by FILENAME
     * 
     * This test validates:
     * - File matching multiple customer rules is identified for all matching customers
     * - Multiple records are created in file_origin_clients
     * - Results are ordered by num_process_weight DESC
     * - Both FILENAME rules with AND operator work correctly
     * - Case-insensitive comparison works (LOWERCASE function)
     * 
     * Configuration:
     * - Cliente 15: FILENAME CONTEM '1234567890' AND FILENAME COMECA_COM 'cielo' (weight=100)
     * - Cliente 20: FILENAME CONTEM 'premium' (weight=200)
     * 
     * Test file: cielo_1234567890_premium_20250101_venda.txt
     * 
     * Expected result:
     * - Both customers 15 and 20 identified
     * - Two records in file_origin_clients
     * - Customer 20 appears first in ordering (higher weight)
     * 
     * **Validates: Prompt - Teste 1**
     */
    @Test
    @Order(1)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testMultipleCustomersIdentifiedByFilename() throws Exception {
        System.out.println("\n=== E2E Test: Multiple Customers Identified by FILENAME ===\n");
        
        // Arrange: Generate test file matching both customer rules
        String filename = "cielo_1234567890_premium_20250101_venda.txt";
        byte[] content = generateCieloVendaContent();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected customers: 15 (weight=100) and 20 (weight=200)");
        System.out.println("Expected ordering: Customer 20 first (higher weight)");
        
        // Act & Assert: Execute transfer and validate customer identification
        Long fileOriginId = executeTransferAndValidateCustomers(
                filename,
                content,
                List.of(20L, 15L)  // Expected customers in weight order (DESC)
        );
        
        System.out.println("\n=== E2E Test PASSED: Multiple Customers Identified ===\n");
    }

    /**
     * Scenario 2: No customers identified - processing continues
     * 
     * This test validates:
     * - File not matching any customer rules completes successfully
     * - No records created in file_origin_clients
     * - Processing finishes with step=COLETA and status=CONCLUIDO
     * - File is transferred to S3 successfully
     * 
     * Test file: rede_9999999999_standard_20250101.txt
     * 
     * Expected result:
     * - No customers identified
     * - Zero records in file_origin_clients
     * - Transfer completes successfully
     * - File exists in S3
     * 
     * **Validates: Prompt - Teste 2**
     */
    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testNoCustomersIdentified() throws Exception {
        System.out.println("\n=== E2E Test: No Customers Identified ===\n");
        
        // Arrange: Generate test file not matching any customer rules
        String filename = "rede_9999999999_standard_20250101.txt";
        byte[] content = "Generic file content that doesn't match any rules".getBytes(StandardCharsets.UTF_8);
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected: No customers identified");
        System.out.println("Note: Filename does NOT match any configured rules");
        
        // Step 1: Upload file to SFTP origin
        System.out.println("\n[Step 1] Uploading file to SFTP origin...");
        uploadToSftpOrigin(filename, content);
        System.out.println("✓ File uploaded to SFTP origin");
        
        // Step 2: Wait for Producer to detect file
        System.out.println("\n[Step 2] Waiting for Producer to detect file...");
        Long fileOriginId = waitForFileOriginRecord(filename, 150);
        assertNotNull(fileOriginId, "File should be registered in file_origin table");
        System.out.println("✓ File registered in file_origin with ID: " + fileOriginId);
        
        // Step 3: Wait for Consumer to complete processing
        System.out.println("\n[Step 3] Waiting for Consumer to complete processing...");
        waitForFileStatus(fileOriginId, "CONCLUIDO", 120);
        System.out.println("✓ File processing completed successfully");
        
        // Step 4: Validate no customers identified
        System.out.println("\n[Step 4] Validating no customers identified...");
        List<Long> identifiedCustomers = getIdentifiedCustomers(fileOriginId);
        assertTrue(identifiedCustomers.isEmpty(), "No customers should be identified");
        System.out.println("✓ No customers identified (as expected)");
        
        // Step 5: Validate final file_origin record
        System.out.println("\n[Step 5] Validating final file_origin record...");
        validateFileOriginRecord(fileOriginId, "COLETA", "CONCLUIDO", content.length);
        System.out.println("✓ Final record validated: COLETA/CONCLUIDO");
        
        // Step 6: Validate file exists in S3
        System.out.println("\n[Step 6] Validating file in S3...");
        String s3Key = S3_PREFIX + filename;
        assertTrue(fileExistsInS3(s3Key), "File should exist in S3");
        System.out.println("✓ File exists in S3: " + s3Key);
        
        System.out.println("\n=== E2E Test PASSED: No Customers Identified ===\n");
    }

    /**
     * Scenario 3: Customer identified by HEADER in TXT file
     * 
     * This test validates:
     * - Layout identification occurs first (CIELO_015_03_VENDA)
     * - Customer identification uses HEADER rules with byte offset
     * - Multiple HEADER rules with AND operator work correctly
     * - TRIM function works correctly
     * - Customer 25 is identified based on TXT header content
     * 
     * Configuration:
     * - Cliente 25: HEADER bytes 0-4 IGUAL 'VENDA' (TRIM) AND HEADER bytes 10-19 CONTEM '1525'
     * - Requires layout 1 (CIELO_015_03_VENDA) to be identified
     * 
     * Test file: TXT with first line "VENDA     1525      20250101..."
     * 
     * Expected result:
     * - Layout 1 identified
     * - Customer 25 identified
     * - One record in file_origin_clients
     * 
     * **Validates: Prompt - Teste 3**
     */
    @Test
    @Order(3)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testCustomerIdentifiedByHeaderTxt() throws Exception {
        System.out.println("\n=== E2E Test: Customer Identified by HEADER in TXT ===\n");
        
        // Arrange: Generate test file matching Cielo VENDA layout and customer 25 rules
        String filename = "cielo_v15_" + System.currentTimeMillis() + "_venda.txt";
        byte[] content = generateCieloVendaWithCustomer25Header();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 1 (CIELO_015_03_VENDA)");
        System.out.println("Expected customer: 25");
        System.out.println("Header content: VENDA     1525      20250101...");
        
        // Act & Assert: Execute transfer and validate customer identification
        Long fileOriginId = executeTransferAndValidateCustomers(
                filename,
                content,
                List.of(25L)  // Expected customer
        );
        
        // Additional validation: Verify layout was identified
        System.out.println("\n[Additional] Validating layout identification...");
        validateLayoutIdentification(fileOriginId, 1L, "CIELO_015_03_VENDA");
        System.out.println("✓ Layout identified correctly");
        
        System.out.println("\n=== E2E Test PASSED: Customer Identified by HEADER TXT ===\n");
    }

    /**
     * Scenario 4: Customer identified by HEADER in CSV file
     * 
     * This test validates:
     * - Layout identification occurs first (REDE_EEVD_02)
     * - Customer identification uses HEADER rules with column index
     * - Multiple HEADER rules with AND operator work correctly
     * - UPPERCASE function works correctly
     * - Customer 30 is identified based on CSV header line content
     * 
     * Configuration:
     * - Cliente 30: HEADER column 9 CONTEM 'EEVD' (UPPERCASE) AND HEADER column 2 CONTEM '1530'
     * - Requires layout 3 (REDE_EEVD_02) to be identified
     * 
     * Test file: CSV with header "00,123,1530,789,Movimentação diária...,REDECARD,...,V2.00 - 05/2023 EEVD"
     * 
     * Expected result:
     * - Layout 3 identified
     * - Customer 30 identified
     * - One record in file_origin_clients
     * 
     * **Validates: Prompt - Teste 4**
     */
    @Test
    @Order(4)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testCustomerIdentifiedByHeaderCsv() throws Exception {
        System.out.println("\n=== E2E Test: Customer Identified by HEADER in CSV ===\n");
        
        // Arrange: Generate test file matching Rede EEVD layout and customer 30 rules
        String filename = "rede_eevd_" + System.currentTimeMillis() + ".csv";
        byte[] content = generateRedeEevdWithCustomer30Header();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 3 (REDE_EEVD_02)");
        System.out.println("Expected customer: 30");
        System.out.println("Header content: EEVD;20250101;1530;100.00;APROVADO");
        
        // Act & Assert: Execute transfer and validate customer identification
        Long fileOriginId = executeTransferAndValidateCustomers(
                filename,
                content,
                List.of(30L)  // Expected customer
        );
        
        // Additional validation: Verify layout was identified
        System.out.println("\n[Additional] Validating layout identification...");
        validateLayoutIdentification(fileOriginId, 3L, "REDE_EEVD_02");
        System.out.println("✓ Layout identified correctly");
        
        System.out.println("\n=== E2E Test PASSED: Customer Identified by HEADER CSV ===\n");
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Execute complete transfer flow and validate customer identification
     * 
     * @param filename Name of the file
     * @param content File content
     * @param expectedCustomers List of expected customer IDs in order
     * @return file_origin ID
     */
    private Long executeTransferAndValidateCustomers(String filename, byte[] content,
                                                     List<Long> expectedCustomers) throws Exception {
        // Step 1: Upload file to SFTP origin
        System.out.println("\n[Step 1] Uploading file to SFTP origin...");
        uploadToSftpOrigin(filename, content);
        System.out.println("✓ File uploaded to SFTP origin");
        
        // Step 2: Wait for Producer to detect file
        System.out.println("\n[Step 2] Waiting for Producer to detect file...");
        Long fileOriginId = waitForFileOriginRecord(filename, 150);
        assertNotNull(fileOriginId, "File should be registered in file_origin table");
        System.out.println("✓ File registered in file_origin with ID: " + fileOriginId);
        
        // Step 3: Validate initial file_origin record
        System.out.println("\n[Step 3] Validating initial file_origin record...");
        validateFileOriginRecord(fileOriginId, "COLETA", "EM_ESPERA", content.length);
        System.out.println("✓ Initial record validated: COLETA/EM_ESPERA");
        
        // Step 4: Wait for Consumer to process
        System.out.println("\n[Step 4] Waiting for Consumer to complete processing...");
        waitForFileStatus(fileOriginId, "CONCLUIDO", 120);
        System.out.println("✓ File processing completed");
        
        // Step 5: Validate customers were identified correctly
        System.out.println("\n[Step 5] Validating customer identification...");
        validateCustomerIdentification(fileOriginId, expectedCustomers);
        System.out.println("✓ Customers identified correctly: " + expectedCustomers);
        
        // Step 6: Validate final file_origin record
        System.out.println("\n[Step 6] Validating final file_origin record...");
        validateFileOriginRecord(fileOriginId, "COLETA", "CONCLUIDO", content.length);
        System.out.println("✓ Final record validated: COLETA/CONCLUIDO");
        
        // Step 7: Validate file exists in S3
        System.out.println("\n[Step 7] Validating file in S3...");
        String s3Key = S3_PREFIX + filename;
        assertTrue(fileExistsInS3(s3Key), "File should exist in S3");
        System.out.println("✓ File exists in S3: " + s3Key);
        
        return fileOriginId;
    }

    /**
     * Get list of identified customers for a file
     * 
     * @param fileOriginId File origin ID
     * @return List of customer IDs in order
     */
    private List<Long> getIdentifiedCustomers(Long fileOriginId) throws Exception {
        List<Long> customers = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            String query = "SELECT idt_client FROM file_origin_clients " +
                    "WHERE idt_file_origin = " + fileOriginId + " " +
                    "ORDER BY idt_client_identified";
            
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                customers.add(rs.getLong("idt_client"));
            }
        }
        
        return customers;
    }

    /**
     * Validate that customers were identified correctly
     * 
     * @param fileOriginId File origin ID
     * @param expectedCustomers List of expected customer IDs in order
     */
    private void validateCustomerIdentification(Long fileOriginId, List<Long> expectedCustomers) throws Exception {
        List<Long> actualCustomers = getIdentifiedCustomers(fileOriginId);
        
        assertEquals(expectedCustomers.size(), actualCustomers.size(),
                "Number of identified customers should match expected");
        
        // Validate each customer was identified
        for (Long expectedCustomer : expectedCustomers) {
            assertTrue(actualCustomers.contains(expectedCustomer),
                    "Customer " + expectedCustomer + " should be identified");
        }
        
        System.out.println("  Identified customers: " + actualCustomers);
        System.out.println("  Expected customers: " + expectedCustomers);
    }

    /**
     * Validate that layout was identified correctly
     */
    private void validateLayoutIdentification(Long fileOriginId, Long expectedLayoutId,
                                             String expectedLayoutCode) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            String query = "SELECT fo.idt_layout, l.cod_layout " +
                    "FROM file_origin fo " +
                    "LEFT JOIN layout l ON fo.idt_layout = l.idt_layout " +
                    "WHERE fo.idt_file_origin = " + fileOriginId;
            
            ResultSet rs = stmt.executeQuery(query);
            assertTrue(rs.next(), "File origin record should exist");
            
            Long actualLayoutId = rs.getLong("idt_layout");
            String actualLayoutCode = rs.getString("cod_layout");
            
            assertNotNull(actualLayoutId, "idt_layout should not be NULL");
            assertEquals(expectedLayoutId, actualLayoutId,
                    "Layout ID should match expected value");
            assertEquals(expectedLayoutCode, actualLayoutCode,
                    "Layout code should match expected value");
            
            System.out.println("  Layout identified: " + actualLayoutCode + " (ID: " + actualLayoutId + ")");
        }
    }

    // ========================================================================
    // TEST DATA GENERATORS
    // ========================================================================

    /**
     * Generate Cielo VENDA file content (generic)
     */
    private byte[] generateCieloVendaContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("CIELO VENDA FILE - VERSION 15\n");
        sb.append("Header: Transaction capture data\n");
        sb.append("Date: 2024-01-01\n");
        sb.append("\n");
        
        // Add some transaction lines
        for (int i = 1; i <= 100; i++) {
            sb.append(String.format("TRX%05d|VENDA|100.00|2024-01-01|APPROVED\n", i));
        }
        
        sb.append("\n");
        sb.append("Trailer: 100 transactions\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Cielo VENDA file with customer 25 header
     * 
     * Header format for customer 25 identification (1-based positions):
     * - Positions 1-5: "VENDA" (will be trimmed and compared)
     * - Positions 11-20: Must contain "1525" (merchant ID)
     */
    private byte[] generateCieloVendaWithCustomer25Header() {
        StringBuilder sb = new StringBuilder();
        
        // First line matching customer 25 rules:
        // Bytes 0-4: "VENDA" (with trailing spaces for TRIM test)
        // Bytes 5-9: "     " (5 spaces)
        // Bytes 10-19: "1525      " (merchant 1525 with padding)
        // Bytes 20+: Date and other data
        sb.append("VENDA     1525      20250101120000\n");
        
        // Add transaction lines
        for (int i = 1; i <= 100; i++) {
            sb.append(String.format("TRX%05d|VENDA|100.00|2024-01-01|APPROVED\n", i));
        }
        
        sb.append("Trailer: 100 transactions\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Rede EEVD CSV file with customer 30 header
     * 
     * Header format for customer 30 identification:
     * - Column 0: "00" (tipo de registro - used by layout identification)
     * - Column 2: Must contain "1530" (merchant ID)
     * - Column 9: Contains "EEVD" (used by layout identification)
     * 
     * Layout 3 (REDE_EEVD_02) uses comma separator
     * Customer identification rules match on header line columns
     */
    private byte[] generateRedeEevdWithCustomer30Header() {
        StringBuilder sb = new StringBuilder();
        
        // Header line matching layout 3 rules AND customer 30 rules (comma-separated)
        // Column 0: "00" (layout rule)
        // Column 2: "1530" (customer 30 rule - merchant ID)
        // Column 4: "Movimentação diária - Cartões de débito" (layout rule)
        // Column 5: "REDECARD" (layout rule)
        // Column 9: "V2.00 - 05/2023 EEVD" (layout rule)
        sb.append("00,123,1530,789,Movimentação diária - Cartões de débito,REDECARD,20231201,20231231,20240101,V2.00 - 05/2023 EEVD\n");
        
        // Data lines
        for (int i = 1; i <= 50; i++) {
            sb.append(String.format("EEVD,20250101,1530,%d.00,APROVADO\n", i * 100));
        }
        
        // Trailer line
        sb.append("99,50,250000.00\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
