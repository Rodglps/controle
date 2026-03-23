package com.controle.arquivos.processor.exception;

/**
 * Exceção lançada quando ocorre falha durante o upload de arquivo para o destino.
 * 
 * Esta exceção é considerada recuperável, pois falhas de upload geralmente são
 * causadas por problemas temporários de rede, throttling, ou indisponibilidade
 * temporária do serviço de destino (S3 ou SFTP).
 * 
 * Quando esta exceção é lançada, o sistema deve:
 * - Fazer NACK da mensagem RabbitMQ para reprocessamento
 * - Manter o arquivo na origem (não deletar)
 * - Registrar erro detalhado com informações do destino
 * - Permitir retry até o limite de 5 tentativas
 * 
 * Cenários que geram esta exceção:
 * - Falha ao conectar ao servidor de destino
 * - Timeout durante upload
 * - Erro de validação de tamanho após upload
 * - Falha ao completar multipart upload no S3
 * - Erro ao escrever no SFTP de destino
 */
public class FalhaUploadException extends ErroRecuperavelException {

    public FalhaUploadException(String message) {
        super(message);
    }

    public FalhaUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
