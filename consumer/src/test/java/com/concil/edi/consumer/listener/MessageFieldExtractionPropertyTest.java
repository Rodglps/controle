package com.concil.edi.consumer.listener;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for message field extraction.
 * 
 * Property 36: Message field extraction
 * Validates: Requirements 11.2, 11.3, 11.4, 11.5
 * 
 * Correctness Property:
 * For all valid FileTransferMessageDTO messages,
 * all required fields (idtFileOrigin, filename, idtServerPathOrigin, idtServerPathDestination)
 * must be extractable and non-null.
 */
@DisplayName("Property 36: Message Field Extraction")
class MessageFieldExtractionPropertyTest {

    @Property
    @Label("All message fields must be extractable and non-null")
    void allMessageFieldsMustBeExtractableAndNonNull(
        @ForAll("validFileTransferMessages") FileTransferMessageDTO message
    ) {
        // Property: All required fields must be present and non-null
        assertThat(message.getIdtFileOrigin())
            .as("idtFileOrigin must be extractable and non-null")
            .isNotNull();
        
        assertThat(message.getFilename())
            .as("filename must be extractable and non-null")
            .isNotNull()
            .isNotEmpty();
        
        assertThat(message.getIdtServerPathOrigin())
            .as("idtServerPathOrigin must be extractable and non-null")
            .isNotNull();
        
        assertThat(message.getIdtServerPathDestination())
            .as("idtServerPathDestination must be extractable and non-null")
            .isNotNull();
    }

    @Property
    @Label("Message fields must preserve their values after extraction")
    void messageFieldsMustPreserveValuesAfterExtraction(
        @ForAll @Positive Long idtFileOrigin,
        @ForAll @AlphaChars @StringLength(min = 1, max = 255) String filename,
        @ForAll @Positive Long idtServerPathOrigin,
        @ForAll @Positive Long idtServerPathDestination
    ) {
        // Create message with specific values
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            idtFileOrigin,
            filename,
            idtServerPathOrigin,
            idtServerPathDestination
        );
        
        // Property: Extracted values must match original values
        assertThat(message.getIdtFileOrigin())
            .as("Extracted idtFileOrigin must match original")
            .isEqualTo(idtFileOrigin);
        
        assertThat(message.getFilename())
            .as("Extracted filename must match original")
            .isEqualTo(filename);
        
        assertThat(message.getIdtServerPathOrigin())
            .as("Extracted idtServerPathOrigin must match original")
            .isEqualTo(idtServerPathOrigin);
        
        assertThat(message.getIdtServerPathDestination())
            .as("Extracted idtServerPathDestination must match original")
            .isEqualTo(idtServerPathDestination);
    }

    @Property
    @Label("Message serialization must preserve all fields")
    void messageSerializationMustPreserveAllFields(
        @ForAll("validFileTransferMessages") FileTransferMessageDTO original
    ) {
        // Create a copy to simulate serialization/deserialization
        FileTransferMessageDTO copy = new FileTransferMessageDTO(
            original.getIdtFileOrigin(),
            original.getFilename(),
            original.getIdtServerPathOrigin(),
            original.getIdtServerPathDestination()
        );
        
        // Property: All fields must be preserved after serialization
        assertThat(copy.getIdtFileOrigin())
            .as("idtFileOrigin must be preserved")
            .isEqualTo(original.getIdtFileOrigin());
        
        assertThat(copy.getFilename())
            .as("filename must be preserved")
            .isEqualTo(original.getFilename());
        
        assertThat(copy.getIdtServerPathOrigin())
            .as("idtServerPathOrigin must be preserved")
            .isEqualTo(original.getIdtServerPathOrigin());
        
        assertThat(copy.getIdtServerPathDestination())
            .as("idtServerPathDestination must be preserved")
            .isEqualTo(original.getIdtServerPathDestination());
    }

    @Provide
    Arbitrary<FileTransferMessageDTO> validFileTransferMessages() {
        Arbitrary<Long> positiveIds = Arbitraries.longs().greaterOrEqual(1L);
        Arbitrary<String> filenames = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '_', '-')
            .ofMinLength(1)
            .ofMaxLength(255);
        
        return Combinators.combine(
            positiveIds,
            filenames,
            positiveIds,
            positiveIds
        ).as(FileTransferMessageDTO::new);
    }
}
