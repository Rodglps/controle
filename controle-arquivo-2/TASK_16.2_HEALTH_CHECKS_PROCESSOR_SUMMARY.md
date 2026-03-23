# Task 16.2: Configurar Health Checks - Processor

## Resumo

Configuração completa de health checks para o Pod Processador usando Spring Boot Actuator, permitindo monitoramento e gerenciamento pelo Kubernetes através de liveness e readiness probes.

## Implementação

### 1. Dependências

Spring Boot Actuator já estava configurado no `processor/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Adicionado H2 database para testes:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. Configuração

Configuração de health checks adicionada em `processor/src/main/resources/application.yml`:

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

### 3. Health Indicators Configurados

O Spring Boot Actuator configura automaticamente os seguintes health indicators:

#### Database Health Indicator
- **Bean:** `dbHealthIndicator`
- **Verifica:** Conectividade com Oracle Database
- **Critério UP:** Consegue executar query de validação
- **Critério DOWN:** Falha ao conectar ou executar query

#### RabbitMQ Health Indicator
- **Bean:** `rabbitHealthIndicator`
- **Verifica:** Conectividade com RabbitMQ
- **Critério UP:** Consegue conectar e obter versão do broker
- **Critério DOWN:** Falha ao conectar com o broker

#### Disk Space Health Indicator
- **Bean:** `diskSpaceHealthIndicator`
- **Verifica:** Espaço disponível em disco
- **Critério UP:** Espaço livre acima do threshold
- **Critério DOWN:** Espaço livre abaixo do threshold

#### Ping Health Indicator
- **Bean:** `pingHealthIndicator`
- **Verifica:** Aplicação está respondendo
- **Sempre UP** se a aplicação está rodando

### 4. Endpoints Disponíveis

#### `/actuator/health`
Endpoint principal que retorna status geral da aplicação e todas as dependências.

**Resposta quando UP:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

**Resposta quando DOWN:**
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "org.springframework.jdbc.CannotGetJdbcConnectionException"
      }
    }
  }
}
```

#### `/actuator/health/liveness`
Liveness probe usado pelo Kubernetes para verificar se o pod está vivo.
- Se falhar, Kubernetes reinicia o pod

#### `/actuator/health/readiness`
Readiness probe usado pelo Kubernetes para verificar se o pod está pronto.
- Se falhar, Kubernetes remove o pod do load balancer

### 5. Integração com Kubernetes

Criado arquivo `processor/k8s-deployment-example.yml` com configuração completa:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3

startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 30
```

### 6. Testes

#### Testes Unitários
`processor/src/test/java/com/controle/arquivos/processor/health/HealthCheckTest.java`

Verifica:
- ✅ Spring Boot Actuator está configurado
- ✅ Health indicator do banco de dados está presente
- ✅ Health indicator do RabbitMQ está presente
- ✅ Liveness e readiness probes estão habilitados

#### Testes de Integração
`processor/src/test/java/com/controle/arquivos/processor/health/HealthEndpointIntegrationTest.java`

Verifica:
- ✅ Endpoint `/actuator/health` retorna 200 OK
- ✅ Endpoint `/actuator/health/liveness` retorna 200 OK
- ✅ Endpoint `/actuator/health/readiness` retorna 200 OK
- ✅ Status UP quando dependências estão disponíveis

### 7. Documentação

Criado `processor/src/main/java/com/controle/arquivos/processor/health/README.md` com:
- Visão geral dos health checks
- Descrição de todos os endpoints
- Configuração dos health indicators
- Comportamento em produção
- Integração com Kubernetes
- Monitoramento e alertas

## Arquivos Criados/Modificados

### Criados
1. `processor/src/main/java/com/controle/arquivos/processor/health/README.md`
2. `processor/src/test/java/com/controle/arquivos/processor/health/HealthCheckTest.java`
3. `processor/src/test/java/com/controle/arquivos/processor/health/HealthEndpointIntegrationTest.java`
4. `processor/k8s-deployment-example.yml`

### Modificados
1. `processor/src/main/resources/application.yml` - Adicionada configuração de management endpoints
2. `processor/pom.xml` - Adicionada dependência H2 para testes
3. `common/pom.xml` - Adicionada dependência H2 para testes

## Requisitos Atendidos

- ✅ **Requisito 16.1**: Adicionar Spring Boot Actuator
- ✅ **Requisito 16.2**: Expor endpoint /actuator/health
- ✅ **Requisito 16.3**: Configurar health indicators para banco de dados
- ✅ **Requisito 16.4**: Configurar health indicators para RabbitMQ
- ✅ **Requisito 16.5**: Retornar status UP quando todas as dependências estão disponíveis
- ✅ **Requisito 16.6**: Retornar status DOWN se alguma dependência crítica estiver indisponível

## Propriedades de Corretude Validadas

**Propriedade 31**: Health Check com Dependências
- Para qualquer execução de health check, o Sistema deve verificar conectividade com banco de dados e RabbitMQ
- Retorna status UP quando todas as dependências estão disponíveis
- Retorna status DOWN se alguma dependência crítica estiver indisponível

## Comportamento em Produção

### Status UP
O sistema retorna `UP` quando:
- ✅ Banco de dados Oracle está acessível
- ✅ RabbitMQ está acessível
- ✅ Espaço em disco está acima do threshold
- ✅ Aplicação está respondendo

### Status DOWN
O sistema retorna `DOWN` quando:
- ❌ Banco de dados Oracle está inacessível
- ❌ RabbitMQ está inacessível
- ❌ Espaço em disco está abaixo do threshold

### Impacto no Kubernetes

**Liveness Probe:**
- Kubernetes reinicia o pod após 3 falhas consecutivas
- Útil para recuperar de deadlocks ou estados inválidos

**Readiness Probe:**
- Kubernetes remove o pod do load balancer
- Impede que requisições sejam enviadas para pods não prontos
- Pod volta ao load balancer quando readiness retorna UP

**Startup Probe:**
- Permite até 30 tentativas (150 segundos) para a aplicação inicializar
- Evita que liveness probe reinicie o pod durante inicialização lenta

## Monitoramento

### Prometheus
O endpoint `/actuator/prometheus` expõe métricas incluindo:
- Status dos health indicators
- Tempo de resposta dos health checks
- Contadores de UP/DOWN por componente

### Alertas Recomendados
- Health check DOWN por mais de 5 minutos
- Liveness probe falhando repetidamente
- Readiness probe falhando repetidamente
- Reinicializações frequentes do pod

## Notas Técnicas

1. **Configuração Automática**: Spring Boot configura automaticamente os health indicators quando as dependências estão no classpath
2. **Sem Código Customizado**: Não foi necessário criar health indicators customizados
3. **Herança de Configuração**: A configuração de management endpoints pode ser herdada do módulo common
4. **Testes com H2**: Adicionado H2 database para permitir testes sem Oracle
5. **Compatibilidade**: Configuração compatível com Kubernetes 1.20+

## Próximos Passos

1. Executar testes em ambiente com Oracle e RabbitMQ reais
2. Configurar alertas no Prometheus/Grafana
3. Ajustar thresholds de probes conforme comportamento em produção
4. Adicionar health indicators customizados se necessário (ex: verificar conectividade com Vault)
