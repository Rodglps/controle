package com.controle.arquivos.processor.exception;

/**
 * Exceção lançada quando nenhum layout é identificado para um arquivo.
 * 
 * Esta exceção é considerada não recuperável, pois indica que nenhuma regra
 * de identificação de layout retornou match para o arquivo. Isso geralmente
 * significa que:
 * - O arquivo não corresponde a nenhum layout configurado
 * - As regras de identificação estão incompletas ou incorretas
 * - O conteúdo do arquivo não segue o padrão esperado
 * 
 * Quando esta exceção é lançada, o sistema deve:
 * - Fazer ACK da mensagem RabbitMQ (não reprocessar)
 * - Marcar o arquivo como ERRO permanente
 * - Registrar erro com detalhes do arquivo, cliente e regras aplicadas
 * - Atualizar status em file_origin_client_processing
 */
public class LayoutNaoIdentificadoException extends ErroNaoRecuperavelException {

    public LayoutNaoIdentificadoException(String message) {
        super(message);
    }

    public LayoutNaoIdentificadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
