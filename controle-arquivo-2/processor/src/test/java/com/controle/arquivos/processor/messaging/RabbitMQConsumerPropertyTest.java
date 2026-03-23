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
import net.jqwik.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para RabbitMQConsumer.
 * 
 * **Valida: Requisitos 6.5, 6.6**
 * 
 * Feature: controle-de-arquivos, Property 12: Confirmação de Mensagens
 * 
 * Para qualquer processamento concluído com sucesso, o Processador deve confirmar (ACK) a mensagem,
 * e para qualquer falha, deve rejeitar (NACK) a mensagem para reprocessamento.
 */
class RabbitMQConsumerPropertyTest {

    private ProcessadorService processadorService;
    private FileOriginRepository fileOriginRepository;
    private FileOriginClientProcessingRepository processingRepository;
    private Channel channel;
    private ObjectMapper objectMapper;
    private RabbitMQConsumer consumer;

    /**
     * **Propriedade 12: Confirmação de Mensagens**
     * **Valida: Requisitos 6.5, 6.6**
     * 
     * Para qualquer mensagem válida processada com sucesso,
     * o sistema deve enviar ACK e nunca NACK.
     */
    @Property(tries = 100)
    void mensagensValidasProcessadasComSucessoRecebemAck(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), true);
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(mensagem.getIdFileOrigin())).thenReturn(Collections.emptyList());
        doNothing().when(processadorService).processarArquivo(any());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * **Propriedade 12: Confirmação de Mensagens**
     * **Valida: Requisitos 6.5, 6.6**
     * 
     * Para qualquer mensagem inválida (arquivo não existe, já processado, ou inativo),
     * o sistema deve enviar ACK para descartar e nunca NACK.
     */
    @Property(tries = 100)
    void mensagensInvalidasRecebemAckParaDescartar(
            @ForAll("mensagemInvalida") MensagemInvalidaScenario scenario,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        Message rabbitMessage = criarRabbitMessage(scenario.mensagem, deliveryTag);

        // Configure mock based on scenario type
        switch (scenario.tipo) {
            case ARQUIVO_NAO_EXISTE:
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin())).thenReturn(Optional.empty());
                break;
            case ARQUIVO_INATIVO:
                FileOrigin fileOriginInativo = criarFileOrigin(scenario.mensagem.getIdFileOrigin(), 
                                                                scenario.mensagem.getNomeArquivo(), false);
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOriginInativo));
                break;
            case ARQUIVO_JA_PROCESSADO:
                FileOrigin fileOriginProcessado = criarFileOrigin(scenario.mensagem.getIdFileOrigin(), 
                                                                   scenario.mensagem.getNomeArquivo(), true);
                FileOriginClientProcessing processing = criarProcessing(scenario.mensagem.getIdFileOrigin(), 
                                                                        EtapaProcessamento.PROCESSED, 
                                                                        StatusProcessamento.CONCLUIDO);
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOriginProcessado));
                when(processingRepository.findByFileOriginClientId(scenario.mensagem.getIdFileOrigin())).thenReturn(List.of(processing));
                break;
            case ID_FILE_ORIGIN_NULO:
                // No additional setup needed - mensagem already has null idFileOrigin
                break;
        }

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * **Propriedade 12: Confirmação de Mensagens**
     * **Valida: Requisitos 6.5, 6.6**
     * 
     * Para qualquer mensagem válida que falha durante processamento,
     * o sistema deve enviar NACK com requeue=true e nunca ACK.
     */
    @Property(tries = 100)
    void mensagensComFalhaDeProcessamentoRecebemNack(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag,
            @ForAll("erroProcessamento") Exception erro) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), true);
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(mensagem.getIdFileOrigin())).thenReturn(Collections.emptyList());
        doThrow(erro).when(processadorService).processarArquivo(any());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(deliveryTag, false, true);
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<MensagemProcessamento> mensagemValida() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 1000000L),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50).map(s -> s + ".txt"),
                Arbitraries.longs().between(1L, 100L),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(20)
        ).as((idFileOrigin, nomeArquivo, idMapeamento, correlationId) ->
                MensagemProcessamento.builder()
                        .idFileOrigin(idFileOrigin)
                        .nomeArquivo(nomeArquivo)
                        .idMapeamentoOrigemDestino(idMapeamento)
                        .correlationId(correlationId)
                        .build()
        );
    }

    @Provide
    Arbitrary<MensagemInvalidaScenario> mensagemInvalida() {
        return Arbitraries.of(TipoMensagemInvalida.values()).flatMap(tipo -> {
            switch (tipo) {
                case ID_FILE_ORIGIN_NULO:
                    return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50).map(nome ->
                            new MensagemInvalidaScenario(
                                    tipo,
                                    MensagemProcessamento.builder()
                                            .idFileOrigin(null)
                                            .nomeArquivo(nome + ".txt")
                                            .idMapeamentoOrigemDestino(1L)
                                            .correlationId("corr-" + nome)
                                            .build()
                            )
                    );
                default:
                    return mensagemValida().map(msg -> new MensagemInvalidaScenario(tipo, msg));
            }
        });
    }

    @Provide
    Arbitrary<Long> deliveryTag() {
        return Arbitraries.longs().between(1L, 1000000L);
    }

    @Provide
    Arbitrary<Exception> erroProcessamento() {
        return Arbitraries.of(
                new RuntimeException("Erro de conexão SFTP"),
                new RuntimeException("Erro ao baixar arquivo"),
                new RuntimeException("Erro ao fazer upload"),
                new IllegalStateException("Estado inválido"),
                new Exception("Erro genérico de processamento")
        );
    }

    // ========== Helper Classes ==========

    enum TipoMensagemInvalida {
        ARQUIVO_NAO_EXISTE,
        ARQUIVO_INATIVO,
        ARQUIVO_JA_PROCESSADO,
        ID_FILE_ORIGIN_NULO
    }

    static class MensagemInvalidaScenario {
        final TipoMensagemInvalida tipo;
        final MensagemProcessamento mensagem;

        MensagemInvalidaScenario(TipoMensagemInvalida tipo, MensagemProcessamento mensagem) {
            this.tipo = tipo;
            this.mensagem = mensagem;
        }
    }

    // ========== Helper Methods ==========

    private void setupMocks() {
        processadorService = mock(ProcessadorService.class);
        fileOriginRepository = mock(FileOriginRepository.class);
        processingRepository = mock(FileOriginClientProcessingRepository.class);
        channel = mock(Channel.class);
        objectMapper = new ObjectMapper();
        consumer = new RabbitMQConsumer(
                processadorService,
                fileOriginRepository,
                processingRepository,
                objectMapper
        );
    }

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
