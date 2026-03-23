package com.controle.arquivos.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utilitários para gerenciamento de contexto de logging.
 * Fornece métodos para adicionar e remover informações do MDC.
 * 
 * **Valida: Requisitos 20.2**
 */
public class LoggingUtils {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String FILE_ORIGIN_ID_KEY = "fileOriginId";
    private static final String FILE_NAME_KEY = "fileName";
    private static final String CLIENT_ID_KEY = "clientId";
    private static final String LAYOUT_ID_KEY = "layoutId";
    private static final String STEP_KEY = "step";
    private static final String ACQUIRER_ID_KEY = "acquirerId";

    private LoggingUtils() {
        // Classe utilitária - construtor privado
    }

    /**
     * Adiciona ou atualiza o correlationId no MDC.
     * Se nenhum correlationId for fornecido, gera um novo UUID.
     * 
     * @param correlationId ID de correlação (pode ser null)
     * @return O correlationId usado (gerado ou fornecido)
     */
    public static String setCorrelationId(String correlationId) {
        String id = (correlationId != null && !correlationId.trim().isEmpty())
                ? correlationId
                : UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, id);
        return id;
    }

    /**
     * Obtém o correlationId atual do MDC.
     * 
     * @return O correlationId ou null se não estiver definido
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Remove o correlationId do MDC.
     */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }

    /**
     * Adiciona o ID do arquivo de origem ao MDC.
     * 
     * @param fileOriginId ID do arquivo de origem
     */
    public static void setFileOriginId(Long fileOriginId) {
        if (fileOriginId != null) {
            MDC.put(FILE_ORIGIN_ID_KEY, fileOriginId.toString());
        }
    }

    /**
     * Adiciona o nome do arquivo ao MDC.
     * 
     * @param fileName Nome do arquivo
     */
    public static void setFileName(String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            MDC.put(FILE_NAME_KEY, fileName);
        }
    }

    /**
     * Adiciona o ID do cliente ao MDC.
     * 
     * @param clientId ID do cliente
     */
    public static void setClientId(Long clientId) {
        if (clientId != null) {
            MDC.put(CLIENT_ID_KEY, clientId.toString());
        }
    }

    /**
     * Adiciona o ID do layout ao MDC.
     * 
     * @param layoutId ID do layout
     */
    public static void setLayoutId(Long layoutId) {
        if (layoutId != null) {
            MDC.put(LAYOUT_ID_KEY, layoutId.toString());
        }
    }

    /**
     * Adiciona a etapa de processamento ao MDC.
     * 
     * @param step Etapa de processamento
     */
    public static void setStep(String step) {
        if (step != null && !step.trim().isEmpty()) {
            MDC.put(STEP_KEY, step);
        }
    }

    /**
     * Adiciona o ID do adquirente ao MDC.
     * 
     * @param acquirerId ID do adquirente
     */
    public static void setAcquirerId(Long acquirerId) {
        if (acquirerId != null) {
            MDC.put(ACQUIRER_ID_KEY, acquirerId.toString());
        }
    }

    /**
     * Remove todos os campos de contexto do MDC.
     * Útil para limpar o contexto após o processamento.
     */
    public static void clearAll() {
        MDC.clear();
    }

    /**
     * Remove campos específicos de arquivo do MDC.
     */
    public static void clearFileContext() {
        MDC.remove(FILE_ORIGIN_ID_KEY);
        MDC.remove(FILE_NAME_KEY);
        MDC.remove(CLIENT_ID_KEY);
        MDC.remove(LAYOUT_ID_KEY);
        MDC.remove(STEP_KEY);
        MDC.remove(ACQUIRER_ID_KEY);
    }
}
