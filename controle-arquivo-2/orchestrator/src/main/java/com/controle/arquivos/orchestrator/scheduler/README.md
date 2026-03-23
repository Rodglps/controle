# Scheduler de Coleta de Arquivos

## Visão Geral

O `ColetaArquivosScheduler` é responsável pela execução periódica do ciclo de coleta de arquivos. Ele invoca o `OrquestradorService.executarCicloColeta()` em intervalos configuráveis usando Spring Scheduling.

## Componentes

### ColetaArquivosScheduler

Classe principal do scheduler que:
- Executa o ciclo de coleta periodicamente usando `@Scheduled`
- Registra logs estruturados de início e fim de cada ciclo
- Calcula e registra a duração de cada execução
- Trata exceções sem propagar para não interromper execuções futuras

### SchedulingConfig

Configuração Spring que habilita o scheduling no módulo orquestrador através da anotação `@EnableScheduling`.

## Configuração

A expressão cron é configurável via propriedade no `application.yml`:

```yaml
app:
  scheduler:
    coleta:
      cron: "0 */5 * * * *"  # A cada 5 minutos (padrão)
```

### Formato da Expressão Cron

O formato segue o padrão Spring Cron:
```
segundo minuto hora dia mês dia-da-semana
```

Exemplos:
- `0 */5 * * * *` - A cada 5 minutos
- `0 0 * * * *` - A cada hora
- `0 0 */2 * * *` - A cada 2 horas
- `0 0 0 * * *` - Todo dia à meia-noite
- `0 0 9-17 * * MON-FRI` - De hora em hora, das 9h às 17h, de segunda a sexta

## Logs Estruturados

O scheduler registra logs estruturados em três momentos:

### 1. Início do Ciclo
```
INFO: Iniciando ciclo de coleta agendado: timestamp=2024-01-15T10:00:00.000Z
```

### 2. Conclusão com Sucesso
```
INFO: Ciclo de coleta concluído com sucesso: timestamp=2024-01-15T10:05:23.456Z, duracao_ms=323456
```

### 3. Falha no Ciclo
```
ERROR: Ciclo de coleta falhou: timestamp=2024-01-15T10:05:23.456Z, duracao_ms=123456, erro=Conexão SFTP falhou
```

## Tratamento de Erros

O scheduler implementa tratamento robusto de erros:

1. **Captura de Exceções**: Todas as exceções são capturadas e logadas
2. **Não Propagação**: Exceções não são propagadas para não interromper o agendamento
3. **Próxima Execução**: Mesmo após falha, a próxima execução agendada ocorre normalmente
4. **Log Detalhado**: Stack trace completo é registrado para debugging

## Controle de Concorrência

O scheduler delega o controle de concorrência para o `OrquestradorService`, que utiliza a tabela `job_concurrency_control` para:

- Verificar se existe execução RUNNING antes de iniciar
- Criar registro RUNNING ao iniciar
- Atualizar para COMPLETED ao finalizar com sucesso
- Atualizar para PENDING em caso de falha

Isso garante que múltiplas instâncias do orquestrador não processem os mesmos arquivos simultaneamente.

## Testes

### Testes Unitários

`ColetaArquivosSchedulerTest` verifica:
- Invocação correta do `OrquestradorService`
- Tratamento de exceções sem propagação
- Cálculo de duração do ciclo

### Testes de Integração

`ColetaArquivosSchedulerIntegrationTest` verifica:
- Carregamento correto do contexto Spring
- Criação do bean do scheduler
- Habilitação do scheduling via `@EnableScheduling`

## Requisitos Validados

Este componente valida os seguintes requisitos:

- **Requisito 1.1**: Carregar configurações de servidores SFTP (via OrquestradorService)
- **Requisito 5.3**: Controlar concorrência de execução (via OrquestradorService)

## Uso

O scheduler é ativado automaticamente quando o módulo orquestrador é iniciado. Não é necessário nenhuma configuração adicional além da expressão cron no `application.yml`.

Para desabilitar o scheduler temporariamente, defina uma expressão cron que nunca executa:
```yaml
app:
  scheduler:
    coleta:
      cron: "-"  # Desabilita o scheduler
```

Ou use a propriedade Spring:
```yaml
spring:
  task:
    scheduling:
      enabled: false
```
