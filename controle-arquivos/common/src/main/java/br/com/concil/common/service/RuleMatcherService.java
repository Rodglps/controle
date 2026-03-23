package br.com.concil.common.service;

import org.springframework.stereotype.Service;

/**
 * Aplica os critérios de identificação (COMECA-COM, TERMINA-COM, CONTEM, IGUAL)
 * sobre um valor extraído do nome do arquivo ou do conteúdo.
 */
@Service
public class RuleMatcherService {

    public boolean matches(String criterionType, String subject, String expectedValue,
                           Integer startPos, Integer endPos) {
        if (subject == null || expectedValue == null) return false;

        String target = extractSubstring(subject, startPos, endPos);

        return switch (criterionType) {
            case "COMECA-COM"  -> target.startsWith(expectedValue);
            case "TERMINA-COM" -> target.endsWith(expectedValue);
            case "CONTEM"      -> target.contains(expectedValue);
            case "IGUAL"       -> target.equals(expectedValue);
            default -> {
                throw new IllegalArgumentException("Critério desconhecido: " + criterionType);
            }
        };
    }

    private String extractSubstring(String value, Integer startPos, Integer endPos) {
        if (startPos == null && endPos == null) return value;
        int start = (startPos != null) ? Math.max(0, startPos - 1) : 0;
        int end   = (endPos   != null) ? Math.min(value.length(), endPos) : value.length();
        if (start >= end || start >= value.length()) return "";
        return value.substring(start, end);
    }
}
