# Controle de Arquivos

Sistema de entrada de dados para conciliação de cartão.

## Estrutura

```
controle-arquivos/
├── common/          # Entidades JPA, repositórios, serviços compartilhados
├── orchestrator/    # Pod 1: coleta arquivos SFTP e publica no RabbitMQ
├── processor/       # Pod 2: consome fila, identifica cliente/layout, faz upload
├── infra/
│   ├── ddl/         # Scripts Oracle (sequences, tables, seed)
│   └── localstack/  # Init scripts LocalStack (S3)
├── k8s/             # Manifests Kubernetes
└── docker-compose.yml
```

## Subir ambiente local

```bash
# Build dos módulos
mvn clean package -DskipTests

# Subir infraestrutura + aplicações
docker-compose up -d

# Acompanhar logs
docker-compose logs -f orchestrator processor
```

## Serviços locais

| Serviço       | URL / Porta                        |
|---------------|------------------------------------|
| Oracle XE     | localhost:1521 (XEPDB1)            |
| RabbitMQ UI   | http://localhost:15672 (guest/guest)|
| LocalStack S3 | http://localhost:4566              |
| SFTP          | localhost:2222 (user/password)     |
| Orchestrator  | http://localhost:8080/actuator     |
| Processor     | http://localhost:8081/actuator     |

## Kubernetes

```bash
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/secret.yml
kubectl apply -f k8s/rabbitmq-deployment.yml
kubectl apply -f k8s/orchestrator-deployment.yml
kubectl apply -f k8s/processor-deployment.yml
```

## Executar testes

```bash
mvn test
```
