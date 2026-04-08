package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.entity.LayoutIdentificationRule;
import com.concil.edi.commons.enums.*;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.LayoutIdentificationRuleRepository;
import com.concil.edi.commons.repository.LayoutRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LayoutIdentificationService with actual database.
 * 
 * Tests cover:
 * - Carregamento de configurações do banco de dados
 * - Filtro por idt_acquirer
 * - Ordenação por idt_layout DESC
 * - Filtro por flg_active
 * - Atualização de file_origin.idt_layout
 * 
 * Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
class LayoutIdentificationIntegrationTest {

    @Autowired
    private LayoutIdentificationService layoutIdentificationService;

    @Autowired
    private LayoutRepository layoutRepository;

    @Autowired
    private LayoutIdentificationRuleRepository ruleRepository;

    @Autowired
    private FileOriginRepository fileOriginRepository;

    private static Long testAcquirerId1;
    private static Long testAcquirerId2;
    private static Long testLayoutId1;
    private static Long testLayoutId2;
    private static Long testLayoutId3;

    /**
     * Setup test data in database before all tests
     */
    @BeforeAll
    static void setupTestData(@Autowired LayoutRepository layoutRepository,
                              @Autowired LayoutIdentificationRuleRepository ruleRepository) {
        System.out.println("Setting up integration test data...");

        // Use unique acquirer IDs for test isolation
        testAcquirerId1 = 9001L;
        testAcquirerId2 = 9002L;

        // Create test layouts for acquirer 1
        Layout layout1 = createLayout(null, "TEST_CIELO_VENDA", testAcquirerId1, 
                FileType.TXT, "UTF-8", 1);
        layout1 = layoutRepository.save(layout1);
        testLayoutId1 = layout1.getIdtLayout();

        Layout layout2 = createLayout(null, "TEST_CIELO_PAGTO", testAcquirerId1, 
                FileType.TXT, "UTF-8", 1);
        layout2 = layoutRepository.save(layout2);
        testLayoutId2 = layout2.getIdtLayout();

        // Create test layout for acquirer 2
        Layout layout3 = createLayout(null, "TEST_REDE_EEVD", testAcquirerId2, 
                FileType.CSV, "UTF-8", 1);
        layout3.setDesColumnSeparator(";");
        layout3 = layoutRepository.save(layout3);
        testLayoutId3 = layout3.getIdtLayout();

        // Create rules for layout 1 (FILENAME COMECA_COM "cielo_venda")
        LayoutIdentificationRule rule1 = createRule(null, testLayoutId1, 
                "Nome começa com cielo_venda",
                ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo_venda", 1);
        ruleRepository.save(rule1);

        // Create rules for layout 2 (FILENAME COMECA_COM "cielo_pagto")
        LayoutIdentificationRule rule2 = createRule(null, testLayoutId2,
                "Nome começa com cielo_pagto",
                ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo_pagto", 1);
        ruleRepository.save(rule2);

        // Create rules for layout 3 (HEADER CSV coluna 0 IGUAL "REDECARD")
        LayoutIdentificationRule rule3 = createRule(null, testLayoutId3,
                "Primeira coluna igual REDECARD",
                ValueOrigin.HEADER, CriteriaType.IGUAL, "REDECARD", 1);
        rule3.setNumStartPosition(0);
        ruleRepository.save(rule3);

        System.out.println("Test data created:");
        System.out.println("  Layout 1 ID: " + testLayoutId1 + " (Acquirer: " + testAcquirerId1 + ")");
        System.out.println("  Layout 2 ID: " + testLayoutId2 + " (Acquirer: " + testAcquirerId1 + ")");
        System.out.println("  Layout 3 ID: " + testLayoutId3 + " (Acquirer: " + testAcquirerId2 + ")");
    }

    /**
     * Cleanup test data after all tests
     */
    @AfterAll
    static void cleanupTestData(@Autowired LayoutRepository layoutRepository,
                                @Autowired LayoutIdentificationRuleRepository ruleRepository,
                                @Autowired FileOriginRepository fileOriginRepository) {
        System.out.println("Cleaning up integration test data...");

        // Delete rules first (foreign key constraint)
        if (testLayoutId1 != null) {
            ruleRepository.deleteAll(ruleRepository.findByIdtLayoutAndFlgActive(testLayoutId1, 1));
        }
        if (testLayoutId2 != null) {
            ruleRepository.deleteAll(ruleRepository.findByIdtLayoutAndFlgActive(testLayoutId2, 1));
        }
        if (testLayoutId3 != null) {
            ruleRepository.deleteAll(ruleRepository.findByIdtLayoutAndFlgActive(testLayoutId3, 1));
        }

        // Delete file_origin records that reference test layouts
        List<FileOrigin> testFileOrigins = fileOriginRepository.findAll().stream()
                .filter(fo -> fo.getIdtLayout() != null && 
                        (fo.getIdtLayout().equals(testLayoutId1) || 
                         fo.getIdtLayout().equals(testLayoutId2) ||
                         fo.getIdtLayout().equals(testLayoutId3)))
                .toList();
        fileOriginRepository.deleteAll(testFileOrigins);

        // Delete layouts
        if (testLayoutId1 != null) {
            layoutRepository.deleteById(testLayoutId1);
        }
        if (testLayoutId2 != null) {
            layoutRepository.deleteById(testLayoutId2);
        }
        if (testLayoutId3 != null) {
            layoutRepository.deleteById(testLayoutId3);
        }

        System.out.println("Test data cleanup complete");
    }

    /**
     * Test: Carregamento de configurações do banco de dados
     * 
     * Validates: Requirements 17.1
     */
    @Test
    @Order(1)
    @Transactional(readOnly = true)
    void testCarregamentoConfiguracoesDoBanco() {
        // Act: Load layouts from database
        List<Layout> layouts = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                testAcquirerId1, 1);

        // Assert: Layouts should be loaded
        assertNotNull(layouts);
        assertEquals(2, layouts.size(), "Should load 2 layouts for acquirer 1");

        // Verify layout details
        Layout layout1 = layouts.stream()
                .filter(l -> l.getCodLayout().equals("TEST_CIELO_VENDA"))
                .findFirst()
                .orElse(null);
        assertNotNull(layout1, "Layout TEST_CIELO_VENDA should be loaded");
        assertEquals(testAcquirerId1, layout1.getIdtAcquirer());
        assertEquals(FileType.TXT, layout1.getDesFileType());
        assertEquals("UTF-8", layout1.getDesEncoding());

        // Load rules for layout
        List<LayoutIdentificationRule> rules = ruleRepository.findByIdtLayoutAndFlgActive(
                layout1.getIdtLayout(), 1);
        assertNotNull(rules);
        assertEquals(1, rules.size(), "Should load 1 rule for layout");

        LayoutIdentificationRule rule = rules.get(0);
        assertEquals(ValueOrigin.FILENAME, rule.getDesValueOrigin());
        assertEquals(CriteriaType.COMECA_COM, rule.getDesCriteriaType());
        assertEquals("cielo_venda", rule.getDesValue());
    }

    /**
     * Test: Filtro por idt_acquirer
     * 
     * Validates: Requirements 17.2
     */
    @Test
    @Order(2)
    @Transactional(readOnly = true)
    void testFiltroPorAcquirer() {
        // Act: Load layouts for acquirer 1
        List<Layout> layoutsAcq1 = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                testAcquirerId1, 1);

        // Assert: Should only get layouts for acquirer 1
        assertNotNull(layoutsAcq1);
        assertEquals(2, layoutsAcq1.size());
        assertTrue(layoutsAcq1.stream().allMatch(l -> l.getIdtAcquirer().equals(testAcquirerId1)),
                "All layouts should belong to acquirer 1");

        // Act: Load layouts for acquirer 2
        List<Layout> layoutsAcq2 = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                testAcquirerId2, 1);

        // Assert: Should only get layouts for acquirer 2
        assertNotNull(layoutsAcq2);
        assertEquals(1, layoutsAcq2.size());
        assertTrue(layoutsAcq2.stream().allMatch(l -> l.getIdtAcquirer().equals(testAcquirerId2)),
                "All layouts should belong to acquirer 2");

        // Act: Load layouts for non-existent acquirer
        List<Layout> layoutsNone = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                99999L, 1);

        // Assert: Should get empty list
        assertNotNull(layoutsNone);
        assertTrue(layoutsNone.isEmpty(), "Should return empty list for non-existent acquirer");
    }

    /**
     * Test: Ordenação por idt_layout DESC
     * 
     * Validates: Requirements 17.3
     */
    @Test
    @Order(3)
    @Transactional(readOnly = true)
    void testOrdenacaoPorIdtLayoutDesc() {
        // Act: Load layouts for acquirer 1
        List<Layout> layouts = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                testAcquirerId1, 1);

        // Assert: Layouts should be ordered by idt_layout DESC
        assertNotNull(layouts);
        assertEquals(2, layouts.size());

        // Verify ordering (higher ID first)
        for (int i = 0; i < layouts.size() - 1; i++) {
            Long currentId = layouts.get(i).getIdtLayout();
            Long nextId = layouts.get(i + 1).getIdtLayout();
            assertTrue(currentId > nextId,
                    String.format("Layout at index %d (ID=%d) should have higher ID than layout at index %d (ID=%d)",
                            i, currentId, i + 1, nextId));
        }

        // The first layout should be the one with higher ID (more recent)
        Long firstLayoutId = layouts.get(0).getIdtLayout();
        Long secondLayoutId = layouts.get(1).getIdtLayout();
        assertTrue(firstLayoutId > secondLayoutId,
                "First layout should have higher ID (first-match wins strategy)");
    }

    /**
     * Test: Filtro por flg_active
     * 
     * Validates: Requirements 17.4
     */
    @Test
    @Order(4)
    @Transactional
    void testFiltroPorFlgActive() {
        // Arrange: Create an inactive layout
        Layout inactiveLayout = createLayout(null, "TEST_INACTIVE", testAcquirerId1,
                FileType.TXT, "UTF-8", 0); // flg_active = 0
        inactiveLayout = layoutRepository.save(inactiveLayout);
        Long inactiveLayoutId = inactiveLayout.getIdtLayout();

        try {
            // Act: Load active layouts
            List<Layout> activeLayouts = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                    testAcquirerId1, 1);

            // Assert: Should not include inactive layout
            assertNotNull(activeLayouts);
            assertTrue(activeLayouts.stream().noneMatch(l -> l.getIdtLayout().equals(inactiveLayoutId)),
                    "Should not include inactive layout");
            assertTrue(activeLayouts.stream().allMatch(l -> l.getFlgActive().equals(1)),
                    "All returned layouts should be active");

            // Act: Load inactive layouts
            List<Layout> inactiveLayouts = layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(
                    testAcquirerId1, 0);

            // Assert: Should include only inactive layout
            assertNotNull(inactiveLayouts);
            assertEquals(1, inactiveLayouts.size());
            assertEquals(inactiveLayoutId, inactiveLayouts.get(0).getIdtLayout());

        } finally {
            // Cleanup: Delete inactive layout
            layoutRepository.deleteById(inactiveLayoutId);
        }
    }

    /**
     * Test: Identificação completa com banco de dados
     * 
     * Validates: Requirements 17.1, 17.2, 17.3
     */
    @Test
    @Order(5)
    void testIdentificacaoCompletaComBanco() {
        // Arrange
        String filename = "cielo_venda_20240101.txt";
        byte[] content = "dummy file content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(content);

        // Act: Identify layout using real database
        Long identifiedLayoutId = layoutIdentificationService.identifyLayout(
                inputStream, filename, testAcquirerId1);

        // Assert: Should identify the correct layout
        assertNotNull(identifiedLayoutId, "Layout should be identified");
        assertEquals(testLayoutId1, identifiedLayoutId,
                "Should identify TEST_CIELO_VENDA layout");

        // Verify the identified layout exists in database
        Layout identifiedLayout = layoutRepository.findById(identifiedLayoutId).orElse(null);
        assertNotNull(identifiedLayout);
        assertEquals("TEST_CIELO_VENDA", identifiedLayout.getCodLayout());
    }

    /**
     * Test: Atualização de file_origin.idt_layout
     * 
     * Validates: Requirements 17.5
     */
    @Test
    @Order(6)
    @Transactional
    void testAtualizacaoFileOriginIdtLayout() {
        // Arrange: Create a file_origin record
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtAcquirer(testAcquirerId1);
        fileOrigin.setDesFileName("cielo_venda_test.txt");
        fileOrigin.setNumFileSize(1024L);
        fileOrigin.setDesFileType(FileType.TXT);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.EM_ESPERA);
        fileOrigin.setDesTransactionType(TransactionType.FINANCEIRO);
        fileOrigin.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        fileOrigin.setIdtSeverPathsInOut(1L);
        fileOrigin.setDatCreation(new Date());
        fileOrigin.setFlgActive(1);
        fileOrigin.setNumRetry(0);
        fileOrigin.setMaxRetry(3);
        fileOrigin.setIdtLayout(null); // Initially null

        fileOrigin = fileOriginRepository.save(fileOrigin);
        Long fileOriginId = fileOrigin.getIdtFileOrigin();

        try {
            // Act: Update idt_layout
            fileOrigin.setIdtLayout(testLayoutId1);
            fileOriginRepository.save(fileOrigin);

            // Assert: Verify update
            FileOrigin updated = fileOriginRepository.findById(fileOriginId).orElse(null);
            assertNotNull(updated);
            assertEquals(testLayoutId1, updated.getIdtLayout(),
                    "idt_layout should be updated");

            // Verify foreign key relationship
            Layout referencedLayout = layoutRepository.findById(updated.getIdtLayout()).orElse(null);
            assertNotNull(referencedLayout, "Referenced layout should exist");
            assertEquals("TEST_CIELO_VENDA", referencedLayout.getCodLayout());

        } finally {
            // Cleanup: Delete file_origin record
            fileOriginRepository.deleteById(fileOriginId);
        }
    }

    /**
     * Test: First-match wins com banco de dados
     * 
     * Validates: Requirements 17.3
     */
    @Test
    @Order(7)
    @Transactional
    void testFirstMatchWinsComBanco() {
        // Arrange: Create a new layout with higher ID that also matches
        Layout newerLayout = createLayout(null, "TEST_CIELO_NEWER", testAcquirerId1,
                FileType.TXT, "UTF-8", 1);
        newerLayout = layoutRepository.save(newerLayout);
        Long newerLayoutId = newerLayout.getIdtLayout();

        // Create rule that matches the same filename pattern
        LayoutIdentificationRule newerRule = createRule(null, newerLayoutId,
                "Nome começa com cielo",
                ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo", 1);
        ruleRepository.save(newerRule);

        try {
            // Act: Identify layout
            String filename = "cielo_venda_20240101.txt";
            byte[] content = "dummy content".getBytes();
            InputStream inputStream = new ByteArrayInputStream(content);

            Long identifiedLayoutId = layoutIdentificationService.identifyLayout(
                    inputStream, filename, testAcquirerId1);

            // Assert: Should identify the newer layout (higher ID, first in DESC order)
            assertNotNull(identifiedLayoutId);
            
            // The newer layout should have a higher ID
            assertTrue(newerLayoutId > testLayoutId1,
                    "Newer layout should have higher ID");
            
            // Should identify the newer layout (first-match wins)
            assertEquals(newerLayoutId, identifiedLayoutId,
                    "Should identify the newer layout (first-match wins)");

        } finally {
            // Cleanup
            ruleRepository.deleteAll(ruleRepository.findByIdtLayoutAndFlgActive(newerLayoutId, 1));
            layoutRepository.deleteById(newerLayoutId);
        }
    }

    /**
     * Test: Filtro de regras ativas
     * 
     * Validates: Requirements 17.4
     */
    @Test
    @Order(8)
    @Transactional
    void testFiltroRegrasAtivas() {
        // Arrange: Create an inactive rule
        LayoutIdentificationRule inactiveRule = createRule(null, testLayoutId1,
                "Regra inativa",
                ValueOrigin.FILENAME, CriteriaType.CONTEM, "inactive", 0); // flg_active = 0
        inactiveRule = ruleRepository.save(inactiveRule);
        Long inactiveRuleId = inactiveRule.getIdtRule();

        try {
            // Act: Load active rules
            List<LayoutIdentificationRule> activeRules = ruleRepository.findByIdtLayoutAndFlgActive(
                    testLayoutId1, 1);

            // Assert: Should not include inactive rule
            assertNotNull(activeRules);
            assertTrue(activeRules.stream().noneMatch(r -> r.getIdtRule().equals(inactiveRuleId)),
                    "Should not include inactive rule");
            assertTrue(activeRules.stream().allMatch(r -> r.getFlgActive().equals(1)),
                    "All returned rules should be active");

        } finally {
            // Cleanup
            ruleRepository.deleteById(inactiveRuleId);
        }
    }

    // Helper methods

    private static Layout createLayout(Long id, String codLayout, Long acquirerId,
                                       FileType fileType, String encoding, Integer flgActive) {
        Layout layout = new Layout();
        layout.setIdtLayout(id);
        layout.setCodLayout(codLayout);
        layout.setIdtAcquirer(acquirerId);
        layout.setDesVersion("1.0");
        layout.setDesFileType(fileType);
        layout.setDesTransactionType(TransactionType.FINANCEIRO);
        layout.setDesDistributionType(DistributionType.DIARIO);
        layout.setDesEncoding(encoding);
        layout.setDatCreation(new Date());
        layout.setNamChangeAgent("integration-test");
        layout.setFlgActive(flgActive);
        return layout;
    }

    private static LayoutIdentificationRule createRule(Long id, Long layoutId, String description,
                                                       ValueOrigin valueOrigin, CriteriaType criteriaType,
                                                       String expectedValue, Integer flgActive) {
        LayoutIdentificationRule rule = new LayoutIdentificationRule();
        rule.setIdtRule(id);
        rule.setIdtLayout(layoutId);
        rule.setDesRule(description);
        rule.setDesValueOrigin(valueOrigin);
        rule.setDesCriteriaType(criteriaType);
        rule.setDesValue(expectedValue);
        rule.setDatCreation(new Date());
        rule.setNamChangeAgent("integration-test");
        rule.setFlgActive(flgActive);
        return rule;
    }
}
