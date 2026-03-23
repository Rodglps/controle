package com.controle.arquivos.processor.exception;

/**
 * Exceção para erros recuperáveis que permitem retry.
 * 
 * Exemplos: falhas de conexão, timeouts, erros transientes de banco de dados.
 */
public class ErroRecuperavelException extends RuntimeException {
    
    public ErroRecuperavelException(String message) {
        super(message);
    }
    
    public ErroRecuperavelException(String message, Throwable cause) {
        super(message, cause);
    }
}
