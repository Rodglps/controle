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
 * Extractor for HEADER value origin with CSV file type.
 * Extracts values using column index (num_start_position) and separator (des_column_separator).
 * Processes line by line within the buffer.
 */
@Component
public class HeaderCsvExtractor implements ValueExtractor {
    
    @Override
    public String extractValue(byte[] buffer, String filename, IdentificationRule rule, Layout layout) {
        if (rule.getNumStartPosition() == null || layout.getDesColumnSeparator() == null) {
            return null;
        }
        
        int columnIndex = rule.getNumStartPosition();
        String separator = layout.getDesColumnSeparator();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(buffer), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                // Try extraction on each line
                String extracted = extractFromLine(line, columnIndex, separator);
                if (extracted != null) {
                    return extracted;
                }
            }
            
            // No newline found - treat entire buffer as single line
            if (buffer.length > 0) {
                String entireBuffer = new String(buffer, StandardCharsets.UTF_8);
                return extractFromLine(entireBuffer, columnIndex, separator);
            }
            
        } catch (Exception e) {
            // Log and return null on error
            return null;
        }
        
        return null;
    }
    
    /**
     * Extracts value from a CSV line using column index.
     * 
     * @param line The CSV line to extract from
     * @param columnIndex Column index (0-indexed)
     * @param separator Column separator
     * @return Extracted column value or null if index is invalid
     */
    private String extractFromLine(String line, int columnIndex, String separator) {
        String[] columns = line.split(separator, -1); // -1 to preserve empty trailing columns
        
        if (columnIndex >= 0 && columnIndex < columns.length) {
            return columns[columnIndex];
        }
        
        return null;
    }
    
    @Override
    public boolean supports(ValueOrigin valueOrigin, FileType fileType) {
        return valueOrigin == ValueOrigin.HEADER && fileType == FileType.CSV;
    }
}
