package com.controle.arquivos.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração do Vault.
 * 
 * Mapeia as configurações definidas em application.yml sob o prefixo "vault".
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    /**
     * Indica se o Vault está habilitado.
     * Padrão: false (habilitado apenas em dev/staging/prod)
     */
    private boolean enabled = false;

    /**
     * URI do servidor Vault.
     * Exemplo: https://vault.example.com
     */
    private String uri;

    /**
     * Token de autenticação do Vault.
     */
    private String token;

    /**
     * Timeout de conexão (em milissegundos).
     * Padrão: 5000ms (5 segundos)
     */
    private int connectionTimeout = 5000;

    /**
     * Timeout de leitura (em milissegundos).
     * Padrão: 15000ms (15 segundos)
     */
    private int readTimeout = 15000;

    private Kv kv = new Kv();

    @Data
    public static class Kv {
        /**
         * Indica se o KV engine está habilitado.
         * Padrão: true
         */
        private boolean enabled = true;

        /**
         * Backend do KV engine.
         * Padrão: secret
         */
        private String backend = "secret";

        /**
         * Contexto padrão para busca de segredos.
         * Exemplo: controle-arquivos/prod
         */
        private String defaultContext;
    }
}
