# Configuração de Logging Estruturado - Processador

## Visão Geral

O Processador utiliza logging estruturado em formato JSON para facilitar análise e observabilidade em ambientes de produção. A configuração é feita através do arquivo `logback-spring.xml`.

## Formato de Logs

### Perfil Local
- **Formato**: Console legível por humanos
- **Padrão**: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- **Nível**: DEBUG para `com.controle.arquivos`, INFO para outros

### Perfis Dev, Staging e Prod
- **Formato**: JSON estruturado via Logstash Encoder
- **Campos incluídos**:
  - `timestamp`: Data e hora do log
  - `level`: Nível do log (INFO, WARN, ERROR)
  - `logger`: Nome do logger
  - `message`: Mensagem do log
  - `thread`: Thread que gerou o log
  - `application`: Nome da aplicação (controle-arquivos-processor)
  - `profile`: Perfil ativo (dev, staging, prod)

### Campos MDC (Mapped Diagnostic Context)

Os seguintes campos são incluídos automaticamente quando disponíveis no MDC:

- `correlationId`: ID de correlação para rastreamento de requisições
- `fileOriginId`: ID do arquivo de origem
- `fileName`: Nome do arquivo sendo processado
- `clientId`: ID do cliente identificado
- `layoutId`: ID do layout identificado
- `step`: Etapa de processamento (COLETA, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED)
- `acquirerId`: ID do adquirente

## Níveis de Log

### INFO
Usado para operações bem-sucedidas:
```java
log.info("Arquivo processado com sucesso: {}", fileName);
```

### WARN
Usado para situações anômalas que não impedem o processamento:
```java
log.warn("Arquivo com tamanho inesperado: {} bytes", fileSize);
```

### ERROR
Usado para falhas com stack trace completo:
```java
log.error("Falha ao processar arquivo: {}", fileName, exception);
```

## Uso do MDC

### Configurar Contexto
```java
import com.controle.arquivos.common.logging.LoggingUtils;

// Configurar correlationId
LoggingUtils.setCorrelationId("abc-123-def");

// Configurar informações do arquivo
LoggingUtils.setFileOriginId(12345L);
LoggingUtils.setFileName("CIELO_20240115.txt");
LoggingUtils.setClientId(999L);
LoggingUtils.setLayoutId(777L);
LoggingUtils.setStep("PROCESSING");
LoggingUtils.setAcquirerId(555L);

// Todos os logs subsequentes incluirão esses campos automaticamente
log.info("Processando arquivo");
```

### Limpar Contexto
```java
// Limpar todo o contexto
LoggingUtils.clearAll();

// Ou limpar apenas campos de arquivo (mantém correlationId)
LoggingUtils.clearFileContext();
```

## Logging Estruturado de Erros

Para erros complexos, use `StructuredErrorLogger`:

```java
import com.controle.arquivos.common.logging.StructuredErrorLogger;
import com.controle.arquivos.common.logging.StructuredErrorLogger.ErrorContext;

ErrorContext context = new ErrorContext()
    .fileName("CIELO_20240115.txt")
    .fileOriginId(12345L)
    .acquirerId(555L)
    .step("PROCESSING")
    .clientId(999L)
    .layoutId(777L)
    .correlationId("abc-123-def");

StructuredErrorLogger.logError(
    log,
    "Falha ao fazer upload do arquivo",
    exception,
    context
);
```

## Exemplo de Log JSON

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.controle.arquivos.processor.service.ProcessadorService",
  "message": "Arquivo processado com sucesso",
  "thread": "pool-1-thread-1",
  "application": "controle-arquivos-processor",
  "profile": "prod",
  "correlationId": "abc-123-def",
  "fileOriginId": "12345",
  "fileName": "CIELO_20240115.txt",
  "clientId": "999",
  "layoutId": "777",
  "step": "PROCESSING",
  "acquirerId": "555"
}
```

## Configuração de Níveis por Perfil

### Local
- `com.controle.arquivos`: DEBUG
- `org.springframework`: INFO
- `org.hibernate.SQL`: DEBUG

### Dev
- `com.controle.arquivos`: DEBUG
- `org.springframework`: WARN
- `org.hibernate`: WARN

### Staging
- `com.controle.arquivos`: INFO
- `org.springframework`: WARN
- `org.hibernate`: WARN

### Prod
- Root: WARN
- `com.controle.arquivos`: INFO
- `org.springframework`: WARN
- `org.hibernate`: WARN

## Performance

- **Async Appender**: Logs são processados de forma assíncrona para não bloquear threads de processamento
- **Queue Size**: 512 mensagens
- **Discarding Threshold**: 0 (não descarta mensagens mesmo sob alta carga)

## Segurança

- Credenciais são automaticamente sanitizadas nos logs
- Padrões como `password`, `senha`, `secret`, `token`, `credential`, `key` são mascarados com `***`
- Stack traces são limitados a 8192 caracteres para evitar logs excessivamente grandes

## Requisitos Validados

- **20.1**: Logs em formato JSON com campos timestamp, level, logger, message, context
- **20.2**: correlationId incluído em todos os logs relacionados ao processamento
- **20.3**: Nível INFO para operações bem-sucedidas
- **20.4**: Nível ERROR para falhas com stack trace
- **20.5**: Nível WARN para situações anômalas
