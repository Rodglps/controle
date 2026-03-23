package com.controle.arquivos.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração SFTP.
 * 
 * Mapeia as configurações definidas em application.yml sob o prefixo "sftp".
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {

    /**
     * Timeout de conexão (em milissegundos).
     * Padrão: 30000ms (30 segundos)
     */
    private int timeout = 30000;

    /**
     * Timeout de sessão (em milissegundos).
     * Padrão: 120000ms (2 minutos)
     */
    private int sessionTimeout = 120000;

    /**
     * Timeout de canal (em milissegundos).
     * Padrão: 60000ms (1 minuto)
     */
    private int channelTimeout = 60000;

    /**
     * Indica se deve verificar a chave do host.
     * Padrão: true (em produção deve ser true)
     */
    private boolean strictHostKeyChecking = true;

    /**
     * Caminho para o arquivo known_hosts.
     * Exemplo: /app/config/known_hosts
     */
    private String knownHostsFile;

    /**
     * Configuração padrão para desenvolvimento local.
     */
    private DefaultConfig defaultConfig = new DefaultConfig();

    @Data
    public static class DefaultConfig {
        /**
         * Host SFTP padrão (usado apenas em local).
         */
        private String host;

        /**
         * Porta SFTP padrão (usado apenas em local).
         */
        private int port = 22;

        /**
         * Usuário SFTP padrão (usado apenas em local).
         */
        private String username;

        /**
         * Senha SFTP padrão (usado apenas em local).
         */
        private String password;

        /**
         * Timeout padrão (em milissegundos).
         */
        private int timeout = 30000;

        /**
         * Timeout de sessão padrão (em milissegundos).
         */
        private int sessionTimeout = 60000;

        /**
         * Timeout de canal padrão (em milissegundos).
         */
        private int channelTimeout = 30000;
    }
}
