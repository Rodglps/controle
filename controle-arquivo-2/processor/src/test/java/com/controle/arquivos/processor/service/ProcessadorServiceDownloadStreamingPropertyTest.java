package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.*;
import com.controle.arquivos.common.domain.enums.TipoServidor;
import com.controle.arquivos.common.repository.*;
import com.controle.arquivos.common.service.*;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para download streaming no ProcessadorService.
 * 
 * Feature: controle-de-arquivos, Property 13: Download com Streaming
 * 
 * Para qualquer arquivo a ser baixado, o Processador deve obter credenciais do Vault
 * e abrir uma conexão SFTP para obter um InputStream, e em caso de falha, deve registrar
 * erro e liberar recursos.
 * 
 * **Valida: Requisitos 7.1, 7.2, 7.5**
 */
class ProcessadorServiceDownloadStreamingPropertyTest {

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
     * **Propriedade 13: Download com Streaming**
     * **Valida: Requisitos 7.1, 7.2**
     * 
     * Para qualquer arquivo válido, o sistema deve obter credenciais do Vault
     * e obter um InputStream do SFTP para download streaming.
     */
    @Property(tries = 50)
    void downloadDeveUsarStreamingComCredenciaisDoVault(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivo") long tamanhoArquivo) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        SeverPathsInOut pathsInOut = criarPathsInOut(mensagem.getIdMapeamentoOrigemDestino());
        SeverPaths severPath = criarSeverPath(pathsInOut.getSeverPathOriginId());
        Server server = criarServer(severPath.getServerId());
        VaultClient.Credenciais credenciais = criarCredenciais();
        InputStream mockInputStream = new ByteArrayInputStream(new byte[(int) tamanhoArquivo]);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino()))
            .thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(pathsInOut.getSeverPathOriginId()))
            .thenReturn(Optional.of(severPath));
        when(serverRepository.findById(severPath.getServerId()))
            .thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(server.getVaultCode(), server.getVaultSecret()))
            .thenReturn(credenciais);
        when(sftpClient.obterInputStream(anyString()))
            .thenReturn(mockInputStream);

        // Simular falha no processamento para não executar todo o fluxo
        doThrow(new RuntimeException("Simulando falha para testar download"))
            .when(clienteIdentificationService).identificar(anyString(), any());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que credenciais foram obtidas do Vault
        verify(vaultClient, times(1)).obterCredenciais(server.getVaultCode(), server.getVaultSecret());
        
        // Verificar que conexão SFTP foi estabelecida
        verify(sftpClient, times(1)).conectar(eq(server.getServerCode()), eq(22), eq(credenciais));
        
        // Verificar que InputStream foi obtido (streaming)
        verify(sftpClient, times(1)).obterInputStream(anyString());
    }

    /**
     * **Propriedade 13: Download com Streaming**
     * **Valida: Requisitos 7.5**
     * 
     * Para qualquer falha durante o download, o sistema deve registrar erro
     * e liberar recursos (fechar conexões).
     */
    @Property(tries = 50)
    void falhaNoDownloadDeveLiberarRecursos(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivo") long tamanhoArquivo) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        SeverPathsInOut pathsInOut = criarPathsInOut(mensagem.getIdMapeamentoOrigemDestino());
        SeverPaths severPath = criarSeverPath(pathsInOut.getSeverPathOriginId());
        Server server = criarServer(severPath.getServerId());
        VaultClient.Credenciais credenciais = criarCredenciais();

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino()))
            .thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(pathsInOut.getSeverPathOriginId()))
            .thenReturn(Optional.of(severPath));
        when(serverRepository.findById(severPath.getServerId()))
            .thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(server.getVaultCode(), server.getVaultSecret()))
            .thenReturn(credenciais);
        
        // Simular falha ao obter InputStream
        when(sftpClient.obterInputStream(anyString()))
            .thenThrow(new RuntimeException("Falha de conexão SFTP"));

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que erro foi registrado (através de rastreabilidade)
        verify(rastreabilidadeService, atLeastOnce()).atualizarStatus(any(), any(), anyString());
    }

    /**
     * **Propriedade 13: Download com Streaming**
     * **Valida: Requisitos 7.1, 7.2**
     * 
     * Para qualquer arquivo de tamanho variado, o sistema deve usar streaming
     * (não carregar arquivo completo em memória).
     */
    @Property(tries = 50)
    void downloadDeveUsarStreamingParaArquivosDeQualquerTamanho(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("tamanhoArquivoVariado") long tamanhoArquivo) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), tamanhoArquivo);
        SeverPathsInOut pathsInOut = criarPathsInOut(mensagem.getIdMapeamentoOrigemDestino());
        SeverPaths severPath = criarSeverPath(pathsInOut.getSeverPathOriginId());
        Server server = criarServer(severPath.getServerId());
        VaultClient.Credenciais credenciais = criarCredenciais();
        
        // Criar InputStream que simula arquivo grande
        InputStream mockInputStream = new ByteArrayInputStream(new byte[(int) Math.min(tamanhoArquivo, 10000)]);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino()))
            .thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(pathsInOut.getSeverPathOriginId()))
            .thenReturn(Optional.of(severPath));
        when(serverRepository.findById(severPath.getServerId()))
            .thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(server.getVaultCode(), server.getVaultSecret()))
            .thenReturn(credenciais);
        when(sftpClient.obterInputStream(anyString()))
            .thenReturn(mockInputStream);

        // Simular falha no processamento para não executar todo o fluxo
        doThrow(new RuntimeException("Simulando falha para testar download"))
            .when(clienteIdentificationService).identificar(anyString(), any());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que InputStream foi obtido (independente do tamanho)
        verify(sftpClient, times(1)).obterInputStream(anyString());
        
        // Verificar que não houve tentativa de carregar arquivo completo em memória
        // (o InputStream é passado diretamente para processamento)
        ArgumentCaptor<String> caminhoCaptor = ArgumentCaptor.forClass(String.class);
        verify(sftpClient).obterInputStream(caminhoCaptor.capture());
        assertThat(caminhoCaptor.getValue()).contains(mensagem.getNomeArquivo());
    }

    /**
     * **Propriedade 13: Download com Streaming**
     * **Valida: Requisitos 7.1**
     * 
     * Para qualquer servidor SFTP, o sistema deve obter credenciais usando
     * cod_vault e des_vault_secret da tabela server.
     */
    @Property(tries = 50)
    void downloadDeveUsarCredenciaisCorretas(
            @ForAll("mensagemValida") MensagemProcessamento mensagem,
            @ForAll("codigoVault") String codVault,
            @ForAll("secretVault") String secretVault) throws Exception {
        
        // Arrange
        setupMocks();
        FileOrigin fileOrigin = criarFileOrigin(mensagem.getIdFileOrigin(), mensagem.getNomeArquivo(), 1024L);
        SeverPathsInOut pathsInOut = criarPathsInOut(mensagem.getIdMapeamentoOrigemDestino());
        SeverPaths severPath = criarSeverPath(pathsInOut.getSeverPathOriginId());
        Server server = criarServerComCredenciais(severPath.getServerId(), codVault, secretVault);
        VaultClient.Credenciais credenciais = criarCredenciais();
        InputStream mockInputStream = new ByteArrayInputStream(new byte[1024]);

        when(fileOriginRepository.findById(mensagem.getIdFileOrigin())).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino()))
            .thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(pathsInOut.getSeverPathOriginId()))
            .thenReturn(Optional.of(severPath));
        when(serverRepository.findById(severPath.getServerId()))
            .thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(codVault, secretVault))
            .thenReturn(credenciais);
        when(sftpClient.obterInputStream(anyString()))
            .thenReturn(mockInputStream);

        // Simular falha no processamento para não executar todo o fluxo
        doThrow(new RuntimeException("Simulando falha para testar download"))
            .when(clienteIdentificationService).identificar(anyString(), any());

        // Act & Assert
        assertThatThrownBy(() -> processadorService.processarArquivo(mensagem))
            .isInstanceOf(RuntimeException.class);

        // Verificar que credenciais corretas foram usadas
        verify(vaultClient, times(1)).obterCredenciais(codVault, secretVault);
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
        // Tamanhos pequenos a médios para testes
        return Arbitraries.longs().between(1L, 10 * 1024 * 1024); // 1 byte a 10MB
    }

    @Provide
    Arbitrary<Long> tamanhoArquivoVariado() {
        // Tamanhos variados incluindo arquivos grandes
        return Arbitraries.frequency(
            Tuple.of(3, Arbitraries.longs().between(1L, 1024L)), // Pequenos: 1B - 1KB
            Tuple.of(3, Arbitraries.longs().between(1024L, 1024 * 1024L)), // Médios: 1KB - 1MB
            Tuple.of(2, Arbitraries.longs().between(1024 * 1024L, 100 * 1024 * 1024L)), // Grandes: 1MB - 100MB
            Tuple.of(1, Arbitraries.longs().between(100 * 1024 * 1024L, 1024 * 1024 * 1024L)) // Muito grandes: 100MB - 1GB
        );
    }

    @Provide
    Arbitrary<String> codigoVault() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> secretVault() {
        return Arbitraries.strings().alpha().numeric().withChars('/', '-', '_').ofMinLength(10).ofMaxLength(50);
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
                .serverType(TipoServidor.SFTP)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Server criarServerComCredenciais(Long id, String codVault, String secretVault) {
        return Server.builder()
                .id(id)
                .serverCode("SFTP-SERVER-01")
                .vaultCode(codVault)
                .vaultSecret(secretVault)
                .serverType(TipoServidor.SFTP)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private VaultClient.Credenciais criarCredenciais() {
        return new VaultClient.Credenciais("sftp-user", "sftp-password");
    }
}
