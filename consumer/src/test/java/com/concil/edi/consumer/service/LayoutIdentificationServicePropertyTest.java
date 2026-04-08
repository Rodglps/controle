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
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.constraints.StringLength;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for LayoutIdentificationService.
 * 
 * Tests the following properties:
 * - Property 15: Leitura limitada ao buffer
 * - Property 23: Filtro por adquirente e flag ativa
 * - Property 24: Ordenação por idt_layout DESC
 * - Property 25: Filtro de regras ativas
 * - Property 26: Operador AND entre regras
 * - Property 27: First-match wins
 * - Property 28: Retorno de idt_layout
 * 
 * Validates: Requirements 3.2, 4.1-4.7
 */
public class LayoutIdentificationServicePropertyTest {

    /**
     * Feature: identificacao_layouts, Property 23: Filtro por adquirente e flag ativa
     * 
     * For any acquirer ID, o serviço deve buscar apenas layouts com idt_acquirer correspondente
     * e flg_active igual a 1.
     * 
     * Validates: Requirements 4.1
     */
    @Property(tries = 100)
    void filtroAcquirerEFlagAtivaProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @Size(min = 1, max = 5) List<@From("layoutGenerator") Layout> layouts
    ) {
        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        List<ValueExtractor> extractors = Collections.emptyList();
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        // Set all layouts to the same acquirer and active flag
        layouts.forEach(layout -> {
            layout.setIdtAcquirer(acquirerId);
            layout.setFlgActive(1);
        });

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(layouts);
        when(ruleRepository.findByIdtLayoutAndFlgActive(anyLong(), eq(1)))
            .thenReturn(Collections.emptyList());
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Verify that the repository was called with correct parameters
        Mockito.verify(layoutRepository).findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1);
    }

    /**
     * Feature: identificacao_layouts, Property 24: Ordenação por idt_layout DESC
     * 
     * For any conjunto de layouts retornados, eles devem estar ordenados por idt_layout
     * em ordem descendente (layouts mais recentes primeiro).
     * 
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    void ordenacaoPorIdtLayoutDescProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @Size(min = 2, max = 10) List<@From("layoutGenerator") Layout> unsortedLayouts
    ) {
        // Arrange: Assign sequential IDs to layouts
        for (int i = 0; i < unsortedLayouts.size(); i++) {
            unsortedLayouts.get(i).setIdtLayout((long) (i + 1));
            unsortedLayouts.get(i).setIdtAcquirer(acquirerId);
            unsortedLayouts.get(i).setFlgActive(1);
        }

        // Sort layouts by idt_layout DESC (as repository should do)
        List<Layout> sortedLayouts = unsortedLayouts.stream()
            .sorted((a, b) -> Long.compare(b.getIdtLayout(), a.getIdtLayout()))
            .collect(Collectors.toList());

        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        List<ValueExtractor> extractors = Collections.emptyList();
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(sortedLayouts);
        when(ruleRepository.findByIdtLayoutAndFlgActive(anyLong(), eq(1)))
            .thenReturn(Collections.emptyList());
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Verify layouts are processed in DESC order (highest ID first)
        // The service should check the highest ID layout first
        Mockito.verify(ruleRepository).findByIdtLayoutAndFlgActive(sortedLayouts.get(0).getIdtLayout(), 1);
    }

    /**
     * Feature: identificacao_layouts, Property 25: Filtro de regras ativas
     * 
     * For any layout, o serviço deve buscar apenas regras com flg_active igual a 1.
     * 
     * Validates: Requirements 4.3
     */
    @Property(tries = 100)
    void filtroRegrasAtivasProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @IntRange(min = 1, max = 100) long layoutId
    ) {
        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        List<ValueExtractor> extractors = Collections.emptyList();
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        Layout layout = new Layout();
        layout.setIdtLayout(layoutId);
        layout.setIdtAcquirer(acquirerId);
        layout.setFlgActive(1);
        layout.setDesFileType(FileType.TXT);
        layout.setDesEncoding("UTF-8");

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(layout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(layoutId, 1))
            .thenReturn(Collections.emptyList());
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Verify that rules are queried with flg_active = 1
        Mockito.verify(ruleRepository).findByIdtLayoutAndFlgActive(layoutId, 1);
    }

    /**
     * Feature: identificacao_layouts, Property 26: Operador AND entre regras
     * 
     * For any layout com múltiplas regras, todas as regras devem ser satisfeitas (operador AND)
     * para que o layout seja considerado identificado.
     * 
     * Validates: Requirements 4.4, 4.5
     */
    @Property(tries = 100)
    void operadorAndEntreRegrasProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @IntRange(min = 2, max = 5) int numRules,
        @ForAll @IntRange(min = 0, max = 10) int failingRuleIndex
    ) {
        Assume.that(failingRuleIndex < numRules);

        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        ValueExtractor extractor = Mockito.mock(ValueExtractor.class);
        List<ValueExtractor> extractors = Collections.singletonList(extractor);
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        Layout layout = new Layout();
        layout.setIdtLayout(100L);
        layout.setIdtAcquirer(acquirerId);
        layout.setFlgActive(1);
        layout.setDesFileType(FileType.TXT);
        layout.setDesEncoding("UTF-8");

        // Create multiple rules
        List<LayoutIdentificationRule> rules = new ArrayList<>();
        for (int i = 0; i < numRules; i++) {
            LayoutIdentificationRule rule = new LayoutIdentificationRule();
            rule.setIdtRule((long) i);
            rule.setIdtLayout(100L);
            rule.setDesRule("Rule " + i);
            rule.setDesValueOrigin(ValueOrigin.FILENAME);
            rule.setDesCriteriaType(CriteriaType.CONTEM);
            rule.setDesValue("value" + i);
            rule.setFlgActive(1);
            rules.add(rule);
        }

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(layout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(rules);
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");
        when(extractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(extractor.extractValue(any(byte[].class), anyString(), any(), any()))
            .thenReturn("extracted");

        // Make one rule fail, others succeed
        for (int i = 0; i < numRules; i++) {
            if (i == failingRuleIndex) {
                when(criteriaComparator.compare(anyString(), eq("value" + i), any(), any(), any()))
                    .thenReturn(false);
            } else {
                when(criteriaComparator.compare(anyString(), eq("value" + i), any(), any(), any()))
                    .thenReturn(true);
            }
        }

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        Long result = service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Layout should NOT be identified because one rule failed
        assert result == null : "Layout should not be identified when any rule fails (AND operator)";
    }

    /**
     * Feature: identificacao_layouts, Property 26: AND operator - all rules must pass
     * 
     * When all rules are satisfied, layout should be identified.
     */
    @Property(tries = 100)
    void andOperatorAllRulesPassProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @IntRange(min = 1, max = 5) int numRules
    ) {
        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        ValueExtractor extractor = Mockito.mock(ValueExtractor.class);
        List<ValueExtractor> extractors = Collections.singletonList(extractor);
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        Layout layout = new Layout();
        layout.setIdtLayout(100L);
        layout.setIdtAcquirer(acquirerId);
        layout.setFlgActive(1);
        layout.setDesFileType(FileType.TXT);
        layout.setDesEncoding("UTF-8");

        // Create multiple rules
        List<LayoutIdentificationRule> rules = new ArrayList<>();
        for (int i = 0; i < numRules; i++) {
            LayoutIdentificationRule rule = new LayoutIdentificationRule();
            rule.setIdtRule((long) i);
            rule.setIdtLayout(100L);
            rule.setDesRule("Rule " + i);
            rule.setDesValueOrigin(ValueOrigin.FILENAME);
            rule.setDesCriteriaType(CriteriaType.CONTEM);
            rule.setDesValue("value" + i);
            rule.setFlgActive(1);
            rules.add(rule);
        }

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(layout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(100L, 1))
            .thenReturn(rules);
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");
        when(extractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(extractor.extractValue(any(byte[].class), anyString(), any(), any()))
            .thenReturn("extracted");

        // All rules succeed
        when(criteriaComparator.compare(anyString(), anyString(), any(), any(), any()))
            .thenReturn(true);

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        Long result = service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Layout should be identified when all rules pass
        assert result != null : "Layout should be identified when all rules pass (AND operator)";
        assert result.equals(100L) : "Should return the correct layout ID";
    }

    /**
     * Feature: identificacao_layouts, Property 27: First-match wins
     * 
     * For any conjunto de layouts onde múltiplos layouts satisfazem todas as regras,
     * o primeiro layout na ordem (maior idt_layout) deve ser retornado.
     * 
     * Validates: Requirements 4.6
     */
    @Property(tries = 100)
    void firstMatchWinsProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @IntRange(min = 2, max = 5) int numLayouts
    ) {
        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        ValueExtractor extractor = Mockito.mock(ValueExtractor.class);
        List<ValueExtractor> extractors = Collections.singletonList(extractor);
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        // Create multiple layouts in DESC order (highest ID first)
        List<Layout> layouts = new ArrayList<>();
        for (int i = numLayouts; i >= 1; i--) {
            Layout layout = new Layout();
            layout.setIdtLayout((long) i * 100);
            layout.setIdtAcquirer(acquirerId);
            layout.setFlgActive(1);
            layout.setDesFileType(FileType.TXT);
            layout.setDesEncoding("UTF-8");
            layouts.add(layout);
        }

        // All layouts have a simple rule that will match
        for (Layout layout : layouts) {
            LayoutIdentificationRule rule = new LayoutIdentificationRule();
            rule.setIdtRule(layout.getIdtLayout());
            rule.setIdtLayout(layout.getIdtLayout());
            rule.setDesRule("Rule for layout " + layout.getIdtLayout());
            rule.setDesValueOrigin(ValueOrigin.FILENAME);
            rule.setDesCriteriaType(CriteriaType.CONTEM);
            rule.setDesValue("test");
            rule.setFlgActive(1);

            when(ruleRepository.findByIdtLayoutAndFlgActive(layout.getIdtLayout(), 1))
                .thenReturn(Collections.singletonList(rule));
        }

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(layouts);
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");
        when(extractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(extractor.extractValue(any(byte[].class), anyString(), any(), any()))
            .thenReturn("test");
        when(criteriaComparator.compare(anyString(), anyString(), any(), any(), any()))
            .thenReturn(true);

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        Long result = service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Should return the first layout (highest ID)
        assert result != null : "Should identify a layout";
        assert result.equals(layouts.get(0).getIdtLayout()) : 
            String.format("Should return first layout (ID %d), but got %d", 
                layouts.get(0).getIdtLayout(), result);
    }

    /**
     * Feature: identificacao_layouts, Property 28: Retorno de idt_layout
     * 
     * For any identificação bem-sucedida, o serviço deve retornar o idt_layout
     * do layout identificado.
     * 
     * Validates: Requirements 4.7
     */
    @Property(tries = 100)
    void retornoIdtLayoutProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId,
        @ForAll @IntRange(min = 1, max = 1000) long layoutId
    ) {
        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        ValueExtractor extractor = Mockito.mock(ValueExtractor.class);
        List<ValueExtractor> extractors = Collections.singletonList(extractor);
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        Layout layout = new Layout();
        layout.setIdtLayout(layoutId);
        layout.setIdtAcquirer(acquirerId);
        layout.setFlgActive(1);
        layout.setDesFileType(FileType.TXT);
        layout.setDesEncoding("UTF-8");

        LayoutIdentificationRule rule = new LayoutIdentificationRule();
        rule.setIdtRule(1L);
        rule.setIdtLayout(layoutId);
        rule.setDesRule("Test rule");
        rule.setDesValueOrigin(ValueOrigin.FILENAME);
        rule.setDesCriteriaType(CriteriaType.CONTEM);
        rule.setDesValue("test");
        rule.setFlgActive(1);

        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.singletonList(layout));
        when(ruleRepository.findByIdtLayoutAndFlgActive(layoutId, 1))
            .thenReturn(Collections.singletonList(rule));
        when(encodingConverter.convertWithFallback(any(byte[].class), anyString()))
            .thenReturn("");
        when(extractor.supports(ValueOrigin.FILENAME, FileType.TXT))
            .thenReturn(true);
        when(extractor.extractValue(any(byte[].class), anyString(), any(), any()))
            .thenReturn("test");
        when(criteriaComparator.compare(anyString(), anyString(), any(), any(), any()))
            .thenReturn(true);

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        Long result = service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Should return the exact layout ID
        assert result != null : "Should identify a layout";
        assert result.equals(layoutId) : 
            String.format("Should return layout ID %d, but got %d", layoutId, result);
    }

    /**
     * Feature: identificacao_layouts, Property 28: Null quando não identificado
     * 
     * When no layout is identified, service should return null.
     */
    @Property(tries = 100)
    void retornoNullQuandoNaoIdentificadoProperty(
        @ForAll @IntRange(min = 1, max = 100) long acquirerId
    ) {
        // Arrange
        LayoutRepository layoutRepository = Mockito.mock(LayoutRepository.class);
        LayoutIdentificationRuleRepository ruleRepository = Mockito.mock(LayoutIdentificationRuleRepository.class);
        List<ValueExtractor> extractors = Collections.emptyList();
        CriteriaComparator criteriaComparator = Mockito.mock(CriteriaComparator.class);
        EncodingConverter encodingConverter = Mockito.mock(EncodingConverter.class);
        RuleValidator ruleValidator = Mockito.mock(RuleValidator.class);

        LayoutIdentificationService service = new LayoutIdentificationService(
            layoutRepository, ruleRepository, extractors, criteriaComparator, encodingConverter, ruleValidator
        );

        // No layouts found
        when(layoutRepository.findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc(acquirerId, 1))
            .thenReturn(Collections.emptyList());

        byte[] buffer = "test".getBytes();
        InputStream inputStream = new ByteArrayInputStream(buffer);

        // Act
        Long result = service.identifyLayout(inputStream, "test.txt", acquirerId);

        // Assert: Should return null when no layout is identified
        assert result == null : "Should return null when no layout is identified";
    }

    // Providers for arbitrary values

    @Provide
    Arbitrary<Layout> layoutGenerator() {
        return Arbitraries.longs().between(1, 1000).map(id -> {
            Layout layout = new Layout();
            layout.setIdtLayout(id);
            layout.setIdtAcquirer(1L);
            layout.setCodLayout("LAYOUT_" + id);
            layout.setDesFileType(FileType.TXT);
            layout.setDesEncoding("UTF-8");
            layout.setFlgActive(1);
            return layout;
        });
    }

    @Provide
    Arbitrary<LayoutIdentificationRule> ruleGenerator() {
        return Arbitraries.longs().between(1, 1000).map(id -> {
            LayoutIdentificationRule rule = new LayoutIdentificationRule();
            rule.setIdtRule(id);
            rule.setIdtLayout(100L);
            rule.setDesRule("Rule " + id);
            rule.setDesValueOrigin(ValueOrigin.FILENAME);
            rule.setDesCriteriaType(CriteriaType.CONTEM);
            rule.setDesValue("value");
            rule.setFlgActive(1);
            return rule;
        });
    }
}
