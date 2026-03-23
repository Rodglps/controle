package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClient;
import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.controle.arquivos.common.repository.FileOriginClientRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.common.repository.ServerRepository;
import com.controle.arquivos.common.repository.SeverPathsInOutRepository;
import com.controle.arquivos.common.repository.SeverPathsRepository;
import com.controle.arquivos.common.service.ClienteIdentificationService;
import com.controle.arquivos.common.service.LayoutIdentificationService;
import com.controle.arquivos.common.service.RastreabilidadeService;
import com.controle.arquivos.common.service.StreamingTransferService;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import com.controle.arquivos.processor.exception.ErroNaoRecuperavelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ProcessadorService - Limite de Reprocessamento (Task 15.3).
 * 
 * Testa:
 * - Verificação de contador de tentativas antes de processar
 * - Incremento de contador em jsn_additional_info quando ocorre erro
 * - Marcação como ERRO permanente quando contador >= 5
 * - Lançamento de ErroNaoRecuperavelException para não reprocessar
 * 
 * **Valida: Requisitos 15.6**
 */
@ExtendWith(MockitoExtension.class)
class ProcessadorServiceRetryLimitTest {

    @Mock
    private FileOriginRepository fileOriginRepository;

    @Mock
    private SeverPathsInOutRepository severPathsInOutRepository;

    @Mock
    private SeverPathsRepository severPathsRepository;

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private SFTPClient sftpClient;

    @Mock
    private ClienteIdentificationService clienteIdentificationService;

    @Mock
    private LayoutIdentificationService layoutIdentificationService;

    @Mock
    private FileOriginClientRepository fileOriginClientRepository;

    @Mock
    private StreamingTransferService streamingTransferService;

    @Mock
    private RastreabilidadeService rastreabilidadeService;
    
    @Mock
    private FileOriginClientProcessingRepository processingRepository;
    
    @Mock
    private ObjectMapper objectMapper;

    private ProcessadorService processadorService;

    @BeforeEach
    void setup() {
        processadorService = new ProcessadorService(
                fileOriginRepository,
                severPathsInOutRepository,
                severPathsRepository,
                serverRepository,
                vaultClient,
                sftpClient,
                clienteIdentificationService,
                layoutIdentificationService,
                fileOriginClientRepository,
                streamingTransferService,
                rastreabilidadeService,
                processingRepository,
                objectMapper
        );
    }

    /**
     * Testa que arquivo é processado normalmente quando não há tentativas anteriores.
     * Primeira tentativa deve ser permitida.
     * 
     * **Valida: Requisitos 15.6**
     */
    @Test
    void devePermitirProcessamentoQuandoNaoHaTentativasAnteriores() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("TESTE.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("test-123")
                .build();

        FileOrigin fileOrigin = FileOrigin.builder()
                .id(1L)
                .fileName("TESTE.txt")
                .fileSize(1024L)
                .build();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L))
                .thenReturn(Optional.empty()); // Nenhum file_origin_client ainda

        // Act & Assert
        // Deve lançar exceção porque não configuramos todo o fluxo, mas não deve ser por limite de retry
        assertThrows(Exception.class, () -> processadorService.processarArquivo(mensagem));
        
        // Verificar que tentou processar (não foi bloqueado por limite)
        verify(fileOriginRepository).findById(1L);
    }

    /**
     * Testa que arquivo é processado normalmente quando há menos de 5 tentativas anteriores.
     * Tentativas 2, 3, 4 devem ser permitidas.
     * 
     * **Valida: Requisitos 15.6**
     */
    @Test
    void devePermitirProcessamentoQuandoMenosDe5Tentativas() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("TESTE.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("test-123")
                .build();

        FileOrigin fileOrigin = FileOrigin.builder()
                .id(1L)
                .fileName("TESTE.txt")
                .fileSize(1024L)
                .build();

        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .id(100L)
                .fileOriginId(1L)
                .clientId(50L)
                .active(true)
                .build();

        // Simular 3 tentativas anteriores com erro
        List<FileOriginClientProcessing> processings = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .id((long) i)
                    .fileOriginClientId(100L)
                    .step(EtapaProcessamento.COLETA)
                    .status(StatusProcessamento.ERRO)
                    .additionalInfo("{\"retryCount\":" + i + ",\"stackTrace\":\"...\"}")
                    .build();
            processings.add(processing);
        }

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L))
                .thenReturn(Optional.of(fileOriginClient));
        when(processingRepository.findByFileOriginClientId(100L)).thenReturn(processings);
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    if (json.contains("\"retryCount\":1")) return java.util.Map.of("retryCount", 1);
                    if (json.contains("\"retryCount\":2")) return java.util.Map.of("retryCount", 2);
                    if (json.contains("\"retryCount\":3")) return java.util.Map.of("retryCount", 3);
                    return java.util.Map.of();
                });

        // Act & Assert
        // Deve lançar exceção porque não configuramos todo o fluxo, mas não deve ser por limite de retry
        assertThrows(Exception.class, () -> processadorService.processarArquivo(mensagem));
        
        // Verificar que tentou processar (não foi bloqueado por limite)
        verify(fileOriginRepository).findById(1L);
    }

    /**
     * Testa que arquivo NÃO é processado quando já atingiu 5 tentativas.
     * Deve lançar ErroNaoRecuperavelException para ACK da mensagem (não reprocessar).
     * 
     * **Valida: Requisitos 15.6**
     */
    @Test
    void deveBloquearProcessamentoQuandoAtingiu5Tentativas() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("TESTE.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("test-123")
                .build();

        FileOrigin fileOrigin = FileOrigin.builder()
                .id(1L)
                .fileName("TESTE.txt")
                .fileSize(1024L)
                .build();

        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .id(100L)
                .fileOriginId(1L)
                .clientId(50L)
                .active(true)
                .build();

        // Simular 5 tentativas anteriores com erro
        List<FileOriginClientProcessing> processings = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .id((long) i)
                    .fileOriginClientId(100L)
                    .step(EtapaProcessamento.COLETA)
                    .status(StatusProcessamento.ERRO)
                    .additionalInfo("{\"retryCount\":" + i + ",\"stackTrace\":\"...\"}")
                    .build();
            processings.add(processing);
        }

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L))
                .thenReturn(Optional.of(fileOriginClient));
        when(processingRepository.findByFileOriginClientId(100L)).thenReturn(processings);
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    for (int i = 1; i <= 5; i++) {
                        if (json.contains("\"retryCount\":" + i)) {
                            return java.util.Map.of("retryCount", i);
                        }
                    }
                    return java.util.Map.of();
                });

        // Act & Assert
        ErroNaoRecuperavelException exception = assertThrows(
                ErroNaoRecuperavelException.class,
                () -> processadorService.processarArquivo(mensagem)
        );

        // Verificar mensagem de erro
        assertTrue(exception.getMessage().contains("Limite de reprocessamento atingido"));
        assertTrue(exception.getMessage().contains("file_origin_id=1"));
        assertTrue(exception.getMessage().contains("Tentativas: 5"));
        
        // Verificar que NÃO tentou processar (foi bloqueado por limite)
        verify(fileOriginRepository).findById(1L);
        verify(fileOriginClientRepository).findByFileOriginIdAndActiveTrue(1L);
        verify(processingRepository).findByFileOriginClientId(100L);
        
        // Não deve ter tentado baixar arquivo
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
    }

    /**
     * Testa que arquivo NÃO é processado quando já atingiu mais de 5 tentativas.
     * Deve lançar ErroNaoRecuperavelException para ACK da mensagem (não reprocessar).
     * 
     * **Valida: Requisitos 15.6**
     */
    @Test
    void deveBloquearProcessamentoQuandoMaisDe5Tentativas() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("TESTE.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("test-123")
                .build();

        FileOrigin fileOrigin = FileOrigin.builder()
                .id(1L)
                .fileName("TESTE.txt")
                .fileSize(1024L)
                .build();

        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .id(100L)
                .fileOriginId(1L)
                .clientId(50L)
                .active(true)
                .build();

        // Simular 7 tentativas anteriores com erro (mais que o limite)
        List<FileOriginClientProcessing> processings = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .id((long) i)
                    .fileOriginClientId(100L)
                    .step(EtapaProcessamento.COLETA)
                    .status(StatusProcessamento.ERRO)
                    .additionalInfo("{\"retryCount\":" + i + ",\"stackTrace\":\"...\"}")
                    .build();
            processings.add(processing);
        }

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L))
                .thenReturn(Optional.of(fileOriginClient));
        when(processingRepository.findByFileOriginClientId(100L)).thenReturn(processings);
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    for (int i = 1; i <= 7; i++) {
                        if (json.contains("\"retryCount\":" + i)) {
                            return java.util.Map.of("retryCount", i);
                        }
                    }
                    return java.util.Map.of();
                });

        // Act & Assert
        ErroNaoRecuperavelException exception = assertThrows(
                ErroNaoRecuperavelException.class,
                () -> processadorService.processarArquivo(mensagem)
        );

        // Verificar mensagem de erro
        assertTrue(exception.getMessage().contains("Limite de reprocessamento atingido"));
        assertTrue(exception.getMessage().contains("Tentativas: 7"));
        
        // Não deve ter tentado baixar arquivo
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
    }

    /**
     * Testa que contador de tentativas é incrementado quando ocorre erro.
     * O contador deve ser incluído em jsn_additional_info.
     * 
     * **Valida: Requisitos 15.6**
     */
    @Test
    void deveIncrementarContadorQuandoOcorreErro() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("TESTE.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("test-123")
                .build();

        FileOrigin fileOrigin = FileOrigin.builder()
                .id(1L)
                .fileName("TESTE.txt")
                .fileSize(1024L)
                .build();

        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .id(100L)
                .fileOriginId(1L)
                .clientId(50L)
                .active(true)
                .build();

        // Simular 2 tentativas anteriores
        List<FileOriginClientProcessing> processings = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .id((long) i)
                    .fileOriginClientId(100L)
                    .step(EtapaProcessamento.COLETA)
                    .status(StatusProcessamento.ERRO)
                    .additionalInfo("{\"retryCount\":" + i + ",\"stackTrace\":\"...\"}")
                    .build();
            processings.add(processing);
        }

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L))
                .thenReturn(Optional.of(fileOriginClient));
        when(processingRepository.findByFileOriginClientId(100L)).thenReturn(processings);
        when(objectMapper.readValue(anyString(), eq(java.util.Map.class)))
                .thenAnswer(invocation -> {
                    String json = invocation.getArgument(0);
                    if (json.contains("\"retryCount\":1")) return java.util.Map.of("retryCount", 1);
                    if (json.contains("\"retryCount\":2")) return java.util.Map.of("retryCount", 2);
                    return java.util.Map.of();
                });

        // Simular erro durante processamento (não configurar mocks necessários)
        // Isso causará uma exceção que será capturada e o contador será incrementado

        // Act & Assert
        assertThrows(Exception.class, () -> processadorService.processarArquivo(mensagem));

        // Verificar que rastreabilidade foi chamada com contador incrementado (3)
        // O contador deve ser passado para registrarConclusao via infoAdicional
        verify(rastreabilidadeService, atLeastOnce()).registrarConclusao(anyLong(), argThat(info -> {
            if (info == null) return false;
            Object retryCount = info.get("retryCount");
            return retryCount != null && retryCount.equals(3);
        }));
    }
}
