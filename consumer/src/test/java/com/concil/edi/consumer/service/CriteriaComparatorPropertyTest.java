package com.concil.edi.consumer.service;

import com.concil.edi.commons.enums.CriteriaType;
import com.concil.edi.commons.enums.FunctionType;
import com.concil.edi.commons.service.CriteriaComparator;
import com.concil.edi.commons.service.TransformationApplier;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;

/**
 * Property-based tests for CriteriaComparator.
 * 
 * Tests the following properties:
 * - Property 1: Critério COMECA_COM (Requirements 5.1)
 * - Property 2: Critério TERMINA_COM (Requirements 5.2)
 * - Property 3: Critério CONTEM (Requirements 5.3)
 * - Property 4: Critério CONTIDO (Requirements 5.4)
 * - Property 5: Critério IGUAL (Requirements 5.5)
 * - Property 6: Transformações aplicadas antes da comparação (Requirements 5.6)
 * - Property 7: Comparação case-sensitive por padrão (Requirements 5.7)
 */
public class CriteriaComparatorPropertyTest {

    private final TransformationApplier transformationApplier = new TransformationApplier();
    private final CriteriaComparator criteriaComparator = new CriteriaComparator(transformationApplier);

    /**
     * Feature: identificacao_layouts, Property 1: Critério COMECA_COM
     * 
     * For any origin value e expected value, quando des_criteria_type é COMECA_COM,
     * a comparação deve retornar true se e somente se origin value começa com expected value.
     * 
     * Validates: Requirements 5.1
     */
    @Property(tries = 100)
    void criterioComecaComProperty(
        @ForAll @StringLength(min = 1, max = 50) String prefix,
        @ForAll @StringLength(min = 0, max = 50) String suffix
    ) {
        // Arrange
        String originValue = prefix + suffix;
        String expectedValue = prefix;
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.COMECA_COM);
        
        // Assert
        assert result : String.format(
            "Origin value '%s' should start with expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 1: Critério COMECA_COM (negative case)
     * 
     * For any origin value that does not start with expected value,
     * COMECA_COM should return false.
     */
    @Property(tries = 100)
    void criterioComecaComNegativeProperty(
        @ForAll @StringLength(min = 1, max = 50) String originValue,
        @ForAll @StringLength(min = 1, max = 50) String expectedValue
    ) {
        Assume.that(!originValue.startsWith(expectedValue));
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.COMECA_COM);
        
        // Assert
        assert !result : String.format(
            "Origin value '%s' should not start with expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 2: Critério TERMINA_COM
     * 
     * For any origin value e expected value, quando des_criteria_type é TERMINA_COM,
     * a comparação deve retornar true se e somente se origin value termina com expected value.
     * 
     * Validates: Requirements 5.2
     */
    @Property(tries = 100)
    void criterioTerminaComProperty(
        @ForAll @StringLength(min = 0, max = 50) String prefix,
        @ForAll @StringLength(min = 1, max = 50) String suffix
    ) {
        // Arrange
        String originValue = prefix + suffix;
        String expectedValue = suffix;
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.TERMINA_COM);
        
        // Assert
        assert result : String.format(
            "Origin value '%s' should end with expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 2: Critério TERMINA_COM (negative case)
     * 
     * For any origin value that does not end with expected value,
     * TERMINA_COM should return false.
     */
    @Property(tries = 100)
    void criterioTerminaComNegativeProperty(
        @ForAll @StringLength(min = 1, max = 50) String originValue,
        @ForAll @StringLength(min = 1, max = 50) String expectedValue
    ) {
        Assume.that(!originValue.endsWith(expectedValue));
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.TERMINA_COM);
        
        // Assert
        assert !result : String.format(
            "Origin value '%s' should not end with expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 3: Critério CONTEM
     * 
     * For any origin value e expected value, quando des_criteria_type é CONTEM,
     * a comparação deve retornar true se e somente se origin value contém expected value.
     * 
     * Validates: Requirements 5.3
     */
    @Property(tries = 100)
    void criterioContemProperty(
        @ForAll @StringLength(min = 0, max = 25) String prefix,
        @ForAll @StringLength(min = 1, max = 25) String middle,
        @ForAll @StringLength(min = 0, max = 25) String suffix
    ) {
        // Arrange
        String originValue = prefix + middle + suffix;
        String expectedValue = middle;
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.CONTEM);
        
        // Assert
        assert result : String.format(
            "Origin value '%s' should contain expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 3: Critério CONTEM (negative case)
     * 
     * For any origin value that does not contain expected value,
     * CONTEM should return false.
     */
    @Property(tries = 100)
    void criterioContemNegativeProperty(
        @ForAll @StringLength(min = 1, max = 50) String originValue,
        @ForAll @StringLength(min = 1, max = 50) String expectedValue
    ) {
        Assume.that(!originValue.contains(expectedValue));
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.CONTEM);
        
        // Assert
        assert !result : String.format(
            "Origin value '%s' should not contain expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 4: Critério CONTIDO
     * 
     * For any origin value e expected value, quando des_criteria_type é CONTIDO,
     * a comparação deve retornar true se e somente se origin value está contido em expected value.
     * 
     * Validates: Requirements 5.4
     */
    @Property(tries = 100)
    void criterioContidoProperty(
        @ForAll @StringLength(min = 0, max = 25) String prefix,
        @ForAll @StringLength(min = 1, max = 25) String middle,
        @ForAll @StringLength(min = 0, max = 25) String suffix
    ) {
        // Arrange
        String originValue = middle;
        String expectedValue = prefix + middle + suffix;
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.CONTIDO);
        
        // Assert
        assert result : String.format(
            "Origin value '%s' should be contained in expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 4: Critério CONTIDO (negative case)
     * 
     * For any origin value that is not contained in expected value,
     * CONTIDO should return false.
     */
    @Property(tries = 100)
    void criterioContidoNegativeProperty(
        @ForAll @StringLength(min = 1, max = 50) String originValue,
        @ForAll @StringLength(min = 1, max = 50) String expectedValue
    ) {
        Assume.that(!expectedValue.contains(originValue));
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.CONTIDO);
        
        // Assert
        assert !result : String.format(
            "Origin value '%s' should not be contained in expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 5: Critério IGUAL
     * 
     * For any origin value e expected value, quando des_criteria_type é IGUAL,
     * a comparação deve retornar true se e somente se origin value é exatamente igual a expected value.
     * 
     * Validates: Requirements 5.5
     */
    @Property(tries = 100)
    void criterioIgualProperty(
        @ForAll @StringLength(min = 1, max = 50) String value
    ) {
        // Arrange
        String originValue = value;
        String expectedValue = value;
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.IGUAL);
        
        // Assert
        assert result : String.format(
            "Origin value '%s' should be equal to expected value '%s'", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 5: Critério IGUAL (negative case)
     * 
     * For any origin value that is not equal to expected value,
     * IGUAL should return false.
     */
    @Property(tries = 100)
    void criterioIgualNegativeProperty(
        @ForAll @StringLength(min = 1, max = 50) String originValue,
        @ForAll @StringLength(min = 1, max = 50) String expectedValue
    ) {
        Assume.that(!originValue.equals(expectedValue));
        
        // Act
        boolean result = criteriaComparator.compare(originValue, expectedValue, CriteriaType.IGUAL);
        
        // Assert
        assert !result : String.format(
            "Origin value '%s' should not be equal to expected value '%s'", 
            originValue, expectedValue
        );
    }


    /**
     * Feature: identificacao_layouts, Property 6: Transformações aplicadas antes da comparação
     * 
     * For any rule com des_function_origin ou des_function_dest definidos,
     * as transformações devem ser aplicadas nos valores antes da execução da comparação do critério.
     * 
     * Validates: Requirements 5.6
     */
    @Property(tries = 100)
    void transformacoesAplicadasAntesComparacaoProperty(
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String baseValue,
        @ForAll("functionTypes") FunctionType functionOrigin,
        @ForAll("functionTypes") FunctionType functionDest
    ) {
        // Arrange: Create values that will match after transformation
        String originValue = baseValue.toLowerCase();
        String expectedValue = baseValue.toUpperCase();
        
        // Act: Compare with UPPERCASE transformation on origin and LOWERCASE on dest
        // After transformation: both become same case
        boolean resultWithTransform = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            FunctionType.UPPERCASE,
            FunctionType.UPPERCASE
        );
        
        // Compare without transformation (should fail due to case difference)
        boolean resultWithoutTransform = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            FunctionType.NONE,
            FunctionType.NONE
        );
        
        // Assert: With transformation should match, without should not
        assert resultWithTransform : String.format(
            "Values '%s' and '%s' should match after UPPERCASE transformation", 
            originValue, expectedValue
        );
        
        assert !resultWithoutTransform : String.format(
            "Values '%s' and '%s' should not match without transformation", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 6: UPPERCASE transformation before comparison
     * 
     * Validates that UPPERCASE transformation is applied before comparison.
     */
    @Property(tries = 100)
    void uppercaseTransformationProperty(
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String value
    ) {
        // Arrange
        String originValue = value.toLowerCase();
        String expectedValue = value.toUpperCase();
        
        // Act: Compare with UPPERCASE on both
        boolean result = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            FunctionType.UPPERCASE,
            FunctionType.UPPERCASE
        );
        
        // Assert
        assert result : String.format(
            "Values '%s' and '%s' should match after UPPERCASE transformation", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 6: LOWERCASE transformation before comparison
     * 
     * Validates that LOWERCASE transformation is applied before comparison.
     */
    @Property(tries = 100)
    void lowercaseTransformationProperty(
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String value
    ) {
        // Arrange
        String originValue = value.toUpperCase();
        String expectedValue = value.toLowerCase();
        
        // Act: Compare with LOWERCASE on both
        boolean result = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            FunctionType.LOWERCASE,
            FunctionType.LOWERCASE
        );
        
        // Assert
        assert result : String.format(
            "Values '%s' and '%s' should match after LOWERCASE transformation", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 6: TRIM transformation before comparison
     * 
     * Validates that TRIM transformation is applied before comparison.
     */
    @Property(tries = 100)
    void trimTransformationProperty(
        @ForAll @StringLength(min = 1, max = 50) String value,
        @ForAll @IntRange(min = 0, max = 5) int leadingSpaces,
        @ForAll @IntRange(min = 0, max = 5) int trailingSpaces
    ) {
        // Arrange
        String originValue = " ".repeat(leadingSpaces) + value + " ".repeat(trailingSpaces);
        String expectedValue = value.trim();
        
        // Act: Compare with TRIM on origin
        boolean result = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            FunctionType.TRIM,
            FunctionType.NONE
        );
        
        // Assert
        assert result : String.format(
            "Values '%s' and '%s' should match after TRIM transformation", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 7: Comparação case-sensitive por padrão
     * 
     * For any rule sem funções de transformação (NONE ou NULL),
     * a comparação deve ser case-sensitive, distinguindo entre maiúsculas e minúsculas.
     * 
     * Validates: Requirements 5.7
     */
    @Property(tries = 100)
    void comparacaoCaseSensitiveProperty(
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String value
    ) {
        // Arrange: Create values with different cases
        String originValue = value.toLowerCase();
        String expectedValue = value.toUpperCase();
        
        // Assume they are actually different (not all non-alphabetic)
        Assume.that(!originValue.equals(expectedValue));
        
        // Act: Compare without transformation (NONE)
        boolean resultWithNone = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            FunctionType.NONE,
            FunctionType.NONE
        );
        
        // Compare without transformation (null)
        boolean resultWithNull = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.IGUAL,
            null,
            null
        );
        
        // Assert: Both should fail due to case difference
        assert !resultWithNone : String.format(
            "Values '%s' and '%s' should not match without transformation (case-sensitive)", 
            originValue, expectedValue
        );
        
        assert !resultWithNull : String.format(
            "Values '%s' and '%s' should not match with null transformation (case-sensitive)", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 7: Case-sensitive COMECA_COM
     * 
     * Validates that COMECA_COM is case-sensitive without transformations.
     */
    @Property(tries = 100)
    void comecaComCaseSensitiveProperty(
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String prefix
    ) {
        // Arrange
        String originValue = prefix.toLowerCase() + "suffix";
        String expectedValue = prefix.toUpperCase();
        
        // Assume they are actually different
        Assume.that(!prefix.toLowerCase().equals(prefix.toUpperCase()));
        
        // Act
        boolean result = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.COMECA_COM,
            FunctionType.NONE,
            FunctionType.NONE
        );
        
        // Assert: Should not match due to case difference
        assert !result : String.format(
            "Origin value '%s' should not start with '%s' (case-sensitive)", 
            originValue, expectedValue
        );
    }

    /**
     * Feature: identificacao_layouts, Property 7: Case-sensitive CONTEM
     * 
     * Validates that CONTEM is case-sensitive without transformations.
     */
    @Property(tries = 100)
    void contemCaseSensitiveProperty(
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String middle
    ) {
        // Arrange
        String originValue = "prefix" + middle.toLowerCase() + "suffix";
        String expectedValue = middle.toUpperCase();
        
        // Assume they are actually different
        Assume.that(!middle.toLowerCase().equals(middle.toUpperCase()));
        
        // Act
        boolean result = criteriaComparator.compare(
            originValue, 
            expectedValue, 
            CriteriaType.CONTEM,
            FunctionType.NONE,
            FunctionType.NONE
        );
        
        // Assert: Should not match due to case difference
        assert !result : String.format(
            "Origin value '%s' should not contain '%s' (case-sensitive)", 
            originValue, expectedValue
        );
    }

    /**
     * Null value handling property
     * 
     * For any criteria type, comparison with null values should return false.
     */
    @Property(tries = 100)
    void nullValueHandlingProperty(
        @ForAll("criteriaTypes") CriteriaType criteriaType
    ) {
        // Act & Assert: All combinations with null should return false
        assert !criteriaComparator.compare(null, "value", criteriaType) : 
            "Comparison with null origin should return false";
        
        assert !criteriaComparator.compare("value", null, criteriaType) : 
            "Comparison with null expected should return false";
        
        assert !criteriaComparator.compare(null, null, criteriaType) : 
            "Comparison with both null should return false";
    }

    // Providers for arbitrary values

    @Provide
    Arbitrary<FunctionType> functionTypes() {
        return Arbitraries.of(
            FunctionType.UPPERCASE,
            FunctionType.LOWERCASE,
            FunctionType.INITCAP,
            FunctionType.TRIM,
            FunctionType.NONE
        );
    }

    @Provide
    Arbitrary<CriteriaType> criteriaTypes() {
        return Arbitraries.of(
            CriteriaType.COMECA_COM,
            CriteriaType.TERMINA_COM,
            CriteriaType.CONTEM,
            CriteriaType.CONTIDO,
            CriteriaType.IGUAL
        );
    }
}
