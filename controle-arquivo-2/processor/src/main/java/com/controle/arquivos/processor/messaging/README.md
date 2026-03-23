# RabbitMQ Consumer - Processador

## Visão Geral

O `RabbitMQConsumer` é responsável por consumir mensagens da fila RabbitMQ e orquestrar o processamento de arquivos no Pod Processador.

## Funcionalidades Implementadas

### 1. Consumo de Mensagens (@RabbitListener)

- Consome mensagens da fila configurada em `${rabbitmq.queue.processamento}`
- Utiliza ACK manual (`ackMode = "MANUAL"`) para controle fino de confirmação
- Deserializa `MensagemProcessamento` usando Jackson ObjectMapper

### 2. Validação de Mensagens

Antes de processar, valida:

- **Campos obrigatórios**: `idFileOrigin` não pode ser nulo
- **Existência do arquivo**: Verifica se existe em `file_origin`
- **Status ativo**: Verifica se `flg_active = true`
- **Não processado**: Verifica se não existe registro com status `CONCLUIDO` na etapa `PROCESSED`

Se a validação falhar, a mensagem é descartada (ACK) e um alerta é registrado.

### 3. Invocação do ProcessadorService

Para mensagens válidas:
- Invoca `ProcessadorService.processarArquivo(mensagem)`
- O ProcessadorService será implementado nas tarefas 13.4+

### 4. ACK/NACK Manual

**ACK (Confirmação)**:
- Enviado após processamento bem-sucedido
- Enviado para mensagens inválidas (descarte, não reprocessar)

**NACK (Rejeição)**:
- Enviado em caso de falha durante processamento
- `requeue=true` permite reprocessamento automático
- Limitado a 5 tentativas (configurado no `application.yml`)

### 5. Correlation ID para Rastreamento

- Extrai `correlationId` da mensagem
- Configura no MDC via `LoggingUtils.setCorrelationId()`
- Todos os logs relacionados incluem o correlation ID
- Limpa o MDC no bloco `finally`

## Configuração

### application.yml

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 1
        retry:
          enabled: true
          initial-interval: 3000
          max-attempts: 5
          multiplier: 2.0
          max-interval: 30000

rabbitmq:
  queue:
    processamento: file-processing-queue
```

### Parâmetros de Retry

- **initial-interval**: 3 segundos
- **max-attempts**: 5 tentativas
- **multiplier**: 2.0 (backoff exponencial)
- **max-interval**: 30 segundos

## Fluxo de Processamento

```
1. Mensagem recebida do RabbitMQ
   ↓
2. Deserializar MensagemProcessamento
   ↓
3. Configurar correlation ID no MDC
   ↓
4. Validar mensagem
   ├─ Inválida → ACK (descartar)
   └─ Válida → Continuar
   ↓
5. Invocar ProcessadorService.processarArquivo()
   ├─ Sucesso → ACK
   └─ Falha → NACK (requeue=true)
   ↓
6. Limpar correlation ID do MDC
```

## Tratamento de Erros

### Erros de Validação
- Mensagem descartada (ACK)
- Log de alerta registrado
- Não reprocessa

### Erros de Processamento
- Mensagem rejeitada (NACK com requeue=true)
- Log de erro com stack trace
- Reprocessamento automático até 5 tentativas

### Erros de Deserialização
- Mensagem rejeitada (NACK)
- Log de erro registrado
- Reprocessamento automático

## Testes

### Testes Unitários (RabbitMQConsumerTest)

- ✅ Consumo de mensagem válida e envio de ACK
- ✅ Descarte de mensagem quando arquivo não existe
- ✅ Descarte de mensagem quando arquivo já processado
- ✅ Descarte de mensagem quando arquivo inativo
- ✅ Envio de NACK quando processamento falhar
- ✅ Descarte de mensagem com idFileOrigin nulo

## Requisitos Validados

- **Requisito 6.1**: Conexão à fila RabbitMQ
- **Requisito 6.2**: Extração de campos da mensagem
- **Requisito 6.3**: Validação de existência do arquivo
- **Requisito 6.4**: Descarte de mensagens inválidas
- **Requisito 6.5**: ACK após sucesso
- **Requisito 6.6**: NACK após falha

## Próximos Passos

O `ProcessadorService` será implementado nas tarefas subsequentes:
- **Task 13.4**: Download via streaming
- **Task 14.1**: Identificação de cliente
- **Task 14.2**: Identificação de layout
- **Task 14.3**: Upload para destino
