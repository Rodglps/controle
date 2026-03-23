# Guia de Configuração - Controle de Arquivos

Este documento detalha todas as propriedades de configuração, perfis de ambiente, variáveis de ambiente e integração com Vault.

## Índice

- [Perfis de Ambiente](#perfis-de-ambiente)
- [Propriedades de Aplicação](#propriedades-de-aplicação)
- [Configuração de Banco de Dados](#configuração-de-banco-de-dados)
- [Configuração de RabbitMQ](#configuração-de-rabbitmq)
- [Configuração de AWS S3](#configuração-de-aws-s3)
- [Configuração de SFTP](#configuração-de-sftp)
- [Integração com Vault](#integração-com-vault)
- [Configuração de Logging](#configuração-de-logging)
- [Health Checks e Actuator](#health-checks-e-actuator)
- [Configuração de Streaming](#configuração-de-streaming)
- [Variáveis de Ambiente](#variáveis-de-ambiente)

## Perfis de Ambiente

O sistema suporta 4 perfis Spring Boot:

### Local (local)

Ambiente de desenvolvimento local com Docker Compose.

**Ativação**:
```bash
export SPRING_PROFILES_ACTIVE=local
# ou
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Características**:
- Oracle XE local (localhost:1521)
- RabbitMQ local (localhost:5672)
- LocalStack S3 (localhost:4566)
- SFTP local (localhost:2222)
- Vault desabilitado
- Logs em DEBUG

### Development (dev)

Ambiente de desenvolvimento compartilhado.

**Ativação**:
```bash
export SPRING_PROFILES_ACTIVE=dev
```

**Características**:
- Oracle RDS de desenvolvimento
- RabbitMQ gerenciado
- AWS S3 real
- Vault habilitado
- Scheduler: a cada 10 minutos
- Logs em DEBUG

### Staging (staging)

Ambiente de homologação.

**Ativação**:
```bash
export SPRING_PROFILES_ACTIVE=staging
```

**Características**:
- Oracle RDS de staging
- RabbitMQ gerenciado
- AWS S3 real
- Vault habilitado
- Scheduler: a cada 7 minutos
- Logs em INFO

### Production (prod)

Ambiente de produção.

**Ativação**:
```bash
export SPRING_PROFILES_ACTIVE=prod
```

**Características**:
- Oracle RDS de produção
- RabbitMQ gerenciado (HA)
- AWS S3 real
- Vault habilitado
- Scheduler: a cada 5 minutos
- Logs em INFO (app), WARN (root)

## Propriedades de Aplicação

### Configuração Base (application.yml)

```yaml
spring:
  application:
    name: controle-arquivos
  
  jpa:
    open-in-view: false
    show-sql: false
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

  jackson:
    serialization:
      write-dates-as-timestamps: false
    time-zone: UTC

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true

logging:
  level:
    root: INFO
    com.controle.arquivos: INFO

app:
  streaming:
    chunk-size: 5242880  # 5MB
    buffer-size: 8192     # 8KB
  retry:
    max-attempts: 3
    backoff-delay: 1000
  processing:
    max-reprocessing-attempts: 5
    timeout-seconds: 300
```

### Propriedades Customizadas

#### app.streaming

Configurações de streaming de arquivos.

| Propriedade | Tipo | Padrão | Descrição |
|-------------|------|--------|-----------|
| `app.streaming.chunk-size` | Integer | 5242880 | Tamanho do chunk para streaming (bytes) |
| `app.streaming.buffer-size` | Integer | 8192 | Tamanho do buffer I/O (bytes) |

**Recomendações**:
- **Local/Dev**: 5MB chunks
- **Staging/Prod**: 10MB chunks para melhor throughput

#### app.retry

Configurações de retry para operações.

| Propriedade | Tipo | Padrão | Descrição |
|-------------|------|--------|-----------|
| `app.retry.max-attempts` | Integer | 3 | Máximo de tentativas |
| `app.retry.backoff-delay` | Long | 1000 | Delay entre tentativas (ms) |

#### app.processing

Configurações de processamento.

| Propriedade | Tipo | Padrão | Descrição |
|-------------|------|--------|-----------|
| `app.processing.max-reprocessing-attempts` | Integer | 5 | Máximo de reprocessamentos |
| `app.processing.timeout-seconds` | Integer | 300 | Timeout de processamento (segundos) |

#### app.scheduler (Orquestrador)

Configurações do scheduler de coleta.

| Propriedade | Tipo | Padrão | Descrição |
|-------------|------|--------|-----------|
| `app.scheduler.enabled` | Boolean | true | Habilita/desabilita scheduler |
| `app.scheduler.cron` | String | `0 */5 * * * *` | Expressão cron |

**Exemplos de Cron**:
- `0 */5 * * * *`: A cada 5 minutos
- `0 */10 * * * *`: A cada 10 minutos
- `0 0 * * * *`: A cada hora
- `0 0 0 * * *`: Diariamente à meia-noite

## Configuração de Banco de Dados

### Oracle Database

#### Propriedades Spring Datasource

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:oracle:thin:@localhost:1521:XE}
    username: ${DB_USERNAME:system}
    password: ${DB_PASSWORD:Oracle123}
    driver-class-name: oracle.jdbc.OracleDriver
```

#### Hikari Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_HIKARI_MAX_POOL_SIZE:10}
      minimum-idle: ${DB_HIKARI_MIN_IDLE:2}
      connection-timeout: ${DB_HIKARI_CONNECTION_TIMEOUT:30000}
      idle-timeout: ${DB_HIKARI_IDLE_TIMEOUT:600000}
      max-lifetime: ${DB_HIKARI_MAX_LIFETIME:1800000}
      pool-name: ControleArquivosHikariPool
      auto-commit: false
      connection-test-query: SELECT 1 FROM DUAL
```

**Tuning por Ambiente**:

| Ambiente | max-pool-size | min-idle | Justificativa |
|----------|---------------|----------|---------------|
| Local | 5 | 1 | Recursos limitados |
| Dev | 10 | 2 | Desenvolvimento |
| Staging | 20 | 5 | Carga moderada |
| Prod | 50 | 10 | Alta carga |

#### JPA/Hibernate

```yaml
spring:
  jpa:
    show-sql: ${DB_SHOW_SQL:false}
    properties:
      hibernate:
        format_sql: true
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
    hibernate:
      ddl-auto: validate  # NUNCA usar 'update' ou 'create' em produção
```

**Importante**: `ddl-auto: validate` garante que o schema do banco corresponde às entidades, mas não faz alterações. Use scripts DDL para mudanças de schema.

## Configuração de RabbitMQ

### Conexão

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:admin}
    password: ${RABBITMQ_PASSWORD:admin123}
    virtual-host: ${RABBITMQ_VIRTUAL_HOST:/}
    connection-timeout: ${RABBITMQ_CONNECTION_TIMEOUT:30000}
```

### Consumer (Processador)

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual  # ACK/NACK manual
        prefetch: ${RABBITMQ_LISTENER_PREFETCH:1}
        concurrency: ${RABBITMQ_LISTENER_CONCURRENCY:1}
        max-concurrency: ${RABBITMQ_LISTENER_MAX_CONCURRENCY:10}
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
          multiplier: 2.0
```

**Tuning de Consumer**:

| Ambiente | prefetch | concurrency | max-concurrency |
|----------|----------|-------------|-----------------|
| Local | 1 | 1 | 2 |
| Dev | 5 | 2 | 10 |
| Staging | 10 | 5 | 20 |
| Prod | 20 | 10 | 50 |

**prefetch**: Número de mensagens que o consumer busca antecipadamente. Valores maiores aumentam throughput mas podem causar desbalanceamento.

**concurrency**: Número inicial de threads consumidoras.

**max-concurrency**: Número máximo de threads consumidoras (auto-scaling).

### Publisher (Orquestrador)

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated  # Confirmação de publicação
    publisher-returns: true
    template:
      retry:
        enabled: true
        initial-interval: 1000
        max-attempts: 3
        multiplier: 2.0
```

### Filas e Exchanges

Configuração programática em `RabbitMQConfig.java`:

```java
@Bean
public Queue fileProcessingQueue() {
    return QueueBuilder.durable("file.processing.queue")
        .withArgument("x-message-ttl", 3600000)  // 1 hora
        .withArgument("x-max-length", 10000)
        .build();
}

@Bean
public DirectExchange fileProcessingExchange() {
    return new DirectExchange("file.processing.exchange", true, false);
}
```

## Configuração de AWS S3

### Credenciais

```yaml
aws:
  region: ${AWS_REGION:us-east-1}
  credentials:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}
```

**Produção**: Use IAM Roles em vez de credenciais estáticas.

### S3 Client

```yaml
aws:
  s3:
    endpoint: ${AWS_S3_ENDPOINT:}  # Vazio para AWS real, URL para LocalStack
    bucket-name: ${AWS_S3_BUCKET_NAME:controle-arquivos}
    path-style-access: ${AWS_S3_PATH_STYLE_ACCESS:false}  # true para LocalStack
```

**path-style-access**:
- `false`: Virtual-hosted style (padrão AWS): `bucket.s3.amazonaws.com`
- `true`: Path style (LocalStack): `s3.amazonaws.com/bucket`

### Multipart Upload

Configuração programática em `S3Config.java`:

```java
@Bean
public S3Client s3Client() {
    return S3Client.builder()
        .region(Region.of(awsRegion))
        .credentialsProvider(credentialsProvider)
        .serviceConfiguration(S3Configuration.builder()
            .pathStyleAccessEnabled(pathStyleAccess)
            .build())
        .build();
}
```

**Multipart Threshold**: 5MB (padrão AWS SDK)

## Configuração de SFTP

### Configuração Padrão

```yaml
sftp:
  default:
    host: ${SFTP_HOST:localhost}
    port: ${SFTP_PORT:22}
    username: ${SFTP_USERNAME:sftpuser}
    password: ${SFTP_PASSWORD:sftppass}
    timeout: ${SFTP_TIMEOUT:30000}
    session-timeout: ${SFTP_SESSION_TIMEOUT:60000}
    channel-timeout: ${SFTP_CHANNEL_TIMEOUT:30000}
```

### Configurações Dinâmicas (Banco de Dados)

Servidores SFTP são configurados na tabela `server`:

```sql
INSERT INTO server (
    idt_server, cod_server, cod_vault, des_vault_secret,
    des_server_type, des_server_origin, flg_active
) VALUES (
    1, 'SFTP_CIELO', 'vault-sftp', 'secret/sftp/cielo',
    'SFTP', 'EXTERNO', 1
);
```

**Campos**:
- `cod_vault`: Código do Vault para obter credenciais
- `des_vault_secret`: Caminho do secret no Vault
- `des_server_type`: SFTP, S3, NFS, BLOB_STORAGE, OBJECT_STORAGE
- `des_server_origin`: INTERNO, EXTERNO

### Timeouts

| Timeout | Padrão | Descrição |
|---------|--------|-----------|
| `timeout` | 30000ms | Timeout de conexão |
| `session-timeout` | 60000ms | Timeout de sessão |
| `channel-timeout` | 30000ms | Timeout de canal |

## Integração com Vault

### Configuração

```yaml
vault:
  enabled: ${VAULT_ENABLED:true}
  uri: ${VAULT_URI:https://vault.example.com}
  token: ${VAULT_TOKEN}
  kv:
    enabled: true
    backend: ${VAULT_KV_BACKEND:secret}
    default-context: ${VAULT_DEFAULT_CONTEXT:controle-arquivos}
```

### Estrutura de Secrets

Secrets são organizados por tipo:

```
secret/
├── controle-arquivos/
│   ├── sftp/
│   │   ├── cielo
│   │   ├── rede
│   │   └── getnet
│   ├── s3/
│   │   └── credentials
│   └── database/
│       └── credentials
```

### Formato de Secret SFTP

```json
{
  "host": "sftp.cielo.com.br",
  "port": "22",
  "username": "user_cielo",
  "password": "***",
  "privateKey": "-----BEGIN RSA PRIVATE KEY-----\n...",
  "passphrase": "***"
}
```

### Uso Programático

```java
@Service
public class VaultClient {
    
    @Value("${vault.uri}")
    private String vaultUri;
    
    @Value("${vault.token}")
    private String vaultToken;
    
    public SftpCredentials getSftpCredentials(String codVault, String secretPath) {
        VaultTemplate vaultTemplate = new VaultTemplate(
            VaultEndpoint.from(URI.create(vaultUri)),
            new TokenAuthentication(vaultToken)
        );
        
        VaultResponse response = vaultTemplate.read(secretPath);
        Map<String, Object> data = response.getData();
        
        return SftpCredentials.builder()
            .host((String) data.get("host"))
            .port(Integer.parseInt((String) data.get("port")))
            .username((String) data.get("username"))
            .password((String) data.get("password"))
            .build();
    }
}
```

### Cache de Credenciais

Credenciais são cacheadas por 5 minutos para reduzir chamadas ao Vault:

```java
@Cacheable(value = "vault-credentials", key = "#secretPath")
public SftpCredentials getSftpCredentials(String codVault, String secretPath) {
    // ...
}
```

### Renovação de Token

Para ambientes de longa duração, configure renovação automática:

```yaml
vault:
  token-renewal:
    enabled: true
    interval: 3600  # 1 hora
```

## Configuração de Logging

### Formato Estruturado (JSON)

Configuração em `logback-spring.xml`:

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>correlationId</includeMdcKeyName>
        <includeMdcKeyName>fileName</includeMdcKeyName>
        <includeMdcKeyName>clientId</includeMdcKeyName>
        <includeMdcKeyName>layoutId</includeMdcKeyName>
    </encoder>
</appender>
```

### Níveis de Log

```yaml
logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    com.controle.arquivos: ${LOG_LEVEL_APP:INFO}
    org.springframework: ${LOG_LEVEL_SPRING:WARN}
    org.hibernate: ${LOG_LEVEL_HIBERNATE:WARN}
    org.hibernate.SQL: ${LOG_LEVEL_SQL:DEBUG}
    com.amazonaws: ${LOG_LEVEL_AWS:INFO}
    software.amazon.awssdk: ${LOG_LEVEL_AWS_SDK:INFO}
```

**Recomendações por Ambiente**:

| Ambiente | root | app | spring | hibernate | sql |
|----------|------|-----|--------|-----------|-----|
| Local | INFO | DEBUG | INFO | WARN | DEBUG |
| Dev | INFO | DEBUG | WARN | WARN | DEBUG |
| Staging | INFO | INFO | WARN | WARN | INFO |
| Prod | WARN | INFO | WARN | ERROR | WARN |

### Correlation ID

Correlation ID é propagado automaticamente via MDC:

```java
@Component
public class CorrelationIdFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

## Health Checks e Actuator

### Endpoints Expostos

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
```

### Health Indicators

Health checks verificam:
- **Database**: Conectividade com Oracle
- **RabbitMQ**: Conectividade com broker
- **Disk Space**: Espaço em disco disponível

### Liveness e Readiness

```yaml
management:
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**Liveness** (`/actuator/health/liveness`):
- Indica se o pod está vivo
- Kubernetes reinicia o pod se falhar

**Readiness** (`/actuator/health/readiness`):
- Indica se o pod está pronto para receber tráfego
- Kubernetes remove o pod do load balancer se falhar

### Métricas Prometheus

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

Métricas disponíveis em `/actuator/prometheus`:
- JVM (memória, threads, GC)
- HTTP (requests, latência)
- Database (conexões, queries)
- RabbitMQ (mensagens, consumers)
- Custom (arquivos processados, erros)

## Configuração de Streaming

### Chunk Size

Tamanho dos chunks para streaming de arquivos:

```yaml
app:
  streaming:
    chunk-size: 5242880  # 5MB
```

**Recomendações**:
- **Arquivos pequenos (<10MB)**: 1MB chunks
- **Arquivos médios (10-100MB)**: 5MB chunks
- **Arquivos grandes (>100MB)**: 10MB chunks

### Buffer Size

Tamanho do buffer para operações I/O:

```yaml
app:
  streaming:
    buffer-size: 8192  # 8KB
```

**Padrão**: 8KB (padrão Java BufferedInputStream/OutputStream)

### Memory Management

Para evitar OutOfMemoryError com arquivos grandes:

```java
// Configuração JVM
-Xms1g -Xmx2g -XX:+UseG1GC
```

**Recomendações por Ambiente**:

| Ambiente | Heap Min | Heap Max | GC |
|----------|----------|----------|-----|
| Local | 512m | 1g | G1GC |
| Dev | 1g | 2g | G1GC |
| Staging | 2g | 4g | G1GC |
| Prod | 4g | 8g | G1GC |

## Variáveis de Ambiente

### Orquestrador

| Variável | Obrigatória | Padrão | Descrição |
|----------|-------------|--------|-----------|
| `SPRING_PROFILES_ACTIVE` | Sim | - | Perfil ativo |
| `DB_URL` | Sim | - | URL do banco |
| `DB_USERNAME` | Sim | - | Usuário do banco |
| `DB_PASSWORD` | Sim | - | Senha do banco |
| `RABBITMQ_HOST` | Sim | - | Host RabbitMQ |
| `RABBITMQ_USERNAME` | Sim | - | Usuário RabbitMQ |
| `RABBITMQ_PASSWORD` | Sim | - | Senha RabbitMQ |
| `VAULT_URI` | Sim | - | URI do Vault |
| `VAULT_TOKEN` | Sim | - | Token do Vault |
| `SCHEDULER_CRON` | Não | `0 */5 * * * *` | Expressão cron |
| `SCHEDULER_ENABLED` | Não | `true` | Habilita scheduler |

### Processador

| Variável | Obrigatória | Padrão | Descrição |
|----------|-------------|--------|-----------|
| `SPRING_PROFILES_ACTIVE` | Sim | - | Perfil ativo |
| `DB_URL` | Sim | - | URL do banco |
| `DB_USERNAME` | Sim | - | Usuário do banco |
| `DB_PASSWORD` | Sim | - | Senha do banco |
| `RABBITMQ_HOST` | Sim | - | Host RabbitMQ |
| `RABBITMQ_USERNAME` | Sim | - | Usuário RabbitMQ |
| `RABBITMQ_PASSWORD` | Sim | - | Senha RabbitMQ |
| `VAULT_URI` | Sim | - | URI do Vault |
| `VAULT_TOKEN` | Sim | - | Token do Vault |
| `AWS_REGION` | Sim | - | Região AWS |
| `AWS_S3_BUCKET_NAME` | Sim | - | Nome do bucket S3 |
| `AWS_ACCESS_KEY_ID` | Condicional | - | Access key AWS (se não usar IAM Role) |
| `AWS_SECRET_ACCESS_KEY` | Condicional | - | Secret key AWS (se não usar IAM Role) |
| `RABBITMQ_LISTENER_PREFETCH` | Não | `1` | Prefetch de mensagens |
| `RABBITMQ_LISTENER_CONCURRENCY` | Não | `1` | Concorrência inicial |
| `RABBITMQ_LISTENER_MAX_CONCURRENCY` | Não | `10` | Concorrência máxima |

### Exemplo de .env

```bash
# Profile
SPRING_PROFILES_ACTIVE=prod

# Database
DB_URL=jdbc:oracle:thin:@oracle-prod.example.com:1521:ORCL
DB_USERNAME=app_user
DB_PASSWORD=***

# RabbitMQ
RABBITMQ_HOST=rabbitmq-prod.example.com
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=app_user
RABBITMQ_PASSWORD=***

# Vault
VAULT_URI=https://vault.example.com
VAULT_TOKEN=hvs.***

# AWS
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=controle-arquivos-prod

# Scheduler (Orquestrador)
SCHEDULER_CRON=0 */5 * * * *
SCHEDULER_ENABLED=true

# Consumer (Processador)
RABBITMQ_LISTENER_PREFETCH=20
RABBITMQ_LISTENER_CONCURRENCY=10
RABBITMQ_LISTENER_MAX_CONCURRENCY=50

# Logging
LOG_LEVEL_ROOT=WARN
LOG_LEVEL_APP=INFO
```

## Validação de Configuração

### Startup Validation

O sistema valida configurações obrigatórias na inicialização:

```java
@Component
public class ConfigurationValidator implements ApplicationListener<ApplicationReadyEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateDatabaseConnection();
        validateRabbitMQConnection();
        validateVaultConnection();
        validateAwsCredentials();
    }
}
```

Se alguma configuração estiver faltando ou inválida, a aplicação falha ao iniciar com mensagem clara.

### Configuration Properties

Use `@ConfigurationProperties` para validação:

```java
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {
    
    @NotNull
    @Min(1024)
    private Integer streamingChunkSize;
    
    @NotNull
    @Min(1)
    @Max(10)
    private Integer retryMaxAttempts;
    
    // getters/setters
}
```

## Troubleshooting de Configuração

### Problema: Aplicação não inicia

**Verificar**:
1. Todas as variáveis obrigatórias estão definidas?
2. Perfil está correto?
3. Logs de startup mostram qual configuração está faltando?

```bash
# Ver configurações carregadas
curl http://localhost:8080/actuator/env
```

### Problema: Conexão com banco falha

**Verificar**:
1. URL do banco está correta?
2. Credenciais estão corretas?
3. Banco está acessível da rede do pod?

```bash
# Testar conexão do pod
kubectl exec -it <pod-name> -- bash
telnet oracle-host 1521
```

### Problema: RabbitMQ não conecta

**Verificar**:
1. Host e porta estão corretos?
2. Virtual host existe?
3. Credenciais estão corretas?

```bash
# Ver configuração RabbitMQ
kubectl get configmap controle-arquivos-config -o yaml | grep rabbitmq
```

## Referências

- [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html)
- [Spring Cloud Vault](https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/)
- [AWS SDK Configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)
- [RabbitMQ Spring AMQP](https://docs.spring.io/spring-amqp/reference/html/)
- [Hikari Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)

