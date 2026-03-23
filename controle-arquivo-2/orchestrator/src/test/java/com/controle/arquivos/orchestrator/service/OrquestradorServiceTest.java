package com.controle.arquivos.orchestrator.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import com.controle.arquivos.common.domain.enums.OrigemServidor;
import com.controle.arquivos.common.domain.enums.TipoCaminho;
import com.controle.arquivos.common.domain.enums.TipoLink;
import com.controle.arquivos.common.domain.enums.TipoServidor;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.common.repository.ServerRepository;
import com.controle.arquivos.common.repository.SeverPathsInOutRepository;
import com.controle.arquivos.common.repository.SeverPathsRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para OrquestradorService.
 * 
 * **Valida: Requisitos 2.3, 3.1, 3.3, 3.4, 3.5, 5.1, 5.2, 5.3, 5.4, 5.5**
 */
@ExtendWith(MockitoExtension.class)
class OrquestradorServiceTest {

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

    private OrquestradorService service;

    @BeforeEach
    void setUp() {
        service = new OrquestradorService(
                serverRepository,
                severPathsRepository,
                severPathsInOutRepository,
                fileOriginRepository,
                sftpClient,
                vaultClient,
                rabbitMQPublisher,
                jobConcurrencyService
        );
        
        // Setup default concurrency control mocks for all tests
        when(jobConcurrencyService.verificarExecucaoAtiva(any())).thenReturn(false);
        when(jobConcurrencyService.iniciarExecucao(any())).thenReturn(1L);
    }

    // ========== Tests for executarCicloColeta - Complete Cycle ==========

    /**
     * Testa que execução é cancelada quando já existe execução RUNNING.
     * 
     * **Valida: Requisitos 5.1, 5.2**
     */
    @Test
    void executarCicloColeta_deveCancelarQuandoExisteExecucaoAtiva() {
        // Arrange
        when(jobConcurrencyService.verificarExecucaoAtiva(any())).thenReturn(true);

        // Act
        service.executarCicloColeta();

        // Assert
        verify(jobConcurrencyService).verificarExecucaoAtiva(any());
        verify(jobConcurrencyService, never()).iniciarExecucao(any());
        verify(serverRepository, never()).findAll();
        verify(sftpClient, never()).conectar(any(), anyInt(), any());
    }

    /**
     * Testa que controle de concorrência é atualizado para PENDING em caso de falha.
     * 
     * **Valida: Requisitos 5.3, 5.5**
     */
    @Test
    void executarCicloColeta_deveAtualizarParaPendingEmCasoDeFalha() {
        // Arrange
        when(jobConcurrencyService.verificarExecucaoAtiva(any())).thenReturn(false);
        when(jobConcurrencyService.iniciarExecucao(any())).thenReturn(1L);
        
        // Simular erro crítico ao carregar configurações
        when(serverRepository.findAll()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> service.executarCicloColeta());
        
        verify(jobConcurrencyService).verificarExecucaoAtiva(any());
        verify(jobConcurrencyService).iniciarExecucao(any());
        verify(jobConcurrencyService).finalizarExecucao(any(), eq(false));
        verify(jobConcurrencyService, never()).registrarDataExecucao(any());
    }

    /**
     * Testa ciclo completo de coleta com mocks.
     * Deve carregar configurações, conectar ao SFTP, listar arquivos e registrar novos arquivos.
     */
    @Test
    void executarCicloColeta_deveConcluirCicloCompletoComSucesso() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        FileOrigin savedFile1 = FileOrigin.builder().id(1L).build();
        FileOrigin savedFile2 = FileOrigin.builder().id(2L).build();
        when(fileOriginRepository.save(any(FileOrigin.class)))
                .thenReturn(savedFile1)
                .thenReturn(savedFile2);

        // Act
        service.executarCicloColeta();

        // Assert
        verify(jobConcurrencyService).verificarExecucaoAtiva(any());
        verify(jobConcurrencyService).iniciarExecucao(any());
        verify(vaultClient).obterCredenciais("vault1", "secret/sftp1");
        verify(sftpClient).conectar("sftp.example.com", 22, credenciais);
        verify(sftpClient).listarArquivos("/incoming");
        verify(fileOriginRepository, times(2)).save(any(FileOrigin.class));
        verify(sftpClient).desconectar();
        verify(jobConcurrencyService).finalizarExecucao(any(), eq(true));
        verify(jobConcurrencyService).registrarDataExecucao(any());
    }

    /**
     * Testa ciclo de coleta quando não há configurações válidas.
     * Deve abortar o ciclo sem tentar conectar ao SFTP.
     */
    @Test
    void executarCicloColeta_deveAbortarQuandoNaoHaConfiguracoesValidas() {
        // Arrange
        when(serverRepository.findAll()).thenReturn(new ArrayList<>());
        when(severPathsRepository.findAll()).thenReturn(new ArrayList<>());
        when(severPathsInOutRepository.findAll()).thenReturn(new ArrayList<>());

        // Act
        service.executarCicloColeta();

        // Assert
        verify(jobConcurrencyService).verificarExecucaoAtiva(any());
        verify(jobConcurrencyService).iniciarExecucao(any());
        verify(vaultClient, never()).obterCredenciais(any(), any());
        verify(sftpClient, never()).conectar(any(), anyInt(), any());
        verify(fileOriginRepository, never()).save(any());
        verify(jobConcurrencyService).finalizarExecucao(any(), eq(true));
        verify(jobConcurrencyService).registrarDataExecucao(any());
    }

    /**
     * Testa ciclo de coleta quando erro ocorre ao obter credenciais do Vault.
     * Deve registrar erro e continuar sem lançar exceção.
     */
    @Test
    void executarCicloColeta_deveContinuarQuandoErroAoObterCredenciais() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        when(vaultClient.obterCredenciais("vault1", "secret/sftp1"))
                .thenThrow(new VaultClient.VaultException("Vault error"));

        // Act - não deve lançar exceção
        assertDoesNotThrow(() -> service.executarCicloColeta());

        // Assert
        verify(sftpClient, never()).conectar(any(), anyInt(), any());
        verify(fileOriginRepository, never()).save(any());
        verify(jobConcurrencyService).finalizarExecucao(any(), eq(true));
    }

    /**
     * Testa ciclo de coleta quando erro ocorre ao conectar ao SFTP.
     * Deve registrar erro e continuar sem lançar exceção.
     */
    @Test
    void executarCicloColeta_deveContinuarQuandoErroAoConectarSFTP() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        doThrow(new SFTPClient.SFTPException("Connection failed"))
                .when(sftpClient).conectar("sftp.example.com", 22, credenciais);

        // Act - não deve lançar exceção
        assertDoesNotThrow(() -> service.executarCicloColeta());

        // Assert
        verify(sftpClient).conectar("sftp.example.com", 22, credenciais);
        verify(sftpClient, never()).listarArquivos(any());
        verify(fileOriginRepository, never()).save(any());
        verify(jobConcurrencyService).finalizarExecucao(any(), eq(true));
    }

    // ========== Tests for Deduplication ==========

    /**
     * Testa deduplicação de arquivos.
     * Arquivos que já existem no banco devem ser ignorados.
     */
    @Test
    void executarCicloColeta_deveIgnorarArquivosDuplicados() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        long timestamp = System.currentTimeMillis();
        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, timestamp),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, timestamp)
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        // file1.txt já existe (duplicado)
        FileOrigin existente = FileOrigin.builder().id(999L).build();
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                eq("file1.txt"), eq(100L), any(Instant.class)))
                .thenReturn(Optional.of(existente));

        // file2.txt não existe (novo)
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                eq("file2.txt"), eq(100L), any(Instant.class)))
                .thenReturn(Optional.empty());

        FileOrigin savedFile = FileOrigin.builder().id(1L).build();
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(savedFile);

        // Act
        service.executarCicloColeta();

        // Assert
        // Apenas file2.txt deve ser salvo (file1.txt é duplicado)
        verify(fileOriginRepository, times(1)).save(any(FileOrigin.class));
        
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());
        
        FileOrigin captured = captor.getValue();
        assertEquals("file2.txt", captured.getFileName());
        assertEquals(2048L, captured.getFileSize());
    }

    // ========== Tests for File Origin Registration ==========

    /**
     * Testa registro em file_origin com todos os campos corretos.
     * Deve incluir des_file_name, num_file_size, dat_timestamp_file e idt_sever_paths_in_out.
     */
    @Test
    void executarCicloColeta_deveRegistrarArquivoComTodosCampos() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC
        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("test_file.txt", 5120L, timestamp)
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        FileOrigin savedFile = FileOrigin.builder().id(1L).build();
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(savedFile);

        // Act
        service.executarCicloColeta();

        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());

        FileOrigin captured = captor.getValue();
        assertEquals("test_file.txt", captured.getFileName());
        assertEquals(5120L, captured.getFileSize());
        assertEquals(Instant.ofEpochMilli(timestamp), captured.getFileTimestamp());
        assertEquals(100L, captured.getAcquirerId());
        assertEquals(20L, captured.getSeverPathsInOutId());
        assertTrue(captured.getActive());
    }

    // ========== Tests for Uniqueness Violation Handling ==========

    /**
     * Testa tratamento de violação de unicidade.
     * Quando DataIntegrityViolationException ocorre, deve registrar alerta e continuar.
     */
    @Test
    void executarCicloColeta_deveTratarViolacaoDeUnicidade() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        long timestamp = System.currentTimeMillis();
        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, timestamp),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, timestamp)
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        // Primeira tentativa de salvar lança violação de unicidade
        when(fileOriginRepository.save(any(FileOrigin.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"))
                .thenReturn(FileOrigin.builder().id(2L).build());

        // Act - não deve lançar exceção
        assertDoesNotThrow(() -> service.executarCicloColeta());

        // Assert
        // Ambos os arquivos devem tentar ser salvos
        verify(fileOriginRepository, times(2)).save(any(FileOrigin.class));
    }

    /**
     * Testa que violação de unicidade não impede processamento de outros arquivos.
     * Deve continuar processando arquivos subsequentes.
     */
    @Test
    void executarCicloColeta_deveContinuarProcessandoAposViolacaoDeUnicidade() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        long timestamp = System.currentTimeMillis();
        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, timestamp),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, timestamp),
                new SFTPClient.ArquivoMetadata("file3.txt", 3072L, timestamp)
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        // file1.txt: sucesso
        // file2.txt: violação de unicidade
        // file3.txt: sucesso
        when(fileOriginRepository.save(any(FileOrigin.class)))
                .thenReturn(FileOrigin.builder().id(1L).build())
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"))
                .thenReturn(FileOrigin.builder().id(3L).build());

        // Act
        service.executarCicloColeta();

        // Assert
        // Todos os 3 arquivos devem tentar ser salvos
        verify(fileOriginRepository, times(3)).save(any(FileOrigin.class));
    }

    /**
     * Testa que SFTP é sempre desconectado, mesmo quando ocorrem erros.
     * Deve garantir liberação de recursos.
     */
    @Test
    void executarCicloColeta_deveDesconectarSFTPMesmoComErros() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        // Simular erro ao listar arquivos
        when(sftpClient.listarArquivos("/incoming"))
                .thenThrow(new SFTPClient.SFTPException("List error"));

        // Act
        service.executarCicloColeta();

        // Assert
        verify(sftpClient).conectar("sftp.example.com", 22, credenciais);
        verify(sftpClient).desconectar();
    }

    // ========== Tests for RabbitMQ Publishing Integration ==========

    /**
     * Testa que mensagens são publicadas após registro de arquivos.
     * Deve publicar uma mensagem para cada arquivo coletado com correlationId único.
     * 
     * **Valida: Requisitos 4.1, 4.2, 4.4**
     */
    @Test
    void executarCicloColeta_devePublicarMensagensParaArquivosColetados() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        FileOrigin savedFile1 = FileOrigin.builder()
                .id(1L)
                .fileName("file1.txt")
                .severPathsInOutId(20L)
                .build();
        FileOrigin savedFile2 = FileOrigin.builder()
                .id(2L)
                .fileName("file2.txt")
                .severPathsInOutId(20L)
                .build();
        when(fileOriginRepository.save(any(FileOrigin.class)))
                .thenReturn(savedFile1)
                .thenReturn(savedFile2);

        // Act
        service.executarCicloColeta();

        // Assert
        ArgumentCaptor<MensagemProcessamento> captor = ArgumentCaptor.forClass(MensagemProcessamento.class);
        verify(rabbitMQPublisher, times(2)).publicar(captor.capture());

        List<MensagemProcessamento> mensagens = captor.getAllValues();
        
        // Verificar primeira mensagem
        MensagemProcessamento msg1 = mensagens.get(0);
        assertEquals(1L, msg1.getIdFileOrigin());
        assertEquals("file1.txt", msg1.getNomeArquivo());
        assertEquals(20L, msg1.getIdMapeamentoOrigemDestino());
        assertNotNull(msg1.getCorrelationId());
        assertFalse(msg1.getCorrelationId().isEmpty());
        
        // Verificar segunda mensagem
        MensagemProcessamento msg2 = mensagens.get(1);
        assertEquals(2L, msg2.getIdFileOrigin());
        assertEquals("file2.txt", msg2.getNomeArquivo());
        assertEquals(20L, msg2.getIdMapeamentoOrigemDestino());
        assertNotNull(msg2.getCorrelationId());
        assertFalse(msg2.getCorrelationId().isEmpty());
        
        // Verificar que correlationIds são únicos
        assertNotEquals(msg1.getCorrelationId(), msg2.getCorrelationId());
    }

    /**
     * Testa que mensagens não são publicadas para arquivos duplicados.
     * Apenas arquivos novos devem gerar mensagens.
     * 
     * **Valida: Requisitos 2.3, 4.1**
     */
    @Test
    void executarCicloColeta_naoDevePublicarMensagensParaArquivosDuplicados() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        long timestamp = System.currentTimeMillis();
        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, timestamp),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, timestamp)
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        // file1.txt já existe (duplicado)
        FileOrigin existente = FileOrigin.builder().id(999L).build();
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                eq("file1.txt"), eq(100L), any(Instant.class)))
                .thenReturn(Optional.of(existente));

        // file2.txt não existe (novo)
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                eq("file2.txt"), eq(100L), any(Instant.class)))
                .thenReturn(Optional.empty());

        FileOrigin savedFile = FileOrigin.builder()
                .id(1L)
                .fileName("file2.txt")
                .severPathsInOutId(20L)
                .build();
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(savedFile);

        // Act
        service.executarCicloColeta();

        // Assert
        // Apenas 1 mensagem deve ser publicada (para file2.txt)
        ArgumentCaptor<MensagemProcessamento> captor = ArgumentCaptor.forClass(MensagemProcessamento.class);
        verify(rabbitMQPublisher, times(1)).publicar(captor.capture());

        MensagemProcessamento msg = captor.getValue();
        assertEquals(1L, msg.getIdFileOrigin());
        assertEquals("file2.txt", msg.getNomeArquivo());
    }

    /**
     * Testa tratamento de falha de publicação.
     * Quando RabbitMQPublisher lança exceção (após 3 tentativas), deve registrar erro crítico.
     * 
     * **Valida: Requisitos 4.5**
     */
    @Test
    void executarCicloColeta_deveTratarFalhaDePublicacao() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        FileOrigin savedFile = FileOrigin.builder()
                .id(1L)
                .fileName("file1.txt")
                .severPathsInOutId(20L)
                .build();
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(savedFile);

        // Simular falha de publicação (após 3 tentativas no RabbitMQPublisher)
        doThrow(new RuntimeException("RabbitMQ connection failed"))
                .when(rabbitMQPublisher).publicar(any(MensagemProcessamento.class));

        // Act - não deve lançar exceção
        assertDoesNotThrow(() -> service.executarCicloColeta());

        // Assert
        verify(rabbitMQPublisher).publicar(any(MensagemProcessamento.class));
        // Ciclo deve continuar mesmo com falha de publicação
        verify(sftpClient).desconectar();
    }

    /**
     * Testa que falha de publicação não impede processamento de outros arquivos.
     * Deve continuar publicando mensagens para arquivos subsequentes.
     * 
     * **Valida: Requisitos 4.5**
     */
    @Test
    void executarCicloColeta_deveContinuarPublicandoAposFalha() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        List<SFTPClient.ArquivoMetadata> arquivos = Arrays.asList(
                new SFTPClient.ArquivoMetadata("file1.txt", 1024L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("file2.txt", 2048L, System.currentTimeMillis()),
                new SFTPClient.ArquivoMetadata("file3.txt", 3072L, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos("/incoming")).thenReturn(arquivos);

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(any(), any(), any()))
                .thenReturn(Optional.empty());

        FileOrigin savedFile1 = FileOrigin.builder().id(1L).fileName("file1.txt").severPathsInOutId(20L).build();
        FileOrigin savedFile2 = FileOrigin.builder().id(2L).fileName("file2.txt").severPathsInOutId(20L).build();
        FileOrigin savedFile3 = FileOrigin.builder().id(3L).fileName("file3.txt").severPathsInOutId(20L).build();
        when(fileOriginRepository.save(any(FileOrigin.class)))
                .thenReturn(savedFile1)
                .thenReturn(savedFile2)
                .thenReturn(savedFile3);

        // file1: sucesso
        // file2: falha de publicação
        // file3: sucesso
        doNothing()
                .doThrow(new RuntimeException("RabbitMQ connection failed"))
                .doNothing()
                .when(rabbitMQPublisher).publicar(any(MensagemProcessamento.class));

        // Act
        service.executarCicloColeta();

        // Assert
        // Todas as 3 mensagens devem tentar ser publicadas
        verify(rabbitMQPublisher, times(3)).publicar(any(MensagemProcessamento.class));
    }

    /**
     * Testa que nenhuma mensagem é publicada quando nenhum arquivo é coletado.
     * 
     * **Valida: Requisitos 4.1**
     */
    @Test
    void executarCicloColeta_naoDevePublicarQuandoNenhumArquivoColetado() {
        // Arrange
        Server servidorOrigem = criarServidorOrigem(1L, "sftp.example.com:22", "vault1", "secret/sftp1");
        Server servidorDestino = criarServidorDestino(2L, "s3.amazonaws.com", "vault2", "secret/s3");
        SeverPaths caminhoOrigem = criarCaminhoOrigem(10L, 1L, 100L, "/incoming");
        SeverPathsInOut mapeamento = criarMapeamento(20L, 10L, 2L);

        when(serverRepository.findAll()).thenReturn(Arrays.asList(servidorOrigem, servidorDestino));
        when(severPathsRepository.findAll()).thenReturn(Arrays.asList(caminhoOrigem));
        when(severPathsInOutRepository.findAll()).thenReturn(Arrays.asList(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault1", "secret/sftp1")).thenReturn(credenciais);

        // Nenhum arquivo no servidor SFTP
        when(sftpClient.listarArquivos("/incoming")).thenReturn(new ArrayList<>());

        // Act
        service.executarCicloColeta();

        // Assert
        verify(rabbitMQPublisher, never()).publicar(any(MensagemProcessamento.class));
    }

    // ========== Helper Methods ==========

    private Server criarServidorOrigem(Long id, String serverCode, String vaultCode, String vaultSecret) {
        return Server.builder()
                .id(id)
                .serverCode(serverCode)
                .vaultCode(vaultCode)
                .vaultSecret(vaultSecret)
                .serverType(TipoServidor.SFTP)
                .serverOrigin(OrigemServidor.EXTERNO)
                .active(true)
                .build();
    }

    private Server criarServidorDestino(Long id, String serverCode, String vaultCode, String vaultSecret) {
        return Server.builder()
                .id(id)
                .serverCode(serverCode)
                .vaultCode(vaultCode)
                .vaultSecret(vaultSecret)
                .serverType(TipoServidor.S3)
                .serverOrigin(OrigemServidor.INTERNO)
                .active(true)
                .build();
    }

    private SeverPaths criarCaminhoOrigem(Long id, Long serverId, Long acquirerId, String path) {
        return SeverPaths.builder()
                .id(id)
                .serverId(serverId)
                .acquirerId(acquirerId)
                .path(path)
                .pathType(TipoCaminho.ORIGIN)
                .active(true)
                .build();
    }

    private SeverPathsInOut criarMapeamento(Long id, Long originPathId, Long destinationServerId) {
        return SeverPathsInOut.builder()
                .id(id)
                .severPathOriginId(originPathId)
                .severDestinationId(destinationServerId)
                .linkType(TipoLink.PRINCIPAL)
                .active(true)
                .build();
    }
}
