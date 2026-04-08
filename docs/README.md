# 📚 Índice de Documentação

Guia completo de toda a documentação do projeto Controle de Arquivos EDI.

## 🎯 Início Rápido

Novo no projeto? Comece aqui:

1. **[../README.md](../README.md)** - Visão geral do projeto
2. **[makefile/cheatsheet.md](makefile/cheatsheet.md)** - Comandos essenciais
3. **[setup/docker.md](setup/docker.md)** - Setup do ambiente

## 📖 Documentação por Categoria

### 🛠️ Makefile e Ferramentas

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [makefile/cheatsheet.md](makefile/cheatsheet.md) | Referência rápida de comandos | Consulta rápida diária |
| [makefile/guide.md](makefile/guide.md) | Guia completo do Makefile | Aprender todos os comandos |
| [makefile/examples.md](makefile/examples.md) | Exemplos práticos e cenários | Ver casos de uso reais |
| [makefile/workflows.md](makefile/workflows.md) | Workflows e fluxos de trabalho | Entender processos |
| [makefile/migration.md](makefile/migration.md) | Migração dos scripts .sh | Entender a transição |
| [makefile/summary.md](makefile/summary.md) | Sumário da implementação | Visão geral técnica |

### 🐳 Docker e Infraestrutura

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [setup/docker.md](setup/docker.md) | Configuração do Docker Compose | Setup inicial do ambiente |
| [../docker-compose.yml](../docker-compose.yml) | Definição dos serviços | Referência de configuração |

### 🧪 Testes

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [testing/e2e.md](testing/e2e.md) | Implementação de testes E2E | Entender testes E2E |
| [testing/run-e2e.md](testing/run-e2e.md) | Como executar testes E2E | Executar testes |
| [../commons/src/test/java/com/concil/edi/commons/e2e/README.md](../commons/src/test/java/com/concil/edi/commons/e2e/README.md) | Documentação dos testes E2E | Detalhes técnicos |

### 📋 Especificações

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [../.kiro/specs/controle-arquivos-edi/requirements.md](../.kiro/specs/controle-arquivos-edi/requirements.md) | Requisitos do sistema | Entender o que construir |
| [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md) | Design técnico | Entender como construir |
| [../.kiro/specs/controle-arquivos-edi/tasks.md](../.kiro/specs/controle-arquivos-edi/tasks.md) | Plano de implementação | Acompanhar progresso |

### 🏗️ Arquitetura

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [architecture/producer-flow.md](architecture/producer-flow.md) | Explicação do fluxo do Producer | Entender o Producer |

### ✨ Funcionalidades

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [features/layout-identification.md](features/layout-identification.md) | Identificação automática de layouts | Entender identificação de layouts |
| [features/customer-identification.md](features/customer-identification.md) | Identificação automática de clientes | Entender identificação de clientes |

### 🔍 Análises e Relatórios

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [reports/verification.md](reports/verification.md) | Relatório de verificação | Ver status do projeto |

### ⚙️ Configurações

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [../.kiro/steering/tech.md](../.kiro/steering/tech.md) | Stack tecnológica | Referência de tecnologias |
| [../.kiro/steering/structure.md](../.kiro/steering/structure.md) | Estrutura do projeto | Entender organização |
| [../.kiro/steering/product.md](../.kiro/steering/product.md) | Visão de produto | Entender o negócio |

## 🎓 Guias de Aprendizado

### Para Novos Desenvolvedores

1. Leia o [../README.md](../README.md) para visão geral
2. Configure o ambiente com [setup/docker.md](setup/docker.md)
3. Aprenda os comandos com [makefile/cheatsheet.md](makefile/cheatsheet.md)
4. Entenda o negócio com [../.kiro/steering/product.md](../.kiro/steering/product.md)
5. Veja a arquitetura em [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md)

### Para Desenvolvimento

1. Use [makefile/guide.md](makefile/guide.md) como referência
2. Consulte [makefile/examples.md](makefile/examples.md) para cenários
3. Execute testes com [testing/run-e2e.md](testing/run-e2e.md)
4. Veja o fluxo em [architecture/producer-flow.md](architecture/producer-flow.md)

### Para Troubleshooting

1. Verifique [makefile/examples.md](makefile/examples.md) seção Troubleshooting
2. Consulte [setup/docker.md](setup/docker.md) para problemas de infraestrutura
3. Use `make status` e `make logs` para diagnóstico

## 📊 Documentação por Papel

### 👨‍💻 Desenvolvedor

**Essenciais:**
- [../README.md](../README.md)
- [makefile/cheatsheet.md](makefile/cheatsheet.md)
- [makefile/guide.md](makefile/guide.md)
- [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md)

**Úteis:**
- [makefile/examples.md](makefile/examples.md)
- [setup/docker.md](setup/docker.md)
- [testing/e2e.md](testing/e2e.md)

### 🧪 QA / Tester

**Essenciais:**
- [testing/run-e2e.md](testing/run-e2e.md)
- [testing/e2e.md](testing/e2e.md)
- [makefile/cheatsheet.md](makefile/cheatsheet.md)

**Úteis:**
- [makefile/examples.md](makefile/examples.md)
- [../.kiro/specs/controle-arquivos-edi/requirements.md](../.kiro/specs/controle-arquivos-edi/requirements.md)

### 🏗️ DevOps / SRE

**Essenciais:**
- [setup/docker.md](setup/docker.md)
- [../docker-compose.yml](../docker-compose.yml)
- [makefile/guide.md](makefile/guide.md)

**Úteis:**
- [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md)
- [reports/verification.md](reports/verification.md)

### 📋 Product Owner / Gerente

**Essenciais:**
- [../README.md](../README.md)
- [../.kiro/steering/product.md](../.kiro/steering/product.md)
- [../.kiro/specs/controle-arquivos-edi/requirements.md](../.kiro/specs/controle-arquivos-edi/requirements.md)

**Úteis:**
- [../.kiro/specs/controle-arquivos-edi/tasks.md](../.kiro/specs/controle-arquivos-edi/tasks.md)
- [reports/verification.md](reports/verification.md)

### 🏛️ Arquiteto

**Essenciais:**
- [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md)
- [../.kiro/steering/tech.md](../.kiro/steering/tech.md)
- [../.kiro/steering/structure.md](../.kiro/steering/structure.md)

**Úteis:**
- [setup/docker.md](setup/docker.md)
- [architecture/producer-flow.md](architecture/producer-flow.md)

## 🔍 Busca Rápida

### Comandos Make

- **Iniciar sistema**: [makefile/cheatsheet.md](makefile/cheatsheet.md#comandos-essenciais)
- **Build e testes**: [makefile/guide.md](makefile/guide.md#build--test)
- **Logs e debug**: [makefile/guide.md](makefile/guide.md#utilities)
- **Exemplos práticos**: [makefile/examples.md](makefile/examples.md)

### Configuração

- **Docker Compose**: [setup/docker.md](setup/docker.md)
- **Variáveis de ambiente**: [../README.md](../README.md#configuração)
- **Stack tecnológica**: [../.kiro/steering/tech.md](../.kiro/steering/tech.md)

### Testes

- **Executar E2E**: [testing/run-e2e.md](testing/run-e2e.md)
- **Implementação E2E**: [testing/e2e.md](testing/e2e.md)
- **Comandos de teste**: [makefile/guide.md](makefile/guide.md#build--test)

### Arquitetura

- **Design técnico**: [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md)
- **Estrutura do projeto**: [../.kiro/steering/structure.md](../.kiro/steering/structure.md)
- **Fluxo do Producer**: [architecture/producer-flow.md](architecture/producer-flow.md)

## 📝 Convenções de Documentação

### Emojis Usados

- 🚀 Início rápido / Deploy
- 📦 Build / Compilação
- 🧪 Testes
- 🐳 Docker
- 🔧 Configuração / Ferramentas
- 📊 Relatórios / Análises
- 🔍 Busca / Investigação
- 📋 Especificações / Requisitos
- 🏗️ Arquitetura / Infraestrutura
- 💡 Dicas / Sugestões
- ⚠️ Avisos / Atenção
- ✅ Sucesso / Completo
- ❌ Erro / Falha

### Estrutura de Documentos

Todos os documentos seguem uma estrutura similar:
1. Título e descrição
2. Índice (se necessário)
3. Conteúdo principal
4. Exemplos práticos
5. Troubleshooting (se aplicável)
6. Referências

## 🔄 Atualizações

Este índice é atualizado sempre que:
- Novos documentos são adicionados
- Documentos existentes são reorganizados
- Mudanças significativas na estrutura

**Última atualização**: 29 de Março de 2025

## 📞 Suporte

Se você não encontrou o que procura:

1. Use `make help` para comandos
2. Consulte [MAKEFILE_EXAMPLES.md](MAKEFILE_EXAMPLES.md) para cenários
3. Veja [MAKEFILE_GUIDE.md](MAKEFILE_GUIDE.md) para referência completa
4. Abra uma issue no repositório

## 🎯 Próximos Passos

Dependendo do seu objetivo:

**Quero começar a desenvolver:**
→ [../README.md](../README.md) → [makefile/cheatsheet.md](makefile/cheatsheet.md) → [setup/docker.md](setup/docker.md)

**Quero entender a arquitetura:**
→ [../.kiro/steering/product.md](../.kiro/steering/product.md) → [../.kiro/specs/controle-arquivos-edi/design.md](../.kiro/specs/controle-arquivos-edi/design.md)

**Quero executar testes:**
→ [makefile/cheatsheet.md](makefile/cheatsheet.md) → [testing/run-e2e.md](testing/run-e2e.md)

**Quero fazer deploy:**
→ [makefile/examples.md](makefile/examples.md#7-preparação-para-deploy) → [setup/docker.md](setup/docker.md)
