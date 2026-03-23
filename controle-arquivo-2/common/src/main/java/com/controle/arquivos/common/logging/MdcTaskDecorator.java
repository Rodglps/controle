package com.controle.arquivos.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Decorator para propagar o contexto MDC para threads assíncronas.
 * Garante que o correlationId e outros campos MDC sejam mantidos
 * em operações executadas em threads diferentes.
 * 
 * **Valida: Requisitos 20.2**
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capturar o contexto MDC da thread atual
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        
        return () -> {
            try {
                // Restaurar o contexto MDC na thread de execução
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                // Limpar o MDC após a execução
                MDC.clear();
            }
        };
    }
}
