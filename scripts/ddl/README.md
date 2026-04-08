# Scripts DDL Oracle - Controle de Arquivos EDI

Este diretório contém os scripts DDL para criação do modelo de dados Oracle.

## Estrutura

- `00_run_all.sql` - Script master que executa todos os DDLs em ordem
- `01_create_table_server.sql` - Cria tabela SERVER (servidores de origem/destino)
- `02_create_table_sever_paths.sql` - Cria tabela SEVER_PATHS (diretórios)
- `03_create_table_sever_paths_in_out.sql` - Cria tabela SEVER_PATHS_IN_OUT (mapeamentos)
- `04_create_table_file_origin.sql` - Cria tabela FILE_ORIGIN (rastreamento de arquivos)
- `05_insert_initial_data.sql` - Insere dados iniciais para desenvolvimento
- `06_create_layout_tables.sql` - Cria tabelas LAYOUT e LAYOUT_IDENTIFICATION_RULE (identificação de layouts)
- `07_insert_layout_examples.sql` - Insere exemplos de layouts (2 Cielo, 3 Rede)

## Execução

### Opção 1: Automática via Makefile (Recomendado)

Os scripts são executados automaticamente quando você inicia o ambiente:

```bash
# Subir o ambiente (scripts executam automaticamente)
make up
```

Ou executar manualmente após o banco estar rodando:

```bash
# Inicializar banco de dados com DDL scripts
make init-db
```

### Opção 2: Executar todos os scripts manualmente via Docker

```bash
# Copiar scripts para o container
docker cp scripts/ddl edi-oracle:/tmp/

# Ajustar permissões
docker exec -u root edi-oracle chmod -R 755 /tmp/ddl

# Executar scripts
docker exec edi-oracle bash -c "cd /tmp/ddl && sqlplus -s edi_user/edi_pass@//localhost/XEPDB1 @00_run_all.sql"
```

### Opção 3: Executar via SQL*Plus local

```bash
sqlplus edi_user/edi_pass@localhost:1521/XEPDB1 @scripts/ddl/00_run_all.sql
```

### Opção 4: Executar scripts individuais

```bash
sqlplus edi_user/edi_pass@localhost:1521/XEPDB1 @scripts/ddl/01_create_table_server.sql
sqlplus edi_user/edi_pass@localhost:1521/XEPDB1 @scripts/ddl/02_create_table_sever_paths.sql
sqlplus edi_user/edi_pass@localhost:1521/XEPDB1 @scripts/ddl/03_create_table_sever_paths_in_out.sql
sqlplus edi_user/edi_pass@localhost:1521/XEPDB1 @scripts/ddl/04_create_table_file_origin.sql
sqlplus edi_user/edi_pass@localhost:1521/XEPDB1 @scripts/ddl/05_insert_test_data.sql
```

## Idempotência

Todos os scripts são idempotentes - podem ser executados múltiplas vezes sem erros. Eles:
- Removem objetos existentes antes de criar (DROP IF EXISTS)
- Usam blocos PL/SQL com tratamento de exceções
- Permitem re-execução segura

## Objetos Criados

### Tabelas
- `SERVER` - Configurações de servidores (SFTP, S3, etc.)
- `SEVER_PATHS` - Diretórios dentro dos servidores
- `SEVER_PATHS_IN_OUT` - Mapeamentos origem → destino
- `FILE_ORIGIN` - Rastreamento de arquivos processados
- `LAYOUT` - Configurações de layouts de arquivos EDI
- `LAYOUT_IDENTIFICATION_RULE` - Regras de identificação de layouts

#### Tabela FILE_ORIGIN - Status de Processamento

A tabela `FILE_ORIGIN` rastreia o estado de cada arquivo no processo de transferência. Os status possíveis são:

- **EM_ESPERA**: Arquivo aguardando processamento pelo Consumer
- **PROCESSAMENTO**: Arquivo sendo processado no momento
- **CONCLUIDO**: Arquivo transferido com sucesso (layout identificado ou definido como layout 0 se não identificado)
- **ERRO**: Falha na transferência - será retentado pelo Producer até atingir max_retry

**Layout especial para arquivos não identificados:**
- **idt_layout = 0**: Layout "SEM_IDENTIFICACAO" usado quando nenhuma regra de identificação corresponde ao arquivo
- Arquivos com layout 0 são transferidos normalmente com status CONCLUIDO
- Indicam que novas regras de identificação podem ser necessárias

**Importante**: 
- Apenas arquivos com status `ERRO` são retentados pelo Producer
- Arquivos com `idt_layout = 0` são transferidos normalmente para o destino com status CONCLUIDO
- O Producer não retenta arquivos com layout 0

### Sequences
- `server_seq` - Auto-increment para SERVER
- `sever_paths_seq` - Auto-increment para SEVER_PATHS
- `sever_paths_in_out_seq` - Auto-increment para SEVER_PATHS_IN_OUT
- `file_origin_seq` - Auto-increment para FILE_ORIGIN
- `layout_seq` - Auto-increment para LAYOUT
- `layout_identification_rule_seq` - Auto-increment para LAYOUT_IDENTIFICATION_RULE

### Triggers
- `server_bir` - Before Insert para SERVER (auto-increment e dat_creation)
- `sever_paths_bir` - Before Insert para SEVER_PATHS
- `sever_paths_in_out_bir` - Before Insert para SEVER_PATHS_IN_OUT
- `file_origin_bir` - Before Insert para FILE_ORIGIN

### Constraints
- Primary Keys em todas as tabelas
- Foreign Keys entre tabelas relacionadas
- Unique Indexes para garantir unicidade de dados
- Check Constraints para validação de enums

## Dados de Teste

O script `05_insert_initial_data.sql` insere:
- 3 servidores (SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION)
- 3 paths (1 origem SFTP, 2 destinos S3/SFTP)
- 2 mapeamentos PRINCIPAL (SFTP→S3, SFTP→SFTP)

O script `07_insert_layout_examples.sql` insere:
- 6 layouts de exemplo (1 especial SEM_IDENTIFICACAO + 2 Cielo + 3 Rede)
- 18 regras de identificação (3 por layout Cielo, 4 por layout Rede)

### Layout Especial

**Layout 0: SEM_IDENTIFICACAO**
- Tipo: TXT
- Transação: AUXILIAR
- Distribuição: SAZONAL
- Usado automaticamente quando nenhuma regra de identificação corresponde ao arquivo
- Arquivos com este layout são transferidos normalmente com status CONCLUIDO

### Layouts Cielo (Identificação por FILENAME)

**Layout 1: CIELO_015_03_VENDA**
- Tipo: TXT
- Regras:
  1. Filename começa com "cielo"
  2. Filename contém "v15"
  3. Filename termina com "venda"

**Layout 2: CIELO_015_04_PAGTO**
- Tipo: TXT
- Regras:
  1. Filename começa com "cielo"
  2. Filename contém "v15"
  3. Filename termina com "pagto"

### Layouts Rede (Identificação por HEADER)

**Layout 3: REDE_EEVD_02 (CSV)**
- Tipo: CSV
- Separador: ;
- Regras:
  1. Coluna 0 = "00"
  2. Coluna 1 = "Movimentação diária - Cartões de débito"
  3. Coluna 2 = "V2.00 - 05/2023 EEVD"

**Layout 4: REDE_EEVC_03 (TXT)**
- Tipo: TXT
- Regras:
  1. Posição 0-2 = "002"
  2. Posição 3-10 = "REDECARD"
  3. Posição 11-41 = "EXTRATO DE MOVIMENTO DE VENDAS"
  4. Posição 42-62 = "V3.00 - 05/2023 EEVC"

**Layout 5: REDE_EEFI_04 (TXT)**
- Tipo: TXT
- Regras:
  1. Posição 0-2 = "030"
  2. Posição 3-10 = "REDECARD"
  3. Posição 11-42 = "EXTRATO DE MOVIMENTACAO FINANCEIRA"
  4. Posição 43-63 = "V4.00 - 05/2023 EEFI"

## Verificação

Após execução, verificar objetos criados:

```sql
-- Listar tabelas
SELECT table_name FROM user_tables 
WHERE table_name IN ('SERVER', 'SEVER_PATHS', 'SEVER_PATHS_IN_OUT', 'FILE_ORIGIN', 
                     'LAYOUT', 'LAYOUT_IDENTIFICATION_RULE');

-- Listar sequences
SELECT sequence_name FROM user_sequences 
WHERE sequence_name LIKE '%_SEQ';

-- Listar triggers
SELECT trigger_name FROM user_triggers 
WHERE trigger_name LIKE '%_BIR';

-- Verificar dados de teste
SELECT COUNT(*) FROM server;
SELECT COUNT(*) FROM sever_paths;
SELECT COUNT(*) FROM sever_paths_in_out;
SELECT COUNT(*) FROM layout;
SELECT COUNT(*) FROM layout_identification_rule;
```

## Troubleshooting

### Erro: ORA-00942 (table or view does not exist)
- Executar scripts em ordem (00_run_all.sql garante isso)
- Verificar se usuário tem permissões CREATE TABLE

### Erro: ORA-02292 (integrity constraint violated)
- Executar scripts de DROP em ordem reversa
- Usar CASCADE CONSTRAINTS nos DROPs

### Erro: ORA-01031 (insufficient privileges)
- Garantir que usuário tem privilégios:
  ```sql
  GRANT CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER TO edi_user;
  ```

## Segurança

Todos os comentários de colunas incluem a label `[NOT_SECURITY_APPLY]` para controle de segurança em nível de coluna.
