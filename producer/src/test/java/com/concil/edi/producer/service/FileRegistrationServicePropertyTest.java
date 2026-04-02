package com.concil.edi.producer.service;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.producer.dto.FileMetadataDTO;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Assertions;

import java.sql.Timestamp;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for FileRegistrationService.
 * Validates duplicate file detection logic and initial registration state using jqwik.
 * 
 * Property 24: Duplicate file detection
 * Property 25: Initial file registration state
 * Validates Requirements 7.6, 7.7, 8.2, 8.3
 */
class FileRegistrationServicePropertyTest {
    
    /**
     * Property 24: Duplicate file detection
     * 
     * GIVEN a file with (filename, acquirerId, timestamp)
     * WHEN checking if the file exists with the same (filename, acquirerId, timestamp)
     * THEN the system MUST detect it as a duplicate
     * 
     * This validates the unique constraint file_origin_idx_01:
     * (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)
     * 
     * Requirements 7.6, 7.7:
     * - 7.6: Producer SHALL verify if file already exists using file_origin_idx_01
     * - 7.7: IF file already exists, THEN Producer SHALL ignore the file
     */
    @Property
    @Label("Property 24: Duplicate detection - same filename, acquirer, and timestamp")
    void duplicateDetection_SameFilenameAcquirerTimestamp_DetectsAsDuplicate(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + ".csv";
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileOrigin existingFile = new FileOrigin();
        existingFile.setIdtFileOrigin(1L);
        existingFile.setDesFileName(filename);
        existingFile.setIdtAcquirer(acquirerId);
        existingFile.setDatTimestampFile(timestamp);
        existingFile.setFlgActive(1);
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(filename), eq(acquirerId), eq(timestamp), eq(1)
        )).thenReturn(Optional.of(existingFile));
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        boolean exists = service.fileExists(filename, acquirerId, timestamp);
        
        // Assert
        Assertions.assertTrue(exists, 
            "File with same filename, acquirer, and timestamp should be detected as duplicate");
    }
    
    /**
     * Property 24.1: Non-duplicate detection - different filename
     * 
     * GIVEN a file with (filename1, acquirerId, timestamp)
     * WHEN checking if a file exists with (filename2, acquirerId, timestamp) where filename1 != filename2
     * THEN the system MUST NOT detect it as a duplicate
     */
    @Property
    @Label("Property 24.1: Non-duplicate - different filename")
    void duplicateDetection_DifferentFilename_NotDetectedAsDuplicate(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String filename1,
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String filename2,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis
    ) {
        Assume.that(!filename1.equals(filename2));
        
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String file1 = filename1 + ".csv";
        String file2 = filename2 + ".csv";
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(file2), eq(acquirerId), eq(timestamp), eq(1)
        )).thenReturn(Optional.empty());
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        boolean exists = service.fileExists(file2, acquirerId, timestamp);
        
        // Assert
        Assertions.assertFalse(exists, 
            "File with different filename should NOT be detected as duplicate");
    }
    
    /**
     * Property 24.2: Non-duplicate detection - different acquirer
     * 
     * GIVEN a file with (filename, acquirerId1, timestamp)
     * WHEN checking if a file exists with (filename, acquirerId2, timestamp) where acquirerId1 != acquirerId2
     * THEN the system MUST NOT detect it as a duplicate
     */
    @Property
    @Label("Property 24.2: Non-duplicate - different acquirer")
    void duplicateDetection_DifferentAcquirer_NotDetectedAsDuplicate(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId1,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId2,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis
    ) {
        Assume.that(!acquirerId1.equals(acquirerId2));
        
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + ".csv";
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(filename), eq(acquirerId2), eq(timestamp), eq(1)
        )).thenReturn(Optional.empty());
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        boolean exists = service.fileExists(filename, acquirerId2, timestamp);
        
        // Assert
        Assertions.assertFalse(exists, 
            "File with different acquirer should NOT be detected as duplicate");
    }
    
    /**
     * Property 24.3: Non-duplicate detection - different timestamp
     * 
     * GIVEN a file with (filename, acquirerId, timestamp1)
     * WHEN checking if a file exists with (filename, acquirerId, timestamp2) where timestamp1 != timestamp2
     * THEN the system MUST NOT detect it as a duplicate
     */
    @Property
    @Label("Property 24.3: Non-duplicate - different timestamp")
    void duplicateDetection_DifferentTimestamp_NotDetectedAsDuplicate(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis1,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis2
    ) {
        Assume.that(!timestampMillis1.equals(timestampMillis2));
        
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + ".csv";
        Timestamp timestamp1 = new Timestamp(timestampMillis1);
        Timestamp timestamp2 = new Timestamp(timestampMillis2);
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(filename), eq(acquirerId), eq(timestamp2), eq(1)
        )).thenReturn(Optional.empty());
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        boolean exists = service.fileExists(filename, acquirerId, timestamp2);
        
        // Assert
        Assertions.assertFalse(exists, 
            "File with different timestamp should NOT be detected as duplicate");
    }
    
    /**
     * Property 24.4: Duplicate detection consistency
     * 
     * GIVEN any file with (filename, acquirerId, timestamp)
     * WHEN checking if the file exists multiple times with the same parameters
     * THEN the result MUST be consistent (always true or always false)
     * 
     * This validates that the duplicate detection is deterministic and idempotent.
     */
    @Property
    @Label("Property 24.4: Duplicate detection is consistent and idempotent")
    void duplicateDetection_IsConsistentAndIdempotent(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll boolean fileExists
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + ".csv";
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        Optional<FileOrigin> result = fileExists ? 
            Optional.of(createMockFileOrigin(filename, acquirerId, timestamp)) : 
            Optional.empty();
        
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(filename), eq(acquirerId), eq(timestamp), eq(1)
        )).thenReturn(result);
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act - check multiple times
        boolean check1 = service.fileExists(filename, acquirerId, timestamp);
        boolean check2 = service.fileExists(filename, acquirerId, timestamp);
        boolean check3 = service.fileExists(filename, acquirerId, timestamp);
        
        // Assert
        Assertions.assertEquals(check1, check2, 
            "Duplicate detection should return consistent results");
        Assertions.assertEquals(check2, check3, 
            "Duplicate detection should return consistent results");
        Assertions.assertEquals(fileExists, check1, 
            "Duplicate detection should match expected existence");
    }
    
    /**
     * Property 24.5: Active flag enforcement
     * 
     * GIVEN a file with (filename, acquirerId, timestamp, flg_active=1)
     * WHEN checking if the file exists
     * THEN the system MUST only check for active files (flg_active=1)
     * 
     * This validates that the unique constraint considers the active flag.
     */
    @Property
    @Label("Property 24.5: Duplicate detection only considers active files")
    void duplicateDetection_OnlyConsidersActiveFiles(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 10) Long acquirerId,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + ".csv";
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        // Repository should only be queried with flg_active=1
        when(fileOriginRepository.findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(filename), eq(acquirerId), eq(timestamp), eq(1)
        )).thenReturn(Optional.empty());
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        service.fileExists(filename, acquirerId, timestamp);
        
        // Assert - verify that the repository was called with flg_active=1
        verify(fileOriginRepository).findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
            eq(filename), eq(acquirerId), eq(timestamp), eq(1)
        );
    }
    
    // Helper method to create mock FileOrigin
    private FileOrigin createMockFileOrigin(String filename, Long acquirerId, Timestamp timestamp) {
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(1L);
        fileOrigin.setDesFileName(filename);
        fileOrigin.setIdtAcquirer(acquirerId);
        fileOrigin.setDatTimestampFile(timestamp);
        fileOrigin.setFlgActive(1);
        return fileOrigin;
    }
    
    /**
     * Property 25: Initial file registration state
     * 
     * GIVEN any valid file metadata
     * WHEN registering a new file
     * THEN the file MUST be registered with des_step=COLETA and des_status=EM_ESPERA
     * 
     * This validates Requirements 8.2 and 8.3:
     * - 8.2: WHEN Producer inserts registro, THE Producer SHALL definir des_step como COLETA
     * - 8.3: WHEN Producer inserts registro, THE Producer SHALL definir des_status como EM_ESPERA
     */
    @Property
    @Label("Property 25: Initial file registration state - COLETA/EM_ESPERA")
    void initialFileRegistration_AlwaysHasColetaStepAndEmEsperaStatus(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        // Capture the FileOrigin that will be saved
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L); // Simulate database ID generation
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - Requirement 8.2: des_step MUST be COLETA
        Assertions.assertEquals(Step.COLETA, registered.getDesStep(),
            "Initial file registration MUST have des_step=COLETA");
        
        // Assert - Requirement 8.3: des_status MUST be EM_ESPERA
        Assertions.assertEquals(Status.EM_ESPERA, registered.getDesStatus(),
            "Initial file registration MUST have des_status=EM_ESPERA");
    }
    
    /**
     * Property 25.1: Initial registration state is independent of file metadata
     * 
     * GIVEN any two different file metadata inputs
     * WHEN registering both files
     * THEN both MUST have the same initial state (COLETA/EM_ESPERA)
     * 
     * This validates that the initial state is constant regardless of input variations.
     */
    @Property
    @Label("Property 25.1: Initial state is constant across different file metadata")
    void initialFileRegistration_StateIsConstantAcrossMetadata(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String filename1,
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String filename2,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize1,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize2,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestamp1,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestamp2,
        @ForAll FileType fileType1,
        @ForAll FileType fileType2,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        
        FileMetadataDTO metadata1 = new FileMetadataDTO(
            filename1 + "." + fileType1.name(),
            fileSize1,
            new Timestamp(timestamp1),
            fileType1
        );
        
        FileMetadataDTO metadata2 = new FileMetadataDTO(
            filename2 + "." + fileType2.name(),
            fileSize2,
            new Timestamp(timestamp2),
            fileType2
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered1 = service.registerFile(metadata1, serverPathInOutId);
        FileOrigin registered2 = service.registerFile(metadata2, serverPathInOutId);
        
        // Assert - Both files MUST have the same initial state
        Assertions.assertEquals(registered1.getDesStep(), registered2.getDesStep(),
            "All files MUST have the same initial des_step");
        Assertions.assertEquals(registered1.getDesStatus(), registered2.getDesStatus(),
            "All files MUST have the same initial des_status");
        Assertions.assertEquals(Step.COLETA, registered1.getDesStep(),
            "Initial des_step MUST be COLETA");
        Assertions.assertEquals(Status.EM_ESPERA, registered1.getDesStatus(),
            "Initial des_status MUST be EM_ESPERA");
    }
    
    /**
     * Property 25.2: Initial state never changes during registration
     * 
     * GIVEN any file metadata
     * WHEN registering the file multiple times (simulating retries)
     * THEN each registration MUST have the same initial state (COLETA/EM_ESPERA)
     * 
     * This validates that the initial state logic is deterministic and idempotent.
     */
    @Property
    @Label("Property 25.2: Initial state is deterministic across multiple registrations")
    void initialFileRegistration_StateIsDeterministic(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act - Register the same file metadata multiple times
        FileOrigin registered1 = service.registerFile(metadata, serverPathInOutId);
        FileOrigin registered2 = service.registerFile(metadata, serverPathInOutId);
        FileOrigin registered3 = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - All registrations MUST have the same initial state
        Assertions.assertEquals(Step.COLETA, registered1.getDesStep());
        Assertions.assertEquals(Step.COLETA, registered2.getDesStep());
        Assertions.assertEquals(Step.COLETA, registered3.getDesStep());
        
        Assertions.assertEquals(Status.EM_ESPERA, registered1.getDesStatus());
        Assertions.assertEquals(Status.EM_ESPERA, registered2.getDesStatus());
        Assertions.assertEquals(Status.EM_ESPERA, registered3.getDesStatus());
    }
    
    /**
     * Property 25.3: Initial state validation - no other Step values
     * 
     * GIVEN any file metadata
     * WHEN registering a new file
     * THEN des_step MUST NOT be any value other than COLETA
     * 
     * This validates that the system never initializes files with incorrect step values.
     */
    @Property
    @Label("Property 25.3: Initial des_step is never anything other than COLETA")
    void initialFileRegistration_StepIsNeverOtherThanColeta(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - des_step MUST NOT be any of these values
        Assertions.assertNotEquals(Step.DELETE, registered.getDesStep(),
            "Initial des_step MUST NOT be DELETE");
        Assertions.assertNotEquals(Step.RAW, registered.getDesStep(),
            "Initial des_step MUST NOT be RAW");
        Assertions.assertNotEquals(Step.STAGING, registered.getDesStep(),
            "Initial des_step MUST NOT be STAGING");
        Assertions.assertNotEquals(Step.ORDINATION, registered.getDesStep(),
            "Initial des_step MUST NOT be ORDINATION");
        Assertions.assertNotEquals(Step.PROCESSING, registered.getDesStep(),
            "Initial des_step MUST NOT be PROCESSING");
        Assertions.assertNotEquals(Step.PROCESSED, registered.getDesStep(),
            "Initial des_step MUST NOT be PROCESSED");
    }
    
    /**
     * Property 25.4: Initial state validation - no other Status values
     * 
     * GIVEN any file metadata
     * WHEN registering a new file
     * THEN des_status MUST NOT be any value other than EM_ESPERA
     * 
     * This validates that the system never initializes files with incorrect status values.
     */
    @Property
    @Label("Property 25.4: Initial des_status is never anything other than EM_ESPERA")
    void initialFileRegistration_StatusIsNeverOtherThanEmEspera(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - des_status MUST NOT be any of these values
        Assertions.assertNotEquals(Status.PROCESSAMENTO, registered.getDesStatus(),
            "Initial des_status MUST NOT be PROCESSAMENTO");
        Assertions.assertNotEquals(Status.CONCLUIDO, registered.getDesStatus(),
            "Initial des_status MUST NOT be CONCLUIDO");
        Assertions.assertNotEquals(Status.ERRO, registered.getDesStatus(),
            "Initial des_status MUST NOT be ERRO");
    }
    
    /**
     * Property 26: Initial retry configuration
     * 
     * GIVEN any valid file metadata
     * WHEN registering a new file
     * THEN the file MUST be registered with num_retry=0 and max_retry=5
     * 
     * This validates Requirements 8.6 and 8.7:
     * - 8.6: WHEN Producer inserts registro, THE Producer SHALL definir num_retry como 0
     * - 8.7: WHEN Producer inserts registro, THE Producer SHALL definir max_retry como 5
     */
    @Property
    @Label("Property 26: Initial retry configuration - num_retry=0, max_retry=5")
    void initialFileRegistration_AlwaysHasCorrectRetryConfiguration(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        // Capture the FileOrigin that will be saved
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L); // Simulate database ID generation
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - Requirement 8.6: num_retry MUST be 0
        Assertions.assertEquals(0, registered.getNumRetry(),
            "Initial file registration MUST have num_retry=0");
        
        // Assert - Requirement 8.7: max_retry MUST be 5
        Assertions.assertEquals(5, registered.getMaxRetry(),
            "Initial file registration MUST have max_retry=5");
    }
    
    /**
     * Property 26.1: Retry configuration is independent of file metadata
     * 
     * GIVEN any two different file metadata inputs
     * WHEN registering both files
     * THEN both MUST have the same retry configuration (num_retry=0, max_retry=5)
     * 
     * This validates that the retry configuration is constant regardless of input variations.
     */
    @Property
    @Label("Property 26.1: Retry configuration is constant across different file metadata")
    void initialFileRegistration_RetryConfigIsConstantAcrossMetadata(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String filename1,
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String filename2,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize1,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize2,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestamp1,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestamp2,
        @ForAll FileType fileType1,
        @ForAll FileType fileType2,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        
        FileMetadataDTO metadata1 = new FileMetadataDTO(
            filename1 + "." + fileType1.name(),
            fileSize1,
            new Timestamp(timestamp1),
            fileType1
        );
        
        FileMetadataDTO metadata2 = new FileMetadataDTO(
            filename2 + "." + fileType2.name(),
            fileSize2,
            new Timestamp(timestamp2),
            fileType2
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered1 = service.registerFile(metadata1, serverPathInOutId);
        FileOrigin registered2 = service.registerFile(metadata2, serverPathInOutId);
        
        // Assert - Both files MUST have the same retry configuration
        Assertions.assertEquals(registered1.getNumRetry(), registered2.getNumRetry(),
            "All files MUST have the same initial num_retry");
        Assertions.assertEquals(registered1.getMaxRetry(), registered2.getMaxRetry(),
            "All files MUST have the same initial max_retry");
        Assertions.assertEquals(0, registered1.getNumRetry(),
            "Initial num_retry MUST be 0");
        Assertions.assertEquals(5, registered1.getMaxRetry(),
            "Initial max_retry MUST be 5");
    }
    
    /**
     * Property 26.2: Retry configuration is deterministic
     * 
     * GIVEN any file metadata
     * WHEN registering the file multiple times (simulating retries)
     * THEN each registration MUST have the same retry configuration (num_retry=0, max_retry=5)
     * 
     * This validates that the retry configuration logic is deterministic and idempotent.
     */
    @Property
    @Label("Property 26.2: Retry configuration is deterministic across multiple registrations")
    void initialFileRegistration_RetryConfigIsDeterministic(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act - Register the same file metadata multiple times
        FileOrigin registered1 = service.registerFile(metadata, serverPathInOutId);
        FileOrigin registered2 = service.registerFile(metadata, serverPathInOutId);
        FileOrigin registered3 = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - All registrations MUST have the same retry configuration
        Assertions.assertEquals(0, registered1.getNumRetry());
        Assertions.assertEquals(0, registered2.getNumRetry());
        Assertions.assertEquals(0, registered3.getNumRetry());
        
        Assertions.assertEquals(5, registered1.getMaxRetry());
        Assertions.assertEquals(5, registered2.getMaxRetry());
        Assertions.assertEquals(5, registered3.getMaxRetry());
    }
    
    /**
     * Property 26.3: Retry configuration validation - num_retry is never negative
     * 
     * GIVEN any file metadata
     * WHEN registering a new file
     * THEN num_retry MUST NOT be negative
     * 
     * This validates that the system never initializes files with invalid retry counts.
     */
    @Property
    @Label("Property 26.3: Initial num_retry is never negative")
    void initialFileRegistration_NumRetryIsNeverNegative(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - num_retry MUST be >= 0
        Assertions.assertTrue(registered.getNumRetry() >= 0,
            "Initial num_retry MUST NOT be negative");
    }
    
    /**
     * Property 26.4: Retry configuration validation - max_retry is positive
     * 
     * GIVEN any file metadata
     * WHEN registering a new file
     * THEN max_retry MUST be positive (> 0)
     * 
     * This validates that the system always allows at least one retry attempt.
     */
    @Property
    @Label("Property 26.4: Initial max_retry is always positive")
    void initialFileRegistration_MaxRetryIsAlwaysPositive(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - max_retry MUST be > 0
        Assertions.assertTrue(registered.getMaxRetry() > 0,
            "Initial max_retry MUST be positive (> 0)");
    }
    
    /**
     * Property 26.5: Retry configuration invariant - num_retry <= max_retry
     * 
     * GIVEN any file metadata
     * WHEN registering a new file
     * THEN num_retry MUST be less than or equal to max_retry
     * 
     * This validates the fundamental retry invariant at initialization.
     */
    @Property
    @Label("Property 26.5: Initial state maintains invariant num_retry <= max_retry")
    void initialFileRegistration_MaintainsRetryInvariant(
        @ForAll @StringLength(min = 5, max = 100) @AlphaChars String baseFilename,
        @ForAll @LongRange(min = 1, max = 1000000) Long fileSize,
        @ForAll @LongRange(min = 1000000000000L, max = 9999999999999L) Long timestampMillis,
        @ForAll FileType fileType,
        @ForAll @LongRange(min = 1, max = 100) Long serverPathInOutId
    ) {
        // Arrange
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        String filename = baseFilename + "." + fileType.name();
        Timestamp timestamp = new Timestamp(timestampMillis);
        
        FileMetadataDTO metadata = new FileMetadataDTO(
            filename,
            fileSize,
            timestamp,
            fileType
        );
        
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin saved = invocation.getArgument(0);
            saved.setIdtFileOrigin(1L);
            return saved;
        });
        
        FileRegistrationService service = new FileRegistrationService(fileOriginRepository);
        
        // Act
        FileOrigin registered = service.registerFile(metadata, serverPathInOutId);
        
        // Assert - Fundamental retry invariant
        Assertions.assertTrue(registered.getNumRetry() <= registered.getMaxRetry(),
            "Initial state MUST maintain invariant: num_retry <= max_retry");
    }
}
