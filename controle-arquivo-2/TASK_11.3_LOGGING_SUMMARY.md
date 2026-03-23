# Task 11.3 - Configurar Logging Estruturado

## Resumo

Configuração completa de logging estruturado em formato JSON com suporte a correlationId e contexto de processamento para rastreabilidade completa.

## Implementação

### 1. Componentes de Logging (common/src/main/java/.../common/logging/)

#### CorrelationIdFilter
- Filtro HTTP que adiciona automaticamente correlationId ao MDC
- Suporta header `X-Correlation-Id` ou gera novo UUID
- Adiciona correlationId ao response header
- Limpa MDC automaticamente após requisição

#### MdcTaskDecorator
- Propaga contexto MDC para threads assíncronas
- Captura contexto da thread principal
- Restaura contexto na thread de execução
- Limpa MDC após execução

#### LoggingUtils
- Classe utilitária para gerenciar campos do MDC
- Métodos para adicionar: correlationId, fileOriginId, fileName, clientId, layoutId, step, acquirerId
- Métodos para limpar contexto completo ou parcial

### 2. Configuração Async (orchestrator/src/main/java/.../orchestrator/config/)

#### AsyncConfig
- Configura executor assíncrono com MdcTaskDecorator
- Garante propagação de MDC em operações assíncronas
- ThreadPool configurado: 5 core, 10 max, 100 queue

### 3. Configuração Logback (common/src/main/resources/logback-spring.xml)

**Já existente e validado:**

#### Formato JSON
- Usa LogstashEncoder para formato JSON estruturado
- Campos: timestamp, level, logger, message, thread
- Campos MDC: correlationId, fileOriginId, fileName, clientId, layoutId, step, acquirerId
- Campos customizados: application, profile

#### Perfis
- **local**: Logs texto legível (desenvolvimento)
- **dev**: Logs JSON com DEBUG para aplicação
- **staging**: Logs JSON com INFO para aplicação
- **prod**: Logs JSON com INFO para aplicação, WARN para root

#### Níveis de Log
- **INFO**: Operações bem-sucedidas
- **WARN**: Situações anômalas que não impedem processamento
- **ERROR**: Falhas com stack trace

#### Appenders
- Console appender para local
- JSON console appender para outros perfis
- Async appender para melhor performance

### 4. Testes

#### Testes Unitários (common/src/test/java/.../common/logging/)
- **CorrelationIdFilterTest**: 6 testes
  - Geração de correlationId quando não fornecido
  - Uso de correlationId do header quando fornecido
  - Adição ao MDC durante execução
  - Limpeza do MDC após execução
  - Tratamento de headers vazios
  - Limpeza mesmo com exceção

- **LoggingUtilsTest**: 15 testes
  - Definição e obtenção de correlationId
  - Geração automática quando não fornecido
  - Definição de todos os campos MDC
  - Validação de valores null/vazios
  - Limpeza completa e parcial do contexto

- **MdcTaskDecoratorTest**: 6 testes
  - Propagação de contexto para thread filha
  - Limpeza após execução
  - Manutenção de MDC vazio quando não há contexto
  - Limpeza mesmo com exceção
  - Isolamento entre threads

#### Teste de Integração (orchestrator/src/test/java/.../orchestrator/logging/)
- **LoggingIntegrationTest**: 7 testes
  - Log INFO para operação bem-sucedida
  - Log WARN para situação anômala
  - Log ERROR para falha com stack trace
  - Inclusão de contexto de arquivo nos logs
  - Níveis apropriados para diferentes cenários
  - Limpeza de contexto após processamento

## Validação de Requisitos

### Requisito 20.1 ✅
**Formato JSON com campos estruturados**
- Logback configurado com LogstashEncoder
- Campos: timestamp, level, logger, message, context
- Implementado em: `common/src/main/resources/logback-spring.xml`

### Requisito 20.2 ✅
**MDC com correlationId**
- CorrelationIdFilter adiciona correlationId automaticamente
- MdcTaskDecorator propaga para threads assíncronas
- LoggingUtils fornece API para gerenciar MDC
- Implementado em: `common/src/main/java/.../common/logging/`

### Requisito 20.3 ✅
**Nível INFO para operações bem-sucedidas**
- Configurado em logback-spring.xml
- Validado em LoggingIntegrationTest
- Exemplos: "Ciclo de coleta iniciado", "Arquivo registrado com sucesso"

### Requisito 20.4 ✅
**Nível ERROR para falhas**
- Configurado em logback-spring.xml
- Stack trace incluído via throwableConverter
- Validado em LoggingIntegrationTest
- Exemplos: "Falha ao conectar ao SFTP"

### Requisito 20.5 ✅
**Nível WARN para situações anômalas**
- Configurado em logback-spring.xml
- Validado em LoggingIntegrationTest
- Exemplos: "Arquivo duplicado ignorado", "Configuração inválida"

## Arquivos Criados

### Código de Produção
1. `common/src/main/java/com/controle/arquivos/common/logging/CorrelationIdFilter.java`
2. `common/src/main/java/com/controle/arquivos/common/logging/MdcTaskDecorator.java`
3. `common/src/main/java/com/controle/arquivos/common/logging/LoggingUtils.java`
4. `orchestrator/src/main/java/com/controle/arquivos/orchestrator/config/AsyncConfig.java`
5. `common/src/main/java/com/controle/arquivos/common/logging/README.md`

### Testes
6. `common/src/test/java/com/controle/arquivos/common/logging/CorrelationIdFilterTest.java`
7. `common/src/test/java/com/controle/arquivos/common/logging/LoggingUtilsTest.java`
8. `common/src/test/java/com/controle/arquivos/common/logging/MdcTaskDecoratorTest.java`
9. `orchestrator/src/test/java/com/controle/arquivos/orchestrator/logging/LoggingIntegrationTest.java`

### Documentação
10. `TASK_11.3_LOGGING_SUMMARY.md` (este arquivo)

## Configuração Existente Validada

- `common/src/main/resources/logback-spring.xml`: Já configurado corretamente
- `common/pom.xml`: Dependência logstash-logback-encoder já presente

## Uso

### Em Requisições HTTP
```java
// CorrelationIdFilter adiciona automaticamente
@GetMapping("/endpoint")
public String endpoint() {
    logger.info("Processando requisição"); // correlationId já no MDC
    return "OK";
}
```

### Em Processamento de Arquivos
```java
public void processarArquivo(Long fileOriginId, String fileName) {
    LoggingUtils.setCorrelationId(UUID.randomUUID().toString());
    LoggingUtils.setFileOriginId(fileOriginId);
    LoggingUtils.setFileName(fileName);
    
    try {
        logger.info("Iniciando processamento");
        // ... processamento ...
        logger.info("Processamento concluído");
    } finally {
        LoggingUtils.clearFileContext();
    }
}
```

### Em Operações Assíncronas
```java
// AsyncConfig já configurado com MdcTaskDecorator
@Async
public void operacaoAssincrona() {
    // MDC propagado automaticamente
    logger.info("Executando operação assíncrona");
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

## Próximos Passos

A configuração de logging estruturado está completa e pronta para uso. Os próximos passos são:

1. **Task 11.4**: Escrever testes de integração para Orquestrador
2. **Checkpoint 12**: Validar Pod Orquestrador completo

## Observações

- Todos os componentes compilam sem erros
- Testes unitários cobrem casos principais e edge cases
- Teste de integração valida comportamento end-to-end
- Configuração Logback já existente foi validada e está correta
- Dependências já presentes no pom.xml
- Pronto para uso em produção
