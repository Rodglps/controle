package com.concil.edi.producer.service;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.repository.FileOriginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Service for publishing file transfer messages to RabbitMQ.
 * Publishes file transfer messages to RabbitMQ with retry and recovery logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePublisherService {
    
    private final RabbitTemplate rabbitTemplate;
    private final FileOriginRepository fileOriginRepository;
    
    @Value("${rabbitmq.exchange.file-transfer:edi.file.transfer.exchange}")
    private String exchangeName;
    
    @Value("${rabbitmq.routing-key.file-transfer:edi.file.transfer}")
    private String routingKey;
    
    @Retryable(
        value = {AmqpException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void publishFileTransferMessage(FileTransferMessageDTO message) {
        log.debug("Publishing message for file_origin: {}", message.getIdtFileOrigin());
        
        rabbitTemplate.convertAndSend(exchangeName, routingKey, message);
        
        log.info("Message published successfully for file_origin: {}", message.getIdtFileOrigin());
    }
    
    @Recover
    @Transactional
    public void recoverFromPublishFailure(AmqpException e, FileTransferMessageDTO message) {
        log.error("RabbitMQ publish failed after 5 attempts for file_origin: {}, marking as ERRO", 
            message.getIdtFileOrigin(), e);
        
        FileOrigin fileOrigin = fileOriginRepository.findById(message.getIdtFileOrigin())
            .orElseThrow(() -> new IllegalStateException(
                "FileOrigin not found: " + message.getIdtFileOrigin()));
        
        // Update status to ERRO
        fileOrigin.setDesStatus(Status.ERRO);
        
        // Set error message
        String errorMessage = String.format(
            "Failed to publish RabbitMQ message after 5 attempts: %s", 
            e.getMessage()
        );
        fileOrigin.setDesMessageError(errorMessage);
        
        // Set num_retry=1 to allow scheduler retry
        fileOrigin.setNumRetry(1);
        
        // Update metadata
        fileOrigin.setDatUpdate(new Date());
        fileOrigin.setNamChangeAgent("PRODUCER");
        
        fileOriginRepository.save(fileOrigin);
        
        log.info("File_origin {} marked as ERRO with num_retry=1 for scheduler retry", 
            message.getIdtFileOrigin());
    }
}
