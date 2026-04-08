package com.concil.edi.commons.service.extractor;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ValueOrigin;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Extractor for HEADER value origin with TXT file type.
 * Extracts values using byte offset positions (num_start_position, num_end_position).
 * Processes line by line within the buffer.
 */
@Component
public class HeaderTxtExtractor implements ValueExtractor {
    
    @Override
    public String extractValue(byte[] buffer, String filename, IdentificationRule rule, Layout layout) {
        if (rule.getNumStartPosition() == null) {
            return null;
        }
        
        // Convert from 1-based (DDL convention) to 0-based (Java array index)
        int startPos = rule.getNumStartPosition() - 1;
        Integer endPos = rule.getNumEndPosition() != null ? rule.getNumEndPosition() : null;
        
        if (startPos < 0) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(buffer), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // Try extraction on each line
                String extracted = extractFromLine(line, startPos, endPos);
                if (extracted != null) {
                    return extracted;
                }
            }
            
            // No newline found - treat entire buffer as single line
            if (buffer.length > 0) {
                String entireBuffer = new String(buffer, StandardCharsets.UTF_8);
                return extractFromLine(entireBuffer, startPos, endPos);
            }
            
        } catch (Exception e) {
            // Log and return null on error
            return null;
        }
        
        return null;
    }
    
    /**
     * Extracts substring from a line using byte positions.
     * 
     * @param line The line to extract from
     * @param startPos Start position (0-indexed, already converted from 1-based)
     * @param endPos End position (1-based from DDL, used as exclusive upper bound; null means end of line)
     * @return Extracted substring or null if positions are invalid
     */
    private String extractFromLine(String line, int startPos, Integer endPos) {
        byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
        
        if (startPos >= lineBytes.length) {
            return null;
        }
        
        int actualEndPos = (endPos == null) ? lineBytes.length : Math.min(endPos, lineBytes.length);
        
        if (actualEndPos <= startPos) {
            return null;
        }
        
        byte[] extracted = new byte[actualEndPos - startPos];
        System.arraycopy(lineBytes, startPos, extracted, 0, extracted.length);
        
        return new String(extracted, StandardCharsets.UTF_8);
    }
    
    @Override
    public boolean supports(ValueOrigin valueOrigin, FileType fileType) {
        return valueOrigin == ValueOrigin.HEADER && fileType == FileType.TXT;
    }
}
