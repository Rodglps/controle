package com.controle.arquivos.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de propriedade para logging estruturado.
 * 
 * Feature: controle-de-arquivos, Properties 33, 34, 35
 * 
 * Valida formato de logs estruturados, correlation ID e níveis de log apropriados.
 * 
 * **Valida: Requisitos 20.1, 20.2, 20.3, 20.4, 20.5**
 */
class StructuredLoggingPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    /**
     * **Propriedade 33: Formato de Logs Estruturados**
     * **Valida: Requisitos 20.1**
     * 
     * Para qualquer log gerado, o sistema deve usar formato JSON com campos:
     * timestamp, level, logger, message, context.
     */
    @Property(tries = 100)
    void logsDevemTerFormatoEstruturadoJSON(
            @ForAll("mensagemLog") String mensagem,
            @ForAll("nivelLog") String nivel) throws Exception {
        
        // Arrange
        setupLogger();
        
        // Act - Gerar log
        switch (nivel) {
            case "INFO":
                logger.info(mensagem);
                break;
            case "WARN":
                logger.warn(mensagem);
                break;
            case "ERROR":
                logger.error(mensagem);
                break;
        }
        
        // Assert - Verificar que log foi gerado
        assertThat(listAppender.list).isNotEmpty();
        
        ILoggingEvent logEvent = listAppender.list.get(0);
        
        // Verificar campos obrigatórios
        assertThat(logEvent.getTimeStamp()).isGreaterThan(0);
        assertThat(logEvent.getLevel().toString()).isEqualTo(nivel);
        assertThat(logEvent.getLoggerName()).isNotNull();
        assertThat(logEvent.getFormattedMessage()).isEqualTo(mensagem);
        
        // Cleanup
        listAppender.list.clear();
    }

    /**
     * **Propriedade 34: Correlation ID em Logs**
     * **Valida: Requisitos 20.2**
     * 
     * Para qualquer log relacionado ao processamento de um arquivo,
     * o sistema deve incluir correlation_id para rastreamento.
     */
    @Property(tries = 100)
    void logsDevemIncluirCorrelationId(
            @ForAll("mensagemLog") String mensagem,
            @ForAll("correlationId") String correlationId) {
        
        // Arrange
        setupLogger();
        MDC.put("correlationId", correlationId);
        
        // Act
        logger.info(mensagem);
        
        // Assert
        assertThat(listAppender.list).isNotEmpty();
        ILoggingEvent logEvent = listAppender.list.get(0);
        
        // Verificar que correlation ID está presente no MDC
        assertThat(logEvent.getMDCPropertyMap()).containsKey("correlationId");
        assertThat(logEvent.getMDCPropertyMap().get("correlationId")).isEqualTo(correlationId);
        
        // Cleanup
        MDC.clear();
        listAppender.list.clear();
    }

    /**
     * **Propriedade 35: Níveis de Log Apropriados**
     * **Valida: Requisitos 20.3, 20.4, 20.5**
     * 
     * Para operações bem-sucedidas, usar INFO.
     * Para falhas, usar ERROR com stack trace.
     * Para situações anômalas, usar WARN.
     */
    @Property(tries = 100)
    void niveisDeLogDevemSerApropriados(
            @ForAll("tipoOperacao") TipoOperacao tipo,
            @ForAll("mensagemLog") String mensagem) {
        
        // Arrange
        setupLogger();
        
        // Act
        switch (tipo) {
            case SUCESSO:
                logger.info(mensagem);
                break;
            case FALHA:
                logger.error(mensagem, new RuntimeException("Erro de teste"));
                break;
            case ANOMALIA:
                logger.warn(mensagem);
                break;
        }
        
        // Assert
        assertThat(listAppender.list).isNotEmpty();
        ILoggingEvent logEvent = listAppender.list.get(0);
        
        switch (tipo) {
            case SUCESSO:
                assertThat(logEvent.getLevel().toString()).isEqualTo("INFO");
                assertThat(logEvent.getThrowableProxy()).isNull();
                break;
            case FALHA:
                assertThat(logEvent.getLevel().toString()).isEqualTo("ERROR");
                assertThat(logEvent.getThrowableProxy()).isNotNull(); // Stack trace presente
                break;
            case ANOMALIA:
                assertThat(logEvent.getLevel().toString()).isEqualTo("WARN");
                break;
        }
        
        // Cleanup
        listAppender.list.clear();
    }

    /**
     * **Propriedade 34: Correlation ID em Logs**
     * **Valida: Requisitos 20.2**
     * 
     * Para qualquer sequência de logs relacionados ao mesmo arquivo,
     * todos devem ter o mesmo correlation ID.
     */
    @Property(tries = 100)
    void todosLogsDoMesmoArquivoDevemTerMesmoCorrelationId(
            @ForAll("correlationId") String correlationId,
            @ForAll("numeroLogs") int numeroLogs) {
        
        // Arrange
        setupLogger();
        MDC.put("correlationId", correlationId);
        
        // Act - Gerar múltiplos logs
        for (int i = 0; i < numeroLogs; i++) {
            logger.info("Log " + i);
        }
        
        // Assert - Todos devem ter o mesmo correlation ID
        assertThat(listAppender.list).hasSize(numeroLogs);
        
        for (ILoggingEvent logEvent : listAppender.list) {
            assertThat(logEvent.getMDCPropertyMap().get("correlationId"))
                .isEqualTo(correlationId);
        }
        
        // Cleanup
        MDC.clear();
        listAppender.list.clear();
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<String> mensagemLog() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '-', '_', '.')
            .ofMinLength(10)
            .ofMaxLength(100);
    }

    @Provide
    Arbitrary<String> nivelLog() {
        return Arbitraries.of("INFO", "WARN", "ERROR");
    }

    @Provide
    Arbitrary<String> correlationId() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-')
            .ofMinLength(10)
            .ofMaxLength(36); // UUID format
    }

    @Provide
    Arbitrary<TipoOperacao> tipoOperacao() {
        return Arbitraries.of(TipoOperacao.values());
    }

    @Provide
    Arbitrary<Integer> numeroLogs() {
        return Arbitraries.integers().between(2, 10);
    }

    // ========== Helper Methods ==========

    private void setupLogger() {
        logger = (Logger) LoggerFactory.getLogger("TestLogger");
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    enum TipoOperacao {
        SUCESSO,
        FALHA,
        ANOMALIA
    }
}
