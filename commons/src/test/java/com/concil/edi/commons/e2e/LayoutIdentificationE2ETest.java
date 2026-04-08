package com.concil.edi.commons.e2e;

import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for layout identification during file transfer flow.
 * 
 * Tests validate the complete flow from file upload to layout identification:
 * 1. Upload file to SFTP origin
 * 2. Producer detects file and creates file_origin record
 * 3. Consumer identifies layout during transfer
 * 4. file_origin.idt_layout is updated with identified layout
 * 5. Transfer completes successfully or fails if layout not identified
 * 
 * **Validates: Requirements 18.1, 18.2, 18.3, 18.4, 18.5**
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LayoutIdentificationE2ETest extends E2ETestBase {

    /**
     * Scenario 1: Cielo VENDA file identified by FILENAME
     * 
     * This test validates:
     * - File with name matching "cielo_v15_venda" pattern is identified as Layout 1
     * - Layout identification occurs during transfer
     * - file_origin.idt_layout is updated correctly
     * - Transfer completes successfully
     * 
     * **Validates: Requirements 18.1**
     */
    @Test
    @Order(1)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testCieloVendaIdentifiedByFilename() throws Exception {
        System.out.println("\n=== E2E Test: Cielo VENDA Identified by FILENAME ===\n");
        
        // Arrange: Generate test file matching Cielo VENDA pattern
        String filename = "cielo_v15_" + System.currentTimeMillis() + "_venda.txt";
        byte[] content = generateCieloVendaContent();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 1 (CIELO_015_03_VENDA)");
        
        // Act & Assert: Execute transfer and validate layout identification
        Long fileOriginId = executeTransferAndValidateLayout(
                filename, 
                content, 
                1L,  // Expected layout ID
                "CIELO_015_03_VENDA"
        );
        
        System.out.println("\n=== E2E Test PASSED: Cielo VENDA Identified ===\n");
    }

    /**
     * Scenario 2: Cielo PAGTO file identified by FILENAME
     * 
     * This test validates:
     * - File with name matching "cielo_v15_pagto" pattern is identified as Layout 2
     * - Layout identification distinguishes between VENDA and PAGTO
     * - file_origin.idt_layout is updated correctly
     * - Transfer completes successfully
     * 
     * **Validates: Requirements 18.1**
     */
    @Test
    @Order(2)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testCieloPagtoIdentifiedByFilename() throws Exception {
        System.out.println("\n=== E2E Test: Cielo PAGTO Identified by FILENAME ===\n");
        
        // Arrange: Generate test file matching Cielo PAGTO pattern
        String filename = "cielo_v15_" + System.currentTimeMillis() + "_pagto.txt";
        byte[] content = generateCieloPagtoContent();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 2 (CIELO_015_04_PAGTO)");
        
        // Act & Assert: Execute transfer and validate layout identification
        Long fileOriginId = executeTransferAndValidateLayout(
                filename, 
                content, 
                2L,  // Expected layout ID
                "CIELO_015_04_PAGTO"
        );
        
        System.out.println("\n=== E2E Test PASSED: Cielo PAGTO Identified ===\n");
    }

    /**
     * Scenario 3: Rede EEVD CSV file identified by HEADER
     * 
     * This test validates:
     * - CSV file with header matching Rede EEVD pattern is identified as Layout 3
     * - Layout identification uses CSV column extraction
     * - Multiple header rules are applied with AND operator
     * - file_origin.idt_layout is updated correctly
     * - Transfer completes successfully
     * 
     * **Validates: Requirements 18.2**
     */
    @Test
    @Order(3)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testRedeEevdCsvIdentifiedByHeader() throws Exception {
        System.out.println("\n=== E2E Test: Rede EEVD CSV Identified by HEADER ===\n");
        
        // Arrange: Generate test file with Rede EEVD CSV header
        String filename = "rede_eevd_" + System.currentTimeMillis() + ".csv";
        byte[] content = generateRedeEevdCsvContent();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 3 (REDE_EEVD_02)");
        
        // Act & Assert: Execute transfer and validate layout identification
        Long fileOriginId = executeTransferAndValidateLayout(
                filename, 
                content, 
                3L,  // Expected layout ID
                "REDE_EEVD_02"
        );
        
        System.out.println("\n=== E2E Test PASSED: Rede EEVD CSV Identified ===\n");
    }

    /**
     * Scenario 4: Rede EEVC TXT file identified by HEADER
     * 
     * This test validates:
     * - TXT file with header matching Rede EEVC pattern is identified as Layout 4
     * - Layout identification uses byte offset extraction
     * - Multiple header rules are applied with AND operator
     * - file_origin.idt_layout is updated correctly
     * - Transfer completes successfully
     * 
     * **Validates: Requirements 18.3**
     */
    @Test
    @Order(4)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testRedeEevcTxtIdentifiedByHeader() throws Exception {
        System.out.println("\n=== E2E Test: Rede EEVC TXT Identified by HEADER ===\n");
        
        // Arrange: Generate test file with Rede EEVC TXT header
        String filename = "rede_eevc_" + System.currentTimeMillis() + ".txt";
        byte[] content = generateRedeEevcTxtContent();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 4 (REDE_EEVC_03)");
        
        // Act & Assert: Execute transfer and validate layout identification
        Long fileOriginId = executeTransferAndValidateLayout(
                filename, 
                content, 
                4L,  // Expected layout ID
                "REDE_EEVC_03"
        );
        
        System.out.println("\n=== E2E Test PASSED: Rede EEVC TXT Identified ===\n");
    }

    /**
     * Scenario 5: Rede EEFI TXT file identified by HEADER
     * 
     * This test validates:
     * - TXT file with header matching Rede EEFI pattern is identified as Layout 5
     * - Layout identification distinguishes between EEVC and EEFI
     * - Multiple header rules are applied with AND operator
     * - file_origin.idt_layout is updated correctly
     * - Transfer completes successfully
     * 
     * **Validates: Requirements 18.3**
     */
    @Test
    @Order(5)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testRedeEefiTxtIdentifiedByHeader() throws Exception {
        System.out.println("\n=== E2E Test: Rede EEFI TXT Identified by HEADER ===\n");
        
        // Arrange: Generate test file with Rede EEFI TXT header
        String filename = "rede_eefi_" + System.currentTimeMillis() + ".txt";
        byte[] content = generateRedeEefiTxtContent();
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected layout: 5 (REDE_EEFI_04)");
        
        // Act & Assert: Execute transfer and validate layout identification
        Long fileOriginId = executeTransferAndValidateLayout(
                filename, 
                content, 
                5L,  // Expected layout ID
                "REDE_EEFI_04"
        );
        
        System.out.println("\n=== E2E Test PASSED: Rede EEFI TXT Identified ===\n");
    }

    /**
     * Scenario 6: File with no matching layout - uses Layout 0 (SEM_IDENTIFICACAO)
     * 
     * This test validates:
     * - File that doesn't match any layout rules is assigned Layout 0
     * - Transfer completes successfully even without layout identification
     * - file_origin.des_status is set to CONCLUIDO
     * - file_origin.idt_layout is set to 0 (SEM_IDENTIFICACAO)
     * - File is transferred to S3 successfully
     * 
     * **Validates: Requirements 18.4, 18.5**
     */
    @Test
    @Order(6)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public void testUnidentifiedLayoutUsesLayout0() throws Exception {
        System.out.println("\n=== E2E Test: Unidentified Layout Uses Layout 0 ===\n");
        
        // Arrange: Generate test file that doesn't match any layout rules
        // This filename does NOT follow the cielo_v15_<timestamp>_<tipo> pattern
        // and does NOT match any Rede patterns either
        String filename = "arquivo_desconhecido_" + System.currentTimeMillis() + ".txt";
        byte[] content = "Conteúdo de arquivo que não corresponde a nenhum layout".getBytes(StandardCharsets.UTF_8);
        
        System.out.println("Test file: " + filename);
        System.out.println("Content size: " + content.length + " bytes");
        System.out.println("Expected: Layout 0 (SEM_IDENTIFICACAO)");
        System.out.println("Note: Filename does NOT match cielo_v15_<timestamp>_<tipo> pattern");
        
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
        
        // Step 4: Validate file_origin record has Layout 0
        System.out.println("\n[Step 4] Validating Layout 0 assignment...");
        validateLayout0Assignment(fileOriginId);
        System.out.println("✓ Layout 0 assignment validated");
        
        // Step 5: Validate file exists in S3
        System.out.println("\n[Step 5] Validating file in S3...");
        String s3Key = S3_PREFIX + filename;
        assertTrue(fileExistsInS3(s3Key), "File should exist in S3");
        System.out.println("✓ File exists in S3: " + s3Key);
        
        System.out.println("\n=== E2E Test PASSED: Unidentified Layout Uses Layout 0 ===\n");
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Execute complete transfer flow and validate layout identification
     * 
     * @param filename Name of the file
     * @param content File content
     * @param expectedLayoutId Expected layout ID
     * @param expectedLayoutCode Expected layout code
     * @return file_origin ID
     */
    private Long executeTransferAndValidateLayout(String filename, byte[] content,
                                                  Long expectedLayoutId, String expectedLayoutCode) throws Exception {
        // Step 1: Upload file to SFTP origin
        System.out.println("\n[Step 1] Uploading file to SFTP origin...");
        uploadToSftpOrigin(filename, content);
        System.out.println("✓ File uploaded to SFTP origin");
        
        // Step 2: Wait for Producer to detect file
        System.out.println("\n[Step 2] Waiting for Producer to detect file...");
        Long fileOriginId = waitForFileOriginRecord(filename, 150);
        assertNotNull(fileOriginId, "File should be registered in file_origin table");
        System.out.println("✓ File registered in file_origin with ID: " + fileOriginId);
        
        // Step 3: Validate initial file_origin record (COLETA/EM_ESPERA, idt_layout should be NULL initially)
        System.out.println("\n[Step 3] Validating initial file_origin record...");
        validateFileOriginRecord(fileOriginId, "COLETA", "EM_ESPERA", content.length);
        System.out.println("✓ Initial record validated: COLETA/EM_ESPERA");
        
        // Step 4: Wait for Consumer to process and identify layout
        System.out.println("\n[Step 4] Waiting for Consumer to process and identify layout...");
        waitForFileStatus(fileOriginId, "CONCLUIDO", 120);
        System.out.println("✓ File processing completed");
        
        // Step 5: Validate layout was identified correctly
        System.out.println("\n[Step 5] Validating layout identification...");
        validateLayoutIdentification(fileOriginId, expectedLayoutId, expectedLayoutCode);
        System.out.println("✓ Layout identified correctly: " + expectedLayoutCode + " (ID: " + expectedLayoutId + ")");
        
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

    /**
     * Validate that layout 0 (SEM_IDENTIFICACAO) was assigned for unidentified files
     */
    private void validateLayout0Assignment(Long fileOriginId) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            
            String query = "SELECT fo.des_step, fo.des_status, fo.idt_layout, l.cod_layout " +
                    "FROM file_origin fo " +
                    "LEFT JOIN layout l ON fo.idt_layout = l.idt_layout " +
                    "WHERE fo.idt_file_origin = " + fileOriginId;
            
            ResultSet rs = stmt.executeQuery(query);
            assertTrue(rs.next(), "File origin record should exist");
            
            String step = rs.getString("des_step");
            String status = rs.getString("des_status");
            Long layoutId = rs.getLong("idt_layout");
            String layoutCode = rs.getString("cod_layout");
            
            assertEquals("COLETA", step, "Step should be COLETA");
            assertEquals("CONCLUIDO", status, "Status should be CONCLUIDO");
            assertEquals(0L, layoutId, "idt_layout should be 0 for unidentified files");
            assertEquals("SEM_IDENTIFICACAO", layoutCode, 
                    "Layout code should be SEM_IDENTIFICACAO");
            
            System.out.println("  Transfer completed successfully: COLETA/CONCLUIDO");
            System.out.println("  Layout assigned: " + layoutCode + " (ID: " + layoutId + ")");
        }
    }

    // ========================================================================
    // TEST DATA GENERATORS
    // ========================================================================

    /**
     * Generate Cielo VENDA file content
     * Matches layout 1 rules:
     * - Filename ends with "venda"
     * - Filename starts with "cielo"
     * - Filename contains "v15"
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
     * Generate Cielo PAGTO file content
     * Matches layout 2 rules:
     * - Filename ends with "pagto"
     * - Filename starts with "cielo"
     * - Filename contains "v15"
     */
    private byte[] generateCieloPagtoContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("CIELO PAGAMENTO FILE - VERSION 15\n");
        sb.append("Header: Financial settlement data\n");
        sb.append("Date: 2024-01-01\n");
        sb.append("\n");
        
        // Add some payment lines
        for (int i = 1; i <= 50; i++) {
            sb.append(String.format("PAY%05d|PAGTO|5000.00|2024-01-01|SETTLED\n", i));
        }
        
        sb.append("\n");
        sb.append("Trailer: 50 payments\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Rede EEVD CSV file content
     * Matches layout 3 rules:
     * - Column 0 = "00" (tipo de registro)
     * - Column 4 = "Movimentação diária - Cartões de débito"
     * - Column 5 = "REDECARD"
     * - Column 9 = "V2.00 - 05/2023 EEVD"
     */
    private byte[] generateRedeEevdCsvContent() {
        StringBuilder sb = new StringBuilder();
        
        // Header line matching the rules
        sb.append("00,123,456,789,Movimentação diária - Cartões de débito,REDECARD,20231201,20231231,20240101,V2.00 - 05/2023 EEVD\n");
        
        // Add some data lines
        for (int i = 1; i <= 100; i++) {
            sb.append(String.format("01,%d,DEBIT,100.00,APPROVED,2024-01-01\n", i));
        }
        
        // Trailer line
        sb.append("99,100,10000.00\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Rede EEVC TXT file content
     * Matches layout 4 rules (1-based positions):
     * - Positions 1-3 = "002" (tipo de registro)
     * - Positions 12-19 = "REDECARD"
     * - Positions 22-51 = "EXTRATO DE MOVIMENTO DE VENDAS"
     * - Positions 110-129 = "V3.00 - 05/2023 EEVC"
     */
    private byte[] generateRedeEevcTxtContent() {
        StringBuilder sb = new StringBuilder();
        
        // Header line with exact byte positions (1-based)
        // Positions 1-3: "002" (3 chars)
        sb.append("002");
        sb.append("        "); // 8 spaces → positions 4-11
        
        // Positions 12-19: "REDECARD" (8 chars)
        sb.append("REDECARD");
        sb.append("  "); // 2 spaces → positions 20-21
        
        // Positions 22-51: "EXTRATO DE MOVIMENTO DE VENDAS" (30 chars)
        sb.append("EXTRATO DE MOVIMENTO DE VENDAS");
        sb.append("                                                          "); // 58 spaces → positions 52-109
        
        // Positions 110-129: "V3.00 - 05/2023 EEVC" (20 chars)
        sb.append("V3.00 - 05/2023 EEVC");
        sb.append("\n");
        
        // Add some data lines
        for (int i = 1; i <= 100; i++) {
            sb.append(String.format("010%-100s\n", "Transaction " + i + " data"));
        }
        
        // Trailer line
        sb.append("999" + " ".repeat(100) + "\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate Rede EEFI TXT file content
     * Matches layout 5 rules (1-based positions):
     * - Positions 1-3 = "030" (tipo de registro)
     * - Positions 12-19 = "REDECARD"
     * - Positions 22-55 = "EXTRATO DE MOVIMENTACAO FINANCEIRA"
     * - Positions 108-127 = "V4.00 - 05/2023 EEFI"
     */
    private byte[] generateRedeEefiTxtContent() {
        StringBuilder sb = new StringBuilder();
        
        // Header line with exact byte positions (1-based)
        // Positions 1-3: "030" (3 chars)
        sb.append("030");
        sb.append("        "); // 8 spaces → positions 4-11
        
        // Positions 12-19: "REDECARD" (8 chars)
        sb.append("REDECARD");
        sb.append("  "); // 2 spaces → positions 20-21
        
        // Positions 22-55: "EXTRATO DE MOVIMENTACAO FINANCEIRA" (34 chars)
        sb.append("EXTRATO DE MOVIMENTACAO FINANCEIRA");
        sb.append("                                                    "); // 52 spaces → positions 56-107
        
        // Positions 108-127: "V4.00 - 05/2023 EEFI" (20 chars)
        sb.append("V4.00 - 05/2023 EEFI");
        sb.append("\n");
        
        // Add some data lines
        for (int i = 1; i <= 50; i++) {
            sb.append(String.format("040%-100s\n", "Financial transaction " + i + " data"));
        }
        
        // Trailer line
        sb.append("999" + " ".repeat(100) + "\n");
        
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
