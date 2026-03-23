package com.controle.arquivos.processor.messaging;

import com.controle.arquivos.common.domain.entity.FileOrigin;
import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.logging.LoggingUtils;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.controle.arquivos.common.repository.FileOriginRepository;
import com.controle.arquivos.processor.dto.MensagemProcessamento;
import com.controle.arquivos.processor.service.ProcessadorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consumidor RabbitMQ para mensagens de processamento de arquivos.
 * 
 * Responsabilidades:
 * - Consumir mensagens da fila de processamento
 * - Deserializar MensagemProcessamento
 * - Validar que o arquivo existe e não foi processado
 * - Invocar ProcessadorService para processar o arquivo
 * - Implementar ACK/NACK manual para controle de reprocessamento
 * 
 * **Valida: Requisitos 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQConsumer {

    private final ProcessadorService processadorService;
    private final FileOriginRepository fileOriginRepository;
    private final FileOriginClientProcessingRepository processingRepository;
    private final ObjectMapper objectMapper;

    /**
     * Consome mensagens da fila de processamento.
     * 
     * Implementa validação de mensagem:
     * - Verifica se arquivo existe em file_origin
     * - Verifica se arquivo não foi processado (status CONCLUIDO)
     * - Se inválida, descarta e registra alerta
     * 
     * Implementa ACK/NACK manual:
     * - ACK: Processamento bem-sucedido
     * - NACK: Falha durante processamento (permite reprocessamento)
     * 
     * @param message mensagem RabbitMQ
     * @param channel canal RabbitMQ para ACK/NACK manual
     */
    @RabbitListener(queues = "${rabbitmq.queue.processamento}", ackMode = "MANUAL")
    public void consumir(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        MensagemProcessamento mensagem = null;
        
        try {
            // Deserializar mensagem
            mensagem = objectMapper.readValue(message.getBody(), MensagemProcessamento.class);
            
            // Configurar correlation ID para logging
            if (mensagem.getCorrelationId() != null) {
                LoggingUtils.setCorrelationId(mensagem.getCorrelationId());
            }
            
            log.info("Mensagem recebida do RabbitMQ - idFileOrigin: {}, nomeArquivo: {}", 
                     mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
            
            // Validar mensagem
            if (!validarMensagem(mensagem)) {
                log.warn("Mensagem inválida descartada - idFileOrigin: {}, nomeArquivo: {}", 
                         mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
                // ACK para descartar mensagem inválida (não reprocessar)
                channel.basicAck(deliveryTag, false);
                return;
            }
            
            // Processar arquivo
            processadorService.processarArquivo(mensagem);
            
            // ACK manual após processamento bem-sucedido
            channel.basicAck(deliveryTag, false);
            log.info("Processamento concluído com sucesso - idFileOrigin: {}, nomeArquivo: {}", 
                     mensagem.getIdFileOrigin(), mensagem.getNomeArquivo());
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem - idFileOrigin: {}, nomeArquivo: {}, erro: {}", 
                      mensagem != null ? mensagem.getIdFileOrigin() : "N/A",
                      mensagem != null ? mensagem.getNomeArquivo() : "N/A",
                      e.getMessage(), e);
            
            try {
                // NACK manual em caso de falha para reprocessamento
                // requeue=true permite que a mensagem seja reprocessada
                channel.basicNack(deliveryTag, false, true);
                log.info("Mensagem rejeitada para reprocessamento - idFileOrigin: {}", 
                         mensagem != null ? mensagem.getIdFileOrigin() : "N/A");
            } catch (Exception nackException) {
                log.error("Erro ao enviar NACK: {}", nackException.getMessage(), nackException);
            }
        } finally {
            LoggingUtils.clearCorrelationId();
        }
    }

    /**
     * Valida se a mensagem é válida para processamento.
     * 
     * Critérios de validação:
     * 1. Arquivo existe na tabela file_origin
     * 2. Arquivo não foi processado (não possui registro CONCLUIDO em file_origin_client_processing)
     * 
     * @param mensagem mensagem a validar
     * @return true se mensagem é válida, false caso contrário
     */
    private boolean validarMensagem(MensagemProcessamento mensagem) {
        // Validar campos obrigatórios
        if (mensagem.getIdFileOrigin() == null) {
            log.warn("Mensagem inválida: idFileOrigin é nulo");
            return false;
        }
        
        // Verificar se arquivo existe em file_origin
        Optional<FileOrigin> fileOriginOpt = fileOriginRepository.findById(mensagem.getIdFileOrigin());
        if (fileOriginOpt.isEmpty()) {
            log.warn("Arquivo não encontrado em file_origin - idFileOrigin: {}", 
                     mensagem.getIdFileOrigin());
            return false;
        }
        
        FileOrigin fileOrigin = fileOriginOpt.get();
        
        // Verificar se arquivo está ativo
        if (!fileOrigin.getActive()) {
            log.warn("Arquivo inativo em file_origin - idFileOrigin: {}", 
                     mensagem.getIdFileOrigin());
            return false;
        }
        
        // Verificar se arquivo já foi processado
        // Um arquivo é considerado processado se existe registro com status CONCLUIDO
        // na etapa PROCESSED (última etapa do fluxo)
        boolean jaProcessado = processingRepository.findByFileOriginClientId(mensagem.getIdFileOrigin())
            .stream()
            .anyMatch(p -> p.getStep() == EtapaProcessamento.PROCESSED 
                        && p.getStatus() == StatusProcessamento.CONCLUIDO);
        
        if (jaProcessado) {
            log.warn("Arquivo já foi processado - idFileOrigin: {}", mensagem.getIdFileOrigin());
            return false;
        }
        
        log.debug("Mensagem validada com sucesso - idFileOrigin: {}", mensagem.getIdFileOrigin());
        return true;
    }
}
