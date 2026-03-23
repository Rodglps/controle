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
 * Testes de propriedade para tratamento de falha de identificação de layout.
 * 
 * Feature: controle-de-arquivos, Property 18: Tratamento de Falha de Identificação de Layout
 * 
 * Para qualquer arquivo onde nenhum layout for identificado, o Processador deve registrar
 * erro e atualizar status para ERRO.
 * 
 * **Valida: Requisitos 9.6**
 */
class ProcessadorServiceLayoutIdentificationFailurePropertyTest {

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
     * **Propriedade 18: Tratamento de Falha de Identificação de Layout**
     * **Valida: Requisitos 9.6**
     * 
     * Para qualquer arquivo onde nenhum layout é identificado,
     * o sistema deve registrar erro e atualizar status para ERRO.
     */
    @Property(tries = 100)
    void falhaIdentificacaoLayoutDeveRegistrarErro(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("idCliente") Long idCliente) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        configurarMocksParaDownloadECliente(mensagem, fileOrigin, idCliente);

        // Simular que nenhum layout foi identificado
        when(layoutIdentificationService.identificar(
            eq(mensagem.getNomeArquivo()),
            any(InputStream.class),
            eq(idCliente),
            eq(fileOrigin.getAcquirerId())
        )).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Layout não identificado");

        // Verificar que erro foi registrado na rastreabilidade
        verify(rastreabilidadeService, atLeastOnce()).atualizarStatus(
            any(),
            eq(StatusProcessamento.ERRO),
            contains("Layout não identificado")
        );
    }

    /**
     * **Propriedade 18: Tratamento de Falha de Identificação de Layout**
     * **Valida: Requisitos 9.6**
     * 
     * Para qualquer arquivo onde layout não é identificado,
     * o sistema NÃO deve prosseguir para upload.
     */
    @Property(tries = 100)
    void falhaIdentificacaoLayoutNaoDeveProsseguirParaUpload(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("idCliente") Long idCliente) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        configurarMocksParaDownloadECliente(mensagem, fileOrigin, idCliente);

        // Simular que nenhum layout foi identificado
        when(layoutIdentificationService.identificar(
            anyString(),
            any(InputStream.class),
            any(),
            any()
        )).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que upload NÃO foi chamado
        verify(streamingTransferService, never()).transferirSFTPparaS3(any(), anyString(), anyString(), anyLong());
        verify(streamingTransferService, never()).transferirSFTPparaSFTP(any(), any(), anyString(), anyLong());
    }

    /**
     * **Propriedade 18: Tratamento de Falha de Identificação de Layout**
     * **Valida: Requisitos 9.6**
     * 
     * Para qualquer arquivo onde a identificação de layout lança exceção,
     * o sistema deve registrar erro e atualizar status para ERRO.
     */
    @Property(tries = 100)
    void excecaoNaIdentificacaoLayoutDeveRegistrarErro(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("idCliente") Long idCliente,
            @ForAll("mensagemErro") String mensagemErro) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        configurarMocksParaDownloadECliente(mensagem, fileOrigin, idCliente);

        // Simular exceção durante identificação de layout
        when(layoutIdentificationService.identificar(
            anyString(),
            any(InputStream.class),
            any(),
            any()
        )).thenThrow(new RuntimeException(mensagemErro));

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
     * **Propriedade 18: Tratamento de Falha de Identificação de Layout**
     * **Valida: Requisitos 9.6**
     * 
     * Para qualquer arquivo onde layout não é identificado,
     * o sistema deve registrar a etapa em que ocorreu o erro (ORDINATION).
     */
    @Property(tries = 100)
    void falhaIdentificacaoLayoutDeveRegistrarEtapaCorreta(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("idCliente") Long idCliente) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
        configurarMocksParaDownloadECliente(mensagem, fileOrigin, idCliente);

        // Simular que nenhum layout foi identificado
        when(layoutIdentificationService.identificar(
            anyString(),
            any(InputStream.class),
            any(),
            any()
        )).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que etapa ORDINATION foi registrada (onde ocorre identificação de layout)
        verify(rastreabilidadeService, atLeastOnce()).registrarEtapa(
            any(),
            eq(EtapaProcessamento.ORDINATION),
            any()
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
    Arbitrary<Long> idCliente() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> mensagemErro() {
        return Arbitraries.of(
            "Erro ao ler header do arquivo",
            "Timeout ao buscar regras de layout",
            "Regra de layout inválida",
            "Erro inesperado no serviço de layout",
            "Falha ao processar critério de layout"
        );
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

    private void configurarMocksParaDownloadECliente(MensagemProcessamento mensagem, FileOrigin fileOrigin, Long idCliente) throws Exception {
        SeverPathsInOut pathsInOut = criarPathsInOut(mensagem.getIdMapeamentoOrigemDestino());
        SeverPaths severPath = criarSeverPath(pathsInOut.getSeverPathOriginId());
        Server server = criarServer(severPath.getServerId());
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream(new byte[1024]);
        CustomerIdentification cliente = criarCliente(idCliente);
        FileOriginClient fileOriginClient = criarFileOriginClient(fileOrigin.getId(), idCliente);

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
        
        // Simular identificação de cliente bem-sucedida
        when(clienteIdentificationService.identificar(mensagem.getNomeArquivo(), fileOrigin.getAcquirerId()))
            .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any())).thenReturn(fileOriginClient);
        
        // Mock rastreabilidade para não lançar exceções
        doNothing().when(rastreabilidadeService).registrarEtapa(any(), any(), any());
        doNothing().when(rastreabilidadeService).atualizarStatus(any(), any(), anyString());
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

    private CustomerIdentification criarCliente(Long id) {
        return CustomerIdentification.builder()
                .id(id)
                .clientCode("CLIENT-" + id)
                .clientName("Cliente " + id)
                .processingWeight(100)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private FileOriginClient criarFileOriginClient(Long fileOriginId, Long clientId) {
        return FileOriginClient.builder()
                .id(1L)
                .fileOriginId(fileOriginId)
                .clientId(clientId)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
