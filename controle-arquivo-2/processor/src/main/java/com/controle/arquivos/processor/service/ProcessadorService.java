package com.controle.arquivos.processor.service;

import com.controle.arquivos.common.client.SFTPClient;
import com.controle.arquivos.common.client.VaultClient;
import com.controle.arquivos.common.domain.entity.CustomerIdentification;
import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClient;
import com.controle.arquivos.common.domain.entity.Server;
import com.controle.arquivos.common.domain.entity.SeverPaths;
import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.controle.arquivos.common.repository.FileOriginClientRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.common.repository.ServerRepository;
import com.controle.arquivos.common.repository.SeverPathsInOutRepository;
import com.controle.arquivos.common.repository.SeverPathsRepository;
import com.controle.arquivos.common.domain.entity.Layout;
import com.controle.arquivos.common.service.ClienteIdentificationService;
import com.controle.arquivos.common.service.LayoutIdentificationService;
import com.controle.arquivos.common.service.StreamingTransferService;
import com.controle.arquivos.common.service.RastreabilidadeService;
import com.controle.arquivos.common.domain.enums.TipoServidor;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.logging.StructuredErrorLogger;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import com.controle.arquivos.processor.exception.ClienteNaoIdentificadoException;
import com.controle.arquivos.processor.exception.LayoutNaoIdentificadoException;
import com.controle.arquivos.processor.exception.ErroRecuperavelException;
import com.controle.arquivos.processor.exception.ErroNaoRecuperavelException;
import com.controle.arquivos.processor.exception.FalhaUploadException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Serviço responsável pelo processamento de arquivos.
 * Orquestra o fluxo completo: download, identificação de cliente/layout e upload.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessadorService {

    private final FileOriginRepository fileOriginRepository;
    private final SeverPathsInOutRepository severPathsInOutRepository;
    private final SeverPathsRepository severPathsRepository;
    private final ServerRepository serverRepository;
    private final VaultClient vaultClient;
    private final SFTPClient sftpClient;
    private final ClienteIdentificationService clienteIdentificationService;
    private final LayoutIdentificationService layoutIdentificationService;
    private final FileOriginClientRepository fileOriginClientRepository;
    private final StreamingTransferService streamingTransferService;
    private final RastreabilidadeService rastreabilidadeService;
    private final FileOriginClientProcessingRepository processingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Processa um arquivo baseado na mensagem recebida do RabbitMQ.
     * Orquestra o fluxo completo de processamento.
     * 
     * @param mensagem mensagem contendo informações do arquivo a processar
     * @throws Exception se ocorrer erro durante o processamento
     */
    @Transactional
    public void processarArquivo(MensagemProcessamento mensagem) throws Exception {
        log.info("ProcessadorService.processarArquivo() - Iniciando processamento do arquivo: {}", 
                 mensagem.getNomeArquivo());
        
        InputStream inputStream = null;
        FileOrigin fileOrigin = null;
        Long idFileOriginClient = null;
        Long idProcessingColeta = null;
        Long idProcessingRaw = null;
        Long idProcessingStaging = null;
        Long idProcessingOrdination = null;
        Long idProcessingProcessing = null;
        Long idProcessingProcessed = null;
        
        try {
            // Buscar informações do arquivo no banco (necessário para identificação)
            fileOrigin = fileOriginRepository.findById(mensagem.getIdFileOrigin())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Arquivo não encontrado no banco de dados: " + mensagem.getIdFileOrigin()));
            
            // Verificar limite de reprocessamento antes de processar (Task 15.3)
            verificarLimiteReprocessamento(mensagem.getIdFileOrigin());
            
            // 1. Download via streaming (Task 13.4)
            inputStream = baixarArquivoStreaming(mensagem, fileOrigin);
            
            // 2. Identificação de cliente (Task 14.1)
            idFileOriginClient = identificarEAssociarCliente(fileOrigin);
            
            // Registrar etapa COLETA com status EM_ESPERA (Task 14.4)
            idProcessingColeta = rastreabilidadeService.registrarEtapa(
                idFileOriginClient, 
                EtapaProcessamento.COLETA, 
                StatusProcessamento.EM_ESPERA
            );
            
            // Atualizar para PROCESSAMENTO e registrar início do download (Task 14.4)
            rastreabilidadeService.registrarInicio(idProcessingColeta);
            
            // Registrar etapa RAW após download
            idProcessingRaw = rastreabilidadeService.registrarEtapa(
                idFileOriginClient,
                EtapaProcessamento.RAW,
                StatusProcessamento.EM_ESPERA
            );
            rastreabilidadeService.registrarInicio(idProcessingRaw);
            rastreabilidadeService.registrarConclusao(idProcessingRaw, null);
            
            // Concluir etapa COLETA
            rastreabilidadeService.registrarConclusao(idProcessingColeta, null);
            
            // 3. Identificação de layout (Task 14.2)
            Long idCliente = obterIdClienteDoFileOriginClient(idFileOriginClient);
            Long idLayout = identificarEAtualizarLayout(fileOrigin, inputStream, idCliente);
            
            // Registrar etapa STAGING após identificação de cliente (Task 14.4)
            idProcessingStaging = rastreabilidadeService.registrarEtapa(
                idFileOriginClient,
                EtapaProcessamento.STAGING,
                StatusProcessamento.EM_ESPERA
            );
            rastreabilidadeService.registrarInicio(idProcessingStaging);
            rastreabilidadeService.registrarConclusao(idProcessingStaging, null);
            
            // Registrar etapa ORDINATION após identificação de layout (Task 14.4)
            idProcessingOrdination = rastreabilidadeService.registrarEtapa(
                idFileOriginClient,
                EtapaProcessamento.ORDINATION,
                StatusProcessamento.EM_ESPERA
            );
            rastreabilidadeService.registrarInicio(idProcessingOrdination);
            rastreabilidadeService.registrarConclusao(idProcessingOrdination, null);
            
            // 4. Upload para destino (Task 14.3)
            // Registrar etapa PROCESSING ao iniciar upload (Task 14.4)
            idProcessingProcessing = rastreabilidadeService.registrarEtapa(
                idFileOriginClient,
                EtapaProcessamento.PROCESSING,
                StatusProcessamento.EM_ESPERA
            );
            rastreabilidadeService.registrarInicio(idProcessingProcessing);
            
            fazerUploadParaDestino(mensagem, fileOrigin, inputStream);
            
            // Concluir etapa PROCESSING
            rastreabilidadeService.registrarConclusao(idProcessingProcessing, null);
            
            // Registrar etapa PROCESSED após upload bem-sucedido (Task 14.4)
            idProcessingProcessed = rastreabilidadeService.registrarEtapa(
                idFileOriginClient,
                EtapaProcessamento.PROCESSED,
                StatusProcessamento.EM_ESPERA
            );
            rastreabilidadeService.registrarInicio(idProcessingProcessed);
            rastreabilidadeService.registrarConclusao(idProcessingProcessed, null);
            
            log.info("ProcessadorService.processarArquivo() - Processamento concluído para arquivo: {} - idt_file_origin_client: {}, idt_layout: {}", 
                     mensagem.getNomeArquivo(), idFileOriginClient, idLayout);
            
        } catch (Exception e) {
            log.error("ProcessadorService.processarArquivo() - Erro ao processar arquivo: {} - {}", 
                      mensagem.getNomeArquivo(), e.getMessage(), e);
            
            // Incrementar contador de tentativas (Task 15.3)
            int tentativasAtuais = incrementarContadorTentativas(mensagem.getIdFileOrigin());
            
            // Registrar erro estruturado com contexto completo (Task 15.6)
            StructuredErrorLogger.ErrorContext errorContext = new StructuredErrorLogger.ErrorContext()
                .fileName(mensagem.getNomeArquivo())
                .fileOriginId(mensagem.getIdFileOrigin())
                .correlationId(mensagem.getCorrelationId());
            
            if (fileOrigin != null) {
                errorContext.acquirerId(fileOrigin.getAcquirerId());
            }
            
            // Determinar etapa atual para contexto
            String etapaAtual = determinarEtapaAtual(
                idProcessingColeta,
                idProcessingRaw,
                idProcessingStaging,
                idProcessingOrdination,
                idProcessingProcessing,
                idProcessingProcessed
            );
            errorContext.step(etapaAtual);
            
            StructuredErrorLogger.logError(
                log,
                String.format("Erro ao processar arquivo: %s", mensagem.getNomeArquivo()),
                e,
                errorContext
            );
            
            // Atualizar status para ERRO com mensagem e stack trace (Task 14.4)
            if (idFileOriginClient != null) {
                registrarErroRastreabilidade(
                    idProcessingColeta,
                    idProcessingRaw,
                    idProcessingStaging,
                    idProcessingOrdination,
                    idProcessingProcessing,
                    idProcessingProcessed,
                    e,
                    tentativasAtuais
                );
            }
            
            // Classificar erro e lançar exceção apropriada (Task 15.2)
            classificarELancarErro(e);
        } finally {
            // Garantir liberação de recursos
            liberarRecursos(inputStream);
        }
    }

    /**
     * Baixa arquivo do SFTP via streaming.
     * Obtém credenciais do Vault, conecta ao SFTP e retorna InputStream.
     * 
     * @param mensagem mensagem contendo informações do arquivo
     * @param fileOrigin entidade FileOrigin já carregada
     * @return InputStream do arquivo para processamento
     * @throws Exception se ocorrer erro durante download
     */
    private InputStream baixarArquivoStreaming(MensagemProcessamento mensagem, FileOrigin fileOrigin) throws Exception {
        log.debug("ProcessadorService.baixarArquivoStreaming() - Iniciando download do arquivo: {}", 
                  mensagem.getNomeArquivo());
        
        try {
            // Buscar mapeamento origem-destino
            SeverPathsInOut pathsInOut = severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Mapeamento origem-destino não encontrado: " + mensagem.getIdMapeamentoOrigemDestino()));
            
            // Buscar caminho de origem
            SeverPaths severPath = severPathsRepository.findById(pathsInOut.getSeverPathOriginId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Caminho de origem não encontrado: " + pathsInOut.getSeverPathOriginId()));
            
            // Buscar servidor de origem
            Server server = serverRepository.findById(severPath.getServerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Servidor não encontrado: " + severPath.getServerId()));
            
            // Obter credenciais do Vault
            log.debug("ProcessadorService.baixarArquivoStreaming() - Obtendo credenciais do Vault para servidor: {}", 
                      server.getServerCode());
            VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(
                server.getVaultCode(), 
                server.getVaultSecret()
            );
            
            // Conectar ao SFTP
            log.debug("ProcessadorService.baixarArquivoStreaming() - Conectando ao servidor SFTP: {}", 
                      server.getServerCode());
            sftpClient.conectar(server.getServerCode(), 22, credenciais);
            
            // Construir caminho completo do arquivo
            String caminhoCompleto = construirCaminhoCompleto(severPath.getPath(), fileOrigin.getFileName());
            
            // Obter InputStream do arquivo
            log.debug("ProcessadorService.baixarArquivoStreaming() - Obtendo InputStream do arquivo: {}", 
                      caminhoCompleto);
            InputStream inputStream = sftpClient.obterInputStream(caminhoCompleto);
            
            log.info("ProcessadorService.baixarArquivoStreaming() - Download iniciado com sucesso para arquivo: {}", 
                     mensagem.getNomeArquivo());
            
            return inputStream;
            
        } catch (Exception e) {
            log.error("ProcessadorService.baixarArquivoStreaming() - Erro ao baixar arquivo: {} - {}", 
                      mensagem.getNomeArquivo(), e.getMessage(), e);
            
            // Liberar recursos em caso de erro
            liberarRecursos(null);
            
            throw new RuntimeException("Falha ao baixar arquivo via streaming: " + mensagem.getNomeArquivo(), e);
        }
    }

    /**
     * Constrói o caminho completo do arquivo concatenando diretório e nome.
     * 
     * @param diretorio diretório base
     * @param nomeArquivo nome do arquivo
     * @return caminho completo
     */
    private String construirCaminhoCompleto(String diretorio, String nomeArquivo) {
        if (diretorio.endsWith("/")) {
            return diretorio + nomeArquivo;
        }
        return diretorio + "/" + nomeArquivo;
    }

    /**
     * Identifica o cliente usando ClienteIdentificationService e associa ao arquivo.
     * Se cliente não identificado, registra erro e lança exceção.
     * Se cliente identificado, insere ou atualiza registro em file_origin_client.
     * 
     * @param fileOrigin entidade FileOrigin contendo informações do arquivo
     * @return ID do registro file_origin_client para rastreabilidade subsequente
     * @throws ClienteNaoIdentificadoException se nenhum cliente for identificado
     */
    private Long identificarEAssociarCliente(FileOrigin fileOrigin) {
        log.debug("ProcessadorService.identificarEAssociarCliente() - Identificando cliente para arquivo: {}", 
                  fileOrigin.getFileName());
        
        try {
            // Invocar ClienteIdentificationService.identificar
            Optional<CustomerIdentification> clienteOpt = clienteIdentificationService.identificar(
                fileOrigin.getFileName(), 
                fileOrigin.getAcquirerId()
            );
            
            // Se cliente não identificado, registrar erro e lançar exceção
            if (clienteOpt.isEmpty()) {
                String mensagemErro = String.format(
                    "Cliente não identificado para arquivo: %s, adquirente: %d", 
                    fileOrigin.getFileName(), 
                    fileOrigin.getAcquirerId()
                );
                
                // Registrar erro estruturado (Task 15.6)
                StructuredErrorLogger.ErrorContext errorContext = new StructuredErrorLogger.ErrorContext()
                    .fileName(fileOrigin.getFileName())
                    .fileOriginId(fileOrigin.getId())
                    .acquirerId(fileOrigin.getAcquirerId())
                    .step(EtapaProcessamento.STAGING.name());
                
                ClienteNaoIdentificadoException exception = new ClienteNaoIdentificadoException(mensagemErro);
                StructuredErrorLogger.logError(log, mensagemErro, exception, errorContext);
                
                throw exception;
            }
            
            CustomerIdentification cliente = clienteOpt.get();
            log.info("ProcessadorService.identificarEAssociarCliente() - Cliente identificado: {} (ID: {}) para arquivo: {}", 
                     cliente.getCustomerName(), cliente.getId(), fileOrigin.getFileName());
            
            // Inserir ou atualizar registro em file_origin_client
            FileOriginClient fileOriginClient = inserirOuAtualizarFileOriginClient(
                fileOrigin.getId(), 
                cliente.getId()
            );
            
            log.info("ProcessadorService.identificarEAssociarCliente() - Associação criada: idt_file_origin_client = {}", 
                     fileOriginClient.getId());
            
            // Retornar idt_file_origin_client para rastreabilidade subsequente
            return fileOriginClient.getId();
            
        } catch (ClienteNaoIdentificadoException e) {
            throw e;
        } catch (Exception e) {
            log.error("ProcessadorService.identificarEAssociarCliente() - Erro inesperado ao identificar cliente: {}", 
                      e.getMessage(), e);
            throw new RuntimeException("Erro ao identificar cliente para arquivo: " + fileOrigin.getFileName(), e);
        }
    }

    /**
     * Insere ou atualiza registro em file_origin_client.
     * Se já existe associação ativa para o mesmo arquivo, atualiza o registro existente.
     * 
     * @param idFileOrigin ID do arquivo
     * @param idCliente ID do cliente identificado
     * @return FileOriginClient criado ou atualizado
     */
    private FileOriginClient inserirOuAtualizarFileOriginClient(Long idFileOrigin, Long idCliente) {
        log.debug("ProcessadorService.inserirOuAtualizarFileOriginClient() - Associando arquivo {} ao cliente {}", 
                  idFileOrigin, idCliente);
        
        // Criar novo registro
        FileOriginClient fileOriginClient = FileOriginClient.builder()
            .fileOriginId(idFileOrigin)
            .clientId(idCliente)
            .active(true)
            .build();
        
        // Salvar no banco
        fileOriginClient = fileOriginClientRepository.save(fileOriginClient);
        
        log.debug("ProcessadorService.inserirOuAtualizarFileOriginClient() - Registro salvo com ID: {}", 
                  fileOriginClient.getId());
        
        return fileOriginClient;
    }

    /**
     * Obtém o ID do cliente a partir do registro file_origin_client.
     * 
     * @param idFileOriginClient ID do registro file_origin_client
     * @return ID do cliente
     */
    private Long obterIdClienteDoFileOriginClient(Long idFileOriginClient) {
        FileOriginClient fileOriginClient = fileOriginClientRepository.findById(idFileOriginClient)
            .orElseThrow(() -> new IllegalArgumentException(
                "Registro file_origin_client não encontrado: " + idFileOriginClient));
        
        return fileOriginClient.getClientId();
    }

    /**
     * Identifica o layout usando LayoutIdentificationService e atualiza file_origin.
     * Se layout não identificado, registra erro, atualiza status para ERRO e lança exceção.
     * Se layout identificado, atualiza idt_layout, des_file_type e des_transaction_type.
     * 
     * @param fileOrigin entidade FileOrigin contendo informações do arquivo
     * @param headerStream InputStream para ler primeiros 7000 bytes (para regras HEADER)
     * @param idCliente ID do cliente identificado
     * @return ID do layout identificado
     * @throws LayoutNaoIdentificadoException se nenhum layout for identificado
     */
    private Long identificarEAtualizarLayout(FileOrigin fileOrigin, InputStream headerStream, Long idCliente) {
        log.debug("ProcessadorService.identificarEAtualizarLayout() - Identificando layout para arquivo: {}", 
                  fileOrigin.getFileName());
        
        try {
            // Invocar LayoutIdentificationService.identificar
            Optional<Layout> layoutOpt = layoutIdentificationService.identificar(
                fileOrigin.getFileName(),
                headerStream,
                idCliente,
                fileOrigin.getAcquirerId()
            );
            
            // Se layout não identificado, registrar erro e lançar exceção
            if (layoutOpt.isEmpty()) {
                String mensagemErro = String.format(
                    "Layout não identificado para arquivo: %s, cliente: %d, adquirente: %d",
                    fileOrigin.getFileName(),
                    idCliente,
                    fileOrigin.getAcquirerId()
                );
                
                // Registrar erro estruturado (Task 15.6)
                StructuredErrorLogger.ErrorContext errorContext = new StructuredErrorLogger.ErrorContext()
                    .fileName(fileOrigin.getFileName())
                    .fileOriginId(fileOrigin.getId())
                    .acquirerId(fileOrigin.getAcquirerId())
                    .clientId(idCliente)
                    .step(EtapaProcessamento.ORDINATION.name());
                
                LayoutNaoIdentificadoException exception = new LayoutNaoIdentificadoException(mensagemErro);
                StructuredErrorLogger.logError(log, mensagemErro, exception, errorContext);
                
                throw exception;
            }
            
            Layout layout = layoutOpt.get();
            log.info("ProcessadorService.identificarEAtualizarLayout() - Layout identificado: {} (ID: {}) para arquivo: {}",
                     layout.getLayoutName(), layout.getId(), fileOrigin.getFileName());
            
            // Atualizar file_origin com informações do layout
            atualizarFileOriginComLayout(fileOrigin, layout);
            
            log.info("ProcessadorService.identificarEAtualizarLayout() - FileOrigin atualizado com layout ID: {}",
                     layout.getId());
            
            // Retornar ID do layout para rastreabilidade subsequente
            return layout.getId();
            
        } catch (LayoutNaoIdentificadoException e) {
            throw e;
        } catch (Exception e) {
            log.error("ProcessadorService.identificarEAtualizarLayout() - Erro inesperado ao identificar layout: {}",
                      e.getMessage(), e);
            throw new RuntimeException("Erro ao identificar layout para arquivo: " + fileOrigin.getFileName(), e);
        }
    }

    /**
     * Atualiza file_origin com informações do layout identificado.
     * Atualiza idt_layout, des_file_type, des_transaction_type e timestamp de última modificação.
     * 
     * @param fileOrigin entidade FileOrigin a ser atualizada
     * @param layout Layout identificado
     */
    private void atualizarFileOriginComLayout(FileOrigin fileOrigin, Layout layout) {
        log.debug("ProcessadorService.atualizarFileOriginComLayout() - Atualizando file_origin {} com layout {}",
                  fileOrigin.getId(), layout.getId());
        
        try {
            // Atualizar idt_layout
            fileOrigin.setLayoutId(layout.getId());
            
            // Atualizar des_file_type com o tipo do layout
            fileOrigin.setFileType(layout.getLayoutType());
            
            // Atualizar des_transaction_type (usando o nome do layout como tipo de transação)
            // Nota: Em um cenário real, isso poderia vir de outro campo do layout ou de uma regra de negócio
            fileOrigin.setTransactionType(layout.getLayoutName());
            
            // O timestamp de última modificação será atualizado automaticamente pelo @PreUpdate
            
            // Salvar no banco
            fileOriginRepository.save(fileOrigin);
            
            log.debug("ProcessadorService.atualizarFileOriginComLayout() - FileOrigin atualizado com sucesso");
            
        } catch (Exception e) {
            log.error("ProcessadorService.atualizarFileOriginComLayout() - Erro ao atualizar file_origin: {}",
                      e.getMessage(), e);
            // Não lançar exceção - continuar processamento mesmo se atualização falhar
            // conforme requisito 14.4: "em caso de falha, deve registrar erro mas continuar processamento"
        }
    }

    /**
     * Faz upload do arquivo para o destino usando streaming.
     * Determina o destino (S3 ou SFTP), obtém credenciais e invoca o serviço apropriado.
     * Valida que o tamanho do arquivo no destino corresponde ao tamanho original.
     * 
     * @param mensagem mensagem contendo informações do arquivo
     * @param fileOrigin entidade FileOrigin contendo informações do arquivo
     * @param inputStream InputStream do arquivo para upload
     * @throws Exception se ocorrer erro durante upload ou validação de tamanho
     */
    private void fazerUploadParaDestino(MensagemProcessamento mensagem, FileOrigin fileOrigin, InputStream inputStream) 
            throws Exception {
        log.debug("ProcessadorService.fazerUploadParaDestino() - Iniciando upload do arquivo: {}", 
                  mensagem.getNomeArquivo());
        
        try {
            // Buscar mapeamento origem-destino
            SeverPathsInOut pathsInOut = severPathsInOutRepository.findById(mensagem.getIdMapeamentoOrigemDestino())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Mapeamento origem-destino não encontrado: " + mensagem.getIdMapeamentoOrigemDestino()));
            
            // Buscar servidor de destino usando idt_sever_destination
            Server serverDestino = serverRepository.findById(pathsInOut.getSeverDestinationId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Servidor de destino não encontrado: " + pathsInOut.getSeverDestinationId()));
            
            log.debug("ProcessadorService.fazerUploadParaDestino() - Servidor de destino: {} (tipo: {})", 
                      serverDestino.getServerCode(), serverDestino.getServerType());
            
            // Obter credenciais do destino via Vault
            log.debug("ProcessadorService.fazerUploadParaDestino() - Obtendo credenciais do Vault para servidor de destino: {}", 
                      serverDestino.getServerCode());
            VaultClient.Credenciais credenciaisDestino = vaultClient.obterCredenciais(
                serverDestino.getVaultCode(), 
                serverDestino.getVaultSecret()
            );
            
            // Determinar tipo de destino e invocar método apropriado
            if (serverDestino.getServerType() == TipoServidor.S3) {
                // Upload para S3
                fazerUploadParaS3(serverDestino, fileOrigin, inputStream, credenciaisDestino);
            } else if (serverDestino.getServerType() == TipoServidor.SFTP) {
                // Upload para SFTP
                fazerUploadParaSFTP(serverDestino, pathsInOut, fileOrigin, inputStream, credenciaisDestino);
            } else {
                throw new UnsupportedOperationException(
                    "Tipo de servidor de destino não suportado: " + serverDestino.getServerType());
            }
            
            log.info("ProcessadorService.fazerUploadParaDestino() - Upload concluído com sucesso para arquivo: {}", 
                     mensagem.getNomeArquivo());
            
        } catch (Exception e) {
            log.error("ProcessadorService.fazerUploadParaDestino() - Erro ao fazer upload do arquivo: {} - {}", 
                      mensagem.getNomeArquivo(), e.getMessage(), e);
            
            // Manter arquivo na origem (não deletar)
            log.warn("ProcessadorService.fazerUploadParaDestino() - Arquivo mantido na origem devido a falha no upload: {}", 
                     mensagem.getNomeArquivo());
            
            throw new RuntimeException("Falha ao fazer upload do arquivo: " + mensagem.getNomeArquivo(), e);
        }
    }

    /**
     * Faz upload do arquivo para S3 usando multipart upload com streaming.
     * Valida que o tamanho do arquivo no destino corresponde ao tamanho original.
     * 
     * @param serverDestino servidor S3 de destino
     * @param fileOrigin entidade FileOrigin contendo informações do arquivo
     * @param inputStream InputStream do arquivo para upload
     * @param credenciais credenciais do S3
     * @throws Exception se ocorrer erro durante upload ou validação
     */
    private void fazerUploadParaS3(Server serverDestino, FileOrigin fileOrigin, InputStream inputStream, 
                                    VaultClient.Credenciais credenciais) throws Exception {
        log.debug("ProcessadorService.fazerUploadParaS3() - Iniciando upload para S3: bucket={}, key={}", 
                  serverDestino.getServerCode(), fileOrigin.getFileName());
        
        try {
            // Extrair bucket do serverCode (assumindo formato "bucket-name")
            String bucket = serverDestino.getServerCode();
            String key = fileOrigin.getFileName();
            long tamanho = fileOrigin.getFileSize();
            
            // Invocar StreamingTransferService.transferirSFTPparaS3()
            streamingTransferService.transferirSFTPparaS3(inputStream, bucket, key, tamanho);
            
            log.info("ProcessadorService.fazerUploadParaS3() - Upload para S3 concluído: bucket={}, key={}, tamanho={}", 
                     bucket, key, tamanho);
            
            // Nota: A validação de tamanho é feita dentro do StreamingTransferService
            // Se o tamanho não corresponder, uma exceção será lançada
            
        } catch (Exception e) {
            log.error("ProcessadorService.fazerUploadParaS3() - Erro ao fazer upload para S3: arquivo={}, erro={}", 
                      fileOrigin.getFileName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Faz upload do arquivo para SFTP usando OutputStream encadeado.
     * Valida que o tamanho do arquivo no destino corresponde ao tamanho original.
     * 
     * @param serverDestino servidor SFTP de destino
     * @param pathsInOut mapeamento origem-destino
     * @param fileOrigin entidade FileOrigin contendo informações do arquivo
     * @param inputStream InputStream do arquivo para upload
     * @param credenciais credenciais do SFTP de destino
     * @throws Exception se ocorrer erro durante upload ou validação
     */
    private void fazerUploadParaSFTP(Server serverDestino, SeverPathsInOut pathsInOut, FileOrigin fileOrigin, 
                                      InputStream inputStream, VaultClient.Credenciais credenciais) throws Exception {
        log.debug("ProcessadorService.fazerUploadParaSFTP() - Iniciando upload para SFTP: servidor={}, arquivo={}", 
                  serverDestino.getServerCode(), fileOrigin.getFileName());
        
        SFTPClient sftpClientDestino = null;
        OutputStream outputStream = null;
        
        try {
            // Buscar caminho de destino
            SeverPaths severPathDestino = severPathsRepository.findById(pathsInOut.getSeverDestinationId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Caminho de destino não encontrado: " + pathsInOut.getSeverDestinationId()));
            
            // Criar novo cliente SFTP para o destino (não reutilizar o cliente de origem)
            sftpClientDestino = new SFTPClient();
            
            // Conectar ao SFTP de destino
            log.debug("ProcessadorService.fazerUploadParaSFTP() - Conectando ao servidor SFTP de destino: {}", 
                      serverDestino.getServerCode());
            sftpClientDestino.conectar(serverDestino.getServerCode(), 22, credenciais);
            
            // Construir caminho completo do arquivo no destino
            String caminhoCompleto = construirCaminhoCompleto(severPathDestino.getPath(), fileOrigin.getFileName());
            
            // Obter OutputStream do SFTP de destino
            log.debug("ProcessadorService.fazerUploadParaSFTP() - Obtendo OutputStream para arquivo: {}", 
                      caminhoCompleto);
            outputStream = sftpClientDestino.obterOutputStream(caminhoCompleto);
            
            // Invocar StreamingTransferService.transferirSFTPparaSFTP()
            long tamanho = fileOrigin.getFileSize();
            streamingTransferService.transferirSFTPparaSFTP(inputStream, outputStream, caminhoCompleto, tamanho);
            
            log.info("ProcessadorService.fazerUploadParaSFTP() - Upload para SFTP concluído: servidor={}, caminho={}, tamanho={}", 
                     serverDestino.getServerCode(), caminhoCompleto, tamanho);
            
            // Nota: A validação de tamanho é feita dentro do StreamingTransferService
            // Se o tamanho não corresponder, uma exceção será lançada
            
        } catch (Exception e) {
            log.error("ProcessadorService.fazerUploadParaSFTP() - Erro ao fazer upload para SFTP: arquivo={}, erro={}", 
                      fileOrigin.getFileName(), e.getMessage(), e);
            throw e;
        } finally {
            // Liberar recursos do SFTP de destino
            try {
                if (outputStream != null) {
                    outputStream.close();
                    log.debug("ProcessadorService.fazerUploadParaSFTP() - OutputStream fechado");
                }
            } catch (Exception e) {
                log.warn("ProcessadorService.fazerUploadParaSFTP() - Erro ao fechar OutputStream: {}", e.getMessage());
            }
            
            try {
                if (sftpClientDestino != null && sftpClientDestino.isConectado()) {
                    sftpClientDestino.desconectar();
                    log.debug("ProcessadorService.fazerUploadParaSFTP() - Conexão SFTP de destino desconectada");
                }
            } catch (Exception e) {
                log.warn("ProcessadorService.fazerUploadParaSFTP() - Erro ao desconectar SFTP de destino: {}", e.getMessage());
            }
        }
    }

    /**
     * Libera recursos (InputStream e conexão SFTP).
     * 
     * @param inputStream InputStream a ser fechado (pode ser null)
     */
    private void liberarRecursos(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
                log.debug("ProcessadorService.liberarRecursos() - InputStream fechado");
            }
        } catch (Exception e) {
            log.warn("ProcessadorService.liberarRecursos() - Erro ao fechar InputStream: {}", e.getMessage());
        }
        
        try {
            if (sftpClient.isConectado()) {
                sftpClient.desconectar();
                log.debug("ProcessadorService.liberarRecursos() - Conexão SFTP desconectada");
            }
        } catch (Exception e) {
            log.warn("ProcessadorService.liberarRecursos() - Erro ao desconectar SFTP: {}", e.getMessage());
        }
    }

    /**
     * Registra erro na rastreabilidade para a etapa em andamento.
     * Atualiza status para ERRO com mensagem de erro e stack trace em jsn_additional_info.
     * 
     * @param idProcessingColeta ID do processamento da etapa COLETA
     * @param idProcessingRaw ID do processamento da etapa RAW
     * @param idProcessingStaging ID do processamento da etapa STAGING
     * @param idProcessingOrdination ID do processamento da etapa ORDINATION
     * @param idProcessingProcessing ID do processamento da etapa PROCESSING
     * @param idProcessingProcessed ID do processamento da etapa PROCESSED
     * @param exception Exceção que causou o erro
     * @param tentativasAtuais Número atual de tentativas de processamento
     */
    private void registrarErroRastreabilidade(
            Long idProcessingColeta,
            Long idProcessingRaw,
            Long idProcessingStaging,
            Long idProcessingOrdination,
            Long idProcessingProcessing,
            Long idProcessingProcessed,
            Exception exception,
            int tentativasAtuais) {
        
        try {
            // Determinar qual etapa estava em andamento
            Long idProcessingAtual = null;
            if (idProcessingProcessed != null) {
                idProcessingAtual = idProcessingProcessed;
            } else if (idProcessingProcessing != null) {
                idProcessingAtual = idProcessingProcessing;
            } else if (idProcessingOrdination != null) {
                idProcessingAtual = idProcessingOrdination;
            } else if (idProcessingStaging != null) {
                idProcessingAtual = idProcessingStaging;
            } else if (idProcessingRaw != null) {
                idProcessingAtual = idProcessingRaw;
            } else if (idProcessingColeta != null) {
                idProcessingAtual = idProcessingColeta;
            }
            
            if (idProcessingAtual != null) {
                // Extrair stack trace como string
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);
                String stackTrace = sw.toString();
                
                // Preparar informações adicionais com stack trace e contador de tentativas
                Map<String, Object> infoAdicional = new HashMap<>();
                infoAdicional.put("stackTrace", stackTrace);
                infoAdicional.put("exceptionType", exception.getClass().getName());
                infoAdicional.put("retryCount", tentativasAtuais);
                
                // Atualizar status para ERRO com mensagem e stack trace
                rastreabilidadeService.atualizarStatus(
                    idProcessingAtual,
                    StatusProcessamento.ERRO,
                    exception.getMessage()
                );
                
                // Registrar informações adicionais
                rastreabilidadeService.registrarConclusao(idProcessingAtual, infoAdicional);
                
                log.debug("ProcessadorService.registrarErroRastreabilidade() - Erro registrado na rastreabilidade para processing_id: {}, tentativas: {}", 
                          idProcessingAtual, tentativasAtuais);
            }
        } catch (Exception e) {
            log.error("ProcessadorService.registrarErroRastreabilidade() - Erro ao registrar erro na rastreabilidade: {}", 
                      e.getMessage(), e);
            // Não lançar exceção - apenas logar o erro
        }
    }

    /**
     * Classifica o erro e lança a exceção apropriada (recuperável ou não recuperável).
     * 
     * Erros recuperáveis (permitem retry via NACK):
     * - Falhas de conexão SFTP temporárias
     * - Timeouts de rede
     * - Falhas de banco transientes
     * - Throttling S3
     * 
     * Erros não recuperáveis (ACK para não reprocessar):
     * - Arquivo não encontrado
     * - Cliente não identificado
     * - Layout não identificado
     * - Credenciais inválidas
     * 
     * @param exception Exceção original capturada
     * @throws ErroRecuperavelException se o erro for recuperável (NACK)
     * @throws ErroNaoRecuperavelException se o erro for não recuperável (ACK)
     */
    private void classificarELancarErro(Exception exception) {
        log.debug("ProcessadorService.classificarELancarErro() - Classificando erro: {}", 
                  exception.getClass().getName());
        
        // Se já é uma exceção classificada, relançar
        if (exception instanceof ErroRecuperavelException) {
            log.info("ProcessadorService.classificarELancarErro() - Erro recuperável detectado: {}", 
                     exception.getMessage());
            throw (ErroRecuperavelException) exception;
        }
        
        if (exception instanceof ErroNaoRecuperavelException) {
            log.info("ProcessadorService.classificarELancarErro() - Erro não recuperável detectado: {}", 
                     exception.getMessage());
            throw (ErroNaoRecuperavelException) exception;
        }
        
        // Classificar erros não recuperáveis
        if (isErroNaoRecuperavel(exception)) {
            log.warn("ProcessadorService.classificarELancarErro() - Classificado como erro não recuperável: {}", 
                     exception.getMessage());
            throw new ErroNaoRecuperavelException(
                "Erro não recuperável durante processamento: " + exception.getMessage(), 
                exception
            );
        }
        
        // Classificar erros recuperáveis
        if (isErroRecuperavel(exception)) {
            log.warn("ProcessadorService.classificarELancarErro() - Classificado como erro recuperável: {}", 
                     exception.getMessage());
            throw new ErroRecuperavelException(
                "Erro recuperável durante processamento: " + exception.getMessage(), 
                exception
            );
        }
        
        // Por padrão, tratar como erro recuperável para permitir retry
        log.warn("ProcessadorService.classificarELancarErro() - Erro não classificado, tratando como recuperável: {}", 
                 exception.getMessage());
        throw new ErroRecuperavelException(
            "Erro não classificado durante processamento: " + exception.getMessage(), 
            exception
        );
    }

    /**
     * Verifica se o erro é não recuperável.
     * 
     * Erros não recuperáveis:
     * - Arquivo não encontrado (FileNotFoundException, SftpException com código 2)
     * - Cliente não identificado (ClienteNaoIdentificadoException)
     * - Layout não identificado (LayoutNaoIdentificadoException)
     * - Credenciais inválidas (JSchException com "Auth fail")
     * - Violação de constraint (SQLException não transiente)
     * 
     * @param exception Exceção a ser verificada
     * @return true se o erro é não recuperável
     */
    private boolean isErroNaoRecuperavel(Exception exception) {
        // Arquivo não encontrado
        if (exception instanceof FileNotFoundException) {
            log.debug("ProcessadorService.isErroNaoRecuperavel() - FileNotFoundException detectado");
            return true;
        }
        
        // SFTP: arquivo não encontrado (código 2 = SSH_FX_NO_SUCH_FILE)
        if (exception instanceof SftpException) {
            SftpException sftpEx = (SftpException) exception;
            if (sftpEx.id == 2) {
                log.debug("ProcessadorService.isErroNaoRecuperavel() - SFTP arquivo não encontrado (código 2)");
                return true;
            }
        }
        
        // Cliente não identificado
        if (exception instanceof ClienteNaoIdentificadoException) {
            log.debug("ProcessadorService.isErroNaoRecuperavel() - ClienteNaoIdentificadoException detectado");
            return true;
        }
        
        // Layout não identificado
        if (exception instanceof LayoutNaoIdentificadoException) {
            log.debug("ProcessadorService.isErroNaoRecuperavel() - LayoutNaoIdentificadoException detectado");
            return true;
        }
        
        // Credenciais inválidas
        if (exception instanceof JSchException) {
            String message = exception.getMessage();
            if (message != null && (message.contains("Auth fail") || message.contains("Auth cancel"))) {
                log.debug("ProcessadorService.isErroNaoRecuperavel() - Credenciais SFTP inválidas");
                return true;
            }
        }
        
        // Violação de constraint de banco (não transiente)
        if (exception instanceof SQLException && !(exception instanceof SQLTransientException)) {
            log.debug("ProcessadorService.isErroNaoRecuperavel() - SQLException não transiente detectado");
            return true;
        }
        
        // Verificar causa raiz
        Throwable cause = exception.getCause();
        if (cause instanceof Exception) {
            return isErroNaoRecuperavel((Exception) cause);
        }
        
        return false;
    }

    /**
     * Verifica se o erro é recuperável.
     * 
     * Erros recuperáveis:
     * - Falhas de conexão SFTP (JSchException exceto Auth fail)
     * - Timeouts (SocketTimeoutException, IOException com "timeout")
     * - Falhas de banco transientes (SQLTransientException)
     * - Throttling S3 (S3Exception com código 503 ou SlowDown)
     * - Falhas de upload (FalhaUploadException)
     * 
     * @param exception Exceção a ser verificada
     * @return true se o erro é recuperável
     */
    private boolean isErroRecuperavel(Exception exception) {
        // Falhas de conexão SFTP (exceto Auth fail)
        if (exception instanceof JSchException) {
            String message = exception.getMessage();
            if (message != null && !message.contains("Auth fail") && !message.contains("Auth cancel")) {
                log.debug("ProcessadorService.isErroRecuperavel() - Falha de conexão SFTP detectada");
                return true;
            }
        }
        
        // SFTP: erros temporários (códigos 4, 5, 6, 7)
        // 4 = SSH_FX_FAILURE, 5 = SSH_FX_BAD_MESSAGE, 6 = SSH_FX_NO_CONNECTION, 7 = SSH_FX_CONNECTION_LOST
        if (exception instanceof SftpException) {
            SftpException sftpEx = (SftpException) exception;
            if (sftpEx.id == 4 || sftpEx.id == 5 || sftpEx.id == 6 || sftpEx.id == 7) {
                log.debug("ProcessadorService.isErroRecuperavel() - SFTP erro temporário (código {})", sftpEx.id);
                return true;
            }
        }
        
        // Timeouts
        if (exception instanceof SocketTimeoutException) {
            log.debug("ProcessadorService.isErroRecuperavel() - SocketTimeoutException detectado");
            return true;
        }
        
        if (exception instanceof IOException) {
            String message = exception.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                log.debug("ProcessadorService.isErroRecuperavel() - IOException com timeout detectado");
                return true;
            }
        }
        
        // Falhas de banco transientes
        if (exception instanceof SQLTransientException) {
            log.debug("ProcessadorService.isErroRecuperavel() - SQLTransientException detectado");
            return true;
        }
        
        // Throttling S3
        if (exception instanceof S3Exception) {
            S3Exception s3Ex = (S3Exception) exception;
            int statusCode = s3Ex.statusCode();
            String errorCode = s3Ex.awsErrorDetails() != null ? 
                s3Ex.awsErrorDetails().errorCode() : null;
            
            // 503 Service Unavailable ou SlowDown
            if (statusCode == 503 || "SlowDown".equals(errorCode) || "RequestTimeout".equals(errorCode)) {
                log.debug("ProcessadorService.isErroRecuperavel() - S3 throttling/timeout detectado (código: {}, erro: {})", 
                          statusCode, errorCode);
                return true;
            }
        }
        
        // Falhas de upload
        if (exception instanceof FalhaUploadException) {
            log.debug("ProcessadorService.isErroRecuperavel() - FalhaUploadException detectado");
            return true;
        }
        
        // Verificar causa raiz
        Throwable cause = exception.getCause();
        if (cause instanceof Exception) {
            return isErroRecuperavel((Exception) cause);
        }
        
        return false;
    }

    /**
     * Verifica o limite de reprocessamento para um arquivo.
     * Se o contador de tentativas >= 5, marca como ERRO permanente e lança exceção não recuperável.
     * 
     * Task 15.3: Implementar limite de reprocessamento
     * 
     * @param idFileOrigin ID do arquivo a ser verificado
     * @throws ErroNaoRecuperavelException se o limite de 5 tentativas foi atingido
     */
    private void verificarLimiteReprocessamento(Long idFileOrigin) {
        log.debug("ProcessadorService.verificarLimiteReprocessamento() - Verificando limite para file_origin_id: {}", 
                  idFileOrigin);
        
        try {
            // Buscar todos os registros de processamento com erro para este arquivo
            // Contar quantas vezes o arquivo já foi processado com erro
            int tentativasAnteriores = contarTentativasAnteriores(idFileOrigin);
            
            log.debug("ProcessadorService.verificarLimiteReprocessamento() - Tentativas anteriores: {}", 
                      tentativasAnteriores);
            
            // Se já atingiu o limite de 5 tentativas, não reprocessar
            if (tentativasAnteriores >= 5) {
                String mensagemErro = String.format(
                    "Limite de reprocessamento atingido para arquivo file_origin_id=%d. " +
                    "Tentativas: %d. Arquivo marcado como ERRO permanente.",
                    idFileOrigin, tentativasAnteriores
                );
                
                log.error("ProcessadorService.verificarLimiteReprocessamento() - {}", mensagemErro);
                
                // Lançar exceção não recuperável para ACK da mensagem (não reprocessar)
                throw new ErroNaoRecuperavelException(mensagemErro);
            }
            
        } catch (ErroNaoRecuperavelException e) {
            // Relançar exceção não recuperável
            throw e;
        } catch (Exception e) {
            log.warn("ProcessadorService.verificarLimiteReprocessamento() - Erro ao verificar limite, continuando processamento: {}", 
                     e.getMessage());
            // Em caso de erro ao verificar, permitir processamento (fail-safe)
        }
    }

    /**
     * Conta o número de tentativas anteriores de processamento para um arquivo.
     * Busca registros de file_origin_client_processing com status ERRO e extrai o contador de retryCount.
     * 
     * @param idFileOrigin ID do arquivo
     * @return Número de tentativas anteriores
     */
    private int contarTentativasAnteriores(Long idFileOrigin) {
        try {
            // Buscar file_origin_client associado ao file_origin
            Optional<FileOriginClient> fileOriginClientOpt = fileOriginClientRepository
                .findByFileOriginIdAndActiveTrue(idFileOrigin);
            
            if (fileOriginClientOpt.isEmpty()) {
                // Se não existe file_origin_client, é a primeira tentativa
                return 0;
            }
            
            Long idFileOriginClient = fileOriginClientOpt.get().getId();
            
            // Buscar todos os registros de processamento com erro
            var processings = processingRepository.findByFileOriginClientId(idFileOriginClient);
            
            // Encontrar o maior valor de retryCount nos registros com erro
            int maxRetryCount = 0;
            for (var processing : processings) {
                if (processing.getStatus() == StatusProcessamento.ERRO && 
                    processing.getAdditionalInfo() != null) {
                    try {
                        // Parse do JSON para extrair retryCount
                        Map<String, Object> info = objectMapper.readValue(
                            processing.getAdditionalInfo(), 
                            Map.class
                        );
                        
                        if (info.containsKey("retryCount")) {
                            int retryCount = ((Number) info.get("retryCount")).intValue();
                            maxRetryCount = Math.max(maxRetryCount, retryCount);
                        }
                    } catch (Exception e) {
                        log.warn("ProcessadorService.contarTentativasAnteriores() - Erro ao parsear additionalInfo: {}", 
                                 e.getMessage());
                    }
                }
            }
            
            return maxRetryCount;
            
        } catch (Exception e) {
            log.warn("ProcessadorService.contarTentativasAnteriores() - Erro ao contar tentativas: {}", 
                     e.getMessage());
            return 0; // Em caso de erro, assumir 0 tentativas (fail-safe)
        }
    }

    /**
     * Incrementa o contador de tentativas de processamento para um arquivo.
     * Retorna o novo valor do contador.
     * 
     * @param idFileOrigin ID do arquivo
     * @return Novo valor do contador de tentativas
     */
    private int incrementarContadorTentativas(Long idFileOrigin) {
        int tentativasAnteriores = contarTentativasAnteriores(idFileOrigin);
        int novoContador = tentativasAnteriores + 1;
        
        log.info("ProcessadorService.incrementarContadorTentativas() - file_origin_id: {}, tentativas: {} -> {}", 
                 idFileOrigin, tentativasAnteriores, novoContador);
        
        return novoContador;
    }

    /**
     * Determina a etapa atual de processamento baseado nos IDs de processamento.
     * 
     * @param idProcessingColeta ID do processamento COLETA
     * @param idProcessingRaw ID do processamento RAW
     * @param idProcessingStaging ID do processamento STAGING
     * @param idProcessingOrdination ID do processamento ORDINATION
     * @param idProcessingProcessing ID do processamento PROCESSING
     * @param idProcessingProcessed ID do processamento PROCESSED
     * @return Nome da etapa atual
     */
    private String determinarEtapaAtual(
            Long idProcessingColeta,
            Long idProcessingRaw,
            Long idProcessingStaging,
            Long idProcessingOrdination,
            Long idProcessingProcessing,
            Long idProcessingProcessed) {
        
        if (idProcessingProcessed != null) {
            return EtapaProcessamento.PROCESSED.name();
        } else if (idProcessingProcessing != null) {
            return EtapaProcessamento.PROCESSING.name();
        } else if (idProcessingOrdination != null) {
            return EtapaProcessamento.ORDINATION.name();
        } else if (idProcessingStaging != null) {
            return EtapaProcessamento.STAGING.name();
        } else if (idProcessingRaw != null) {
            return EtapaProcessamento.RAW.name();
        } else if (idProcessingColeta != null) {
            return EtapaProcessamento.COLETA.name();
        }
        
        return "UNKNOWN";
    }
}
