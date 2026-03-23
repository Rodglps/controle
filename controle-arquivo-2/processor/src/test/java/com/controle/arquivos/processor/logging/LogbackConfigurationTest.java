package com.controle.arquivos.processor.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes para validar que a configuração do Logback foi carregada corretamente.
 * 
 * Valida: Requisitos 20.1, 20.2
 */
@SpringBootTest
@ActiveProfiles("local")
class LogbackConfigurationTest {

    @Test
    void deveCarregarConfiguracaoLogbackCorretamente() {
        // Given
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        
        // When
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
        
        // Then - Deve ter pelo menos um appender configurado
        assertThat(appenderIterator.hasNext()).isTrue();
        
        // Verificar que existe um appender (CONSOLE para perfil local)
        boolean hasConsoleAppender = false;
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if ("CONSOLE".equals(appender.getName())) {
                hasConsoleAppender = true;
                break;
            }
        }
        
        assertThat(hasConsoleAppender)
            .as("Deve ter appender CONSOLE configurado para perfil local")
            .isTrue();
    }

    @Test
    void deveConfigurarLoggerDaAplicacao() {
        // Given
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // When
        ch.qos.logback.classic.Logger appLogger = loggerContext.getLogger("com.controle.arquivos");
        
        // Then - Logger da aplicação deve estar configurado
        assertThat(appLogger).isNotNull();
        assertThat(appLogger.getLevel()).isNotNull();
    }
}
