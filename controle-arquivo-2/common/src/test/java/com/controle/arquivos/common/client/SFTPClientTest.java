package com.controle.arquivos.common.client;

import com.controle.arquivos.common.config.SftpProperties;
import com.jcraft.jsch.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para SFTPClient.
 * 
 * **Valida: Requisitos 2.1, 2.2, 2.5**
 */
@ExtendWith(MockitoExtension.class)
class SFTPClientTest {

    @Mock
    private JSch jsch;

    @Mock
    private Session session;

    @Mock
    private ChannelSftp channelSftp;

    @Mock
    private Channel channel;

    private SftpProperties sftpProperties;
    private SFTPClient sftpClient;

    @BeforeEach
    void setUp() {
        sftpProperties = new SftpProperties();
        sftpProperties.setTimeout(30000);
        sftpProperties.setSessionTimeout(120000);
        sftpProperties.setChannelTimeout(60000);
        sftpProperties.setStrictHostKeyChecking(false);

        sftpClient = new SFTPClient(sftpProperties);
        
        // Inject mocked JSch via reflection
        try {
            var field = SFTPClient.class.getDeclaredField("jsch");
            field.setAccessible(true);
            field.set(sftpClient, jsch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void conectar_deveEstabelecerConexaoComSucesso() throws Exception {
        // Arrange
        String host = "sftp.example.com";
        int port = 22;
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");

        when(jsch.getSession("testuser", host, port)).thenReturn(session);
        when(session.openChannel("sftp")).thenReturn(channelSftp);
        when(session.isConnected()).thenReturn(true);
        when(channelSftp.isConnected()).thenReturn(true);

        // Act
        sftpClient.conectar(host, port, credenciais);

        // Assert
        assertTrue(sftpClient.isConectado());
        verify(session).setPassword("testpass");
        verify(session).setTimeout(30000);
        verify(session).connect(120000);
        verify(channelSftp).connect(60000);
    }

    @Test
    void conectar_deveLancarExcecaoQuandoFalhaConexao() throws Exception {
        // Arrange
        String host = "sftp.example.com";
        int port = 22;
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");

        when(jsch.getSession("testuser", host, port)).thenReturn(session);
        doThrow(new JSchException("Connection refused")).when(session).connect(anyInt());

        // Act & Assert
        SFTPClient.SFTPException exception = assertThrows(SFTPClient.SFTPException.class, () -> {
            sftpClient.conectar(host, port, credenciais);
        });

        assertTrue(exception.getMessage().contains("Falha ao conectar ao servidor SFTP"));
        assertFalse(sftpClient.isConectado());
    }

    @Test
    void conectar_deveDesconectarQuandoFalhaAbrirCanal() throws Exception {
        // Arrange
        String host = "sftp.example.com";
        int port = 22;
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");

        when(jsch.getSession("testuser", host, port)).thenReturn(session);
        when(session.openChannel("sftp")).thenThrow(new JSchException("Failed to open channel"));

        // Act & Assert
        assertThrows(SFTPClient.SFTPException.class, () -> {
            sftpClient.conectar(host, port, credenciais);
        });

        verify(session).disconnect();
        assertFalse(sftpClient.isConectado());
    }

    @Test
    void conectar_deveConfigurarStrictHostKeyCheckingQuandoHabilitado() throws Exception {
        // Arrange
        sftpProperties.setStrictHostKeyChecking(true);
        sftpProperties.setKnownHostsFile("/path/to/known_hosts");
        
        SFTPClient strictClient = new SFTPClient(sftpProperties);
        try {
            var field = SFTPClient.class.getDeclaredField("jsch");
            field.setAccessible(true);
            field.set(strictClient, jsch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String host = "sftp.example.com";
        int port = 22;
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");

        when(jsch.getSession("testuser", host, port)).thenReturn(session);
        when(session.openChannel("sftp")).thenReturn(channelSftp);
        when(session.isConnected()).thenReturn(true);
        when(channelSftp.isConnected()).thenReturn(true);

        // Act
        strictClient.conectar(host, port, credenciais);

        // Assert
        verify(jsch).setKnownHosts("/path/to/known_hosts");
        verify(session).setConfig(argThat(config -> 
            "yes".equals(config.getProperty("StrictHostKeyChecking"))
        ));
    }

    @Test
    void listarArquivos_deveRetornarListaDeArquivos() throws Exception {
        // Arrange
        setupConexaoMock();
        String caminho = "/remote/path";

        Vector<ChannelSftp.LsEntry> entries = new Vector<>();
        entries.add(createLsEntry("file1.txt", 1024L, 1704067200L, false));
        entries.add(createLsEntry("file2.csv", 2048L, 1704153600L, false));
        entries.add(createLsEntry(".", 0L, 0L, true)); // Deve ser ignorado
        entries.add(createLsEntry("..", 0L, 0L, true)); // Deve ser ignorado
        entries.add(createLsEntry("subdir", 0L, 0L, true)); // Deve ser ignorado

        when(channelSftp.ls(caminho)).thenReturn(entries);

        // Act
        List<SFTPClient.ArquivoMetadata> arquivos = sftpClient.listarArquivos(caminho);

        // Assert
        assertEquals(2, arquivos.size());
        
        assertEquals("file1.txt", arquivos.get(0).getNome());
        assertEquals(1024L, arquivos.get(0).getTamanho());
        assertEquals(1704067200000L, arquivos.get(0).getTimestamp());
        
        assertEquals("file2.csv", arquivos.get(1).getNome());
        assertEquals(2048L, arquivos.get(1).getTamanho());
        assertEquals(1704153600000L, arquivos.get(1).getTimestamp());
    }

    @Test
    void listarArquivos_deveRetornarListaVaziaQuandoNaoHaArquivos() throws Exception {
        // Arrange
        setupConexaoMock();
        String caminho = "/remote/empty";

        Vector<ChannelSftp.LsEntry> entries = new Vector<>();
        entries.add(createLsEntry(".", 0L, 0L, true));
        entries.add(createLsEntry("..", 0L, 0L, true));

        when(channelSftp.ls(caminho)).thenReturn(entries);

        // Act
        List<SFTPClient.ArquivoMetadata> arquivos = sftpClient.listarArquivos(caminho);

        // Assert
        assertTrue(arquivos.isEmpty());
    }

    @Test
    void listarArquivos_deveLancarExcecaoQuandoFalhaListagem() throws Exception {
        // Arrange
        setupConexaoMock();
        String caminho = "/remote/path";

        when(channelSftp.ls(caminho)).thenThrow(new SftpException(2, "No such file"));

        // Act & Assert
        SFTPClient.SFTPException exception = assertThrows(SFTPClient.SFTPException.class, () -> {
            sftpClient.listarArquivos(caminho);
        });

        assertTrue(exception.getMessage().contains("Falha ao listar arquivos"));
    }

    @Test
    void listarArquivos_deveLancarExcecaoQuandoNaoConectado() {
        // Arrange
        String caminho = "/remote/path";

        // Act & Assert
        SFTPClient.SFTPException exception = assertThrows(SFTPClient.SFTPException.class, () -> {
            sftpClient.listarArquivos(caminho);
        });

        assertTrue(exception.getMessage().contains("Não há conexão ativa"));
    }

    @Test
    void obterInputStream_deveRetornarInputStreamDoArquivo() throws Exception {
        // Arrange
        setupConexaoMock();
        String caminho = "/remote/path/file.txt";
        InputStream expectedStream = new ByteArrayInputStream("test content".getBytes());

        when(channelSftp.get(caminho)).thenReturn(expectedStream);

        // Act
        InputStream result = sftpClient.obterInputStream(caminho);

        // Assert
        assertNotNull(result);
        assertSame(expectedStream, result);
        verify(channelSftp).get(caminho);
    }

    @Test
    void obterInputStream_deveLancarExcecaoQuandoArquivoNaoEncontrado() throws Exception {
        // Arrange
        setupConexaoMock();
        String caminho = "/remote/path/nonexistent.txt";

        when(channelSftp.get(caminho)).thenThrow(new SftpException(2, "No such file"));

        // Act & Assert
        SFTPClient.SFTPException exception = assertThrows(SFTPClient.SFTPException.class, () -> {
            sftpClient.obterInputStream(caminho);
        });

        assertTrue(exception.getMessage().contains("Falha ao obter InputStream do arquivo"));
    }

    @Test
    void obterInputStream_deveLancarExcecaoQuandoNaoConectado() {
        // Arrange
        String caminho = "/remote/path/file.txt";

        // Act & Assert
        SFTPClient.SFTPException exception = assertThrows(SFTPClient.SFTPException.class, () -> {
            sftpClient.obterInputStream(caminho);
        });

        assertTrue(exception.getMessage().contains("Não há conexão ativa"));
    }

    @Test
    void desconectar_deveFecharCanalESessao() throws Exception {
        // Arrange
        setupConexaoMock();

        // Act
        sftpClient.desconectar();

        // Assert
        verify(channelSftp).disconnect();
        verify(session).disconnect();
        assertFalse(sftpClient.isConectado());
    }

    @Test
    void desconectar_deveSerSeguroQuandoJaDesconectado() {
        // Arrange - não conectado

        // Act & Assert - não deve lançar exceção
        assertDoesNotThrow(() -> sftpClient.desconectar());
    }

    @Test
    void desconectar_deveSerSeguroQuandoCanalNulo() throws Exception {
        // Arrange
        when(jsch.getSession(anyString(), anyString(), anyInt())).thenReturn(session);
        when(session.isConnected()).thenReturn(true);

        // Inject session without channel
        try {
            var sessionField = SFTPClient.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(sftpClient, session);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Act & Assert
        assertDoesNotThrow(() -> sftpClient.desconectar());
        verify(session).disconnect();
    }

    @Test
    void desconectar_deveTratarExcecaoDuranteDesconexao() throws Exception {
        // Arrange
        setupConexaoMock();
        doThrow(new RuntimeException("Disconnect error")).when(channelSftp).disconnect();

        // Act & Assert - não deve propagar exceção
        assertDoesNotThrow(() -> sftpClient.desconectar());
    }

    @Test
    void isConectado_deveRetornarTrueQuandoConectado() throws Exception {
        // Arrange
        setupConexaoMock();

        // Act & Assert
        assertTrue(sftpClient.isConectado());
    }

    @Test
    void isConectado_deveRetornarFalseQuandoNaoConectado() {
        // Act & Assert
        assertFalse(sftpClient.isConectado());
    }

    @Test
    void isConectado_deveRetornarFalseQuandoSessaoDesconectada() throws Exception {
        // Arrange
        setupConexaoMock();
        when(session.isConnected()).thenReturn(false);

        // Act & Assert
        assertFalse(sftpClient.isConectado());
    }

    @Test
    void isConectado_deveRetornarFalseQuandoCanalDesconectado() throws Exception {
        // Arrange
        setupConexaoMock();
        when(channelSftp.isConnected()).thenReturn(false);

        // Act & Assert
        assertFalse(sftpClient.isConectado());
    }

    @Test
    void arquivoMetadata_deveConterInformacoesCorretas() {
        // Arrange & Act
        SFTPClient.ArquivoMetadata metadata = new SFTPClient.ArquivoMetadata(
            "test.txt", 1024L, 1704067200000L
        );

        // Assert
        assertEquals("test.txt", metadata.getNome());
        assertEquals(1024L, metadata.getTamanho());
        assertEquals(1704067200000L, metadata.getTimestamp());
    }

    @Test
    void arquivoMetadata_toStringShouldContainAllFields() {
        // Arrange
        SFTPClient.ArquivoMetadata metadata = new SFTPClient.ArquivoMetadata(
            "test.txt", 1024L, 1704067200000L
        );

        // Act
        String toString = metadata.toString();

        // Assert
        assertTrue(toString.contains("test.txt"));
        assertTrue(toString.contains("1024"));
        assertTrue(toString.contains("1704067200000"));
    }

    @Test
    void sftpException_deveConterMensagemECausa() {
        // Arrange
        Exception causa = new RuntimeException("Root cause");

        // Act
        SFTPClient.SFTPException exception = new SFTPClient.SFTPException("Test message", causa);

        // Assert
        assertEquals("Test message", exception.getMessage());
        assertSame(causa, exception.getCause());
    }

    @Test
    void sftpException_deveConterApenasMensagem() {
        // Act
        SFTPClient.SFTPException exception = new SFTPClient.SFTPException("Test message");

        // Assert
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void conectar_deveUsarTimeoutsConfigurados() throws Exception {
        // Arrange
        sftpProperties.setTimeout(10000);
        sftpProperties.setSessionTimeout(20000);
        sftpProperties.setChannelTimeout(15000);
        
        SFTPClient customClient = new SFTPClient(sftpProperties);
        try {
            var field = SFTPClient.class.getDeclaredField("jsch");
            field.setAccessible(true);
            field.set(customClient, jsch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String host = "sftp.example.com";
        int port = 22;
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("testuser", "testpass");

        when(jsch.getSession("testuser", host, port)).thenReturn(session);
        when(session.openChannel("sftp")).thenReturn(channelSftp);
        when(session.isConnected()).thenReturn(true);
        when(channelSftp.isConnected()).thenReturn(true);

        // Act
        customClient.conectar(host, port, credenciais);

        // Assert
        verify(session).setTimeout(10000);
        verify(session).connect(20000);
        verify(channelSftp).connect(15000);
    }

    @Test
    void listarArquivos_deveConverterTimestampCorretamente() throws Exception {
        // Arrange
        setupConexaoMock();
        String caminho = "/remote/path";

        Vector<ChannelSftp.LsEntry> entries = new Vector<>();
        // Unix timestamp em segundos: 1704067200 = 2024-01-01 00:00:00 UTC
        entries.add(createLsEntry("file.txt", 1024L, 1704067200L, false));

        when(channelSftp.ls(caminho)).thenReturn(entries);

        // Act
        List<SFTPClient.ArquivoMetadata> arquivos = sftpClient.listarArquivos(caminho);

        // Assert
        assertEquals(1, arquivos.size());
        // Deve converter para milissegundos
        assertEquals(1704067200000L, arquivos.get(0).getTimestamp());
    }

    // Helper methods

    private void setupConexaoMock() throws Exception {
        when(jsch.getSession(anyString(), anyString(), anyInt())).thenReturn(session);
        when(session.openChannel("sftp")).thenReturn(channelSftp);
        when(session.isConnected()).thenReturn(true);
        when(channelSftp.isConnected()).thenReturn(true);

        // Inject mocked session and channel
        var sessionField = SFTPClient.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(sftpClient, session);

        var channelField = SFTPClient.class.getDeclaredField("channelSftp");
        channelField.setAccessible(true);
        channelField.set(sftpClient, channelSftp);
    }

    private ChannelSftp.LsEntry createLsEntry(String filename, long size, long mtime, boolean isDir) {
        SftpATTRS attrs = mock(SftpATTRS.class);
        when(attrs.getSize()).thenReturn(size);
        when(attrs.getMTime()).thenReturn((int) mtime);
        when(attrs.isDir()).thenReturn(isDir);

        ChannelSftp.LsEntry entry = mock(ChannelSftp.LsEntry.class);
        when(entry.getFilename()).thenReturn(filename);
        when(entry.getAttrs()).thenReturn(attrs);

        return entry;
    }
}
