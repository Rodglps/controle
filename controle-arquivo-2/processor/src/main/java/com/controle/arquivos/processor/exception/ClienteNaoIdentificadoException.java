package com.controle.arquivos.processor.exception;

/**
 * Exceção lançada quando nenhum cliente é identificado para um arquivo.
 * 
 * Esta exceção é considerada não recuperável, pois indica que nenhuma regra
 * de identificação de cliente retornou match para o arquivo. Isso geralmente
 * significa que:
 * - O arquivo não pertence a nenhum cliente configurado
 * - As regras de identificação estão incompletas ou incorretas
 * - O nome do arquivo não segue o padrão esperado
 * 
 * Quando esta exceção é lançada, o sistema deve:
 * - Fazer ACK da mensagem RabbitMQ (não reprocessar)
 * - Marcar o arquivo como ERRO permanente
 * - Registrar erro com detalhes do arquivo e regras aplicadas
 * - Atualizar status em file_origin_client_processing
 */
public class ClienteNaoIdentificadoException extends ErroNaoRecuperavelException {

    public ClienteNaoIdentificadoException(String message) {
        super(message);
    }

    public ClienteNaoIdentificadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
