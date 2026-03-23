package com.controle.arquivos.processor.exception;

/**
 * Exceção para erros não recuperáveis que não permitem retry.
 * 
 * Exemplos: arquivo não encontrado, cliente não identificado, layout não identificado,
 * credenciais inválidas, violação de constraint.
 */
public class ErroNaoRecuperavelException extends RuntimeException {
    
    public ErroNaoRecuperavelException(String message) {
        super(message);
    }
    
    public ErroNaoRecuperavelException(String message, Throwable cause) {
        super(message, cause);
    }
}
