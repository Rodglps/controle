package com.controle.arquivos.processor.logging;

import com.controle.arquivos.common.logging.LoggingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes para validar configuração de logging estruturado do Processador.
 * 
 * Valida: Requisitos 20.1, 20.2, 20.3, 20.4, 20.5
 */
@SpringBootTest
@ActiveProfiles("local")
class StructuredLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingTest.class);

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void deveIncluirCorrelationIdNoMDC() {
        // Given
        String correlationId = "test-correlation-123";
        
        // When
        LoggingUtils.setCorrelationId(correlationId);
        
        // Then
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
        
        // Log para verificação manual
        log.info("Teste de correlationId no MDC");
    }

    @Test
    void deveIncluirFileOriginIdNoMDC() {
        // Given
        Long fileOriginId = 12345L;
        
        // When
        LoggingUtils.setFileOriginId(fileOriginId);
        
        // Then
        assertThat(MDC.get("fileOriginId")).isEqualTo("12345");
        
        // Log para verificação manual
        log.info("Teste de fileOriginId no MDC");
    }

    @Test
    void deveIncluirFileNameNoMDC() {
        // Given
        String fileName = "CIELO_20240115.txt";
        
        // When
        LoggingUtils.setFileName(fileName);
        
        // Then
        assertThat(MDC.get("fileName")).isEqualTo(fileName);
        
        // Log para verificação manual
        log.info("Teste de fileName no MDC");
    }

    @Test
    void deveIncluirClientIdNoMDC() {
        // Given
        Long clientId = 999L;
        
        // When
        LoggingUtils.setClientId(clientId);
        
        // Then
        assertThat(MDC.get("clientId")).isEqualTo("999");
        
        // Log para verificação manual
        log.info("Teste de clientId no MDC");
    }

    @Test
    void deveIncluirLayoutIdNoMDC() {
        // Given
        Long layoutId = 777L;
        
        // When
        LoggingUtils.setLayoutId(layoutId);
        
        // Then
        assertThat(MDC.get("layoutId")).isEqualTo("777");
        
        // Log para verificação manual
        log.info("Teste de layoutId no MDC");
    }

    @Test
    void deveIncluirStepNoMDC() {
        // Given
        String step = "PROCESSING";
        
        // When
        LoggingUtils.setStep(step);
        
        // Then
        assertThat(MDC.get("step")).isEqualTo(step);
        
        // Log para verificação manual
        log.info("Teste de step no MDC");
    }

    @Test
    void deveIncluirAcquirerIdNoMDC() {
        // Given
        Long acquirerId = 555L;
        
        // When
        LoggingUtils.setAcquirerId(acquirerId);
        
        // Then
        assertThat(MDC.get("acquirerId")).isEqualTo("555");
        
        // Log para verificação manual
        log.info("Teste de acquirerId no MDC");
    }

    @Test
    void deveIncluirTodosOsCamposNoMDC() {
        // Given
        String correlationId = "test-correlation-456";
        Long fileOriginId = 11111L;
        String fileName = "REDE_20240115.csv";
        Long clientId = 888L;
        Long layoutId = 666L;
        String step = "STAGING";
        Long acquirerId = 444L;
        
        // When
        LoggingUtils.setCorrelationId(correlationId);
        LoggingUtils.setFileOriginId(fileOriginId);
        LoggingUtils.setFileName(fileName);
        LoggingUtils.setClientId(clientId);
        LoggingUtils.setLayoutId(layoutId);
        LoggingUtils.setStep(step);
        LoggingUtils.setAcquirerId(acquirerId);
        
        // Then
        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
        assertThat(MDC.get("fileOriginId")).isEqualTo("11111");
        assertThat(MDC.get("fileName")).isEqualTo(fileName);
        assertThat(MDC.get("clientId")).isEqualTo("888");
        assertThat(MDC.get("layoutId")).isEqualTo("666");
        assertThat(MDC.get("step")).isEqualTo(step);
        assertThat(MDC.get("acquirerId")).isEqualTo("444");
        
        // Log para verificação manual - deve incluir todos os campos no JSON
        log.info("Teste com todos os campos do MDC preenchidos");
    }

    @Test
    void deveRegistrarLogDeNivelInfo() {
        // Given
        LoggingUtils.setCorrelationId("info-test-123");
        
        // When/Then - Log de nível INFO para operação bem-sucedida
        log.info("Operação bem-sucedida: arquivo processado com sucesso");
    }

    @Test
    void deveRegistrarLogDeNivelError() {
        // Given
        LoggingUtils.setCorrelationId("error-test-456");
        Exception exception = new RuntimeException("Erro simulado para teste");
        
        // When/Then - Log de nível ERROR para falha
        log.error("Falha ao processar arquivo", exception);
    }

    @Test
    void deveRegistrarLogDeNivelWarn() {
        // Given
        LoggingUtils.setCorrelationId("warn-test-789");
        
        // When/Then - Log de nível WARN para situação anômala
        log.warn("Situação anômala: arquivo com tamanho inesperado, mas processamento continua");
    }

    @Test
    void deveLimparContextoDoMDC() {
        // Given
        LoggingUtils.setCorrelationId("clear-test-123");
        LoggingUtils.setFileOriginId(99999L);
        LoggingUtils.setFileName("test-file.txt");
        
        assertThat(MDC.get("correlationId")).isNotNull();
        assertThat(MDC.get("fileOriginId")).isNotNull();
        assertThat(MDC.get("fileName")).isNotNull();
        
        // When
        LoggingUtils.clearAll();
        
        // Then
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("fileOriginId")).isNull();
        assertThat(MDC.get("fileName")).isNull();
    }

    @Test
    void deveLimparApenasContextoDeArquivo() {
        // Given
        LoggingUtils.setCorrelationId("partial-clear-test-123");
        LoggingUtils.setFileOriginId(88888L);
        LoggingUtils.setFileName("test-file-2.txt");
        LoggingUtils.setClientId(777L);
        
        assertThat(MDC.get("correlationId")).isNotNull();
        assertThat(MDC.get("fileOriginId")).isNotNull();
        assertThat(MDC.get("fileName")).isNotNull();
        assertThat(MDC.get("clientId")).isNotNull();
        
        // When
        LoggingUtils.clearFileContext();
        
        // Then - correlationId deve permanecer, mas campos de arquivo devem ser removidos
        assertThat(MDC.get("correlationId")).isNotNull();
        assertThat(MDC.get("fileOriginId")).isNull();
        assertThat(MDC.get("fileName")).isNull();
        assertThat(MDC.get("clientId")).isNull();
    }
}
