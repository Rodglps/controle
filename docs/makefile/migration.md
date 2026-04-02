# Migração de Scripts .sh para Makefile

Este documento explica a migração dos scripts shell para o Makefile e como usar os novos comandos.

## Motivação

O Makefile oferece várias vantagens sobre scripts .sh individuais:

✅ **Interface unificada**: Um único ponto de entrada (`make`) para todos os comandos  
✅ **Auto-documentação**: `make help` mostra todos os comandos disponíveis  
✅ **Organização**: Comandos agrupados por categoria  
✅ **Cores e formatação**: Output mais legível e profissional  
✅ **Reutilização**: Comandos podem chamar outros comandos  
✅ **Portabilidade**: Funciona em qualquer sistema com `make` instalado  
✅ **Extensibilidade**: Fácil adicionar novos comandos  

## Mapeamento de Comandos

### Scripts Antigos → Comandos Makefile

| Script Antigo | Comando Makefile Equivalente | Notas |
|---------------|------------------------------|-------|
| `./start.sh` | `make up` | Funcionalidade idêntica + melhor output |
| `./stop.sh` | `make down` | Funcionalidade idêntica |
| N/A | `make down-volumes` | Nova funcionalidade: remove dados |
| N/A | `make restart` | Nova funcionalidade: restart completo |
| `mvn clean package -DskipTests` | `make build` | Simplificado |
| `mvn test` | `make test` | Simplificado |
| N/A | `make e2e` | Nova funcionalidade: testes E2E |
| N/A | `make rebuild` | Nova funcionalidade: workflow completo |
| `docker-compose logs -f` | `make logs` | Simplificado |
| `docker-compose ps` | `make status` | Simplificado |

## Guia de Migração

### Para Desenvolvedores

Se você estava usando os scripts .sh, aqui está como migrar:

#### Antes (com scripts .sh)

```bash
# Iniciar sistema
./start.sh

# Parar sistema
./stop.sh

# Ver logs
docker-compose logs -f producer

# Build
mvn clean package -DskipTests

# Testes
mvn test
```

#### Depois (com Makefile)

```bash
# Iniciar sistema
make up

# Parar sistema
make down

# Ver logs
make logs-producer

# Build
make build

# Testes
make test
```

### Novos Workflows Disponíveis

O Makefile adiciona workflows que não existiam antes:

```bash
# Rebuild completo: build + testes + restart
make rebuild

# Rebuild incluindo imagens Docker
make full-rebuild

# Testes E2E
make e2e

# Acesso rápido aos containers
make shell-producer
make shell-consumer
make shell-oracle

# Monitoramento
make rabbitmq-queues
make s3-list
```

## Status dos Scripts Antigos

### Scripts Mantidos

Os scripts `.sh` foram **mantidos** para compatibilidade, mas o uso do Makefile é recomendado:

- ✅ `start.sh` - Mantido (use `make up`)
- ✅ `stop.sh` - Mantido (use `make down`)
- ✅ `scripts/localstack/init-s3.sh` - Mantido (usado pelo Docker)

### Recomendação

**Use o Makefile para desenvolvimento diário**. Os scripts .sh podem ser removidos no futuro.

## Vantagens Específicas

### 1. Help Integrado

```bash
# Antes: precisava abrir o script para ver o que faz
cat start.sh

# Depois: help automático
make help
```

### 2. Feedback Visual

O Makefile usa cores para melhor visualização:

- 🔵 Azul: Informações e títulos
- 🟢 Verde: Sucesso
- 🟡 Amarelo: Avisos
- 🔴 Vermelho: Erros

### 3. Comandos Compostos

```bash
# Antes: múltiplos comandos
mvn clean install
docker-compose down
docker-compose up -d

# Depois: um único comando
make rebuild
```

### 4. Validações Automáticas

O Makefile valida automaticamente:
- Docker está rodando
- Serviços estão saudáveis antes de continuar
- Comandos executaram com sucesso

### 5. Organização por Categoria

```bash
make help
```

Mostra comandos organizados em:
- General
- Docker Compose
- Build & Test
- Utilities
- Internal Helpers

## Exemplos de Uso

### Desenvolvimento Diário

```bash
# Manhã: iniciar ambiente
make up

# Durante o dia: rebuild após mudanças
make rebuild

# Ver logs
make logs-producer

# Tarde: executar testes
make test
make e2e

# Noite: parar ambiente
make down
```

### Debugging

```bash
# Ver status
make status

# Logs específicos
make logs-consumer

# Acessar container
make shell-producer

# Verificar RabbitMQ
make rabbitmq-queues

# Verificar S3
make s3-list
```

### CI/CD

```bash
# Build e testes
make build
make test

# Testes E2E
make up
make e2e
make down-volumes
```

## Troubleshooting

### "make: command not found"

```bash
# Ubuntu/Debian
sudo apt-get install make

# macOS
xcode-select --install

# Fedora/RHEL
sudo dnf install make
```

### Scripts .sh não funcionam mais

Os scripts ainda funcionam! Mas recomendamos usar o Makefile:

```bash
# Ainda funciona
./start.sh

# Recomendado
make up
```

### Quero adicionar um novo comando

Edite o `Makefile` e adicione:

```makefile
meu-comando: ## Descrição do comando
	@echo "Executando meu comando..."
	# seus comandos aqui
```

Depois execute:

```bash
make meu-comando
```

## Próximos Passos

1. ✅ Makefile criado e testado
2. ✅ Documentação atualizada (README.md, tech.md)
3. ✅ Guia de uso criado (MAKEFILE_GUIDE.md)
4. ✅ Guia de migração criado (este arquivo)
5. ⏭️ Opcional: Remover scripts .sh após período de transição
6. ⏭️ Opcional: Adicionar comandos específicos do projeto

## Feedback

Se você encontrar problemas ou tiver sugestões de melhorias para o Makefile, por favor:

1. Abra uma issue
2. Ou adicione diretamente no Makefile e documente aqui

## Referências

- [GNU Make Manual](https://www.gnu.org/software/make/manual/)
- [Makefile Tutorial](https://makefiletutorial.com/)
- [MAKEFILE_GUIDE.md](./MAKEFILE_GUIDE.md) - Guia completo de uso
