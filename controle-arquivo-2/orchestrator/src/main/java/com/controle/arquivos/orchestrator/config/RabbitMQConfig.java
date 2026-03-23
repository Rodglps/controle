package com.controle.arquivos.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Configuração do RabbitMQ para o Orquestrador.
 * Configura exchange, queue, binding e publisher confirms.
 */
@Slf4j
@Configuration
@EnableRetry
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange:controle-arquivos-exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue:processamento-queue}")
    private String queue;

    @Value("${app.rabbitmq.routing-key:processamento}")
    private String routingKey;

    /**
     * Cria o exchange do tipo Direct
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(exchange, true, false);
    }

    /**
     * Cria a fila de processamento com durabilidade
     */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(queue).build();
    }

    /**
     * Cria o binding entre exchange e queue
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    /**
     * Configura o RabbitTemplate com publisher confirms e conversão JSON
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        
        // Configurar conversão JSON
        template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        
        // Habilitar publisher confirms
        template.setMandatory(true);
        
        // Callback para confirmação de publicação
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Mensagem confirmada pelo broker: correlationId={}", 
                    correlationData != null ? correlationData.getId() : "unknown");
            } else {
                log.error("Mensagem rejeitada pelo broker: correlationId={}, causa={}", 
                    correlationData != null ? correlationData.getId() : "unknown", 
                    cause);
            }
        });
        
        // Callback para mensagens retornadas (não roteadas)
        template.setReturnsCallback(returned -> {
            log.error("Mensagem retornada pelo broker (não roteada): exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText());
        });
        
        return template;
    }
}
