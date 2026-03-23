package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.Layout;
import com.controle.arquivos.common.domain.entity.LayoutIdentificationRule;
import com.controle.arquivos.common.domain.enums.OrigemValor;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import com.controle.arquivos.common.repository.LayoutIdentificationRuleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para LayoutIdentificationService.
 * 
 * **Valida: Requisitos 9.1, 9.2, 9.3, 9.5**
 */
@ExtendWith(MockitoExtension.class)
class LayoutIdentificationServiceTest {

    @Mock
    private LayoutIdentificationRuleRepository ruleRepository;

    @Mock
    private EntityManager entityManager;

    private LayoutIdentificationService service;

    @BeforeEach
    void setUp() {
        service = new LayoutIdentificationService(ruleRepository, entityManager);
    }

    // Helper methods to create test data
    private LayoutIdentificationRule createRule(Long id, Long layoutId, Long clientId, Long acquirerId,
                                                 OrigemValor valueOrigin, TipoCriterio criterio, String value,
                                                 Integer startPos, Integer endPos) {
        return LayoutIdentificationRule.builder()
                .id(id)
                .layoutId(layoutId)
                .clientId(clientId)
                .acquirerId(acquirerId)
                .valueOrigin(valueOrigin)
                .criterionType(criterio.getValor())
                .value(value)
                .startingPosition(startPos)
                .endingPosition(endPos)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Layout createLayout(Long id, String name, String type) {
        return Layout.builder()
                .id(id)
                .layoutName(name)
                .layoutType(type)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private InputStream createHeaderStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // Test: Identification by FILENAME with COMECA-COM
    @Test
    void identificar_deveIdentificarLayoutPorFilenameComCriterioComecaCom() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS_20240115.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        
        Layout layout = createLayout(100L, "Layout CSV", "CSV");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
        assertEquals("Layout CSV", result.get().getLayoutName());
        
        verify(ruleRepository, times(1)).findActiveByClientIdAndAcquirerId(clientId, acquirerId);
        verify(entityManager, times(1)).find(Layout.class, 100L);
    }

    // Test: Identification by FILENAME with TERMINA-COM
    @Test
    void identificar_deveIdentificarLayoutPorFilenameComCriterioTerminaCom() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "vendas_20240115.json";
        
        LayoutIdentificationRule rule = createRule(1L, 200L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.TERMINA_COM, ".json", null, null);
        
        Layout layout = createLayout(200L, "Layout JSON", "JSON");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 200L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(200L, result.get().getId());
        assertEquals("Layout JSON", result.get().getLayoutName());
    }

    // Test: Identification by FILENAME with CONTEM
    @Test
    void identificar_deveIdentificarLayoutPorFilenameComCriterioContem() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo_XML_vendas.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 300L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.CONTEM, "XML", null, null);
        
        Layout layout = createLayout(300L, "Layout XML", "XML");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 300L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(300L, result.get().getId());
        assertEquals("Layout XML", result.get().getLayoutName());
    }

    // Test: Identification by FILENAME with IGUAL
    @Test
    void identificar_deveIdentificarLayoutPorFilenameComCriterioIgual() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "OFX_STANDARD.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 400L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.IGUAL, "OFX_STANDARD.txt", null, null);
        
        Layout layout = createLayout(400L, "Layout OFX", "OFX");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 400L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(400L, result.get().getId());
        assertEquals("Layout OFX", result.get().getLayoutName());
    }

    // Test: Identification by HEADER with COMECA-COM
    @Test
    void identificar_deveIdentificarLayoutPorHeaderComCriterioComecaCom() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo.txt";
        String headerContent = "<?xml version=\"1.0\"?><root>data</root>";
        InputStream headerStream = createHeaderStream(headerContent);
        
        LayoutIdentificationRule rule = createRule(1L, 300L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.COMECA_COM, "<?xml", null, null);
        
        Layout layout = createLayout(300L, "Layout XML", "XML");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 300L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(300L, result.get().getId());
        assertEquals("Layout XML", result.get().getLayoutName());
    }

    // Test: Identification by HEADER with CONTEM
    @Test
    void identificar_deveIdentificarLayoutPorHeaderComCriterioContem() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo.txt";
        String headerContent = "{\"type\":\"transaction\",\"data\":[]}";
        InputStream headerStream = createHeaderStream(headerContent);
        
        LayoutIdentificationRule rule = createRule(1L, 200L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.CONTEM, "\"type\":\"transaction\"", null, null);
        
        Layout layout = createLayout(200L, "Layout JSON", "JSON");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 200L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(200L, result.get().getId());
        assertEquals("Layout JSON", result.get().getLayoutName());
    }

    // Test: Reading exactly 7000 bytes from header
    @Test
    void identificar_deveLerExatamente7000BytesDoHeader() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo.txt";
        
        // Create content larger than 7000 bytes
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 8000; i++) {
            largeContent.append("X");
        }
        String content = largeContent.toString();
        InputStream headerStream = createHeaderStream(content);
        
        // Rule that checks for content at position 6999 (should be within 7000 bytes)
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.CONTEM, "X", null, null);
        
        Layout layout = createLayout(100L, "Layout Test", "TXT");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    // Test: Multiple rules for same layout (AND logic)
    @Test
    void identificar_deveAplicarTodasAsRegrasDoLayout_ANDLogico() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        String headerContent = "ID;NOME;VALOR\n1;Produto;100.00";
        InputStream headerStream = createHeaderStream(headerContent);
        
        // Layout with TWO rules - both must match
        LayoutIdentificationRule rule1 = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        LayoutIdentificationRule rule2 = createRule(2L, 100L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.CONTEM, "ID;NOME;VALOR", null, null);
        
        Layout layout = createLayout(100L, "Layout CSV", "CSV");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    @Test
    void identificar_naoDeveIdentificarQuandoUmaRegraFalha_ANDLogico() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        String headerContent = "HEADER_DIFERENTE\n1;Produto;100.00";
        InputStream headerStream = createHeaderStream(headerContent);
        
        // Layout with TWO rules - second rule will fail
        LayoutIdentificationRule rule1 = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        LayoutIdentificationRule rule2 = createRule(2L, 100L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.CONTEM, "ID;NOME;VALOR", null, null); // This will fail
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(entityManager, never()).find(eq(Layout.class), any());
    }

    // Test: No layout identified
    @Test
    void identificar_deveRetornarEmptyQuandoNenhumLayoutIdentificado() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "ARQUIVO_DESCONHECIDO.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(entityManager, never()).find(eq(Layout.class), any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoNenhumaRegraAtiva() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.emptyList());

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(entityManager, never()).find(eq(Layout.class), any());
    }

    // Test: Null/empty validations
    @Test
    void identificar_deveRetornarEmptyQuandoNomeArquivoVazio() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;

        // Act
        Optional<Layout> result = service.identificar("", null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByClientIdAndAcquirerId(any(), any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoNomeArquivoNulo() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;

        // Act
        Optional<Layout> result = service.identificar(null, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByClientIdAndAcquirerId(any(), any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoClienteNulo() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, null, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByClientIdAndAcquirerId(any(), any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoAdquirenteNulo() {
        // Arrange
        Long clientId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, null);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByClientIdAndAcquirerId(any(), any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoHeaderStreamNuloMasExistemRegrasHeader() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.CONTEM, "<?xml", null, null);
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(entityManager, never()).find(eq(Layout.class), any());
    }

    // Test: Layout not found or inactive
    @Test
    void identificar_deveRetornarEmptyQuandoLayoutNaoEncontradoNoBanco() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(null); // Layout not found

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoLayoutInativo() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        
        Layout layout = createLayout(100L, "Layout CSV", "CSV");
        layout.setActive(false); // Inactive layout
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent());
    }

    // Test: Multiple layouts match - select first by ID
    @Test
    void identificar_deveSelecionarPrimeiroLayoutQuandoMultiplosSatisfazemRegras() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        
        // Two layouts with rules that both match
        LayoutIdentificationRule rule1 = createRule(1L, 200L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        LayoutIdentificationRule rule2 = createRule(2L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.CONTEM, "CSV", null, null);
        
        Layout layout = createLayout(100L, "Layout CSV 1", "CSV"); // Lower ID
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId()); // Should select layout with lower ID
    }

    // Test: Substring extraction with positions
    @Test
    void identificar_deveExtrairSubstringComPosicoesEspecificadas() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "PREFIX_CSV_SUFFIX.txt";
        // Extract positions 8-11 (1-indexed) = "CSV_"
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", 8, 11);
        
        Layout layout = createLayout(100L, "Layout CSV", "CSV");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    // Test: aplicarRegra method directly
    @Test
    void aplicarRegra_deveRetornarFalseQuandoRegraNula() {
        // Act
        boolean result = service.aplicarRegra(null, "CSV_VENDAS.txt", "content");

        // Assert
        assertFalse(result);
    }

    @Test
    void aplicarRegra_deveRetornarFalseQuandoConteudoNulo() {
        // Arrange
        LayoutIdentificationRule rule = createRule(1L, 100L, 1L, 1L,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);

        // Act
        boolean result = service.aplicarRegra(rule, "filename", null);

        // Assert
        assertFalse(result);
    }

    @Test
    void aplicarRegra_deveRetornarFalseQuandoValorEsperadoNulo() {
        // Arrange
        LayoutIdentificationRule rule = createRule(1L, 100L, 1L, 1L,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, null, null, null);

        // Act
        boolean result = service.aplicarRegra(rule, "CSV_VENDAS.txt", "content");

        // Assert
        assertFalse(result);
    }

    // Test: Empty header stream
    @Test
    void identificar_deveTratarHeaderVazioCorretamente() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo.txt";
        InputStream headerStream = createHeaderStream("");
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.IGUAL, "", null, null);
        
        Layout layout = createLayout(100L, "Layout Empty", "TXT");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    // Test: Case sensitivity
    @Test
    void identificar_deveTratarCaseSensitiveCorretamente() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "csv_vendas.txt"; // lowercase
        
        LayoutIdentificationRule rule = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null); // uppercase
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertFalse(result.isPresent()); // Should not match due to case sensitivity
    }

    // Test: Complex scenario with mixed FILENAME and HEADER rules
    @Test
    void identificar_deveAplicarRegrasFilenameEHeaderJuntas() {
        // Arrange
        Long clientId = 1L;
        Long acquirerId = 1L;
        String nomeArquivo = "CSV_VENDAS.txt";
        String headerContent = "ID;NOME;VALOR\n1;Produto;100.00";
        InputStream headerStream = createHeaderStream(headerContent);
        
        // Layout with THREE rules - all must match
        LayoutIdentificationRule rule1 = createRule(1L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.COMECA_COM, "CSV", null, null);
        LayoutIdentificationRule rule2 = createRule(2L, 100L, clientId, acquirerId,
                OrigemValor.FILENAME, TipoCriterio.TERMINA_COM, ".txt", null, null);
        LayoutIdentificationRule rule3 = createRule(3L, 100L, clientId, acquirerId,
                OrigemValor.HEADER, TipoCriterio.CONTEM, "ID;NOME;VALOR", null, null);
        
        Layout layout = createLayout(100L, "Layout CSV", "CSV");
        
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2, rule3));
        when(entityManager.find(Layout.class, 100L))
                .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }
}
