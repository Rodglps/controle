package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.TransactionType;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.commons.repository.FileOriginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileRegistrationService.
 * Tests file registration with correct initial values and duplicate detection.
 */
@ExtendWith(MockitoExtension.class)
class FileRegistrationServiceTest {
    
    @Mock
    private FileOriginRepository fileOriginRepository;
    
    private FileRegistrationService fileRegistrationService;
    
    @BeforeEach
    void setUp() {
        fileRegistrationService = new FileRegistrationService(fileOriginRepository);
    }
    
    /**
     * Test: registerFile should create record with correct initial values
     */
    @Test
    void testRegisterFile_CreatesRecordWithCorrectInitialValues() {
        // Arrange
        String filename = "test_file.csv";
        Long fileSize = 1024L;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        FileType fileType = FileType.CSV;
        Long serverPathInOutId = 10L;
        
        FileMetadataDTO metadata = new FileMetadataDTO(filename, fileSize, timestamp, fileType);
        
        FileOrigin savedFileOrigin = new FileOrigin();
        savedFileOrigin.setIdtFileOrigin(100L);
        savedFileOrigin.setDesFileName(filename);
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(savedFileOrigin);
        
        // Act
        FileOrigin result = fileRegistrationService.registerFile(metadata, serverPathInOutId);
        
        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getIdtFileOrigin());
        
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());
        
        FileOrigin captured = captor.getValue();
        
        // Verify MVP values
        assertEquals(1L, captured.getIdtAcquirer());
        assertEquals(1L, captured.getIdtLayout());
        
        // Verify file metadata
        assertEquals(filename, captured.getDesFileName());
        assertEquals(fileSize, captured.getNumFileSize());
        assertEquals(fileType, captured.getDesFileType());
        assertEquals(timestamp, captured.getDatTimestampFile());
        
        // Verify initial state
        assertEquals(Step.COLETA, captured.getDesStep());
        assertEquals(Status.EM_ESPERA, captured.getDesStatus());
        
        // Verify retry configuration
        assertEquals(0, captured.getNumRetry());
        assertEquals(5, captured.getMaxRetry());
        
        // Verify server path mapping
        assertEquals(serverPathInOutId, captured.getIdtSeverPathsInOut());
        
        // Verify transaction type
        assertEquals(TransactionType.COMPLETO, captured.getDesTransactionType());
        
        // Verify active flag
        assertEquals(1, captured.getFlgActive());
        
        // Verify metadata
        assertNotNull(captured.getDatCreation());
        assertEquals("PRODUCER", captured.getNamChangeAgent());
    }
    
    /**
     * Test: fileExists should detect duplicate (same filename, acquirer, timestamp)
     */
    @Test
    void testFileExists_DetectsDuplicate() {
        // Arrange
        String filename = "duplicate_file.csv";
        Long acquirerId = 1L;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        FileOrigin existingFile = new FileOrigin();
        existingFile.setIdtFileOrigin(50L);
        existingFile.setDesFileName(filename);
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            filename, acquirerId, timestamp, 1
        )).thenReturn(Optional.of(existingFile));
        
        // Act
        boolean exists = fileRegistrationService.fileExists(filename, acquirerId, timestamp);
        
        // Assert
        assertTrue(exists);
        verify(fileOriginRepository).findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            filename, acquirerId, timestamp, 1
        );
    }
    
    /**
     * Test: fileExists should return false when file doesn't exist
     */
    @Test
    void testFileExists_ReturnsFalseWhenNotFound() {
        // Arrange
        String filename = "new_file.csv";
        Long acquirerId = 1L;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            filename, acquirerId, timestamp, 1
        )).thenReturn(Optional.empty());
        
        // Act
        boolean exists = fileRegistrationService.fileExists(filename, acquirerId, timestamp);
        
        // Assert
        assertFalse(exists);
    }
    
    /**
     * Test: fileExists should allow same filename with different timestamp
     */
    @Test
    void testFileExists_AllowsSameFilenameWithDifferentTimestamp() {
        // Arrange
        String filename = "same_name.csv";
        Long acquirerId = 1L;
        Timestamp timestamp1 = new Timestamp(System.currentTimeMillis());
        Timestamp timestamp2 = new Timestamp(System.currentTimeMillis() + 10000);
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            filename, acquirerId, timestamp2, 1
        )).thenReturn(Optional.empty());
        
        // Act
        boolean exists = fileRegistrationService.fileExists(filename, acquirerId, timestamp2);
        
        // Assert
        assertFalse(exists);
    }
    
    /**
     * Test: fileExists should allow same filename with different acquirer
     */
    @Test
    void testFileExists_AllowsSameFilenameWithDifferentAcquirer() {
        // Arrange
        String filename = "same_name.csv";
        Long acquirerId1 = 1L;
        Long acquirerId2 = 2L;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            filename, acquirerId2, timestamp, 1
        )).thenReturn(Optional.empty());
        
        // Act
        boolean exists = fileRegistrationService.fileExists(filename, acquirerId2, timestamp);
        
        // Assert
        assertFalse(exists);
    }
}
