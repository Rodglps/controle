# Configurações Spring Boot Compartilhadas

Este diretório contém as configurações compartilhadas do Spring Boot para todos os módulos do sistema Controle de Arquivos.

## Arquivos de Configuração

### application.yml
Configuração base compartilhada por todos os perfis. Contém:
- Configurações JPA/Hibernate
- Configurações Jackson (JSON)
- Endpoints de gerenciamento (Actuator)
- Configurações de logging padrão
- Configurações de aplicação (streaming, retry, processamento)

### application-local.yml
Perfil para desenvolvimento local usando Docker Compose:
- **Oracle XE**: localhost:1521 (system/Oracle123)
- **RabbitMQ**: localhost:5672 (admin/admin123)
- **LocalStack S3**: localhost:4566 (simula AWS S3)
- **SFTP**: localhost:2222 (sftpuser/sftppass)
- **Vault**: Desabilitado (credenciais hardcoded para desenvolvimento)
- **Logging**: DEBUG com SQL visível

### application-dev.yml
Perfil para ambiente de desenvolvimento:
- **Oracle RDS**: Configurado via variáveis de ambiente
- **RabbitMQ AWS MQ**: SSL habilitado, configurado via variáveis
- **AWS S3**: Bucket real de desenvolvimento
- **SFTP**: Servidores externos via Vault
- **Vault**: Habilitado para gerenciamento de credenciais
- **Logging**: DEBUG em JSON estruturado

### application-staging.yml
Perfil para ambiente de staging:
- **Oracle RDS**: Configurado via variáveis de ambiente
- **RabbitMQ AWS MQ**: SSL habilitado, maior concorrência
- **AWS S3**: Bucket real de staging
- **SFTP**: Servidores externos via Vault
- **Vault**: Habilitado
- **Logging**: INFO em JSON estruturado

### application-prod.yml
Perfil para ambiente de produção:
- **Oracle RDS**: Configurado via variáveis de ambiente (obrigatório)
- **RabbitMQ AWS MQ**: SSL habilitado, máxima concorrência
- **AWS S3**: Bucket real de produção
- **SFTP**: Servidores externos via Vault
- **Vault**: Habilitado (obrigatório)
- **Logging**: WARN/INFO em JSON estruturado
- **Pool de conexões**: Otimizado para alta carga (50 conexões)

### logback-spring.xml
Configuração de logging estruturado:
- **Local**: Logs legíveis em console (formato texto)
- **Dev/Staging/Prod**: Logs em JSON estruturado via Logstash encoder
- **MDC Context**: Suporte para correlationId, fileOriginId, fileName, etc.
- **Async Logging**: Melhor performance em produção

## Variáveis de Ambiente Obrigatórias

### Todos os ambientes (exceto local)
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

### Opcionais
```bash
# Scheduler
SCHEDULER_ENABLED=true
SCHEDULER_CRON=0 */5 * * * *

# SFTP
SFTP_KNOWN_HOSTS_FILE=/app/config/known_hosts
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
# Definir perfil via variável de ambiente
export SPRING_PROFILES_ACTIVE=dev

# Ou via argumento JVM
java -jar app.jar --spring.profiles.active=dev
```

## Configurações de Streaming

O sistema usa processamento em streaming para suportar arquivos de qualquer tamanho:

- **Chunk Size**: 5MB (local/dev/staging), 10MB (prod)
- **Buffer Size**: 8KB
- **Timeout**: 5 minutos (local: 10 minutos)

## Configurações de Retry

- **Max Attempts**: 3 (local/dev/staging), 5 (prod)
- **Backoff Delay**: 1s (local), 2s (dev/staging), 3s (prod)
- **Max Reprocessing**: 5 tentativas por arquivo

## Health Checks

Endpoints disponíveis em `/actuator`:
- `/actuator/health` - Status geral do sistema
- `/actuator/health/liveness` - Liveness probe (Kubernetes)
- `/actuator/health/readiness` - Readiness probe (Kubernetes)
- `/actuator/metrics` - Métricas do sistema
- `/actuator/prometheus` - Métricas no formato Prometheus

## Logging Estruturado

### Campos MDC Disponíveis
- `correlationId`: ID de correlação para rastreamento
- `fileOriginId`: ID do arquivo na tabela file_origin
- `fileName`: Nome do arquivo sendo processado
- `clientId`: ID do cliente identificado
- `layoutId`: ID do layout identificado
- `step`: Etapa atual do processamento
- `acquirerId`: ID do adquirente

### Exemplo de Log JSON
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.controle.arquivos.processor.ProcessadorService",
  "thread": "pool-1-thread-1",
  "message": "Arquivo processado com sucesso",
  "application": "controle-arquivos",
  "profile": "prod",
  "correlationId": "abc-123-def",
  "fileOriginId": "12345",
  "fileName": "CIELO_20240115.txt",
  "clientId": "100",
  "layoutId": "50",
  "step": "PROCESSED"
}
```

## Requisitos Atendidos

Esta configuração atende aos seguintes requisitos:

- **19.1**: Perfis Spring Boot (local, dev, staging, prod)
- **19.2**: Datasource Oracle configurado para cada perfil
- **19.3**: RabbitMQ connection factory configurado
- **19.4**: AWS SDK v2 para S3 (LocalStack em local, AWS real em prod)
- **20.1**: Logging estruturado JSON com Logback

## Dependências Adicionadas

As seguintes dependências foram adicionadas ao `common/pom.xml`:

- `spring-boot-starter-amqp` - RabbitMQ
- `spring-boot-starter-actuator` - Health checks e métricas
- `software.amazon.awssdk:s3` - AWS S3 SDK v2
- `software.amazon.awssdk:sts` - AWS STS para credenciais
- `logstash-logback-encoder` - JSON logging
- `micrometer-registry-prometheus` - Métricas Prometheus
