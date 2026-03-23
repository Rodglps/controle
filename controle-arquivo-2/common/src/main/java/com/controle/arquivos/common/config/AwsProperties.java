package com.controle.arquivos.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração da AWS.
 * 
 * Mapeia as configurações definidas em application.yml sob o prefixo "aws".
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    /**
     * Região AWS.
     * Exemplo: us-east-1
     */
    private String region = "us-east-1";

    private S3 s3 = new S3();
    private Credentials credentials = new Credentials();

    @Data
    public static class S3 {
        /**
         * Endpoint customizado para S3 (usado com LocalStack).
         * Exemplo: http://localhost:4566
         */
        private String endpoint;

        /**
         * Nome do bucket S3.
         */
        private String bucketName;

        /**
         * Indica se deve usar path-style access (necessário para LocalStack).
         * Padrão: false
         */
        private boolean pathStyleAccess = false;
    }

    @Data
    public static class Credentials {
        /**
         * AWS Access Key ID.
         */
        private String accessKey;

        /**
         * AWS Secret Access Key.
         */
        private String secretKey;
    }
}
