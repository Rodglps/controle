package com.controle.arquivos.processor.messaging;

import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import com.controle.arquivos.processor.service.ProcessadorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para RabbitMQConsumer.
 * 
 * Testa:
 * - Consumo de mensagem válida
 * - Validação de mensagem inválida (arquivo não existe)
 * - ACK após sucesso
 * - NACK após falha
 * 
 * **Valida: Requisitos 6.3, 6.4, 6.5, 6.6**
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQConsumerTest {

    @Mock
    private ProcessadorService processadorService;

    @Mock
    private FileOriginRepository fileOriginRepository;

    @Mock
    private FileOriginClientProcessingRepository processingRepository;

    @Mock
    private Channel channel;

    private ObjectMapper objectMapper;
    private RabbitMQConsumer consumer;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        consumer = new RabbitMQConsumer(
                processadorService,
                fileOriginRepository,
                processingRepository,
                objectMapper
        );
    }

    /**
     * Testa consumo de mensagem válida.
     * Deve processar o arquivo e enviar ACK.
     * 
     * **Valida: Requisitos 6.1, 6.2, 6.5**
     */
    @Test
    void deveConsumirMensagemValidaEEnviarAck() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("CIELO_20240115.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("abc-123")
                .build();

        FileOrigin fileOrigin = criarFileOrigin(1L, "CIELO_20240115.txt", true);

        Message rabbitMessage = criarRabbitMessage(mensagem, 1L);

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(1L)).thenReturn(Collections.emptyList());
        doNothing().when(processadorService).processarArquivo(any());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(processadorService, times(1)).processarArquivo(any(MensagemProcessamento.class));
        verify(channel, times(1)).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * Testa validação de mensagem inválida - arquivo não existe.
     * Deve descartar a mensagem e enviar ACK (não reprocessar).
     * 
     * **Valida: Requisitos 6.3, 6.4**
     */
    @Test
    void deveDescartarMensagemQuandoArquivoNaoExiste() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(999L)
                .nomeArquivo("ARQUIVO_INEXISTENTE.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("abc-456")
                .build();

        Message rabbitMessage = criarRabbitMessage(mensagem, 2L);

        when(fileOriginRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * Testa validação de mensagem inválida - arquivo já processado.
     * Deve descartar a mensagem e enviar ACK (não reprocessar).
     * 
     * **Valida: Requisitos 6.3, 6.4**
     */
    @Test
    void deveDescartarMensagemQuandoArquivoJaProcessado() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("CIELO_20240115.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("abc-789")
                .build();

        FileOrigin fileOrigin = criarFileOrigin(1L, "CIELO_20240115.txt", true);
        FileOriginClientProcessing processing = criarProcessing(1L, EtapaProcessamento.PROCESSED, StatusProcessamento.CONCLUIDO);

        Message rabbitMessage = criarRabbitMessage(mensagem, 3L);

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(1L)).thenReturn(List.of(processing));

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(3L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * Testa validação de mensagem inválida - arquivo inativo.
     * Deve descartar a mensagem e enviar ACK (não reprocessar).
     * 
     * **Valida: Requisitos 6.3, 6.4**
     */
    @Test
    void deveDescartarMensagemQuandoArquivoInativo() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("CIELO_20240115.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("abc-101")
                .build();

        FileOrigin fileOrigin = criarFileOrigin(1L, "CIELO_20240115.txt", false);

        Message rabbitMessage = criarRabbitMessage(mensagem, 4L);

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(4L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * Testa NACK após falha no processamento.
     * Deve enviar NACK com requeue=true para reprocessamento.
     * 
     * **Valida: Requisitos 6.6**
     */
    @Test
    void deveEnviarNackQuandoProcessamentoFalhar() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("CIELO_20240115.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("abc-202")
                .build();

        FileOrigin fileOrigin = criarFileOrigin(1L, "CIELO_20240115.txt", true);

        Message rabbitMessage = criarRabbitMessage(mensagem, 5L);

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(1L)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Erro de processamento")).when(processadorService).processarArquivo(any());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(processadorService, times(1)).processarArquivo(any(MensagemProcessamento.class));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(5L, false, true);
    }

    /**
     * Testa validação de mensagem com idFileOrigin nulo.
     * Deve descartar a mensagem e enviar ACK.
     * 
     * **Valida: Requisitos 6.3, 6.4**
     */
    @Test
    void deveDescartarMensagemComIdFileOriginNulo() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(null)
                .nomeArquivo("ARQUIVO.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("abc-303")
                .build();

        Message rabbitMessage = criarRabbitMessage(mensagem, 6L);

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(fileOriginRepository, never()).findById(any());
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(6L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    // Helper methods

    private FileOrigin criarFileOrigin(Long id, String fileName, boolean active) {
        return FileOrigin.builder()
                .id(id)
                .acquirerId(1L)
                .fileName(fileName)
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private FileOriginClientProcessing criarProcessing(Long fileOriginClientId, 
                                                       EtapaProcessamento step, 
                                                       StatusProcessamento status) {
        return FileOriginClientProcessing.builder()
                .id(1L)
                .fileOriginClientId(fileOriginClientId)
                .step(step)
                .status(status)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Message criarRabbitMessage(MensagemProcessamento mensagem, long deliveryTag) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(mensagem);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(body, properties);
    }
}
