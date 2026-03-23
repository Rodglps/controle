package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para RastreabilidadeService.
 * 
 * **Valida: Requisitos 12.1, 12.2, 12.3, 12.4, 12.5**
 */
@ExtendWith(MockitoExtension.class)
class RastreabilidadeServiceTest {

    @Mock
    private FileOriginClientProcessingRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    private RastreabilidadeService service;

    @BeforeEach
    void setUp() {
        service = new RastreabilidadeService(repository, objectMapper);
    }

    // ========== Tests for registrarEtapa ==========

    /**
     * Testa registro de nova etapa com status EM_ESPERA.
     * Deve criar um novo registro com os dados fornecidos.
     */
    @Test
    void registrarEtapa_deveRegistrarNovaEtapaComStatusEmEspera() {
        // Arrange
        Long idFileOriginClient = 1L;
        EtapaProcessamento step = EtapaProcessamento.COLETA;
        StatusProcessamento status = StatusProcessamento.EM_ESPERA;

        FileOriginClientProcessing savedProcessing = FileOriginClientProcessing.builder()
                .id(100L)
                .fileOriginClientId(idFileOriginClient)
                .step(step)
                .status(status)
                .active(true)
                .build();

        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(savedProcessing);

        // Act
        Long result = service.registrarEtapa(idFileOriginClient, step, status);

        // Assert
        assertEquals(100L, result);
        
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(idFileOriginClient, captured.getFileOriginClientId());
        assertEquals(step, captured.getStep());
        assertEquals(status, captured.getStatus());
        assertTrue(captured.getActive());
    }

    /**
     * Testa que registrarEtapa lança exceção quando idFileOriginClient é nulo.
     */
    @Test
    void registrarEtapa_deveLancarExcecaoQuandoIdFileOriginClientNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarEtapa(null, EtapaProcessamento.COLETA, StatusProcessamento.EM_ESPERA);
        });

        assertEquals("idFileOriginClient não pode ser nulo", exception.getMessage());
        verify(repository, never()).save(any());
    }

    /**
     * Testa que registrarEtapa lança exceção quando step é nulo.
     */
    @Test
    void registrarEtapa_deveLancarExcecaoQuandoStepNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarEtapa(1L, null, StatusProcessamento.EM_ESPERA);
        });

        assertEquals("step não pode ser nulo", exception.getMessage());
        verify(repository, never()).save(any());
    }

    /**
     * Testa que registrarEtapa lança exceção quando status é nulo.
     */
    @Test
    void registrarEtapa_deveLancarExcecaoQuandoStatusNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarEtapa(1L, EtapaProcessamento.COLETA, null);
        });

        assertEquals("status não pode ser nulo", exception.getMessage());
        verify(repository, never()).save(any());
    }

    /**
     * Testa que registrarEtapa lança exceção quando status inicial não é EM_ESPERA.
     */
    @Test
    void registrarEtapa_deveLancarExcecaoQuandoStatusInicialNaoEhEmEspera() {
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.registrarEtapa(1L, EtapaProcessamento.COLETA, StatusProcessamento.PROCESSAMENTO);
        });

        assertTrue(exception.getMessage().contains("Status inicial deve ser EM_ESPERA"));
        verify(repository, never()).save(any());
    }

    // ========== Tests for atualizarStatus ==========

    /**
     * Testa atualização para PROCESSAMENTO a partir de EM_ESPERA.
     * Deve atualizar o status corretamente.
     */
    @Test
    void atualizarStatus_deveAtualizarParaProcessamentoAPartirDeEmEspera() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        // Act
        service.atualizarStatus(idProcessing, StatusProcessamento.PROCESSAMENTO, null);

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(StatusProcessamento.PROCESSAMENTO, captured.getStatus());
        assertNull(captured.getMessageError());
    }

    /**
     * Testa atualização para CONCLUIDO a partir de PROCESSAMENTO.
     * Deve atualizar o status corretamente.
     */
    @Test
    void atualizarStatus_deveAtualizarParaConcluidoAPartirDeProcessamento() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        // Act
        service.atualizarStatus(idProcessing, StatusProcessamento.CONCLUIDO, null);

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(StatusProcessamento.CONCLUIDO, captured.getStatus());
    }

    /**
     * Testa atualização para ERRO com mensagem de erro a partir de PROCESSAMENTO.
     * Deve atualizar o status e armazenar a mensagem de erro.
     */
    @Test
    void atualizarStatus_deveAtualizarParaErroComMensagemAPartirDeProcessamento() {
        // Arrange
        Long idProcessing = 100L;
        String mensagemErro = "Erro ao processar arquivo";
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        // Act
        service.atualizarStatus(idProcessing, StatusProcessamento.ERRO, mensagemErro);

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(StatusProcessamento.ERRO, captured.getStatus());
        assertEquals(mensagemErro, captured.getMessageError());
    }

    /**
     * Testa atualização para ERRO a partir de EM_ESPERA (erro antes de iniciar processamento).
     * Deve permitir a transição.
     */
    @Test
    void atualizarStatus_deveAtualizarParaErroAPartirDeEmEspera() {
        // Arrange
        Long idProcessing = 100L;
        String mensagemErro = "Erro na validação inicial";
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        // Act
        service.atualizarStatus(idProcessing, StatusProcessamento.ERRO, mensagemErro);

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(StatusProcessamento.ERRO, captured.getStatus());
        assertEquals(mensagemErro, captured.getMessageError());
    }

    /**
     * Testa que atualizarStatus lança exceção quando idProcessing é nulo.
     */
    @Test
    void atualizarStatus_deveLancarExcecaoQuandoIdProcessingNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.atualizarStatus(null, StatusProcessamento.PROCESSAMENTO, null);
        });

        assertEquals("idProcessing não pode ser nulo", exception.getMessage());
        verify(repository, never()).save(any());
    }

    /**
     * Testa que atualizarStatus lança exceção quando status é nulo.
     */
    @Test
    void atualizarStatus_deveLancarExcecaoQuandoStatusNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.atualizarStatus(100L, null, null);
        });

        assertEquals("status não pode ser nulo", exception.getMessage());
        verify(repository, never()).findById(any());
    }

    /**
     * Testa que atualizarStatus lança exceção quando registro não é encontrado.
     */
    @Test
    void atualizarStatus_deveLancarExcecaoQuandoRegistroNaoEncontrado() {
        // Arrange
        Long idProcessing = 999L;
        when(repository.findById(idProcessing)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.atualizarStatus(idProcessing, StatusProcessamento.PROCESSAMENTO, null);
        });

        assertTrue(exception.getMessage().contains("Registro de processamento não encontrado"));
        verify(repository, never()).save(any());
    }

    /**
     * Testa que atualizarStatus lança exceção para transição inválida (CONCLUIDO -> PROCESSAMENTO).
     */
    @Test
    void atualizarStatus_deveLancarExcecaoParaTransicaoInvalidaDeConcluidoParaProcessamento() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.CONCLUIDO)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.atualizarStatus(idProcessing, StatusProcessamento.PROCESSAMENTO, null);
        });

        assertTrue(exception.getMessage().contains("Transição de status inválida"));
        verify(repository, never()).save(any());
    }

    /**
     * Testa que atualizarStatus lança exceção para transição inválida (ERRO -> PROCESSAMENTO).
     */
    @Test
    void atualizarStatus_deveLancarExcecaoParaTransicaoInvalidaDeErroParaProcessamento() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.ERRO)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.atualizarStatus(idProcessing, StatusProcessamento.PROCESSAMENTO, null);
        });

        assertTrue(exception.getMessage().contains("Transição de status inválida"));
        verify(repository, never()).save(any());
    }

    // ========== Tests for registrarInicio ==========

    /**
     * Testa registro de início de processamento com dat_step_start.
     * Deve atualizar o status para PROCESSAMENTO e registrar o timestamp.
     */
    @Test
    void registrarInicio_deveAtualizarStatusParaProcessamentoComTimestamp() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        Instant before = Instant.now();

        // Act
        service.registrarInicio(idProcessing);

        Instant after = Instant.now();

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(StatusProcessamento.PROCESSAMENTO, captured.getStatus());
        assertNotNull(captured.getStepStart());
        assertTrue(captured.getStepStart().isAfter(before.minusSeconds(1)));
        assertTrue(captured.getStepStart().isBefore(after.plusSeconds(1)));
    }

    /**
     * Testa que registrarInicio lança exceção quando idProcessing é nulo.
     */
    @Test
    void registrarInicio_deveLancarExcecaoQuandoIdProcessingNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarInicio(null);
        });

        assertEquals("idProcessing não pode ser nulo", exception.getMessage());
        verify(repository, never()).findById(any());
    }

    /**
     * Testa que registrarInicio lança exceção quando registro não é encontrado.
     */
    @Test
    void registrarInicio_deveLancarExcecaoQuandoRegistroNaoEncontrado() {
        // Arrange
        Long idProcessing = 999L;
        when(repository.findById(idProcessing)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarInicio(idProcessing);
        });

        assertTrue(exception.getMessage().contains("Registro de processamento não encontrado"));
        verify(repository, never()).save(any());
    }

    // ========== Tests for registrarConclusao ==========

    /**
     * Testa registro de conclusão com dat_step_end.
     * Deve atualizar o status para CONCLUIDO e registrar o timestamp.
     */
    @Test
    void registrarConclusao_deveAtualizarStatusParaConcluidoComTimestamp() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .stepStart(Instant.now().minusSeconds(60))
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        Instant before = Instant.now();

        // Act
        service.registrarConclusao(idProcessing, null);

        Instant after = Instant.now();

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(StatusProcessamento.CONCLUIDO, captured.getStatus());
        assertNotNull(captured.getStepEnd());
        assertTrue(captured.getStepEnd().isAfter(before.minusSeconds(1)));
        assertTrue(captured.getStepEnd().isBefore(after.plusSeconds(1)));
    }

    /**
     * Testa armazenamento de jsn_additional_info.
     * Deve serializar o mapa para JSON e armazenar no campo additionalInfo.
     */
    @Test
    void registrarConclusao_deveArmazenarInformacoesAdicionaisEmJson() throws JsonProcessingException {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .active(true)
                .build();

        Map<String, Object> infoAdicional = new HashMap<>();
        infoAdicional.put("tamanho", 1024000);
        infoAdicional.put("destino", "s3://bucket/file.txt");
        infoAdicional.put("duracao", 45);

        String jsonEsperado = "{\"tamanho\":1024000,\"destino\":\"s3://bucket/file.txt\",\"duracao\":45}";

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);
        when(objectMapper.writeValueAsString(infoAdicional)).thenReturn(jsonEsperado);

        // Act
        service.registrarConclusao(idProcessing, infoAdicional);

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertEquals(jsonEsperado, captured.getAdditionalInfo());
        verify(objectMapper).writeValueAsString(infoAdicional);
    }

    /**
     * Testa que registrarConclusao não armazena JSON quando infoAdicional é nulo.
     */
    @Test
    void registrarConclusao_naoDeveArmazenarJsonQuandoInfoAdicionalNulo() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        // Act
        service.registrarConclusao(idProcessing, null);

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertNull(captured.getAdditionalInfo());
        verify(objectMapper, never()).writeValueAsString(any());
    }

    /**
     * Testa que registrarConclusao não armazena JSON quando infoAdicional está vazio.
     */
    @Test
    void registrarConclusao_naoDeveArmazenarJsonQuandoInfoAdicionalVazio() {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .active(true)
                .build();

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);

        // Act
        service.registrarConclusao(idProcessing, new HashMap<>());

        // Assert
        ArgumentCaptor<FileOriginClientProcessing> captor = 
                ArgumentCaptor.forClass(FileOriginClientProcessing.class);
        verify(repository).save(captor.capture());

        FileOriginClientProcessing captured = captor.getValue();
        assertNull(captured.getAdditionalInfo());
        verify(objectMapper, never()).writeValueAsString(any());
    }

    /**
     * Testa que registrarConclusao não lança exceção quando serialização JSON falha.
     * Deve apenas logar o erro e continuar.
     */
    @Test
    void registrarConclusao_naoDeveLancarExcecaoQuandoSerializacaoJsonFalha() throws JsonProcessingException {
        // Arrange
        Long idProcessing = 100L;
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .id(idProcessing)
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.PROCESSAMENTO)
                .active(true)
                .build();

        Map<String, Object> infoAdicional = new HashMap<>();
        infoAdicional.put("key", "value");

        when(repository.findById(idProcessing)).thenReturn(Optional.of(processing));
        when(repository.save(any(FileOriginClientProcessing.class))).thenReturn(processing);
        when(objectMapper.writeValueAsString(infoAdicional))
                .thenThrow(new JsonProcessingException("Serialization error") {});

        // Act - não deve lançar exceção
        assertDoesNotThrow(() -> {
            service.registrarConclusao(idProcessing, infoAdicional);
        });

        // Assert
        verify(repository).save(any(FileOriginClientProcessing.class));
    }

    /**
     * Testa que registrarConclusao lança exceção quando idProcessing é nulo.
     */
    @Test
    void registrarConclusao_deveLancarExcecaoQuandoIdProcessingNulo() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarConclusao(null, null);
        });

        assertEquals("idProcessing não pode ser nulo", exception.getMessage());
        verify(repository, never()).findById(any());
    }

    /**
     * Testa que registrarConclusao lança exceção quando registro não é encontrado.
     */
    @Test
    void registrarConclusao_deveLancarExcecaoQuandoRegistroNaoEncontrado() {
        // Arrange
        Long idProcessing = 999L;
        when(repository.findById(idProcessing)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.registrarConclusao(idProcessing, null);
        });

        assertTrue(exception.getMessage().contains("Registro de processamento não encontrado"));
        verify(repository, never()).save(any());
    }
}
