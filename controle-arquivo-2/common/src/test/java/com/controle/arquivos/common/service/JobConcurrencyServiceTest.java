package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.JobConcurrencyControl;
import com.controle.arquivos.common.repository.JobConcurrencyControlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para JobConcurrencyService.
 * 
 * **Valida: Requisitos 5.1, 5.2, 5.3, 5.4, 5.5**
 */
@ExtendWith(MockitoExtension.class)
class JobConcurrencyServiceTest {

    @Mock
    private JobConcurrencyControlRepository repository;

    private JobConcurrencyService service;

    @BeforeEach
    void setUp() {
        service = new JobConcurrencyService(repository);
    }

    // ========== Tests for iniciarExecucao ==========

    /**
     * Testa início de execução criando registro com status RUNNING.
     * Deve criar um novo registro com os dados corretos.
     */
    @Test
    void iniciarExecucao_deveCriarRegistroComStatusRunning() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl savedControl = JobConcurrencyControl.builder()
                .id(1L)
                .jobName(jobName)
                .status("RUNNING")
                .active(true)
                .build();

        when(repository.save(any(JobConcurrencyControl.class))).thenReturn(savedControl);

        // Act
        Long result = service.iniciarExecucao(jobName);

        // Assert
        assertEquals(1L, result);
        
        ArgumentCaptor<JobConcurrencyControl> captor = 
                ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl captured = captor.getValue();
        assertEquals(jobName, captured.getJobName());
        assertEquals("RUNNING", captured.getStatus());
        assertTrue(captured.getActive());
    }

    /**
     * Testa que iniciarExecucao lança exceção quando jobName é nulo.
     */
    @Test
    void iniciarExecucao_deveLancarExcecaoQuandoJobNameNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.iniciarExecucao(null);
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).save(any());
    }

    /**
     * Testa que iniciarExecucao lança exceção quando jobName é vazio.
     */
    @Test
    void iniciarExecucao_deveLancarExcecaoQuandoJobNameVazio() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.iniciarExecucao("");
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).save(any());
    }

    /**
     * Testa que iniciarExecucao lança exceção quando jobName contém apenas espaços.
     */
    @Test
    void iniciarExecucao_deveLancarExcecaoQuandoJobNameApenasEspacos() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.iniciarExecucao("   ");
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).save(any());
    }

    // ========== Tests for verificarExecucaoAtiva ==========

    /**
     * Testa verificação quando existe execução RUNNING ativa.
     * Deve retornar true.
     */
    @Test
    void verificarExecucaoAtiva_deveRetornarTrueQuandoExisteExecucaoRunning() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
                .id(1L)
                .jobName(jobName)
                .status("RUNNING")
                .active(true)
                .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));

        // Act
        boolean result = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertTrue(result);
        verify(repository).findByJobNameAndStatusAndActive(jobName, "RUNNING", true);
    }

    /**
     * Testa verificação quando não existe execução RUNNING.
     * Deve retornar false.
     */
    @Test
    void verificarExecucaoAtiva_deveRetornarFalseQuandoNaoExisteExecucaoRunning() {
        // Arrange
        String jobName = "orquestrador-coleta";

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.empty());

        // Act
        boolean result = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertFalse(result);
        verify(repository).findByJobNameAndStatusAndActive(jobName, "RUNNING", true);
    }

    /**
     * Testa verificação quando existe execução RUNNING mas inativa.
     * Deve retornar false.
     */
    @Test
    void verificarExecucaoAtiva_deveRetornarFalseQuandoExecucaoRunningInativa() {
        // Arrange
        String jobName = "orquestrador-coleta";

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.empty());

        // Act
        boolean result = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertFalse(result);
        verify(repository).findByJobNameAndStatusAndActive(jobName, "RUNNING", true);
    }

    /**
     * Testa verificação quando não existe nenhum registro para o job.
     * Deve retornar false.
     */
    @Test
    void verificarExecucaoAtiva_deveRetornarFalseQuandoNaoExisteRegistro() {
        // Arrange
        String jobName = "orquestrador-coleta";
        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.empty());

        // Act
        boolean result = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertFalse(result);
        verify(repository).findByJobNameAndStatusAndActive(jobName, "RUNNING", true);
    }

    /**
     * Testa verificação com múltiplos registros, apenas um RUNNING.
     * Deve retornar true.
     */
    @Test
    void verificarExecucaoAtiva_deveRetornarTrueQuandoExisteUmRunningEntreMultiplos() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
                .id(2L)
                .jobName(jobName)
                .status("RUNNING")
                .active(true)
                .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));

        // Act
        boolean result = service.verificarExecucaoAtiva(jobName);

        // Assert
        assertTrue(result);
        verify(repository).findByJobNameAndStatusAndActive(jobName, "RUNNING", true);
    }

    /**
     * Testa que verificarExecucaoAtiva lança exceção quando jobName é nulo.
     */
    @Test
    void verificarExecucaoAtiva_deveLancarExcecaoQuandoJobNameNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.verificarExecucaoAtiva(null);
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).findByJobNameAndStatusAndActive(any(), any(), any());
    }

    /**
     * Testa que verificarExecucaoAtiva lança exceção quando jobName é vazio.
     */
    @Test
    void verificarExecucaoAtiva_deveLancarExcecaoQuandoJobNameVazio() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.verificarExecucaoAtiva("");
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).findByJobNameAndStatusAndActive(any(), any(), any());
    }

    // ========== Tests for finalizarExecucao ==========

    /**
     * Testa finalização com sucesso atualizando status para COMPLETED.
     * Deve atualizar o status corretamente.
     */
    @Test
    void finalizarExecucao_deveAtualizarParaCompletedQuandoSucesso() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
                .id(1L)
                .jobName(jobName)
                .status("RUNNING")
                .active(true)
                .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));
        when(repository.save(any(JobConcurrencyControl.class))).thenReturn(runningControl);

        // Act
        service.finalizarExecucao(jobName, true);

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = 
                ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl captured = captor.getValue();
        assertEquals("COMPLETED", captured.getStatus());
    }

    /**
     * Testa finalização com falha atualizando status para PENDING.
     * Deve atualizar o status corretamente.
     */
    @Test
    void finalizarExecucao_deveAtualizarParaPendingQuandoFalha() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl runningControl = JobConcurrencyControl.builder()
                .id(1L)
                .jobName(jobName)
                .status("RUNNING")
                .active(true)
                .build();

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.of(runningControl));
        when(repository.save(any(JobConcurrencyControl.class))).thenReturn(runningControl);

        // Act
        service.finalizarExecucao(jobName, false);

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = 
                ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl captured = captor.getValue();
        assertEquals("PENDING", captured.getStatus());
    }

    /**
     * Testa que finalizarExecucao lança exceção quando não existe execução RUNNING.
     */
    @Test
    void finalizarExecucao_deveLancarExcecaoQuandoNaoExisteExecucaoRunning() {
        // Arrange
        String jobName = "orquestrador-coleta";

        when(repository.findByJobNameAndStatusAndActive(jobName, "RUNNING", true))
            .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.finalizarExecucao(jobName, true);
        });

        assertTrue(exception.getMessage().contains("Nenhuma execução RUNNING encontrada"));
        verify(repository, never()).save(any());
    }

    /**
     * Testa que finalizarExecucao lança exceção quando jobName é nulo.
     */
    @Test
    void finalizarExecucao_deveLancarExcecaoQuandoJobNameNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.finalizarExecucao(null, true);
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).findByJobNameAndStatusAndActive(any(), any(), any());
    }

    /**
     * Testa que finalizarExecucao lança exceção quando jobName é vazio.
     */
    @Test
    void finalizarExecucao_deveLancarExcecaoQuandoJobNameVazio() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.finalizarExecucao("", true);
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).findByJobNameAndStatusAndActive(any(), any(), any());
    }

    // ========== Tests for registrarDataExecucao ==========

    /**
     * Testa registro de data de execução atualizando dat_last_execution.
     * Deve atualizar o timestamp corretamente.
     */
    @Test
    void registrarDataExecucao_deveAtualizarDataUltimaExecucao() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl control = JobConcurrencyControl.builder()
                .id(1L)
                .jobName(jobName)
                .status("RUNNING")
                .active(true)
                .build();

        when(repository.findByJobNameAndActive(jobName, true))
            .thenReturn(Optional.of(control));
        when(repository.save(any(JobConcurrencyControl.class))).thenReturn(control);

        Instant before = Instant.now();

        // Act
        service.registrarDataExecucao(jobName);

        Instant after = Instant.now();

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = 
                ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl captured = captor.getValue();
        assertNotNull(captured.getLastExecution());
        assertTrue(captured.getLastExecution().isAfter(before.minusSeconds(1)));
        assertTrue(captured.getLastExecution().isBefore(after.plusSeconds(1)));
    }

    /**
     * Testa que registrarDataExecucao funciona com qualquer status ativo.
     * Deve atualizar independente do status.
     */
    @Test
    void registrarDataExecucao_deveFuncionarComQualquerStatusAtivo() {
        // Arrange
        String jobName = "orquestrador-coleta";
        JobConcurrencyControl control = JobConcurrencyControl.builder()
                .id(1L)
                .jobName(jobName)
                .status("COMPLETED")
                .active(true)
                .build();

        when(repository.findByJobNameAndActive(jobName, true))
            .thenReturn(Optional.of(control));
        when(repository.save(any(JobConcurrencyControl.class))).thenReturn(control);

        // Act
        service.registrarDataExecucao(jobName);

        // Assert
        ArgumentCaptor<JobConcurrencyControl> captor = 
                ArgumentCaptor.forClass(JobConcurrencyControl.class);
        verify(repository).save(captor.capture());

        JobConcurrencyControl captured = captor.getValue();
        assertNotNull(captured.getLastExecution());
    }

    /**
     * Testa que registrarDataExecucao lança exceção quando não existe registro ativo.
     */
    @Test
    void registrarDataExecucao_deveLancarExcecaoQuandoNaoExisteRegistroAtivo() {
        // Arrange
        String jobName = "orquestrador-coleta";

        when(repository.findByJobNameAndActive(jobName, true))
            .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.registrarDataExecucao(jobName);
        });

        assertTrue(exception.getMessage().contains("Nenhum registro ativo encontrado"));
        verify(repository, never()).save(any());
    }

    /**
     * Testa que registrarDataExecucao lança exceção quando jobName é nulo.
     */
    @Test
    void registrarDataExecucao_deveLancarExcecaoQuandoJobNameNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarDataExecucao(null);
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).findByJobNameAndActive(any(), any());
    }

    /**
     * Testa que registrarDataExecucao lança exceção quando jobName é vazio.
     */
    @Test
    void registrarDataExecucao_deveLancarExcecaoQuandoJobNameVazio() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarDataExecucao("");
        });

        assertEquals("jobName não pode ser nulo ou vazio", exception.getMessage());
        verify(repository, never()).findByJobNameAndActive(any(), any());
    }

    /**
     * Testa que registrarDataExecucao lança exceção quando não existe nenhum registro.
     */
    @Test
    void registrarDataExecucao_deveLancarExcecaoQuandoNaoExisteRegistro() {
        // Arrange
        String jobName = "orquestrador-coleta";
        when(repository.findByJobNameAndActive(jobName, true))
            .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.registrarDataExecucao(jobName);
        });

        assertTrue(exception.getMessage().contains("Nenhum registro ativo encontrado"));
        verify(repository, never()).save(any());
    }
}
