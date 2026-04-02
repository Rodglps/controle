# Project Structure

## Mono Repository Layout

```
controle-arquivos-edi/
├── pom.xml                 # Parent POM with dependency management
├── commons/                # Shared module
├── producer/               # Producer module
└── consumer/               # Consumer module
```

## Module Organization

### Commons Module
Shared code used by both producer and consumer:

```
commons/src/main/java/com/concil/edi/commons/
├── config/                 # Shared configurations (RabbitMQConfig)
├── dto/                    # Data Transfer Objects (FileTransferMessage)
├── enums/                  # Enumerations (FileType, Status, Step, etc.)
├── entity/                 # JPA entities (FileOrigin, Server, ServerPath, etc.)
└── repository/             # JPA repositories for database access (centralized)
```

### Producer Module
File collection and message publishing:

```
producer/src/main/java/com/concil/edi/producer/
├── config/                 # Producer-specific config (VaultConfig)
├── dto/                    # Producer DTOs (Credentials, FileMetadata, ServerConfiguration)
├── scheduler/              # Scheduled jobs (FileCollectionScheduler)
├── service/                # Business logic services
└── ProducerApplication.java
```

### Consumer Module
Message consumption and file transfer:

```
consumer/src/main/java/com/concil/edi/consumer/
├── config/                 # Consumer-specific config
├── service/                # Business logic services
└── ConsumerApplication.java
```

## Package Naming Convention

Base package: `com.concil.edi`
- Commons: `com.concil.edi.commons`
- Producer: `com.concil.edi.producer`
- Consumer: `com.concil.edi.consumer`

## Test Structure

Tests mirror the main source structure:

```
src/test/java/com/concil/edi/{module}/
```

Test naming conventions:
- Unit tests: `*Test.java`
- Property-based tests: `*PropertyTest.java`

## Configuration Files

- `application.yml`: Module-specific Spring Boot configuration
- `.env`: Environment variables (local development)
- `pom.xml`: Maven build configuration at root and module levels

## Key Architectural Patterns

1. **Layered architecture**: Scheduler/Listener (Controller) → Service → Repository (Spring Boot)
2. **Shared commons**: Entities, DTOs, configs, and repositories in commons module to centralize database integration
3. **Dependency direction**: Producer and Consumer depend on Commons, never the reverse
4. **Streaming pattern**: Services use InputStream/OutputStream for file operations, never loading full files into memory
5. **Configuration externalization**: All environment-specific values via environment variables or application.yml profiles
6. **Repository centralization**: All JPA repositories are in commons.repository package for centralized database access
