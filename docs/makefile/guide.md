# Guia de Uso do Makefile

Este documento fornece uma referência rápida para todos os comandos disponíveis no Makefile do projeto.

## Início Rápido

```bash
# Ver todos os comandos disponíveis
make help

# Iniciar o sistema completo
make up

# Parar o sistema
make down
```

## Comandos por Categoria

### 🚀 Docker Compose

| Comando | Descrição |
|---------|-----------|
| `make up` | Inicia todos os serviços do docker-compose |
| `make down` | Para e remove todos os containers (mantém volumes) |
| `make down-volumes` | Para e remove todos os containers e volumes (limpa dados) |
| `make restart` | Reinicia todos os serviços |

### 📦 Build & Test

| Comando | Descrição |
|---------|-----------|
| `make build` | Compila o projeto (sem testes) |
| `make test` | Executa testes unitários |
| `make e2e` | Executa testes E2E (requer docker-compose rodando) |
| `make rebuild` | Build completo + testes unitários + reinicia docker-compose |
| `make full-rebuild` | Build completo incluindo reconstrução das imagens Docker |

### 🔧 Utilitários

| Comando | Descrição |
|---------|-----------|
| `make logs` | Exibe logs de todos os serviços (use CTRL+C para sair) |
| `make logs-producer` | Exibe logs do Producer |
| `make logs-consumer` | Exibe logs do Consumer |
| `make logs-infra` | Exibe logs da infraestrutura (Oracle, RabbitMQ, LocalStack) |
| `make status` | Exibe status de todos os containers |
| `make clean` | Remove arquivos de build do Maven |

### 🐚 Acesso aos Containers

| Comando | Descrição |
|---------|-----------|
| `make shell-producer` | Abre shell no container do Producer |
| `make shell-consumer` | Abre shell no container do Consumer |
| `make shell-oracle` | Abre SQL*Plus no Oracle |

### 📊 Monitoramento

| Comando | Descrição |
|---------|-----------|
| `make rabbitmq-queues` | Lista filas do RabbitMQ |
| `make s3-list` | Lista buckets e objetos no LocalStack S3 |

## Fluxos de Trabalho Comuns

### Desenvolvimento Diário

```bash
# 1. Iniciar o ambiente
make up

# 2. Ver logs em tempo real
make logs-producer
# ou
make logs-consumer

# 3. Fazer alterações no código...

# 4. Rebuild e restart
make rebuild

# 5. Executar testes E2E
make e2e

# 6. Parar ao final do dia
make down
```

### Desenvolvimento com Mudanças no Dockerfile

```bash
# Rebuild completo incluindo imagens Docker
make full-rebuild
```

### Debugging

```bash
# Ver status dos containers
make status

# Ver logs de um serviço específico
make logs-producer

# Acessar shell do container
make shell-producer

# Verificar filas do RabbitMQ
make rabbitmq-queues

# Verificar arquivos no S3
make s3-list

# Acessar banco de dados
make shell-oracle
```

### Limpeza Completa

```bash
# Parar e remover containers e dados
make down-volumes

# Limpar arquivos de build
make clean

# Rebuild completo do zero
make full-rebuild
```

### Testes

```bash
# Testes unitários
make test

# Testes E2E (certifique-se que docker-compose está rodando)
make up
make e2e
```

## URLs dos Serviços

Após executar `make up`, os seguintes serviços estarão disponíveis:

### Aplicações

- **Producer Health**: http://localhost:8080/actuator/health
- **Consumer Health**: http://localhost:8081/actuator/health

### Infraestrutura

- **RabbitMQ Management**: http://localhost:15672
  - Usuário: `admin`
  - Senha: `admin`

- **Oracle Database**: `localhost:1521/XEPDB1`
  - Usuário: `edi_user`
  - Senha: `edi_pass`

- **LocalStack S3**: http://localhost:4566

### Servidores SFTP

- **Origin**: `sftp://cielo@localhost:2222`
  - Senha: `admin-1-2-3`

- **Destination**: `sftp://internal@localhost:2223`
  - Senha: `internal-pass`

- **Client**: `sftp://client@localhost:2224`
  - Senha: `client-pass`

## Testando o Sistema

### Upload Manual de Arquivo via SFTP

```bash
# Conectar ao SFTP origin
sftp -P 2222 cielo@localhost

# Fazer upload de um arquivo
put seu-arquivo.csv upload/seu-arquivo.csv

# Sair
exit
```

### Verificar Processamento

```bash
# Ver logs do Producer
make logs-producer

# Ver logs do Consumer
make logs-consumer

# Verificar arquivo no S3
make s3-list
```

## Troubleshooting

### Containers não iniciam

```bash
# Verificar status
make status

# Ver logs de infraestrutura
make logs-infra

# Parar tudo e tentar novamente
make down
make up
```

### Serviços não ficam saudáveis

```bash
# Ver logs específicos
make logs-infra

# Verificar se Docker tem recursos suficientes
docker info

# Tentar rebuild completo
make down-volumes
make full-rebuild
```

### Testes E2E falham

```bash
# Certificar que docker-compose está rodando
make status

# Verificar logs
make logs

# Reiniciar serviços
make restart

# Executar testes novamente
make e2e
```

### Limpar tudo e começar do zero

```bash
# Parar e remover tudo
make down-volumes

# Limpar build
make clean

# Rebuild completo
make full-rebuild
```

## Dicas

1. **Use `make help`** sempre que esquecer um comando
2. **Use `make status`** para verificar rapidamente o estado dos containers
3. **Use `make logs`** sem argumentos para ver todos os logs simultaneamente
4. **Use CTRL+C** para sair da visualização de logs
5. **Use `make rebuild`** após mudanças no código (mais rápido que `full-rebuild`)
6. **Use `make full-rebuild`** apenas quando mudar Dockerfiles ou dependências
7. **Use `make down-volumes`** com cuidado - remove todos os dados!

## Comparação com Scripts .sh

| Script Antigo | Comando Makefile | Vantagens |
|---------------|------------------|-----------|
| `./start.sh` | `make up` | Mais curto, colorido, organizado |
| `./stop.sh` | `make down` | Mais opções (down-volumes, restart) |
| N/A | `make rebuild` | Workflow completo automatizado |
| N/A | `make e2e` | Execução simplificada de testes |
| N/A | `make logs-*` | Acesso rápido a logs específicos |

## Contribuindo

Ao adicionar novos comandos ao Makefile:

1. Adicione comentários `##` para aparecerem no help
2. Use categorias `##@` para organizar
3. Adicione cores para melhor visualização
4. Documente neste guia
5. Teste o comando antes de commitar
