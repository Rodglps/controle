package com.controle.arquivos.orchestrator.messaging;

import com.controle.arquivos.orchestrator.dto.MensagemProcessamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para RabbitMQPublisher
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RabbitMQPublisher publisher;

    private static final String EXCHANGE = "controle-arquivos-exchange";
    private static final String ROUTING_KEY = "processamento";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(publisher, "routingKey", ROUTING_KEY);
    }

    @Test
    void devePublicarMensagemComSucesso() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .nomeArquivo("CIELO_20240115.txt")
            .idMapeamentoOrigemDestino(10L)
            .correlationId("abc-123-def")
            .build();

        String json = "{\"idt_file_origin\":1,\"des_file_name\":\"CIELO_20240115.txt\",\"idt_sever_paths_in_out\":10,\"correlation_id\":\"abc-123-def\"}";
        when(objectMapper.writeValueAsString(mensagem)).thenReturn(json);

        // When
        publisher.publicar(mensagem);

        // Then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        verify(rabbitTemplate).convertAndSend(
            eq(EXCHANGE),
            eq(ROUTING_KEY),
            messageCaptor.capture(),
            correlationCaptor.capture()
        );

        Message capturedMessage = messageCaptor.getValue();
        assertThat(new String(capturedMessage.getBody())).isEqualTo(json);
        assertThat(capturedMessage.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(capturedMessage.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(capturedMessage.getMessageProperties().getCorrelationId()).isEqualTo("abc-123-def");

        CorrelationData capturedCorrelation = correlationCaptor.getValue();
        assertThat(capturedCorrelation.getId()).isEqualTo("abc-123-def");
    }

    @Test
    void deveSerializarMensagemCorretamente() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(2L)
            .nomeArquivo("REDE_20240115.txt")
            .idMapeamentoOrigemDestino(20L)
            .correlationId("xyz-456-ghi")
            .build();

        String expectedJson = "{\"idt_file_origin\":2,\"des_file_name\":\"REDE_20240115.txt\",\"idt_sever_paths_in_out\":20,\"correlation_id\":\"xyz-456-ghi\"}";
        when(objectMapper.writeValueAsString(mensagem)).thenReturn(expectedJson);

        // When
        publisher.publicar(mensagem);

        // Then
        verify(objectMapper).writeValueAsString(mensagem);
        
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), messageCaptor.capture(), any(CorrelationData.class));
        
        String actualJson = new String(messageCaptor.getValue().getBody());
        assertThat(actualJson).isEqualTo(expectedJson);
    }

    @Test
    void deveLancarExcecaoQuandoSerializacaoFalhar() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(3L)
            .nomeArquivo("INVALID.txt")
            .idMapeamentoOrigemDestino(30L)
            .correlationId("err-789-jkl")
            .build();

        when(objectMapper.writeValueAsString(mensagem))
            .thenThrow(new RuntimeException("Erro de serialização"));

        // When / Then
        assertThatThrownBy(() -> publisher.publicar(mensagem))
            .isInstanceOf(AmqpException.class)
            .hasMessageContaining("Falha ao publicar mensagem no RabbitMQ");

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void deveLancarExcecaoQuandoPublicacaoFalhar() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(4L)
            .nomeArquivo("FAIL.txt")
            .idMapeamentoOrigemDestino(40L)
            .correlationId("fail-123-mno")
            .build();

        String json = "{\"idt_file_origin\":4}";
        when(objectMapper.writeValueAsString(mensagem)).thenReturn(json);
        
        doThrow(new AmqpException("Conexão perdida"))
            .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        // When / Then
        assertThatThrownBy(() -> publisher.publicar(mensagem))
            .isInstanceOf(AmqpException.class)
            .hasMessageContaining("Falha ao publicar mensagem no RabbitMQ");
    }

    @Test
    void deveIncluirCorrelationIdNaMensagem() throws Exception {
        // Given
        String correlationId = "unique-correlation-id-12345";
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(5L)
            .nomeArquivo("TEST.txt")
            .idMapeamentoOrigemDestino(50L)
            .correlationId(correlationId)
            .build();

        when(objectMapper.writeValueAsString(mensagem)).thenReturn("{}");

        // When
        publisher.publicar(mensagem);

        // Then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);

        verify(rabbitTemplate).convertAndSend(
            eq(EXCHANGE),
            eq(ROUTING_KEY),
            messageCaptor.capture(),
            correlationCaptor.capture()
        );

        assertThat(messageCaptor.getValue().getMessageProperties().getCorrelationId()).isEqualTo(correlationId);
        assertThat(messageCaptor.getValue().getMessageProperties().getMessageId()).isEqualTo(correlationId);
        assertThat(correlationCaptor.getValue().getId()).isEqualTo(correlationId);
    }

    /**
     * Testa serialização e deserialização round-trip de MensagemProcessamento
     * Valida: Requisitos 4.2, 4.5
     */
    @Test
    void deveSerializarEDeserializarMensagemCorretamente() throws Exception {
        // Given - Criar mensagem original
        MensagemProcessamento mensagemOriginal = MensagemProcessamento.builder()
            .idFileOrigin(100L)
            .nomeArquivo("CIELO_ROUNDTRIP_20240115.txt")
            .idMapeamentoOrigemDestino(200L)
            .correlationId("roundtrip-test-123")
            .build();

        // Usar ObjectMapper real para testar serialização/deserialização
        ObjectMapper realObjectMapper = new ObjectMapper();
        
        // When - Serializar para JSON
        String json = realObjectMapper.writeValueAsString(mensagemOriginal);
        
        // Then - Deserializar de volta para objeto
        MensagemProcessamento mensagemDeserializada = realObjectMapper.readValue(json, MensagemProcessamento.class);
        
        // Validar que todos os campos foram preservados no round-trip
        assertThat(mensagemDeserializada.getIdFileOrigin()).isEqualTo(mensagemOriginal.getIdFileOrigin());
        assertThat(mensagemDeserializada.getNomeArquivo()).isEqualTo(mensagemOriginal.getNomeArquivo());
        assertThat(mensagemDeserializada.getIdMapeamentoOrigemDestino()).isEqualTo(mensagemOriginal.getIdMapeamentoOrigemDestino());
        assertThat(mensagemDeserializada.getCorrelationId()).isEqualTo(mensagemOriginal.getCorrelationId());
        
        // Validar que o JSON contém os nomes de campo corretos (snake_case)
        assertThat(json).contains("\"idt_file_origin\"");
        assertThat(json).contains("\"des_file_name\"");
        assertThat(json).contains("\"idt_sever_paths_in_out\"");
        assertThat(json).contains("\"correlation_id\"");
    }

    /**
     * Testa round-trip com valores nulos
     * Valida: Requisitos 4.2
     */
    @Test
    void deveSerializarEDeserializarMensagemComValoresNulos() throws Exception {
        // Given - Mensagem com alguns campos nulos
        MensagemProcessamento mensagemOriginal = MensagemProcessamento.builder()
            .idFileOrigin(null)
            .nomeArquivo(null)
            .idMapeamentoOrigemDestino(null)
            .correlationId("null-test-456")
            .build();

        // Usar ObjectMapper real
        ObjectMapper realObjectMapper = new ObjectMapper();
        
        // When - Serializar e deserializar
        String json = realObjectMapper.writeValueAsString(mensagemOriginal);
        MensagemProcessamento mensagemDeserializada = realObjectMapper.readValue(json, MensagemProcessamento.class);
        
        // Then - Validar que valores nulos foram preservados
        assertThat(mensagemDeserializada.getIdFileOrigin()).isNull();
        assertThat(mensagemDeserializada.getNomeArquivo()).isNull();
        assertThat(mensagemDeserializada.getIdMapeamentoOrigemDestino()).isNull();
        assertThat(mensagemDeserializada.getCorrelationId()).isEqualTo("null-test-456");
    }

    /**
     * Testa retry em caso de falha de publicação
     * Valida: Requisitos 4.5
     * 
     * Nota: Este teste valida que a exceção é lançada após falha.
     * O comportamento de retry é configurado via @Retryable e requer
     * contexto Spring completo para ser testado de forma integrada.
     */
    @Test
    void deveTentarRetryAoFalharPublicacao() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(6L)
            .nomeArquivo("RETRY_TEST.txt")
            .idMapeamentoOrigemDestino(60L)
            .correlationId("retry-test-789")
            .build();

        String json = "{\"idt_file_origin\":6}";
        when(objectMapper.writeValueAsString(mensagem)).thenReturn(json);
        
        // Simular falha na publicação
        doThrow(new AmqpException("Falha temporária de conexão"))
            .when(rabbitTemplate).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));

        // When / Then
        assertThatThrownBy(() -> publisher.publicar(mensagem))
            .isInstanceOf(AmqpException.class)
            .hasMessageContaining("Falha ao publicar mensagem no RabbitMQ")
            .hasCauseInstanceOf(AmqpException.class);

        // Verificar que tentou publicar (o retry aconteceria em contexto Spring real)
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(
            eq(EXCHANGE),
            eq(ROUTING_KEY),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    /**
     * Testa que retry não acontece para erros de serialização
     * Valida: Requisitos 4.5
     */
    @Test
    void naoDeveTentarRetryParaErroDeSerializacao() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(7L)
            .nomeArquivo("SERIALIZATION_ERROR.txt")
            .idMapeamentoOrigemDestino(70L)
            .correlationId("serialization-error-999")
            .build();

        // Simular erro de serialização (não é AmqpException, então não deve fazer retry)
        when(objectMapper.writeValueAsString(mensagem))
            .thenThrow(new RuntimeException("Erro de serialização"));

        // When / Then
        assertThatThrownBy(() -> publisher.publicar(mensagem))
            .isInstanceOf(AmqpException.class)
            .hasMessageContaining("Falha ao publicar mensagem no RabbitMQ");

        // Verificar que não tentou publicar no RabbitMQ
        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Message.class), any(CorrelationData.class));
    }
}
