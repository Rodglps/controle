package com.concil.edi.consumer.service;

import com.concil.edi.commons.enums.FunctionType;
import com.concil.edi.commons.service.TransformationApplier;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;

/**
 * Property-based tests for TransformationApplier.
 * 
 * Tests the following properties:
 * - Property 18: Transformação UPPERCASE (Requirements 12.1, 12.6)
 * - Property 19: Transformação LOWERCASE (Requirements 12.2, 12.7)
 * - Property 20: Transformação INITCAP (Requirements 12.3, 12.8)
 * - Property 21: Transformação TRIM (Requirements 12.4, 12.9)
 * - Property 22: Transformação NONE (Requirements 12.5, 12.10)
 */
public class TransformationApplierPropertyTest {

    private final TransformationApplier transformationApplier = new TransformationApplier();

    /**
     * Feature: identificacao_layouts, Property 18: Transformação UPPERCASE
     * 
     * For any valor e function_type igual a UPPERCASE,
     * a transformação deve converter todos os caracteres para maiúsculas.
     * 
     * Validates: Requirements 12.1, 12.6
     */
    @Property(tries = 100)
    void uppercaseTransformationProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.UPPERCASE);
        
        // Assert
        assert result.equals(value.toUpperCase()) : String.format(
            "UPPERCASE transformation of '%s' should equal '%s', but got '%s'",
            value, value.toUpperCase(), result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 18: UPPERCASE idempotence
     * 
     * Applying UPPERCASE twice should produce the same result as applying it once.
     */
    @Property(tries = 100)
    void uppercaseIdempotenceProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String firstApplication = transformationApplier.applyTransformation(value, FunctionType.UPPERCASE);
        String secondApplication = transformationApplier.applyTransformation(firstApplication, FunctionType.UPPERCASE);
        
        // Assert
        assert firstApplication.equals(secondApplication) : String.format(
            "UPPERCASE should be idempotent: '%s' != '%s'",
            firstApplication, secondApplication
        );
    }

    /**
     * Feature: identificacao_layouts, Property 18: UPPERCASE preserves length
     * 
     * UPPERCASE transformation should preserve string length for ASCII characters.
     * Note: Some Unicode characters may change length during case conversion.
     */
    @Property(tries = 100)
    void uppercasePreservesLengthProperty(
        @ForAll @StringLength(min = 1, max = 100) @AlphaChars String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.UPPERCASE);
        
        // Assert: For ASCII alpha chars, length should be preserved
        assert result.length() == value.length() : String.format(
            "UPPERCASE should preserve length for ASCII: original=%d, result=%d",
            value.length(), result.length()
        );
    }

    /**
     * Feature: identificacao_layouts, Property 19: Transformação LOWERCASE
     * 
     * For any valor e function_type igual a LOWERCASE,
     * a transformação deve converter todos os caracteres para minúsculas.
     * 
     * Validates: Requirements 12.2, 12.7
     */
    @Property(tries = 100)
    void lowercaseTransformationProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.LOWERCASE);
        
        // Assert
        assert result.equals(value.toLowerCase()) : String.format(
            "LOWERCASE transformation of '%s' should equal '%s', but got '%s'",
            value, value.toLowerCase(), result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 19: LOWERCASE idempotence
     * 
     * Applying LOWERCASE twice should produce the same result as applying it once.
     */
    @Property(tries = 100)
    void lowercaseIdempotenceProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String firstApplication = transformationApplier.applyTransformation(value, FunctionType.LOWERCASE);
        String secondApplication = transformationApplier.applyTransformation(firstApplication, FunctionType.LOWERCASE);
        
        // Assert
        assert firstApplication.equals(secondApplication) : String.format(
            "LOWERCASE should be idempotent: '%s' != '%s'",
            firstApplication, secondApplication
        );
    }

    /**
     * Feature: identificacao_layouts, Property 19: LOWERCASE preserves length
     * 
     * LOWERCASE transformation should preserve string length for ASCII characters.
     */
    @Property(tries = 100)
    void lowercasePreservesLengthProperty(
        @ForAll @StringLength(min = 1, max = 100) @AlphaChars String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.LOWERCASE);
        
        // Assert
        assert result.length() == value.length() : String.format(
            "LOWERCASE should preserve length for ASCII: original=%d, result=%d",
            value.length(), result.length()
        );
    }

    /**
     * Feature: identificacao_layouts, Property 20: Transformação INITCAP
     * 
     * For any valor e function_type igual a INITCAP,
     * a transformação deve converter a primeira letra para maiúscula e as demais para minúsculas.
     * 
     * Validates: Requirements 12.3, 12.8
     */
    @Property(tries = 100)
    void initcapTransformationProperty(
        @ForAll @StringLength(min = 1, max = 100) @AlphaChars String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.INITCAP);
        
        // Assert: First character should be uppercase
        assert Character.isUpperCase(result.charAt(0)) : String.format(
            "INITCAP first character should be uppercase: '%s'",
            result
        );
        
        // Assert: Remaining characters should be lowercase
        if (result.length() > 1) {
            String remaining = result.substring(1);
            assert remaining.equals(remaining.toLowerCase()) : String.format(
                "INITCAP remaining characters should be lowercase: '%s'",
                remaining
            );
        }
    }

    /**
     * Feature: identificacao_layouts, Property 20: INITCAP with leading whitespace
     * 
     * INITCAP should find the first letter even with leading whitespace.
     */
    @Property(tries = 100)
    void initcapWithLeadingWhitespaceProperty(
        @ForAll @IntRange(min = 1, max = 5) int leadingSpaces,
        @ForAll @StringLength(min = 1, max = 50) @AlphaChars String value
    ) {
        // Arrange
        String input = " ".repeat(leadingSpaces) + value;
        
        // Act
        String result = transformationApplier.applyTransformation(input, FunctionType.INITCAP);
        
        // Assert: Should have leading spaces in lowercase
        for (int i = 0; i < leadingSpaces; i++) {
            assert result.charAt(i) == ' ' : "Leading whitespace should be preserved";
        }
        
        // Assert: First letter after spaces should be uppercase
        assert Character.isUpperCase(result.charAt(leadingSpaces)) : String.format(
            "First letter after whitespace should be uppercase: '%s'",
            result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 20: INITCAP idempotence
     * 
     * Applying INITCAP twice should produce the same result as applying it once.
     */
    @Property(tries = 100)
    void initcapIdempotenceProperty(
        @ForAll @StringLength(min = 1, max = 100) @AlphaChars String value
    ) {
        // Act
        String firstApplication = transformationApplier.applyTransformation(value, FunctionType.INITCAP);
        String secondApplication = transformationApplier.applyTransformation(firstApplication, FunctionType.INITCAP);
        
        // Assert
        assert firstApplication.equals(secondApplication) : String.format(
            "INITCAP should be idempotent: '%s' != '%s'",
            firstApplication, secondApplication
        );
    }

    /**
     * Feature: identificacao_layouts, Property 20: INITCAP preserves length
     * 
     * INITCAP transformation should preserve string length.
     */
    @Property(tries = 100)
    void initcapPreservesLengthProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.INITCAP);
        
        // Assert
        assert result.length() == value.length() : String.format(
            "INITCAP should preserve length: original=%d, result=%d",
            value.length(), result.length()
        );
    }

    /**
     * Feature: identificacao_layouts, Property 20: INITCAP with empty string
     * 
     * INITCAP on empty string should return empty string.
     */
    @Property(tries = 10)
    void initcapEmptyStringProperty() {
        // Act
        String result = transformationApplier.applyTransformation("", FunctionType.INITCAP);
        
        // Assert
        assert result.isEmpty() : "INITCAP on empty string should return empty string";
    }

    /**
     * Feature: identificacao_layouts, Property 21: Transformação TRIM
     * 
     * For any valor e function_type igual a TRIM,
     * a transformação deve remover espaços em branco no início e fim do valor.
     * 
     * Validates: Requirements 12.4, 12.9
     */
    @Property(tries = 100)
    void trimTransformationProperty(
        @ForAll @StringLength(min = 1, max = 50) String value,
        @ForAll @IntRange(min = 0, max = 10) int leadingSpaces,
        @ForAll @IntRange(min = 0, max = 10) int trailingSpaces
    ) {
        // Arrange
        String input = " ".repeat(leadingSpaces) + value + " ".repeat(trailingSpaces);
        
        // Act
        String result = transformationApplier.applyTransformation(input, FunctionType.TRIM);
        
        // Assert
        assert result.equals(value.trim()) : String.format(
            "TRIM transformation of '%s' should equal '%s', but got '%s'",
            input, value.trim(), result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 21: TRIM removes only leading and trailing spaces
     * 
     * TRIM should not remove spaces in the middle of the string.
     */
    @Property(tries = 100)
    void trimPreservesInternalSpacesProperty(
        @ForAll @StringLength(min = 1, max = 25) @AlphaChars String prefix,
        @ForAll @StringLength(min = 1, max = 25) @AlphaChars String suffix,
        @ForAll @IntRange(min = 1, max = 5) int middleSpaces,
        @ForAll @IntRange(min = 0, max = 5) int leadingSpaces,
        @ForAll @IntRange(min = 0, max = 5) int trailingSpaces
    ) {
        // Arrange
        String expectedMiddle = prefix.trim() + " ".repeat(middleSpaces) + suffix.trim();
        String input = " ".repeat(leadingSpaces) + prefix + " ".repeat(middleSpaces) + suffix + " ".repeat(trailingSpaces);
        
        // Act
        String result = transformationApplier.applyTransformation(input, FunctionType.TRIM);
        
        // Assert
        assert result.equals(expectedMiddle) : String.format(
            "TRIM should preserve internal spaces: expected '%s', got '%s'",
            expectedMiddle, result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 21: TRIM idempotence
     * 
     * Applying TRIM twice should produce the same result as applying it once.
     */
    @Property(tries = 100)
    void trimIdempotenceProperty(
        @ForAll @StringLength(min = 1, max = 100) String value,
        @ForAll @IntRange(min = 0, max = 10) int leadingSpaces,
        @ForAll @IntRange(min = 0, max = 10) int trailingSpaces
    ) {
        // Arrange
        String input = " ".repeat(leadingSpaces) + value + " ".repeat(trailingSpaces);
        
        // Act
        String firstApplication = transformationApplier.applyTransformation(input, FunctionType.TRIM);
        String secondApplication = transformationApplier.applyTransformation(firstApplication, FunctionType.TRIM);
        
        // Assert
        assert firstApplication.equals(secondApplication) : String.format(
            "TRIM should be idempotent: '%s' != '%s'",
            firstApplication, secondApplication
        );
    }

    /**
     * Feature: identificacao_layouts, Property 21: TRIM with only whitespace
     * 
     * TRIM on string with only whitespace should return empty string.
     */
    @Property(tries = 100)
    void trimOnlyWhitespaceProperty(
        @ForAll @IntRange(min = 1, max = 20) int spaces
    ) {
        // Arrange
        String input = " ".repeat(spaces);
        
        // Act
        String result = transformationApplier.applyTransformation(input, FunctionType.TRIM);
        
        // Assert
        assert result.isEmpty() : String.format(
            "TRIM on whitespace-only string should return empty string, got '%s'",
            result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 22: Transformação NONE
     * 
     * For any valor e function_type igual a NONE ou NULL,
     * nenhuma transformação deve ser aplicada ao valor.
     * 
     * Validates: Requirements 12.5, 12.10
     */
    @Property(tries = 100)
    void noneTransformationProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.NONE);
        
        // Assert
        assert result.equals(value) : String.format(
            "NONE transformation should not modify value: expected '%s', got '%s'",
            value, result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 22: NULL function type behaves as NONE
     * 
     * When function_type is NULL, no transformation should be applied.
     */
    @Property(tries = 100)
    void nullFunctionTypeProperty(
        @ForAll @StringLength(min = 1, max = 100) String value
    ) {
        // Act
        String result = transformationApplier.applyTransformation(value, null);
        
        // Assert
        assert result.equals(value) : String.format(
            "NULL function type should not modify value: expected '%s', got '%s'",
            value, result
        );
    }

    /**
     * Feature: identificacao_layouts, Property 22: NONE preserves all characters
     * 
     * NONE transformation should preserve all characters including whitespace and special chars.
     */
    @Property(tries = 100)
    void nonePreservesAllCharactersProperty(
        @ForAll @StringLength(min = 1, max = 50) String prefix,
        @ForAll @IntRange(min = 1, max = 5) int spaces,
        @ForAll @StringLength(min = 1, max = 50) String suffix
    ) {
        // Arrange
        String value = prefix + " ".repeat(spaces) + suffix;
        
        // Act
        String result = transformationApplier.applyTransformation(value, FunctionType.NONE);
        
        // Assert
        assert result.equals(value) : String.format(
            "NONE should preserve all characters: expected '%s', got '%s'",
            value, result
        );
    }

    /**
     * Null value handling property
     * 
     * For any function type, transformation of null value should return null.
     */
    @Property(tries = 100)
    void nullValueHandlingProperty(
        @ForAll("functionTypes") FunctionType functionType
    ) {
        // Act
        String result = transformationApplier.applyTransformation(null, functionType);
        
        // Assert
        assert result == null : String.format(
            "Transformation of null should return null for %s",
            functionType
        );
    }

    /**
     * Composition property: UPPERCASE then LOWERCASE equals LOWERCASE
     * 
     * Validates that transformations can be composed (for ASCII characters).
     */
    @Property(tries = 100)
    void compositionUpperThenLowerProperty(
        @ForAll @StringLength(min = 1, max = 100) @AlphaChars String value
    ) {
        // Act
        String upperThenLower = transformationApplier.applyTransformation(
            transformationApplier.applyTransformation(value, FunctionType.UPPERCASE),
            FunctionType.LOWERCASE
        );
        String directLower = transformationApplier.applyTransformation(value, FunctionType.LOWERCASE);
        
        // Assert
        assert upperThenLower.equals(directLower) : String.format(
            "UPPERCASE then LOWERCASE should equal direct LOWERCASE: '%s' != '%s'",
            upperThenLower, directLower
        );
    }

    /**
     * Composition property: TRIM then UPPERCASE equals UPPERCASE then TRIM
     * 
     * Validates that TRIM and case transformations commute.
     */
    @Property(tries = 100)
    void compositionTrimUpperCommutesProperty(
        @ForAll @StringLength(min = 1, max = 50) String value,
        @ForAll @IntRange(min = 0, max = 5) int leadingSpaces,
        @ForAll @IntRange(min = 0, max = 5) int trailingSpaces
    ) {
        // Arrange
        String input = " ".repeat(leadingSpaces) + value + " ".repeat(trailingSpaces);
        
        // Act
        String trimThenUpper = transformationApplier.applyTransformation(
            transformationApplier.applyTransformation(input, FunctionType.TRIM),
            FunctionType.UPPERCASE
        );
        String upperThenTrim = transformationApplier.applyTransformation(
            transformationApplier.applyTransformation(input, FunctionType.UPPERCASE),
            FunctionType.TRIM
        );
        
        // Assert
        assert trimThenUpper.equals(upperThenTrim) : String.format(
            "TRIM then UPPERCASE should equal UPPERCASE then TRIM: '%s' != '%s'",
            trimThenUpper, upperThenTrim
        );
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
}
