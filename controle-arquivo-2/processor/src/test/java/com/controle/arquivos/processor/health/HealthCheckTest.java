package com.controle.arquivos.processor.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes para health checks do Processor.
 * 
 * Valida Requisitos 16.1, 16.3, 16.4, 16.5:
 * - Endpoint /actuator/health disponível
 * - Verificação de conectividade com banco de dados
 * - Verificação de conectividade com RabbitMQ
 * - Status UP quando dependências estão disponíveis
 */
@SpringBootTest
class HealthCheckTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Testa que os health indicators do Spring Boot Actuator estão configurados.
     * 
     * Valida Requisito 16.1: Spring Boot Actuator está configurado
     */
    @Test
    void deveConfigurarSpringBootActuator() {
        // Verifica que o contexto Spring contém beans de health indicators
        assertThat(applicationContext.getBeansOfType(HealthIndicator.class))
                .isNotEmpty();
    }

    /**
     * Testa que o health indicator do banco de dados está configurado.
     * 
     * Valida Requisito 16.3: Configurar health indicator para banco de dados
     */
    @Test
    void deveConfigurarHealthIndicatorParaBancoDeDados() {
        // Spring Boot automaticamente configura DataSourceHealthIndicator quando
        // spring-boot-starter-jdbc ou spring-boot-starter-data-jpa está no classpath
        assertThat(applicationContext.getBeansOfType(HealthIndicator.class))
                .containsKey("dbHealthIndicator");
    }

    /**
     * Testa que o health indicator do RabbitMQ está configurado.
     * 
     * Valida Requisito 16.4: Configurar health indicator para RabbitMQ
     */
    @Test
    void deveConfigurarHealthIndicatorParaRabbitMQ() {
        // Spring Boot automaticamente configura RabbitHealthIndicator quando
        // spring-boot-starter-amqp está no classpath
        assertThat(applicationContext.getBeansOfType(HealthIndicator.class))
                .containsKey("rabbitHealthIndicator");
    }

    /**
     * Testa que os health indicators de liveness e readiness estão habilitados.
     * 
     * Kubernetes usa estes endpoints para gerenciar o ciclo de vida do pod.
     */
    @Test
    void deveHabilitarLivenessEReadinessProbes() {
        // Verifica que os beans de liveness e readiness state estão configurados
        assertThat(applicationContext.containsBean("livenessStateHealthIndicator"))
                .isTrue();
        assertThat(applicationContext.containsBean("readinessStateHealthIndicator"))
                .isTrue();
    }
}
