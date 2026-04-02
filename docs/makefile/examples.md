# Exemplos Práticos de Uso do Makefile

Este documento contém exemplos práticos e cenários reais de uso do Makefile.

## Cenários Comuns

### 1. Primeiro Uso do Projeto

```bash
# Clone o repositório
git clone <repo-url>
cd controle-arquivos-edi

# Veja os comandos disponíveis
make help

# Inicie o sistema completo
make up

# Aguarde a mensagem de sucesso e acesse:
# http://localhost:8080/actuator/health (Producer)
# http://localhost:8081/actuator/health (Consumer)
```

### 2. Desenvolvimento de Nova Feature

```bash
# Inicie o ambiente
make up

# Faça suas alterações no código...
# vim producer/src/main/java/...

# Rebuild e restart
make rebuild

# Verifique os logs
make logs-producer

# Execute testes
make test

# Execute testes E2E
make e2e

# Commit suas mudanças
git add .
git commit -m "feat: nova funcionalidade"
```

### 3. Debugging de Problema

```bash
# Verifique o status dos containers
make status

# Veja os logs do serviço com problema
make logs-consumer

# Acesse o container para investigar
make shell-consumer

# Dentro do container:
ls -la
cat /app/application.yml
exit

# Verifique o banco de dados
make shell-oracle

# No SQL*Plus:
SELECT * FROM file_origin WHERE status = 'ERRO';
exit

# Verifique as filas do RabbitMQ
make rabbitmq-queues

# Verifique arquivos no S3
make s3-list
```

### 4. Teste de Integração Manual

```bash
# Inicie o sistema
make up

# Em outro terminal, monitore os logs
make logs

# Em outro terminal, faça upload de arquivo teste
sftp -P 2222 cielo@localhost
# Senha: admin-1-2-3

# No prompt SFTP:
put test-file.csv upload/test-file.csv
ls upload/
exit

# Volte ao terminal de logs e observe o processamento

# Verifique se o arquivo chegou no S3
make s3-list

# Verifique o registro no banco
make shell-oracle
# SELECT * FROM file_origin ORDER BY created_at DESC;
```

### 5. Atualização de Dependências

```bash
# Edite o pom.xml
vim pom.xml

# Rebuild completo incluindo imagens Docker
make full-rebuild

# Teste se tudo funciona
make test
make e2e
```

### 6. Limpeza e Reset Completo

```bash
# Parar e remover tudo (containers + dados)
make down-volumes

# Limpar arquivos de build
make clean

# Rebuild do zero
make full-rebuild

# Verificar se está tudo ok
make status
make test
```

### 7. Preparação para Deploy

```bash
# Build completo com testes
make rebuild

# Execute todos os testes
make test

# Execute testes E2E
make e2e

# Se tudo passou, faça o deploy
# docker push ...
```

### 8. Investigação de Performance

```bash
# Inicie o sistema
make up

# Monitore logs em tempo real
make logs-consumer

# Em outro terminal, gere carga
# (script de teste ou upload manual)

# Acesse o container para ver recursos
make shell-consumer

# Dentro do container:
top
df -h
free -m
exit

# Verifique métricas do RabbitMQ
# Acesse: http://localhost:15672
# Ou via CLI:
make rabbitmq-queues
```

### 9. Desenvolvimento com Hot Reload

```bash
# Inicie apenas a infraestrutura
docker-compose up -d oracle rabbitmq localstack sftp-origin sftp-destination

# Execute o Producer localmente (com hot reload)
cd producer
mvn spring-boot:run

# Em outro terminal, execute o Consumer localmente
cd consumer
mvn spring-boot:run

# Faça alterações no código e o Spring Boot recarrega automaticamente

# Quando terminar, pare tudo
docker-compose down
```

### 10. Teste de Falha e Recovery

```bash
# Inicie o sistema
make up

# Simule falha do RabbitMQ
docker stop edi-rabbitmq

# Observe os logs
make logs-producer

# Reinicie o RabbitMQ
docker start edi-rabbitmq

# Observe a recuperação
make logs-producer
```

## Comandos Encadeados

### Build e Deploy Completo

```bash
make clean && make rebuild && make e2e && echo "✅ Pronto para deploy!"
```

### Reset e Restart

```bash
make down-volumes && make full-rebuild
```

### Monitoramento Completo

```bash
# Terminal 1: Logs do Producer
make logs-producer

# Terminal 2: Logs do Consumer
make logs-consumer

# Terminal 3: Status
watch -n 5 'make status'
```

## Aliases Úteis (Opcional)

Adicione ao seu `~/.bashrc` ou `~/.zshrc`:

```bash
# Aliases para o projeto EDI
alias edi-up='cd ~/projetos/controle-arquivos-edi && make up'
alias edi-down='cd ~/projetos/controle-arquivos-edi && make down'
alias edi-logs='cd ~/projetos/controle-arquivos-edi && make logs'
alias edi-status='cd ~/projetos/controle-arquivos-edi && make status'
alias edi-rebuild='cd ~/projetos/controle-arquivos-edi && make rebuild'
alias edi-test='cd ~/projetos/controle-arquivos-edi && make test'
alias edi-e2e='cd ~/projetos/controle-arquivos-edi && make e2e'
```

Depois, de qualquer lugar:

```bash
edi-up      # Inicia o sistema
edi-logs    # Vê os logs
edi-down    # Para o sistema
```

## Scripts de Automação

### Script de Teste Completo

Crie `test-all.sh`:

```bash
#!/bin/bash
set -e

echo "🧪 Executando suite completa de testes..."

echo "1️⃣ Limpando ambiente..."
make down-volumes

echo "2️⃣ Build do projeto..."
make build

echo "3️⃣ Testes unitários..."
make test

echo "4️⃣ Iniciando docker-compose..."
make up

echo "5️⃣ Aguardando 30 segundos..."
sleep 30

echo "6️⃣ Testes E2E..."
make e2e

echo "7️⃣ Parando ambiente..."
make down

echo "✅ Todos os testes passaram!"
```

Execute:

```bash
chmod +x test-all.sh
./test-all.sh
```

### Script de Deploy

Crie `deploy.sh`:

```bash
#!/bin/bash
set -e

echo "🚀 Preparando deploy..."

# Testes
./test-all.sh

# Build das imagens
echo "📦 Building Docker images..."
docker-compose build producer consumer

# Tag das imagens
echo "🏷️  Tagging images..."
docker tag edi-producer:latest registry.example.com/edi-producer:latest
docker tag edi-consumer:latest registry.example.com/edi-consumer:latest

# Push
echo "⬆️  Pushing images..."
docker push registry.example.com/edi-producer:latest
docker push registry.example.com/edi-consumer:latest

echo "✅ Deploy preparado!"
```

## Integração com CI/CD

### GitHub Actions

```yaml
name: CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 21
      uses: actions/setup-java@v2
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Build
      run: make build
    
    - name: Unit Tests
      run: make test
    
    - name: Start Docker Compose
      run: make up
    
    - name: E2E Tests
      run: make e2e
    
    - name: Cleanup
      run: make down-volumes
```

### GitLab CI

```yaml
stages:
  - build
  - test
  - e2e

build:
  stage: build
  script:
    - make build

test:
  stage: test
  script:
    - make test

e2e:
  stage: e2e
  script:
    - make up
    - make e2e
    - make down-volumes
```

## Dicas e Truques

### 1. Execução Paralela de Logs

```bash
# Terminal multiplexer (tmux)
tmux new-session \; \
  split-window -h \; \
  split-window -v \; \
  select-pane -t 0 \; \
  send-keys 'make logs-producer' C-m \; \
  select-pane -t 1 \; \
  send-keys 'make logs-consumer' C-m \; \
  select-pane -t 2 \; \
  send-keys 'watch -n 5 make status' C-m
```

### 2. Verificação Rápida de Saúde

```bash
# Crie um alias
alias edi-health='curl -s http://localhost:8080/actuator/health && curl -s http://localhost:8081/actuator/health'

# Use
edi-health
```

### 3. Backup de Dados

```bash
# Backup dos volumes
docker run --rm -v edi-oracle-data:/data -v $(pwd):/backup ubuntu tar czf /backup/oracle-backup.tar.gz /data

# Restore
docker run --rm -v edi-oracle-data:/data -v $(pwd):/backup ubuntu tar xzf /backup/oracle-backup.tar.gz -C /
```

### 4. Monitoramento de Recursos

```bash
# Recursos dos containers
docker stats edi-producer edi-consumer edi-oracle edi-rabbitmq
```

### 5. Limpeza de Espaço em Disco

```bash
# Limpar imagens não utilizadas
docker image prune -a

# Limpar volumes não utilizados
docker volume prune

# Limpar tudo
docker system prune -a --volumes
```

## Troubleshooting Comum

### Problema: "Port already in use"

```bash
# Encontre o processo usando a porta
lsof -i :8080

# Mate o processo
kill -9 <PID>

# Ou pare todos os containers
make down
```

### Problema: "Out of memory"

```bash
# Aumente memória do Docker
# Docker Desktop > Settings > Resources > Memory

# Ou reduza containers rodando
docker-compose up -d oracle rabbitmq localstack
# Execute producer/consumer localmente
```

### Problema: "Tests failing"

```bash
# Reset completo
make down-volumes
make clean
make full-rebuild

# Execute testes novamente
make test
make e2e
```

## Recursos Adicionais

- [MAKEFILE_GUIDE.md](./MAKEFILE_GUIDE.md) - Guia completo
- [MIGRATION_TO_MAKEFILE.md](./MIGRATION_TO_MAKEFILE.md) - Guia de migração
- [README.md](./README.md) - Documentação principal
- [DOCKER_SETUP.md](./DOCKER_SETUP.md) - Setup do Docker
