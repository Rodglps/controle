package com.controle.arquivos.processor;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Aplicação Spring Boot do Pod Processador.
 * Responsável por consumir mensagens do RabbitMQ e processar arquivos.
 */
@SpringBootApplication(scanBasePackages = {
    "com.controle.arquivos.processor",
    "com.controle.arquivos.common"
})
@EnableRabbit
@EntityScan(basePackages = "com.controle.arquivos.common.domain.entity")
@EnableJpaRepositories(basePackages = "com.controle.arquivos.common.repository")
public class ProcessadorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessadorApplication.class, args);
    }
}
