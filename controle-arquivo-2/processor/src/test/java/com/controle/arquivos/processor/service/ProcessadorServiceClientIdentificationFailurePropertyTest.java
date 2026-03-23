package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.*;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.*;
import com.controle.arquivos.common.service.*;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import net.jqwik.api.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para tratamento de falha de identificação de cliente.
 * 
 * Feature: controle-de-arquivos, Property 15: Tratamento de Falha de Identificação de Cliente
 * 
 * Para qualquer arquivo onde nenhum cliente for identificado, o Processador deve registrar
 * erro e atualizar status para ERRO.
 * 
 * **Valida: Requisitos 8.5**
 */
class ProcessadorServiceClientIdentificationFailurePropertyTest {

    private ProcessadorService processadorService;
    private SFTPClient sftpClient;
    private VaultClient vaultClient;
    private FileOriginRepository fileOriginRepository;
    private SeverPathsInOutRepository severPathsInOutRepository;
    private SeverPathsRepository severPathsRepository;
    private ServerRepository serverRepository;
    private FileOriginClientRepository fileOriginClientRepository;
    private FileOriginClientProcessingRepository processingRepository;
    private CustomerIdentificationRuleRepository customerRuleRepository;
    private LayoutIdentificationRuleRepository layoutRuleRepository;
    private ClienteIdentificationService clienteIdentificationService;
    private LayoutIdentificationService layoutIdentificationService;
    private StreamingTransferService streamingTransferService;
    private RastreabilidadeService rastreabilidadeService;

    /**
     * **Propriedade 15: Tratamento de Falha de Identificação de Cliente**
     * **Valida: Requisitos 8.5**
     * 
     * Para qualquer arquivo onde nenhum cliente é identificado,
     * o sistema deve registrar erro e atualizar status para ERRO.
     */
    @Property(tries = 100)
    void falhaIdentificacaoClienteDeveRegistrarErro(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivo") long tamanhoArquivo) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        configurarMocksParaDownload(mensagem, fileOrigin);

        // Simular que nenhum cliente foi identificado
        when(clienteIdentificationService.identificar(mensagem.getNomeArquivo(), fileOrigin.getAcquirerId()))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Cliente não identificado");

        // Verificar que erro foi registrado na rastreabilidade
        verify(rastreabilidadeService, atLeastOnce()).atualizarStatus(
            any(),
            eq(StatusProcessamento.ERRO),
            contains("Cliente não identificado")
        );
    }

    /**
     * **Propriedade 15: Tratamento de Falha de Identificação de Cliente**
     * **Valida: Requisitos 8.5**
     * 
     * Para qualquer arquivo onde a identificação de cliente lança exceção,
     * o sistema deve registrar erro e atualizar status para ERRO.
     */
    @Property(tries = 100)
    void excecaoNaIdentificacaoClienteDeveRegistrarErro(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivo") long tamanhoArquivo,
            @ForAll("mensagemErro") String mensagemErro) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        configurarMocksParaDownload(mensagem, fileOrigin);

        // Simular exceção durante identificação
        when(clienteIdentificationService.identificar(mensagem.getNomeArquivo(), fileOrigin.getAcquirerId()))
            .thenThrow(new RuntimeException(mensagemErro));

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que erro foi registrado na rastreabilidade
        verify(rastreabilidadeService, atLeastOnce()).atualizarStatus(
            any(),
            eq(StatusProcessamento.ERRO),
            anyString()
        );
    }

    /**
     * **Propriedade 15: Tratamento de Falha de Identificação de Cliente**
     * **Valida: Requisitos 8.5**
     * 
     * Para qualquer arquivo onde cliente não é identificado,
     * o sistema NÃO deve prosseguir para identificação de layout.
     */
    @Property(tries = 100)
    void falhaIdentificacaoClienteNaoDeveProsseguirParaLayout(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivo") long tamanhoArquivo) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        configurarMocksParaDownload(mensagem, fileOrigin);

        // Simular que nenhum cliente foi identificado
        when(clienteIdentificationService.identificar(mensagem.getNomeArquivo(), fileOrigin.getAcquirerId()))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que identificação de layout NÃO foi chamada
        verify(layoutIdentificationService, never()).identificar(anyString(), any(), any(), any());
        
        // Verificar que upload NÃO foi chamado
        verify(streamingTransferService, never()).transferirSFTPparaS3(any(), anyString(), anyString(), anyLong());
        verify(streamingTransferService, never()).transferirSFTPparaSFTP(any(), any(), anyString(), anyLong());
    }

    /**
     * **Propriedade 15: Tratamento de Falha de Identificação de Cliente**
     * **Valida: Requisitos 8.5**
     * 
     * Para qualquer arquivo onde cliente não é identificado,
     * o sistema deve registrar a etapa em que ocorreu o erro (STAGING).
     */
    @Property(tries = 100)
    void falhaIdentificacaoClienteDeveRegistrarEtapaCorreta(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivo") long tamanhoArquivo) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        configurarMocksParaDownload(mensagem, fileOrigin);

        // Simular que nenhum cliente foi identificado
        when(clienteIdentificationService.identificar(mensagem.getNomeArquivo(), fileOrigin.getAcquirerId()))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que etapa STAGING foi registrada (onde ocorre identificação de cliente)
        verify(rastreabilidadeService, atLeastOnce()).registrarEtapa(
            any(),
            eq(EtapaProcessamento.STAGING),
            any()
        );
    }

    /**
     * **Propriedade 15: Tratamento de Falha de Identificação de Cliente**
     * **Valida: Requisitos 8.5**
     * 
     * Para qualquer combinação de nome de arquivo e adquirente onde nenhum cliente
     * é identificado, o sistema deve sempre registrar erro.
     */
    @Property(tries = 100)
    void qualquerArquivoSemClienteDeveRegistrarErro(
            @ForAll("nomeArquivoQualquer") String nomeArquivo,
            @ForAll("idAdquirente") Long idAdquirente,
            @ForAll("idFileOrigin") Long idFileOrigin) throws Exception {
        
        // Arrange
        setupMocks();
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(idFileOrigin)
            .nomeArquivo(nomeArquivo)
            .idMapeamentoOrigemDestino(1L)
            .correlationId("corr-" + idFileOrigin)
            .build();
        
        FileOrigin fileOrigin = criarFileOriginComAdquirente(idFileOrigin, nomeArquivo, idAdquirente);
        configurarMocksParaDownload(mensagem, fileOrigin);

        // Simular que nenhum cliente foi identificado (independente do nome/adquirente)
        when(clienteIdentificationService.identificar(nomeArquivo, idAdquirente))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que erro foi registrado
        verify(rastreabilidadeService, atLeastOnce()).atualizarStatus(
            any(),
            eq(StatusProcessamento.ERRO),
            anyString()
        );
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<MensagemProcessamento> mensagemValida() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 1000000L),
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50).map(s -> s + ".txt"),
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
    Arbitrary<Long> tamanhoArquivo() {
        return Arbitraries.longs().between(1L, 10 * 1024 * 1024); // 1 byte a 10MB
    }

    @Provide
    Arbitrary<String> mensagemErro() {
        return Arbitraries.of(
            "Erro ao conectar ao banco de dados",
            "Timeout ao buscar regras",
            "Regra inválida encontrada",
            "Erro inesperado no serviço",
            "Falha ao processar critério"
        );
    }

    @Provide
    Arbitrary<String> nomeArquivoQualquer() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(5)
            .ofMaxLength(100);
    }

    @Provide
    Arbitrary<Long> idAdquirente() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Long> idFileOrigin() {
        return Arbitraries.longs().between(1L, 1000000L);
    }

    // ========== Helper Methods ==========

    private void setupMocks() {
        sftpClient = mock(SFTPClient.class);
        vaultClient = mock(VaultClient.class);
        fileOriginRepository = mock(FileOriginRepository.class);
        severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        severPathsRepository = mock(SeverPathsRepository.class);
        serverRepository = mock(ServerRepository.class);
        fileOriginClientRepository = mock(FileOriginClientRepository.class);
        processingRepository = mock(FileOriginClientProcessingRepository.class);
        customerRuleRepository = mock(CustomerIdentificationRuleRepository.class);
        layoutRuleRepository = mock(LayoutIdentificationRuleRepository.class);
        clienteIdentificationService = mock(ClienteIdentificationService.class);
        layoutIdentificationService = mock(LayoutIdentificationService.class);
        streamingTransferService = mock(StreamingTransferService.class);
        rastreabilidadeService = mock(RastreabilidadeService.class);

        processadorService = new ProcessadorService(
                sftpClient,
                vaultClient,
                fileOriginRepository,
                severPathsInOutRepository,
                severPathsRepository,
                serverRepository,
                fileOriginClientRepository,
                processingRepository,
                customerRuleRepository,
                layoutRuleRepository,
                clienteIdentificationService,
                layoutIdentificationService,
                streamingTransferService,
                rastreabilidadeService
        );
    }

    private void configurarMocksParaDownload(MensagemProcessamento mensagem, FileOrigin fileOrigin) throws Exception {
        SeverPathsInOut pathsInOut = criarPathsInOut(mensagem.getIdMapeamentoOrigemDestino());
        SeverPaths severPath = criarSeverPath(pathsInOut.getSeverPathOriginId());
        Server server = criarServer(severPath.getServerId());
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream(new byte[(int) fileOrigin.getFileSize()]);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino()))
            .thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(pathsInOut.getSeverPathOriginId()))
            .thenReturn(Optional.of(severPath));
        when(serverRepository.findById(severPath.getServerId()))
            .thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(anyString(), anyString()))
            .thenReturn(credenciais);
        when(sftpClient.obterInputStream(anyString()))
            .thenReturn(mockInputStream);
        
        // Mock rastreabilidade para não lançar exceções
        doNothing().when(rastreabilidadeService).registrarEtapa(any(), any(), any());
        doNothing().when(rastreabilidadeService).atualizarStatus(any(), any(), anyString());
    }

    private FileOrigin criarFileOrigin(Long id, String fileName, long fileSize) {
        return FileOrigin.builder()
                .id(id)
                .acquirerId(1L)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private FileOrigin criarFileOriginComAdquirente(Long id, String fileName, Long acquirerId) {
        return FileOrigin.builder()
                .id(id)
                .acquirerId(acquirerId)
                .fileName(fileName)
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SeverPathsInOut criarPathsInOut(Long id) {
        return SeverPathsInOut.builder()
                .id(id)
                .severPathOriginId(100L)
                .severDestinationId(200L)
                .linkType("PRINCIPAL")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SeverPaths criarSeverPath(Long id) {
        return SeverPaths.builder()
                .id(id)
                .serverId(1000L)
                .acquirerId(1L)
                .path("/sftp/origem")
                .pathType("ORIGIN")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Server criarServer(Long id) {
        return Server.builder()
                .id(id)
                .serverCode("SFTP-SERVER-01")
                .vaultCode("vault-code-123")
                .vaultSecret("secret/sftp/credentials")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
