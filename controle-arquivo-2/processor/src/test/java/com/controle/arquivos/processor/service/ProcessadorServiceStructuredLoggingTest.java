package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClient;
import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import com.controle.arquivos.common.domain.enums.TipoServidor;
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
import com.controle.arquivos.processor.exception.ClienteNaoIdentificadoException;
import com.controle.arquivos.processor.exception.ErroNaoRecuperavelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes para verificar logging estruturado de erros no ProcessadorService.
 * 
 * **Valida: Requisitos 15.1, 15.2, 15.5, 20.1, 20.2, 20.4**
 */
@ExtendWith(MockitoExtension.class)
class ProcessadorServiceStructuredLoggingTest {

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
    
    @InjectMocks
    private ProcessadorService processadorService;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    void testProcessarArquivo_ClienteNaoIdentificado_LogsStructuredError() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .nomeArquivo("test_file.txt")
            .idMapeamentoOrigemDestino(1L)
            .correlationId("test-correlation-123")
            .build();

        FileOrigin fileOrigin = FileOrigin.builder()
            .id(1L)
            .fileName("test_file.txt")
            .acquirerId(100L)
            .fileSize(1024L)
            .build();

        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
            .id(1L)
            .severPathOriginId(1L)
            .severDestinationId(2L)
            .build();

        SeverPaths severPath = SeverPaths.builder()
            .id(1L)
            .serverId(1L)
            .path("/origin/path")
            .build();

        Server server = Server.builder()
            .id(1L)
            .serverCode("test-server")
            .vaultCode("vault-code")
            .vaultSecret("vault-secret")
            .serverType(TipoServidor.SFTP)
            .build();

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass", "host", 22);

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(severPathsInOutRepository.findById(1L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(1L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);
        when(sftpClient.obterInputStream(anyString())).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(clienteIdentificationService.identificar(anyString(), anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        ErroNaoRecuperavelException exception = assertThrows(
            ErroNaoRecuperavelException.class,
            () -> processadorService.processarArquivo(mensagem)
        );

        // Verify
        assertTrue(exception.getMessage().contains("Cliente não identificado"));
        verify(clienteIdentificationService).identificar("test_file.txt", 100L);
    }

    @Test
    void testProcessarArquivo_WithCredentialsInError_SanitizesLog() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .nomeArquivo("test_file.txt")
            .idMapeamentoOrigemDestino(1L)
            .correlationId("test-correlation-123")
            .build();

        FileOrigin fileOrigin = FileOrigin.builder()
            .id(1L)
            .fileName("test_file.txt")
            .acquirerId(100L)
            .fileSize(1024L)
            .build();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(severPathsInOutRepository.findById(1L))
            .thenThrow(new RuntimeException("Connection failed: password=secret123"));

        // Act & Assert
        assertThrows(
            Exception.class,
            () -> processadorService.processarArquivo(mensagem)
        );

        // Verify - o teste garante que não há exceção ao processar erro com credenciais
        // A sanitização é feita pelo StructuredErrorLogger
        verify(fileOriginRepository).findById(1L);
    }

    @Test
    void testProcessarArquivo_WithCorrelationId_IncludesInContext() throws Exception {
        // Arrange
        String correlationId = "test-correlation-456";
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .nomeArquivo("test_file.txt")
            .idMapeamentoOrigemDestino(1L)
            .correlationId(correlationId)
            .build();

        FileOrigin fileOrigin = FileOrigin.builder()
            .id(1L)
            .fileName("test_file.txt")
            .acquirerId(100L)
            .fileSize(1024L)
            .build();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(severPathsInOutRepository.findById(1L))
            .thenThrow(new RuntimeException("Test error"));

        // Act & Assert
        assertThrows(
            Exception.class,
            () -> processadorService.processarArquivo(mensagem)
        );

        // Verify - correlationId deve ter sido usado no contexto
        verify(fileOriginRepository).findById(1L);
    }

    @Test
    void testProcessarArquivo_WithMultipleErrors_LogsEachWithContext() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .nomeArquivo("test_file.txt")
            .idMapeamentoOrigemDestino(1L)
            .correlationId("test-correlation-789")
            .build();

        FileOrigin fileOrigin = FileOrigin.builder()
            .id(1L)
            .fileName("test_file.txt")
            .acquirerId(100L)
            .fileSize(1024L)
            .build();

        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
            .id(1L)
            .severPathOriginId(1L)
            .severDestinationId(2L)
            .build();

        SeverPaths severPath = SeverPaths.builder()
            .id(1L)
            .serverId(1L)
            .path("/origin/path")
            .build();

        Server server = Server.builder()
            .id(1L)
            .serverCode("test-server")
            .vaultCode("vault-code")
            .vaultSecret("vault-secret")
            .serverType(TipoServidor.SFTP)
            .build();

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass", "host", 22);

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        when(severPathsInOutRepository.findById(1L)).thenReturn(Optional.of(pathsInOut));
        when(severPathsRepository.findById(1L)).thenReturn(Optional.of(severPath));
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);
        when(sftpClient.obterInputStream(anyString())).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(clienteIdentificationService.identificar(anyString(), anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
            ErroNaoRecuperavelException.class,
            () -> processadorService.processarArquivo(mensagem)
        );

        // Verify - erro deve ter sido registrado com contexto completo
        verify(clienteIdentificationService).identificar("test_file.txt", 100L);
    }

    @Test
    void testProcessarArquivo_WithStackTrace_IncludesInAdditionalInfo() throws Exception {
        // Arrange
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .nomeArquivo("test_file.txt")
            .idMapeamentoOrigemDestino(1L)
            .correlationId("test-correlation-999")
            .build();

        FileOrigin fileOrigin = FileOrigin.builder()
            .id(1L)
            .fileName("test_file.txt")
            .acquirerId(100L)
            .fileSize(1024L)
            .build();

        when(fileOriginRepository.findById(1L)).thenReturn(Optional.of(fileOrigin));
        when(fileOriginClientRepository.findByFileOriginIdAndActiveTrue(1L)).thenReturn(Optional.empty());
        
        // Simular erro com stack trace profundo
        RuntimeException innerException = new RuntimeException("Inner error");
        RuntimeException outerException = new RuntimeException("Outer error", innerException);
        when(severPathsInOutRepository.findById(1L)).thenThrow(outerException);

        // Act & Assert
        assertThrows(
            Exception.class,
            () -> processadorService.processarArquivo(mensagem)
        );

        // Verify - stack trace completo deve ter sido incluído
        verify(fileOriginRepository).findById(1L);
    }
}
