package com.concil.edi.commons.dto;

import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileTransferMessageDTO DTO.
 */
class FileTransferMessageTest {

    @Test
    void testFileTransferMessageCreation() {
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            123L,
            "test-file.csv",
            1L,
            2L,
            1024L
        );

        assertNotNull(message);
        assertEquals(123L, message.getIdtFileOrigin());
        assertEquals("test-file.csv", message.getFilename());
        assertEquals(1L, message.getIdtServerPathOrigin());
        assertEquals(2L, message.getIdtServerPathDestination());
        assertEquals(1024L, message.getFileSize());
    }

    @Test
    void testFileTransferMessageSerialization() throws IOException, ClassNotFoundException {
        FileTransferMessageDTO original = new FileTransferMessageDTO(
            456L,
            "cielo-captura-20250115.txt",
            10L,
            20L,
            2048L
        );

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        FileTransferMessageDTO deserialized = (FileTransferMessageDTO) ois.readObject();
        ois.close();

        // Verify
        assertNotNull(deserialized);
        assertEquals(original.getIdtFileOrigin(), deserialized.getIdtFileOrigin());
        assertEquals(original.getFilename(), deserialized.getFilename());
        assertEquals(original.getIdtServerPathOrigin(), deserialized.getIdtServerPathOrigin());
        assertEquals(original.getIdtServerPathDestination(), deserialized.getIdtServerPathDestination());
        assertEquals(original.getFileSize(), deserialized.getFileSize());
    }

    @Test
    void testFileTransferMessageSetters() {
        FileTransferMessageDTO message = new FileTransferMessageDTO();
        message.setIdtFileOrigin(789L);
        message.setFilename("rede-financeiro-20250115.json");
        message.setIdtServerPathOrigin(5L);
        message.setIdtServerPathDestination(6L);
        message.setFileSize(4096L);

        assertEquals(789L, message.getIdtFileOrigin());
        assertEquals("rede-financeiro-20250115.json", message.getFilename());
        assertEquals(5L, message.getIdtServerPathOrigin());
        assertEquals(6L, message.getIdtServerPathDestination());
        assertEquals(4096L, message.getFileSize());
    }

    @Test
    void testFileTransferMessageAllFieldsRequired() {
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            100L,
            "test.txt",
            1L,
            2L,
            512L
        );

        // All fields should be non-null for a valid message
        assertNotNull(message.getIdtFileOrigin());
        assertNotNull(message.getFilename());
        assertNotNull(message.getIdtServerPathOrigin());
        assertNotNull(message.getIdtServerPathDestination());
        assertNotNull(message.getFileSize());
    }
}
