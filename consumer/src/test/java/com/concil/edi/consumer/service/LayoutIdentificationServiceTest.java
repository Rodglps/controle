package com.concil.edi.consumer.service;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.entity.LayoutIdentificationRule;
import com.concil.edi.commons.enums.CriteriaType;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.FunctionType;
import com.concil.edi.commons.enums.ValueOrigin;
import com.concil.edi.commons.repository.LayoutIdentificationRuleRepository;
import com.concil.edi.commons.repository.LayoutRepository;
import com.concil.edi.commons.service.CriteriaComparator;
import com.concil.edi.commons.service.EncodingConverter;
import com.concil.edi.commons.service.RuleValidator;
import com.concil.edi.commons.service.extractor.ValueExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LayoutIdentificationService.
 * 
 * Tests cover:
 * - Identificação por FILENAME (Cielo)
 * - Identificação por HEADER CSV (Rede EEVD)
 * - Identificação por HEADER TXT (Rede EEVC, EEFI)
 * - Múltiplas regras com AND
 * - First-match wins
 * - Falha de identificação
 * 
 * Validates: Requirements 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7
 */
@ExtendWith(MockitoExtension.class)
class LayoutIdentificationServiceTest {

    @Mock
    private LayoutRepository layoutRepository;

    @Mock
    private LayoutIdentificationRuleRepository ruleRepository;

    @Mock
    private ValueExtractor filenameExtractor;

    @Mock
    private ValueExtractor headerTxtExtractor;

    @Mock
    private ValueExtractor headerCsvExtractor;

    @Mock
    private CriteriaComparator criteriaComparator;

    @Mock
    private EncodingConverter encodingConverter;

    @Mock
    private RuleValidator ruleValidator;

    private LayoutIdentificationService service;

    @BeforeEach
    void setUp() {
        List<ValueExtractor> extractors = Arrays.asList(
            filenameExtractor,
            headerTxtExtractor,
            headerCsvExtractor
        );

        service = new LayoutIdentificationService(
            layoutRepository,
            ruleRepository,
            extractors,
            criteriaComparator,
            encodingConverter,
            ruleValidator
        );
    }

    /**
     * Test: Identificação por FILENAME (Cielo VENDA)
     * 
     * Scenario: Arquivo com nome "cielo_v15_venda.txt" deve ser identificado como layout Cielo VENDA
     * Rule: FILENAME COMECA_COM "cielo"
     * 
     * Validates: Requirements 16.1
     */
    @Test
    void testIdentificacaoPorFilename_Cielo() {
        // Arrange
        String filename = "cielo_v15_venda.txt";
        Long acquirerId = 1L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout cieloLayout = createLayout(100L, "CIELO_VENDA", FileType.TXT, "UTF-8");
        LayoutIdentificationRule rule = createRule(1L, 100L, "Nome começa com cielo", 
            ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(cieloLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(Collections.singletonList(rule));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(new String(buffer));
        when(filenameExtractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(filenameExtractor.extractValue(any(byte[].class), eq(filename), eq(rule), eq(cieloLayout)))
            .thenReturn(filename);
        when(criteriaComparator.compare(eq(filename), eq("cielo"), eq(CriteriaType.COMECA_COM), any(), any()))
            .thenReturn(true);

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result);
        verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(100L, 1);
        verify(ruleValidator).validate(rule, cieloLayout);
    }

    /**
     * Test: Identificação por HEADER CSV (Rede EEVD)
     * 
     * Scenario: Arquivo CSV com primeira coluna "REDECARD" deve ser identificado como layout Rede EEVD
     * Rule: HEADER (CSV) coluna 0 IGUAL "REDECARD"
     * 
     * Validates: Requirements 16.1
     */
    @Test
    void testIdentificacaoPorHeaderCsv_RedeEEVD() {
        // Arrange
        String filename = "rede_eevd.csv";
        Long acquirerId = 2L;
        String csvContent = "REDECARD;20240101;VENDA\n";
        byte[] buffer = csvContent.getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout redeLayout = createLayout(200L, "REDE_EEVD", FileType.CSV, "UTF-8");
        redeLayout.setDesColumnSeparator(";");
        LayoutIdentificationRule rule = createRule(2L, 200L, "Primeira coluna igual REDECARD",
            ValueOrigin.HEADER, CriteriaType.IGUAL, "REDECARD");
        rule.setNumStartPosition(0);

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(redeLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(200L, 1))
            .thenReturn(Collections.singletonList(rule));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(csvContent);
        when(headerCsvExtractor.supports(ValueOrigin.HEADER, FileType.CSV))
            .thenReturn(true);
        when(headerCsvExtractor.extractValue(any(byte[].class), eq(filename), eq(rule), eq(redeLayout)))
            .thenReturn("REDECARD");
        when(criteriaComparator.compare(eq("REDECARD"), eq("REDECARD"), eq(CriteriaType.IGUAL), any(), any()))
            .thenReturn(true);

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNotNull(result);
        assertEquals(200L, result);
        verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(200L, 1);
    }

    /**
     * Test: Identificação por HEADER TXT (Rede EEVC)
     * 
     * Scenario: Arquivo TXT com bytes 0-8 contendo "REDECARD" deve ser identificado como layout Rede EEVC
     * Rule: HEADER (TXT) posição 0-8 CONTEM "REDECARD"
     * 
     * Validates: Requirements 16.1
     */
    @Test
    void testIdentificacaoPorHeaderTxt_RedeEEVC() {
        // Arrange
        String filename = "rede_eevc.txt";
        Long acquirerId = 2L;
        String txtContent = "REDECARD 20240101 VENDA\n";
        byte[] buffer = txtContent.getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout redeLayout = createLayout(201L, "REDE_EEVC", FileType.TXT, "UTF-8");
        LayoutIdentificationRule rule = createRule(3L, 201L, "Bytes 0-8 contém REDECARD",
            ValueOrigin.HEADER, CriteriaType.CONTEM, "REDECARD");
        rule.setNumStartPosition(0);
        rule.setNumEndPosition(8);

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(redeLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(201L, 1))
            .thenReturn(Collections.singletonList(rule));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(txtContent);
        when(headerTxtExtractor.supports(ValueOrigin.HEADER, FileType.TXT))
            .thenReturn(true);
        when(headerTxtExtractor.extractValue(any(byte[].class), eq(filename), eq(rule), eq(redeLayout)))
            .thenReturn("REDECARD");
        when(criteriaComparator.compare(eq("REDECARD"), eq("REDECARD"), eq(CriteriaType.CONTEM), any(), any()))
            .thenReturn(true);

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNotNull(result);
        assertEquals(201L, result);
        verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(201L, 1);
    }

    /**
     * Test: Múltiplas regras com operador AND
     * 
     * Scenario: Layout com 2 regras - ambas devem ser satisfeitas
     * Rule 1: FILENAME COMECA_COM "cielo"
     * Rule 2: FILENAME CONTEM "venda"
     * 
     * Validates: Requirements 16.4
     */
    @Test
    void testMultiplasRegrasComAND_TodasSatisfeitas() {
        // Arrange
        String filename = "cielo_v15_venda.txt";
        Long acquirerId = 1L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout cieloLayout = createLayout(100L, "CIELO_VENDA", FileType.TXT, "UTF-8");
        LayoutIdentificationRule rule1 = createRule(1L, 100L, "Nome começa com cielo",
            ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo");
        LayoutIdentificationRule rule2 = createRule(2L, 100L, "Nome contém venda",
            ValueOrigin.FILENAME, CriteriaType.CONTEM, "venda");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(cieloLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(Arrays.asList(rule1, rule2));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(new String(buffer));
        when(filenameExtractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(filenameExtractor.extractValue(any(byte[].class), eq(filename), any(), eq(cieloLayout)))
            .thenReturn(filename);
        when(criteriaComparator.compare(eq(filename), eq("cielo"), eq(CriteriaType.COMECA_COM), any(), any()))
            .thenReturn(true);
        when(criteriaComparator.compare(eq(filename), eq("venda"), eq(CriteriaType.CONTEM), any(), any()))
            .thenReturn(true);

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(100L, 1);
        verify(criteriaComparator, times(2)).compare(any(), any(), any(), any(), any());
    }

    /**
     * Test: Múltiplas regras com AND - uma falha
     * 
     * Scenario: Layout com 2 regras - apenas uma é satisfeita, layout não deve ser identificado
     * 
     * Validates: Requirements 16.4
     */
    @Test
    void testMultiplasRegrasComAND_UmaFalha() {
        // Arrange
        String filename = "cielo_v15_pagto.txt";
        Long acquirerId = 1L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout cieloLayout = createLayout(100L, "CIELO_VENDA", FileType.TXT, "UTF-8");
        LayoutIdentificationRule rule1 = createRule(1L, 100L, "Nome começa com cielo",
            ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo");
        LayoutIdentificationRule rule2 = createRule(2L, 100L, "Nome contém venda",
            ValueOrigin.FILENAME, CriteriaType.CONTEM, "venda");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(cieloLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(Arrays.asList(rule1, rule2));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(new String(buffer));
        when(filenameExtractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(filenameExtractor.extractValue(any(byte[].class), eq(filename), any(), eq(cieloLayout)))
            .thenReturn(filename);
        when(criteriaComparator.compare(eq(filename), eq("cielo"), eq(CriteriaType.COMECA_COM), any(), any()))
            .thenReturn(true);
        when(criteriaComparator.compare(eq(filename), eq("venda"), eq(CriteriaType.CONTEM), any(), any()))
            .thenReturn(false); // Segunda regra falha

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNull(result);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(100L, 1);
    }

    /**
     * Test: First-match wins
     * 
     * Scenario: Dois layouts candidatos, ambos com regras satisfeitas
     * O primeiro na ordem (maior idt_layout) deve ser retornado
     * 
     * Validates: Requirements 16.5
     */
    @Test
    void testFirstMatchWins() {
        // Arrange
        String filename = "cielo_arquivo.txt";
        Long acquirerId = 1L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Layout mais recente (maior ID) - deve ser retornado
        Layout layoutNovo = createLayout(102L, "CIELO_V2", FileType.TXT, "UTF-8");
        LayoutIdentificationRule ruleNovo = createRule(10L, 102L, "Nome começa com cielo",
            ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo");

        // Layout mais antigo (menor ID)
        Layout layoutAntigo = createLayout(100L, "CIELO_V1", FileType.TXT, "UTF-8");
        LayoutIdentificationRule ruleAntigo = createRule(11L, 100L, "Nome começa com cielo",
            ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo");

        // Layouts ordenados por idt_layout DESC (mais recente primeiro)
        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Arrays.asList(layoutNovo, layoutAntigo));
        when(ruleRepository.findByIdtLayoutAndFlgActive(102L, 1))
            .thenReturn(Collections.singletonList(ruleNovo));
        // Note: Not stubbing layoutAntigo rules because first-match wins (they won't be called)
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(new String(buffer));
        when(filenameExtractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(filenameExtractor.extractValue(any(byte[].class), eq(filename), any(), any()))
            .thenReturn(filename);
        when(criteriaComparator.compare(eq(filename), eq("cielo"), eq(CriteriaType.COMECA_COM), any(), any()))
            .thenReturn(true);

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNotNull(result);
        assertEquals(102L, result, "Deve retornar o layout mais recente (maior ID)");
        verify(ruleRepository).findByIdtLayoutAndFlgActive(102L, 1);
        verify(ruleRepository, never()).findByIdtLayoutAndFlgActive(100L, 1); // Não deve verificar o segundo
    }

    /**
     * Test: Falha de identificação - nenhum layout encontrado
     * 
     * Scenario: Nenhum layout ativo para a adquirente
     * 
     * Validates: Requirements 16.7
     */
    @Test
    void testFalhaIdentificacao_NenhumLayoutEncontrado() {
        // Arrange
        String filename = "arquivo_desconhecido.txt";
        Long acquirerId = 999L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.emptyList());

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNull(result);
        verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
        verify(ruleRepository, never()).findByIdtLayoutAndFlgActive(anyLong(), anyInt());
    }

    /**
     * Test: Falha de identificação - nenhuma regra satisfeita
     * 
     * Scenario: Layout existe mas nenhuma regra é satisfeita
     * 
     * Validates: Requirements 16.7
     */
    @Test
    void testFalhaIdentificacao_NenhumaRegraSatisfeita() {
        // Arrange
        String filename = "rede_arquivo.txt";
        Long acquirerId = 1L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout cieloLayout = createLayout(100L, "CIELO_VENDA", FileType.TXT, "UTF-8");
        LayoutIdentificationRule rule = createRule(1L, 100L, "Nome começa com cielo",
            ValueOrigin.FILENAME, CriteriaType.COMECA_COM, "cielo");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(cieloLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(Collections.singletonList(rule));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(new String(buffer));
        when(filenameExtractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(filenameExtractor.extractValue(any(byte[].class), eq(filename), eq(rule), eq(cieloLayout)))
            .thenReturn(filename);
        when(criteriaComparator.compare(eq(filename), eq("cielo"), eq(CriteriaType.COMECA_COM), any(), any()))
            .thenReturn(false); // Regra não satisfeita

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNull(result);
        verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(100L, 1);
    }

    /**
     * Test: Layout sem regras ativas
     * 
     * Scenario: Layout existe mas não possui regras ativas
     * 
     * Validates: Requirements 16.7
     */
    @Test
    void testLayoutSemRegrasAtivas() {
        // Arrange
        String filename = "cielo_arquivo.txt";
        Long acquirerId = 1L;
        byte[] buffer = "dummy content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout cieloLayout = createLayout(100L, "CIELO_VENDA", FileType.TXT, "UTF-8");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(cieloLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(Collections.emptyList()); // Sem regras ativas

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNull(result);
        verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
        verify(ruleRepository).findByIdtLayoutAndFlgActive(100L, 1);
    }

    /**
     * Test: Extrator não encontrado para tipo de origem
     * 
     * Scenario: Regra com tipo de origem não suportado
     */
    @Test
    void testExtratorNaoEncontrado() {
        // Arrange
        String filename = "arquivo.xml";
        Long acquirerId = 1L;
        byte[] buffer = "<root></root>".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        Layout xmlLayout = createLayout(300L, "XML_LAYOUT", FileType.XML, "UTF-8");
        LayoutIdentificationRule rule = createRule(20L, 300L, "Tag XML",
            ValueOrigin.TAG, CriteriaType.IGUAL, "root");
        rule.setDesTag("root");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(xmlLayout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(300L, 1))
            .thenReturn(Collections.singletonList(rule));
        when(encodingConverter.convertWithFallback(any(byte[].class), eq("UTF-8")))
            .thenReturn(new String(buffer));
        // Nenhum extrator suporta TAG + XML
        when(filenameExtractor.supports(ValueOrigin.TAG, FileType.XML)).thenReturn(false);
        when(headerTxtExtractor.supports(ValueOrigin.TAG, FileType.XML)).thenReturn(false);
        when(headerCsvExtractor.supports(ValueOrigin.TAG, FileType.XML)).thenReturn(false);

        // Act
        Long result = service.identifyLayout(inputStream, filename, acquirerId);

        // Assert
        assertNull(result);
    }

    // Helper methods

    private Layout createLayout(Long id, String codLayout, FileType fileType, String encoding) {
        Layout layout = new Layout();
        layout.setIdtLayout(id);
        layout.setCodLayout(codLayout);
        layout.setDesFileType(fileType);
        layout.setDesEncoding(encoding);
        layout.setFlgActive(1);
        return layout;
    }

    private LayoutIdentificationRule createRule(Long id, Long layoutId, String description,
                                                 ValueOrigin valueOrigin, CriteriaType criteriaType,
                                                 String expectedValue) {
        LayoutIdentificationRule rule = new LayoutIdentificationRule();
        rule.setIdtRule(id);
        rule.setIdtLayout(layoutId);
        rule.setDesRule(description);
        rule.setDesValueOrigin(valueOrigin);
        rule.setDesCriteriaType(criteriaType);
        rule.setDesValue(expectedValue);
        rule.setFlgActive(1);
        return rule;
    }
}
