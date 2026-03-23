# Task 10.8: Criar Scheduler para Execução Periódica - Resumo

## Objetivo

Criar um scheduler para execução periódica do ciclo de coleta de arquivos, invocando o método `executarCicloColeta()` do `OrquestradorService` em intervalos configuráveis.

## Implementação

### 1. ColetaArquivosScheduler

**Localização**: `orchestrator/src/main/java/com/controle/arquivos/orchestrator/scheduler/ColetaArquivosScheduler.java`

**Características**:
- Usa `@Scheduled` com cron expression configurável
- Invoca `OrquestradorService.executarCicloColeta()` periodicamente
- Registra logs estruturados de início e fim de ciclo
- Calcula e registra duração de cada execução
- Trata exceções sem propagar para não interromper execuções futuras

**Logs Estruturados**:
```
INFO: Iniciando ciclo de coleta agendado: timestamp=2024-01-15T10:00:00.000Z
INFO: Ciclo de coleta concluído com sucesso: timestamp=2024-01-15T10:05:23.456Z, duracao_ms=323456
ERROR: Ciclo de coleta falhou: timestamp=2024-01-15T10:05:23.456Z, duracao_ms=123456, erro=Conexão SFTP falhou
```

### 2. SchedulingConfig

**Localização**: `orchestrator/src/main/java/com/controle/arquivos/orchestrator/config/SchedulingConfig.java`

**Características**:
- Habilita scheduling no módulo orquestrador via `@EnableScheduling`
- Permite o uso de `@Scheduled` em componentes

### 3. Configuração

**Localização**: `orchestrator/src/main/resources/application.yml`

**Propriedade Adicionada**:
```yaml
app:
  scheduler:
    coleta:
      cron: "0 */5 * * * *"  # A cada 5 minutos (padrão)
```

**Formato da Expressão Cron**:
```
segundo minuto hora dia mês dia-da-semana
```

**Exemplos**:
- `0 */5 * * * *` - A cada 5 minutos
- `0 0 * * * *` - A cada hora
- `0 0 */2 * * *` - A cada 2 horas
- `0 0 0 * * *` - Todo dia à meia-noite
- `0 0 9-17 * * MON-FRI` - De hora em hora, das 9h às 17h, de segunda a sexta

## Testes

### Testes Unitários

**Localização**: `orchestrator/src/test/java/com/controle/arquivos/orchestrator/scheduler/ColetaArquivosSchedulerTest.java`

**Cenários Testados**:
1. ✅ Invocação correta do `OrquestradorService`
2. ✅ Registro de log de início
3. ✅ Registro de log de conclusão
4. ✅ Tratamento de exceção sem propagação
5. ✅ Registro de log de erro quando falhar
6. ✅ Cálculo de duração do ciclo

### Testes de Integração

**Localização**: `orchestrator/src/test/java/com/controle/arquivos/orchestrator/scheduler/ColetaArquivosSchedulerIntegrationTest.java`

**Cenários Testados**:
1. ✅ Carregamento correto do contexto Spring
2. ✅ Criação do bean do scheduler
3. ✅ Criação do bean de configuração de scheduling
4. ✅ Habilitação do scheduling via `@EnableScheduling`

## Controle de Concorrência

O scheduler delega o controle de concorrência para o `OrquestradorService`, que utiliza a tabela `job_concurrency_control` para:

- ✅ Verificar se existe execução RUNNING antes de iniciar
- ✅ Criar registro RUNNING ao iniciar
- ✅ Atualizar para COMPLETED ao finalizar com sucesso
- ✅ Atualizar para PENDING em caso de falha

Isso garante que múltiplas instâncias do orquestrador não processem os mesmos arquivos simultaneamente.

## Tratamento de Erros

O scheduler implementa tratamento robusto de erros:

1. **Captura de Exceções**: Todas as exceções são capturadas e logadas
2. **Não Propagação**: Exceções não são propagadas para não interromper o agendamento
3. **Próxima Execução**: Mesmo após falha, a próxima execução agendada ocorre normalmente
4. **Log Detalhado**: Stack trace completo é registrado para debugging

## Requisitos Validados

- ✅ **Requisito 1.1**: Carregar configurações de servidores SFTP (via OrquestradorService)
- ✅ **Requisito 5.3**: Controlar concorrência de execução (via OrquestradorService)

## Arquivos Criados

1. `orchestrator/src/main/java/com/controle/arquivos/orchestrator/scheduler/ColetaArquivosScheduler.java`
2. `orchestrator/src/main/java/com/controle/arquivos/orchestrator/config/SchedulingConfig.java`
3. `orchestrator/src/test/java/com/controle/arquivos/orchestrator/scheduler/ColetaArquivosSchedulerTest.java`
4. `orchestrator/src/test/java/com/controle/arquivos/orchestrator/scheduler/ColetaArquivosSchedulerIntegrationTest.java`
5. `orchestrator/src/main/java/com/controle/arquivos/orchestrator/scheduler/README.md`

## Arquivos Modificados

1. `orchestrator/src/main/resources/application.yml` - Adicionada configuração do scheduler

## Uso

O scheduler é ativado automaticamente quando o módulo orquestrador é iniciado. Não é necessário nenhuma configuração adicional além da expressão cron no `application.yml`.

### Desabilitar o Scheduler

Para desabilitar o scheduler temporariamente:

**Opção 1**: Expressão cron que nunca executa
```yaml
app:
  scheduler:
    coleta:
      cron: "-"
```

**Opção 2**: Propriedade Spring
```yaml
spring:
  task:
    scheduling:
      enabled: false
```

## Próximos Passos

O scheduler está pronto para uso. Para testar em ambiente local:

1. Iniciar o módulo orquestrador
2. Verificar logs de execução periódica
3. Ajustar a expressão cron conforme necessário para o ambiente

## Observações

- A implementação é mínima e focada, seguindo o princípio de escrever apenas o código necessário
- O scheduler não implementa lógica de negócio - apenas invoca o `OrquestradorService`
- O controle de concorrência é delegado ao `OrquestradorService`, mantendo a separação de responsabilidades
- Logs estruturados facilitam monitoramento e debugging em produção
