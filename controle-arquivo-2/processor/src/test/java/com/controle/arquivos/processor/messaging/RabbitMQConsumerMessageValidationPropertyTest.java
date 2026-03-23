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
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para validação de mensagens no RabbitMQConsumer.
 * 
 * Feature: controle-de-arquivos, Property 11: Validação de Mensagem Recebida
 * 
 * Para qualquer mensagem recebida do RabbitMQ, o Processador deve validar que o arquivo existe
 * na tabela file_origin, e se não existir ou já foi processado, deve descartar a mensagem e
 * registrar alerta.
 * 
 * **Valida: Requisitos 6.3, 6.4**
 */
class RabbitMQConsumerMessageValidationPropertyTest {

    private ProcessadorService processadorService;
    private FileOriginRepository fileOriginRepository;
    private FileOriginClientProcessingRepository processingRepository;
    private Channel channel;
    private ObjectMapper objectMapper;
    private RabbitMQConsumer consumer;

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer mensagem válida (arquivo existe, está ativo, não foi processado),
     * o sistema deve processar a mensagem e não descartá-la.
     */
    @Property(tries = 100)
    void mensagensValidasDevemSerProcessadas(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOriginAtivo(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(mensagem.getIdFileOrigin()))
            .thenReturn(Collections.emptyList());
        doNothing().when(processadorService).processarArquivo(any());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert - Mensagem válida deve ser processada
        verify(processadorService, times(1)).processarArquivo(any(MensagemProcessamento.class));
        verify(channel, times(1)).basicAck(deliveryTag, false);
    }

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer mensagem onde o arquivo não existe em file_origin,
     * o sistema deve descartar a mensagem com alerta e não processar.
     */
    @Property(tries = 100)
    void mensagensComArquivoInexistenteDevemSerDescartadas(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        // Simular arquivo não existe
        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.empty());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert - Mensagem deve ser descartada sem processar
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(deliveryTag, false); // ACK para descartar
    }

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer mensagem onde o arquivo está inativo (flg_active = false),
     * o sistema deve descartar a mensagem com alerta e não processar.
     */
    @Property(tries = 100)
    void mensagensComArquivoInativoDevemSerDescartadas(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOriginInativo = criarFileOriginInativo(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOriginInativo));

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert - Mensagem deve ser descartada sem processar
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(deliveryTag, false); // ACK para descartar
    }

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer mensagem onde o arquivo já foi processado (status CONCLUIDO na etapa PROCESSED),
     * o sistema deve descartar a mensagem com alerta e não processar novamente.
     */
    @Property(tries = 100)
    void mensagensComArquivoJaProcessadoDevemSerDescartadas(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOriginAtivo(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        FileOriginClientProcessing processingConcluido = criarProcessing(
            mensagem.getIdFileOrigin(),
            EtapaProcessamento.PROCESSED,
            StatusProcessamento.CONCLUIDO
        );
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(mensagem.getIdFileOrigin()))
            .thenReturn(List.of(processingConcluido));

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert - Mensagem deve ser descartada sem processar
        verify(processadorService, never()).processarArquivo(any());
        verify(channel, times(1)).basicAck(deliveryTag, false); // ACK para descartar
    }

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer mensagem com idFileOrigin nulo,
     * o sistema deve descartar a mensagem com alerta e não processar.
     */
    @Property(tries = 100)
    void mensagensComIdFileOriginNuloDevemSerDescartadas(
            @ForAll("nomeArquivo") String nomeArquivo,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        MensagemProcessamento mensagemInvalida = MensagemProcessamento.builder()
            .idFileOrigin(null) // ID nulo
            .nomeArquivo(nomeArquivo)
            .idMapeamentoOrigemDestino(1L)
            .correlationId("corr-" + nomeArquivo)
            .build();
        Message rabbitMessage = criarRabbitMessage(mensagemInvalida, deliveryTag);

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert - Mensagem deve ser descartada sem processar
        verify(processadorService, never()).processarArquivo(any());
        verify(fileOriginRepository, never()).findById(any()); // Não deve nem buscar no banco
        verify(channel, times(1)).basicAck(deliveryTag, false); // ACK para descartar
    }

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer mensagem onde o arquivo tem processamento em outras etapas
     * (mas não PROCESSED/CONCLUIDO), o sistema deve processar normalmente.
     */
    @Property(tries = 100)
    void mensagensComProcessamentoEmOutrasEtapasDevemSerProcessadas(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("deliveryTag") long deliveryTag,
            @ForAll("etapaIntermediaria") EtapaProcessamento etapa,
            @ForAll("statusIntermediario") StatusProcessamento status) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOriginAtivo(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        FileOriginClientProcessing processingIntermediario = criarProcessing(
            mensagem.getIdFileOrigin(),
            etapa,
            status
        );
        Message rabbitMessage = criarRabbitMessage(mensagem, deliveryTag);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(processingRepository.findByFileOriginClientId(mensagem.getIdFileOrigin()))
            .thenReturn(List.of(processingIntermediario));
        doNothing().when(processadorService).processarArquivo(any());

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert - Mensagem deve ser processada (não está PROCESSED/CONCLUIDO)
        verify(processadorService, times(1)).processarArquivo(any(MensagemProcessamento.class));
        verify(channel, times(1)).basicAck(deliveryTag, false);
    }

    /**
     * **Propriedade 11: Validação de Mensagem Recebida**
     * **Valida: Requisitos 6.3, 6.4**
     * 
     * Para qualquer combinação de mensagens válidas e inválidas,
     * o sistema deve validar corretamente cada uma.
     */
    @Property(tries = 100)
    void validacaoDeveSerConsistenteParaQualquerMensagem(
            @ForAll("mensagemQualquer") MensagemScenario scenario,
            @ForAll("deliveryTag") long deliveryTag) throws Exception {
        
        // Arrange
        setupMocks();
        Message rabbitMessage = criarRabbitMessage(scenario.mensagem, deliveryTag);

        // Configure mocks based on scenario
        configurarMocksParaScenario(scenario);

        // Act
        consumer.consumir(rabbitMessage, channel);

        // Assert
        if (scenario.deveSerProcessada) {
            verify(processadorService, times(1)).processarArquivo(any(MensagemProcessamento.class));
        } else {
            verify(processadorService, never()).processarArquivo(any());
        }
        
        // Sempre deve enviar ACK (para processar ou descartar)
        verify(channel, times(1)).basicAck(deliveryTag, false);
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
    Arbitrary<String> nomeArquivo() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(5)
            .ofMaxLength(50)
            .map(s -> s + ".txt");
    }

    @Provide
    Arbitrary<Long> deliveryTag() {
        return Arbitraries.longs().between(1L, 1000000L);
    }

    @Provide
    Arbitrary<EtapaProcessamento> etapaIntermediaria() {
        // Retornar qualquer etapa EXCETO PROCESSED
        return Arbitraries.of(
            EtapaProcessamento.COLETA,
            EtapaProcessamento.RAW,
            EtapaProcessamento.STAGING,
            EtapaProcessamento.ORDINATION,
            EtapaProcessamento.PROCESSING
        );
    }

    @Provide
    Arbitrary<StatusProcessamento> statusIntermediario() {
        // Retornar qualquer status EXCETO a combinação PROCESSED/CONCLUIDO
        return Arbitraries.of(
            StatusProcessamento.EM_ESPERA,
            StatusProcessamento.PROCESSAMENTO,
            StatusProcessamento.ERRO
        );
    }

    @Provide
    Arbitrary<MensagemScenario> mensagemQualquer() {
        return Arbitraries.of(TipoScenario.values()).flatMap(tipo -> {
            return mensagemValida().map(msg -> new MensagemScenario(tipo, msg));
        });
    }

    // ========== Helper Classes ==========

    enum TipoScenario {
        VALIDA_NAO_PROCESSADA(true),
        ARQUIVO_NAO_EXISTE(false),
        ARQUIVO_INATIVO(false),
        ARQUIVO_JA_PROCESSADO(false),
        ID_FILE_ORIGIN_NULO(false),
        PROCESSAMENTO_INTERMEDIARIO(true);

        final boolean deveSerProcessada;

        TipoScenario(boolean deveSerProcessada) {
            this.deveSerProcessada = deveSerProcessada;
        }
    }

    static class MensagemScenario {
        final TipoScenario tipo;
        final MensagemProcessamento mensagem;
        final boolean deveSerProcessada;

        MensagemScenario(TipoScenario tipo, MensagemProcessamento mensagem) {
            this.tipo = tipo;
            this.mensagem = mensagem;
            this.deveSerProcessada = tipo.deveSerProcessada;
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

    private void configurarMocksParaScenario(MensagemScenario scenario) throws Exception {
        switch (scenario.tipo) {
            case VALIDA_NAO_PROCESSADA:
                FileOrigin fileOriginValido = criarFileOriginAtivo(
                    scenario.mensagem.getIdFileOrigin(),
                    scenario.mensagem.getNomeArquivo()
                );
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(Optional.of(fileOriginValido));
                when(processingRepository.findByFileOriginClientId(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(Collections.emptyList());
                doNothing().when(processadorService).processarArquivo(any());
                break;

            case ARQUIVO_NAO_EXISTE:
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(Optional.empty());
                break;

            case ARQUIVO_INATIVO:
                FileOrigin fileOriginInativo = criarFileOriginInativo(
                    scenario.mensagem.getIdFileOrigin(),
                    scenario.mensagem.getNomeArquivo()
                );
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(Optional.of(fileOriginInativo));
                break;

            case ARQUIVO_JA_PROCESSADO:
                FileOrigin fileOriginProcessado = criarFileOriginAtivo(
                    scenario.mensagem.getIdFileOrigin(),
                    scenario.mensagem.getNomeArquivo()
                );
                FileOriginClientProcessing processingConcluido = criarProcessing(
                    scenario.mensagem.getIdFileOrigin(),
                    EtapaProcessamento.PROCESSED,
                    StatusProcessamento.CONCLUIDO
                );
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(Optional.of(fileOriginProcessado));
                when(processingRepository.findByFileOriginClientId(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(List.of(processingConcluido));
                break;

            case ID_FILE_ORIGIN_NULO:
                // Mensagem já tem ID nulo, não precisa configurar mocks
                break;

            case PROCESSAMENTO_INTERMEDIARIO:
                FileOrigin fileOriginIntermediario = criarFileOriginAtivo(
                    scenario.mensagem.getIdFileOrigin(),
                    scenario.mensagem.getNomeArquivo()
                );
                FileOriginClientProcessing processingIntermediario = criarProcessing(
                    scenario.mensagem.getIdFileOrigin(),
                    EtapaProcessamento.STAGING,
                    StatusProcessamento.PROCESSAMENTO
                );
                when(fileOriginRepository.findById(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(Optional.of(fileOriginIntermediario));
                when(processingRepository.findByFileOriginClientId(scenario.mensagem.getIdFileOrigin()))
                    .thenReturn(List.of(processingIntermediario));
                doNothing().when(processadorService).processarArquivo(any());
                break;
        }
    }

    private FileOrigin criarFileOriginAtivo(Long id, String fileName) {
        return FileOrigin.builder()
                .id(id)
                .acquirerId(1L)
                .fileName(fileName)
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(true) // Ativo
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private FileOrigin criarFileOriginInativo(Long id, String fileName) {
        return FileOrigin.builder()
                .id(id)
                .acquirerId(1L)
                .fileName(fileName)
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(false) // Inativo
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
