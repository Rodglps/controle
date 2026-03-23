# RabbitMQ Consumer Tests

Este diretório contém testes para o componente `RabbitMQConsumer`, responsável por consumir mensagens do RabbitMQ e orquestrar o processamento de arquivos.

## Testes Unitários

**Arquivo:** `RabbitMQConsumerTest.java`

Testes unitários que verificam cenários específicos:
- Consumo de mensagem válida com ACK
- Validação de mensagem inválida (arquivo não existe, inativo, já processado)
- ACK para mensagens inválidas (descarte)
- NACK para falhas de processamento

**Valida: Requisitos 6.3, 6.4, 6.5, 6.6**

## Testes de Propriedade

**Arquivo:** `RabbitMQConsumerPropertyTest.java`

Testes baseados em propriedades usando jqwik que verificam comportamentos universais através de múltiplas entradas geradas.

### Propriedade 12: Confirmação de Mensagens

**Valida: Requisitos 6.5, 6.6**

Esta propriedade garante que o sistema implementa corretamente o protocolo ACK/NACK do RabbitMQ:

#### Teste 1: Mensagens Válidas Processadas com Sucesso Recebem ACK

```java
@Property(tries = 100)
void mensagensValidasProcessadasComSucessoRecebemAck(...)
```

**Comportamento esperado:**
- Para qualquer mensagem válida (arquivo existe, ativo, não processado)
- Quando o processamento é concluído com sucesso
- Então o sistema deve enviar `basicAck()` e nunca `basicNack()`

**Geradores:**
- `mensagemValida()`: Gera mensagens com IDs válidos, nomes de arquivo e correlation IDs
- `deliveryTag()`: Gera tags de entrega aleatórias (1 a 1.000.000)

#### Teste 2: Mensagens Inválidas Recebem ACK para Descartar

```java
@Property(tries = 100)
void mensagensInvalidasRecebemAckParaDescartar(...)
```

**Comportamento esperado:**
- Para qualquer mensagem inválida (arquivo não existe, inativo, já processado, ou ID nulo)
- O sistema deve enviar `basicAck()` para descartar a mensagem
- O sistema nunca deve enviar `basicNack()` (não reprocessar mensagens inválidas)
- O processamento nunca deve ser invocado

**Cenários de invalidez testados:**
- `ARQUIVO_NAO_EXISTE`: ID não encontrado no banco de dados
- `ARQUIVO_INATIVO`: Arquivo com `flg_active = false`
- `ARQUIVO_JA_PROCESSADO`: Arquivo com status `PROCESSED` e `CONCLUIDO`
- `ID_FILE_ORIGIN_NULO`: Mensagem com `idFileOrigin = null`

**Geradores:**
- `mensagemInvalida()`: Gera cenários de mensagens inválidas com diferentes tipos de problemas

#### Teste 3: Mensagens com Falha de Processamento Recebem NACK

```java
@Property(tries = 100)
void mensagensComFalhaDeProcessamentoRecebemNack(...)
```

**Comportamento esperado:**
- Para qualquer mensagem válida que falha durante o processamento
- O sistema deve enviar `basicNack(deliveryTag, false, true)` com `requeue=true`
- O sistema nunca deve enviar `basicAck()`
- Isso permite que a mensagem seja reprocessada automaticamente

**Geradores:**
- `erroProcessamento()`: Gera diferentes tipos de exceções que podem ocorrer durante o processamento:
  - Erros de conexão SFTP
  - Erros de download
  - Erros de upload
  - Estados inválidos
  - Erros genéricos

## Executando os Testes

### Testes Unitários

```bash
mvn test -Dtest=RabbitMQConsumerTest -pl processor
```

### Testes de Propriedade

```bash
mvn test -Dtest=RabbitMQConsumerPropertyTest -pl processor
```

### Todos os Testes

```bash
mvn test -pl processor
```

## Configuração do jqwik

Os testes de propriedade usam jqwik 1.8.2, configurado no `pom.xml`:

```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <scope>test</scope>
</dependency>
```

Cada propriedade executa 100 iterações por padrão (`@Property(tries = 100)`), gerando diferentes combinações de entradas para verificar que o comportamento é consistente.

## Cobertura

Estes testes cobrem:
- ✅ Requisito 6.1: Conexão à fila RabbitMQ
- ✅ Requisito 6.2: Extração de dados da mensagem
- ✅ Requisito 6.3: Validação de existência do arquivo
- ✅ Requisito 6.4: Descarte de mensagens inválidas
- ✅ Requisito 6.5: Confirmação (ACK) de mensagens bem-sucedidas
- ✅ Requisito 6.6: Rejeição (NACK) de mensagens com falha

## Referências

- [jqwik Documentation](https://jqwik.net/)
- [Property-Based Testing Guide](https://jqwik.net/docs/current/user-guide.html)
- Design Document: `.kiro/specs/controle-de-arquivos/design.md` - Propriedade 12
- Requirements Document: `.kiro/specs/controle-de-arquivos/requirements.md` - Requisitos 6.5, 6.6
