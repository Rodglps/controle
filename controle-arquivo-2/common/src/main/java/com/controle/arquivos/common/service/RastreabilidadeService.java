package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Serviço responsável por registrar todas as etapas de processamento de arquivos.
 * Gerencia a rastreabilidade completa através da tabela file_origin_client_processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RastreabilidadeService {

    private final FileOriginClientProcessingRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Registra uma nova etapa de processamento.
     * 
     * @param idFileOriginClient ID do file_origin_client
     * @param step Etapa do processamento
     * @param status Status inicial da etapa
     * @return ID do registro de processamento criado
     */
    @Transactional
    public Long registrarEtapa(Long idFileOriginClient, EtapaProcessamento step, StatusProcessamento status) {
        if (idFileOriginClient == null) {
            throw new IllegalArgumentException("idFileOriginClient não pode ser nulo");
        }
        if (step == null) {
            throw new IllegalArgumentException("step não pode ser nulo");
        }
        if (status == null) {
            throw new IllegalArgumentException("status não pode ser nulo");
        }

        // Validar transição de status
        validarTransicaoStatus(null, status);

        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
            .fileOriginClientId(idFileOriginClient)
            .step(step)
            .status(status)
            .active(true)
            .build();

        FileOriginClientProcessing saved = repository.save(processing);
        
        log.info("Etapa {} registrada com status {} para file_origin_client_id={}, processing_id={}", 
            step, status, idFileOriginClient, saved.getId());

        return saved.getId();
    }

    /**
     * Atualiza o status de um registro de processamento.
     * 
     * @param idProcessing ID do registro de processamento
     * @param status Novo status
     * @param mensagemErro Mensagem de erro (opcional, usado quando status é ERRO)
     */
    @Transactional
    public void atualizarStatus(Long idProcessing, StatusProcessamento status, String mensagemErro) {
        if (idProcessing == null) {
            throw new IllegalArgumentException("idProcessing não pode ser nulo");
        }
        if (status == null) {
            throw new IllegalArgumentException("status não pode ser nulo");
        }

        FileOriginClientProcessing processing = repository.findById(idProcessing)
            .orElseThrow(() -> new IllegalArgumentException(
                "Registro de processamento não encontrado: " + idProcessing));

        // Validar transição de status
        validarTransicaoStatus(processing.getStatus(), status);

        processing.setStatus(status);
        
        if (status == StatusProcessamento.ERRO && mensagemErro != null) {
            processing.setMessageError(mensagemErro);
        }

        repository.save(processing);
        
        log.info("Status atualizado para {} no processing_id={}", status, idProcessing);
    }

    /**
     * Registra o início do processamento de uma etapa.
     * 
     * @param idProcessing ID do registro de processamento
     */
    @Transactional
    public void registrarInicio(Long idProcessing) {
        if (idProcessing == null) {
            throw new IllegalArgumentException("idProcessing não pode ser nulo");
        }

        FileOriginClientProcessing processing = repository.findById(idProcessing)
            .orElseThrow(() -> new IllegalArgumentException(
                "Registro de processamento não encontrado: " + idProcessing));

        processing.setStepStart(Instant.now());
        processing.setStatus(StatusProcessamento.PROCESSAMENTO);

        repository.save(processing);
        
        log.info("Início de processamento registrado para processing_id={} às {}", 
            idProcessing, processing.getStepStart());
    }

    /**
     * Registra a conclusão do processamento de uma etapa.
     * 
     * @param idProcessing ID do registro de processamento
     * @param infoAdicional Informações adicionais a serem armazenadas em JSON (opcional)
     */
    @Transactional
    public void registrarConclusao(Long idProcessing, Map<String, Object> infoAdicional) {
        if (idProcessing == null) {
            throw new IllegalArgumentException("idProcessing não pode ser nulo");
        }

        FileOriginClientProcessing processing = repository.findById(idProcessing)
            .orElseThrow(() -> new IllegalArgumentException(
                "Registro de processamento não encontrado: " + idProcessing));

        processing.setStepEnd(Instant.now());
        processing.setStatus(StatusProcessamento.CONCLUIDO);

        if (infoAdicional != null && !infoAdicional.isEmpty()) {
            try {
                String jsonInfo = objectMapper.writeValueAsString(infoAdicional);
                processing.setAdditionalInfo(jsonInfo);
            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar informações adicionais para JSON", e);
                // Não lançar exceção, apenas logar o erro
            }
        }

        repository.save(processing);
        
        log.info("Conclusão de processamento registrada para processing_id={} às {}", 
            idProcessing, processing.getStepEnd());
    }

    /**
     * Valida se a transição de status é válida.
     * 
     * Transições válidas:
     * - null -> EM_ESPERA (criação inicial)
     * - EM_ESPERA -> PROCESSAMENTO
     * - PROCESSAMENTO -> CONCLUIDO
     * - PROCESSAMENTO -> ERRO
     * - EM_ESPERA -> ERRO (erro antes de iniciar processamento)
     * 
     * @param statusAtual Status atual (pode ser null para criação inicial)
     * @param novoStatus Novo status
     * @throws IllegalStateException se a transição não for válida
     */
    private void validarTransicaoStatus(StatusProcessamento statusAtual, StatusProcessamento novoStatus) {
        // Criação inicial - apenas EM_ESPERA é permitido
        if (statusAtual == null) {
            if (novoStatus != StatusProcessamento.EM_ESPERA) {
                throw new IllegalStateException(
                    "Status inicial deve ser EM_ESPERA, mas foi fornecido: " + novoStatus);
            }
            return;
        }

        // Validar transições baseadas no status atual
        boolean transicaoValida = switch (statusAtual) {
            case EM_ESPERA -> novoStatus == StatusProcessamento.PROCESSAMENTO || 
                             novoStatus == StatusProcessamento.ERRO;
            case PROCESSAMENTO -> novoStatus == StatusProcessamento.CONCLUIDO || 
                                 novoStatus == StatusProcessamento.ERRO;
            case CONCLUIDO, ERRO -> false; // Estados finais, não permitem transição
        };

        if (!transicaoValida) {
            throw new IllegalStateException(
                String.format("Transição de status inválida: %s -> %s", statusAtual, novoStatus));
        }
    }
}
