# DDL Scripts Summary - Controle de Arquivos

## Overview

Este documento resume os scripts DDL criados para o sistema "Controle de Arquivos".

## Scripts Criados

### 01-create-sequences.sql
Cria 11 sequences para geração de IDs primários:
- `seq_job_concurrency_control`
- `seq_server`
- `seq_sever_paths`
- `seq_sever_paths_in_out`
- `seq_layout`
- `seq_layout_identification_rule`
- `seq_customer_identification`
- `seq_customer_identification_rule`
- `seq_file_origin`
- `seq_file_origin_client`
- `seq_file_origin_client_processing`

### 02-create-tables.sql
Cria 11 tabelas do sistema:

#### Controle de Concorrência
- **job_concurrency_control**: Controle de execução de jobs com status RUNNING/COMPLETED/PENDING

#### Configuração de Servidores
- **server**: Servidores SFTP/S3/NFS com credenciais Vault
- **sever_paths**: Caminhos de diretórios em servidores (ORIGIN/DESTINATION)
- **sever_paths_in_out**: Mapeamento origem-destino (PRINCIPAL/SECUNDARIO)

#### Identificação
- **layout**: Layouts de arquivos (CSV, TXT, JSON, OFX, XML)
- **layout_identification_rule**: Regras para identificar layout (HEADER/TAG/FILENAME/KEY)
- **customer_identification**: Clientes com peso para desempate
- **customer_identification_rule**: Regras para identificar cliente

#### Processamento
- **file_origin**: Arquivos coletados com metadados
- **file_origin_client**: Associação arquivo-cliente
- **file_origin_client_processing**: Rastreabilidade completa (COLETA/RAW/STAGING/ORDINATION/PROCESSING/PROCESSED/DELETE)

### 03-create-indexes.sql
Cria índices para otimização:

#### Índices Únicos
- **idx_file_origin_unique**: Garante unicidade de arquivos (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)

#### Índices de Busca
- Índices em chaves estrangeiras para joins eficientes
- Índices em colunas de busca frequente (status, tipo, data)
- Índices compostos para queries específicas (cliente+adquirente, etapa+status)
- Índices temporais para consultas por data (DESC para queries recentes)

Total: 35 índices criados

### 04-create-constraints.sql
Cria constraints de chave estrangeira:

#### Relacionamentos
- **sever_paths** → server
- **sever_paths_in_out** → sever_paths (origem) e server (destino)
- **layout_identification_rule** → layout
- **customer_identification_rule** → customer_identification
- **file_origin** → layout e sever_paths_in_out
- **file_origin_client** → file_origin e customer_identification
- **file_origin_client_processing** → file_origin_client

Total: 9 constraints de FK criadas

### 05-insert-test-data.sql
Insere dados de teste para desenvolvimento:

#### Servidores (4 registros)
- SFTP_CIELO (externo)
- SFTP_REDE (externo)
- S3_DESTINO (interno)
- SFTP_INTERNO (interno)

#### Caminhos (4 registros)
- 2 caminhos de origem (Cielo e Rede)
- 2 caminhos de destino S3

#### Mapeamentos (2 registros)
- Cielo SFTP → S3
- Rede SFTP → S3

#### Layouts (3 registros)
- CIELO_CSV_V1
- REDE_TXT_V1
- CIELO_OFX_V1

#### Clientes (3 registros)
- CLIENTE_A (peso 100)
- CLIENTE_B (peso 90)
- CLIENTE_C (peso 80)

#### Regras de Identificação
- 3 regras de cliente (COMECA-COM, CONTEM, TERMINA-COM)
- 6 regras de layout (combinando FILENAME e HEADER)

## Validação dos Requisitos

### Requisito 18.1 ✓
**Criar scripts DDL para todas as tabelas**
- ✓ job_concurrency_control com sequences
- ✓ server, sever_paths, sever_paths_in_out
- ✓ layout, layout_identification_rule
- ✓ customer_identification, customer_identification_rule
- ✓ file_origin, file_origin_client, file_origin_client_processing

### Requisito 18.2 ✓
**Criar índices necessários incluindo índice único em file_origin**
- ✓ Índice único: idx_file_origin_unique (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)
- ✓ 35 índices adicionais para otimização de consultas

### Requisito 18.3 ✓
**Criar sequences para geração de IDs primários**
- ✓ 11 sequences criadas (uma para cada tabela)

### Requisito 18.4 ✓
**Definir constraints de chave estrangeira**
- ✓ 9 constraints de FK entre tabelas relacionadas

### Requisito 18.5 ✓
**Criar índices em colunas de busca frequente**
- ✓ Índices em FKs para joins
- ✓ Índices em colunas de status e tipo
- ✓ Índices compostos para queries específicas
- ✓ Índices temporais para consultas por data

## Características Técnicas

### Tipos de Dados
- **IDs**: NUMBER(19) para suportar valores grandes
- **Códigos/Nomes**: VARCHAR2 com tamanhos apropriados
- **Timestamps**: TIMESTAMP para precisão de milissegundos
- **JSON**: CLOB para jsn_additional_info
- **Flags**: NUMBER(1) para booleanos (1=true, 0=false)

### Padrões de Design
- Todas as tabelas têm `flg_active` para soft delete
- Todas as tabelas têm `dat_created` e `dat_updated` para auditoria
- Timestamps com DEFAULT CURRENT_TIMESTAMP
- Comentários em todas as tabelas e colunas
- Nomenclatura consistente (idt_, des_, num_, dat_, flg_, jsn_)

### Integridade Referencial
- Todas as FKs definidas explicitamente
- Índices em todas as colunas de FK para performance
- Índice único em file_origin para prevenir duplicatas

## Execução

### Ordem de Execução
Os scripts são executados automaticamente pelo Docker Compose na ordem alfabética:
1. Sequences
2. Tables
3. Indexes
4. Constraints
5. Test Data

### Validação
Para validar a criação das tabelas:

```sql
-- Verificar tabelas criadas
SELECT table_name FROM user_tables ORDER BY table_name;

-- Verificar sequences criadas
SELECT sequence_name FROM user_sequences ORDER BY sequence_name;

-- Verificar índices criados
SELECT index_name, table_name FROM user_indexes ORDER BY table_name, index_name;

-- Verificar constraints criadas
SELECT constraint_name, constraint_type, table_name 
FROM user_constraints 
WHERE constraint_type = 'R' 
ORDER BY table_name;

-- Contar registros de teste
SELECT 'server' as tabela, COUNT(*) as total FROM server
UNION ALL
SELECT 'layout', COUNT(*) FROM layout
UNION ALL
SELECT 'customer_identification', COUNT(*) FROM customer_identification;
```

## Notas Importantes

1. **Idempotência**: Os scripts não são idempotentes. Para recriar, é necessário dropar as tabelas manualmente ou recriar o container Oracle.

2. **Ordem de Execução**: A ordem dos scripts é crítica devido às dependências de FK.

3. **Dados de Teste**: O script 05 é opcional e pode ser removido em produção.

4. **Performance**: Os índices foram criados considerando as queries mais frequentes descritas no documento de design.

5. **Nomenclatura**: A nomenclatura segue o padrão do projeto (sever_paths ao invés de server_paths é intencional).

## Próximos Passos

1. Testar execução dos scripts no Docker Compose
2. Validar criação de todas as tabelas e índices
3. Testar inserção de dados de teste
4. Validar constraints de FK
5. Executar queries de validação
