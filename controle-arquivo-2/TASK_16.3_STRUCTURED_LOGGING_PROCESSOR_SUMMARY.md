# Task 16.3 - Configurar Logging Estruturado do Processador

## Resumo

Configuração completa de logging estruturado em formato JSON para o Pod Processador, incluindo MDC (Mapped Diagnostic Context) para rastreabilidade com correlationId e outros campos de contexto.

## Implementação

### 1. Arquivo de Configuração Logback

**Arquivo**: `processor/src/main/resources/logback-spring.xml`

Configuração criada com:

#### Appenders Configurados
- **CONSOLE**: Appender para perfil local com formato legível por humanos
- **JSON_CONSOLE**: Appender para perfis dev/staging/prod com formato JSON via Logstash Encoder
- **ASYNC_JSON_CONSOLE**: Wrapper assíncrono para melhor performance

#### Campos JSON Incluídos
- `timestamp`: Data e hora do log
- `level`: Nível do log (INFO, WARN, ERROR)
- `logger`: Nome do logger
- `message`: Mensagem do log
- `thread`: Thread que gerou o log
- `application`: Nome da aplicação (controle-arquivos-processor)
- `profile`: Perfil ativo (dev, staging, prod)

#### Campos MDC Incluídos
- `correlationId`: ID de correlação para rastreamento
- `fileOriginId`: ID do arquivo de origem
- `fileName`: Nome do arquivo sendo processado
- `clientId`: ID do cliente identificado
- `layoutId`: ID do layout identificado
- `step`: Etapa de processamento
- `acquirerId`: ID do adquirente

#### Configuração por Perfil

**Local**:
- Formato: Console legível
- Nível: DEBUG para `com.controle.arquivos`, INFO para outros
- SQL logging habilitado

**Dev**:
- Formato: JSON estruturado
- Nível: DEBUG para `com.controle.arquivos`, WARN para frameworks
- Async appender para performance

**Staging**:
- Formato: JSON estruturado
- Nível: INFO para `com.controle.arquivos`, WARN para frameworks
- Async appender para performance

**Prod**:
- Formato: JSON estruturado
- Nível: INFO para `com.controle.arquivos`, WARN para root e frameworks
- Async appender para performance
- Configuração mais restritiva para reduzir volume de logs

### 2. Atualização do application.yml

**Arquivo**: `processor/src/main/resources/application.yml`

Removida a configuração básica de logging pattern, pois o Logback agora gerencia completamente a configuração de logs.

### 3. Testes Criados

#### StructuredLoggingTest
**Arquivo**: `processor/src/test/java/com/controle/arquivos/processor/logging/StructuredLoggingTest.java`

Testes para validar:
- Inclusão de correlationId no MDC
- Inclusão de fileOriginId no MDC
- Inclusão de fileName no MDC
- Inclusão de clientId no MDC
- Inclusão de layoutId no MDC
- Inclusão de step no MDC
- Inclusão de acquirerId no MDC
- Inclusão de todos os campos simultaneamente
- Registro de logs de nível INFO (operações bem-sucedidas)
- Registro de logs de nível ERROR (falhas com stack trace)
- Registro de logs de nível WARN (situações anômalas)
- Limpeza completa do contexto MDC
- Limpeza parcial do contexto (apenas campos de arquivo)

#### LogbackConfigurationTest
**Arquivo**: `processor/src/test/java/com/controle/arquivos/processor/logging/LogbackConfigurationTest.java`

Testes para validar:
- Carregamento correto da configuração do Logback
- Existência do appender CONSOLE para perfil local
- Configuração do logger da aplicação

### 4. Documentação

**Arquivo**: `processor/src/main/resources/README.md`

Documentação completa incluindo:
- Visão geral do logging estruturado
- Formato de logs por perfil
- Campos MDC disponíveis
- Níveis de log e quando usar cada um
- Exemplos de uso do MDC
- Exemplo de uso do StructuredErrorLogger
- Exemplo de log JSON gerado
- Configuração de níveis por perfil
- Considerações de performance
- Considerações de segurança

## Características Implementadas

### 1. Formato JSON Estruturado
✅ Logs em formato JSON com campos: timestamp, level, logger, message, context
✅ Campos customizados: application, profile
✅ Integração com Logstash Encoder

### 2. MDC para Rastreabilidade
✅ correlationId incluído em todos os logs relacionados ao processamento
✅ Campos de contexto: fileOriginId, fileName, clientId, layoutId, step, acquirerId
✅ Utilitários LoggingUtils para gerenciar MDC

### 3. Níveis de Log Apropriados
✅ INFO para operações bem-sucedidas
✅ ERROR para falhas com stack trace completo
✅ WARN para situações anômalas que não impedem processamento

### 4. Performance
✅ Async appender para não bloquear threads de processamento
✅ Queue size de 512 mensagens
✅ Discarding threshold 0 (não descarta mensagens)

### 5. Segurança
✅ Credenciais automaticamente sanitizadas (via StructuredErrorLogger)
✅ Stack traces limitados a 8192 caracteres
✅ Padrões sensíveis mascarados com ***

## Integração com Código Existente

O ProcessadorService já utiliza:
- `StructuredErrorLogger` para logging de erros com contexto completo
- `LoggingUtils` para gerenciar campos do MDC
- Logs estruturados em todas as etapas de processamento

Exemplo do código existente:
```java
// Registrar erro estruturado com contexto completo
StructuredErrorLogger.ErrorContext errorContext = new StructuredErrorLogger.ErrorContext()
    .fileName(mensagem.getNomeArquivo())
    .fileOriginId(mensagem.getIdFileOrigin())
    .correlationId(mensagem.getCorrelationId())
    .acquirerId(fileOrigin.getAcquirerId())
    .step(etapaAtual);

StructuredErrorLogger.logError(
    log,
    String.format("Erro ao processar arquivo: %s", mensagem.getNomeArquivo()),
    e,
    errorContext
);
```

## Exemplo de Log JSON Gerado

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

## Requisitos Validados

✅ **20.1**: Logs em formato JSON com campos timestamp, level, logger, message, context
✅ **20.2**: correlationId incluído em todos os logs relacionados ao processamento
✅ **20.3**: Nível INFO para operações bem-sucedidas
✅ **20.4**: Nível ERROR para falhas com stack trace
✅ **20.5**: Nível WARN para situações anômalas

## Dependências

A dependência `logstash-logback-encoder` já está presente no módulo `common`, que é herdado pelo `processor`:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

## Arquivos Criados/Modificados

### Criados
1. `processor/src/main/resources/logback-spring.xml` - Configuração do Logback
2. `processor/src/test/java/com/controle/arquivos/processor/logging/StructuredLoggingTest.java` - Testes de MDC
3. `processor/src/test/java/com/controle/arquivos/processor/logging/LogbackConfigurationTest.java` - Testes de configuração
4. `processor/src/main/resources/README.md` - Documentação completa

### Modificados
1. `processor/src/main/resources/application.yml` - Removida configuração básica de logging

## Próximos Passos

A configuração de logging estruturado está completa e pronta para uso. O Processador agora:
- Gera logs em formato JSON em ambientes não-locais
- Inclui correlationId e outros campos de contexto automaticamente
- Usa níveis de log apropriados (INFO, WARN, ERROR)
- Sanitiza credenciais automaticamente
- Processa logs de forma assíncrona para melhor performance

Para testar localmente:
1. Execute o Processador com perfil `local` para ver logs legíveis
2. Execute com perfil `dev`, `staging` ou `prod` para ver logs JSON
3. Verifique que os campos MDC são incluídos nos logs durante o processamento de arquivos
