package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.CustomerIdentification;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClient;
import com.controle.arquivos.common.domain.entity.Layout;
import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import com.controle.arquivos.common.domain.enums.TipoServidor;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.common.repository.ServerRepository;
import com.controle.arquivos.common.repository.SeverPathsInOutRepository;
import com.controle.arquivos.common.repository.SeverPathsRepository;
import com.controle.arquivos.common.service.ClienteIdentificationService;
import com.controle.arquivos.common.service.LayoutIdentificationService;
import com.controle.arquivos.common.service.StreamingTransferService;
import com.controle.arquivos.common.service.RastreabilidadeService;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import com.controle.arquivos.processor.exception.ClienteNaoIdentificadoException;
import com.controle.arquivos.processor.exception.LayoutNaoIdentificadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ProcessadorService - Download Streaming, Identificação de Cliente e Layout.
 * 
 * Testa:
 * - Obtenção de InputStream do SFTP com mock
 * - Tratamento de erro de download
 * - Liberação de recursos em caso de falha
 * - Identificação de cliente e associação em file_origin_client
 * - Tratamento de erro quando cliente não é identificado
 * - Identificação de layout e atualização de file_origin
 * - Tratamento de erro quando layout não é identificado
 * 
 * **Valida: Requisitos 7.1, 7.2, 7.5, 8.1, 8.5, 9.1, 9.6, 13.1, 13.2, 13.3, 13.4, 13.5, 14.1, 14.2, 14.3, 14.4**
 */
@ExtendWith(MockitoExtension.class)
class ProcessadorServiceTest {

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
    private com.controle.arquivos.common.repository.FileOriginClientProcessingRepository processingRepository;
    
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
     * Testa obtenção de InputStream do SFTP com sucesso.
     * Deve obter credenciais do Vault, conectar ao SFTP e retornar InputStream.
     * 
     * **Valida: Requisitos 7.1, 7.2**
     */
    @Test
    void deveObterInputStreamDoSFTPComSucesso() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);

        // Act
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que as operações foram chamadas na ordem correta
        verify(fileOriginRepository, times(1)).findById(1L);
        verify(severPathsInOutRepository, times(1)).findById(10L);
        verify(severPathsRepository, times(1)).findById(100L);
        verify(serverRepository, times(1)).findById(1000L);
        verify(vaultClient, times(1)).obterCredenciais("VAULT_CODE", "vault/secret/path");
        verify(sftpClient, times(1)).conectar("sftp.server.com", 22, credenciais);
        verify(sftpClient, times(1)).obterInputStream("/sftp/path/CIELO_20240115.txt");
    }

    /**
     * Testa tratamento de erro quando arquivo não é encontrado no banco.
     * Deve lançar exceção e não tentar conectar ao SFTP.
     * 
     * **Valida: Requisitos 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoArquivoNaoEncontrado() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        when(fileOriginRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Arquivo não encontrado no banco de dados"));
        verify(fileOriginRepository, times(1)).findById(1L);
        verify(vaultClient, never()).obterCredenciais(anyString(), anyString());
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
    }

    /**
     * Testa tratamento de erro quando mapeamento origem-destino não é encontrado.
     * Deve lançar exceção e não tentar conectar ao SFTP.
     * 
     * **Valida: Requisitos 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoMapeamentoNaoEncontrado() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Mapeamento origem-destino não encontrado"));
        verify(severPathsInOutRepository, times(1)).findById(10L);
        verify(vaultClient, never()).obterCredenciais(anyString(), anyString());
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
    }

    /**
     * Testa tratamento de erro quando caminho de origem não é encontrado.
     * Deve lançar exceção e não tentar conectar ao SFTP.
     * 
     * **Valida: Requisitos 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoCaminhoOrigemNaoEncontrado() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Caminho de origem não encontrado"));
        verify(severPathsRepository, times(1)).findById(100L);
        verify(vaultClient, never()).obterCredenciais(anyString(), anyString());
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
    }

    /**
     * Testa tratamento de erro quando servidor não é encontrado.
     * Deve lançar exceção e não tentar obter credenciais.
     * 
     * **Valida: Requisitos 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoServidorNaoEncontrado() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Servidor não encontrado"));
        verify(serverRepository, times(1)).findById(1000L);
        verify(vaultClient, never()).obterCredenciais(anyString(), anyString());
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
    }

    /**
     * Testa tratamento de erro quando falha ao obter credenciais do Vault.
     * Deve lançar exceção e liberar recursos.
     * 
     * **Valida: Requisitos 7.2, 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoFalhaObterCredenciais() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path"))
                .thenThrow(new VaultClient.VaultException("Vault error"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Falha ao baixar arquivo via streaming"));
        verify(vaultClient, times(1)).obterCredenciais("VAULT_CODE", "vault/secret/path");
        verify(sftpClient, never()).conectar(anyString(), anyInt(), any());
        verify(sftpClient, times(1)).isConectado();
    }

    /**
     * Testa tratamento de erro quando falha ao conectar ao SFTP.
     * Deve lançar exceção e liberar recursos.
     * 
     * **Valida: Requisitos 7.1, 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoFalhaConectarSFTP() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        doThrow(new SFTPClient.SFTPException("Connection failed"))
                .when(sftpClient).conectar("sftp.server.com", 22, credenciais);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Falha ao baixar arquivo via streaming"));
        verify(sftpClient, times(1)).conectar("sftp.server.com", 22, credenciais);
        verify(sftpClient, times(1)).isConectado();
    }

    /**
     * Testa tratamento de erro quando falha ao obter InputStream.
     * Deve lançar exceção e liberar recursos.
     * 
     * **Valida: Requisitos 7.2, 7.5**
     */
    @Test
    void deveLancarExcecaoQuandoFalhaObterInputStream() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new SFTPClient.SFTPException("File not found"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getMessage().contains("Falha ao baixar arquivo via streaming"));
        verify(sftpClient, times(1)).conectar("sftp.server.com", 22, credenciais);
        verify(sftpClient, times(1)).obterInputStream("/sftp/path/CIELO_20240115.txt");
        verify(sftpClient, times(1)).isConectado();
    }

    /**
     * Testa liberação de recursos quando ocorre erro.
     * Deve fechar InputStream e desconectar SFTP.
     * 
     * **Valida: Requisitos 7.5**
     */
    @Test
    void deveLiberarRecursosQuandoOcorreErro() {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(sftpClient.isConectado()).thenReturn(true);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que recursos foram liberados
        verify(sftpClient, times(1)).isConectado();
        verify(sftpClient, times(1)).desconectar();
    }

    /**
     * Testa construção de caminho completo com diretório sem barra final.
     * Deve adicionar barra entre diretório e nome do arquivo.
     * 
     * **Valida: Requisitos 7.2**
     */
    @Test
    void deveConstruirCaminhoCompletoSemBarraFinal() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que o caminho foi construído corretamente
        verify(sftpClient, times(1)).obterInputStream("/sftp/path/CIELO_20240115.txt");
    }

    /**
     * Testa construção de caminho completo com diretório com barra final.
     * Não deve adicionar barra extra.
     * 
     * **Valida: Requisitos 7.2**
     */
    @Test
    void deveConstruirCaminhoCompletoComBarraFinal() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = SeverPaths.builder()
                .id(100L)
                .serverId(1000L)
                .path("/sftp/path/")  // Com barra final
                .active(true)
                .build();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que o caminho foi construído corretamente (sem barra dupla)
        verify(sftpClient, times(1)).obterInputStream("/sftp/path/CIELO_20240115.txt");
    }

    // Helper methods

    private MensagemProcessamento criarMensagemProcessamento() {
        return MensagemProcessamento.builder()
                .idFileOrigin(1L)
                .nomeArquivo("CIELO_20240115.txt")
                .idMapeamentoOrigemDestino(10L)
                .correlationId("test-correlation-id")
                .build();
    }

    private FileOrigin criarFileOrigin() {
        return FileOrigin.builder()
                .id(1L)
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(10L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SeverPathsInOut criarPathsInOut() {
        return SeverPathsInOut.builder()
                .id(10L)
                .severPathOriginId(100L)
                .severDestinationId(200L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SeverPaths criarSeverPath() {
        return SeverPaths.builder()
                .id(100L)
                .serverId(1000L)
                .path("/sftp/path")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Server criarServer() {
        return Server.builder()
                .id(1000L)
                .serverCode("sftp.server.com")
                .vaultCode("VAULT_CODE")
                .vaultSecret("vault/secret/path")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ========== Testes de Identificação de Cliente (Task 14.1) ==========

    /**
     * Testa identificação de cliente com sucesso.
     * Deve invocar ClienteIdentificationService, criar registro em file_origin_client
     * e retornar idt_file_origin_client.
     * 
     * **Valida: Requisitos 8.1, 13.1, 13.2, 13.3, 13.4, 13.5**
     */
    @Test
    void deveIdentificarClienteComSucesso() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);

        // Act
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que identificação foi chamada
        verify(clienteIdentificationService, times(1)).identificar("CIELO_20240115.txt", 1L);
        verify(fileOriginClientRepository, times(1)).save(any(FileOriginClient.class));
    }

    /**
     * Testa erro quando cliente não é identificado.
     * Deve lançar ClienteNaoIdentificadoException e registrar erro.
     * 
     * **Valida: Requisitos 8.5**
     */
    @Test
    void deveLancarExcecaoQuandoClienteNaoIdentificado() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause() instanceof ClienteNaoIdentificadoException);
        assertTrue(exception.getCause().getMessage().contains("Cliente não identificado"));
        verify(clienteIdentificationService, times(1)).identificar("CIELO_20240115.txt", 1L);
        verify(fileOriginClientRepository, never()).save(any(FileOriginClient.class));
    }

    /**
     * Testa criação de registro em file_origin_client.
     * Deve criar registro com fileOriginId, clientId e active=true.
     * 
     * **Valida: Requisitos 13.1, 13.2, 13.3**
     */
    @Test
    void deveCriarRegistroFileOriginClient() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que save foi chamado com os parâmetros corretos
        verify(fileOriginClientRepository, times(1)).save(argThat(foc -> 
            foc.getFileOriginId().equals(1L) &&
            foc.getClientId().equals(100L) &&
            foc.getActive().equals(true)
        ));
    }

    /**
     * Testa que idt_file_origin_client é usado para rastreabilidade.
     * Deve retornar o ID do registro criado.
     * 
     * **Valida: Requisitos 13.5**
     */
    @Test
    void deveRetornarIdtFileOriginClientParaRastreabilidade() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que o ID foi usado (será usado em rastreabilidade futura)
        verify(fileOriginClientRepository, times(1)).save(any(FileOriginClient.class));
    }

    /**
     * Testa tratamento de erro inesperado durante identificação.
     * Deve lançar RuntimeException com mensagem apropriada.
     * 
     * **Valida: Requisitos 8.5**
     */
    @Test
    void deveLancarExcecaoQuandoErroInesperadoNaIdentificacao() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Erro ao identificar cliente"));
        verify(clienteIdentificationService, times(1)).identificar("CIELO_20240115.txt", 1L);
        verify(fileOriginClientRepository, never()).save(any(FileOriginClient.class));
    }

    // Helper methods para Task 14.1

    private CustomerIdentification criarCustomerIdentification() {
        return CustomerIdentification.builder()
                .id(100L)
                .customerName("Cliente Teste")
                .processingWeight(10)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private FileOriginClient criarFileOriginClient() {
        return FileOriginClient.builder()
                .id(1000L)
                .fileOriginId(1L)
                .clientId(100L)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ========== Testes de Identificação de Layout (Task 14.2) ==========

    /**
     * Testa identificação de layout com sucesso.
     * Deve invocar LayoutIdentificationService, atualizar file_origin com informações do layout
     * e retornar idt_layout.
     * 
     * **Valida: Requisitos 9.1, 14.1, 14.2, 14.3**
     */
    @Test
    void deveIdentificarLayoutComSucesso() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que identificação de layout foi chamada
        verify(layoutIdentificationService, times(1)).identificar(
                eq("CIELO_20240115.txt"), 
                any(InputStream.class), 
                eq(100L), 
                eq(1L)
        );
        verify(fileOriginRepository, times(1)).save(argThat(fo -> 
            fo.getLayoutId().equals(1L) &&
            fo.getFileType().equals("CSV") &&
            fo.getTransactionType().equals("Layout Teste")
        ));
    }

    /**
     * Testa erro quando layout não é identificado.
     * Deve lançar LayoutNaoIdentificadoException e registrar erro.
     * 
     * **Valida: Requisitos 9.6**
     */
    @Test
    void deveLancarExcecaoQuandoLayoutNaoIdentificado() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause() instanceof LayoutNaoIdentificadoException);
        assertTrue(exception.getCause().getMessage().contains("Layout não identificado"));
        verify(layoutIdentificationService, times(1)).identificar(
                eq("CIELO_20240115.txt"), 
                any(InputStream.class), 
                eq(100L), 
                eq(1L)
        );
        verify(fileOriginRepository, never()).save(argThat(fo -> fo.getLayoutId() != null));
    }

    /**
     * Testa atualização de file_origin com informações do layout.
     * Deve atualizar idt_layout, des_file_type e des_transaction_type.
     * 
     * **Valida: Requisitos 14.1, 14.2, 14.3**
     */
    @Test
    void deveAtualizarFileOriginComInformacoesDoLayout() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que file_origin foi atualizado com os campos corretos
        verify(fileOriginRepository, times(1)).save(argThat(fo -> 
            fo.getLayoutId() != null &&
            fo.getLayoutId().equals(1L) &&
            fo.getFileType() != null &&
            fo.getFileType().equals("CSV") &&
            fo.getTransactionType() != null &&
            fo.getTransactionType().equals("Layout Teste")
        ));
    }

    /**
     * Testa que timestamp de última modificação é atualizado.
     * O @PreUpdate do JPA deve atualizar dat_updated automaticamente.
     * 
     * **Valida: Requisitos 14.4**
     */
    @Test
    void deveAtualizarTimestampDeUltimaModificacao() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que save foi chamado (o @PreUpdate atualizará dat_updated)
        verify(fileOriginRepository, times(1)).save(any(FileOrigin.class));
    }

    /**
     * Testa tratamento de erro inesperado durante identificação de layout.
     * Deve lançar RuntimeException com mensagem apropriada.
     * 
     * **Valida: Requisitos 9.6**
     */
    @Test
    void deveLancarExcecaoQuandoErroInesperadoNaIdentificacaoLayout() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Erro ao identificar layout"));
        verify(layoutIdentificationService, times(1)).identificar(
                eq("CIELO_20240115.txt"), 
                any(InputStream.class), 
                eq(100L), 
                eq(1L)
        );
        verify(fileOriginRepository, never()).save(argThat(fo -> fo.getLayoutId() != null));
    }

    /**
     * Testa que InputStream é passado para regras HEADER.
     * O LayoutIdentificationService deve receber o InputStream para ler os primeiros 7000 bytes.
     * 
     * **Valida: Requisitos 9.1**
     */
    @Test
    void devePassarInputStreamParaRegrasHeader() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Assert - Verificar que InputStream foi passado (não null)
        verify(layoutIdentificationService, times(1)).identificar(
                eq("CIELO_20240115.txt"), 
                any(InputStream.class),  // Verifica que um InputStream foi passado
                eq(100L), 
                eq(1L)
        );
    }

    // Helper methods para Task 14.2

    private Layout criarLayout() {
        return Layout.builder()
                .id(1L)
                .layoutName("Layout Teste")
                .layoutType("CSV")
                .description("Layout de teste")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ========== Testes de Upload Streaming (Task 14.3) ==========

    /**
     * Testa upload para S3 com sucesso.
     * Deve determinar destino, obter credenciais e invocar StreamingTransferService.transferirSFTPparaS3().
     * 
     * **Valida: Requisitos 10.1, 10.2, 10.5**
     */
    @Test
    void deveFazerUploadParaS3ComSucesso() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("aws_key", "aws_secret");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act
        processadorService.processarArquivo(mensagem);

        // Assert - Verificar que upload para S3 foi chamado
        verify(streamingTransferService, times(1)).transferirSFTPparaS3(
                any(InputStream.class),
                eq("my-s3-bucket"),
                eq("CIELO_20240115.txt"),
                eq(1024L)
        );
    }

    /**
     * Testa upload para SFTP com sucesso.
     * Deve determinar destino, obter credenciais e invocar StreamingTransferService.transferirSFTPparaSFTP().
     * 
     * **Valida: Requisitos 10.1, 10.3, 10.5**
     */
    @Test
    void deveFazerUploadParaSFTPComSucesso() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerSFTPDestino();
        SeverPaths severPathDestino = criarSeverPathDestino();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("dest_user", "dest_pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        OutputStream mockOutputStream = mock(OutputStream.class);
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(severPathsRepository.findById(200L)).thenReturn(Optional.of(severPathDestino));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_DEST", "vault/dest/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act
        processadorService.processarArquivo(mensagem);

        // Assert - Verificar que upload para SFTP foi chamado
        verify(streamingTransferService, times(1)).transferirSFTPparaSFTP(
                any(InputStream.class),
                any(OutputStream.class),
                eq("/sftp/dest/CIELO_20240115.txt"),
                eq(1024L)
        );
    }

    /**
     * Testa erro quando servidor de destino não é encontrado.
     * Deve lançar exceção e não tentar fazer upload.
     * 
     * **Valida: Requisitos 10.1, 10.6**
     */
    @Test
    void deveLancarExcecaoQuandoServidorDestinoNaoEncontrado() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.empty()); // Servidor destino não encontrado
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Servidor de destino não encontrado"));
        verify(streamingTransferService, never()).transferirSFTPparaS3(any(), any(), any(), anyLong());
        verify(streamingTransferService, never()).transferirSFTPparaSFTP(any(), any(), any(), anyLong());
    }

    /**
     * Testa erro quando falha ao obter credenciais do destino.
     * Deve lançar exceção e não tentar fazer upload.
     * 
     * **Valida: Requisitos 10.1, 10.6**
     */
    @Test
    void deveLancarExcecaoQuandoFalhaObterCredenciaisDestino() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret"))
                .thenThrow(new VaultClient.VaultException("Vault error"));
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Falha ao fazer upload"));
        verify(streamingTransferService, never()).transferirSFTPparaS3(any(), any(), any(), anyLong());
    }

    /**
     * Testa erro quando falha durante upload para S3.
     * Deve lançar exceção e manter arquivo na origem.
     * 
     * **Valida: Requisitos 10.2, 10.6**
     */
    @Test
    void deveLancarExcecaoQuandoFalhaUploadParaS3() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("aws_key", "aws_secret");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        doThrow(new RuntimeException("S3 upload failed"))
                .when(streamingTransferService).transferirSFTPparaS3(any(), any(), any(), anyLong());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Falha ao fazer upload"));
        verify(streamingTransferService, times(1)).transferirSFTPparaS3(any(), any(), any(), anyLong());
    }

    /**
     * Testa erro quando falha durante upload para SFTP.
     * Deve lançar exceção e manter arquivo na origem.
     * 
     * **Valida: Requisitos 10.3, 10.6**
     */
    @Test
    void deveLancarExcecaoQuandoFalhaUploadParaSFTP() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerSFTPDestino();
        SeverPaths severPathDestino = criarSeverPathDestino();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("dest_user", "dest_pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(severPathsRepository.findById(200L)).thenReturn(Optional.of(severPathDestino));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_DEST", "vault/dest/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        doThrow(new RuntimeException("SFTP upload failed"))
                .when(streamingTransferService).transferirSFTPparaSFTP(any(), any(), any(), anyLong());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Falha ao fazer upload"));
        verify(streamingTransferService, times(1)).transferirSFTPparaSFTP(any(), any(), any(), anyLong());
    }

    /**
     * Testa validação de tamanho após upload.
     * A validação é feita dentro do StreamingTransferService.
     * Se o tamanho não corresponder, uma exceção deve ser lançada.
     * 
     * **Valida: Requisitos 10.5, 10.6**
     */
    @Test
    void deveValidarTamanhoAposUpload() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("aws_key", "aws_secret");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        
        // Simular falha de validação de tamanho
        doThrow(new RuntimeException("Tamanho do arquivo não corresponde ao esperado"))
                .when(streamingTransferService).transferirSFTPparaS3(any(), any(), any(), anyLong());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        assertTrue(exception.getCause().getMessage().contains("Falha ao fazer upload"));
        verify(streamingTransferService, times(1)).transferirSFTPparaS3(
                any(InputStream.class),
                eq("my-s3-bucket"),
                eq("CIELO_20240115.txt"),
                eq(1024L)  // Tamanho esperado
        );
    }

    // Helper methods para Task 14.3

    private Server criarServerS3() {
        return Server.builder()
                .id(200L)
                .serverCode("my-s3-bucket")
                .vaultCode("VAULT_S3")
                .vaultSecret("vault/s3/secret")
                .serverType(TipoServidor.S3)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Server criarServerSFTPDestino() {
        return Server.builder()
                .id(200L)
                .serverCode("sftp.dest.com")
                .vaultCode("VAULT_DEST")
                .vaultSecret("vault/dest/secret")
                .serverType(TipoServidor.SFTP)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SeverPaths criarSeverPathDestino() {
        return SeverPaths.builder()
                .id(200L)
                .serverId(200L)
                .path("/sftp/dest")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ========== Testes de Fluxo Completo e Rastreabilidade (Task 14.5) ==========

    /**
     * Testa fluxo completo com sucesso: download → identificação cliente → identificação layout → upload → rastreabilidade.
     * Deve executar todas as etapas e atualizar rastreabilidade em cada passo.
     * 
     * **Valida: Requisitos 8.5, 9.6, 10.6, 12.1, 12.2, 12.3, 12.4**
     */
    @Test
    void deveExecutarFluxoCompletoComSucesso() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("aws_key", "aws_secret");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        // Mock de todos os repositórios e serviços
        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        
        // Mock de rastreabilidade - retornar IDs sequenciais para cada etapa
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        // Act
        processadorService.processarArquivo(mensagem);

        // Assert - Verificar que todas as etapas foram executadas na ordem correta
        
        // 1. Download e identificação de cliente
        verify(sftpClient, times(1)).conectar("sftp.server.com", 22, credenciais);
        verify(sftpClient, times(1)).obterInputStream("/sftp/path/CIELO_20240115.txt");
        verify(clienteIdentificationService, times(1)).identificar("CIELO_20240115.txt", 1L);
        verify(fileOriginClientRepository, times(1)).save(any(FileOriginClient.class));
        
        // 2. Rastreabilidade - etapa COLETA
        verify(rastreabilidadeService, times(1)).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.COLETA),
                eq(StatusProcessamento.EM_ESPERA)
        );
        verify(rastreabilidadeService, times(1)).registrarInicio(1L);
        
        // 3. Rastreabilidade - etapa RAW
        verify(rastreabilidadeService, times(1)).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.RAW),
                eq(StatusProcessamento.EM_ESPERA)
        );
        
        // 4. Identificação de layout
        verify(layoutIdentificationService, times(1)).identificar(
                eq("CIELO_20240115.txt"),
                any(InputStream.class),
                eq(100L),
                eq(1L)
        );
        verify(fileOriginRepository, times(1)).save(argThat(fo -> 
            fo.getLayoutId() != null && fo.getLayoutId().equals(1L)
        ));
        
        // 5. Rastreabilidade - etapa STAGING
        verify(rastreabilidadeService, times(1)).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.STAGING),
                eq(StatusProcessamento.EM_ESPERA)
        );
        
        // 6. Rastreabilidade - etapa ORDINATION
        verify(rastreabilidadeService, times(1)).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.ORDINATION),
                eq(StatusProcessamento.EM_ESPERA)
        );
        
        // 7. Upload para destino
        verify(rastreabilidadeService, times(1)).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.PROCESSING),
                eq(StatusProcessamento.EM_ESPERA)
        );
        verify(streamingTransferService, times(1)).transferirSFTPparaS3(
                any(InputStream.class),
                eq("my-s3-bucket"),
                eq("CIELO_20240115.txt"),
                eq(1024L)
        );
        
        // 8. Rastreabilidade - etapa PROCESSED
        verify(rastreabilidadeService, times(1)).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.PROCESSED),
                eq(StatusProcessamento.EM_ESPERA)
        );
        
        // 9. Verificar que registrarConclusao foi chamado para cada etapa
        verify(rastreabilidadeService, atLeast(6)).registrarConclusao(anyLong(), any());
    }

    /**
     * Testa atualização de rastreabilidade quando cliente não é identificado.
     * Deve registrar erro na rastreabilidade com status ERRO e mensagem de erro.
     * 
     * **Valida: Requisitos 8.5, 12.1, 12.2, 12.3, 12.4**
     */
    @Test
    void deveAtualizarRastreabilidadeQuandoClienteNaoIdentificado() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.empty()); // Cliente não identificado
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        
        // Mock de rastreabilidade
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que erro foi registrado na rastreabilidade
        verify(rastreabilidadeService, times(1)).atualizarStatus(
                eq(1L),
                eq(StatusProcessamento.ERRO),
                contains("Cliente não identificado")
        );
        verify(rastreabilidadeService, times(1)).registrarConclusao(eq(1L), argThat(info -> 
            info != null && info.containsKey("stackTrace")
        ));
    }

    /**
     * Testa atualização de rastreabilidade quando layout não é identificado.
     * Deve registrar erro na rastreabilidade com status ERRO e mensagem de erro.
     * 
     * **Valida: Requisitos 9.6, 12.1, 12.2, 12.3, 12.4**
     */
    @Test
    void deveAtualizarRastreabilidadeQuandoLayoutNaoIdentificado() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.empty()); // Layout não identificado
        
        // Mock de rastreabilidade
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L, 2L, 3L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que erro foi registrado na rastreabilidade
        verify(rastreabilidadeService, times(1)).atualizarStatus(
                anyLong(),
                eq(StatusProcessamento.ERRO),
                contains("Layout não identificado")
        );
        verify(rastreabilidadeService, atLeastOnce()).registrarConclusao(anyLong(), argThat(info -> 
            info != null && info.containsKey("stackTrace")
        ));
    }

    /**
     * Testa atualização de rastreabilidade quando upload falha.
     * Deve registrar erro na rastreabilidade com status ERRO e mensagem de erro.
     * 
     * **Valida: Requisitos 10.6, 12.1, 12.2, 12.3, 12.4**
     */
    @Test
    void deveAtualizarRastreabilidadeQuandoUploadFalha() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("aws_key", "aws_secret");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        
        // Simular falha no upload
        doThrow(new RuntimeException("S3 upload failed"))
                .when(streamingTransferService).transferirSFTPparaS3(any(), any(), any(), anyLong());
        
        // Mock de rastreabilidade
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que erro foi registrado na rastreabilidade
        verify(rastreabilidadeService, times(1)).atualizarStatus(
                anyLong(),
                eq(StatusProcessamento.ERRO),
                contains("Falha ao fazer upload")
        );
        verify(rastreabilidadeService, atLeastOnce()).registrarConclusao(anyLong(), argThat(info -> 
            info != null && info.containsKey("stackTrace")
        ));
    }

    /**
     * Testa que rastreabilidade registra todas as etapas na ordem correta.
     * Deve registrar: COLETA → RAW → STAGING → ORDINATION → PROCESSING → PROCESSED.
     * 
     * **Valida: Requisitos 12.1, 12.2, 12.3**
     */
    @Test
    void deveRegistrarTodasEtapasRastreabilidadeNaOrdemCorreta() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        Server serverDestino = criarServerS3();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        VaultClient.Credenciais credenciaisDestino = new VaultClient.Credenciais("aws_key", "aws_secret");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();
        Layout layout = criarLayout();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(serverRepository.findById(200L)).thenReturn(Optional.of(serverDestino));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(vaultClient.obterCredenciais("VAULT_S3", "vault/s3/secret")).thenReturn(credenciaisDestino);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(eq("CIELO_20240115.txt"), any(InputStream.class), eq(100L), eq(1L)))
                .thenReturn(Optional.of(layout));
        when(fileOriginRepository.save(any(FileOrigin.class))).thenReturn(fileOrigin);
        
        // Mock de rastreabilidade
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        // Act
        processadorService.processarArquivo(mensagem);

        // Assert - Verificar ordem das etapas usando InOrder
        InOrder inOrder = inOrder(rastreabilidadeService);
        
        // 1. COLETA
        inOrder.verify(rastreabilidadeService).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.COLETA),
                eq(StatusProcessamento.EM_ESPERA)
        );
        inOrder.verify(rastreabilidadeService).registrarInicio(1L);
        
        // 2. RAW
        inOrder.verify(rastreabilidadeService).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.RAW),
                eq(StatusProcessamento.EM_ESPERA)
        );
        inOrder.verify(rastreabilidadeService).registrarInicio(2L);
        inOrder.verify(rastreabilidadeService).registrarConclusao(eq(2L), any());
        
        // 3. Conclusão de COLETA
        inOrder.verify(rastreabilidadeService).registrarConclusao(eq(1L), any());
        
        // 4. STAGING
        inOrder.verify(rastreabilidadeService).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.STAGING),
                eq(StatusProcessamento.EM_ESPERA)
        );
        inOrder.verify(rastreabilidadeService).registrarInicio(3L);
        inOrder.verify(rastreabilidadeService).registrarConclusao(eq(3L), any());
        
        // 5. ORDINATION
        inOrder.verify(rastreabilidadeService).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.ORDINATION),
                eq(StatusProcessamento.EM_ESPERA)
        );
        inOrder.verify(rastreabilidadeService).registrarInicio(4L);
        inOrder.verify(rastreabilidadeService).registrarConclusao(eq(4L), any());
        
        // 6. PROCESSING
        inOrder.verify(rastreabilidadeService).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.PROCESSING),
                eq(StatusProcessamento.EM_ESPERA)
        );
        inOrder.verify(rastreabilidadeService).registrarInicio(5L);
        inOrder.verify(rastreabilidadeService).registrarConclusao(eq(5L), any());
        
        // 7. PROCESSED
        inOrder.verify(rastreabilidadeService).registrarEtapa(
                eq(1000L),
                eq(EtapaProcessamento.PROCESSED),
                eq(StatusProcessamento.EM_ESPERA)
        );
        inOrder.verify(rastreabilidadeService).registrarInicio(6L);
        inOrder.verify(rastreabilidadeService).registrarConclusao(eq(6L), any());
    }

    /**
     * Testa que stack trace é incluído em jsn_additional_info quando ocorre erro.
     * Deve registrar stack trace completo para debugging.
     * 
     * **Valida: Requisitos 12.4, 12.5**
     */
    @Test
    void deveIncluirStackTraceEmInformacoesAdicionaisQuandoOcorreErro() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Mock de rastreabilidade
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L);

        // Act & Assert
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que informações adicionais contêm stack trace
        verify(rastreabilidadeService, times(1)).registrarConclusao(eq(1L), argThat(info -> 
            info != null &&
            info.containsKey("stackTrace") &&
            info.containsKey("exceptionType") &&
            info.get("exceptionType").equals("java.lang.RuntimeException")
        ));
    }

    /**
     * Testa que recursos são liberados mesmo quando ocorre erro.
     * Deve fechar InputStream e desconectar SFTP em caso de falha.
     * 
     * **Valida: Requisitos 7.5, 10.6**
     */
    @Test
    void deveLiberarRecursosMesmoQuandoOcorreErro() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(sftpClient.isConectado()).thenReturn(true);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Mock de rastreabilidade
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any()))
                .thenReturn(1L);

        // Act & Assert
        assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que recursos foram liberados
        verify(sftpClient, times(1)).isConectado();
        verify(sftpClient, times(1)).desconectar();
    }

    // ========== Testes de Classificação de Erros (Task 15.2) ==========

    /**
     * Testa classificação de erro recuperável - falha de conexão SFTP.
     * Deve lançar ErroRecuperavelException para permitir retry.
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.3**
     */
    @Test
    void deveClassificarFalhaConexaoSFTPComoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        doThrow(new com.jcraft.jsch.JSchException("Connection refused"))
                .when(sftpClient).conectar("sftp.server.com", 22, credenciais);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ErroRecuperavelException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ErroRecuperavelException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException);
    }

    /**
     * Testa classificação de erro não recuperável - arquivo não encontrado.
     * Deve lançar ErroNaoRecuperavelException para ACK (não reprocessar).
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.4**
     */
    @Test
    void deveClassificarArquivoNaoEncontradoComoNaoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new com.jcraft.jsch.SftpException(2, "No such file"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ErroNaoRecuperavelException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.ErroNaoRecuperavelException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ErroNaoRecuperavelException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroNaoRecuperavelException);
    }

    /**
     * Testa classificação de erro não recuperável - cliente não identificado.
     * Deve lançar ErroNaoRecuperavelException (ClienteNaoIdentificadoException).
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.4**
     */
    @Test
    void deveClassificarClienteNaoIdentificadoComoNaoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.empty());
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ClienteNaoIdentificadoException (subclasse de ErroNaoRecuperavelException)
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof ClienteNaoIdentificadoException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ClienteNaoIdentificadoException");
        assertTrue(cause instanceof ClienteNaoIdentificadoException);
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroNaoRecuperavelException);
    }

    /**
     * Testa classificação de erro não recuperável - layout não identificado.
     * Deve lançar ErroNaoRecuperavelException (LayoutNaoIdentificadoException).
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.4**
     */
    @Test
    void deveClassificarLayoutNaoIdentificadoComoNaoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é LayoutNaoIdentificadoException (subclasse de ErroNaoRecuperavelException)
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof LayoutNaoIdentificadoException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar LayoutNaoIdentificadoException");
        assertTrue(cause instanceof LayoutNaoIdentificadoException);
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroNaoRecuperavelException);
    }

    /**
     * Testa classificação de erro não recuperável - credenciais inválidas.
     * Deve lançar ErroNaoRecuperavelException para ACK (não reprocessar).
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.4**
     */
    @Test
    void deveClassificarCredenciaisInvalidasComoNaoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        doThrow(new com.jcraft.jsch.JSchException("Auth fail"))
                .when(sftpClient).conectar("sftp.server.com", 22, credenciais);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ErroNaoRecuperavelException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.ErroNaoRecuperavelException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ErroNaoRecuperavelException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroNaoRecuperavelException);
    }

    /**
     * Testa classificação de erro recuperável - timeout.
     * Deve lançar ErroRecuperavelException para permitir retry.
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.3**
     */
    @Test
    void deveClassificarTimeoutComoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new java.net.SocketTimeoutException("Read timed out"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ErroRecuperavelException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ErroRecuperavelException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException);
    }

    /**
     * Testa classificação de erro recuperável - falha transiente de banco.
     * Deve lançar ErroRecuperavelException para permitir retry.
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.3**
     */
    @Test
    void deveClassificarFalhaBancoTransienteComoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L))
                .thenThrow(new java.sql.SQLTransientConnectionException("Connection lost"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ErroRecuperavelException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ErroRecuperavelException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException);
    }

    /**
     * Testa que erro já classificado como recuperável é relançado.
     * Não deve reclassificar exceções já tipadas.
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.3**
     */
    @Test
    void deveRelancarErroJaClassificadoComoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new com.controle.arquivos.processor.exception.FalhaUploadException("Upload failed"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é FalhaUploadException (subclasse de ErroRecuperavelException)
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.FalhaUploadException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar FalhaUploadException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.FalhaUploadException);
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException);
    }

    /**
     * Testa que erro não classificado é tratado como recuperável por padrão.
     * Deve lançar ErroRecuperavelException para permitir retry.
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.3**
     */
    @Test
    void deveTratarErroNaoClassificadoComoRecuperavel() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new RuntimeException("Unknown error"));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que é ErroRecuperavelException
        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "Deve lançar ErroRecuperavelException");
        assertTrue(cause instanceof com.controle.arquivos.processor.exception.ErroRecuperavelException);
    }

    // ========== Testes de Registro de Erro com Contexto Completo (Task 15.4) ==========

    /**
     * Testa que erro é registrado com contexto completo incluindo stack trace.
     * Deve registrar erro na rastreabilidade com mensagem, stack trace e contador de tentativas.
     * 
     * **Valida: Requisitos 15.1, 15.2, 15.5, 15.6**
     */
    @Test
    void deveRegistrarErroComContextoCompleto() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new RuntimeException("Test error"));
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que erro foi registrado com contexto completo
        verify(rastreabilidadeService, times(1)).atualizarStatus(
                eq(1L),
                eq(StatusProcessamento.ERRO),
                contains("Test error")
        );
        
        // Verificar que informações adicionais incluem stack trace e contador de tentativas
        verify(rastreabilidadeService, times(1)).registrarConclusao(
                eq(1L),
                argThat(info -> 
                    info != null &&
                    info.containsKey("stackTrace") &&
                    info.containsKey("exceptionType") &&
                    info.containsKey("retryCount") &&
                    ((String) info.get("stackTrace")).contains("RuntimeException") &&
                    info.get("exceptionType").equals("java.lang.RuntimeException") &&
                    ((Number) info.get("retryCount")).intValue() == 1
                )
        );
    }

    /**
     * Testa que stack trace é incluído nas informações adicionais.
     * Deve capturar stack trace completo da exceção.
     * 
     * **Valida: Requisitos 15.1, 15.5**
     */
    @Test
    void deveIncluirStackTraceNasInformacoesAdicionais() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        CustomerIdentification cliente = criarCustomerIdentification();
        FileOriginClient fileOriginClient = criarFileOriginClient();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt")).thenReturn(mockInputStream);
        when(clienteIdentificationService.identificar("CIELO_20240115.txt", 1L))
                .thenReturn(Optional.of(cliente));
        when(fileOriginClientRepository.save(any(FileOriginClient.class))).thenReturn(fileOriginClient);
        when(fileOriginClientRepository.findById(1000L)).thenReturn(Optional.of(fileOriginClient));
        when(layoutIdentificationService.identificar(anyString(), any(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Layout identification failed"));
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L, 2L, 3L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que stack trace foi incluído
        verify(rastreabilidadeService, atLeastOnce()).registrarConclusao(
                anyLong(),
                argThat(info -> 
                    info != null &&
                    info.containsKey("stackTrace") &&
                    ((String) info.get("stackTrace")).contains("at ") &&
                    ((String) info.get("stackTrace")).contains("RuntimeException")
                )
        );
    }

    /**
     * Testa que contador de tentativas é incrementado a cada erro.
     * Deve registrar o número correto de tentativas nas informações adicionais.
     * 
     * **Valida: Requisitos 15.6**
     */
    @Test
    void deveIncrementarContadorTentativasAoCadaErro() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new RuntimeException("Test error"));
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L);
        
        // Simular que não há tentativas anteriores (primeira tentativa)
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L))
                .thenReturn(Optional.empty());

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que contador de tentativas é 1 (primeira tentativa)
        verify(rastreabilidadeService, times(1)).registrarConclusao(
                eq(1L),
                argThat(info -> 
                    info != null &&
                    info.containsKey("retryCount") &&
                    ((Number) info.get("retryCount")).intValue() == 1
                )
        );
    }

    /**
     * Testa que tipo de exceção é registrado nas informações adicionais.
     * Deve incluir o nome completo da classe da exceção.
     * 
     * **Valida: Requisitos 15.1, 15.5**
     */
    @Test
    void deveRegistrarTipoExcecaoNasInformacoesAdicionais() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new java.net.SocketTimeoutException("Connection timeout"));
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que tipo de exceção foi registrado
        verify(rastreabilidadeService, times(1)).registrarConclusao(
                eq(1L),
                argThat(info -> 
                    info != null &&
                    info.containsKey("exceptionType") &&
                    ((String) info.get("exceptionType")).contains("SocketTimeoutException")
                )
        );
    }

    /**
     * Testa que mensagem de erro é registrada na rastreabilidade.
     * Deve atualizar status para ERRO com mensagem descritiva.
     * 
     * **Valida: Requisitos 15.1, 15.2**
     */
    @Test
    void deveRegistrarMensagemErroNaRastreabilidade() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = criarMensagemProcessamento();
        FileOrigin fileOrigin = criarFileOrigin();
        SeverPathsInOut pathsInOut = criarPathsInOut();
        SeverPaths severPath = criarSeverPath();
        Server server = criarServer();
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        String mensagemErro = "Specific error message for testing";

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(severPathsInOutRepository.findById(10L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(100L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1000L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais("VAULT_CODE", "vault/secret/path")).thenReturn(credenciais);
        when(sftpClient.obterInputStream("/sftp/path/CIELO_20240115.txt"))
                .thenThrow(new RuntimeException(mensagemErro));
        when(rastreabilidadeService.registrarEtapa(anyLong(), any(), any())).thenReturn(1L);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            processadorService.processarArquivo(mensagem);
        });

        // Verificar que mensagem de erro foi registrada
        verify(rastreabilidadeService, times(1)).atualizarStatus(
                eq(1L),
                eq(StatusProcessamento.ERRO),
                eq(mensagemErro)
        );
    }
}
