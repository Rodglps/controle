# Product Overview

Sistema de transferência automatizada de arquivos EDI (Electronic Data Interchange) para conciliação de cartão de crédito.

## Purpose

Coleta arquivos EDI de adquirentes (Cielo, Rede, PagSeguro) via SFTP e APIs, identifica arquivos disponíveis e os encaminha para destinos configurados (S3 ou SFTP interno), mantendo rastreabilidade completa do processo.

## Architecture

Two-container architecture with message-driven communication:

- **Producer**: Scheduled job that connects to external SFTP servers, lists files, registers them in Oracle database, and publishes messages to RabbitMQ
- **Consumer**: Message consumer that downloads files via streaming from SFTP and uploads to configured destinations (S3 or internal SFTP) using streaming to avoid memory overload
- **Commons**: Shared module containing JPA entities, DTOs, enums, and RabbitMQ configuration

## Key Business Rules

1. **Streaming-only transfers**: Never load entire files into memory. Use InputStream chaining from SFTP directly to destination (S3 multipart upload or SFTP output stream)
2. **Full traceability**: Every step/status change must update the file_origin table
3. **Secure credentials**: Server credentials (SFTP, S3) must be read from Vault using cod_vault + des_vault_secret from server table. Never hardcode credentials
4. **Current implementation**: Initially using environment variables in JSON format (e.g., SFTP_CIELO_VAULT = {"user":"cielo", "key":"admin-1-2-3"})

## Database Tables

Core Oracle tables for configuration and tracking:
- **server**: Origin/destination servers (S3, SFTP, NFS, Blob Storage, Object Storage)
- **server_paths**: Directories within servers
- **server_paths_in_out**: Mapping of origin to destination paths
- **file_origin**: State table tracking each file's transfer process (step/status)

## Current Scope

Initial implementation focuses on the "COLETA" (collection) step with all possible statuses: EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO.
