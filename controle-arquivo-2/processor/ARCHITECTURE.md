# Processor Pod - Architecture

## Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Processor Pod                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           RabbitMQ Consumer                          │  │
│  │  - @RabbitListener (Manual ACK)                      │  │
│  │  - Message Deserialization                           │  │
│  │  - Message Validation                                │  │
│  │  - ACK/NACK Logic                                    │  │
│  │  - Correlation ID Management                         │  │
│  └────────────────┬─────────────────────────────────────┘  │
│                   │                                          │
│                   │ invokes                                  │
│                   ▼                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         ProcessadorService                           │  │
│  │  - processarArquivo(mensagem)                        │  │
│  │  - Download Streaming (Task 13.4)                    │  │
│  │  - Client Identification (Task 14.1)                 │  │
│  │  - Layout Identification (Task 14.2)                 │  │
│  │  - Upload Streaming (Task 14.3)                      │  │
│  └────────────────┬─────────────────────────────────────┘  │
│                   │                                          │
│                   │ uses                                     │
│                   ▼                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Common Services (from common module)         │  │
│  │  - ClienteIdentificationService                      │  │
│  │  - LayoutIdentificationService                       │  │
│  │  - StreamingTransferService                          │  │
│  │  - RastreabilidadeService                            │  │
│  │  - SFTPClient                                        │  │
│  │  - VaultClient                                       │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Message Flow

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│ Orchestrator│         │   RabbitMQ   │         │  Processor  │
│     Pod     │         │              │         │     Pod     │
└──────┬──────┘         └──────┬───────┘         └──────┬──────┘
       │                       │                        │
       │ 1. Publish Message    │                        │
       │──────────────────────>│                        │
       │                       │                        │
       │                       │ 2. Consume Message     │
       │                       │<───────────────────────│
       │                       │                        │
       │                       │ 3. Validate Message    │
       │                       │    - File exists?      │
       │                       │    - Not processed?    │
       │                       │    - Active?           │
       │                       │                        │
       │                       │ 4a. Valid → Process    │
       │                       │     - Download         │
       │                       │     - Identify         │
       │                       │     - Upload           │
       │                       │                        │
       │                       │ 5a. Success → ACK      │
       │                       │<───────────────────────│
       │                       │                        │
       │                       │ 4b. Invalid → Discard  │
       │                       │                        │
       │                       │ 5b. Discard → ACK      │
       │                       │<───────────────────────│
       │                       │                        │
       │                       │ 4c. Error → Retry      │
       │                       │                        │
       │                       │ 5c. Error → NACK       │
       │                       │<───────────────────────│
       │                       │    (requeue=true)      │
       │                       │                        │
```

## Validation Logic

```
Message Received
    ↓
idFileOrigin != null?
    ├─ No → INVALID (ACK)
    └─ Yes
        ↓
File exists in file_origin?
    ├─ No → INVALID (ACK)
    └─ Yes
        ↓
File is active (flg_active=true)?
    ├─ No → INVALID (ACK)
    └─ Yes
        ↓
File already processed?
    ├─ Yes → INVALID (ACK)
    └─ No → VALID
        ↓
    Process File
        ├─ Success → ACK
        └─ Error → NACK (requeue)
```

## ACK/NACK Decision Tree

```
Processing Result
    ├─ Success
    │   └─> ACK (remove from queue)
    │
    ├─ Validation Failed
    │   └─> ACK (discard, don't retry)
    │
    └─ Processing Error
        └─> NACK with requeue=true
            ├─ Attempt < 5 → Retry
            └─ Attempt = 5 → Dead Letter Queue
```

## Configuration Hierarchy

```
application.yml (base)
    ├─ application-local.yml (local development)
    ├─ application-dev.yml (development environment)
    ├─ application-staging.yml (staging environment)
    └─ application-prod.yml (production environment)
```

## Dependencies

### Internal
- common module (entities, repositories, services)

### External
- Spring Boot Starter Web
- Spring Boot Starter AMQP (RabbitMQ)
- Spring Boot Starter Actuator
- Spring Cloud Vault
- AWS SDK v2 (S3)
- JSch (SFTP)
- Lombok
- Jackson (JSON)

### Testing
- Spring Boot Starter Test
- Spring Rabbit Test
- Mockito
- JUnit 5
- jqwik (Property-Based Testing)
- Testcontainers

## Current Implementation Status

✅ **Task 13.1**: RabbitMQConsumer (COMPLETED)
- Message consumption with @RabbitListener
- Message validation
- ACK/NACK manual implementation
- ProcessadorService stub

⏳ **Task 13.4**: Download Streaming (PENDING)
⏳ **Task 14.1**: Client Identification (PENDING)
⏳ **Task 14.2**: Layout Identification (PENDING)
⏳ **Task 14.3**: Upload Streaming (PENDING)
