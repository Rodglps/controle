package com.controle.arquivos.orchestrator.messaging;

import com.controle.arquivos.orchestrator.dto.MensagemProcessamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Publisher responsável por publicar mensagens de processamento no RabbitMQ.
 * Implementa serialização, confirmação de publicação e retry automático.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange:controle-arquivos-exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key:processamento}")
    private String routingKey;

    /**
     * Publica uma mensagem de processamento no RabbitMQ.
     * Implementa retry até 3 vezes em caso de falha.
     * 
     * @param mensagem Mensagem a ser publicada
     * @throws AmqpException se a publicação falhar após todas as tentativas
     */
    @Retryable(
        retryFor = {AmqpException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void publicar(MensagemProcessamento mensagem) {
        try {
            // Serializar mensagem para JSON
            String json = objectMapper.writeValueAsString(mensagem);
            
            // Criar mensagem AMQP com propriedades
            Message message = MessageBuilder
                .withBody(json.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setCorrelationId(mensagem.getCorrelationId())
                .setMessageId(mensagem.getCorrelationId())
                .build();
            
            // Criar CorrelationData para confirmação de publicação
            CorrelationData correlationData = new CorrelationData(mensagem.getCorrelationId());
            
            // Publicar mensagem com confirmação
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
            
            // Registrar log estruturado
            log.info("Mensagem publicada com sucesso: idFileOrigin={}, nomeArquivo={}, correlationId={}, exchange={}, routingKey={}",
                mensagem.getIdFileOrigin(),
                mensagem.getNomeArquivo(),
                mensagem.getCorrelationId(),
                exchange,
                routingKey);
            
        } catch (Exception e) {
            log.error("Erro ao publicar mensagem: idFileOrigin={}, nomeArquivo={}, correlationId={}, erro={}",
                mensagem.getIdFileOrigin(),
                mensagem.getNomeArquivo(),
                mensagem.getCorrelationId(),
                e.getMessage(),
                e);
            throw new AmqpException("Falha ao publicar mensagem no RabbitMQ", e);
        }
    }
}
