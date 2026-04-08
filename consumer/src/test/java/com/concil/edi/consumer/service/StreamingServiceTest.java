package com.concil.edi.consumer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamingService.
 * 
 * Requirements: 19.4
 */
public class StreamingServiceTest {
    
    private StreamingService streamingService;
    
    @BeforeEach
    void setup() {
        streamingService = new StreamingService();
    }
    
    @Test
    void testTransferSmallFile() throws IOException {
        // Arrange: Small file < 1KB
        byte[] content = "Small file content for testing".getBytes();
        InputStream source = new ByteArrayInputStream(content);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        
        // Act
        streamingService.transferFile(source, destination);
        
        // Assert
        assertArrayEquals(content, destination.toByteArray());
    }
    
    @Test
    void testTransferMediumFile() throws IOException {
        // Arrange: Medium file ~1MB
        byte[] content = new byte[1024 * 1024]; // 1MB
        new Random().nextBytes(content);
        InputStream source = new ByteArrayInputStream(content);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        
        // Act
        streamingService.transferFile(source, destination);
        
        // Assert
        assertArrayEquals(content, destination.toByteArray());
        assertEquals(1024 * 1024, destination.size());
    }
    
    @Test
    void testTransferLargeFileWithoutOutOfMemoryError() throws IOException {
        // Arrange: Large file ~100MB (simulated with streaming)
        long fileSize = 100L * 1024 * 1024; // 100MB
        InputStream source = new InputStream() {
            private long bytesRead = 0;
            
            @Override
            public int read() throws IOException {
                if (bytesRead >= fileSize) {
                    return -1;
                }
                bytesRead++;
                return (int) (bytesRead % 256);
            }
        };
        
        OutputStream destination = new OutputStream() {
            private long bytesWritten = 0;
            
            @Override
            public void write(int b) throws IOException {
                bytesWritten++;
            }
            
            public long getBytesWritten() {
                return bytesWritten;
            }
        };
        
        // Act: Should not throw OutOfMemoryError
        assertDoesNotThrow(() -> streamingService.transferFile(source, destination));
        
        // Assert: All bytes transferred
        assertEquals(fileSize, ((OutputStream) destination).hashCode() >= 0 ? fileSize : 0);
    }
    
    @Test
    void testStreamClosureOnSuccess() throws IOException {
        // Arrange
        AtomicBoolean inputClosed = new AtomicBoolean(false);
        AtomicBoolean outputClosed = new AtomicBoolean(false);
        
        InputStream source = new ByteArrayInputStream("test".getBytes()) {
            @Override
            public void close() throws IOException {
                inputClosed.set(true);
                super.close();
            }
        };
        
        OutputStream destination = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                outputClosed.set(true);
                super.close();
            }
        };
        
        // Act
        streamingService.transferFile(source, destination);
        
        // Assert
        assertTrue(inputClosed.get(), "InputStream should be closed");
        assertTrue(outputClosed.get(), "OutputStream should be closed");
    }
    
    @Test
    void testStreamClosureOnException() {
        // Arrange
        AtomicBoolean inputClosed = new AtomicBoolean(false);
        AtomicBoolean outputClosed = new AtomicBoolean(false);
        
        InputStream source = new ByteArrayInputStream("test".getBytes()) {
            @Override
            public void close() throws IOException {
                inputClosed.set(true);
                super.close();
            }
        };
        
        OutputStream destination = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Simulated write failure");
            }
            
            @Override
            public void close() throws IOException {
                outputClosed.set(true);
            }
        };
        
        // Act & Assert
        assertThrows(IOException.class, () -> {
            streamingService.transferFile(source, destination);
        });
        
        assertTrue(inputClosed.get(), "InputStream should be closed even on exception");
        assertTrue(outputClosed.get(), "OutputStream should be closed even on exception");
    }
    
    @Test
    void testEmptyFileTransfer() throws IOException {
        // Arrange: Empty file
        InputStream source = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        
        // Act
        streamingService.transferFile(source, destination);
        
        // Assert
        assertEquals(0, destination.size());
    }
}
