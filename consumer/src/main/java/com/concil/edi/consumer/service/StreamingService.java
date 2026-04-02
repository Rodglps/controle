package com.concil.edi.consumer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Service for streaming file transfers using buffered read/write operations.
 * 
 * Requirements: 13.5, 13.6
 */
@Service
@Slf4j
public class StreamingService {
    
    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    
    /**
     * Transfer file from source to destination using streaming.
     * Does not load entire file into memory.
     * 
     * @param source Source InputStream
     * @param destination Destination OutputStream
     * @throws IOException if transfer fails
     */
    public void transferFile(InputStream source, OutputStream destination) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesTransferred = 0;
        
        // try-with-resources ensures streams are closed even if exception occurs
        try (source; destination) {
            while ((bytesRead = source.read(buffer)) != -1) {
                destination.write(buffer, 0, bytesRead);
                totalBytesTransferred += bytesRead;
            }
            destination.flush();
            
            log.info("Successfully transferred {} bytes using streaming", totalBytesTransferred);
        } catch (IOException e) {
            log.error("Error during file streaming transfer after {} bytes", totalBytesTransferred, e);
            throw e;
        }
    }
}
