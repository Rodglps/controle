package com.controle.arquivos.orchestrator.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.*;
import com.controle.arquivos.common.domain.enums.*;
import com.controle.arquivos.common.repository.*;
import com.controle.arquivos.common.service.JobConcurrencyService;
import com.controle.arquivos.orchestrator.dto.ConfiguracaoServidor;
import com.controle.arquivos.orchestrator.dto.MensagemProcessamento;
import com.controle.arquivos.orchestrator.messaging.RabbitMQPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes de integração para OrquestradorService usando mocks.
 * 
 * Testa o fluxo completo de orquestração sem dependências externas:
 * 1. Carregar configurações do banco
 * 2. Listar arquivos no SFTP (mockado)
 * 3. Registrar arquivos no banco
 * 4. Publicar mensagens no RabbitMQ (mockado)
 * 5. Controlar concorrência
 * 
 * **Valida: Requisitos 1.1, 2.1, 3.1, 4.1, 5.1**
 */
@ExtendWith(MockitoExtension.class)
class OrquestradorServiceIntegrationTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private SeverPathsRepository severPathsRepository;

    @Mock
    private SeverPathsInOutRepository severPathsInOutRepository;

    @Mock
    private FileOriginRepository fileOriginRepository;

    @Mock
    private SFTPClient sftpClient;

    @Mock
    private VaultClient vaultClient;

    @Mock
    private RabbitMQPublisher rabbitMQPublisher;

    @Mock
    private JobConcurrencyService jobConcurrencyService;

    private OrquestradorService orquestradorService;

    @BeforeEach
    void setup() {
        orquestradorService = new OrquestradorService(
                serverRepository,
                severPathsRepository,
                severPathsInOutRepository,
                fileOriginRepository,
                sftpClient,
                vaultClient,
                rabbitMQPublisher,
                jobConcurrencyService
        );
    }

    /**
     * Testa o fluxo completo de orquestração com mocks.
     * 
     * **Valida: Requisitos 1.1, 2.1, 3.1, 4.1, 5.1**
     */
    @Test
    void deveExecutarFluxoCompletoDeOrquestracaoComMocks() {
        // Arrange: Setup mock data
        Server servidorOrigem = criarServidorSFTP();
        Server servidorDestino = criarServidorS3();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        SeverPathsInOut mapeamento = criarMapeamento(caminhoOrigem.getId(), servidorDestino.getId());

        // Mock repository responses
        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        // Mock VaultClient
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);

        // Mock SFTP file listing
        List<SFTPClient.ArquivoMetadata> arquivosSFTP = Arrays.asList(
                new SFTPClient.ArquivoMetadata("CIELO_20240115_001.txt", 1000L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("CIELO_20240115_002.txt", 2000L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("REDE_20240115_001.txt", 1500L, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos(anyString())).thenReturn(arquivosSFTP);

        // Mock file deduplication check (no duplicates)
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(anyString(), anyLong(), any(Instant.class)))
                .thenReturn(Optional.empty());

        // Mock file save
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin arquivo = invocation.getArgument(0);
            arquivo.setId(1L); // Simulate ID generation
            return arquivo;
        });

        // Mock job concurrency control
        when(jobConcurrencyService.verificarExecucaoAtiva(anyString())).thenReturn(false);
        when(jobConcurrencyService.iniciarExecucao(anyString())).thenReturn(1L);

        // Act: Execute collection cycle
        orquestradorService.executarCicloColeta();

        // Assert: Verify configuration loading
        verify(serverRepository).findAll();
        verify(severPathsRepository).findAll();
        verify(severPathsInOutRepository).findAll();

        // Assert: Verify SFTP connection
        verify(vaultClient).obterCredenciais("sftp-vault", "secret/sftp");
        verify(sftpClient).conectar(eq("sftp-server"), eq(22), eq(credenciais));
        verify(sftpClient).listarArquivos("/upload/test");
        verify(sftpClient).desconectar();

        // Assert: Verify file registration (3 files)
        verify(fileOriginRepository, times(3)).save(any(FileOrigin.class));

        // Assert: Verify message publication (3 messages)
        ArgumentCaptor<MensagemProcessamento> mensagemCaptor = ArgumentCaptor.forClass(MensagemProcessamento.class);
        verify(rabbitMQPublisher, times(3)).publicar(mensagemCaptor.capture());

        List<MensagemProcessamento> mensagens = mensagemCaptor.getAllValues();
        assertThat(mensagens).hasSize(3);
        assertThat(mensagens.get(0).getNomeArquivo()).isEqualTo("CIELO_20240115_001.txt");
        assertThat(mensagens.get(1).getNomeArquivo()).isEqualTo("CIELO_20240115_002.txt");
        assertThat(mensagens.get(2).getNomeArquivo()).isEqualTo("REDE_20240115_001.txt");

        // Assert: Verify job concurrency control
        verify(jobConcurrencyService).verificarExecucaoAtiva("ORCHESTRATOR_FILE_COLLECTION");
        verify(jobConcurrencyService).iniciarExecucao("ORCHESTRATOR_FILE_COLLECTION");
        verify(jobConcurrencyService).finalizarExecucao("ORCHESTRATOR_FILE_COLLECTION", true);
        verify(jobConcurrencyService).registrarDataExecucao("ORCHESTRATOR_FILE_COLLECTION");
    }

    /**
     * Testa deduplicação de arquivos.
     * 
     * **Valida: Requisito 2.3**
     */
    @Test
    void deveIgnorarArquivosDuplicados() {
        // Arrange
        Server servidorOrigem = criarServidorSFTP();
        Server servidorDestino = criarServidorS3();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        SeverPathsInOut mapeamento = criarMapeamento(caminhoOrigem.getId(), servidorDestino.getId());

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);

        List<SFTPClient.ArquivoMetadata> arquivosSFTP = Arrays.asList(
                new SFTPClient.ArquivoMetadata("CIELO_20240115_001.txt", 1000L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("CIELO_20240115_002.txt", 2000L, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos(anyString())).thenReturn(arquivosSFTP);

        // Mock: First file is duplicate, second is new
        FileOrigin arquivoExistente = FileOrigin.builder()
                .id(1L)
                .fileName("CIELO_20240115_001.txt")
                .build();

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                eq("CIELO_20240115_001.txt"), anyLong(), any(Instant.class)))
                .thenReturn(Optional.of(arquivoExistente));

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                eq("CIELO_20240115_002.txt"), anyLong(), any(Instant.class)))
                .thenReturn(Optional.empty());

        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin arquivo = invocation.getArgument(0);
            arquivo.setId(2L);
            return arquivo;
        });

        when(jobConcurrencyService.verificarExecucaoAtiva(anyString())).thenReturn(false);
        when(jobConcurrencyService.iniciarExecucao(anyString())).thenReturn(1L);

        // Act
        orquestradorService.executarCicloColeta();

        // Assert: Only 1 file should be saved (the non-duplicate)
        verify(fileOriginRepository, times(1)).save(any(FileOrigin.class));

        // Assert: Only 1 message should be published
        verify(rabbitMQPublisher, times(1)).publicar(any(MensagemProcessamento.class));
    }

    /**
     * Testa controle de concorrência - execução bloqueada.
     * 
     * **Valida: Requisitos 5.1, 5.2**
     */
    @Test
    void deveBloquearExecucaoQuandoHaExecucaoAtiva() {
        // Arrange: Simulate active execution
        when(jobConcurrencyService.verificarExecucaoAtiva("ORCHESTRATOR_FILE_COLLECTION"))
                .thenReturn(true);

        // Act
        orquestradorService.executarCicloColeta();

        // Assert: No processing should occur
        verify(serverRepository, never()).findAll();
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
        verify(fileOriginRepository, never()).save(any());
        verify(rabbitMQPublisher, never()).publicar(any());

        // Assert: Job should not be started
        verify(jobConcurrencyService, never()).iniciarExecucao(anyString());
    }

    /**
     * Testa tratamento de erro de conexão SFTP.
     * 
     * **Valida: Requisito 2.5**
     */
    @Test
    void deveContinuarAposErroDeConexaoSFTP() {
        // Arrange
        Server servidorOrigem = criarServidorSFTP();
        Server servidorDestino = criarServidorS3();
        SeverPaths caminhoOrigem = criarCaminhoOrigem(servidorOrigem.getId(), 1L);
        SeverPathsInOut mapeamento = criarMapeamento(caminhoOrigem.getId(), servidorDestino.getId());

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);

        // Mock SFTP connection failure
        doThrow(new SFTPClient.SFTPException("Connection failed"))
                .when(sftpClient).conectar(anyString(), anyInt(), any());

        when(jobConcurrencyService.verificarExecucaoAtiva(anyString())).thenReturn(false);
        when(jobConcurrencyService.iniciarExecucao(anyString())).thenReturn(1L);

        // Act
        orquestradorService.executarCicloColeta();

        // Assert: Job should complete despite SFTP error
        verify(jobConcurrencyService).finalizarExecucao("ORCHESTRATOR_FILE_COLLECTION", true);
        verify(jobConcurrencyService).registrarDataExecucao("ORCHESTRATOR_FILE_COLLECTION");

        // Assert: No files should be registered
        verify(fileOriginRepository, never()).save(any());
        verify(rabbitMQPublisher, never()).publicar(any());
    }

    // Helper methods

    private Server criarServidorSFTP() {
        return Server.builder()
                .id(1L)
                .serverCode("sftp-server:22")
                .vaultCode("sftp-vault")
                .vaultSecret("secret/sftp")
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .active(true)
                .build();
    }

    private Server criarServidorS3() {
        return Server.builder()
                .id(2L)
                .serverCode("s3-bucket")
                .vaultCode("s3-vault")
                .vaultSecret("secret/s3")
                .serverType(TipoServidor.S3)
                .serverOrigin(OrigemServidor.INTERNO)
                .active(true)
                .build();
    }

    private SeverPaths criarCaminhoOrigem(Long serverId, Long acquirerId) {
        return SeverPaths.builder()
                .id(1L)
                .serverId(serverId)
                .acquirerId(acquirerId)
                .path("/upload/test")
                .pathType(TipoCaminho.ORIGIN)
                .active(true)
                .build();
    }

    private SeverPathsInOut criarMapeamento(Long originPathId, Long destinationServerId) {
        return SeverPathsInOut.builder()
                .id(1L)
                .severPathOriginId(originPathId)
                .severDestinationId(destinationServerId)
                .linkType(TipoLink.PRINCIPAL)
                .active(true)
                .build();
    }
}
