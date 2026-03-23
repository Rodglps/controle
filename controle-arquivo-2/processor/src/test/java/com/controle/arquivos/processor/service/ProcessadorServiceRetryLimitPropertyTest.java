package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClient;
import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
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
import com.controle.arquivos.processor.exception.ErroRecuperavelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para limite de reprocessamento do ProcessadorService.
 * 
 * **Valida: Requisitos 15.6**
 * 
 * Feature: controle-de-arquivos, Property 30: Limite de Reprocessamento
 * 
 * Para qualquer arquivo com múltiplos erros, o Sistema deve limitar tentativas a 5 reprocessamentos.
 */
class ProcessadorServiceRetryLimitPropertyTest {

    private ProcessadorService processadorService;
    private FileOriginRepository fileOriginRepository;
    private SeverPathsInOutRepository severPathsInOutRepository;
    private SeverPathsRepository severPathsRepository;
    private ServerRepository serverRepository;
    private VaultClient vaultClient;
    private SFTPClient sftpClient;
    private ClienteIdentificationService clienteIdentificationService;
    private LayoutIdentificationService layoutIdentificationService;
    private FileOriginClientRepository fileOriginClientRepository;
    private StreamingTransferService streamingTransferService;
    private RastreabilidadeService rastreabilidadeService;
    private FileOriginClientProcessingRepository processingRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        setupMocks();
    }

    /**
     * **Propriedade 30: Limite de Reprocessamento**
     * **Valida: Requisitos 15.6**
     * 
     * Para qualquer arquivo que já falhou menos de 5 vezes,
     * o sistema deve permitir reprocessamento.
     */
    @Property(tries = 100)
    void arquivosComMenosDe5FalhasPermitemReprocessamento(
            @ForAll("mensagemProcessamento") MensagemProcessamento mensagem,
            @ForAll("numeroTentativasAnterior") @IntRange(min = 0, max = 4) int tentativasAnteriores) throws Exception {
        
        // Arrange
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        FileOriginClient fileOriginClient = criarFileOriginClient(mensagem.getIdFileOrigin(), 100L);
        List<FileOriginClientProcessing> processingsAnteriores = criarProcessingsComErro(
                fileOriginClient.getId(), tentativasAnteriores);
        
        configurarMocksParaProcessamento(mensagem, fileOrigin, fileOriginClient, processingsAnteriores);
        
        // Simular erro durante processamento para testar que o sistema tenta processar
        doThrow(new RuntimeException("Erro simulado")).when(sftpClient).conectar(any(), anyInt(), any());
        
        // Act & Assert
        // O sistema deve tentar processar (não lançar ErroNaoRecuperavelException imediatamente)
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
                .isNotInstanceOf(ErroNaoRecuperavelException.class)
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(ErroRecuperavelException.class),
                        ex -> assertThat(ex).isInstanceOf(RuntimeException.class)
                );
        
        // Verificar que o sistema tentou processar (não bloqueou por limite)
        verify(fileOriginRepository, atLeastOnce()).findById(mensagem.getIdFileOrigin());
    }

    /**
     * **Propriedade 30: Limite de Reprocessamento**
     * **Valida: Requisitos 15.6**
     * 
     * Para qualquer arquivo que já falhou exatamente 5 vezes,
     * o sistema deve bloquear reprocessamento e lançar ErroNaoRecuperavelException.
     */
    @Property(tries = 100)
    void arquivosComExatamente5FalhasBloqueiaReprocessamento(
            @ForAll("mensagemProcessamento") MensagemProcessamento mensagem) throws Exception {
        
        // Arrange
        int tentativasAnteriores = 5;
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        FileOriginClient fileOriginClient = criarFileOriginClient(mensagem.getIdFileOrigin(), 100L);
        List<FileOriginClientProcessing> processingsAnteriores = criarProcessingsComErro(
                fileOriginClient.getId(), tentativasAnteriores);
        
        configurarMocksParaProcessamento(mensagem, fileOrigin, fileOriginClient, processingsAnteriores);
        
        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
                .isInstanceOf(ErroNaoRecuperavelException.class)
                .hasMessageContaining("Limite de reprocessamento atingido")
                .hasMessageContaining("file_origin_id=" + mensagem.getIdFileOrigin())
                .hasMessageContaining("Tentativas: 5");
        
        // Verificar que o sistema não tentou processar (bloqueou antes)
        verify(sftpClient, never()).conectar(any(), anyInt(), any());
        verify(clienteIdentificationService, never()).identificar(any(), any());
    }

    /**
     * **Propriedade 30: Limite de Reprocessamento**
     * **Valida: Requisitos 15.6**
     * 
     * Para qualquer arquivo que já falhou mais de 5 vezes (cenário de dados inconsistentes),
     * o sistema deve bloquear reprocessamento.
     */
    @Property(tries = 50)
    void arquivosComMaisDe5FalhasBloqueiaReprocessamento(
            @ForAll("mensagemProcessamento") MensagemProcessamento mensagem,
            @ForAll("numeroTentativasExcedente") @IntRange(min = 6, max = 20) int tentativasAnteriores) throws Exception {
        
        // Arrange
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        FileOriginClient fileOriginClient = criarFileOriginClient(mensagem.getIdFileOrigin(), 100L);
        List<FileOriginClientProcessing> processingsAnteriores = criarProcessingsComErro(
                fileOriginClient.getId(), tentativasAnteriores);
        
        configurarMocksParaProcessamento(mensagem, fileOrigin, fileOriginClient, processingsAnteriores);
        
        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
                .isInstanceOf(ErroNaoRecuperavelException.class)
                .hasMessageContaining("Limite de reprocessamento atingido");
        
        // Verificar que o sistema não tentou processar
        verify(sftpClient, never()).conectar(any(), anyInt(), any());
    }

    /**
     * **Propriedade 30: Limite de Reprocessamento**
     * **Valida: Requisitos 15.6**
     * 
     * Para qualquer arquivo sem histórico de falhas (primeira tentativa),
     * o sistema deve permitir processamento normalmente.
     */
    @Property(tries = 100)
    void arquivosSemHistoricoPermitemProcessamento(
            @ForAll("mensagemProcessamento") MensagemProcessamento mensagem) throws Exception {
        
        // Arrange
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        
        // Sem FileOriginClient (primeira tentativa)
        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(mensagem.getIdFileOrigin()))
                .thenReturn(Optional.empty());
        
        configurarMocksBasicos(mensagem);
        
        // Simular erro para verificar que o sistema tenta processar
        doThrow(new RuntimeException("Erro simulado")).when(sftpClient).conectar(any(), anyInt(), any());
        
        // Act & Assert
        // O sistema deve tentar processar (não bloquear por limite)
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
                .isNotInstanceOf(ErroNaoRecuperavelException.class);
        
        // Verificar que o sistema tentou buscar o arquivo
        verify(fileOriginRepository, atLeastOnce()).findById(mensagem.getIdFileOrigin());
    }

    /**
     * **Propriedade 30: Limite de Reprocessamento**
     * **Valida: Requisitos 15.6**
     * 
     * Para qualquer número de tentativas anteriores (0 a 10),
     * o comportamento do sistema deve ser consistente:
     * - < 5: permite reprocessamento
     * - >= 5: bloqueia reprocessamento
     */
    @Property(tries = 100)
    void limiteDeReprocessamentoEhConsistente(
            @ForAll("mensagemProcessamento") MensagemProcessamento mensagem,
            @ForAll("numeroTentativas") @IntRange(min = 0, max = 10) int tentativasAnteriores) throws Exception {
        
        // Arrange
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        FileOriginClient fileOriginClient = criarFileOriginClient(mensagem.getIdFileOrigin(), 100L);
        List<FileOriginClientProcessing> processingsAnteriores = criarProcessingsComErro(
                fileOriginClient.getId(), tentativasAnteriores);
        
        configurarMocksParaProcessamento(mensagem, fileOrigin, fileOriginClient, processingsAnteriores);
        
        // Simular erro durante processamento
        doThrow(new RuntimeException("Erro simulado")).when(sftpClient).conectar(any(), anyInt(), any());
        
        // Act & Assert
        if (tentativasAnteriores < 5) {
            // Deve permitir reprocessamento
            assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
                    .isNotInstanceOf(ErroNaoRecuperavelException.class);
            
            verify(fileOriginRepository, atLeastOnce()).findById(mensagem.getIdFileOrigin());
        } else {
            // Deve bloquear reprocessamento
            assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
                    .isInstanceOf(ErroNaoRecuperavelException.class)
                    .hasMessageContaining("Limite de reprocessamento atingido");
            
            verify(sftpClient, never()).conectar(any(), anyInt(), any());
        }
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<MensagemProcessamento> mensagemProcessamento() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 1000000L),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30).map(s -> s + ".txt"),
                Arbitraries.longs().between(1L, 100L),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(20)
        ).as((idFileOrigin, nomeArquivo, idMapeamento, correlationId) ->
                MensagemProcessamento.builder()
                        .idFileOrigin(idFileOrigin)
                        .nomeArquivo(nomeArquivo)
                        .idMapeamentoOrigemDestino(idMapeamento)
                        .correlationId(correlationId)
                        .build()
        );
    }

    @Provide
    Arbitrary<Integer> numeroTentativasAnterior() {
        return Arbitraries.integers().between(0, 4);
    }

    @Provide
    Arbitrary<Integer> numeroTentativasExcedente() {
        return Arbitraries.integers().between(6, 20);
    }

    @Provide
    Arbitrary<Integer> numeroTentativas() {
        return Arbitraries.integers().between(0, 10);
    }

    // ========== Helper Methods ==========

    private void setupMocks() {
        fileOriginRepository = mock(FileOriginRepository.class);
        severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        severPathsRepository = mock(SeverPathsRepository.class);
        serverRepository = mock(ServerRepository.class);
        vaultClient = mock(VaultClient.class);
        sftpClient = mock(SFTPClient.class);
        clienteIdentificationService = mock(ClienteIdentificationService.class);
        layoutIdentificationService = mock(LayoutIdentificationService.class);
        fileOriginClientRepository = mock(FileOriginClientRepository.class);
        streamingTransferService = mock(StreamingTransferService.class);
        rastreabilidadeService = mock(RastreabilidadeService.class);
        processingRepository = mock(FileOriginClientProcessingRepository.class);
        objectMapper = new ObjectMapper();

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

    private FileOrigin criarFileOrigin(Long id, String fileName) {
        return FileOrigin.builder()
                .id(id)
                .acquirerId(1L)
                .fileName(fileName)
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private FileOriginClient criarFileOriginClient(Long fileOriginId, Long clientId) {
        return FileOriginClient.builder()
                .id(fileOriginId * 10) // ID único baseado no fileOriginId
                .fileOriginId(fileOriginId)
                .clientId(clientId)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private List<FileOriginClientProcessing> criarProcessingsComErro(Long fileOriginClientId, int numeroTentativas) {
        List<FileOriginClientProcessing> processings = new ArrayList<>();
        
        for (int i = 1; i <= numeroTentativas; i++) {
            Map<String, Object> additionalInfo = new HashMap<>();
            additionalInfo.put("retryCount", i);
            additionalInfo.put("stackTrace", "Simulated stack trace for attempt " + i);
            additionalInfo.put("exceptionType", "RuntimeException");
            
            String additionalInfoJson;
            try {
                additionalInfoJson = objectMapper.writeValueAsString(additionalInfo);
            } catch (Exception e) {
                additionalInfoJson = "{\"retryCount\":" + i + "}";
            }
            
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .id((long) i)
                    .fileOriginClientId(fileOriginClientId)
                    .step(com.controle.arquivos.common.domain.enums.EtapaProcessamento.COLETA)
                    .status(StatusProcessamento.ERRO)
                    .messageError("Erro na tentativa " + i)
                    .additionalInfo(additionalInfoJson)
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            
            processings.add(processing);
        }
        
        return processings;
    }

    private void configurarMocksParaProcessamento(
            MensagemProcessamento mensagem,
            FileOrigin fileOrigin,
            FileOriginClient fileOriginClient,
            List<FileOriginClientProcessing> processingsAnteriores) {
        
        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(mensagem.getIdFileOrigin()))
                .thenReturn(Optional.of(fileOriginClient));
        when(processingRepository.findByFileOriginClientId(fileOriginClient.getId()))
                .thenReturn(processingsAnteriores);
        
        configurarMocksBasicos(mensagem);
    }

    private void configurarMocksBasicos(MensagemProcessamento mensagem) {
        // Configurar mocks básicos para evitar NullPointerException
        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .id(mensagem.getIdMapeamentoOrigemDestino())
                .severPathOriginId(1L)
                .severDestinationId(2L)
                .build();
        
        SeverPaths severPath = SeverPaths.builder()
                .id(1L)
                .serverId(1L)
                .path("/origem")
                .build();
        
        Server server = Server.builder()
                .id(1L)
                .serverCode("sftp-server")
                .vaultCode("vault-code")
                .vaultSecret("vault-secret")
                .build();
        
        when(severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino()))
                .thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(1L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        
        try {
            when(vaultClient.obterCredenciais(any(), any()))
                    .thenReturn(new VaultClient.Credenciais("user", "pass"));
        } catch (Exception e) {
            // Ignore
        }
    }
}
