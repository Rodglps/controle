package com.concil.edi.consumer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Configuration class for AWS S3 Client.
 * Supports both LocalStack (development) and real AWS S3 (production).
 */
@Configuration
public class S3Config {

    @Value("${aws.s3.endpoint:${AWS_ENDPOINT:}}")
    private String awsEndpoint;

    @Value("${aws.s3.region:${AWS_REGION:us-east-1}}")
    private String awsRegion;

    @Value("${aws.s3.access-key:${AWS_ACCESS_KEY_ID:test}}")
    private String awsAccessKeyId;

    @Value("${aws.s3.secret-key:${AWS_SECRET_ACCESS_KEY:test}}")
    private String awsSecretAccessKey;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                ));

        // If endpoint is specified (LocalStack), use it with path-style access
        if (awsEndpoint != null && !awsEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(awsEndpoint))
                   .forcePathStyle(true); // Required for LocalStack
        }

        return builder.build();
    }
}
