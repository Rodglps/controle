# Health Checks - Processor

## Visão Geral

O Processor expõe endpoints de health check através do Spring Boot Actuator para monitoramento e gerenciamento pelo Kubernetes.

## Endpoints Disponíveis

### `/actuator/health`
Endpoint principal de health check que retorna o status geral da aplicação e de todas as dependências.

**Resposta quando todas as dependências estão UP:**
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
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 123456789012,
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Resposta quando alguma dependência está DOWN:**
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection"
      }
    },
    "rabbit": {
      "status": "UP"
    }
  }
}
```

### `/actuator/health/liveness`
Endpoint de liveness probe usado pelo Kubernetes para verificar se o pod está vivo.

**Uso no Kubernetes:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

### `/actuator/health/readiness`
Endpoint de readiness probe usado pelo Kubernetes para verificar se o pod está pronto para receber tráfego.

**Uso no Kubernetes:**
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

## Health Indicators Configurados

### 1. Database Health Indicator
- **Bean:** `dbHealthIndicator`
- **Verifica:** Conectividade com o banco de dados Oracle
- **Automático:** Configurado automaticamente pelo Spring Boot quando `spring-boot-starter-data-jpa` está no classpath
- **Critério UP:** Consegue executar query de validação no banco
- **Critério DOWN:** Falha ao conectar ou executar query

### 2. RabbitMQ Health Indicator
- **Bean:** `rabbitHealthIndicator`
- **Verifica:** Conectividade com o RabbitMQ
- **Automático:** Configurado automaticamente pelo Spring Boot quando `spring-boot-starter-amqp` está no classpath
- **Critério UP:** Consegue conectar e obter versão do RabbitMQ
- **Critério DOWN:** Falha ao conectar com o broker

### 3. Disk Space Health Indicator
- **Bean:** `diskSpaceHealthIndicator`
- **Verifica:** Espaço disponível em disco
- **Automático:** Configurado automaticamente pelo Spring Boot Actuator
- **Critério UP:** Espaço livre acima do threshold configurado
- **Critério DOWN:** Espaço livre abaixo do threshold

### 4. Ping Health Indicator
- **Bean:** `pingHealthIndicator`
- **Verifica:** Aplicação está respondendo
- **Automático:** Sempre retorna UP se a aplicação está rodando

## Configuração

A configuração dos health checks está no arquivo `common/src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

### Configurações Importantes

- **`show-details: when-authorized`**: Mostra detalhes dos health indicators apenas para usuários autorizados
- **`probes.enabled: true`**: Habilita os endpoints de liveness e readiness
- **`livenessState.enabled: true`**: Habilita o liveness state
- **`readinessState.enabled: true`**: Habilita o readiness state

## Comportamento em Produção

### Status UP
O sistema retorna status `UP` quando:
- ✅ Banco de dados Oracle está acessível
- ✅ RabbitMQ está acessível
- ✅ Espaço em disco está acima do threshold
- ✅ Aplicação está respondendo

### Status DOWN
O sistema retorna status `DOWN` quando:
- ❌ Banco de dados Oracle está inacessível
- ❌ RabbitMQ está inacessível
- ❌ Espaço em disco está abaixo do threshold

### Impacto no Kubernetes

Quando o health check retorna `DOWN`:
- **Liveness Probe:** Kubernetes reinicia o pod após `failureThreshold` falhas consecutivas
- **Readiness Probe:** Kubernetes remove o pod do load balancer, impedindo que receba tráfego

## Monitoramento

Os health checks podem ser integrados com ferramentas de monitoramento:

### Prometheus
O endpoint `/actuator/prometheus` expõe métricas incluindo o status dos health indicators.

### Grafana
Dashboards podem ser criados para visualizar:
- Status dos health indicators ao longo do tempo
- Tempo de resposta dos health checks
- Frequência de falhas

### Alertas
Alertas podem ser configurados para notificar quando:
- Health check retorna DOWN por mais de X minutos
- Liveness probe falha repetidamente
- Readiness probe falha repetidamente

## Testes

Os health checks são testados em:
- `HealthCheckTest.java`: Testes unitários verificando a configuração dos health indicators
- `HealthEndpointIntegrationTest.java`: Testes de integração verificando os endpoints HTTP

## Requisitos Atendidos

Este componente atende aos seguintes requisitos:

- **Requisito 16.1**: Adicionar Spring Boot Actuator ✅
- **Requisito 16.2**: Expor endpoint /actuator/health ✅
- **Requisito 16.3**: Configurar health indicators para banco de dados ✅
- **Requisito 16.4**: Configurar health indicators para RabbitMQ ✅
- **Requisito 16.5**: Retornar status UP quando todas as dependências estão disponíveis ✅
- **Requisito 16.6**: Retornar status DOWN se alguma dependência crítica estiver indisponível ✅
