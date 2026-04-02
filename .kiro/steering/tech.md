# Technology Stack

## Core Technologies

- **Java 21**: Language version with modern features
- **Spring Boot 3.4.0**: Application framework
- **Maven**: Build tool (mono repository structure)
- **Oracle Database**: Primary data store
- **RabbitMQ 3.12+**: Message broker using Quorum Queues
- **AWS S3**: Object storage destination
- **SFTP (Spring Integration)**: File transfer protocol client with connection pooling

## Key Dependencies

- **Spring Boot Starter Data JPA**: Database access and ORM
- **Spring Boot Starter AMQP**: RabbitMQ integration
- **Spring Boot Starter Actuator**: Health checks and monitoring
- **Spring Retry 2.0.5**: Retry logic with @Retryable support
- **Oracle JDBC Driver (ojdbc11) 23.3.0.23.09**: Database connectivity
- **Spring Integration SFTP 6.4.0**: SFTP client with Spring abstractions (uses Apache SSHD internally)
- **AWS SDK S3 2.20.26**: S3 client (consumer module only)
- **Lombok**: Boilerplate reduction (optional)
- **jqwik 1.8.2**: Property-based testing framework

## Build Commands

### Usando Makefile (Recomendado)

```bash
# Build sem testes (rápido)
make build

# Build completo com testes unitários
make test

# Rebuild completo: build + testes + restart docker-compose
make rebuild

# Rebuild completo incluindo reconstrução das imagens Docker
make full-rebuild

# Limpar arquivos de build
make clean
```

### Usando Maven Diretamente

```bash
# Full build with tests
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build specific module
cd producer  # or consumer, commons
mvn clean package

# Run Spring Boot application
mvn spring-boot:run
```

## Test Commands

### Usando Makefile (Recomendado)

```bash
# Executar testes unitários
make test

# Executar testes E2E (requer docker-compose rodando)
make e2e
```

### Usando Maven Diretamente

```bash
# Run all tests
mvn test

# Run tests for specific module
cd producer
mvn test

# Run only unit tests
mvn test -Dtest=*Test

# Run only property-based tests
mvn test -Dtest=*PropertyTest

# Run E2E tests
mvn test -Dtest=FileTransferE2ETest -pl commons
```

## Local Development

### Usando Makefile (Recomendado)

```bash
# Iniciar todo o sistema
make up

# Parar sistema
make down

# Parar e remover dados
make down-volumes

# Reiniciar sistema
make restart

# Ver status
make status

# Ver logs
make logs
make logs-producer
make logs-consumer
make logs-infra
```

### Usando Docker Compose Diretamente

Docker Compose provides local infrastructure:
- Oracle XE (or lite version)
- RabbitMQ 3.12+
- LocalStack (S3 simulation with CLI support)
- SFTP server (lite version)
- SFTP client for manual testing
- Bridge network mode for integrated communication

```bash
# Start infrastructure
docker-compose up -d

# Stop infrastructure
docker-compose down
```

## Utilitários do Makefile

```bash
# Acesso aos containers
make shell-producer    # Shell no Producer
make shell-consumer    # Shell no Consumer
make shell-oracle      # SQL*Plus no Oracle

# Monitoramento
make rabbitmq-queues   # Lista filas do RabbitMQ
make s3-list           # Lista objetos no S3
```

## Configuration

Externalized via environment variables and application.yml in each module.

Key environment variables:
- **Producer**: DB_URL, DB_USERNAME, DB_PASSWORD, RABBITMQ_HOST, SFTP_CIELO_VAULT (JSON format)
- **Consumer**: DB_URL, RABBITMQ_HOST, AWS_ENDPOINT, AWS_REGION

## Project Encoding

UTF-8 encoding for all source files (project.build.sourceEncoding=UTF-8).
