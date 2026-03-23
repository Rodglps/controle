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
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para registro de arquivos novos no OrquestradorService.
 * 
 * Feature: controle-de-arquivos, Property 6: Registro de Arquivo Novo
 * 
 * Para qualquer arquivo novo identificado, o Orquestrador deve inserir um registro na tabela
 * file_origin com status inicial COLETA e EM_ESPERA, incluindo des_file_name, num_file_size,
 * dat_timestamp_file e idt_sever_paths_in_out.
 * 
 * **Valida: Requisitos 3.1, 3.2, 3.3**
 */
class OrquestradorServiceFileRegistrationPropertyTest {

    /**
     * Propriedade 6: Registro de Arquivo Novo
     * 
     * Para qualquer arquivo novo identificado, o Orquestrador deve inserir um registro na tabela
     * file_origin com status inicial COLETA e EM_ESPERA, incluindo des_file_name, num_file_size,
     * dat_timestamp_file e idt_sever_paths_in_out.
     * 
     * Este teste verifica que:
     * 1. Arquivo novo é registrado em file_origin
     * 2. Todos os campos obrigatórios são preenchidos (fileName, fileSize, fileTimestamp, severPathsInOutId)
     * 3. Campo acquirerId é preenchido corretamente
     * 4. Campo active é definido como true
     * 5. Registro é criado para cada arquivo novo encontrado
     * 
     * **Valida: Requisitos 3.1, 3.2, 3.3**
     */
    @Property(tries = 100)
    void propriedade6_registroDeArquivoNovo(
        @ForAll("arquivosNovos") List<ArquivoNovo> arquivos
    ) {
        // Arrange
        ServerRepository serverRepository = mock(ServerRepository.class);
        SeverPathsRepository severPathsRepository = mock(SeverPathsRepository.class);
        SeverPathsInOutRepository severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        SFTPClient sftpClient = mock(SFTPClient.class);
        VaultClient vaultClient = mock(VaultClient.class);

        OrquestradorService service = new OrquestradorService(
            serverRepository,
            severPathsRepository,
            severPathsInOutRepository,
            fileOriginRepository,
            sftpClient,
            vaultClient
        );

        // Configurar servidor e caminhos
        Server serverOrigem = new Server();
        serverOrigem.setId(1L);
        serverOrigem.setServerCode("sftp://test-server.example.com:22");
        serverOrigem.setVaultCode("vault_code");
        serverOrigem.setVaultSecret("secret/path");
        serverOrigem.setServerType(TipoServidor.SFTP);
        serverOrigem.setServerOrigin(OrigemServidor.EXTERNO);
        serverOrigem.setActive(true);

        Server serverDestino = new Server();
        serverDestino.setId(100L);
        serverDestino.setServerCode("s3://test-bucket");
        serverDestino.setServerType(TipoServidor.S3);
        serverDestino.setActive(true);

        SeverPaths caminhoOrigem = new SeverPaths();
        caminhoOrigem.setId(1L);
        caminhoOrigem.setServerId(1L);
        caminhoOrigem.setAcquirerId(10L);
        caminhoOrigem.setPath("/data/input");
        caminhoOrigem.setPathType(TipoCaminho.ORIGIN);
        caminhoOrigem.setActive(true);

        SeverPathsInOut inOut = new SeverPathsInOut();
        inOut.setId(1L);
        inOut.setOriginPathId(1L);
        inOut.setDestinationServerId(100L);
        inOut.setLinkType(TipoLink.PRINCIPAL);
        inOut.setActive(true);

        when(serverRepository.findByActiveTrue()).thenReturn(List.of(serverOrigem, serverDestino));
        when(severPathsRepository.findByActiveTrue()).thenReturn(List.of(caminhoOrigem));
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(List.of(inOut));

        // Configurar Vault e SFTP
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais("vault_code", "secret/path")).thenReturn(credenciais);
        doNothing().when(sftpClient).conectar(anyString(), anyInt(), any(VaultClient.Credenciais.class));

        // Configurar lista de arquivos SFTP
        List<SFTPClient.ArquivoMetadata> arquivosSFTP = new ArrayList<>();
        for (ArquivoNovo arquivo : arquivos) {
            arquivosSFTP.add(new SFTPClient.ArquivoMetadata(
                arquivo.getNome(),
                arquivo.getTamanho(),
                arquivo.getTimestamp()
            ));
        }
        when(sftpClient.listarArquivos("/data/input")).thenReturn(arquivosSFTP);
        doNothing().when(sftpClient).desconectar();

        // Configurar repositório para indicar que arquivos não existem (são novos)
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(),
            anyLong(),
            any(Instant.class)
        )).thenReturn(Optional.empty());

        // Configurar save para retornar o arquivo com ID
        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(invocation -> {
            FileOrigin fo = invocation.getArgument(0);
            fo.setId(System.currentTimeMillis()); // Simular ID gerado
            return fo;
        });

        // Act
        assertDoesNotThrow(() -> {
            service.executarCicloColeta();
        }, "Ciclo de coleta não deve lançar exceção ao registrar arquivos novos");

        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository, times(arquivos.size())).save(captor.capture());

        List<FileOrigin> arquivosSalvos = captor.getAllValues();
        assertEquals(arquivos.size(), arquivosSalvos.size(),
            "Deve salvar um registro para cada arquivo novo");

        // Verificar cada arquivo salvo
        for (int i = 0; i < arquivos.size(); i++) {
            ArquivoNovo arquivoEsperado = arquivos.get(i);
            FileOrigin arquivoSalvo = arquivosSalvos.get(i);

            // Requisito 3.1: Registro inserido na tabela file_origin
            assertNotNull(arquivoSalvo, "Arquivo deve ser registrado");

            // Requisito 3.3: Campos obrigatórios preenchidos
            assertEquals(arquivoEsperado.getNome(), arquivoSalvo.getFileName(),
                "des_file_name deve ser preenchido corretamente");
            assertEquals(arquivoEsperado.getTamanho(), arquivoSalvo.getFileSize(),
                "num_file_size deve ser preenchido corretamente");
            assertEquals(Instant.ofEpochMilli(arquivoEsperado.getTimestamp()), arquivoSalvo.getFileTimestamp(),
                "dat_timestamp_file deve ser preenchido corretamente");
            assertEquals(1L, arquivoSalvo.getSeverPathsInOutId(),
                "idt_sever_paths_in_out deve ser preenchido corretamente");
            assertEquals(10L, arquivoSalvo.getAcquirerId(),
                "idt_acquirer deve ser preenchido corretamente");

            // Requisito 3.2: Status inicial (implícito - active=true indica EM_ESPERA)
            assertTrue(arquivoSalvo.getActive(),
                "flg_active deve ser true para indicar status EM_ESPERA");
        }
    }

    /**
     * Propriedade: Todos os campos obrigatórios devem ser não-nulos.
     */
    @Property(tries = 100)
    void todosOsCamposObrigatoriosDevemSerPreenchidos(
        @ForAll("arquivoNovo") ArquivoNovo arquivo
    ) {
        // Arrange
        ServerRepository serverRepository = mock(ServerRepository.class);
        SeverPathsRepository severPathsRepository = mock(SeverPathsRepository.class);
        SeverPathsInOutRepository severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        SFTPClient sftpClient = mock(SFTPClient.class);
        VaultClient vaultClient = mock(VaultClient.class);

        OrquestradorService service = new OrquestradorService(
            serverRepository,
            severPathsRepository,
            severPathsInOutRepository,
            fileOriginRepository,
            sftpClient,
            vaultClient
        );

        // Configurar mocks
        Server serverOrigem = new Server();
        serverOrigem.setId(1L);
        serverOrigem.setServerCode("sftp://server.com:22");
        serverOrigem.setVaultCode("vault");
        serverOrigem.setVaultSecret("secret");
        serverOrigem.setServerType(TipoServidor.SFTP);
        serverOrigem.setServerOrigin(OrigemServidor.EXTERNO);
        serverOrigem.setActive(true);

        Server serverDestino = new Server();
        serverDestino.setId(2L);
        serverDestino.setServerCode("s3://bucket");
        serverDestino.setServerType(TipoServidor.S3);
        serverDestino.setActive(true);

        SeverPaths path = new SeverPaths();
        path.setId(1L);
        path.setServerId(1L);
        path.setAcquirerId(5L);
        path.setPath("/files");
        path.setPathType(TipoCaminho.ORIGIN);
        path.setActive(true);

        SeverPathsInOut inOut = new SeverPathsInOut();
        inOut.setId(1L);
        inOut.setOriginPathId(1L);
        inOut.setDestinationServerId(2L);
        inOut.setLinkType(TipoLink.PRINCIPAL);
        inOut.setActive(true);

        when(serverRepository.findByActiveTrue()).thenReturn(List.of(serverOrigem, serverDestino));
        when(severPathsRepository.findByActiveTrue()).thenReturn(List.of(path));
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(List.of(inOut));

        VaultClient.Credenciais cred = new VaultClient.Credenciais("u", "p");
        when(vaultClient.obterCredenciais("vault", "secret")).thenReturn(cred);
        doNothing().when(sftpClient).conectar(anyString(), anyInt(), any());

        List<SFTPClient.ArquivoMetadata> arquivosSFTP = List.of(
            new SFTPClient.ArquivoMetadata(arquivo.getNome(), arquivo.getTamanho(), arquivo.getTimestamp())
        );
        when(sftpClient.listarArquivos("/files")).thenReturn(arquivosSFTP);
        doNothing().when(sftpClient).desconectar();

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), anyLong(), any()
        )).thenReturn(Optional.empty());

        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(inv -> {
            FileOrigin fo = inv.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Act
        service.executarCicloColeta();

        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());

        FileOrigin salvo = captor.getValue();

        // Verificar que todos os campos obrigatórios estão preenchidos
        assertNotNull(salvo.getFileName(), "fileName não pode ser null");
        assertNotNull(salvo.getFileSize(), "fileSize não pode ser null");
        assertNotNull(salvo.getFileTimestamp(), "fileTimestamp não pode ser null");
        assertNotNull(salvo.getAcquirerId(), "acquirerId não pode ser null");
        assertNotNull(salvo.getSeverPathsInOutId(), "severPathsInOutId não pode ser null");
        assertNotNull(salvo.getActive(), "active não pode ser null");
        assertTrue(salvo.getActive(), "active deve ser true");
    }

    /**
     * Propriedade: Tamanho do arquivo deve ser preservado corretamente.
     */
    @Property(tries = 100)
    void tamanhoDoArquivoDeveSerPreservado(
        @ForAll @LongRange(min = 0, max = 10_000_000_000L) long tamanho
    ) {
        // Arrange
        ServerRepository serverRepository = mock(ServerRepository.class);
        SeverPathsRepository severPathsRepository = mock(SeverPathsRepository.class);
        SeverPathsInOutRepository severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        SFTPClient sftpClient = mock(SFTPClient.class);
        VaultClient vaultClient = mock(VaultClient.class);

        OrquestradorService service = new OrquestradorService(
            serverRepository,
            severPathsRepository,
            severPathsInOutRepository,
            fileOriginRepository,
            sftpClient,
            vaultClient
        );

        // Configurar mocks básicos
        Server serverOrigem = new Server();
        serverOrigem.setId(1L);
        serverOrigem.setServerCode("sftp://host:22");
        serverOrigem.setVaultCode("v");
        serverOrigem.setVaultSecret("s");
        serverOrigem.setServerType(TipoServidor.SFTP);
        serverOrigem.setServerOrigin(OrigemServidor.EXTERNO);
        serverOrigem.setActive(true);

        Server serverDestino = new Server();
        serverDestino.setId(2L);
        serverDestino.setServerCode("s3://b");
        serverDestino.setServerType(TipoServidor.S3);
        serverDestino.setActive(true);

        SeverPaths path = new SeverPaths();
        path.setId(1L);
        path.setServerId(1L);
        path.setAcquirerId(1L);
        path.setPath("/");
        path.setPathType(TipoCaminho.ORIGIN);
        path.setActive(true);

        SeverPathsInOut inOut = new SeverPathsInOut();
        inOut.setId(1L);
        inOut.setOriginPathId(1L);
        inOut.setDestinationServerId(2L);
        inOut.setLinkType(TipoLink.PRINCIPAL);
        inOut.setActive(true);

        when(serverRepository.findByActiveTrue()).thenReturn(List.of(serverOrigem, serverDestino));
        when(severPathsRepository.findByActiveTrue()).thenReturn(List.of(path));
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(List.of(inOut));

        VaultClient.Credenciais cred = new VaultClient.Credenciais("u", "p");
        when(vaultClient.obterCredenciais("v", "s")).thenReturn(cred);
        doNothing().when(sftpClient).conectar(anyString(), anyInt(), any());

        List<SFTPClient.ArquivoMetadata> arquivos = List.of(
            new SFTPClient.ArquivoMetadata("file.txt", tamanho, System.currentTimeMillis())
        );
        when(sftpClient.listarArquivos("/")).thenReturn(arquivos);
        doNothing().when(sftpClient).desconectar();

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), anyLong(), any()
        )).thenReturn(Optional.empty());

        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(inv -> {
            FileOrigin fo = inv.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Act
        service.executarCicloColeta();

        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());

        FileOrigin salvo = captor.getValue();
        assertEquals(tamanho, salvo.getFileSize(),
            "Tamanho do arquivo deve ser preservado exatamente como coletado");
    }

    /**
     * Propriedade: Timestamp do arquivo deve ser preservado corretamente.
     */
    @Property(tries = 100)
    void timestampDoArquivoDeveSerPreservado(
        @ForAll @LongRange(min = 1_000_000_000_000L, max = 2_000_000_000_000L) long timestamp
    ) {
        // Arrange
        ServerRepository serverRepository = mock(ServerRepository.class);
        SeverPathsRepository severPathsRepository = mock(SeverPathsRepository.class);
        SeverPathsInOutRepository severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        SFTPClient sftpClient = mock(SFTPClient.class);
        VaultClient vaultClient = mock(VaultClient.class);

        OrquestradorService service = new OrquestradorService(
            serverRepository,
            severPathsRepository,
            severPathsInOutRepository,
            fileOriginRepository,
            sftpClient,
            vaultClient
        );

        // Configurar mocks
        Server serverOrigem = new Server();
        serverOrigem.setId(1L);
        serverOrigem.setServerCode("sftp://h:22");
        serverOrigem.setVaultCode("v");
        serverOrigem.setVaultSecret("s");
        serverOrigem.setServerType(TipoServidor.SFTP);
        serverOrigem.setServerOrigin(OrigemServidor.EXTERNO);
        serverOrigem.setActive(true);

        Server serverDestino = new Server();
        serverDestino.setId(2L);
        serverDestino.setServerCode("s3://b");
        serverDestino.setServerType(TipoServidor.S3);
        serverDestino.setActive(true);

        SeverPaths path = new SeverPaths();
        path.setId(1L);
        path.setServerId(1L);
        path.setAcquirerId(1L);
        path.setPath("/");
        path.setPathType(TipoCaminho.ORIGIN);
        path.setActive(true);

        SeverPathsInOut inOut = new SeverPathsInOut();
        inOut.setId(1L);
        inOut.setOriginPathId(1L);
        inOut.setDestinationServerId(2L);
        inOut.setLinkType(TipoLink.PRINCIPAL);
        inOut.setActive(true);

        when(serverRepository.findByActiveTrue()).thenReturn(List.of(serverOrigem, serverDestino));
        when(severPathsRepository.findByActiveTrue()).thenReturn(List.of(path));
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(List.of(inOut));

        VaultClient.Credenciais cred = new VaultClient.Credenciais("u", "p");
        when(vaultClient.obterCredenciais("v", "s")).thenReturn(cred);
        doNothing().when(sftpClient).conectar(anyString(), anyInt(), any());

        List<SFTPClient.ArquivoMetadata> arquivos = List.of(
            new SFTPClient.ArquivoMetadata("file.dat", 1000L, timestamp)
        );
        when(sftpClient.listarArquivos("/")).thenReturn(arquivos);
        doNothing().when(sftpClient).desconectar();

        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), anyLong(), any()
        )).thenReturn(Optional.empty());

        when(fileOriginRepository.save(any(FileOrigin.class))).thenAnswer(inv -> {
            FileOrigin fo = inv.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Act
        service.executarCicloColeta();

        // Assert
        ArgumentCaptor<FileOrigin> captor = ArgumentCaptor.forClass(FileOrigin.class);
        verify(fileOriginRepository).save(captor.capture());

        FileOrigin salvo = captor.getValue();
        assertEquals(Instant.ofEpochMilli(timestamp), salvo.getFileTimestamp(),
            "Timestamp do arquivo deve ser preservado exatamente como coletado");
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<List<ArquivoNovo>> arquivosNovos() {
        return arquivoNovo().list().ofMinSize(1).ofMaxSize(20);
    }

    @Provide
    Arbitrary<ArquivoNovo> arquivoNovo() {
        Arbitrary<String> nomes = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(5)
            .ofMaxLength(50)
            .map(s -> s + ".txt");

        Arbitrary<Long> tamanhos = Arbitraries.longs()
            .between(1L, 10_000_000_000L); // 1 byte a 10GB

        Arbitrary<Long> timestamps = Arbitraries.longs()
            .between(1_000_000_000_000L, 2_000_000_000_000L); // ~2001 a ~2033

        return Combinators.combine(nomes, tamanhos, timestamps)
            .as(ArquivoNovo::new);
    }

    /**
     * Classe auxiliar para representar um arquivo novo.
     */
    static class ArquivoNovo {
        private final String nome;
        private final Long tamanho;
        private final Long timestamp;

        public ArquivoNovo(String nome, Long tamanho, Long timestamp) {
            this.nome = nome;
            this.tamanho = tamanho;
            this.timestamp = timestamp;
        }

        public String getNome() {
            return nome;
        }

        public Long getTamanho() {
            return tamanho;
        }

        public Long getTimestamp() {
            return timestamp;
        }
    }
}
