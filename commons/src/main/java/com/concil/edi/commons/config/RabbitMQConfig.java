package com.concil.edi.commons.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for file transfer messaging.
 * Configures Quorum Queue with durability for high availability.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.file-transfer:edi.file.transfer.queue}")
    private String queueName;

    @Value("${rabbitmq.exchange.file-transfer:edi.file.transfer.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routing-key.file-transfer:edi.file.transfer}")
    private String routingKey;

    /**
     * Declares a Quorum Queue for file transfer messages.
     * Quorum queues provide high availability and data safety.
     */
    @Bean
    public Queue fileTransferQueue() {
        return QueueBuilder.durable(queueName)
                .quorum()
                .build();
    }

    /**
     * Declares a direct exchange for file transfer routing.
     */
    @Bean
    public DirectExchange fileTransferExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /**
     * Binds the queue to the exchange with the routing key.
     */
    @Bean
    public Binding fileTransferBinding(Queue fileTransferQueue, DirectExchange fileTransferExchange) {
        return BindingBuilder.bind(fileTransferQueue)
                .to(fileTransferExchange)
                .with(routingKey);
    }

    /**
     * Configures JSON message converter for serializing/deserializing messages.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configures RabbitTemplate with JSON message converter.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
