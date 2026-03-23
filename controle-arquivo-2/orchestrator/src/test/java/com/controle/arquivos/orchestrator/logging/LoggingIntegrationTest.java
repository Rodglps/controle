package com.controle.arquivos.orchestrator.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.controle.arquivos.common.logging.LoggingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração para validar configuração de logging estruturado.
 * 
 * Verifica que:
 * - Logs são gerados com níveis apropriados (INFO, WARN, ERROR)
 * - MDC correlationId é incluído nos logs
 * - Contexto de arquivo é incluído nos logs
 * 
 * **Valida: Requisitos 20.1, 20.2, 20.3, 20.4, 20.5**
 */
class LoggingIntegrationTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LoggingIntegrationTest.class);
        
        // Criar appender para capturar logs
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        MDC.clear();
    }

    @Test
    void deveGerarLogInfoParaOperacaoBemSucedida() {
        // Given
        String correlationId = "test-correlation-123";
        LoggingUtils.setCorrelationId(correlationId);

        // When
        logger.info("Operação concluída com sucesso");

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        
        assertThat(event.getLevel().toString()).isEqualTo("INFO");
        assertThat(event.getMessage()).isEqualTo("Operação concluída com sucesso");
        assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", correlationId);
    }

    @Test
    void deveGerarLogWarnParaSituacaoAnomala() {
        // Given
        String correlationId = "test-correlation-456";
        LoggingUtils.setCorrelationId(correlationId);

        // When
        logger.warn("Situação anômala detectada, mas processamento continua");

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        
        assertThat(event.getLevel().toString()).isEqualTo("WARN");
        assertThat(event.getMessage()).contains("anômala");
        assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", correlationId);
    }

    @Test
    void deveGerarLogErrorParaFalha() {
        // Given
        String correlationId = "test-correlation-789";
        LoggingUtils.setCorrelationId(correlationId);
        Exception exception = new RuntimeException("Erro de teste");

        // When
        logger.error("Falha durante processamento", exception);

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getMessage()).isEqualTo("Falha durante processamento");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("Erro de teste");
        assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", correlationId);
    }

    @Test
    void deveIncluirContextoDeArquivoNosLogs() {
        // Given
        String correlationId = "test-correlation-abc";
        Long fileOriginId = 12345L;
        String fileName = "test-file.txt";
        Long clientId = 999L;
        Long layoutId = 777L;
        String step = "PROCESSING";
        Long acquirerId = 555L;

        LoggingUtils.setCorrelationId(correlationId);
        LoggingUtils.setFileOriginId(fileOriginId);
        LoggingUtils.setFileName(fileName);
        LoggingUtils.setClientId(clientId);
        LoggingUtils.setLayoutId(layoutId);
        LoggingUtils.setStep(step);
        LoggingUtils.setAcquirerId(acquirerId);

        // When
        logger.info("Processando arquivo");

        // Then
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.get(0);
        
        assertThat(event.getMDCPropertyMap())
                .containsEntry("correlationId", correlationId)
                .containsEntry("fileOriginId", fileOriginId.toString())
                .containsEntry("fileName", fileName)
                .containsEntry("clientId", clientId.toString())
                .containsEntry("layoutId", layoutId.toString())
                .containsEntry("step", step)
                .containsEntry("acquirerId", acquirerId.toString());
    }

    @Test
    void deveGerarLogsComNiveisApropriados() {
        // Given
        LoggingUtils.setCorrelationId("test-id");

        // When - Simular diferentes cenários
        logger.info("Ciclo de coleta iniciado");
        logger.info("Arquivo registrado com sucesso");
        logger.warn("Arquivo duplicado ignorado");
        logger.error("Falha ao conectar ao SFTP", new RuntimeException("Connection timeout"));

        // Then
        assertThat(listAppender.list).hasSize(4);
        
        assertThat(listAppender.list.get(0).getLevel().toString()).isEqualTo("INFO");
        assertThat(listAppender.list.get(1).getLevel().toString()).isEqualTo("INFO");
        assertThat(listAppender.list.get(2).getLevel().toString()).isEqualTo("WARN");
        assertThat(listAppender.list.get(3).getLevel().toString()).isEqualTo("ERROR");
        assertThat(listAppender.list.get(3).getThrowableProxy()).isNotNull();
    }

    @Test
    void deveLimparContextoAposProcessamento() {
        // Given
        LoggingUtils.setCorrelationId("test-id");
        LoggingUtils.setFileOriginId(123L);
        LoggingUtils.setFileName("test.txt");

        // When
        logger.info("Processando arquivo");
        LoggingUtils.clearFileContext();
        logger.info("Contexto limpo");

        // Then
        assertThat(listAppender.list).hasSize(2);
        
        ILoggingEvent eventComContexto = listAppender.list.get(0);
        assertThat(eventComContexto.getMDCPropertyMap())
                .containsKey("correlationId")
                .containsKey("fileOriginId")
                .containsKey("fileName");
        
        ILoggingEvent eventSemContexto = listAppender.list.get(1);
        assertThat(eventSemContexto.getMDCPropertyMap())
                .containsKey("correlationId")
                .doesNotContainKey("fileOriginId")
                .doesNotContainKey("fileName");
    }
}
