package com.controle.arquivos.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilitário para logging estruturado de erros.
 * Garante que todos os erros sejam registrados em formato JSON com contexto completo.
 * 
 * **Valida: Requisitos 15.1, 15.2, 15.5, 20.1, 20.2, 20.4**
 */
public class StructuredErrorLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredErrorLogger.class);
    
    // Padrões para detectar credenciais em logs
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(password|senha|secret|token|credential|key)\\s*[:=]\\s*['\"]?([^'\"\\s,}]+)",
        Pattern.CASE_INSENSITIVE
    );
    
    private StructuredErrorLogger() {
        // Classe utilitária - construtor privado
    }

    /**
     * Registra erro estruturado com contexto completo.
     * 
     * @param logger Logger específico da classe que está registrando o erro
     * @param message Mensagem de erro
     * @param exception Exceção que causou o erro
     * @param context Contexto adicional (arquivo, adquirente, etapa, etc.)
     */
    public static void logError(Logger logger, String message, Exception exception, ErrorContext context) {
        // Configurar MDC com contexto
        if (context != null) {
            context.applyToMDC();
        }
        
        try {
            // Extrair stack trace completo
            String stackTrace = extractStackTrace(exception);
            
            // Sanitizar stack trace para remover credenciais
            stackTrace = sanitizeCredentials(stackTrace);
            
            // Criar mapa de informações adicionais
            Map<String, Object> additionalInfo = new HashMap<>();
            additionalInfo.put("exceptionType", exception.getClass().getName());
            additionalInfo.put("stackTrace", stackTrace);
            
            if (context != null) {
                additionalInfo.put("context", context.toMap());
            }
            
            // Registrar log de erro
            // O logback irá serializar automaticamente para JSON
            logger.error("{} - Additional info: {}", 
                sanitizeCredentials(message), 
                additionalInfo, 
                exception);
            
        } finally {
            // Limpar contexto do MDC após logging
            if (context != null) {
                context.clearFromMDC();
            }
        }
    }

    /**
     * Registra erro estruturado sem contexto adicional.
     * 
     * @param logger Logger específico da classe
     * @param message Mensagem de erro
     * @param exception Exceção que causou o erro
     */
    public static void logError(Logger logger, String message, Exception exception) {
        logError(logger, message, exception, null);
    }

    /**
     * Extrai stack trace completo de uma exceção.
     * 
     * @param exception Exceção
     * @return Stack trace como string
     */
    private static String extractStackTrace(Exception exception) {
        if (exception == null) {
            return "";
        }
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Remove credenciais de strings para evitar exposição em logs.
     * 
     * @param text Texto a ser sanitizado
     * @return Texto com credenciais mascaradas
     */
    private static String sanitizeCredentials(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Substituir valores de credenciais por ***
        return PASSWORD_PATTERN.matcher(text).replaceAll("$1=***");
    }

    /**
     * Classe para encapsular contexto de erro.
     */
    public static class ErrorContext {
        private String fileName;
        private Long fileOriginId;
        private Long acquirerId;
        private String step;
        private Long clientId;
        private Long layoutId;
        private String correlationId;
        
        public ErrorContext() {
        }
        
        public ErrorContext fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public ErrorContext fileOriginId(Long fileOriginId) {
            this.fileOriginId = fileOriginId;
            return this;
        }
        
        public ErrorContext acquirerId(Long acquirerId) {
            this.acquirerId = acquirerId;
            return this;
        }
        
        public ErrorContext step(String step) {
            this.step = step;
            return this;
        }
        
        public ErrorContext clientId(Long clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public ErrorContext layoutId(Long layoutId) {
            this.layoutId = layoutId;
            return this;
        }
        
        public ErrorContext correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        /**
         * Aplica contexto ao MDC para inclusão automática nos logs.
         */
        void applyToMDC() {
            if (correlationId != null) {
                LoggingUtils.setCorrelationId(correlationId);
            }
            if (fileOriginId != null) {
                LoggingUtils.setFileOriginId(fileOriginId);
            }
            if (fileName != null) {
                LoggingUtils.setFileName(fileName);
            }
            if (acquirerId != null) {
                LoggingUtils.setAcquirerId(acquirerId);
            }
            if (step != null) {
                LoggingUtils.setStep(step);
            }
            if (clientId != null) {
                LoggingUtils.setClientId(clientId);
            }
            if (layoutId != null) {
                LoggingUtils.setLayoutId(layoutId);
            }
        }
        
        /**
         * Remove contexto do MDC.
         */
        void clearFromMDC() {
            LoggingUtils.clearFileContext();
        }
        
        /**
         * Converte contexto para mapa para inclusão em logs.
         */
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (fileName != null) map.put("fileName", fileName);
            if (fileOriginId != null) map.put("fileOriginId", fileOriginId);
            if (acquirerId != null) map.put("acquirerId", acquirerId);
            if (step != null) map.put("step", step);
            if (clientId != null) map.put("clientId", clientId);
            if (layoutId != null) map.put("layoutId", layoutId);
            if (correlationId != null) map.put("correlationId", correlationId);
            return map;
        }
    }
}
