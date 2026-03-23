# Resumo da Configuração Spring Boot - Task 2.4

## Arquivos Criados

### Configurações YAML (common/src/main/resources/)

1. **application.yml** - Configuração base compartilhada
   - Configurações JPA/Hibernate
   - Configurações Jackson (JSON)
   - Endpoints Actuator (health, metrics, prometheus)
   - Configurações de logging
   - Configurações de aplicação (streaming, retry, processamento)

2. **application-local.yml** - Perfil de desenvolvimento local
   - Oracle XE: localhost:1521 (system/Oracle123)
   - RabbitMQ: localhost:5672 (admin/admin123)
   - LocalStack S3: localhost:4566
   - SFTP: localhost:2222 (sftpuser/sftppass)
   - Vault: Desabilitado
   - Logging: DEBUG com SQL visível

3. **application-dev.yml** - Perfil de desenvolvimento
   - Oracle RDS via variáveis de ambiente
   - RabbitMQ AWS MQ com SSL
   - AWS S3 real (bucket dev)
   - SFTP via Vault
   - Vault habilitado
   - Logging: DEBUG em JSON

4. **application-staging.yml** - Perfil de staging
   - Oracle RDS via variáveis de ambiente
   - RabbitMQ AWS MQ com SSL e maior concorrência
   - AWS S3 real (bucket staging)
   - SFTP via Vault
   - Vault habilitado
   - Logging: INFO em JSON

5. **application-prod.yml** - Perfil de produção
   - Oracle RDS via variáveis de ambiente (obrigatório)
   - RabbitMQ AWS MQ com SSL e máxima concorrência
   - AWS S3 real (bucket prod)
   - SFTP via Vault
   - Vault habilitado (obrigatório)
   - Logging: WARN/INFO em JSON
   - Pool otimizado (50 conexões)

6. **logback-spring.xml** - Configuração de logging estruturado
   - Local: Logs legíveis em console
   - Dev/Staging/Prod: Logs em JSON via Logstash encoder
   - Suporte para MDC (correlationId, fileOriginId, etc.)
   - Async logging para melhor performance

7. **README.md** - Documentação completa das configurações

### Classes Java de Configuração (common/src/main/java/com/controle/arquivos/common/config/)

1. **AppProperties.java** - Propriedades da aplicação
   - Configurações de streaming (chunk-size, buffer-size)
   - Configurações de retry (max-attempts, backoff-delay)
   - Configurações de processamento (max-reprocessing-attempts, timeout)
   - Configurações de scheduler (enabled, cron)

2. **AwsProperties.java** - Propriedades AWS
   - Região AWS
   - Configurações S3 (endpoint, bucket-name, path-style-access)
   - Credenciais AWS (access-key, secret-key)

3. **SftpProperties.java** - Propriedades SFTP
   - Timeouts (connection, session, channel)
   - Configurações de segurança (strict-host-key-checking, known-hosts-file)
   - Configuração padrão para desenvolvimento local

4. **VaultProperties.java** - Propriedades Vault
   - Habilitação do Vault
   - URI e token
   - Timeouts
   - Configurações KV engine

### Dependências Adicionadas

#### common/pom.xml
- `spring-boot-starter-amqp` - RabbitMQ
- `spring-boot-starter-actuator` - Health checks e métricas
- `software.amazon.awssdk:s3` - AWS S3 SDK v2
- `software.amazon.awssdk:sts` - AWS STS para credenciais
- `logstash-logback-encoder:7.4` - JSON logging
- `micrometer-registry-prometheus` - Métricas Prometheus

#### pom.xml (parent)
- AWS SDK BOM v2.21.42 para gerenciamento de versões

## Requisitos Atendidos

✅ **19.1** - Criar application.yml com perfis: local, dev, staging, prod
✅ **19.2** - Configurar datasource Oracle para cada perfil
✅ **19.3** - Configurar RabbitMQ connection factory
✅ **19.4** - Configurar AWS SDK v2 para S3 (LocalStack em local, AWS real em prod)
✅ **20.1** - Configurar logging estruturado JSON com Logback

## Características Principais

### Datasource Oracle
- Pool de conexões Hikari configurado
- Validação de conexões (SELECT 1 FROM DUAL)
- Leak detection habilitado (dev/staging/prod)
- Pool dimensionado por ambiente:
  - Local: 10 conexões
  - Dev: 20 conexões
  - Staging: 30 conexões
  - Prod: 50 conexões

### RabbitMQ
- Acknowledge manual para controle de processamento
- Retry configurado com backoff exponencial
- Publisher confirms habilitado
- SSL habilitado em ambientes não-locais
- Concorrência ajustada por ambiente:
  - Local: 1 consumer
  - Dev: 2-10 consumers
  - Staging: 5-20 consumers
  - Prod: 10-50 consumers

### AWS S3
- SDK v2 configurado
- LocalStack para desenvolvimento local
- Path-style access para LocalStack
- Credenciais via variáveis de ambiente

### Logging Estruturado
- JSON em ambientes não-locais
- MDC context para rastreabilidade
- Campos customizados: correlationId, fileOriginId, fileName, etc.
- Async logging para melhor performance
- Stack traces otimizados

### Health Checks
- Liveness e Readiness probes para Kubernetes
- Verificação de dependências (DB, RabbitMQ)
- Métricas Prometheus habilitadas

## Variáveis de Ambiente Necessárias

### Dev/Staging/Prod
```bash
# Database
DB_URL=jdbc:oracle:thin:@host:port:sid
DB_USERNAME=usuario
DB_PASSWORD=senha

# RabbitMQ
RABBITMQ_HOST=host
RABBITMQ_PORT=5671
RABBITMQ_USERNAME=usuario
RABBITMQ_PASSWORD=senha
RABBITMQ_VHOST=/

# AWS
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=key
AWS_SECRET_ACCESS_KEY=secret
S3_BUCKET_NAME=bucket-name

# Vault
VAULT_URI=https://vault.example.com
VAULT_TOKEN=token
```

## Como Usar

### Desenvolvimento Local
```bash
# Iniciar ambiente Docker
docker-compose up -d

# Executar aplicação
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Outros Ambientes
```bash
# Via variável de ambiente
export SPRING_PROFILES_ACTIVE=dev

# Via argumento JVM
java -jar app.jar --spring.profiles.active=dev

# Via Kubernetes ConfigMap
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
```

## Próximos Passos

1. Implementar classes de configuração Spring para:
   - AWS S3 Client
   - RabbitMQ Template e Listener
   - Vault Client (se necessário)

2. Criar testes de integração para validar configurações

3. Documentar variáveis de ambiente em Kubernetes ConfigMaps/Secrets

4. Configurar CI/CD para diferentes perfis
