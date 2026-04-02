# Makefile Cheatsheet - Referência Rápida

## Comandos Essenciais

```bash
make help          # Ver todos os comandos
make up            # Iniciar sistema
make down          # Parar sistema
make rebuild       # Build + testes + restart
make logs          # Ver logs
make status        # Status dos containers
```

## Início Rápido

```bash
# Primeira vez
make up

# Desenvolvimento
make rebuild

# Parar
make down
```

## Docker Compose

| Comando | O que faz |
|---------|-----------|
| `make up` | Inicia tudo |
| `make down` | Para (mantém dados) |
| `make down-volumes` | Para e remove dados |
| `make restart` | Reinicia |
| `make status` | Status |

## Build & Test

| Comando | O que faz |
|---------|-----------|
| `make build` | Build sem testes |
| `make test` | Testes unitários |
| `make e2e` | Testes E2E |
| `make rebuild` | Build + testes + restart |
| `make full-rebuild` | Rebuild + imagens Docker |
| `make clean` | Limpa build |

## Logs

| Comando | O que faz |
|---------|-----------|
| `make logs` | Todos os logs |
| `make logs-producer` | Logs do Producer |
| `make logs-consumer` | Logs do Consumer |
| `make logs-infra` | Logs da infra |

## Acesso

| Comando | O que faz |
|---------|-----------|
| `make shell-producer` | Shell no Producer |
| `make shell-consumer` | Shell no Consumer |
| `make shell-oracle` | SQL*Plus |

## Monitoramento

| Comando | O que faz |
|---------|-----------|
| `make rabbitmq-queues` | Filas RabbitMQ |
| `make s3-list` | Objetos S3 |

## URLs

- Producer: http://localhost:8080/actuator/health
- Consumer: http://localhost:8081/actuator/health
- RabbitMQ: http://localhost:15672 (admin/admin)
- Oracle: localhost:1521/XEPDB1 (edi_user/edi_pass)
- LocalStack: http://localhost:4566

## SFTP

- Origin: `sftp -P 2222 cielo@localhost` (senha: admin-1-2-3)
- Destination: `sftp -P 2223 internal@localhost` (senha: internal-pass)
- Client: `sftp -P 2224 client@localhost` (senha: client-pass)

## Workflows

### Desenvolvimento Diário
```bash
make up → código → make rebuild → make test → make down
```

### Debugging
```bash
make status → make logs-producer → make shell-producer
```

### Reset Completo
```bash
make down-volumes → make clean → make full-rebuild
```

### Teste Manual
```bash
make up → sftp -P 2222 cielo@localhost → make logs → make s3-list
```

## Dicas

- Use `make` ou `make help` para ver comandos
- Use `CTRL+C` para sair de logs
- Use `make rebuild` após mudanças no código
- Use `make full-rebuild` após mudanças no Dockerfile
- Use `make down-volumes` para limpar dados

## Troubleshooting

```bash
# Não inicia
make down && make up

# Testes falham
make down-volumes && make full-rebuild

# Porta em uso
make down

# Ver o que está errado
make status && make logs-infra
```

## Comparação com Scripts

| Antes | Agora |
|-------|-------|
| `./start.sh` | `make up` |
| `./stop.sh` | `make down` |
| `mvn clean package -DskipTests` | `make build` |
| `mvn test` | `make test` |
| `docker-compose logs -f producer` | `make logs-producer` |
| `docker-compose ps` | `make status` |

## Mais Informações

- [MAKEFILE_GUIDE.md](./MAKEFILE_GUIDE.md) - Guia completo
- [MAKEFILE_EXAMPLES.md](./MAKEFILE_EXAMPLES.md) - Exemplos práticos
- [MIGRATION_TO_MAKEFILE.md](./MIGRATION_TO_MAKEFILE.md) - Migração
