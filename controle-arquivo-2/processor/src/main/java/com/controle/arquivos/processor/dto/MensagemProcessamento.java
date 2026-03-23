package com.controle.arquivos.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO que representa uma mensagem de processamento recebida do RabbitMQ.
 * Contém informações necessárias para o Processador baixar e processar um arquivo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensagemProcessamento implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID do arquivo na tabela file_origin
     */
    @JsonProperty("idt_file_origin")
    private Long idFileOrigin;

    /**
     * Nome do arquivo
     */
    @JsonProperty("des_file_name")
    private String nomeArquivo;

    /**
     * ID do mapeamento origem-destino (sever_paths_in_out)
     */
    @JsonProperty("idt_sever_paths_in_out")
    private Long idMapeamentoOrigemDestino;

    /**
     * ID de correlação para rastreamento
     */
    @JsonProperty("correlation_id")
    private String correlationId;
}
