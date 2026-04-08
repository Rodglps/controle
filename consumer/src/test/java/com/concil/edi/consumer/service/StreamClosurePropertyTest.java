package com.concil.edi.consumer.service;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Property 42: Stream closure after transfer
 * 
 * Property: When transferFile is called,
 * THEN both InputStream and OutputStream must be closed after transfer completes,
 * AND streams must be closed even if an exception occurs during transfer
 * 
 * Validates: Requirements 13.6
 */
public class StreamClosurePropertyTest {
    
    private StreamingService streamingService;
    
    @BeforeTry
    void setup() {
        streamingService = new StreamingService();
    }
    
    @Property
    void streamsMustBeClosedAfterSuccessfulTransfer(
        @ForAll("fileContents") byte[] content
    ) throws IOException {
        // Arrange: Create trackable streams
        AtomicBoolean inputClosed = new AtomicBoolean(false);
        AtomicBoolean outputClosed = new AtomicBoolean(false);
        
        InputStream source = new ByteArrayInputStream(content) {
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
        
        // Act: Transfer file
        streamingService.transferFile(source, destination);
        
        // Assert: Both streams must be closed
        assert inputClosed.get() : "InputStream must be closed after successful transfer";
        assert outputClosed.get() : "OutputStream must be closed after successful transfer";
    }
    
    @Property
    void streamsMustBeClosedEvenWhenExceptionOccurs(
        @ForAll("fileContents") byte[] content
    ) {
        // Arrange: Create trackable streams with failing output
        AtomicBoolean inputClosed = new AtomicBoolean(false);
        AtomicBoolean outputClosed = new AtomicBoolean(false);
        
        InputStream source = new ByteArrayInputStream(content) {
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
        
        // Act: Transfer file (expect exception)
        try {
            streamingService.transferFile(source, destination);
        } catch (IOException e) {
            // Expected exception
        }
        
        // Assert: Both streams must be closed even after exception
        assert inputClosed.get() : "InputStream must be closed even when exception occurs";
        assert outputClosed.get() : "OutputStream must be closed even when exception occurs";
    }
    
    @Property
    void streamsMustBeClosedWhenReadFails(
        @ForAll("fileContents") byte[] content
    ) {
        // Arrange: Create trackable streams with failing input
        AtomicBoolean inputClosed = new AtomicBoolean(false);
        AtomicBoolean outputClosed = new AtomicBoolean(false);
        
        InputStream source = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Simulated read failure");
            }
            
            @Override
            public void close() throws IOException {
                inputClosed.set(true);
            }
        };
        
        OutputStream destination = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                outputClosed.set(true);
                super.close();
            }
        };
        
        // Act: Transfer file (expect exception)
        try {
            streamingService.transferFile(source, destination);
        } catch (IOException e) {
            // Expected exception
        }
        
        // Assert: Both streams must be closed even after read failure
        assert inputClosed.get() : "InputStream must be closed even when read fails";
        assert outputClosed.get() : "OutputStream must be closed even when read fails";
    }
    
    @Provide
    Arbitrary<byte[]> fileContents() {
        return Arbitraries.bytes()
            .array(byte[].class)
            .ofMinSize(0)
            .ofMaxSize(10000);
    }
}
