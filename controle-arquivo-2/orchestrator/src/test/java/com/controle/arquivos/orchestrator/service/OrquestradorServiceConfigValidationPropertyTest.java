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
import com.controle.arquivos.common.service.JobConcurrencyService;
import com.controle.arquivos.orchestrator.dto.ConfiguracaoServidor;
import com.controle.arquivos.orchestrator.messaging.RabbitMQPublisher;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para validação de configurações do OrquestradorService.
 * 
 * Feature: controle-de-arquivos, Property 1: Validação de Configurações
 * 
 * Para qualquer configuração carregada do banco de dados, se a configuração não possui
 * servidor de origem e destino válidos, então o sistema deve registrar um erro estruturado
 * e pular essa configuração.
 * 
 * **Valida: Requisitos 1.2, 1.3**
 */
class OrquestradorServiceConfigValidationPropertyTest {

    /**
     * Propriedade 1: Validação de Configurações
     * 
     * Para qualquer configuração carregada do banco de dados, se a configuração não possui
     * servidor de origem e destino válidos, então o sistema deve registrar um erro estruturado
     * e pular essa configuração.
     * 
     * Este teste verifica que:
     * 1. Configurações válidas são aceitas e retornadas
     * 2. Configurações inválidas são registradas como erro e puladas
     * 3. O sistema não falha ao encontrar configurações inválidas
     */
    @Property(tries = 100)
    void propriedade1_configuracoesInvalidasSaoRegistradasComoErroEPuladas(
        @ForAll("configuracoesMistas") ConfiguracoesMistas configuracoes
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

        // Configurar mocks para retornar as entidades geradas
        when(serverRepository.findAll()).thenReturn(configuracoes.servers);
        when(severPathsRepository.findAll()).thenReturn(configuracoes.paths);
        when(severPathsInOutRepository.findAll()).thenReturn(configuracoes.pathsInOut);

        // Act
        List<ConfiguracaoServidor> configuracoesCarregadas = service.carregarConfiguracoes();

        // Assert
        // 1. O sistema não deve falhar ao encontrar configurações inválidas
        assertNotNull(configuracoesCarregadas);

        // 2. Apenas configurações válidas devem ser retornadas
        for (ConfiguracaoServidor config : configuracoesCarregadas) {
            // Verificar que a configuração possui servidor de origem válido
            assertNotNull(config.getServidorOrigem(), 
                "Configuração retornada deve ter servidor de origem");
            assertNotNull(config.getServidorOrigem().getServerCode(), 
                "Servidor de origem deve ter código");
            assertFalse(config.getServidorOrigem().getServerCode().trim().isEmpty(), 
                "Código do servidor de origem não deve estar vazio");
            assertNotNull(config.getServidorOrigem().getVaultCode(), 
                "Servidor de origem deve ter código Vault");
            assertFalse(config.getServidorOrigem().getVaultCode().trim().isEmpty(), 
                "Código Vault do servidor de origem não deve estar vazio");
            assertNotNull(config.getServidorOrigem().getVaultSecret(), 
                "Servidor de origem deve ter secret Vault");
            assertFalse(config.getServidorOrigem().getVaultSecret().trim().isEmpty(), 
                "Secret Vault do servidor de origem não deve estar vazio");
            assertEquals(OrigemServidor.EXTERNO, config.getServidorOrigem().getServerOrigin(), 
                "Servidor de origem deve ser EXTERNO");

            // Verificar que a configuração possui servidor de destino válido
            assertNotNull(config.getServidorDestino(), 
                "Configuração retornada deve ter servidor de destino");
            assertNotNull(config.getServidorDestino().getServerCode(), 
                "Servidor de destino deve ter código");
            assertFalse(config.getServidorDestino().getServerCode().trim().isEmpty(), 
                "Código do servidor de destino não deve estar vazio");
            assertNotNull(config.getServidorDestino().getVaultCode(), 
                "Servidor de destino deve ter código Vault");
            assertFalse(config.getServidorDestino().getVaultCode().trim().isEmpty(), 
                "Código Vault do servidor de destino não deve estar vazio");
            assertNotNull(config.getServidorDestino().getVaultSecret(), 
                "Servidor de destino deve ter secret Vault");
            assertFalse(config.getServidorDestino().getVaultSecret().trim().isEmpty(), 
                "Secret Vault do servidor de destino não deve estar vazio");

            // Verificar que a configuração possui caminho de origem válido
            assertNotNull(config.getCaminhoOrigem(), 
                "Configuração retornada deve ter caminho de origem");
            assertEquals(TipoCaminho.ORIGIN, config.getCaminhoOrigem().getPathType(), 
                "Caminho de origem deve ter tipo ORIGIN");
        }

        // 3. O número de configurações válidas deve ser <= número total de mapeamentos
        assertTrue(configuracoesCarregadas.size() <= configuracoes.pathsInOut.size(),
            "Número de configurações válidas deve ser menor ou igual ao número de mapeamentos");

        // 4. Se existem configurações inválidas, o número de configurações carregadas deve ser menor
        int configuracoesValidas = contarConfiguracoesValidas(configuracoes);
        assertEquals(configuracoesValidas, configuracoesCarregadas.size(),
            "Número de configurações carregadas deve corresponder ao número de configurações válidas");
    }

    /**
     * Conta o número de configurações válidas no conjunto gerado.
     */
    private int contarConfiguracoesValidas(ConfiguracoesMistas configuracoes) {
        int validas = 0;

        for (SeverPathsInOut inOut : configuracoes.pathsInOut) {
            // Buscar caminho de origem
            SeverPaths caminhoOrigem = configuracoes.paths.stream()
                .filter(p -> p.getId().equals(inOut.getSeverPathOriginId()))
                .findFirst()
                .orElse(null);

            // Buscar servidor de origem
            Server servidorOrigem = caminhoOrigem != null
                ? configuracoes.servers.stream()
                    .filter(s -> s.getId().equals(caminhoOrigem.getServerId()))
                    .findFirst()
                    .orElse(null)
                : null;

            // Buscar servidor de destino
            Server servidorDestino = configuracoes.servers.stream()
                .filter(s -> s.getId().equals(inOut.getSeverDestinationId()))
                .findFirst()
                .orElse(null);

            // Validar configuração
            if (isConfiguracaoValida(caminhoOrigem, servidorOrigem, servidorDestino)) {
                validas++;
            }
        }

        return validas;
    }

    /**
     * Verifica se uma configuração é válida segundo as regras de negócio.
     */
    private boolean isConfiguracaoValida(SeverPaths caminhoOrigem, Server servidorOrigem, Server servidorDestino) {
        // Validar caminho de origem
        if (caminhoOrigem == null || caminhoOrigem.getPathType() != TipoCaminho.ORIGIN) {
            return false;
        }

        // Validar servidor de origem
        if (servidorOrigem == null) {
            return false;
        }
        if (servidorOrigem.getServerOrigin() != OrigemServidor.EXTERNO) {
            return false;
        }
        if (servidorOrigem.getServerCode() == null || servidorOrigem.getServerCode().trim().isEmpty()) {
            return false;
        }
        if (servidorOrigem.getVaultCode() == null || servidorOrigem.getVaultCode().trim().isEmpty()) {
            return false;
        }
        if (servidorOrigem.getVaultSecret() == null || servidorOrigem.getVaultSecret().trim().isEmpty()) {
            return false;
        }

        // Validar servidor de destino
        if (servidorDestino == null) {
            return false;
        }
        if (servidorDestino.getServerCode() == null || servidorDestino.getServerCode().trim().isEmpty()) {
            return false;
        }
        if (servidorDestino.getVaultCode() == null || servidorDestino.getVaultCode().trim().isEmpty()) {
            return false;
        }
        if (servidorDestino.getVaultSecret() == null || servidorDestino.getVaultSecret().trim().isEmpty()) {
            return false;
        }

        return true;
    }

    // ==================== Geradores jqwik ====================

    /**
     * Gera um conjunto misto de configurações válidas e inválidas.
     */
    @Provide
    Arbitrary<ConfiguracoesMistas> configuracoesMistas() {
        return Combinators.combine(
            Arbitraries.integers().between(1, 5),  // Número de servidores
            Arbitraries.integers().between(1, 5),  // Número de caminhos
            Arbitraries.integers().between(1, 5)   // Número de mapeamentos
        ).as((numServers, numPaths, numMappings) -> {
            List<Server> servers = new ArrayList<>();
            List<SeverPaths> paths = new ArrayList<>();
            List<SeverPathsInOut> pathsInOut = new ArrayList<>();

            // Gerar servidores (mix de válidos e inválidos)
            for (int i = 0; i < numServers; i++) {
                servers.add(gerarServidor((long) (i + 1), i % 3 == 0)); // 1/3 inválidos
            }

            // Gerar caminhos (mix de válidos e inválidos)
            for (int i = 0; i < numPaths; i++) {
                long serverId = (long) ((i % numServers) + 1);
                paths.add(gerarCaminho((long) (i + 1), serverId, i % 4 == 0)); // 1/4 inválidos
            }

            // Gerar mapeamentos origem-destino
            for (int i = 0; i < numMappings; i++) {
                long pathOriginId = (long) ((i % numPaths) + 1);
                long serverDestId = (long) ((i % numServers) + 1);
                pathsInOut.add(gerarMapeamento((long) (i + 1), pathOriginId, serverDestId));
            }

            return new ConfiguracoesMistas(servers, paths, pathsInOut);
        });
    }

    /**
     * Gera um servidor (válido ou inválido conforme flag).
     */
    private Server gerarServidor(Long id, boolean invalido) {
        Server.ServerBuilder builder = Server.builder()
            .id(id)
            .active(true);

        if (invalido) {
            // Gerar servidor inválido (faltando campos obrigatórios ou com origem errada)
            int tipoInvalido = (int) (id % 5);
            switch (tipoInvalido) {
                case 0:
                    // Sem código de servidor
                    builder.serverCode(null)
                        .vaultCode("vault-code-" + id)
                        .vaultSecret("vault-secret-" + id)
                        .serverType(TipoServidor.SFTP)
                        .serverOrigin(OrigemServidor.EXTERNO);
                    break;
                case 1:
                    // Código de servidor vazio
                    builder.serverCode("   ")
                        .vaultCode("vault-code-" + id)
                        .vaultSecret("vault-secret-" + id)
                        .serverType(TipoServidor.SFTP)
                        .serverOrigin(OrigemServidor.EXTERNO);
                    break;
                case 2:
                    // Sem código Vault
                    builder.serverCode("server-" + id)
                        .vaultCode(null)
                        .vaultSecret("vault-secret-" + id)
                        .serverType(TipoServidor.SFTP)
                        .serverOrigin(OrigemServidor.EXTERNO);
                    break;
                case 3:
                    // Sem secret Vault
                    builder.serverCode("server-" + id)
                        .vaultCode("vault-code-" + id)
                        .vaultSecret(null)
                        .serverType(TipoServidor.SFTP)
                        .serverOrigin(OrigemServidor.EXTERNO);
                    break;
                case 4:
                    // Origem INTERNO (inválido para servidor de origem)
                    builder.serverCode("server-" + id)
                        .vaultCode("vault-code-" + id)
                        .vaultSecret("vault-secret-" + id)
                        .serverType(TipoServidor.SFTP)
                        .serverOrigin(OrigemServidor.INTERNO);
                    break;
            }
        } else {
            // Gerar servidor válido
            builder.serverCode("server-" + id)
                .vaultCode("vault-code-" + id)
                .vaultSecret("vault-secret-" + id)
                .serverType(TipoServidor.SFTP)
                .serverOrigin(id % 2 == 0 ? OrigemServidor.EXTERNO : OrigemServidor.INTERNO);
        }

        return builder.build();
    }

    /**
     * Gera um caminho (válido ou inválido conforme flag).
     */
    private SeverPaths gerarCaminho(Long id, Long serverId, boolean invalido) {
        SeverPaths.SeverPathsBuilder builder = SeverPaths.builder()
            .id(id)
            .serverId(serverId)
            .acquirerId(1L)
            .active(true);

        if (invalido) {
            // Gerar caminho inválido (tipo DESTINATION ao invés de ORIGIN)
            builder.path("/path/to/files/" + id)
                .pathType(TipoCaminho.DESTINATION);
        } else {
            // Gerar caminho válido
            builder.path("/path/to/files/" + id)
                .pathType(TipoCaminho.ORIGIN);
        }

        return builder.build();
    }

    /**
     * Gera um mapeamento origem-destino.
     */
    private SeverPathsInOut gerarMapeamento(Long id, Long pathOriginId, Long serverDestId) {
        return SeverPathsInOut.builder()
            .id(id)
            .severPathOriginId(pathOriginId)
            .severDestinationId(serverDestId)
            .linkType(TipoLink.PRINCIPAL)
            .active(true)
            .build();
    }

    /**
     * Classe auxiliar para agrupar configurações geradas.
     */
    static class ConfiguracoesMistas {
        final List<Server> servers;
        final List<SeverPaths> paths;
        final List<SeverPathsInOut> pathsInOut;

        ConfiguracoesMistas(List<Server> servers, List<SeverPaths> paths, List<SeverPathsInOut> pathsInOut) {
            this.servers = servers;
            this.paths = paths;
            this.pathsInOut = pathsInOut;
        }
    }
}
