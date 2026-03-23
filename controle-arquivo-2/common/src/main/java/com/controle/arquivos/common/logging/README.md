# Logging Estruturado

Este pacote contém componentes para logging estruturado em formato JSON com suporte a correlationId e contexto de processamento.

## Componentes

### CorrelationIdFilter

Filtro HTTP que adiciona automaticamente um `correlationId` ao MDC para todas as requisições.

- Se a requisição possui header `X-Correlation-Id`, ele é usado
- Caso contrário, um novo UUID é gerado
- O correlationId é adicionado ao response header para rastreamento pelo cliente
- O MDC é limpo automaticamente após a requisição

### MdcTaskDecorator

Decorator para propagar o contexto MDC para threads assíncronas.

- Captura o contexto MDC da thread principal
- Restaura o contexto na thread de execução
- Limpa o MDC após a execução

### LoggingUtils

Classe utilitária para gerenciar campos do MDC.

**Métodos principais:**

- `setCorrelationId(String)`: Define ou gera correlationId
- `getCorrelationId()`: Obtém correlationId atual
- `setFileOriginId(Long)`: Adiciona ID do arquivo ao contexto
- `setFileName(String)`: Adiciona nome do arquivo ao contexto
- `setClientId(Long)`: Adiciona ID do cliente ao contexto
- `setLayoutId(Long)`: Adiciona ID do layout ao contexto
- `setStep(String)`: Adiciona etapa de processamento ao contexto
- `setAcquirerId(Long)`: Adiciona ID do adquirente ao contexto
- `clearAll()`: Remove todos os campos do MDC
- `clearFileContext()`: Remove apenas campos relacionados a arquivo

## Configuração Logback

O arquivo `logback-spring.xml` está configurado com:

### Campos JSON

- `timestamp`: Data/hora do log
- `level`: Nível do log (INFO, WARN, ERROR)
- `logger`: Nome do logger
- `message`: Mensagem do log
- `thread`: Nome da thread
- `correlationId`: ID de correlação (do MDC)
- `fileOriginId`: ID do arquivo de origem (do MDC)
- `fileName`: Nome do arquivo (do MDC)
- `clientId`: ID do cliente (do MDC)
- `layoutId`: ID do layout (do MDC)
- `step`: Etapa de processamento (do MDC)
- `acquirerId`: ID do adquirente (do MDC)
- `application`: Nome da aplicação
- `profile`: Perfil Spring ativo

### Perfis

**local**: Logs em formato texto legível para desenvolvimento
**dev**: Logs JSON com nível DEBUG para aplicação
**staging**: Logs JSON com nível INFO para aplicação
**prod**: Logs JSON com nível INFO para aplicação, WARN para root

### Níveis de Log

Conforme requisitos 20.3, 20.4, 20.5:

- **INFO**: Operações bem-sucedidas
- **WARN**: Situações anômalas que não impedem processamento
- **ERROR**: Falhas com stack trace

## Uso

### Em Requisições HTTP

O `CorrelationIdFilter` adiciona automaticamente o correlationId ao MDC.

```java
@RestController
public class MyController {
    private static final Logger logger = LoggerFactory.getLogger(MyController.class);
    
    @GetMapping("/endpoint")
    public String endpoint() {
        // correlationId já está no MDC
        logger.info("Processando requisição");
        return "OK";
    }
}
```

### Em Processamento de Arquivos

Use `LoggingUtils` para adicionar contexto:

```java
public void processarArquivo(Long fileOriginId, String fileName) {
    // Adicionar contexto ao MDC
    LoggingUtils.setCorrelationId(UUID.randomUUID().toString());
    LoggingUtils.setFileOriginId(fileOriginId);
    LoggingUtils.setFileName(fileName);
    
    try {
        logger.info("Iniciando processamento");
        // ... processamento ...
        logger.info("Processamento concluído");
    } finally {
        // Limpar contexto
        LoggingUtils.clearFileContext();
    }
}
```

### Em Operações Assíncronas

Configure o executor com `MdcTaskDecorator`:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
```

## Exemplo de Log JSON

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.controle.arquivos.orchestrator.service.OrquestradorService",
  "message": "Arquivo registrado com sucesso",
  "thread": "scheduler-1",
  "correlationId": "abc-123-def-456",
  "fileOriginId": "12345",
  "fileName": "CIELO_20240115.txt",
  "acquirerId": "1",
  "application": "orchestrator",
  "profile": "prod"
}
```

## Validação de Requisitos

- **Requisito 20.1**: Logs em formato JSON com campos timestamp, level, logger, message, context ✅
- **Requisito 20.2**: MDC configurado para incluir correlationId em todos os logs ✅
- **Requisito 20.3**: Nível INFO para operações bem-sucedidas ✅
- **Requisito 20.4**: Nível ERROR para falhas ✅
- **Requisito 20.5**: Nível WARN para situações anômalas ✅
