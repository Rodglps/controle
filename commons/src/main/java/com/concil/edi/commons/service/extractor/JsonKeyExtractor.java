package com.concil.edi.commons.service.extractor;

import com.concil.edi.commons.entity.Layout;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ValueOrigin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * Extractor for KEY value origin (JSON files).
 * Extracts values using dot notation paths from des_key field.
 * Supports nested paths within the buffer.
 */
@Component
public class JsonKeyExtractor implements ValueExtractor {
    
    private final ObjectMapper objectMapper;
    
    public JsonKeyExtractor() {
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public String extractValue(byte[] buffer, String filename, IdentificationRule rule, Layout layout) {
        if (rule.getDesKey() == null || rule.getDesKey().isEmpty()) {
            return null;
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(new ByteArrayInputStream(buffer));
            JsonNode targetNode = navigateJsonPath(rootNode, rule.getDesKey());
            
            if (targetNode != null && !targetNode.isNull()) {
                return targetNode.asText();
            }
            
        } catch (Exception e) {
            // Log warning and return null on error (malformed JSON or invalid path)
            return null;
        }
        
        return null;
    }
    
    /**
     * Navigates a JSON path using dot notation.
     * 
     * @param rootNode The root JSON node
     * @param path Dot-separated path (e.g., "header.version" or "data.items.0.name")
     * @return The target node or null if path is invalid
     */
    private JsonNode navigateJsonPath(JsonNode rootNode, String path) {
        String[] parts = path.split("\\.");
        JsonNode currentNode = rootNode;
        
        for (String part : parts) {
            if (currentNode == null || currentNode.isNull()) {
                return null;
            }
            
            // Check if part is an array index
            if (part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                if (currentNode.isArray() && index < currentNode.size()) {
                    currentNode = currentNode.get(index);
                } else {
                    return null;
                }
            } else {
                currentNode = currentNode.get(part);
            }
        }
        
        return currentNode;
    }
    
    @Override
    public boolean supports(ValueOrigin valueOrigin, FileType fileType) {
        return valueOrigin == ValueOrigin.KEY;
    }
}
