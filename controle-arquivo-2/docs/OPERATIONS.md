# Guia de Operações - Controle de Arquivos

Este documento fornece instruções para monitoramento, investigação de erros, reprocessamento e troubleshooting do sistema em produção.

## Índice

- [Monitoramento](#monitoramento)
- [Investigação de Erros](#investigação-de-erros)
- [Reprocessamento](#reprocessamento)
- [Troubleshooting](#troubleshooting)
- [Manutenção](#manutenção)
- [Alertas e Notificações](#alertas-e-notificações)
- [Disaster Recovery](#disaster-recovery)

## Monitoramento

### Health Checks

#### Verificar Status dos Pods

```bash
# Status geral
kubectl get pods -l app=controle-arquivos

# Detalhes de um pod específico
kubectl describe pod <pod-name>

# Health check do Orquestrador
kubectl port-forward svc/controle-arquivos-orchestrator 8080:8080
curl http://localhost:8080/actuator/health

# Health check do Processador
kubectl port-forward svc/controle-arquivos-processor 8081:8081
curl http://localhost:8081/actuator/health
```

**Resposta Esperada**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "Oracle",
        "validationQuery": "isValid()"
      }
    },
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.12.0"
      }
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

#### Liveness e Readiness

```bash
# Liveness (pod está vivo?)
curl http://localhost:8080/actuator/health/liveness

# Readiness (pod está pronto para tráfego?)
curl http://localhost:8080/actuator/health/readiness
```

### Métricas

#### Métricas Prometheus

```bash
# Expor métricas
kubectl port-forward svc/controle-arquivos-processor 8081:8081
curl http://localhost:8081/actuator/prometheus
```

**Métricas Importantes**:

| Métrica | Descrição | Alerta |
|---------|-----------|--------|
| `jvm_memory_used_bytes` | Memória JVM usada | > 80% do max |
| `jvm_threads_live_threads` | Threads ativas | > 200 |
| `hikaricp_connections_active` | Conexões DB ativas | > 80% do pool |
| `rabbitmq_consumed_total` | Mensagens consumidas | Estagnado |
| `http_server_requests_seconds` | Latência HTTP | p99 > 5s |
| `arquivos_processados_total` | Arquivos processados | - |
| `arquivos_erro_total` | Arquivos com erro | Crescimento rápido |

#### Dashboards Grafana

Criar dashboards para:

1. **Overview**:
   - Arquivos processados (últimas 24h)
   - Taxa de erro (%)
   - Latência média de processamento
   - Status dos pods

2. **Performance**:
   - Throughput (arquivos/min)
   - Latência p50, p95, p99
   - Tamanho médio de arquivo
   - Tempo de streaming

3. **Recursos**:
   - CPU usage
   - Memory usage
   - Disk I/O
   - Network I/O

4. **Dependências**:
   - Database connections
   - RabbitMQ queue depth
   - S3 upload latency
   - SFTP connection errors

### Logs

#### Visualizar Logs

```bash
# Logs do Orquestrador
kubectl logs -l component=orchestrator -f

# Logs do Processador
kubectl logs -l component=processor -f

# Logs de todos os pods
kubectl logs -l app=controle-arquivos --all-containers=true -f

# Logs das últimas 1 hora
kubectl logs -l component=processor --since=1h

# Logs com grep
kubectl logs -l component=processor | grep ERROR
```

#### Estrutura de Log

Logs são em formato JSON:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "ProcessadorService",
  "message": "Arquivo processado com sucesso",
  "context": {
    "correlationId": "abc-123-def",
    "fileName": "CIELO_20240115.txt",
    "fileSize": 1048576,
    "clientId": 123,
    "layoutId": 456,
    "duration": 2500
  }
}
```

#### Buscar por Correlation ID

```bash
# Buscar todos os logs de um arquivo específico
kubectl logs -l app=controle-arquivos --all-containers=true | \
  grep "abc-123-def"
```

#### Logs Estruturados no ELK/Splunk

Query Kibana:
```
context.correlationId: "abc-123-def" AND level: ERROR
```

Query Splunk:
```
index=controle-arquivos correlationId="abc-123-def" level=ERROR
```

### Banco de Dados

#### Monitorar Rastreabilidade

```sql
-- Arquivos por status (últimas 24h)
SELECT 
    des_status,
    COUNT(*) as total
FROM file_origin_client_processing
WHERE dat_step_start >= SYSDATE - 1
GROUP BY des_status;

-- Arquivos em erro
SELECT 
    fo.des_file_name,
    focp.des_step,
    focp.des_message_error,
    focp.dat_step_start
FROM file_origin fo
JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
JOIN file_origin_client_processing focp ON foc.idt_file_origin_client = focp.idt_file_origin_client
WHERE focp.des_status = 'ERRO'
AND focp.dat_step_start >= SYSDATE - 1
ORDER BY focp.dat_step_start DESC;

-- Tempo médio de processamento por etapa
SELECT 
    des_step,
    AVG(EXTRACT(SECOND FROM (dat_step_end - dat_step_start))) as avg_seconds
FROM file_origin_client_processing
WHERE des_status = 'CONCLUIDO'
AND dat_step_start >= SYSDATE - 1
GROUP BY des_step;

-- Arquivos pendentes
SELECT COUNT(*)
FROM file_origin_client_processing
WHERE des_status = 'EM_ESPERA'
OR des_status = 'PROCESSAMENTO';
```

#### Monitorar Concorrência

```sql
-- Status do job de coleta
SELECT 
    des_status,
    dat_last_execution,
    dat_created
FROM job_concurrency_control
WHERE cod_job = 'ORCHESTRATOR_COLLECTION'
ORDER BY dat_created DESC
FETCH FIRST 1 ROW ONLY;

-- Verificar locks travados
SELECT 
    des_status,
    dat_last_execution,
    SYSDATE - dat_last_execution as hours_since_last
FROM job_concurrency_control
WHERE cod_job = 'ORCHESTRATOR_COLLECTION'
AND des_status = 'RUNNING'
AND dat_last_execution < SYSDATE - (1/24);  -- Mais de 1 hora
```

### RabbitMQ

#### Management UI

Acessar RabbitMQ Management:

```bash
# Port forward
kubectl port-forward svc/rabbitmq 15672:15672

# Abrir no browser
open http://localhost:15672
```

#### Monitorar Filas

```bash
# Via rabbitmqadmin
rabbitmqadmin list queues name messages consumers

# Via API
curl -u admin:password http://localhost:15672/api/queues
```

**Métricas Importantes**:
- **messages**: Total de mensagens na fila
- **messages_ready**: Mensagens prontas para consumo
- **messages_unacknowledged**: Mensagens sendo processadas
- **consumers**: Número de consumers ativos

**Alertas**:
- Fila crescendo continuamente (> 1000 mensagens)
- Nenhum consumer ativo
- Taxa de consumo < taxa de publicação

## Investigação de Erros

### Fluxo de Investigação

1. **Identificar o Problema**
   - Alertas disparados?
   - Usuário reportou erro?
   - Monitoramento detectou anomalia?

2. **Coletar Contexto**
   - Nome do arquivo
   - Correlation ID
   - Timestamp do erro
   - Etapa do processamento

3. **Analisar Logs**
   - Buscar por correlation ID
   - Verificar stack trace
   - Identificar causa raiz

4. **Verificar Banco de Dados**
   - Status na rastreabilidade
   - Mensagem de erro registrada
   - Tentativas de reprocessamento

5. **Classificar Erro**
   - Recuperável ou não recuperável?
   - Problema de configuração?
   - Problema de dados?
   - Problema de infraestrutura?

6. **Tomar Ação**
   - Reprocessar?
   - Corrigir configuração?
   - Corrigir dados?
   - Escalar para equipe de infra?

### Tipos de Erro

#### Erro: Cliente Não Identificado

**Sintoma**:
```json
{
  "level": "ERROR",
  "message": "Cliente não identificado",
  "context": {
    "fileName": "ARQUIVO_DESCONHECIDO.txt",
    "acquirerId": 1
  }
}
```

**Investigação**:

1. Verificar nome do arquivo:
```sql
SELECT des_file_name 
FROM file_origin 
WHERE idt_file_origin = <id>;
```

2. Verificar regras de identificação:
```sql
SELECT 
    ci.des_customer_name,
    cir.des_criterion_type_enum,
    cir.num_starting_position,
    cir.num_ending_position,
    cir.des_value
FROM customer_identification ci
JOIN customer_identification_rule cir ON ci.idt_customer_identification = cir.idt_customer_identification
WHERE ci.idt_acquirer = <acquirer_id>
AND ci.flg_active = 1
AND cir.flg_active = 1;
```

3. Testar regras manualmente:
```java
String fileName = "ARQUIVO_DESCONHECIDO.txt";
String substring = fileName.substring(0, 5);  // Ajustar posições
// Verificar se substring corresponde a alguma regra
```

**Solução**:
- Adicionar nova regra de identificação
- Corrigir regra existente
- Solicitar ao cliente padronização do nome do arquivo

#### Erro: Layout Não Identificado

**Sintoma**:
```json
{
  "level": "ERROR",
  "message": "Layout não identificado",
  "context": {
    "fileName": "CIELO_20240115.txt",
    "clientId": 123
  }
}
```

**Investigação**:

1. Verificar regras de layout:
```sql
SELECT 
    l.des_layout_name,
    lir.des_value_origin,
    lir.des_criterion_type_enum,
    lir.des_value
FROM layout l
JOIN layout_identification_rule lir ON l.idt_layout = lir.idt_layout
WHERE l.idt_client = <client_id>
AND l.flg_active = 1
AND lir.flg_active = 1;
```

2. Se `des_value_origin = HEADER`, verificar conteúdo:
```bash
# Baixar arquivo do SFTP
sftp user@host
get /path/to/file.txt

# Ver primeiros 7000 bytes
head -c 7000 file.txt
```

**Solução**:
- Adicionar nova regra de layout
- Corrigir regra existente
- Verificar se arquivo está corrompido

#### Erro: Falha de Upload

**Sintoma**:
```json
{
  "level": "ERROR",
  "message": "Falha ao fazer upload para S3",
  "context": {
    "fileName": "CIELO_20240115.txt",
    "bucket": "controle-arquivos",
    "error": "AccessDenied"
  }
}
```

**Investigação**:

1. Verificar credenciais AWS:
```bash
# Testar credenciais
aws s3 ls s3://controle-arquivos --profile <profile>
```

2. Verificar permissões IAM:
```json
{
  "Effect": "Allow",
  "Action": [
    "s3:PutObject",
    "s3:GetObject"
  ],
  "Resource": "arn:aws:s3:::controle-arquivos/*"
}
```

3. Verificar logs AWS CloudTrail

**Solução**:
- Corrigir credenciais no Vault
- Ajustar permissões IAM
- Verificar se bucket existe

#### Erro: Timeout de Processamento

**Sintoma**:
```json
{
  "level": "ERROR",
  "message": "Timeout ao processar arquivo",
  "context": {
    "fileName": "ARQUIVO_GRANDE.txt",
    "fileSize": 5368709120,
    "duration": 300000
  }
}
```

**Investigação**:

1. Verificar tamanho do arquivo
2. Verificar configuração de timeout:
```yaml
app:
  processing:
    timeout-seconds: 300  # 5 minutos
```

3. Verificar performance de rede (SFTP → S3)

**Solução**:
- Aumentar timeout para arquivos grandes
- Otimizar chunk size
- Verificar latência de rede

### Análise de Stack Trace

Exemplo de stack trace em log:

```json
{
  "level": "ERROR",
  "message": "Erro ao processar arquivo",
  "context": {
    "correlationId": "abc-123"
  },
  "error": {
    "type": "SftpException",
    "message": "Connection timeout",
    "stackTrace": [
      "com.jcraft.jsch.JSchException: timeout: socket is not established",
      "at com.controle.arquivos.common.client.SFTPClient.connect(SFTPClient.java:45)",
      "at com.controle.arquivos.processor.service.ProcessadorService.downloadFile(ProcessadorService.java:123)"
    ]
  }
}
```

**Análise**:
- **Tipo**: `SftpException`
- **Causa**: Connection timeout
- **Localização**: `SFTPClient.connect()` linha 45
- **Contexto**: Processamento de arquivo com correlationId `abc-123`

**Ação**: Verificar conectividade com servidor SFTP, aumentar timeout, verificar firewall.

## Reprocessamento

### Reprocessamento Manual

#### Via Banco de Dados

1. Identificar arquivo para reprocessar:
```sql
SELECT 
    fo.idt_file_origin,
    fo.des_file_name,
    focp.des_message_error
FROM file_origin fo
JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
JOIN file_origin_client_processing focp ON foc.idt_file_origin_client = focp.idt_file_origin_client
WHERE focp.des_status = 'ERRO'
AND fo.des_file_name = 'CIELO_20240115.txt';
```

2. Resetar status para reprocessamento:
```sql
-- Atualizar status para EM_ESPERA
UPDATE file_origin_client_processing
SET des_status = 'EM_ESPERA',
    des_message_error = NULL,
    dat_step_start = NULL,
    dat_step_end = NULL
WHERE idt_file_origin_processing = <id>;

COMMIT;
```

3. Republicar mensagem no RabbitMQ:
```java
// Via código ou script
rabbitTemplate.convertAndSend(
    "file.processing.exchange",
    "file.processing.key",
    new MensagemProcessamento(fileOriginId, fileName, mappingId)
);
```

#### Via RabbitMQ Management

1. Acessar Management UI
2. Ir para "Queues"
3. Selecionar fila `file.processing.queue`
4. "Publish message"
5. Payload:
```json
{
  "idFileOrigin": 123,
  "fileName": "CIELO_20240115.txt",
  "idMapeamentoOrigemDestino": 456,
  "correlationId": "manual-reprocess-001"
}
```

### Reprocessamento em Lote

Script para reprocessar múltiplos arquivos:

```sql
-- Identificar arquivos em erro recuperável
SELECT idt_file_origin
FROM file_origin fo
JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
JOIN file_origin_client_processing focp ON foc.idt_file_origin_client = focp.idt_file_origin_client
WHERE focp.des_status = 'ERRO'
AND focp.des_message_error LIKE '%timeout%'  -- Erro recuperável
AND focp.dat_step_start >= SYSDATE - 1;  -- Últimas 24h

-- Resetar status
UPDATE file_origin_client_processing focp
SET focp.des_status = 'EM_ESPERA',
    focp.des_message_error = NULL
WHERE focp.idt_file_origin_processing IN (
    SELECT focp2.idt_file_origin_processing
    FROM file_origin fo
    JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
    JOIN file_origin_client_processing focp2 ON foc.idt_file_origin_client = focp2.idt_file_origin_client
    WHERE focp2.des_status = 'ERRO'
    AND focp2.des_message_error LIKE '%timeout%'
    AND focp2.dat_step_start >= SYSDATE - 1
);

COMMIT;
```

### Limite de Reprocessamento

O sistema limita reprocessamentos a 5 tentativas:

```sql
-- Verificar número de tentativas
SELECT 
    fo.des_file_name,
    COUNT(*) as tentativas
FROM file_origin fo
JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
JOIN file_origin_client_processing focp ON foc.idt_file_origin_client = focp.idt_file_origin_client
WHERE focp.des_status = 'ERRO'
GROUP BY fo.des_file_name
HAVING COUNT(*) >= 5;
```

Arquivos com 5+ tentativas devem ser investigados manualmente.

## Troubleshooting

### Problema: Orquestrador não coleta arquivos

**Sintomas**:
- Nenhum arquivo novo em `file_origin`
- Nenhuma mensagem publicada no RabbitMQ
- Logs não mostram execução do scheduler

**Verificações**:

1. Scheduler está habilitado?
```bash
kubectl get configmap controle-arquivos-config -o yaml | grep scheduler
```

2. Pod está rodando?
```bash
kubectl get pods -l component=orchestrator
```

3. Logs mostram erros?
```bash
kubectl logs -l component=orchestrator --tail=100
```

4. Job concurrency está travado?
```sql
SELECT * FROM job_concurrency_control 
WHERE cod_job = 'ORCHESTRATOR_COLLECTION'
ORDER BY dat_created DESC;
```

**Soluções**:

- Habilitar scheduler: `SCHEDULER_ENABLED=true`
- Reiniciar pod: `kubectl delete pod <pod-name>`
- Liberar lock: 
```sql
UPDATE job_concurrency_control 
SET des_status = 'COMPLETED' 
WHERE cod_job = 'ORCHESTRATOR_COLLECTION' 
AND des_status = 'RUNNING';
```

### Problema: Processador não consome mensagens

**Sintomas**:
- Mensagens acumulando na fila
- Nenhum arquivo sendo processado
- Logs não mostram consumo

**Verificações**:

1. Pod está rodando?
```bash
kubectl get pods -l component=processor
```

2. Consumers estão ativos?
```bash
# Via RabbitMQ Management
curl -u admin:password http://localhost:15672/api/queues/%2F/file.processing.queue
```

3. Conexão com RabbitMQ está OK?
```bash
kubectl logs -l component=processor | grep "rabbit"
```

**Soluções**:

- Reiniciar pod: `kubectl delete pod <pod-name>`
- Verificar credenciais RabbitMQ
- Verificar conectividade de rede

### Problema: Memória alta / OutOfMemoryError

**Sintomas**:
- Pod reiniciando frequentemente
- Logs mostram `OutOfMemoryError`
- Métricas mostram memória > 90%

**Verificações**:

1. Heap usage:
```bash
kubectl exec -it <pod-name> -- jcmd 1 VM.native_memory summary
```

2. Threads:
```bash
kubectl exec -it <pod-name> -- jcmd 1 Thread.print
```

3. Tamanho dos arquivos sendo processados:
```sql
SELECT AVG(num_file_size), MAX(num_file_size)
FROM file_origin
WHERE dat_timestamp_file >= SYSDATE - 1;
```

**Soluções**:

- Aumentar heap: `-Xmx4g`
- Reduzir chunk size: `streaming.chunk-size=2097152` (2MB)
- Reduzir concorrência: `rabbitmq.listener.max-concurrency=5`
- Aumentar resource limits no Kubernetes:
```yaml
resources:
  limits:
    memory: 4Gi
```

### Problema: Processamento lento

**Sintomas**:
- Arquivos levando muito tempo para processar
- Fila crescendo
- Latência alta

**Verificações**:

1. Tempo médio de processamento:
```sql
SELECT 
    AVG(EXTRACT(SECOND FROM (dat_step_end - dat_step_start))) as avg_seconds
FROM file_origin_client_processing
WHERE des_status = 'CONCLUIDO'
AND dat_step_start >= SYSDATE - 1;
```

2. Gargalos:
```sql
SELECT 
    des_step,
    AVG(EXTRACT(SECOND FROM (dat_step_end - dat_step_start))) as avg_seconds
FROM file_origin_client_processing
WHERE des_status = 'CONCLUIDO'
AND dat_step_start >= SYSDATE - 1
GROUP BY des_step
ORDER BY avg_seconds DESC;
```

3. Recursos:
```bash
kubectl top pods -l app=controle-arquivos
```

**Soluções**:

- Aumentar concorrência: `rabbitmq.listener.max-concurrency=20`
- Escalar pods: `kubectl scale deployment processor --replicas=5`
- Otimizar chunk size: `streaming.chunk-size=10485760` (10MB)
- Verificar latência de rede (SFTP, S3)

### Problema: Banco de dados lento

**Sintomas**:
- Queries lentas
- Timeouts de conexão
- Pool de conexões esgotado

**Verificações**:

1. Conexões ativas:
```sql
SELECT COUNT(*) FROM v$session WHERE username = 'APP_USER';
```

2. Queries lentas:
```sql
SELECT sql_text, elapsed_time
FROM v$sql
WHERE elapsed_time > 1000000  -- > 1 segundo
ORDER BY elapsed_time DESC;
```

3. Pool Hikari:
```bash
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

**Soluções**:

- Aumentar pool: `db.hikari.max-pool-size=50`
- Adicionar índices em colunas de busca
- Otimizar queries
- Escalar banco de dados (RDS)

## Manutenção

### Rotação de Credenciais

#### Vault

1. Atualizar secret no Vault:
```bash
vault kv put secret/controle-arquivos/sftp/cielo \
  host=sftp.cielo.com.br \
  username=new_user \
  password=new_password
```

2. Reiniciar pods para recarregar:
```bash
kubectl rollout restart deployment/controle-arquivos-orchestrator
kubectl rollout restart deployment/controle-arquivos-processor
```

#### Banco de Dados

1. Criar novo usuário no Oracle
2. Atualizar secret no Kubernetes:
```bash
kubectl create secret generic controle-arquivos-secrets \
  --from-literal=db-password='new_password' \
  --dry-run=client -o yaml | kubectl apply -f -
```

3. Reiniciar pods:
```bash
kubectl rollout restart deployment/controle-arquivos-orchestrator
kubectl rollout restart deployment/controle-arquivos-processor
```

### Limpeza de Dados Antigos

```sql
-- Arquivar registros antigos (> 90 dias)
INSERT INTO file_origin_client_processing_archive
SELECT * FROM file_origin_client_processing
WHERE dat_step_start < SYSDATE - 90;

-- Deletar registros arquivados
DELETE FROM file_origin_client_processing
WHERE dat_step_start < SYSDATE - 90;

COMMIT;

-- Rebuild índices
ALTER INDEX idx_focp_step_start REBUILD;
```

### Atualização de Versão

1. Build nova imagem:
```bash
mvn clean package
docker build -t controle-arquivos-processor:v2.0.0 .
docker push controle-arquivos-processor:v2.0.0
```

2. Atualizar deployment:
```bash
kubectl set image deployment/controle-arquivos-processor \
  processor=controle-arquivos-processor:v2.0.0
```

3. Monitorar rollout:
```bash
kubectl rollout status deployment/controle-arquivos-processor
```

4. Rollback se necessário:
```bash
kubectl rollout undo deployment/controle-arquivos-processor
```

## Alertas e Notificações

### Alertas Críticos

Configure alertas para:

1. **Pod Down**
   - Condição: Pod não está rodando
   - Ação: Investigar logs, reiniciar se necessário

2. **Health Check Failed**
   - Condição: `/actuator/health` retorna DOWN
   - Ação: Verificar dependências (DB, RabbitMQ)

3. **High Error Rate**
   - Condição: Taxa de erro > 10%
   - Ação: Investigar logs, verificar configurações

4. **Queue Depth High**
   - Condição: Fila RabbitMQ > 1000 mensagens
   - Ação: Escalar processadores, investigar lentidão

5. **Memory High**
   - Condição: Memória > 90%
   - Ação: Aumentar recursos, investigar memory leak

6. **Database Connection Pool Exhausted**
   - Condição: Conexões ativas = max pool size
   - Ação: Aumentar pool, investigar queries lentas

### Configuração de Alertas (Prometheus)

```yaml
groups:
- name: controle-arquivos
  rules:
  - alert: PodDown
    expr: up{job="controle-arquivos"} == 0
    for: 5m
    annotations:
      summary: "Pod {{ $labels.pod }} is down"
  
  - alert: HighErrorRate
    expr: rate(arquivos_erro_total[5m]) / rate(arquivos_processados_total[5m]) > 0.1
    for: 10m
    annotations:
      summary: "High error rate: {{ $value }}"
  
  - alert: QueueDepthHigh
    expr: rabbitmq_queue_messages{queue="file.processing.queue"} > 1000
    for: 15m
    annotations:
      summary: "Queue depth: {{ $value }} messages"
```

## Disaster Recovery

### Backup

#### Banco de Dados

```bash
# Backup via RMAN (Oracle)
rman target /
BACKUP DATABASE PLUS ARCHIVELOG;
```

#### Configurações

```bash
# Backup de ConfigMaps e Secrets
kubectl get configmap,secret -n controle-arquivos -o yaml > backup.yaml
```

### Restore

#### Banco de Dados

```bash
# Restore via RMAN
rman target /
RESTORE DATABASE;
RECOVER DATABASE;
ALTER DATABASE OPEN;
```

#### Aplicação

```bash
# Redeployar
kubectl apply -f k8s/
```

### Testes de DR

Realizar testes trimestrais:

1. Simular falha de pod
2. Simular falha de banco de dados
3. Simular falha de RabbitMQ
4. Verificar recuperação automática
5. Documentar tempo de recuperação (RTO)
6. Documentar perda de dados (RPO)

## Contatos e Escalação

### Níveis de Suporte

**Nível 1** (Operações):
- Monitoramento
- Alertas
- Troubleshooting básico
- Reprocessamento

**Nível 2** (Desenvolvimento):
- Investigação de bugs
- Análise de logs
- Correções de configuração
- Ajustes de performance

**Nível 3** (Arquitetura):
- Problemas de design
- Mudanças arquiteturais
- Escalabilidade
- Disaster recovery

### Procedimento de Escalação

1. **Incidente Detectado** → Nível 1
2. **Não Resolvido em 30min** → Escalar para Nível 2
3. **Não Resolvido em 2h** → Escalar para Nível 3
4. **Incidente Crítico** → Escalar imediatamente para Nível 3

## Referências

- [Kubernetes Troubleshooting](https://kubernetes.io/docs/tasks/debug/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [RabbitMQ Monitoring](https://www.rabbitmq.com/monitoring.html)
- [Oracle Performance Tuning](https://docs.oracle.com/en/database/oracle/oracle-database/19/tgdba/)

