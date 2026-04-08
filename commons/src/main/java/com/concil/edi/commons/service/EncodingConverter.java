package com.concil.edi.commons.service;

import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Component responsible for detecting and converting file encodings.
 * Shared between layout identification and customer identification.
 * 
 * Implements a fallback chain for encoding conversion:
 * 1. Try converting to des_encoding (target encoding from layout configuration)
 * 2. If that fails, try converting to UTF-8
 * 3. If that fails, use the detected encoding
 * 
 * This ensures maximum compatibility with files that may have different encodings
 * than expected.
 */
@Component
public class EncodingConverter {
    
    private static final Logger logger = LoggerFactory.getLogger(EncodingConverter.class);
    
    /**
     * Detects the encoding of a byte buffer using Apache Tika.
     * 
     * @param buffer Buffer of bytes to analyze
     * @return Detected encoding name (e.g., "UTF-8", "ISO-8859-1", "Windows-1252")
     */
    public String detectEncoding(byte[] buffer) {
        if (buffer == null || buffer.length == 0) {
            logger.warn("Empty buffer provided for encoding detection, defaulting to UTF-8");
            return StandardCharsets.UTF_8.name();
        }
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);
            
            // Use AutoDetectReader to detect encoding
            AutoDetectReader reader = new AutoDetectReader(inputStream);
            Charset detectedCharset = reader.getCharset();
            
            if (detectedCharset != null) {
                String encoding = detectedCharset.name();
                logger.debug("Detected encoding: {}", encoding);
                reader.close();
                return encoding;
            } else {
                logger.warn("Could not detect encoding, defaulting to UTF-8");
                reader.close();
                return StandardCharsets.UTF_8.name();
            }
        } catch (IOException | TikaException e) {
            logger.error("Error detecting encoding, defaulting to UTF-8", e);
            return StandardCharsets.UTF_8.name();
        }
    }
    
    /**
     * Converts buffer to the target encoding with fallback chain.
     * 
     * Fallback chain:
     * 1. Try targetEncoding (des_encoding from layout)
     * 2. If that fails, try UTF-8
     * 3. If that fails, use detected encoding
     * 
     * @param buffer Original byte buffer
     * @param targetEncoding Desired encoding (des_encoding from layout configuration)
     * @return String converted to the best available encoding
     */
    public String convertWithFallback(byte[] buffer, String targetEncoding) {
        if (buffer == null || buffer.length == 0) {
            return "";
        }
        
        // Detect the actual encoding of the buffer
        String detectedEncoding = detectEncoding(buffer);
        
        // Try target encoding first (des_encoding)
        if (targetEncoding != null && !targetEncoding.isEmpty()) {
            String result = tryConvert(buffer, detectedEncoding, targetEncoding);
            if (result != null) {
                logger.debug("Successfully converted from {} to {}", detectedEncoding, targetEncoding);
                return result;
            }
            logger.debug("Failed to convert to target encoding {}, trying UTF-8", targetEncoding);
        }
        
        // Fallback to UTF-8
        if (!StandardCharsets.UTF_8.name().equals(targetEncoding)) {
            String result = tryConvert(buffer, detectedEncoding, StandardCharsets.UTF_8.name());
            if (result != null) {
                logger.debug("Successfully converted from {} to UTF-8", detectedEncoding);
                return result;
            }
            logger.debug("Failed to convert to UTF-8, using detected encoding");
        }
        
        // Final fallback: use detected encoding
        String result = tryConvert(buffer, detectedEncoding, detectedEncoding);
        if (result != null) {
            logger.debug("Using detected encoding: {}", detectedEncoding);
            return result;
        }
        
        // Last resort: force UTF-8 with replacement
        logger.warn("All conversion attempts failed, forcing UTF-8 with replacement characters");
        return new String(buffer, StandardCharsets.UTF_8);
    }
    
    /**
     * Attempts to convert buffer from source encoding to target encoding.
     * 
     * @param buffer Byte buffer to convert
     * @param sourceEncoding Source encoding (detected)
     * @param targetEncoding Target encoding
     * @return Converted string, or null if conversion failed
     */
    private String tryConvert(byte[] buffer, String sourceEncoding, String targetEncoding) {
        try {
            // First decode from source encoding
            Charset sourceCharset = Charset.forName(sourceEncoding);
            CharsetDecoder decoder = sourceCharset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            
            CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(buffer));
            String decodedString = charBuffer.toString();
            
            // If source and target are the same, we're done
            if (sourceEncoding.equals(targetEncoding)) {
                return decodedString;
            }
            
            // Verify target encoding can represent the string
            Charset targetCharset = Charset.forName(targetEncoding);
            byte[] encoded = decodedString.getBytes(targetCharset);
            String reDecoded = new String(encoded, targetCharset);
            
            // Verify round-trip conversion preserves the string
            if (decodedString.equals(reDecoded)) {
                return decodedString;
            } else {
                logger.debug("Round-trip conversion failed for encoding {}", targetEncoding);
                return null;
            }
            
        } catch (Exception e) {
            logger.debug("Conversion failed from {} to {}: {}", 
                    sourceEncoding, targetEncoding, e.getMessage());
            return null;
        }
    }
}
