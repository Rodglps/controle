package com.controle.arquivos.orchestrator.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import com.controle.arquivos.common.domain.enums.OrigemServidor;
import com.controle.arquivos.common.domain.enums.TipoCaminho;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.common.repository.ServerRepository;
import com.controle.arquivos.common.repository.SeverPathsInOutRepository;
import com.controle.arquivos.common.repository.SeverPathsRepository;
import com.controle.arquivos.common.service.JobConcurrencyService;
import com.controle.arquivos.orchestrator.dto.ConfiguracaoServidor;
import com.controle.arquivos.orchestrator.dto.MensagemProcessamento;
import com.controle.arquivos.orchestrator.messaging.RabbitMQPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço responsável pela orquestração da coleta de arquivos.
 * Carrega configurações de servidores SFTP do banco de dados e gerencia o ciclo de coleta.
 */
@Service
public class OrquestradorService {

    private static final Logger logger = LoggerFactory.getLogger(OrquestradorService.class);

    private static final String JOB_NAME = "ORCHESTRATOR_FILE_COLLECTION";

    private final ServerRepository serverRepository;
    private final SeverPathsRepository severPathsRepository;
    private final SeverPathsInOutRepository severPathsInOutRepository;
    private final FileOriginRepository fileOriginRepository;
    private final SFTPClient sftpClient;
    private final VaultClient vaultClient;
    private final RabbitMQPublisher rabbitMQPublisher;
    private final JobConcurrencyService jobConcurrencyService;

    public OrquestradorService(
            ServerRepository serverRepository,
            SeverPathsRepository severPathsRepository,
            SeverPathsInOutRepository severPathsInOutRepository,
            FileOriginRepository fileOriginRepository,
            SFTPClient sftpClient,
            VaultClient vaultClient,
            RabbitMQPublisher rabbitMQPublisher,
            JobConcurrencyService jobConcurrencyService) {
        this.serverRepository = serverRepository;
        this.severPathsRepository = severPathsRepository;
        this.severPathsInOutRepository = severPathsInOutRepository;
        this.fileOriginRepository = fileOriginRepository;
        this.sftpClient = sftpClient;
        this.vaultClient = vaultClient;
        this.rabbitMQPublisher = rabbitMQPublisher;
        this.jobConcurrencyService = jobConcurrencyService;
    }

    /**
     * Carrega configurações de servidores SFTP do banco de dados.
     * Valida que cada configuração possui servidor de origem e destino válidos.
     * Configurações inválidas são registradas como erro e puladas.
     *
     * @return Lista de configurações válidas
     */
    public List<ConfiguracaoServidor> carregarConfiguracoes() {
        logger.info("Iniciando carregamento de configurações de servidores");

        try {
            // Carregar todas as entidades ativas do banco
            List<Server> servers = serverRepository.findAll().stream()
                    .filter(s -> s.getActive() != null && s.getActive())
                    .collect(Collectors.toList());

            List<SeverPaths> paths = severPathsRepository.findAll().stream()
                    .filter(p -> p.getActive() != null && p.getActive())
                    .collect(Collectors.toList());

            List<SeverPathsInOut> pathsInOut = severPathsInOutRepository.findAll().stream()
                    .filter(p -> p.getActive() != null && p.getActive())
                    .collect(Collectors.toList());

            logger.info("Carregadas {} servidores, {} caminhos e {} mapeamentos origem-destino",
                    servers.size(), paths.size(), pathsInOut.size());

            // Criar mapas para acesso rápido
            Map<Long, Server> serverMap = servers.stream()
                    .collect(Collectors.toMap(Server::getId, s -> s));

            Map<Long, List<SeverPaths>> pathsByServer = paths.stream()
                    .collect(Collectors.groupingBy(SeverPaths::getServerId));

            Map<Long, List<SeverPathsInOut>> inOutByOriginPath = pathsInOut.stream()
                    .collect(Collectors.groupingBy(SeverPathsInOut::getSeverPathOriginId));

            // Construir configurações válidas
            List<ConfiguracaoServidor> configuracoes = new ArrayList<>();

            for (SeverPathsInOut inOut : pathsInOut) {
                try {
                    ConfiguracaoServidor config = construirConfiguracao(
                            inOut, serverMap, paths, pathsByServer);

                    if (validarConfiguracao(config)) {
                        configuracoes.add(config);
                    }
                } catch (Exception e) {
                    logger.error("Erro ao construir configuração para mapeamento ID {}: {}",
                            inOut.getId(), e.getMessage(), e);
                }
            }

            logger.info("Carregamento concluído: {} configurações válidas de {} mapeamentos",
                    configuracoes.size(), pathsInOut.size());

            return configuracoes;

        } catch (Exception e) {
            logger.error("Erro crítico ao carregar configurações: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Constrói uma configuração a partir das entidades do banco.
     */
    private ConfiguracaoServidor construirConfiguracao(
            SeverPathsInOut inOut,
            Map<Long, Server> serverMap,
            List<SeverPaths> allPaths,
            Map<Long, List<SeverPaths>> pathsByServer) {

        // Buscar caminho de origem
        SeverPaths caminhoOrigem = allPaths.stream()
                .filter(p -> p.getId().equals(inOut.getSeverPathOriginId()))
                .findFirst()
                .orElse(null);

        // Buscar servidor de origem
        Server servidorOrigem = caminhoOrigem != null
                ? serverMap.get(caminhoOrigem.getServerId())
                : null;

        // Buscar servidor de destino
        Server servidorDestino = serverMap.get(inOut.getSeverDestinationId());

        return ConfiguracaoServidor.builder()
                .idMapeamento(inOut.getId())
                .caminhoOrigem(caminhoOrigem)
                .servidorOrigem(servidorOrigem)
                .servidorDestino(servidorDestino)
                .tipoLink(inOut.getLinkType())
                .build();
    }

    /**
     * Valida se uma configuração possui servidor de origem e destino válidos.
     * Registra erro estruturado para configurações inválidas.
     *
     * @param config Configuração a ser validada
     * @return true se a configuração é válida, false caso contrário
     */
    private boolean validarConfiguracao(ConfiguracaoServidor config) {
        List<String> erros = new ArrayList<>();

        // Validar caminho de origem
        if (config.getCaminhoOrigem() == null) {
            erros.add("Caminho de origem não encontrado");
        } else if (config.getCaminhoOrigem().getPathType() != TipoCaminho.ORIGIN) {
            erros.add(String.format("Caminho de origem possui tipo inválido: %s (esperado: ORIGIN)",
                    config.getCaminhoOrigem().getPathType()));
        }

        // Validar servidor de origem
        if (config.getServidorOrigem() == null) {
            erros.add("Servidor de origem não encontrado");
        } else {
            // Validar que servidor de origem é EXTERNO
            if (config.getServidorOrigem().getServerOrigin() != OrigemServidor.EXTERNO) {
                erros.add(String.format("Servidor de origem deve ser EXTERNO, mas é: %s",
                        config.getServidorOrigem().getServerOrigin()));
            }

            // Validar campos obrigatórios do servidor de origem
            if (config.getServidorOrigem().getServerCode() == null ||
                    config.getServidorOrigem().getServerCode().trim().isEmpty()) {
                erros.add("Servidor de origem sem código");
            }

            if (config.getServidorOrigem().getVaultCode() == null ||
                    config.getServidorOrigem().getVaultCode().trim().isEmpty()) {
                erros.add("Servidor de origem sem código Vault");
            }

            if (config.getServidorOrigem().getVaultSecret() == null ||
                    config.getServidorOrigem().getVaultSecret().trim().isEmpty()) {
                erros.add("Servidor de origem sem caminho de secret Vault");
            }
        }

        // Validar servidor de destino
        if (config.getServidorDestino() == null) {
            erros.add("Servidor de destino não encontrado");
        } else {
            // Validar campos obrigatórios do servidor de destino
            if (config.getServidorDestino().getServerCode() == null ||
                    config.getServidorDestino().getServerCode().trim().isEmpty()) {
                erros.add("Servidor de destino sem código");
            }

            if (config.getServidorDestino().getVaultCode() == null ||
                    config.getServidorDestino().getVaultCode().trim().isEmpty()) {
                erros.add("Servidor de destino sem código Vault");
            }

            if (config.getServidorDestino().getVaultSecret() == null ||
                    config.getServidorDestino().getVaultSecret().trim().isEmpty()) {
                erros.add("Servidor de destino sem caminho de secret Vault");
            }
        }

        // Se há erros, registrar e retornar false
        if (!erros.isEmpty()) {
            logger.error("Configuração inválida para mapeamento ID {}: {}",
                    config.getIdMapeamento(),
                    String.join("; ", erros));
            return false;
        }

        return true;
    }

    /**
     * Executa um ciclo completo de coleta de arquivos.
     * Itera sobre todas as configurações, conecta aos servidores SFTP,
     * lista arquivos, verifica deduplicação e registra novos arquivos.
     * 
     * Controla concorrência usando JobConcurrencyService:
     * - Verifica se existe execução RUNNING antes de iniciar
     * - Cria registro RUNNING ao iniciar
     * - Atualiza para COMPLETED ao finalizar com sucesso
     * - Atualiza para PENDING em caso de falha
     * 
     * **Valida: Requisitos 5.1, 5.2, 5.3, 5.4, 5.5**
     */
    public void executarCicloColeta() {
        logger.info("Iniciando ciclo de coleta de arquivos");
        
        // Verificar se existe execução ativa (Requisito 5.1, 5.2)
        if (jobConcurrencyService.verificarExecucaoAtiva(JOB_NAME)) {
            logger.warn("Execução RUNNING já existe para job={}. Cancelando ciclo atual.", JOB_NAME);
            return;
        }
        
        // Criar registro RUNNING ao iniciar (Requisito 5.3)
        Long controlId = jobConcurrencyService.iniciarExecucao(JOB_NAME);
        logger.info("Controle de concorrência criado: control_id={}, status=RUNNING", controlId);
        
        try {
            // Carregar configurações válidas
            List<ConfiguracaoServidor> configuracoes = carregarConfiguracoes();
            
            if (configuracoes.isEmpty()) {
                logger.warn("Nenhuma configuração válida encontrada. Ciclo de coleta abortado.");
                // Finalizar com sucesso mesmo sem configurações
                jobConcurrencyService.finalizarExecucao(JOB_NAME, true);
                jobConcurrencyService.registrarDataExecucao(JOB_NAME);
                return;
            }
            
            int totalArquivosColetados = 0;
            int totalArquivosIgnorados = 0;
            int totalErros = 0;
            
            // Processar cada configuração
            for (ConfiguracaoServidor config : configuracoes) {
                try {
                    logger.info("Processando configuração ID {}: servidor {} - caminho {}",
                            config.getIdMapeamento(),
                            config.getServidorOrigem().getServerCode(),
                            config.getCaminhoOrigem().getPath());
                    
                    ResultadoColeta resultado = coletarArquivosDeConfiguracao(config);
                    
                    totalArquivosColetados += resultado.getArquivosColetados();
                    totalArquivosIgnorados += resultado.getArquivosIgnorados();
                    totalErros += resultado.getErros();
                    
                } catch (Exception e) {
                    logger.error("Erro ao processar configuração ID {}: {}",
                            config.getIdMapeamento(), e.getMessage(), e);
                    totalErros++;
                }
            }
            
            logger.info("Ciclo de coleta concluído: {} arquivos coletados, {} ignorados, {} erros",
                    totalArquivosColetados, totalArquivosIgnorados, totalErros);
            
            // Atualizar para COMPLETED ao finalizar com sucesso (Requisito 5.4)
            jobConcurrencyService.finalizarExecucao(JOB_NAME, true);
            jobConcurrencyService.registrarDataExecucao(JOB_NAME);
            logger.info("Controle de concorrência atualizado: control_id={}, status=COMPLETED", controlId);
            
        } catch (Exception e) {
            // Atualizar para PENDING em caso de falha (Requisito 5.5)
            logger.error("Erro crítico durante ciclo de coleta: {}", e.getMessage(), e);
            try {
                jobConcurrencyService.finalizarExecucao(JOB_NAME, false);
                logger.info("Controle de concorrência atualizado: control_id={}, status=PENDING", controlId);
            } catch (Exception ex) {
                logger.error("Erro ao atualizar controle de concorrência para PENDING: {}", ex.getMessage(), ex);
            }
            throw e;
        }
    }

    /**
     * Coleta arquivos de uma configuração específica.
     */
    private ResultadoColeta coletarArquivosDeConfiguracao(ConfiguracaoServidor config) {
        ResultadoColeta resultado = new ResultadoColeta();
        List<FileOrigin> arquivosColetados = new ArrayList<>();
        
        try {
            // Obter credenciais do Vault
            VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(
                    config.getServidorOrigem().getVaultCode(),
                    config.getServidorOrigem().getVaultSecret()
            );
            
            // Conectar ao servidor SFTP
            String host = extrairHost(config.getServidorOrigem().getServerCode());
            int port = extrairPorta(config.getServidorOrigem().getServerCode());
            
            sftpClient.conectar(host, port, credenciais);
            
            try {
                // Listar arquivos no caminho configurado
                List<SFTPClient.ArquivoMetadata> arquivos = sftpClient.listarArquivos(
                        config.getCaminhoOrigem().getPath()
                );
                
                logger.info("Encontrados {} arquivos no servidor {} - caminho {}",
                        arquivos.size(),
                        config.getServidorOrigem().getServerCode(),
                        config.getCaminhoOrigem().getPath());
                
                // Processar cada arquivo
                for (SFTPClient.ArquivoMetadata arquivo : arquivos) {
                    try {
                        Optional<FileOrigin> arquivoRegistrado = processarArquivo(arquivo, config);
                        if (arquivoRegistrado.isPresent()) {
                            resultado.incrementarColetados();
                            arquivosColetados.add(arquivoRegistrado.get());
                        } else {
                            resultado.incrementarIgnorados();
                        }
                    } catch (Exception e) {
                        logger.error("Erro ao processar arquivo {}: {}",
                                arquivo.getNome(), e.getMessage(), e);
                        resultado.incrementarErros();
                    }
                }
                
                // Publicar mensagens para arquivos coletados
                if (!arquivosColetados.isEmpty()) {
                    publicarMensagensProcessamento(arquivosColetados, config);
                }
                
            } finally {
                // Sempre desconectar do SFTP
                sftpClient.desconectar();
            }
            
        } catch (VaultClient.VaultException e) {
            logger.error("Erro ao obter credenciais do Vault para configuração ID {}: {}",
                    config.getIdMapeamento(), e.getMessage());
            resultado.incrementarErros();
            
        } catch (SFTPClient.SFTPException e) {
            logger.error("Erro de conexão SFTP para configuração ID {}: {}",
                    config.getIdMapeamento(), e.getMessage());
            resultado.incrementarErros();
            
        } catch (Exception e) {
            logger.error("Erro inesperado ao coletar arquivos da configuração ID {}: {}",
                    config.getIdMapeamento(), e.getMessage(), e);
            resultado.incrementarErros();
        }
        
        return resultado;
    }

    /**
     * Processa um arquivo individual: verifica deduplicação e registra se novo.
     * 
     * @return Optional com FileOrigin se o arquivo foi coletado (novo), Optional.empty() se foi ignorado (duplicado)
     */
    @Transactional
    private Optional<FileOrigin> processarArquivo(SFTPClient.ArquivoMetadata arquivo, ConfiguracaoServidor config) {
        // Converter timestamp de milissegundos para Instant
        Instant fileTimestamp = Instant.ofEpochMilli(arquivo.getTimestamp());
        
        // Verificar deduplicação: buscar arquivo existente
        Optional<FileOrigin> existente = fileOriginRepository.findByFileNameAndAcquirerIdAndFileTimestamp(
                arquivo.getNome(),
                config.getCaminhoOrigem().getAcquirerId(),
                fileTimestamp
        );
        
        if (existente.isPresent()) {
            logger.debug("Arquivo {} já existe no banco de dados. Ignorando.",
                    arquivo.getNome());
            return Optional.empty();
        }
        
        // Arquivo é novo - registrar em file_origin
        try {
            FileOrigin novoArquivo = FileOrigin.builder()
                    .fileName(arquivo.getNome())
                    .fileSize(arquivo.getTamanho())
                    .fileTimestamp(fileTimestamp)
                    .acquirerId(config.getCaminhoOrigem().getAcquirerId())
                    .severPathsInOutId(config.getIdMapeamento())
                    .active(true)
                    .build();
            
            fileOriginRepository.save(novoArquivo);
            
            logger.info("Arquivo {} registrado com sucesso. ID: {}, Tamanho: {} bytes",
                    arquivo.getNome(), novoArquivo.getId(), arquivo.getTamanho());
            
            return Optional.of(novoArquivo);
            
        } catch (DataIntegrityViolationException e) {
            // Violação de unicidade - pode ocorrer em cenários de concorrência
            logger.warn("Violação de unicidade ao registrar arquivo {}. " +
                    "Arquivo pode ter sido registrado por outra instância. Continuando.",
                    arquivo.getNome());
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Erro ao registrar arquivo {} no banco de dados: {}",
                    arquivo.getNome(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Publica mensagens de processamento para arquivos coletados.
     * Agrupa arquivos e cria mensagens com correlationId único para rastreamento.
     * Trata falhas de publicação (RabbitMQPublisher já implementa retry até 3 tentativas).
     * 
     * @param arquivos Lista de arquivos coletados
     * @param config Configuração do servidor
     */
    private void publicarMensagensProcessamento(List<FileOrigin> arquivos, ConfiguracaoServidor config) {
        logger.info("Iniciando publicação de {} mensagens para arquivos coletados", arquivos.size());
        
        int sucessos = 0;
        int falhas = 0;
        
        for (FileOrigin arquivo : arquivos) {
            try {
                // Gerar correlationId único para rastreamento
                String correlationId = UUID.randomUUID().toString();
                
                // Criar mensagem de processamento
                MensagemProcessamento mensagem = MensagemProcessamento.builder()
                        .idFileOrigin(arquivo.getId())
                        .nomeArquivo(arquivo.getFileName())
                        .idMapeamentoOrigemDestino(arquivo.getSeverPathsInOutId())
                        .correlationId(correlationId)
                        .build();
                
                // Publicar mensagem (RabbitMQPublisher já implementa retry até 3 vezes)
                rabbitMQPublisher.publicar(mensagem);
                
                sucessos++;
                
            } catch (Exception e) {
                // Erro crítico após 3 tentativas (já tratado pelo RabbitMQPublisher)
                logger.error("ERRO CRÍTICO: Falha ao publicar mensagem para arquivo ID {} após 3 tentativas. " +
                        "Arquivo: {}, Erro: {}",
                        arquivo.getId(),
                        arquivo.getFileName(),
                        e.getMessage(),
                        e);
                falhas++;
            }
        }
        
        logger.info("Publicação concluída: {} sucessos, {} falhas", sucessos, falhas);
        
        if (falhas > 0) {
            logger.error("ATENÇÃO: {} arquivos não puderam ser publicados e precisam de intervenção manual",
                    falhas);
        }
    }

    /**
     * Extrai o hostname do código do servidor.
     * Formato esperado: "host:port" ou apenas "host"
     */
    private String extrairHost(String serverCode) {
        if (serverCode.contains(":")) {
            return serverCode.split(":")[0];
        }
        return serverCode;
    }

    /**
     * Extrai a porta do código do servidor.
     * Formato esperado: "host:port" ou apenas "host" (padrão 22)
     */
    private int extrairPorta(String serverCode) {
        if (serverCode.contains(":")) {
            try {
                return Integer.parseInt(serverCode.split(":")[1]);
            } catch (NumberFormatException e) {
                logger.warn("Porta inválida no código do servidor {}. Usando porta padrão 22.",
                        serverCode);
                return 22;
            }
        }
        return 22; // Porta SFTP padrão
    }

    /**
     * Classe interna para armazenar resultado da coleta.
     */
    private static class ResultadoColeta {
        private int arquivosColetados = 0;
        private int arquivosIgnorados = 0;
        private int erros = 0;

        public void incrementarColetados() {
            arquivosColetados++;
        }

        public void incrementarIgnorados() {
            arquivosIgnorados++;
        }

        public void incrementarErros() {
            erros++;
        }

        public int getArquivosColetados() {
            return arquivosColetados;
        }

        public int getArquivosIgnorados() {
            return arquivosIgnorados;
        }

        public int getErros() {
            return erros;
        }
    }
}
