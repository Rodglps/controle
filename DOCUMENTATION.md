# 📚 Documentação do Projeto

> Toda a documentação foi organizada na pasta `docs/`

## 🚀 Acesso Rápido

### Índice Principal
📖 **[docs/README.md](docs/README.md)** - Índice completo de toda documentação

### Documentação por Categoria

| Categoria | Pasta | Descrição |
|-----------|-------|-----------|
| 🛠️ **Makefile** | [docs/makefile/](docs/makefile/) | Comandos, guias e exemplos do Makefile |
| ⚙️ **Setup** | [docs/setup/](docs/setup/) | Configuração do ambiente |
| 🧪 **Testes** | [docs/testing/](docs/testing/) | Testes E2E e unitários |
| 🏗️ **Arquitetura** | [docs/architecture/](docs/architecture/) | Arquitetura e fluxos do sistema |
| 📊 **Relatórios** | [docs/reports/](docs/reports/) | Relatórios e análises |

## 📘 Documentos Mais Acessados

### Para Começar
- [README.md](README.md) - Visão geral do projeto
- [docs/makefile/cheatsheet.md](docs/makefile/cheatsheet.md) - Comandos essenciais
- [docs/setup/docker.md](docs/setup/docker.md) - Setup do ambiente

### Para Desenvolver
- [docs/makefile/guide.md](docs/makefile/guide.md) - Guia completo do Makefile
- [docs/makefile/examples.md](docs/makefile/examples.md) - Exemplos práticos
- [docs/testing/run-e2e.md](docs/testing/run-e2e.md) - Executar testes

### Para Entender
- [docs/architecture/producer-flow.md](docs/architecture/producer-flow.md) - Fluxo do Producer
- [.kiro/specs/controle-arquivos-edi/design.md](.kiro/specs/controle-arquivos-edi/design.md) - Design técnico
- [.kiro/steering/product.md](.kiro/steering/product.md) - Visão de produto

## 🔍 Estrutura da Documentação

```
docs/
├── README.md                          # Índice principal
├── STRUCTURE.md                       # Estrutura detalhada
│
├── makefile/                          # Documentação do Makefile
│   ├── cheatsheet.md                  # Referência rápida ⭐
│   ├── guide.md                       # Guia completo
│   ├── examples.md                    # Exemplos práticos
│   ├── workflows.md                   # Workflows
│   ├── migration.md                   # Migração dos scripts .sh
│   └── summary.md                     # Sumário
│
├── setup/                             # Setup e configuração
│   └── docker.md                      # Docker setup ⭐
│
├── testing/                           # Testes
│   ├── e2e.md                         # Implementação E2E
│   └── run-e2e.md                     # Como executar ⭐
│
├── architecture/                      # Arquitetura
│   └── producer-flow.md               # Fluxo do Producer
│
└── reports/                           # Relatórios
    └── verification.md                # Relatório de verificação
```

## 🎯 Navegação por Objetivo

### Quero começar a desenvolver
1. [README.md](README.md) - Visão geral
2. [docs/makefile/cheatsheet.md](docs/makefile/cheatsheet.md) - Comandos
3. [docs/setup/docker.md](docs/setup/docker.md) - Setup
4. `make up` - Iniciar ambiente

### Quero entender a arquitetura
1. [.kiro/steering/product.md](.kiro/steering/product.md) - Visão de produto
2. [.kiro/specs/controle-arquivos-edi/design.md](.kiro/specs/controle-arquivos-edi/design.md) - Design
3. [docs/architecture/producer-flow.md](docs/architecture/producer-flow.md) - Fluxos

### Quero executar testes
1. [docs/makefile/cheatsheet.md](docs/makefile/cheatsheet.md) - Comandos
2. [docs/testing/run-e2e.md](docs/testing/run-e2e.md) - Como executar
3. `make test` ou `make e2e` - Executar

### Quero contribuir
1. [docs/makefile/guide.md](docs/makefile/guide.md) - Comandos completos
2. [docs/makefile/examples.md](docs/makefile/examples.md) - Exemplos
3. [.kiro/specs/controle-arquivos-edi/tasks.md](.kiro/specs/controle-arquivos-edi/tasks.md) - Tarefas

## 📊 Estatísticas

- **19 documentos** markdown na pasta docs/
- **5 categorias** organizadas
- **8 READMEs** para navegação
- **Raiz limpa** com apenas arquivos essenciais

## 🆘 Precisa de Ajuda?

### Comandos
```bash
make help                              # Ver todos os comandos
cat docs/makefile/cheatsheet.md        # Referência rápida
```

### Documentação
```bash
cat docs/README.md                     # Índice completo
cat docs/STRUCTURE.md                  # Estrutura detalhada
```

### Navegação
```bash
cd docs/makefile                       # Documentação do Makefile
cd docs/setup                          # Setup
cd docs/testing                        # Testes
```

## 🔗 Links Úteis

- [Índice Completo](docs/README.md)
- [Estrutura da Documentação](docs/STRUCTURE.md)
- [Resumo da Migração](docs/MIGRATION_SUMMARY.md)
- [Especificações](.kiro/specs/controle-arquivos-edi/)
- [Regras de Steering](.kiro/steering/)

---

**💡 Dica**: Comece pelo [docs/README.md](docs/README.md) para ver toda a documentação organizada!
