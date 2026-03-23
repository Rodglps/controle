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
import com.controle.arquivos.orchestrator.messaging.RabbitMQPublisher;
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
 * Testes baseados em propriedades para coleta de metadados do OrquestradorService.
 * 
 * Feature: controle-de-arquivos, Property 4: Coleta de Metadados
 * 
 * Para qualquer arquivo novo encontrado em servidor SFTP, o Orquestrador deve coletar
 * e registrar nome, tamanho e timestamp do arquivo.
 * 
 * **Valida: Requisitos 2.4**
 */
class OrquestradorServiceMetadataCollectionPropertyTest {

    /**
     * Propriedade 4: Coleta de Metadados
     * 
     * Para qualquer arquivo novo encontrado em servidor SFTP, o Orquestrador deve coletar
     * e registrar nome, tamanho e timestamp do arquivo.
     * 
     * Este teste verifica que:
     * 1. Nome do arquivo é coletado corretamente
     * 2. Tamanho do arquivo é coletado corretamente
     * 3. Timestamp do arquivo é coletado corretamente
     * 4. Todos os metadados são registrados no banco de dados
     */
    @Property(tries = 100)
    void propriedade4_metadadosDeArquivosSaoColetadosCorretamente(
        @ForAll("arquivosComMetadados") List<SFTPClient.ArquivoMetadata> arquivos
    ) {
        // Arrange
        ServerRepository serverRepository = mock(ServerRepository.class);
        SeverPathsRepository severPathsRepository = mock(SeverPathsRepository.class);
        SeverPathsInOutRepository severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        SFTPClient sftpClient = mock(SFTPClient.class);
        VaultClient vaultClient = mock(VaultClient.class);
        RabbitMQPublisher rabbitMQPublisher = mock(RabbitMQPublisher.class);
        JobConcurrencyService jobConcurrencyService = mock(JobConcurrencyService.class);

        OrquestradorService service = new OrquestradorService(
            serverRepository,
            severPathsRepository,
            severPathsInOutRepository,
            fileOriginRepository,
            sftpClient,
            vaultClient,
            rabbitMQPublisher,
            jobConcurrencyService
        );

        // Criar configuração válida
        Server servidor = criarServidorValido(1L);
        SeverPaths caminho = criarCaminhoValido(1L, 1L);
        SeverPathsInOut mapeamento = criarMapeamento(1L, 1L, 2L);

        when(serverRepository.findAll()).thenReturn(List.of(servidor, criarServidorValido(2L)));
        when(severPathsRepository.findAll()).thenReturn(List.of(caminho));
        when(severPathsInOutRepository.findAll()).thenReturn(List.of(mapeamento));

        // Configurar mocks para SFTP
        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);
        when(sftpClient.listarArquivos(anyString())).thenReturn(arquivos);

        // Configurar mock para FileOriginRepository - nenhum arquivo existe previamente
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), anyLong(), any(Instant.class)
        )).thenReturn(Optional.empty());

        // Capturar arquivos salvos
        ArgumentCaptor<FileOrigin> fileOriginCaptor = ArgumentCaptor.forClass(FileOrigin.class);
        when(fileOriginRepository.save(fileOriginCaptor.capture())).thenAnswer(invocation -> {
            FileOrigin fo = invocation.getArgument(0);
            // Simular geração de ID pelo banco
            return FileOrigin.builder()
                .id(1L)
                .fileName(fo.getFileName())
                .fileSize(fo.getFileSize())
                .fileTimestamp(fo.getFileTimestamp())
                .acquirerId(fo.getAcquirerId())
                .severPathsInOutId(fo.getSeverPathsInOutId())
                .active(fo.isActive())
                .build();
        });

        // Act
        service.executarCicloColeta();

        // Assert
        List<FileOrigin> arquivosSalvos = fileOriginCaptor.getAllValues();

        // Verificar que o número de arquivos salvos corresponde ao número de arquivos listados
        assertEquals(arquivos.size(), arquivosSalvos.size(),
            "Número de arquivos salvos deve corresponder ao número de arquivos listados");

        // Verificar que cada arquivo foi salvo com os metadados corretos
        for (int i = 0; i < arquivos.size(); i++) {
            SFTPClient.ArquivoMetadata arquivoOriginal = arquivos.get(i);
            FileOrigin arquivoSalvo = arquivosSalvos.get(i);

            // 1. Verificar que o nome foi coletado corretamente
            assertEquals(arquivoOriginal.getNome(), arquivoSalvo.getFileName(),
                String.format("Nome do arquivo deve ser '%s'", arquivoOriginal.getNome()));

            // 2. Verificar que o tamanho foi coletado corretamente
            assertEquals(arquivoOriginal.getTamanho(), arquivoSalvo.getFileSize(),
                String.format("Tamanho do arquivo '%s' deve ser %d bytes",
                    arquivoOriginal.getNome(), arquivoOriginal.getTamanho()));

            // 3. Verificar que o timestamp foi coletado corretamente
            Instant timestampEsperado = Instant.ofEpochMilli(arquivoOriginal.getTimestamp());
            assertEquals(timestampEsperado, arquivoSalvo.getFileTimestamp(),
                String.format("Timestamp do arquivo '%s' deve ser %s",
                    arquivoOriginal.getNome(), timestampEsperado));

            // 4. Verificar que outros campos obrigatórios foram preenchidos
            assertNotNull(arquivoSalvo.getAcquirerId(),
                "AcquirerId deve ser preenchido");
            assertNotNull(arquivoSalvo.getSeverPathsInOutId(),
                "SeverPathsInOutId deve ser preenchido");
            assertTrue(arquivoSalvo.isActive(),
                "Arquivo deve estar ativo");
        }

        // Verificar que o SFTP foi conectado e desconectado corretamente
        verify(sftpClient, times(1)).conectar(anyString(), anyInt(), any(VaultClient.Credenciais.class));
        verify(sftpClient, times(1)).listarArquivos(anyString());
        verify(sftpClient, times(1)).desconectar();
    }

    /**
     * Propriedade adicional: Metadados preservam precisão
     * 
     * Verifica que valores extremos de tamanho e timestamp são preservados corretamente.
     */
    @Property(tries = 50)
    void metadadosPreservamPrecisao(
        @ForAll("arquivosComValoresExtremos") List<SFTPClient.ArquivoMetadata> arquivos
    ) {
        // Arrange
        ServerRepository serverRepository = mock(ServerRepository.class);
        SeverPathsRepository severPathsRepository = mock(SeverPathsRepository.class);
        SeverPathsInOutRepository severPathsInOutRepository = mock(SeverPathsInOutRepository.class);
        FileOriginRepository fileOriginRepository = mock(FileOriginRepository.class);
        SFTPClient sftpClient = mock(SFTPClient.class);
        VaultClient vaultClient = mock(VaultClient.class);
        RabbitMQPublisher rabbitMQPublisher = mock(RabbitMQPublisher.class);
        JobConcurrencyService jobConcurrencyService = mock(JobConcurrencyService.class);

        OrquestradorService service = new OrquestradorService(
            serverRepository,
            severPathsRepository,
            severPathsInOutRepository,
            fileOriginRepository,
            sftpClient,
            vaultClient,
            rabbitMQPublisher,
            jobConcurrencyService
        );

        // Criar configuração válida
        Server servidor = criarServidorValido(1L);
        SeverPaths caminho = criarCaminhoValido(1L, 1L);
        SeverPathsInOut mapeamento = criarMapeamento(1L, 1L, 2L);

        when(serverRepository.findAll()).thenReturn(List.of(servidor, criarServidorValido(2L)));
        when(severPathsRepository.findAll()).thenReturn(List.of(caminho));
        when(severPathsInOutRepository.findAll()).thenReturn(List.of(mapeamento));

        VaultClient.Credenciais credenciais = new VaultClient.Credenciais("user", "pass");
        when(vaultClient.obterCredenciais(anyString(), anyString())).thenReturn(credenciais);
        when(sftpClient.listarArquivos(anyString())).thenReturn(arquivos);
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), anyLong(), any(Instant.class)
        )).thenReturn(Optional.empty());

        ArgumentCaptor<FileOrigin> fileOriginCaptor = ArgumentCaptor.forClass(FileOrigin.class);
        when(fileOriginRepository.save(fileOriginCaptor.capture())).thenAnswer(invocation -> {
            FileOrigin fo = invocation.getArgument(0);
            return FileOrigin.builder()
                .id(1L)
                .fileName(fo.getFileName())
                .fileSize(fo.getFileSize())
                .fileTimestamp(fo.getFileTimestamp())
                .acquirerId(fo.getAcquirerId())
                .severPathsInOutId(fo.getSeverPathsInOutId())
                .active(fo.isActive())
                .build();
        });

        // Act
        service.executarCicloColeta();

        // Assert
        List<FileOrigin> arquivosSalvos = fileOriginCaptor.getAllValues();

        for (int i = 0; i < arquivos.size(); i++) {
            SFTPClient.ArquivoMetadata arquivoOriginal = arquivos.get(i);
            FileOrigin arquivoSalvo = arquivosSalvos.get(i);

            // Verificar precisão de valores extremos
            assertEquals(arquivoOriginal.getTamanho(), arquivoSalvo.getFileSize(),
                "Tamanho deve ser preservado com precisão, mesmo para valores extremos");

            Instant timestampEsperado = Instant.ofEpochMilli(arquivoOriginal.getTimestamp());
            assertEquals(timestampEsperado, arquivoSalvo.getFileTimestamp(),
                "Timestamp deve ser preservado com precisão, mesmo para valores extremos");
        }
    }

    // ==================== Geradores jqwik ====================

    /**
     * Gera lista de arquivos com metadados variados.
     */
    @Provide
    Arbitrary<List<SFTPClient.ArquivoMetadata>> arquivosComMetadados() {
        return Arbitraries.integers().between(1, 10).flatMap(numArquivos -> {
            List<Arbitrary<SFTPClient.ArquivoMetadata>> arquivoArbitraries = new ArrayList<>();
            
            for (int i = 0; i < numArquivos; i++) {
                Arbitrary<SFTPClient.ArquivoMetadata> arquivoArbitrary = Combinators.combine(
                    gerarNomeArquivo(),
                    gerarTamanhoArquivo(),
                    gerarTimestamp()
                ).as(SFTPClient.ArquivoMetadata::new);
                
                arquivoArbitraries.add(arquivoArbitrary);
            }
            
            return Arbitraries.of(arquivoArbitraries).list().ofSize(numArquivos);
        }).flatMap(list -> {
            // Combinar todos os Arbitraries em uma lista
            return Combinators.combine(list).as(objects -> {
                List<SFTPClient.ArquivoMetadata> result = new ArrayList<>();
                for (Object obj : objects) {
                    result.add((SFTPClient.ArquivoMetadata) obj);
                }
                return result;
            });
        });
    }

    /**
     * Gera lista de arquivos com valores extremos para testar precisão.
     */
    @Provide
    Arbitrary<List<SFTPClient.ArquivoMetadata>> arquivosComValoresExtremos() {
        return Arbitraries.integers().between(1, 5).flatMap(numArquivos -> {
            List<Arbitrary<SFTPClient.ArquivoMetadata>> arquivoArbitraries = new ArrayList<>();
            
            for (int i = 0; i < numArquivos; i++) {
                Arbitrary<SFTPClient.ArquivoMetadata> arquivoArbitrary = Combinators.combine(
                    gerarNomeArquivo(),
                    gerarTamanhoExtremo(),
                    gerarTimestampExtremo()
                ).as(SFTPClient.ArquivoMetadata::new);
                
                arquivoArbitraries.add(arquivoArbitrary);
            }
            
            return Arbitraries.of(arquivoArbitraries).list().ofSize(numArquivos);
        }).flatMap(list -> {
            return Combinators.combine(list).as(objects -> {
                List<SFTPClient.ArquivoMetadata> result = new ArrayList<>();
                for (Object obj : objects) {
                    result.add((SFTPClient.ArquivoMetadata) obj);
                }
                return result;
            });
        });
    }

    /**
     * Gera nomes de arquivo variados.
     */
    private Arbitrary<String> gerarNomeArquivo() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
            Arbitraries.of("txt", "csv", "json", "xml", "ofx", "dat")
        ).as((nome, extensao) -> nome + "." + extensao);
    }

    /**
     * Gera tamanhos de arquivo variados (0 bytes a 10GB).
     */
    private Arbitrary<Long> gerarTamanhoArquivo() {
        return Arbitraries.longs().between(0L, 10L * 1024 * 1024 * 1024); // 0 a 10GB
    }

    /**
     * Gera tamanhos extremos (muito pequenos ou muito grandes).
     */
    private Arbitrary<Long> gerarTamanhoExtremo() {
        return Arbitraries.of(
            0L,                           // Arquivo vazio
            1L,                           // 1 byte
            Long.MAX_VALUE / 2,           // Arquivo muito grande
            5L * 1024 * 1024 * 1024       // 5GB
        );
    }

    /**
     * Gera timestamps variados (últimos 5 anos).
     */
    private Arbitrary<Long> gerarTimestamp() {
        long agora = System.currentTimeMillis();
        long cincoAnosAtras = agora - (5L * 365 * 24 * 60 * 60 * 1000);
        return Arbitraries.longs().between(cincoAnosAtras, agora);
    }

    /**
     * Gera timestamps extremos.
     */
    private Arbitrary<Long> gerarTimestampExtremo() {
        return Arbitraries.of(
            0L,                           // Epoch
            1000000000000L,               // 2001-09-09
            System.currentTimeMillis(),   // Agora
            2000000000000L                // 2033-05-18
        );
    }

    // ==================== Métodos auxiliares ====================

    private Server criarServidorValido(Long id) {
        return Server.builder()
            .id(id)
            .serverCode("sftp-server-" + id + ":22")
            .vaultCode("vault-code-" + id)
            .vaultSecret("vault-secret-" + id)
            .serverType(TipoServidor.SFTP)
            .serverOrigin(id == 1L ? OrigemServidor.EXTERNO : OrigemServidor.INTERNO)
            .active(true)
            .build();
    }

    private SeverPaths criarCaminhoValido(Long id, Long serverId) {
        return SeverPaths.builder()
            .id(id)
            .serverId(serverId)
            .acquirerId(1L)
            .path("/path/to/files")
            .pathType(TipoCaminho.ORIGIN)
            .active(true)
            .build();
    }

    private SeverPathsInOut criarMapeamento(Long id, Long pathOriginId, Long serverDestId) {
        return SeverPathsInOut.builder()
            .id(id)
            .severPathOriginId(pathOriginId)
            .severDestinationId(serverDestId)
            .linkType(TipoLink.PRINCIPAL)
            .active(true)
            .build();
    }
}
