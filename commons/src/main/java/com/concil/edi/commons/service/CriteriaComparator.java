package com.concil.edi.commons.service;

import com.concil.edi.commons.enums.CriteriaType;
import com.concil.edi.commons.enums.FunctionType;
import org.springframework.stereotype.Component;

/**
 * Component responsible for comparing values based on different criteria types.
 * Supports COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, and IGUAL comparisons.
 * 
 * Applies transformation functions before comparison to normalize values.
 * Shared between layout identification and customer identification.
 */
@Component
public class CriteriaComparator {

    private final TransformationApplier transformationApplier;

    public CriteriaComparator(TransformationApplier transformationApplier) {
        this.transformationApplier = transformationApplier;
    }

    /**
     * Compares origin value with expected value based on the specified criteria type.
     * Applies transformation functions before comparison.
     * 
     * @param originValue The value extracted from the file
     * @param expectedValue The expected value from the rule (des_value)
     * @param criteriaType The type of comparison to perform
     * @return true if the comparison satisfies the criteria, false otherwise
     */
    public boolean compare(String originValue, String expectedValue, CriteriaType criteriaType) {
        return compare(originValue, expectedValue, criteriaType, null, null);
    }

    /**
     * Compares origin value with expected value based on the specified criteria type.
     * Applies transformation functions before comparison.
     * 
     * @param originValue The value extracted from the file
     * @param expectedValue The expected value from the rule (des_value)
     * @param criteriaType The type of comparison to perform
     * @param functionOrigin Transformation function for origin value (can be null)
     * @param functionDest Transformation function for expected value (can be null)
     * @return true if the comparison satisfies the criteria, false otherwise
     */
    public boolean compare(String originValue, String expectedValue, CriteriaType criteriaType,
                          FunctionType functionOrigin, FunctionType functionDest) {
        // Handle null values
        if (originValue == null || expectedValue == null) {
            return false;
        }

        // Apply transformations
        String transformedOrigin = transformationApplier.applyTransformation(originValue, functionOrigin);
        String transformedExpected = transformationApplier.applyTransformation(expectedValue, functionDest);

        // Handle null results from transformation
        if (transformedOrigin == null || transformedExpected == null) {
            return false;
        }

        // Perform comparison based on criteria type
        return switch (criteriaType) {
            case COMECA_COM -> transformedOrigin.startsWith(transformedExpected);
            case TERMINA_COM -> transformedOrigin.endsWith(transformedExpected);
            case CONTEM -> transformedOrigin.contains(transformedExpected);
            case CONTIDO -> transformedExpected.contains(transformedOrigin);
            case IGUAL -> transformedOrigin.equals(transformedExpected);
        };
    }
}
