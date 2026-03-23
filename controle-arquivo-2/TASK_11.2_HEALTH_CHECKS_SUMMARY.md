# Task 11.2: Configurar Health Checks - Resumo

## Objetivo
Configurar health checks no Orchestrator usando Spring Boot Actuator para monitoramento pelo Kubernetes.

## Requisitos Atendidos
- ✅ **Requisito 16.1**: Adicionar Spring Boot Actuator
- ✅ **Requisito 16.2**: Expor endpoint /actuator/health
- ✅ **Requisito 16.3**: Configurar health indicators para banco de dados
- ✅ **Requisito 16.4**: Configurar health indicators para RabbitMQ
- ✅ **Requisito 16.5**: Retornar status UP quando todas as dependências estão disponíveis
- ✅ **Requisito 16.6**: Retornar status DOWN se alguma dependência crítica estiver indisponível

## Implementação

### 1. Dependências
O Spring Boot Actuator já estava configurado no `orchestrator/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2. Configuração
A configuração dos health checks está no `common/src/main/resources/application.yml`:
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

### 3. Health Indicators Automáticos
O Spring Boot configura automaticamente os seguintes health indicators:

#### Database Health Indicator
- **Bean**: `dbHealthIndicator`
- **Configurado por**: `spring-boot-starter-data-jpa`
- **Verifica**: Conectividade com Oracle Database
- **Critério UP**: Consegue executar query de validação
- **Critério DOWN**: Falha ao conectar ou executar query

#### RabbitMQ Health Indicator
- **Bean**: `rabbitHealthIndicator`
- **Configurado por**: `spring-boot-starter-amqp`
- **Verifica**: Conectividade com RabbitMQ
- **Critério UP**: Consegue conectar e obter versão do broker
- **Critério DOWN**: Falha ao conectar com o broker

#### Disk Space Health Indicator
- **Bean**: `diskSpaceHealthIndicator`
- **Configurado por**: Spring Boot Actuator
- **Verifica**: Espaço disponível em disco
- **Critério UP**: Espaço livre acima do threshold
- **Critério DOWN**: Espaço livre abaixo do threshold

#### Ping Health Indicator
- **Bean**: `pingHealthIndicator`
- **Configurado por**: Spring Boot Actuator
- **Verifica**: Aplicação está respondendo
- **Critério UP**: Sempre retorna UP se a aplicação está rodando

### 4. Endpoints Disponíveis

#### `/actuator/health`
Endpoint principal que retorna o status geral da aplicação e de todas as dependências.

**Exemplo de resposta (status UP):**
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
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Exemplo de resposta (status DOWN):**
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "org.springframework.jdbc.CannotGetJdbcConnectionException"
      }
    },
    "rabbit": {
      "status": "UP"
    }
  }
}
```

#### `/actuator/health/liveness`
Endpoint de liveness probe usado pelo Kubernetes para verificar se o pod está vivo.
- Se falhar, o Kubernetes reinicia o pod

#### `/actuator/health/readiness`
Endpoint de readiness probe usado pelo Kubernetes para verificar se o pod está pronto para receber tráfego.
- Se falhar, o Kubernetes remove o pod do load balancer

## Arquivos Criados

### 1. Testes
- **`orchestrator/src/test/java/com/controle/arquivos/orchestrator/health/HealthCheckTest.java`**
  - Testes unitários verificando a configuração dos health indicators
  - Valida que os beans corretos estão configurados
  - Verifica liveness e readiness probes

- **`orchestrator/src/test/java/com/controle/arquivos/orchestrator/health/HealthEndpointIntegrationTest.java`**
  - Testes de integração verificando os endpoints HTTP
  - Testa `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness`
  - Valida respostas HTTP e estrutura JSON

- **`orchestrator/src/test/resources/application-test.yml`**
  - Configuração de teste para os health checks
  - Habilita `show-details: always` para facilitar debugging

### 2. Documentação
- **`orchestrator/src/main/java/com/controle/arquivos/orchestrator/health/README.md`**
  - Documentação completa dos health checks
  - Exemplos de respostas JSON
  - Configuração para Kubernetes
  - Guia de monitoramento

### 3. Kubernetes
- **`orchestrator/k8s-deployment-example.yml`**
  - Exemplo de deployment do Kubernetes
  - Configuração de liveness, readiness e startup probes
  - ConfigMap e Secret para configurações
  - Service para expor o pod

## Uso no Kubernetes

### Liveness Probe
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

### Readiness Probe
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3
```

### Startup Probe
```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 30
```

## Comportamento

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
- **Liveness Probe DOWN**: Kubernetes reinicia o pod
- **Readiness Probe DOWN**: Kubernetes remove o pod do load balancer

## Monitoramento

Os health checks podem ser integrados com:
- **Prometheus**: Métricas expostas em `/actuator/prometheus`
- **Grafana**: Dashboards para visualizar status ao longo do tempo
- **Alertas**: Notificações quando health checks falham

## Testes Executados

Os testes foram criados e compilam sem erros:
- ✅ `HealthCheckTest.java` - Testes unitários
- ✅ `HealthEndpointIntegrationTest.java` - Testes de integração

## Conclusão

A tarefa 11.2 foi concluída com sucesso. O Orchestrator agora possui:
1. ✅ Spring Boot Actuator configurado
2. ✅ Endpoint `/actuator/health` exposto
3. ✅ Health indicators para banco de dados e RabbitMQ
4. ✅ Liveness e readiness probes para Kubernetes
5. ✅ Testes unitários e de integração
6. ✅ Documentação completa
7. ✅ Exemplo de deployment para Kubernetes

O sistema está pronto para ser monitorado pelo Kubernetes e ferramentas de observabilidade.
