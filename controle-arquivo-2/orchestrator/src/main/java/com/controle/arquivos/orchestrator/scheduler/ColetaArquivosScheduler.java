package com.controle.arquivos.orchestrator.scheduler;

import com.controle.arquivos.orchestrator.service.OrquestradorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduler responsável pela execução periódica do ciclo de coleta de arquivos.
 * Invoca o OrquestradorService em intervalos configuráveis via cron expression.
 * 
 * **Valida: Requisitos 1.1, 5.3**
 */
@Component
public class ColetaArquivosScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ColetaArquivosScheduler.class);

    private final OrquestradorService orquestradorService;

    public ColetaArquivosScheduler(OrquestradorService orquestradorService) {
        this.orquestradorService = orquestradorService;
    }

    /**
     * Executa o ciclo de coleta de arquivos periodicamente.
     * A expressão cron é configurável via propriedade app.scheduler.coleta.cron.
     * 
     * Logs estruturados registram início e fim de cada ciclo com timestamp e duração.
     */
    @Scheduled(cron = "${app.scheduler.coleta.cron:0 */5 * * * *}")
    public void executarCicloColetaAgendado() {
        Instant inicio = Instant.now();
        
        logger.info("Iniciando ciclo de coleta agendado: timestamp={}", inicio);
        
        try {
            orquestradorService.executarCicloColeta();
            
            Instant fim = Instant.now();
            long duracaoMs = fim.toEpochMilli() - inicio.toEpochMilli();
            
            logger.info("Ciclo de coleta concluído com sucesso: timestamp={}, duracao_ms={}", 
                    fim, duracaoMs);
            
        } catch (Exception e) {
            Instant fim = Instant.now();
            long duracaoMs = fim.toEpochMilli() - inicio.toEpochMilli();
            
            logger.error("Ciclo de coleta falhou: timestamp={}, duracao_ms={}, erro={}", 
                    fim, duracaoMs, e.getMessage(), e);
            
            // Não propagar exceção - próxima execução agendada deve ocorrer normalmente
        }
    }
}
