package com.controle.arquivos.processor;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes para ProcessadorApplication.
 * 
 * Valida: Requisitos 6.1
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessadorApplicationTest {

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @Test
    void applicationClassHasSpringBootApplicationAnnotation() {
        assertThat(ProcessadorApplication.class.isAnnotationPresent(SpringBootApplication.class))
            .isTrue();
    }

    @Test
    void applicationClassHasEnableRabbitAnnotation() {
        assertThat(ProcessadorApplication.class.isAnnotationPresent(EnableRabbit.class))
            .isTrue();
    }

    @Test
    void springBootApplicationAnnotationHasCorrectScanBasePackages() {
        SpringBootApplication annotation = ProcessadorApplication.class
            .getAnnotation(SpringBootApplication.class);
        
        assertThat(annotation.scanBasePackages())
            .contains("com.controle.arquivos.processor")
            .contains("com.controle.arquivos.common");
    }
}
