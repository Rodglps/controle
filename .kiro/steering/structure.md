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
├── enums/                  # Enumerations (FileType, Status, Step, CriteriaType, ValueOrigin, FunctionType, etc.)
├── entity/                 # JPA entities (FileOrigin, Server, Layout, CustomerIdentification, etc.)
├── repository/             # JPA repositories for database access (centralized)
└── service/                # Shared services for identification
    ├── CriteriaComparator.java      # Criteria comparison logic
    ├── EncodingConverter.java       # Encoding detection and conversion
    ├── RuleValidator.java           # Rule validation logic
    ├── TransformationApplier.java   # Transformation functions
    └── extractor/                   # Value extraction strategies
        ├── IdentificationRule.java  # Common interface for rules
        ├── ValueExtractor.java      # Strategy interface
        ├── FilenameExtractor.java   # FILENAME extraction
        ├── HeaderTxtExtractor.java  # TXT HEADER extraction
        ├── HeaderCsvExtractor.java  # CSV HEADER extraction
        ├── XmlTagExtractor.java     # XML TAG extraction
        └── JsonKeyExtractor.java    # JSON KEY extraction
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
├── listener/               # RabbitMQ message listeners
│   └── FileTransferListener.java
├── service/                # Business logic services
│   ├── layout/             # Layout identification services
│   │   └── LayoutIdentificationService.java
│   ├── customer/           # Customer identification services
│   │   └── CustomerIdentificationService.java
│   └── upload/             # File upload services
│       ├── FileUploadService.java
│       ├── S3UploadService.java
│       └── SftpUploadService.java
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
