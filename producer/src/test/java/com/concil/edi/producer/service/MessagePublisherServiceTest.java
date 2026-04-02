package com.concil.edi.producer.service;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.repository.FileOriginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessagePublisherService.
 * Tests message publication, retry behavior, and recovery logic.
 */
@ExtendWith(MockitoExtension.class)
class MessagePublisherServiceTest {
    
    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @Mock
    private FileOriginRepository fileOriginRepository;
    
    private MessagePublisherService messagePublisherService;
    
    private static final String EXCHANGE_NAME = "edi.file.transfer.exchange";
    private static final String ROUTING_KEY = "edi.file.transfer";
    
    @BeforeEach
    void setUp() {
        messagePublisherService = new MessagePublisherService(rabbitTemplate, fileOriginRepository);
        ReflectionTestUtils.setField(messagePublisherService, "exchangeName", EXCHANGE_NAME);
        ReflectionTestUtils.setField(messagePublisherService, "routingKey", ROUTING_KEY);
    }
    
    /**
     * Test: publishFileTransferMessage should publish message with all fields
     */
    @Test
    void testPublishFileTransferMessage_PublishesWithAllFields() {
        // Arrange
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            100L,
            "test_file.csv",
            10L,
            20L,
            1024L
        );
        
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(FileTransferMessageDTO.class));
        
        // Act
        messagePublisherService.publishFileTransferMessage(message);
        
        // Assert
        ArgumentCaptor<FileTransferMessageDTO> captor = ArgumentCaptor.forClass(FileTransferMessageDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE_NAME), eq(ROUTING_KEY), captor.capture());
        
        FileTransferMessageDTO captured = captor.getValue();
        assertEquals(100L, captured.getIdtFileOrigin());
        assertEquals("test_file.csv", captured.getFilename());
        assertEquals(10L, captured.getIdtServerPathOrigin());
        assertEquals(20L, captured.getIdtServerPathDestination());
    }
    
    /**
     * Test: publishFileTransferMessage should throw AmqpException on failure
     */
    @Test
    void testPublishFileTransferMessage_ThrowsAmqpException() {
        // Arrange
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            100L,
            "test_file.csv",
            10L,
            20L,
            1024L
        );
        
        doThrow(new AmqpException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(FileTransferMessageDTO.class));
        
        // Act & Assert
        assertThrows(AmqpException.class, () -> {
            messagePublisherService.publishFileTransferMessage(message);
        });
    }
    
    /**
     * Test: recoverFromPublishFailure should update file_origin with ERRO status
     */
    @Test
    void testRecoverFromPublishFailure_UpdatesStatusToErro() {
        // Arrange
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            100L,
            "test_file.csv",
            10L,
            20L,
            1024L
        );
        
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(100L);
        fileOrigin.setDesFileName("test_file.csv");
        fileOrigin.setNumRetry(0);
        
        when(fileOriginRepository.findById(100L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        
        AmqpException exception = new AmqpException("Connection failed after retries");
        
        // Act
        messagePublisherService.recoverFromPublishFailure(exception, message);
        
        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());
        
        FileOrigin captured = captor.getValue();
        assertEquals(Status.ERRO, captured.getDesStatus());
        assertNotNull(captured.getDesMessageError());
        assertTrue(captured.getDesMessageError().contains("Failed to publish RabbitMQ message after 5 attempts"));
        assertEquals(1, captured.getNumRetry());
        assertNotNull(captured.getDatUpdate());
        assertEquals("PRODUCER", captured.getNamChangeAgent());
    }
    
    /**
     * Test: recoverFromPublishFailure should set num_retry=1 to allow scheduler retry
     */
    @Test
    void testRecoverFromPublishFailure_SetsNumRetryToOne() {
        // Arrange
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            100L,
            "test_file.csv",
            10L,
            20L,
            1024L
        );
        
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(100L);
        fileOrigin.setNumRetry(0);
        
        when(fileOriginRepository.findById(100L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        
        AmqpException exception = new AmqpException("Connection failed");
        
        // Act
        messagePublisherService.recoverFromPublishFailure(exception, message);
        
        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());
        
        FileOrigin captured = captor.getValue();
        assertEquals(1, captured.getNumRetry());
    }
    
    /**
     * Test: recoverFromPublishFailure should throw exception when file_origin not found
     */
    @Test
    void testRecoverFromPublishFailure_ThrowsExceptionWhenFileNotFound() {
        // Arrange
        FileTransferMessageDTO message = new FileTransferMessageDTO(
            999L,
            "test_file.csv",
            10L,
            20L,
            1024L
        );
        
        when(fileOriginRepository.findById(999L)).thenReturn(Optional.empty());
        
        AmqpException exception = new AmqpException("Connection failed");
        
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            messagePublisherService.recoverFromPublishFailure(exception, message);
        });
    }
}
