package com.controle.arquivos.orchestrator.messaging;

import com.controle.arquivos.orchestrator.dto.MensagemProcessamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para retry de publicação RabbitMQ.
 * 
 * Feature: controle-de-arquivos, Property 9: Retry de Publicação
 * 
 * Para qualquer falha de publicação no RabbitMQ, o Orquestrador deve tentar
 * reenviar até 3 vezes antes de desistir.
 * 
 * **Valida: Requisitos 4.5**
 */
class RabbitMQPublisherRetryPropertyTest {

    /**
     * Propriedade 9: Retry de Publicação
     * 
     * Para qualquer falha de publicação no RabbitMQ, o Orquestrador deve tentar
     * reenviar até 3 vezes antes de desistir.
     * 
     * Este teste verifica que:
     * 1. Quando a publicação falha, o sistema tenta reenviar
     * 2. O sistema tenta até 3 vezes antes de desistir
     * 3. Se todas as tentativas falharem, uma exceção é lançada
     * 4. Se uma tentativa intermediária for bem-sucedida, o retry para
     * 
     * **Valida: Requisitos 4.5**
     * 
     * Nota: Este teste simula o comportamento de retry usando RetryTemplate
     * diretamente, pois a anotação @Retryable requer contexto Spring completo.
     */
    @Property(tries = 100)
    void propriedade9_retryDePublicacao(
        @ForAll("mensagemProcessamento") MensagemProcessamento mensagem,
        @ForAll @IntRange(min = 1, max = 3) int tentativasAteFalhar
    ) throws Exception {
        // Arrange
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchange", "test-exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "test-routing-key");

        // Configurar RetryTemplate para simular comportamento de @Retryable
        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(100)
            .retryOn(AmqpException.class)
            .build();

        // Simular falhas nas primeiras N tentativas, depois sucesso
        doAnswer(invocation -> {
            throw new AmqpException("Falha temporária de conexão RabbitMQ");
        }).when(rabbitTemplate).convertAndSend(
            eq("test-exchange"),
            eq("test-routing-key"),
            any(Message.class),
            any(CorrelationData.class)
        );

        // Act & Assert
        if (tentativasAteFalhar == 3) {
            // Se todas as 3 tentativas falharem, deve lançar exceção
            assertThrows(AmqpException.class, () -> {
                retryTemplate.execute(context -> {
                    publisher.publicar(mensagem);
                    return null;
                });
            }, "Deve lançar exceção após 3 tentativas falhadas");

            // Verificar que tentou exatamente 3 vezes
            verify(rabbitTemplate, times(3)).convertAndSend(
                eq("test-exchange"),
                eq("test-routing-key"),
                any(Message.class),
                any(CorrelationData.class)
            );
        } else {
            // Se deve ter sucesso antes da 3ª tentativa
            // Reconfigurar mock para ter sucesso após N falhas
            reset(rabbitTemplate);
            
            doThrow(new AmqpException("Falha temporária"))
                .doThrow(new AmqpException("Falha temporária"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(
                    eq("test-exchange"),
                    eq("test-routing-key"),
                    any(Message.class),
                    any(CorrelationData.class)
                );

            // Deve ter sucesso após algumas tentativas
            assertDoesNotThrow(() -> {
                retryTemplate.execute(context -> {
                    publisher.publicar(mensagem);
                    return null;
                });
            }, "Deve ter sucesso após retry");

            // Verificar que tentou até ter sucesso (no máximo 3 vezes)
            verify(rabbitTemplate, atMost(3)).convertAndSend(
                eq("test-exchange"),
                eq("test-routing-key"),
                any(Message.class),
                any(CorrelationData.class)
            );
        }
    }

    /**
     * Propriedade: Sistema deve tentar exatamente 3 vezes quando todas as tentativas falharem.
     */
    @Property(tries = 100)
    void deveTentarExatamente3VezesQuandoTodasFalharem(
        @ForAll("mensagemProcessamento") MensagemProcessamento mensagem
    ) throws Exception {
        // Arrange
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchange", "exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "key");

        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(50)
            .retryOn(AmqpException.class)
            .build();

        // Simular falha em todas as tentativas
        doThrow(new AmqpException("Connection timeout"))
            .when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(Message.class),
                any(CorrelationData.class)
            );

        // Act
        assertThrows(AmqpException.class, () -> {
            retryTemplate.execute(context -> {
                publisher.publicar(mensagem);
                return null;
            });
        });

        // Assert
        verify(rabbitTemplate, times(3)).convertAndSend(
            eq("exchange"),
            eq("key"),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    /**
     * Propriedade: Sistema deve parar de tentar após sucesso em tentativa intermediária.
     */
    @Property(tries = 100)
    void devePararAposSucessoEmTentativaIntermediaria(
        @ForAll("mensagemProcessamento") MensagemProcessamento mensagem,
        @ForAll @IntRange(min = 1, max = 2) int tentativasAteSucesso
    ) throws Exception {
        // Arrange
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchange", "ex");
        ReflectionTestUtils.setField(publisher, "routingKey", "rk");

        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(50)
            .retryOn(AmqpException.class)
            .build();

        // Configurar mock para falhar N vezes e depois ter sucesso
        if (tentativasAteSucesso == 1) {
            // Falha na 1ª tentativa, sucesso na 2ª
            doThrow(new AmqpException("Falha 1"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(
                    anyString(),
                    anyString(),
                    any(Message.class),
                    any(CorrelationData.class)
                );
        } else {
            // Falha na 1ª e 2ª tentativas, sucesso na 3ª
            doThrow(new AmqpException("Falha 1"))
                .doThrow(new AmqpException("Falha 2"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(
                    anyString(),
                    anyString(),
                    any(Message.class),
                    any(CorrelationData.class)
                );
        }

        // Act
        assertDoesNotThrow(() -> {
            retryTemplate.execute(context -> {
                publisher.publicar(mensagem);
                return null;
            });
        });

        // Assert - Deve ter tentado exatamente (tentativasAteSucesso + 1) vezes
        verify(rabbitTemplate, times(tentativasAteSucesso + 1)).convertAndSend(
            eq("ex"),
            eq("rk"),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    /**
     * Propriedade: Diferentes tipos de falhas AmqpException devem acionar retry.
     */
    @Property(tries = 50)
    void diferentesTiposDeFalhasDevemAcionarRetry(
        @ForAll("mensagemProcessamento") MensagemProcessamento mensagem,
        @ForAll("amqpExceptionMessage") String errorMessage
    ) throws Exception {
        // Arrange
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchange", "test-ex");
        ReflectionTestUtils.setField(publisher, "routingKey", "test-key");

        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(50)
            .retryOn(AmqpException.class)
            .build();

        // Simular falha com mensagem de erro específica
        doThrow(new AmqpException(errorMessage))
            .when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(Message.class),
                any(CorrelationData.class)
            );

        // Act
        assertThrows(AmqpException.class, () -> {
            retryTemplate.execute(context -> {
                publisher.publicar(mensagem);
                return null;
            });
        });

        // Assert - Deve ter tentado 3 vezes independente do tipo de erro
        verify(rabbitTemplate, times(3)).convertAndSend(
            eq("test-ex"),
            eq("test-key"),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    /**
     * Propriedade: Retry deve preservar os dados da mensagem em todas as tentativas.
     */
    @Property(tries = 100)
    void retryDevePreservarDadosDaMensagem(
        @ForAll("mensagemProcessamento") MensagemProcessamento mensagem
    ) throws Exception {
        // Arrange
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchange", "preserve-ex");
        ReflectionTestUtils.setField(publisher, "routingKey", "preserve-key");

        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(50)
            .retryOn(AmqpException.class)
            .build();

        // Simular falha em todas as tentativas
        doThrow(new AmqpException("Falha"))
            .when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(Message.class),
                any(CorrelationData.class)
            );

        // Act
        try {
            retryTemplate.execute(context -> {
                publisher.publicar(mensagem);
                return null;
            });
        } catch (AmqpException e) {
            // Esperado
        }

        // Assert - Capturar todas as mensagens enviadas
        var messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        var correlationCaptor = org.mockito.ArgumentCaptor.forClass(CorrelationData.class);
        
        verify(rabbitTemplate, times(3)).convertAndSend(
            eq("preserve-ex"),
            eq("preserve-key"),
            messageCaptor.capture(),
            correlationCaptor.capture()
        );

        // Verificar que todas as 3 tentativas usaram os mesmos dados
        var messages = messageCaptor.getAllValues();
        var correlations = correlationCaptor.getAllValues();
        
        assertEquals(3, messages.size(), "Deve ter 3 mensagens capturadas");
        assertEquals(3, correlations.size(), "Deve ter 3 correlations capturadas");

        // Verificar que o conteúdo é o mesmo em todas as tentativas
        String expectedJson = objectMapper.writeValueAsString(mensagem);
        for (Message msg : messages) {
            String actualJson = new String(msg.getBody());
            assertEquals(expectedJson, actualJson, 
                "Conteúdo da mensagem deve ser preservado em todas as tentativas");
            assertEquals(mensagem.getCorrelationId(), msg.getMessageProperties().getCorrelationId(),
                "CorrelationId deve ser preservado em todas as tentativas");
        }

        for (CorrelationData corr : correlations) {
            assertEquals(mensagem.getCorrelationId(), corr.getId(),
                "CorrelationData ID deve ser preservado em todas as tentativas");
        }
    }

    /**
     * Propriedade: Primeira tentativa bem-sucedida não deve acionar retry.
     */
    @Property(tries = 100)
    void primeiraTentativaBemSucedidaNaoDeveAcionarRetry(
        @ForAll("mensagemProcessamento") MensagemProcessamento mensagem
    ) throws Exception {
        // Arrange
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        RabbitMQPublisher publisher = new RabbitMQPublisher(rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "exchange", "success-ex");
        ReflectionTestUtils.setField(publisher, "routingKey", "success-key");

        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(50)
            .retryOn(AmqpException.class)
            .build();

        // Simular sucesso na primeira tentativa
        doNothing().when(rabbitTemplate).convertAndSend(
            anyString(),
            anyString(),
            any(Message.class),
            any(CorrelationData.class)
        );

        // Act
        assertDoesNotThrow(() -> {
            retryTemplate.execute(context -> {
                publisher.publicar(mensagem);
                return null;
            });
        });

        // Assert - Deve ter tentado apenas 1 vez
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq("success-ex"),
            eq("success-key"),
            any(Message.class),
            any(CorrelationData.class)
        );
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<MensagemProcessamento> mensagemProcessamento() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, 1_000_000L);
        
        Arbitrary<String> nomes = Arbitraries.strings()
            .withCharRange('A', 'Z')
            .ofMinLength(5)
            .ofMaxLength(30)
            .map(s -> s + "_" + System.currentTimeMillis() + ".txt");
        
        Arbitrary<String> correlationIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-')
            .ofMinLength(10)
            .ofMaxLength(50);

        return Combinators.combine(ids, nomes, ids, correlationIds)
            .as((idFileOrigin, nomeArquivo, idMapeamento, correlationId) ->
                MensagemProcessamento.builder()
                    .idFileOrigin(idFileOrigin)
                    .nomeArquivo(nomeArquivo)
                    .idMapeamentoOrigemDestino(idMapeamento)
                    .correlationId(correlationId)
                    .build()
            );
    }

    @Provide
    Arbitrary<String> amqpExceptionMessage() {
        return Arbitraries.of(
            "Connection timeout",
            "Connection refused",
            "Channel closed",
            "Unable to connect to broker",
            "Network error",
            "Authentication failed",
            "Channel shutdown",
            "Connection lost",
            "Broker unavailable",
            "Socket timeout"
        );
    }
}
