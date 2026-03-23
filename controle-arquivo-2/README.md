# Controle de Arquivos

Sistema distribuído para coleta, identificação e encaminhamento de arquivos EDI de adquirentes.

## Visão Geral

O Controle de Arquivos é uma solução baseada em microserviços que automatiza o processo de entrada de dados para conciliação de cartão. O sistema coleta arquivos EDI de múltiplos servidores SFTP, identifica cliente e layout usando regras configuráveis, e encaminha os arquivos para destinos apropriados (S3 ou SFTP interno), mantendo rastreabilidade completa de todas as etapas.

### Características Principais

- **Arquitetura Distribuída**: 2 pods independentes (Orquestrador e Processador) comunicando via RabbitMQ
- **Streaming de Arquivos**: Processamento de arquivos de qualquer tamanho sem limitações de memória
- **Identificação Inteligente**: Sistema de regras configurável para identificação de cliente e layout
- **Rastreabilidade Completa**: Registro detalhado de todas as etapas de processamento
- **Alta Disponibilidade**: Auto-scaling, health checks e recuperação automática de falhas
- **Segurança**: Integração com Vault para gerenciamento seguro de credenciais

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                          │
│                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐            │
│  │  Pod Orquestrador│         │  Pod Processador │            │
│  │                  │         │                  │            │
│  │  - Coleta SFTP   │         │  - Download      │            │
│  │  - Registro DB   │         │  - Identificação │            │
│  │  - Publica MQ    │         │  - Upload        │            │
│  └────────┬─────────┘         └────────▲─────────┘            │
│           │                            │                       │
│           │      ┌──────────────┐      │                       │
│           └─────►│  RabbitMQ    │──────┘                       │
│                  └──────────────┘                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
           │                                    │
           ▼                                    ▼
    ┌─────────────┐                      ┌─────────────┐
    │   Oracle    │                      │    Vault    │
    │  Database   │                      │ (Credenciais)│
    └─────────────┘                      └─────────────┘
           │
           ▼
    ┌─────────────────────────────────────────────────┐
    │  Servidores SFTP Externos  │  AWS S3  │  SFTP   │
    └─────────────────────────────────────────────────┘
```

### Componentes

#### Pod Orquestrador
- Executa ciclos periódicos de coleta (scheduler configurável)
- Lista arquivos em servidores SFTP externos
- Registra novos arquivos no banco de dados
- Publica mensagens no RabbitMQ para processamento
- Controla concorrência via `job_concurrency_control`

#### Pod Processador
- Consome mensagens do RabbitMQ
- Baixa arquivos via streaming (sem carregar em memória)
- Identifica cliente usando regras baseadas no nome do arquivo
- Identifica layout usando regras baseadas no nome ou conteúdo
- Faz upload para destino (S3 ou SFTP) via streaming
- Atualiza rastreabilidade em todas as etapas

## Pré-requisitos

### Desenvolvimento Local

- **Java 17+**
- **Maven 3.8+**
- **Docker** e **Docker Compose**
- **Git**
- Mínimo 8GB RAM disponível

### Produção

- **Kubernetes 1.24+**
- **Oracle Database** (RDS ou on-premises)
- **RabbitMQ** (AWS MQ ou self-hosted)
- **HashiCorp Vault**
- **AWS S3** (ou storage compatível)
- **Servidores SFTP** configurados

## Início Rápido

### 1. Clonar o Repositório

```bash
git clone <repository-url>
cd controle-arquivos
```

### 2. Configurar Ambiente Local

Copie o arquivo de exemplo de variáveis de ambiente:

```bash
cp .env.example .env
```

Edite `.env` com suas configurações locais (se necessário).

### 3. Iniciar Infraestrutura Local

```bash
docker-compose up -d
```

Isso iniciará:
- Oracle XE (porta 1521)
- RabbitMQ (porta 5672, management 15672)
- LocalStack S3 (porta 4566)
- Servidor SFTP (porta 2222)

### 4. Aplicar Scripts DDL

```bash
# Conectar ao Oracle e executar scripts
sqlplus system/Oracle123@localhost:1521/XE @scripts/ddl/create-tables.sql
```

Ou use sua ferramenta SQL preferida (DBeaver, SQL Developer, etc.).

### 5. Compilar o Projeto

```bash
mvn clean install
```

### 6. Executar Orquestrador

```bash
cd orchestrator
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 7. Executar Processador

Em outro terminal:

```bash
cd processor
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 8. Verificar Health

```bash
# Orquestrador
curl http://localhost:8080/actuator/health

# Processador
curl http://localhost:8081/actuator/health
```

## Estrutura do Projeto

```
controle-arquivos/
├── common/                      # Módulo compartilhado
│   ├── src/main/java/
│   │   └── com/controle/arquivos/common/
│   │       ├── client/          # Clientes SFTP e Vault
│   │       ├── config/          # Configurações
│   │       ├── domain/          # Entidades e enums
│   │       ├── logging/         # Logging estruturado
│   │       ├── repository/      # Repositórios JPA
│   │       └── service/         # Serviços compartilhados
│   └── src/main/resources/
│       ├── application.yml      # Configuração base
│       ├── application-local.yml
│       ├── application-dev.yml
│       ├── application-staging.yml
│       └── application-prod.yml
├── orchestrator/                # Pod Orquestrador
│   └── src/main/java/
│       └── com/controle/arquivos/orchestrator/
│           ├── config/          # Configurações específicas
│           ├── messaging/       # RabbitMQ publisher
│           ├── scheduler/       # Scheduler de coleta
│           └── service/         # Serviços de orquestração
├── processor/                   # Pod Processador
│   └── src/main/java/
│       └── com/controle/arquivos/processor/
│           ├── config/          # Configurações específicas
│           ├── health/          # Health checks
│           ├── messaging/       # RabbitMQ consumer
│           └── service/         # Serviços de processamento
├── integration-tests/           # Testes de integração
│   └── src/test/java/
│       └── com/controle/arquivos/integration/
├── k8s/                         # Manifests Kubernetes
│   ├── orchestrator-deployment.yaml
│   ├── processor-deployment.yaml
│   ├── configmap-*.yaml
│   └── secrets-template.yaml
├── scripts/                     # Scripts SQL e utilitários
│   └── ddl/
├── docs/                        # Documentação adicional
│   ├── CONFIGURATION.md
│   ├── OPERATIONS.md
│   └── DEVELOPMENT.md
├── docker-compose.yml           # Ambiente local
├── pom.xml                      # POM parent
└── README.md                    # Este arquivo
```

## Testes

### Testes Unitários

```bash
mvn test
```

### Testes de Propriedade (jqwik)

```bash
mvn test -Dtest="*PropertyTest"
```

### Testes de Integração

```bash
mvn verify
```

Ou teste específico:

```bash
mvn verify -Dit.test=EndToEndFlowIntegrationTest
```

### Cobertura de Código

```bash
mvn clean verify
# Relatório em: target/site/jacoco/index.html
```

## Deployment

### Kubernetes

Consulte [k8s/README.md](k8s/README.md) para instruções detalhadas de deployment.

Resumo:

```bash
# 1. Criar secrets
kubectl create secret generic controle-arquivos-secrets \
  --from-literal=db-password='...' \
  --from-literal=rabbitmq-password='...'

# 2. Aplicar ConfigMap
kubectl apply -f k8s/configmap-prod.yaml

# 3. Deploy
kubectl apply -f k8s/orchestrator-deployment.yaml
kubectl apply -f k8s/processor-deployment.yaml
```

## Configuração

### Perfis Spring Boot

- **local**: Desenvolvimento local com Docker Compose
- **dev**: Ambiente de desenvolvimento
- **staging**: Ambiente de homologação
- **prod**: Ambiente de produção

### Variáveis de Ambiente Principais

| Variável | Descrição | Exemplo |
|----------|-----------|---------|
| `SPRING_PROFILES_ACTIVE` | Perfil ativo | `prod` |
| `DB_URL` | URL do banco Oracle | `jdbc:oracle:thin:@...` |
| `DB_USERNAME` | Usuário do banco | `app_user` |
| `DB_PASSWORD` | Senha do banco | `***` |
| `RABBITMQ_HOST` | Host do RabbitMQ | `rabbitmq.example.com` |
| `RABBITMQ_USERNAME` | Usuário RabbitMQ | `app_user` |
| `RABBITMQ_PASSWORD` | Senha RabbitMQ | `***` |
| `VAULT_URI` | URI do Vault | `https://vault.example.com` |
| `VAULT_TOKEN` | Token do Vault | `hvs.***` |
| `AWS_REGION` | Região AWS | `us-east-1` |
| `AWS_S3_BUCKET` | Bucket S3 | `controle-arquivos` |

Consulte [docs/CONFIGURATION.md](docs/CONFIGURATION.md) para documentação completa.

## Monitoramento

### Health Checks

```bash
# Orquestrador
curl http://localhost:8080/actuator/health

# Processador
curl http://localhost:8081/actuator/health
```

### Métricas Prometheus

```bash
curl http://localhost:8081/actuator/prometheus
```

### Logs Estruturados

Logs são gerados em formato JSON com campos:
- `timestamp`: ISO 8601
- `level`: INFO, WARN, ERROR
- `logger`: Nome do logger
- `message`: Mensagem
- `context`: Contexto adicional (correlationId, fileName, etc.)

Exemplo:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "ProcessadorService",
  "message": "Arquivo processado com sucesso",
  "context": {
    "correlationId": "abc-123-def",
    "fileName": "CIELO_20240115.txt",
    "cliente": "CLIENTE_001",
    "layout": "LAYOUT_EDI_V1"
  }
}
```

## Troubleshooting

### Problema: Orquestrador não coleta arquivos

**Verificações**:
1. Scheduler está habilitado? (`app.scheduler.enabled=true`)
2. Credenciais SFTP estão corretas no Vault?
3. Servidores SFTP estão acessíveis?
4. Logs do Orquestrador mostram erros?

```bash
kubectl logs -l component=orchestrator -f
```

### Problema: Processador não consome mensagens

**Verificações**:
1. RabbitMQ está acessível?
2. Fila existe e tem mensagens?
3. Credenciais RabbitMQ estão corretas?
4. Logs do Processador mostram erros?

```bash
kubectl logs -l component=processor -f
```

### Problema: Cliente não identificado

**Verificações**:
1. Regras de identificação estão cadastradas?
2. Regras estão ativas (`flg_active=true`)?
3. Nome do arquivo corresponde às regras?
4. Logs mostram quais regras foram aplicadas?

Consulte [docs/OPERATIONS.md](docs/OPERATIONS.md) para guia completo de troubleshooting.

## Contribuindo

### Adicionando Novo Critério de Identificação

Consulte [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) seção "Adicionando Novos Critérios".

### Adicionando Novo Destino

Consulte [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) seção "Adicionando Novos Destinos".

### Padrões de Código

- Java 17 features
- Lombok para reduzir boilerplate
- Spring Boot best practices
- Logs estruturados em JSON
- Testes unitários + property-based tests
- Cobertura mínima: 80% linha, 75% branch

## Documentação Adicional

- [Configuração Detalhada](docs/CONFIGURATION.md)
- [Guia de Operações](docs/OPERATIONS.md)
- [Guia de Desenvolvimento](docs/DEVELOPMENT.md)
- [Deployment Kubernetes](k8s/README.md)
- [Testes de Integração](integration-tests/README.md)

## Licença

[Especificar licença]

## Suporte

Para questões ou problemas:
- Criar issue no repositório
- Contatar equipe de desenvolvimento
- Consultar documentação em `docs/`

