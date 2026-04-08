# Changelog: Alteração do Fluxo de Identificação de Layouts

**Data**: 2026-04-04  
**Versão**: 1.1.0

## Resumo da Alteração

Alterado o comportamento do sistema quando um arquivo não tem seu layout identificado. Ao invés de usar um status especial `LAYOUT_NAO_IDENTIFICADO`, agora utilizamos um **layout especial com `idt_layout = 0`** chamado `SEM_IDENTIFICACAO`.

## Motivação

A abordagem anterior criava um status adicional que não refletia a realidade: o arquivo foi transferido com sucesso, apenas não teve seu layout identificado. A nova abordagem é mais limpa e consistente:

- **Status CONCLUIDO** indica que a transferência foi bem-sucedida (o que é verdade)
- **idt_layout = 0** indica que o layout não foi identificado (necessita revisão manual)

## Alterações Técnicas

### 1. Banco de Dados

#### Tabela `layout`
- ✅ Adicionado registro com `idt_layout = 0`:
  - `cod_layout = 'SEM_IDENTIFICACAO'`
  - `idt_acquirer = 0`
  - `des_file_type = 'TXT'`
  - `des_transaction_type = 'AUXILIAR'`
  - `des_distribution_type = 'SAZONAL'`

#### Tabela `file_origin`
- ✅ Removido valor `LAYOUT_NAO_IDENTIFICADO` da constraint de `des_status`
- ✅ Constraint agora aceita apenas: `EM_ESPERA`, `PROCESSAMENTO`, `CONCLUIDO`, `ERRO`

### 2. Código Java

#### Enum `Status`
- ✅ Removido valor `LAYOUT_NAO_IDENTIFICADO`
- ✅ Mantidos apenas: `EM_ESPERA`, `PROCESSAMENTO`, `CONCLUIDO`, `ERRO`

#### `FileTransferListener`
- ✅ Alterado comportamento quando `layoutId == null`:
  - **Antes**: Marcava status como `LAYOUT_NAO_IDENTIFICADO`
  - **Depois**: Define `layoutId = 0` e marca status como `CONCLUIDO`

### 3. Scripts DDL

#### `06_create_layout_tables.sql`
- ✅ Sem alterações (já suporta idt_layout = 0)

#### `07_insert_layout_examples.sql`
- ✅ Adicionado INSERT do layout 0 (SEM_IDENTIFICACAO)

#### `04_create_table_file_origin.sql`
- ✅ Atualizada constraint de `des_status` (removido LAYOUT_NAO_IDENTIFICADO)

### 4. Documentação

#### Arquivos Atualizados:
- ✅ `README.md` - Seção "Status de Processamento"
- ✅ `.kiro/steering/database-scripts.md` - Status e queries
- ✅ `scripts/ddl/README.md` - Documentação dos scripts DDL
- ✅ `.kiro/specs/identificacao_layouts/tasks.md` - Fluxo de identificação
- ✅ `commons/src/main/java/com/concil/edi/commons/repository/FileOriginRepository.java` - Comentários
- ✅ `producer/src/main/java/com/concil/edi/producer/service/FileRegistrationService.java` - Comentários

## Comportamento Novo

### Fluxo de Arquivos Não Identificados

1. **Consumer recebe mensagem** do RabbitMQ
2. **Abre InputStream** do arquivo SFTP
3. **Tenta identificar layout** usando as regras configuradas
4. **Se nenhuma regra corresponder**:
   - Define `idt_layout = 0` (SEM_IDENTIFICACAO)
   - Continua a transferência normalmente
   - Transfere arquivo para o destino (S3 ou SFTP)
   - Marca status como `CONCLUIDO`
5. **Arquivo fica disponível** no destino para processamento manual

### Identificação de Arquivos Não Identificados

Para encontrar arquivos que precisam de revisão manual:

```sql
SELECT 
    idt_file_origin,
    des_file_name,
    des_status,
    idt_layout,
    dat_creation
FROM file_origin
WHERE idt_layout = 0
ORDER BY dat_creation DESC;
```

### Retry Logic

- ✅ **Arquivos com status `ERRO`**: São retentados pelo Producer
- ✅ **Arquivos com `idt_layout = 0`**: NÃO são retentados (transferência foi bem-sucedida)

## Impacto

### Positivo
- ✅ Modelo de dados mais limpo e consistente
- ✅ Status reflete a realidade (transferência bem-sucedida)
- ✅ Facilita identificação de arquivos que precisam de novas regras
- ✅ Reduz complexidade do código (menos um status para gerenciar)

### Compatibilidade
- ⚠️ **Breaking Change**: Queries que buscavam `des_status = 'LAYOUT_NAO_IDENTIFICADO'` devem ser alteradas para `idt_layout = 0`
- ⚠️ **Testes E2E**: Precisam ser atualizados para refletir novo comportamento

## Testes

### Teste Manual Realizado
- ✅ 50 arquivos processados com sucesso
- ✅ 20 arquivos identificados como Layout 1 (VENDA)
- ✅ 20 arquivos identificados como Layout 2 (PAGTO)
- ✅ 10 arquivos não identificados → Layout 0 (SEM_IDENTIFICACAO)
- ✅ Todos com status CONCLUIDO
- ✅ Todos transferidos para S3

### Testes E2E Pendentes
- ⚠️ `LayoutIdentificationE2ETest.testUnidentifiedLayoutFailsTransfer` precisa ser atualizado:
  - Renomear para `testUnidentifiedLayoutUsesLayout0`
  - Alterar expectativa de status `ERRO` para `CONCLUIDO`
  - Alterar expectativa de `idt_layout NULL` para `idt_layout = 0`

## Migração

### Para Ambientes Existentes

1. **Executar script DDL atualizado**:
   ```bash
   make init-db
   ```

2. **Atualizar registros existentes** (se houver):
   ```sql
   -- Não há registros com LAYOUT_NAO_IDENTIFICADO pois o status foi removido
   -- Novos arquivos não identificados receberão automaticamente idt_layout = 0
   ```

3. **Rebuild e restart dos containers**:
   ```bash
   make rebuild
   ```

## Referências

- Issue: Alteração do fluxo de identificação de layouts
- Spec: `.kiro/specs/identificacao_layouts/`
- Commit: [hash do commit]

## Autores

- Equipe Controle de Arquivos EDI
