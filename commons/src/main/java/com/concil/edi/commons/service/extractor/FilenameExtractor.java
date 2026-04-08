package com.concil.edi.commons.service.extractor;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ValueOrigin;
import org.springframework.stereotype.Component;

/**
 * Extractor for FILENAME value origin.
 * Returns the filename without extension as the extracted value.
 * 
 * Example: "cielo_v15_venda_123456.txt" -> "cielo_v15_venda_123456"
 */
@Component
public class FilenameExtractor implements ValueExtractor {
    
    @Override
    public String extractValue(byte[] buffer, String filename, IdentificationRule rule, Layout layout) {
        // Remove file extension for comparison
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(0, lastDotIndex);
        }
        return filename;
    }
    
    @Override
    public boolean supports(ValueOrigin valueOrigin, FileType fileType) {
        return valueOrigin == ValueOrigin.FILENAME;
    }
}
