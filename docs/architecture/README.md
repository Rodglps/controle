# 🏗️ Arquitetura

Documentação sobre a arquitetura do sistema.

## 📄 Documentos Disponíveis

### [producer-flow.md](producer-flow.md)
Explicação detalhada do fluxo do Producer:
- Fluxo de coleta de arquivos
- Interação com SFTP
- Publicação de mensagens no RabbitMQ
- Atualização de status no banco de dados
- Tratamento de erros

## 🎯 Visão Geral

O sistema é composto por dois módulos principais:

- **Producer**: Coleta arquivos de servidores SFTP externos
- **Consumer**: Processa mensagens e transfere arquivos para destinos

## 📚 Documentos Relacionados

- [../../.kiro/specs/controle-arquivos-edi/design.md](../../.kiro/specs/controle-arquivos-edi/design.md) - Design técnico completo
- [../../.kiro/steering/structure.md](../../.kiro/steering/structure.md) - Estrutura do projeto
- [../../.kiro/steering/tech.md](../../.kiro/steering/tech.md) - Stack tecnológica
- [../README.md](../README.md) - Índice geral
