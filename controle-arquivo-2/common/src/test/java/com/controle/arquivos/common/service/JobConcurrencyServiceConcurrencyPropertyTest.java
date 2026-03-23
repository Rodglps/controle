package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.JobConcurrencyControl;
import com.controle.arquivos.common.repository.JobConcurrencyControlRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para controle de concorrência.
 * 
 * Feature: controle-de-arquivos, Property 10: Controle de Concorrência
 * 
 * Para qualquer ciclo de coleta iniciado, o Orquestrador deve criar registro com status RUNNING
 * em job_concurrency_control, atualizar para COMPLETED ao finalizar com sucesso, ou atualizar
 * para PENDING em caso de falha.
 * 
 * **Valida: Requisitos 5.3, 5.4, 5.5**
 */
class JobConcurrencyServiceConcurrencyPropertyTest {

    /**
     * Propriedade 10: Controle de Concorrência
     * 
     * Para qualquer ciclo de coleta iniciado, o Orquestrador deve criar registro com status RUNNING
     * em job_concurrency_control, atualizar para COMPLETED ao finalizar com sucesso, ou atualizar
     * para PENDING em caso de falha.
     * 
     * Este teste verifica que:
     * 1. Ao iniciar execução, status é definido como RUNNING
     * 2. Ao finalizar com sucesso, status é atualizado para COMPLETED
     * 3. Ao finalizar com falha, status é atualizado para PENDING
     * 4. Múltiplas execuções sequenciais mantêm consistência de status
     * 
     * **Valida: Requisitos 5.3, 5.4, 5.5**
     */
    @Property(tries = 100)
    void propriedade10_controleDeConco rrencia(
        @ForAll("jobName") String jobName,
        @ForAll("execucoes") List<ExecucaoSimulada> execucoes
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        AtomicLong idCounter = new AtomicLong(1L);
        List<JobConcurrencyControl> registrosSalvos = new ArrayList<>();

        // Configurar mock para save - simula criação de registros
        when(repository.save(any(JobConcurrencyControl.class))).thenAnswer(invocation -> {
            JobConcurrencyControl control = invocation.getArgument(0);
            if (control.getId() == null) {
                control.setId(idCounter.getAndIncrement());
                control.setCreatedAt(Instant.now());
            }
            control.setUpdatedAt(Instant.now());
            registrosSalvos.add(control);
            return control;
        });

        // Configurar mock para findByJobNameAndStatusAndActive
        when(repository.findByJobNameAndStatusAndActive(eq(jobName), eq("RUNNING"), eq(true)))
            .thenAnswer(invocation -> {
                return registrosSalvos.stream()
                    .filter(c -> c.getJobName().equals(jobName))
                    .filter(c -> "RUNNING".equals(c.getStatus()))
                    .filter(c -> Boolean.TRUE.equals(c.getActive()))
                    .findFirst();
            });

        // Configurar mock para findByJobNameAndActive
        when(repository.findByJobNameAndActive(eq(jobName), eq(true)))
            .thenAnswer(invocation -> {
                return registrosSalvos.stream()
                    .filter(c -> c.getJobName().equals(jobName))
                    .filter(c -> Boolean.TRUE.equals(c.getActive()))
                    .findFirst();
            });

        // Act & Assert - Simular múltiplas execuções
        for (ExecucaoSimulada execucao : execucoes) {
            // Requisito 5.3: Criar registro com status RUNNING ao iniciar
            Long controlId = service.iniciarExecucao(jobName);
            assertNotNull(controlId, "ID do controle deve ser retornado ao iniciar execução");

            // Verificar que registro foi criado com status RUNNING
            Optional<JobConcurrencyControl> runningControl = registrosSalvos.stream()
                .filter(c -> c.getId().equals(controlId))
                .findFirst();
            
            assertTrue(runningControl.isPresent(), "Registro deve existir após iniciar execução");
            assertEquals("RUNNING", runningControl.get().getStatus(),
                "Status deve ser RUNNING ao iniciar execução");
            assertEquals(jobName, runningControl.get().getJobName(),
                "Nome do job deve ser preservado");
            assertTrue(runningControl.get().getActive(),
                "Registro deve estar ativo");

            // Verificar que execução ativa é detectada
            boolean hasActiveExecution = service.verificarExecucaoAtiva(jobName);
            assertTrue(hasActiveExecution,
                "Deve detectar execução ativa após iniciar");

            // Finalizar execução com sucesso ou falha
            service.finalizarExecucao(jobName, execucao.isSucesso());

            // Verificar transição de status
            Optional<JobConcurrencyControl> finalizadoControl = registrosSalvos.stream()
                .filter(c -> c.getId().equals(controlId))
                .findFirst();

            assertTrue(finalizadoControl.isPresent(), "Registro deve existir após finalizar");
            
            if (execucao.isSucesso()) {
                // Requisito 5.4: Atualizar para COMPLETED ao finalizar com sucesso
                assertEquals("COMPLETED", finalizadoControl.get().getStatus(),
                    "Status deve ser COMPLETED ao finalizar com sucesso");
            } else {
                // Requisito 5.5: Atualizar para PENDING em caso de falha
                assertEquals("PENDING", finalizadoControl.get().getStatus(),
                    "Status deve ser PENDING ao finalizar com falha");
            }

            // Verificar que não há mais execução ativa após finalizar
            boolean hasActiveAfterFinish = service.verificarExecucaoAtiva(jobName);
            assertFalse(hasActiveAfterFinish,
                "Não deve detectar execução ativa após finalizar");
        }

        // Verificar que todas as execuções foram registradas
        long totalExecucoes = registrosSalvos.stream()
            .filter(c -> c.getJobName().equals(jobName))
            .count();
        
        assertEquals(execucoes.size(), totalExecucoes,
            "Deve ter um registro para cada execução simulada");
    }

    /**
     * Propriedade: Status RUNNING deve ser criado ao iniciar execução.
     */
    @Property(tries = 100)
    void statusRunningDeveCriadoAoIniciar(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        when(repository.save(any(JobConcurrencyControl.class))).thenAnswer(invocation -> {
            JobConcurrencyControl control = invocation.getArgument(0);
            control.setId(1L);
            control.setCreatedAt(Instant.now());
            control.setUpdatedAt(Instant.now());
            return control;
        });

        // Act
        Long controlId = service.iniciarExecucao(jobName);

        // Assert
        assertNotNull(controlId, "Deve retornar ID do controle");

        ArgumentCaptor<JobConcurrencyControl> captor = ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl saved = captor.getValue();
        assertEquals(jobName, saved.getJobName(), "Nome do job deve ser preservado");
        assertEquals("RUNNING", saved.getStatus(), "Status deve ser RUNNING ao iniciar");
        assertTrue(saved.getActive(), "Registro deve estar ativo");
    }

    /**
     * Propriedade: Status deve ser atualizado para COMPLETED ao finalizar com sucesso.
     */
    @Property(tries = 100)
    void statusDeveSerCompletedAoFinalizarComSucesso(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
            .id(1L)
            .jobName(jobName)
            .status("RUNNING")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));

        when(repository.save(any(JobConcurrencyControl.class))).thenAnswer(invocation -> {
            JobConcurrencyControl control = invocation.getArgument(0);
            control.setUpdatedAt(Instant.now());
            return control;
        });

        // Act
        service.finalizarExecucao(jobName, true);

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl updated = captor.getValue();
        assertEquals("COMPLETED", updated.getStatus(),
            "Status deve ser COMPLETED ao finalizar com sucesso");
    }

    /**
     * Propriedade: Status deve ser atualizado para PENDING ao finalizar com falha.
     */
    @Property(tries = 100)
    void statusDeveSerPendingAoFinalizarComFalha(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
            .id(1L)
            .jobName(jobName)
            .status("RUNNING")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));

        when(repository.save(any(JobConcurrencyControl.class))).thenAnswer(invocation -> {
            JobConcurrencyControl control = invocation.getArgument(0);
            control.setUpdatedAt(Instant.now());
            return control;
        });

        // Act
        service.finalizarExecucao(jobName, false);

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl updated = captor.getValue();
        assertEquals("PENDING", updated.getStatus(),
            "Status deve ser PENDING ao finalizar com falha");
    }

    /**
     * Propriedade: Verificação de execução ativa deve retornar true quando há RUNNING.
     */
    @Property(tries = 100)
    void verificacaoDeveRetornarTrueQuandoHaRunning(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
            .id(1L)
            .jobName(jobName)
            .status("RUNNING")
            .active(true)
            .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));

        // Act
        boolean hasActive = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertTrue(hasActive, "Deve retornar true quando há execução RUNNING");
    }

    /**
     * Propriedade: Verificação de execução ativa deve retornar false quando não há RUNNING.
     */
    @Property(tries = 100)
    void verificacaoDeveRetornarFalseQuandoNaoHaRunning(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.empty());

        // Act
        boolean hasActive = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertFalse(hasActive, "Deve retornar false quando não há execução RUNNING");
    }

    /**
     * Propriedade: Múltiplas execuções sequenciais devem manter consistência.
     */
    @Property(tries = 50)
    void multiplasExecucoesSequenciaisDevemManterConsistencia(
        @ForAll("jobName") String jobName,
        @ForAll @IntRange(min = 2, max = 10) int numeroExecucoes
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        AtomicLong idCounter = new AtomicLong(1L);
        List<JobConcurrencyControl> registros = new ArrayList<>();

        when(repository.save(any(JobConcurrencyControl.class))).thenAnswer(invocation -> {
            JobConcurrencyControl control = invocation.getArgument(0);
            if (control.getId() == null) {
                control.setId(idCounter.getAndIncrement());
                control.setCreatedAt(Instant.now());
            }
            control.setUpdatedAt(Instant.now());
            registros.add(control);
            return control;
        });

        when(repository.findByJobNameAndStatusAndActive(eq(jobName), eq("RUNNING"), eq(true)))
            .thenAnswer(inv -> registros.stream()
                .filter(c -> c.getJobName().equals(jobName))
                .filter(c -> "RUNNING".equals(c.getStatus()))
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .findFirst());

        // Act - Executar múltiplos ciclos
        for (int i = 0; i < numeroExecucoes; i++) {
            Long controlId = service.iniciarExecucao(jobName);
            assertNotNull(controlId);

            boolean sucesso = (i % 2 == 0); // Alternar entre sucesso e falha
            service.finalizarExecucao(jobName, sucesso);
        }

        // Assert
        assertEquals(numeroExecucoes, registros.size(),
            "Deve ter um registro para cada execução");

        // Verificar que cada execução tem transição correta
        for (int i = 0; i < numeroExecucoes; i++) {
            JobConcurrencyControl registro = registros.get(i);
            assertEquals(jobName, registro.getJobName());
            assertTrue(registro.getActive());

            boolean esperadoSucesso = (i % 2 == 0);
            String statusEsperado = esperadoSucesso ? "COMPLETED" : "PENDING";
            assertEquals(statusEsperado, registro.getStatus(),
                "Status deve refletir resultado da execução");
        }
    }

    /**
     * Propriedade: Registro de data de execução deve atualizar lastExecution.
     */
    @Property(tries = 100)
    void registroDeDataDeveAtualizarLastExecution(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        JobConcurrencyControl control = JobConcurrencyControl.builder()
            .id(1L)
            .jobName(jobName)
            .status("RUNNING")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(repository.findByJobNameAndActive(jobName, true))
            .thenReturn(Optional.of(control));

        when(repository.save(any(JobConcurrencyControl.class))).thenAnswer(invocation -> {
            JobConcurrencyControl c = invocation.getArgument(0);
            c.setUpdatedAt(Instant.now());
            return c;
        });

        // Act
        Instant antes = Instant.now();
        service.registrarDataExecucao(jobName);
        Instant depois = Instant.now();

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl updated = captor.getValue();
        assertNotNull(updated.getLastExecution(),
            "lastExecution deve ser definido");
        assertTrue(updated.getLastExecution().isAfter(antes.minusSeconds(1)),
            "lastExecution deve ser após início do teste");
        assertTrue(updated.getLastExecution().isBefore(depois.plusSeconds(1)),
            "lastExecution deve ser antes do fim do teste");
    }

    /**
     * Propriedade: Finalizar execução sem RUNNING deve lançar exceção.
     */
    @Property(tries = 100)
    void finalizarSemRunningDeveLancarExcecao(
        @ForAll("jobName") String jobName
    ) {
        // Arrange
        JobConcurrencyControlRepository repository = mock(JobConcurrencyControlRepository.class);
        JobConcurrencyService service = new JobConcurrencyService(repository);

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            service.finalizarExecucao(jobName, true);
        }, "Deve lançar exceção ao tentar finalizar sem execução RUNNING");
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<String> jobName() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(50)
            .map(s -> "job-" + s);
    }

    @Provide
    Arbitrary<List<ExecucaoSimulada>> execucoes() {
        Arbitrary<Boolean> sucesso = Arbitraries.of(true, false);
        
        return sucesso.map(ExecucaoSimulada::new)
            .list()
            .ofMinSize(1)
            .ofMaxSize(10);
    }

    /**
     * Classe auxiliar para representar uma execução simulada.
     */
    static class ExecucaoSimulada {
        private final boolean sucesso;

        public ExecucaoSimulada(boolean sucesso) {
            this.sucesso = sucesso;
        }

        public boolean isSucesso() {
            return sucesso;
        }
    }
}
