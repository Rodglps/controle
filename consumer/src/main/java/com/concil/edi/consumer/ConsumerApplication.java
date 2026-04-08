package com.concil.edi.consumer;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Consumer Application - Consumes RabbitMQ messages and transfers files from SFTP to destinations
 * 
 * Requirements: 18.2
 */
@SpringBootApplication
@EnableRabbit
@ComponentScan(basePackages = {"com.concil.edi.consumer", "com.concil.edi.commons.config", "com.concil.edi.commons.service"})
@EntityScan(basePackages = "com.concil.edi.commons.entity")
@EnableJpaRepositories(basePackages = "com.concil.edi.commons.repository")
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
