package com.controle.arquivos.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração da aplicação.
 * 
 * Mapeia as configurações definidas em application.yml sob o prefixo "app".
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Streaming streaming = new Streaming();
    private Retry retry = new Retry();
    private Processing processing = new Processing();
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Streaming {
        /**
         * Tamanho do chunk para processamento em streaming (em bytes).
         * Padrão: 5MB (5242880 bytes)
         */
        private int chunkSize = 5242880;

        /**
         * Tamanho do buffer para leitura/escrita (em bytes).
         * Padrão: 8KB (8192 bytes)
         */
        private int bufferSize = 8192;
    }

    @Data
    public static class Retry {
        /**
         * Número máximo de tentativas para operações com retry.
         * Padrão: 3
         */
        private int maxAttempts = 3;

        /**
         * Delay inicial entre tentativas (em milissegundos).
         * Padrão: 1000ms (1 segundo)
         */
        private long backoffDelay = 1000;
    }

    @Data
    public static class Processing {
        /**
         * Número máximo de tentativas de reprocessamento para um arquivo.
         * Padrão: 5
         */
        private int maxReprocessingAttempts = 5;

        /**
         * Timeout para processamento de um arquivo (em segundos).
         * Padrão: 300 segundos (5 minutos)
         */
        private int timeoutSeconds = 300;
    }

    @Data
    public static class Scheduler {
        /**
         * Indica se o scheduler está habilitado.
         * Padrão: true
         */
        private boolean enabled = true;

        /**
         * Expressão cron para execução do scheduler.
         * Padrão: "0 *\/5 * * * *" (a cada 5 minutos)
         */
        private String cron = "0 */5 * * * *";
    }
}
