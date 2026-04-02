package com.concil.edi.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Producer module.
 * Responsible for scheduling file collection from SFTP servers and publishing messages to RabbitMQ.
 */
@SpringBootApplication(scanBasePackages = {
    "com.concil.edi.producer",
    "com.concil.edi.commons"
})
@EnableScheduling
@EnableRetry
@EntityScan(basePackages = "com.concil.edi.commons.entity")
@EnableJpaRepositories(basePackages = "com.concil.edi.commons.repository")
public class ProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }
}
