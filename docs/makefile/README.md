# 🛠️ Makefile - Sistema de Controle de Arquivos EDI

## 📖 Sobre

Este Makefile substitui os scripts `.sh` anteriores e fornece uma interface unificada para gerenciar o projeto.

## 🚀 Início Rápido

```bash
# Ver todos os comandos
make help

# Iniciar o sistema
make up

# Parar o sistema
make down
```

## 📚 Documentação Disponível

| Documento | Descrição | Quando Usar |
|-----------|-----------|-------------|
| [MAKEFILE_CHEATSHEET.md](MAKEFILE_CHEATSHEET.md) | Referência rápida de 1 página | Consulta diária |
| [MAKEFILE_GUIDE.md](MAKEFILE_GUIDE.md) | Guia completo com todos os detalhes | Aprender tudo |
| [MAKEFILE_EXAMPLES.md](MAKEFILE_EXAMPLES.md) | Exemplos práticos e cenários reais | Ver casos de uso |
| [MIGRATION_TO_MAKEFILE.md](MIGRATION_TO_MAKEFILE.md) | Guia de migração dos scripts .sh | Entender mudanças |
| [MAKEFILE_SUMMARY.md](MAKEFILE_SUMMARY.md) | Sumário da implementação | Visão geral |
| [DOCS_INDEX.md](DOCS_INDEX.md) | Índice de toda documentação | Navegar docs |

## 🎯 Comandos Principais

### Docker Compose
```bash
make up              # Inicia todos os serviços
make down            # Para containers (mantém dados)
make down-volumes    # Para containers e remove dados
make restart         # Reinicia todos os serviços
make status          # Status dos containers
```

### Build & Test
```bash
make build           # Build sem testes
make test            # Testes unitários
make e2e             # Testes E2E
make rebuild         # Build + testes + restart
make full-rebuild    # Rebuild + imagens Docker
make clean           # Remove arquivos de build
```

### Logs
```bash
make logs            # Todos os logs
make logs-producer   # Logs do Producer
make logs-consumer   # Logs do Consumer
make logs-infra      # Logs da infraestrutura
```

### Acesso
```bash
make shell-producer  # Shell no Producer
make shell-consumer  # Shell no Consumer
make shell-oracle    # SQL*Plus no Oracle
```

### Monitoramento
```bash
make rabbitmq-queues # Lista filas RabbitMQ
make s3-list         # Lista objetos S3
```

## 💡 Exemplos de Uso

### Desenvolvimento Diário
```bash
make up              # Manhã: iniciar
make rebuild         # Após mudanças
make logs-producer   # Ver logs
make test            # Executar testes
make down            # Noite: parar
```

### Debugging
```bash
make status          # Ver status
make logs-consumer   # Ver logs específicos
make shell-producer  # Acessar container
make rabbitmq-queues # Verificar filas
```

### Teste Manual
```bash
make up              # Iniciar sistema
# Upload arquivo via SFTP
make logs            # Observar processamento
make s3-list         # Verificar resultado
```

## 🔄 Migração dos Scripts .sh

| Script Antigo | Comando Makefile |
|---------------|------------------|
| `./start.sh` | `make up` |
| `./stop.sh` | `make down` |
| `mvn clean package -DskipTests` | `make build` |
| `mvn test` | `make test` |
| `docker-compose logs -f producer` | `make logs-producer` |
| `docker-compose ps` | `make status` |

**Nota**: Os scripts `.sh` foram mantidos para compatibilidade, mas o uso do Makefile é recomendado.

## 🌐 URLs dos Serviços

Após `make up`, os serviços estarão disponíveis em:

- **Producer**: http://localhost:8080/actuator/health
- **Consumer**: http://localhost:8081/actuator/health
- **RabbitMQ**: http://localhost:15672 (admin/admin)
- **Oracle**: localhost:1521/XEPDB1 (edi_user/edi_pass)
- **LocalStack S3**: http://localhost:4566

### SFTP
- **Origin**: `sftp -P 2222 cielo@localhost` (senha: admin-1-2-3)
- **Destination**: `sftp -P 2223 internal@localhost` (senha: internal-pass)
- **Client**: `sftp -P 2224 client@localhost` (senha: client-pass)

## 🆘 Troubleshooting

### Problema: Containers não iniciam
```bash
make down
make up
```

### Problema: Testes falham
```bash
make down-volumes
make full-rebuild
```

### Problema: Porta em uso
```bash
make down
# Ou: lsof -i :8080 e kill -9 <PID>
```

### Problema: Serviços não ficam saudáveis
```bash
make logs-infra
# Verificar logs para identificar problema
```

## 📊 Estatísticas

- **23 comandos** implementados
- **9 arquivos** de documentação
- **~2.500 linhas** de documentação
- **10+ cenários** de uso documentados

## ✨ Vantagens sobre Scripts .sh

- ✅ Interface unificada (`make` ao invés de `./script.sh`)
- ✅ Auto-documentação (`make help`)
- ✅ Organização por categorias
- ✅ Cores e formatação no output
- ✅ Reutilização de comandos
- ✅ Validações automáticas
- ✅ Comandos compostos (rebuild, full-rebuild)
- ✅ Acesso rápido aos containers
- ✅ Monitoramento integrado

## 🎓 Próximos Passos

1. **Novo no projeto?**
   → Leia [MAKEFILE_CHEATSHEET.md](MAKEFILE_CHEATSHEET.md)

2. **Quer aprender tudo?**
   → Leia [MAKEFILE_GUIDE.md](MAKEFILE_GUIDE.md)

3. **Quer ver exemplos?**
   → Leia [MAKEFILE_EXAMPLES.md](MAKEFILE_EXAMPLES.md)

4. **Vindo dos scripts .sh?**
   → Leia [MIGRATION_TO_MAKEFILE.md](MIGRATION_TO_MAKEFILE.md)

5. **Quer navegar toda documentação?**
   → Leia [DOCS_INDEX.md](DOCS_INDEX.md)

## 📞 Suporte

- **Comandos**: `make help`
- **Cheatsheet**: [MAKEFILE_CHEATSHEET.md](MAKEFILE_CHEATSHEET.md)
- **Guia Completo**: [MAKEFILE_GUIDE.md](MAKEFILE_GUIDE.md)
- **Exemplos**: [MAKEFILE_EXAMPLES.md](MAKEFILE_EXAMPLES.md)
- **Índice**: [DOCS_INDEX.md](DOCS_INDEX.md)

## 🎉 Conclusão

O Makefile está pronto para uso! Execute `make help` para começar.

---

**Versão**: 1.0  
**Data**: 29 de Março de 2025  
**Status**: ✅ Completo e testado
