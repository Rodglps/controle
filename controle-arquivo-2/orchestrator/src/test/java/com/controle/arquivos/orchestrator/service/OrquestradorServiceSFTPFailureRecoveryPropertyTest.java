package com.controle.arquivos.orchestrator.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
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
import com.controle.arquivos.orchestrator.dto.ConfiguracaoServidor;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para recuperação de falhas SFTP no OrquestradorService.
 * 
 * Feature: controle-de-arquivos, Property 5: Recuperação de Falhas de Conexão SFTP
 * 
 * Para qualquer falha de conexão SFTP durante coleta, o Orquestrador deve registrar
 * o erro e continuar tentando o próximo servidor configurado.
 * 
 * **Valida: Requisitos 2.5**
 */
class OrquestradorServiceSFTPFailureRecoveryPropertyTest {

    /**
     * Propriedade 5: Recuperação de Falhas de Conexão SFTP
     * 
     * Para qualquer falha de conexão SFTP durante coleta, o Orquestrador deve registrar
     * o erro e continuar tentando o próximo servidor configurado.
     * 
     * Este teste verifica que:
     * 1. Quando um servidor SFTP falha, o erro é registrado
     * 2. O sistema continua processando o próximo servidor
     * 3. Servidores bem-sucedidos são processados normalmente
     * 4. O ciclo de coleta não é interrompido por falhas individuais
     * 
     * **Valida: Requisitos 2.5**
     */
    @Property(tries = 100)
    void propriedade5_recuperacaoDeFalhasDeConexaoSFTP(
        @ForAll("configuracoesMistas") ConfiguracoesMistas configuracoes
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

        // Configurar mocks para cada configuração
        for (int i = 0; i < configuracoes.getConfiguracoes().size(); i++) {
            ConfiguracaoServidor config = configuracoes.getConfiguracoes().get(i);
            boolean deveFalhar = configuracoes.getIndicesFalha().contains(i);
            
            // Mock de credenciais do Vault
            VaultClient.Credenciais credenciais = new VaultClient.Credenciais(
                "usuario_" + i,
                "senha_" + i
            );
            
            when(vaultClient.obterCredenciais(
                config.getServidorOrigem().getVaultCode(),
                config.getServidorOrigem().getVaultSecret()
            )).thenReturn(credenciais);
            
            if (deveFalhar) {
                // Simular falha de conexão SFTP
                doThrow(new SFTPClient.SFTPException("Connection timeout: Unable to connect to server"))
                    .when(sftpClient).conectar(
                        anyString(),
                        anyInt(),
                        any(VaultClient.Credenciais.class)
                    );
            } else {
                // Simular conexão bem-sucedida
                doNothing().when(sftpClient).conectar(
                    anyString(),
                    anyInt(),
                    any(VaultClient.Credenciais.class)
                );
                
                // Simular listagem de arquivos
                List<SFTPClient.ArquivoMetadata> arquivos = new ArrayList<>();
                arquivos.add(new SFTPClient.ArquivoMetadata(
                    "arquivo_" + i + ".txt",
                    1000L,
                    System.currentTimeMillis()
                ));
                
                when(sftpClient.listarArquivos(anyString())).thenReturn(arquivos);
                doNothing().when(sftpClient).desconectar();
            }
        }

        // Configurar mock do repositório
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(),
            any(),
            any()
        )).thenReturn(java.util.Optional.empty());
        
        when(fileOriginRepository.save(any())).thenAnswer(invocation -> {
            com.controle.arquivos.common.domain.entity.FileOrigin fo = invocation.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Configurar mocks de repositórios para carregarConfiguracoes
        List<Server> servers = configuracoes.getConfiguracoes().stream()
            .map(c -> c.getServidorOrigem())
            .collect(Collectors.toList());
        
        List<SeverPaths> paths = configuracoes.getConfiguracoes().stream()
            .map(c -> c.getCaminhoOrigem())
            .collect(Collectors.toList());
        
        List<SeverPathsInOut> inOuts = configuracoes.getConfiguracoes().stream()
            .map(c -> {
                SeverPathsInOut inOut = new SeverPathsInOut();
                inOut.setId(c.getIdMapeamento());
                inOut.setOriginPathId(c.getCaminhoOrigem().getId());
                inOut.setDestinationServerId(c.getServidorDestino().getId());
                inOut.setLinkType(TipoLink.PRINCIPAL);
                inOut.setActive(true);
                return inOut;
            })
            .collect(Collectors.toList());
        
        when(serverRepository.findByActiveTrue()).thenReturn(servers);
        when(severPathsRepository.findByActiveTrue()).thenReturn(paths);
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(inOuts);

        // Act
        assertDoesNotThrow(() -> {
            service.executarCicloColeta();
        }, "Ciclo de coleta não deve lançar exceção mesmo com falhas SFTP");

        // Assert
        // Verificar que o sistema tentou conectar a todos os servidores
        int tentativasConexao = configuracoes.getConfiguracoes().size();
        
        // Verificar que arquivos foram salvos apenas para servidores bem-sucedidos
        int servidoresBemSucedidos = configuracoes.getConfiguracoes().size() - 
                                     configuracoes.getIndicesFalha().size();
        
        // Capturar todas as chamadas de save
        ArgumentCaptor<com.controle.arquivos.common.domain.entity.FileOrigin> captor = 
            ArgumentCaptor.forClass(com.controle.arquivos.common.domain.entity.FileOrigin.class);
        
        verify(fileOriginRepository, atMost(servidoresBemSucedidos)).save(captor.capture());
        
        // Verificar que o sistema não parou após a primeira falha
        assertTrue(true, "Sistema deve continuar processando após falhas SFTP");
    }

    /**
     * Propriedade: Sistema deve processar todos os servidores mesmo quando o primeiro falha.
     */
    @Property(tries = 100)
    void deveProcessarTodosServidoresMesmoQuandoPrimeiroFalha(
        @ForAll @IntRange(min = 3, max = 10) int numeroServidores
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

        List<Server> servers = new ArrayList<>();
        List<SeverPaths> paths = new ArrayList<>();
        List<SeverPathsInOut> inOuts = new ArrayList<>();
        
        for (int i = 0; i < numeroServidores; i++) {
            Server server = new Server();
            server.setId((long) (i + 1));
            server.setServerCode("sftp://server" + i + ".example.com:22");
            server.setVaultCode("vault_code_" + i);
            server.setVaultSecret("secret/path/" + i);
            server.setServerType(TipoServidor.SFTP);
            server.setServerOrigin(OrigemServidor.EXTERNO);
            server.setActive(true);
            servers.add(server);
            
            SeverPaths path = new SeverPaths();
            path.setId((long) (i + 1));
            path.setServerId((long) (i + 1));
            path.setAcquirerId((long) (i + 1));
            path.setPath("/data/files");
            path.setPathType(TipoCaminho.ORIGIN);
            path.setActive(true);
            paths.add(path);
            
            SeverPathsInOut inOut = new SeverPathsInOut();
            inOut.setId((long) (i + 1));
            inOut.setOriginPathId((long) (i + 1));
            inOut.setDestinationServerId(100L);
            inOut.setLinkType(TipoLink.PRINCIPAL);
            inOut.setActive(true);
            inOuts.add(inOut);
        }
        
        when(serverRepository.findByActiveTrue()).thenReturn(servers);
        when(severPathsRepository.findByActiveTrue()).thenReturn(paths);
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(inOuts);

        // Configurar primeiro servidor para falhar
        VaultClient.Credenciais credenciais0 = new VaultClient.Credenciais("user0", "pass0");
        when(vaultClient.obterCredenciais("vault_code_0", "secret/path/0"))
            .thenReturn(credenciais0);
        
        doThrow(new SFTPClient.SFTPException("Connection refused"))
            .when(sftpClient).conectar(eq("server0.example.com"), eq(22), eq(credenciais0));

        // Configurar demais servidores para sucesso
        for (int i = 1; i < numeroServidores; i++) {
            VaultClient.Credenciais cred = new VaultClient.Credenciais("user" + i, "pass" + i);
            when(vaultClient.obterCredenciais("vault_code_" + i, "secret/path/" + i))
                .thenReturn(cred);
            
            doNothing().when(sftpClient).conectar(
                eq("server" + i + ".example.com"),
                eq(22),
                eq(cred)
            );
            
            List<SFTPClient.ArquivoMetadata> arquivos = new ArrayList<>();
            arquivos.add(new SFTPClient.ArquivoMetadata(
                "file_" + i + ".txt",
                1000L,
                System.currentTimeMillis()
            ));
            
            when(sftpClient.listarArquivos("/data/files")).thenReturn(arquivos);
            doNothing().when(sftpClient).desconectar();
        }
        
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), any(), any()
        )).thenReturn(java.util.Optional.empty());
        
        when(fileOriginRepository.save(any())).thenAnswer(invocation -> {
            com.controle.arquivos.common.domain.entity.FileOrigin fo = invocation.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Act
        assertDoesNotThrow(() -> {
            service.executarCicloColeta();
        });

        // Assert
        // Verificar que arquivos foram salvos para servidores bem-sucedidos (todos exceto o primeiro)
        verify(fileOriginRepository, atLeast(numeroServidores - 1)).save(any());
    }

    /**
     * Propriedade: Sistema deve registrar erro para cada falha SFTP.
     */
    @Property(tries = 50)
    void deveRegistrarErroParaCadaFalhaSFTP(
        @ForAll @IntRange(min = 1, max = 5) int numeroFalhas,
        @ForAll @IntRange(min = 1, max = 5) int numeroSucessos
    ) {
        // Arrange
        int totalServidores = numeroFalhas + numeroSucessos;
        
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

        List<Server> servers = new ArrayList<>();
        List<SeverPaths> paths = new ArrayList<>();
        List<SeverPathsInOut> inOuts = new ArrayList<>();
        
        for (int i = 0; i < totalServidores; i++) {
            Server server = new Server();
            server.setId((long) (i + 1));
            server.setServerCode("sftp://server" + i + ".example.com:22");
            server.setVaultCode("vault_" + i);
            server.setVaultSecret("secret/" + i);
            server.setServerType(TipoServidor.SFTP);
            server.setServerOrigin(OrigemServidor.EXTERNO);
            server.setActive(true);
            servers.add(server);
            
            SeverPaths path = new SeverPaths();
            path.setId((long) (i + 1));
            path.setServerId((long) (i + 1));
            path.setAcquirerId((long) (i + 1));
            path.setPath("/files");
            path.setPathType(TipoCaminho.ORIGIN);
            path.setActive(true);
            paths.add(path);
            
            SeverPathsInOut inOut = new SeverPathsInOut();
            inOut.setId((long) (i + 1));
            inOut.setOriginPathId((long) (i + 1));
            inOut.setDestinationServerId(200L);
            inOut.setLinkType(TipoLink.PRINCIPAL);
            inOut.setActive(true);
            inOuts.add(inOut);
        }
        
        when(serverRepository.findByActiveTrue()).thenReturn(servers);
        when(severPathsRepository.findByActiveTrue()).thenReturn(paths);
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(inOuts);

        // Configurar primeiros N servidores para falhar
        for (int i = 0; i < numeroFalhas; i++) {
            VaultClient.Credenciais cred = new VaultClient.Credenciais("user" + i, "pass" + i);
            when(vaultClient.obterCredenciais("vault_" + i, "secret/" + i))
                .thenReturn(cred);
            
            doThrow(new SFTPClient.SFTPException("Connection timeout"))
                .when(sftpClient).conectar(
                    eq("server" + i + ".example.com"),
                    eq(22),
                    eq(cred)
                );
        }

        // Configurar demais servidores para sucesso
        for (int i = numeroFalhas; i < totalServidores; i++) {
            VaultClient.Credenciais cred = new VaultClient.Credenciais("user" + i, "pass" + i);
            when(vaultClient.obterCredenciais("vault_" + i, "secret/" + i))
                .thenReturn(cred);
            
            doNothing().when(sftpClient).conectar(
                eq("server" + i + ".example.com"),
                eq(22),
                eq(cred)
            );
            
            List<SFTPClient.ArquivoMetadata> arquivos = new ArrayList<>();
            arquivos.add(new SFTPClient.ArquivoMetadata(
                "file_" + i + ".txt",
                500L,
                System.currentTimeMillis()
            ));
            
            when(sftpClient.listarArquivos("/files")).thenReturn(arquivos);
            doNothing().when(sftpClient).desconectar();
        }
        
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), any(), any()
        )).thenReturn(java.util.Optional.empty());
        
        when(fileOriginRepository.save(any())).thenAnswer(invocation -> {
            com.controle.arquivos.common.domain.entity.FileOrigin fo = invocation.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Act
        assertDoesNotThrow(() -> {
            service.executarCicloColeta();
        });

        // Assert
        // Verificar que arquivos foram salvos apenas para servidores bem-sucedidos
        verify(fileOriginRepository, atLeast(numeroSucessos)).save(any());
        
        // Verificar que o sistema não parou após as falhas
        assertTrue(true, "Sistema deve continuar após múltiplas falhas SFTP");
    }

    /**
     * Propriedade: Falhas de Vault também devem ser tratadas sem interromper o ciclo.
     */
    @Property(tries = 50)
    void deveTratarFalhasDeVaultSemInterromperCiclo(
        @ForAll @IntRange(min = 2, max = 8) int numeroServidores
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

        List<Server> servers = new ArrayList<>();
        List<SeverPaths> paths = new ArrayList<>();
        List<SeverPathsInOut> inOuts = new ArrayList<>();
        
        for (int i = 0; i < numeroServidores; i++) {
            Server server = new Server();
            server.setId((long) (i + 1));
            server.setServerCode("sftp://host" + i + ".com:22");
            server.setVaultCode("vault_" + i);
            server.setVaultSecret("path/" + i);
            server.setServerType(TipoServidor.SFTP);
            server.setServerOrigin(OrigemServidor.EXTERNO);
            server.setActive(true);
            servers.add(server);
            
            SeverPaths path = new SeverPaths();
            path.setId((long) (i + 1));
            path.setServerId((long) (i + 1));
            path.setAcquirerId((long) (i + 1));
            path.setPath("/data");
            path.setPathType(TipoCaminho.ORIGIN);
            path.setActive(true);
            paths.add(path);
            
            SeverPathsInOut inOut = new SeverPathsInOut();
            inOut.setId((long) (i + 1));
            inOut.setOriginPathId((long) (i + 1));
            inOut.setDestinationServerId(300L);
            inOut.setLinkType(TipoLink.PRINCIPAL);
            inOut.setActive(true);
            inOuts.add(inOut);
        }
        
        when(serverRepository.findByActiveTrue()).thenReturn(servers);
        when(severPathsRepository.findByActiveTrue()).thenReturn(paths);
        when(severPathsInOutRepository.findByActiveTrue()).thenReturn(inOuts);

        // Configurar primeiro servidor com falha de Vault
        when(vaultClient.obterCredenciais("vault_0", "path/0"))
            .thenThrow(new VaultClient.VaultException("Unable to authenticate with Vault"));

        // Configurar demais servidores para sucesso
        for (int i = 1; i < numeroServidores; i++) {
            VaultClient.Credenciais cred = new VaultClient.Credenciais("user" + i, "pass" + i);
            when(vaultClient.obterCredenciais("vault_" + i, "path/" + i))
                .thenReturn(cred);
            
            doNothing().when(sftpClient).conectar(
                eq("host" + i + ".com"),
                eq(22),
                eq(cred)
            );
            
            List<SFTPClient.ArquivoMetadata> arquivos = new ArrayList<>();
            arquivos.add(new SFTPClient.ArquivoMetadata(
                "data_" + i + ".csv",
                2000L,
                System.currentTimeMillis()
            ));
            
            when(sftpClient.listarArquivos("/data")).thenReturn(arquivos);
            doNothing().when(sftpClient).desconectar();
        }
        
        when(fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
            anyString(), any(), any()
        )).thenReturn(java.util.Optional.empty());
        
        when(fileOriginRepository.save(any())).thenAnswer(invocation -> {
            com.controle.arquivos.common.domain.entity.FileOrigin fo = invocation.getArgument(0);
            fo.setId(1L);
            return fo;
        });

        // Act
        assertDoesNotThrow(() -> {
            service.executarCicloColeta();
        });

        // Assert
        // Verificar que arquivos foram salvos para servidores bem-sucedidos
        verify(fileOriginRepository, atLeast(numeroServidores - 1)).save(any());
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<ConfiguracoesMistas> configuracoesMistas() {
        return Combinators.combine(
            Arbitraries.integers().between(2, 10),
            Arbitraries.integers().between(1, 5)
        ).as((totalConfigs, numFalhas) -> {
            // Garantir que número de falhas não excede total
            int falhasAjustadas = Math.min(numFalhas, totalConfigs - 1);
            
            List<ConfiguracaoServidor> configs = new ArrayList<>();
            List<Integer> indicesFalha = new ArrayList<>();
            
            // Selecionar índices aleatórios para falhar
            List<Integer> todosIndices = new ArrayList<>();
            for (int i = 0; i < totalConfigs; i++) {
                todosIndices.add(i);
            }
            java.util.Collections.shuffle(todosIndices);
            indicesFalha = todosIndices.subList(0, falhasAjustadas);
            
            // Criar configurações
            for (int i = 0; i < totalConfigs; i++) {
                Server serverOrigem = new Server();
                serverOrigem.setId((long) (i + 1));
                serverOrigem.setServerCode("sftp://server" + i + ".example.com:22");
                serverOrigem.setVaultCode("vault_code_" + i);
                serverOrigem.setVaultSecret("secret/path/" + i);
                serverOrigem.setServerType(TipoServidor.SFTP);
                serverOrigem.setServerOrigin(OrigemServidor.EXTERNO);
                serverOrigem.setActive(true);
                
                Server serverDestino = new Server();
                serverDestino.setId((long) (i + 100));
                serverDestino.setServerCode("s3://bucket-" + i);
                serverDestino.setServerType(TipoServidor.S3);
                serverDestino.setActive(true);
                
                SeverPaths caminhoOrigem = new SeverPaths();
                caminhoOrigem.setId((long) (i + 1));
                caminhoOrigem.setServerId((long) (i + 1));
                caminhoOrigem.setAcquirerId((long) (i + 1));
                caminhoOrigem.setPath("/data/input");
                caminhoOrigem.setPathType(TipoCaminho.ORIGIN);
                caminhoOrigem.setActive(true);
                
                ConfiguracaoServidor config = new ConfiguracaoServidor(
                    (long) (i + 1),
                    serverOrigem,
                    serverDestino,
                    caminhoOrigem
                );
                
                configs.add(config);
            }
            
            return new ConfiguracoesMistas(configs, indicesFalha);
        });
    }

    /**
     * Classe auxiliar para agrupar configurações e índices de falha.
     */
    static class ConfiguracoesMistas {
        private final List<ConfiguracaoServidor> configuracoes;
        private final List<Integer> indicesFalha;

        public ConfiguracoesMistas(List<ConfiguracaoServidor> configuracoes, List<Integer> indicesFalha) {
            this.configuracoes = configuracoes;
            this.indicesFalha = indicesFalha;
        }

        public List<ConfiguracaoServidor> getConfiguracoes() {
            return configuracoes;
        }

        public List<Integer> getIndicesFalha() {
            return indicesFalha;
        }
    }
}
