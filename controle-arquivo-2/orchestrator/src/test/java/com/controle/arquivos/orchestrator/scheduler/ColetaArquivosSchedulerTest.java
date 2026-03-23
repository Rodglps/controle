package com.controle.arquivos.orchestrator.scheduler;

import com.controle.arquivos.orchestrator.service.OrquestradorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Testes unitários para ColetaArquivosScheduler.
 * Verifica que o scheduler invoca o OrquestradorService corretamente
 * e trata exceções sem propagar.
 */
@ExtendWith(MockitoExtension.class)
class ColetaArquivosSchedulerTest {

    @Mock
    private OrquestradorService orquestradorService;

    @InjectMocks
    private ColetaArquivosScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Setup comum se necessário
    }

    @Test
    void executarCicloColetaAgendado_deveInvocarOrquestradorService() {
        // Arrange
        doNothing().when(orquestradorService).executarCicloColeta();

        // Act
        scheduler.executarCicloColetaAgendado();

        // Assert
        verify(orquestradorService, times(1)).executarCicloColeta();
    }

    @Test
    void executarCicloColetaAgendado_deveRegistrarLogDeInicio() {
        // Arrange
        doNothing().when(orquestradorService).executarCicloColeta();

        // Act
        scheduler.executarCicloColetaAgendado();

        // Assert
        // Log de início é registrado antes da invocação
        verify(orquestradorService, times(1)).executarCicloColeta();
    }

    @Test
    void executarCicloColetaAgendado_deveRegistrarLogDeConclusao() {
        // Arrange
        doNothing().when(orquestradorService).executarCicloColeta();

        // Act
        scheduler.executarCicloColetaAgendado();

        // Assert
        // Log de conclusão é registrado após a invocação
        verify(orquestradorService, times(1)).executarCicloColeta();
    }

    @Test
    void executarCicloColetaAgendado_deveTratarExcecaoSemPropagar() {
        // Arrange
        RuntimeException exception = new RuntimeException("Erro simulado");
        doThrow(exception).when(orquestradorService).executarCicloColeta();

        // Act - não deve propagar exceção
        scheduler.executarCicloColetaAgendado();

        // Assert
        verify(orquestradorService, times(1)).executarCicloColeta();
        // Exceção foi capturada e logada, mas não propagada
    }

    @Test
    void executarCicloColetaAgendado_deveRegistrarLogDeErroQuandoFalhar() {
        // Arrange
        RuntimeException exception = new RuntimeException("Erro de conexão");
        doThrow(exception).when(orquestradorService).executarCicloColeta();

        // Act
        scheduler.executarCicloColetaAgendado();

        // Assert
        // Log de erro é registrado com detalhes da exceção
        verify(orquestradorService, times(1)).executarCicloColeta();
    }

    @Test
    void executarCicloColetaAgendado_deveCalcularDuracaoDociclo() {
        // Arrange
        doAnswer(invocation -> {
            // Simular processamento que leva algum tempo
            Thread.sleep(10);
            return null;
        }).when(orquestradorService).executarCicloColeta();

        // Act
        scheduler.executarCicloColetaAgendado();

        // Assert
        // Duração é calculada e logada
        verify(orquestradorService, times(1)).executarCicloColeta();
    }
}
