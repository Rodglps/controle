package com.controle.arquivos.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para LoggingUtils.
 * 
 * **Valida: Requisitos 20.2**
 */
class LoggingUtilsTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void deveDefinirCorrelationIdQuandoFornecido() {
        // Given
        String expectedId = "test-correlation-id";

        // When
        String actualId = LoggingUtils.setCorrelationId(expectedId);

        // Then
        assertThat(actualId).isEqualTo(expectedId);
        assertThat(MDC.get("correlationId")).isEqualTo(expectedId);
    }

    @Test
    void deveGerarCorrelationIdQuandoNaoFornecido() {
        // When
        String actualId = LoggingUtils.setCorrelationId(null);

        // Then
        assertThat(actualId).isNotNull();
        assertThat(actualId).isNotEmpty();
        assertThat(MDC.get("correlationId")).isEqualTo(actualId);
    }

    @Test
    void deveGerarCorrelationIdQuandoVazio() {
        // When
        String actualId = LoggingUtils.setCorrelationId("");

        // Then
        assertThat(actualId).isNotNull();
        assertThat(actualId).isNotEmpty();
        assertThat(MDC.get("correlationId")).isEqualTo(actualId);
    }

    @Test
    void deveObterCorrelationIdDoMDC() {
        // Given
        String expectedId = "test-id-123";
        MDC.put("correlationId", expectedId);

        // When
        String actualId = LoggingUtils.getCorrelationId();

        // Then
        assertThat(actualId).isEqualTo(expectedId);
    }

    @Test
    void deveRetornarNullQuandoCorrelationIdNaoDefinido() {
        // When
        String actualId = LoggingUtils.getCorrelationId();

        // Then
        assertThat(actualId).isNull();
    }

    @Test
    void deveRemoverCorrelationId() {
        // Given
        MDC.put("correlationId", "test-id");

        // When
        LoggingUtils.clearCorrelationId();

        // Then
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void deveDefinirFileOriginId() {
        // Given
        Long fileOriginId = 12345L;

        // When
        LoggingUtils.setFileOriginId(fileOriginId);

        // Then
        assertThat(MDC.get("fileOriginId")).isEqualTo("12345");
    }

    @Test
    void naoDeveDefinirFileOriginIdQuandoNull() {
        // When
        LoggingUtils.setFileOriginId(null);

        // Then
        assertThat(MDC.get("fileOriginId")).isNull();
    }

    @Test
    void deveDefinirFileName() {
        // Given
        String fileName = "test-file.txt";

        // When
        LoggingUtils.setFileName(fileName);

        // Then
        assertThat(MDC.get("fileName")).isEqualTo(fileName);
    }

    @Test
    void naoDeveDefinirFileNameQuandoNullOuVazio() {
        // When
        LoggingUtils.setFileName(null);
        assertThat(MDC.get("fileName")).isNull();

        LoggingUtils.setFileName("");
        assertThat(MDC.get("fileName")).isNull();

        LoggingUtils.setFileName("   ");
        assertThat(MDC.get("fileName")).isNull();
    }

    @Test
    void deveDefinirClientId() {
        // Given
        Long clientId = 999L;

        // When
        LoggingUtils.setClientId(clientId);

        // Then
        assertThat(MDC.get("clientId")).isEqualTo("999");
    }

    @Test
    void deveDefinirLayoutId() {
        // Given
        Long layoutId = 777L;

        // When
        LoggingUtils.setLayoutId(layoutId);

        // Then
        assertThat(MDC.get("layoutId")).isEqualTo("777");
    }

    @Test
    void deveDefinirStep() {
        // Given
        String step = "PROCESSING";

        // When
        LoggingUtils.setStep(step);

        // Then
        assertThat(MDC.get("step")).isEqualTo(step);
    }

    @Test
    void deveDefinirAcquirerId() {
        // Given
        Long acquirerId = 555L;

        // When
        LoggingUtils.setAcquirerId(acquirerId);

        // Then
        assertThat(MDC.get("acquirerId")).isEqualTo("555");
    }

    @Test
    void deveLimparTodoOContexto() {
        // Given
        LoggingUtils.setCorrelationId("test-id");
        LoggingUtils.setFileOriginId(123L);
        LoggingUtils.setFileName("test.txt");
        LoggingUtils.setClientId(456L);
        LoggingUtils.setLayoutId(789L);
        LoggingUtils.setStep("PROCESSING");
        LoggingUtils.setAcquirerId(111L);

        // When
        LoggingUtils.clearAll();

        // Then
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void deveLimparApenasContextoDeArquivo() {
        // Given
        LoggingUtils.setCorrelationId("test-id");
        LoggingUtils.setFileOriginId(123L);
        LoggingUtils.setFileName("test.txt");
        LoggingUtils.setClientId(456L);
        LoggingUtils.setLayoutId(789L);
        LoggingUtils.setStep("PROCESSING");
        LoggingUtils.setAcquirerId(111L);

        // When
        LoggingUtils.clearFileContext();

        // Then
        assertThat(MDC.get("correlationId")).isEqualTo("test-id");
        assertThat(MDC.get("fileOriginId")).isNull();
        assertThat(MDC.get("fileName")).isNull();
        assertThat(MDC.get("clientId")).isNull();
        assertThat(MDC.get("layoutId")).isNull();
        assertThat(MDC.get("step")).isNull();
        assertThat(MDC.get("acquirerId")).isNull();
    }
}
