package com.controle.arquivos.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para MdcTaskDecorator.
 * 
 * **Valida: Requisitos 20.2**
 */
class MdcTaskDecoratorTest {

    private MdcTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new MdcTaskDecorator();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void devePropagarContextoMDCParaThreadFilha() throws InterruptedException {
        // Given
        String expectedCorrelationId = "test-correlation-id";
        String expectedFileName = "test-file.txt";
        
        MDC.put("correlationId", expectedCorrelationId);
        MDC.put("fileName", expectedFileName);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();
        AtomicReference<String> capturedFileName = new AtomicReference<>();

        Runnable task = () -> {
            capturedCorrelationId.set(MDC.get("correlationId"));
            capturedFileName.set(MDC.get("fileName"));
            latch.countDown();
        };

        // When
        Runnable decoratedTask = decorator.decorate(task);
        new Thread(decoratedTask).start();

        // Then
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedCorrelationId.get()).isEqualTo(expectedCorrelationId);
        assertThat(capturedFileName.get()).isEqualTo(expectedFileName);
    }

    @Test
    void deveLimparMDCNaThreadFilhaAposExecucao() throws InterruptedException {
        // Given
        MDC.put("correlationId", "test-id");

        CountDownLatch executionLatch = new CountDownLatch(1);
        CountDownLatch verificationLatch = new CountDownLatch(1);
        AtomicReference<String> mdcAfterExecution = new AtomicReference<>();

        Runnable task = () -> {
            executionLatch.countDown();
        };

        // When
        Runnable decoratedTask = decorator.decorate(task);
        Thread thread = new Thread(() -> {
            decoratedTask.run();
            // Verificar MDC após a execução da task decorada
            mdcAfterExecution.set(MDC.get("correlationId"));
            verificationLatch.countDown();
        });
        thread.start();

        // Then
        assertThat(executionLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(verificationLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(mdcAfterExecution.get()).isNull();
    }

    @Test
    void deveManterMDCVazioQuandoThreadPrincipalNaoTemContexto() throws InterruptedException {
        // Given - MDC vazio na thread principal
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        Runnable task = () -> {
            capturedCorrelationId.set(MDC.get("correlationId"));
            latch.countDown();
        };

        // When
        Runnable decoratedTask = decorator.decorate(task);
        new Thread(decoratedTask).start();

        // Then
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedCorrelationId.get()).isNull();
    }

    @Test
    void deveLimparMDCMesmoQuandoTaskLancaExcecao() throws InterruptedException {
        // Given
        MDC.put("correlationId", "test-id");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> mdcAfterException = new AtomicReference<>();

        Runnable task = () -> {
            throw new RuntimeException("Test exception");
        };

        // When
        Runnable decoratedTask = decorator.decorate(task);
        Thread thread = new Thread(() -> {
            try {
                decoratedTask.run();
            } catch (RuntimeException e) {
                // Esperado
            }
            // Verificar MDC após exceção
            mdcAfterException.set(MDC.get("correlationId"));
            latch.countDown();
        });
        thread.start();

        // Then
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(mdcAfterException.get()).isNull();
    }

    @Test
    void naoDeveAlterarMDCDaThreadPrincipal() throws InterruptedException {
        // Given
        String originalCorrelationId = "original-id";
        MDC.put("correlationId", originalCorrelationId);

        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = () -> {
            // Thread filha tenta modificar MDC
            MDC.put("correlationId", "modified-id");
            latch.countDown();
        };

        // When
        Runnable decoratedTask = decorator.decorate(task);
        new Thread(decoratedTask).start();

        // Then
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        // MDC da thread principal não deve ser afetado
        assertThat(MDC.get("correlationId")).isEqualTo(originalCorrelationId);
    }
}
