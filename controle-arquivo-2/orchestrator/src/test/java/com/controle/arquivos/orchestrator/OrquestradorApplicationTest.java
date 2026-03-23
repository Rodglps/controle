package com.controle.arquivos.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes para a classe principal OrquestradorApplication.
 * 
 * Valida:
 * - Contexto Spring Boot inicializa corretamente
 * - Scheduling está habilitado
 * - Component scan está configurado corretamente
 */
@SpringBootTest
@ActiveProfiles("test")
class OrquestradorApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void schedulingIsEnabled() {
        // Verifica se a anotação @EnableScheduling está presente
        EnableScheduling enableScheduling = OrquestradorApplication.class
            .getAnnotation(EnableScheduling.class);
        
        assertThat(enableScheduling).isNotNull();
    }

    @Test
    void applicationHasSpringBootApplicationAnnotation() {
        assertThat(OrquestradorApplication.class.isAnnotationPresent(
            org.springframework.boot.autoconfigure.SpringBootApplication.class))
            .isTrue();
    }

    @Test
    void componentScanIncludesOrchestratorPackage() {
        var annotation = OrquestradorApplication.class
            .getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
        
        assertThat(annotation.scanBasePackages())
            .contains("com.controle.arquivos.orchestrator");
    }

    @Test
    void componentScanIncludesCommonPackage() {
        var annotation = OrquestradorApplication.class
            .getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
        
        assertThat(annotation.scanBasePackages())
            .contains("com.controle.arquivos.common");
    }
}
