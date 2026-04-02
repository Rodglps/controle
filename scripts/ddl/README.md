# Scripts DDL Oracle - Controle de Arquivos EDI

Este diretório contém os scripts DDL para criação do modelo de dados Oracle.

## Estrutura

- `00_run_all.sql` - Script master que executa todos os DDLs em ordem
- `01_create_table_server.sql` - Cria tabela SERVER (servidores de origem/destino)
- `02_create_table_sever_paths.sql` - Cria tabela SEVER_PATHS (diretórios)
- `03_create_table_sever_paths_in_out.sql` - Cria tabela SEVER_PATHS_IN_OUT (mapeamentos)
- `04_create_table_file_origin.sql` - Cria tabela FILE_ORIGIN (rastreamento de arquivos)
- `05_insert_test_data.sql` - Insere dados de teste para desenvolvimento

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

### Sequences
- `server_seq` - Auto-increment para SERVER
- `sever_paths_seq` - Auto-increment para SEVER_PATHS
- `sever_paths_in_out_seq` - Auto-increment para SEVER_PATHS_IN_OUT
- `file_origin_seq` - Auto-increment para FILE_ORIGIN

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

O script `05_insert_test_data.sql` insere:
- 3 servidores (SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION)
- 3 paths (1 origem SFTP, 2 destinos S3/SFTP)
- 2 mapeamentos PRINCIPAL (SFTP→S3, SFTP→SFTP)

## Verificação

Após execução, verificar objetos criados:

```sql
-- Listar tabelas
SELECT table_name FROM user_tables 
WHERE table_name IN ('SERVER', 'SEVER_PATHS', 'SEVER_PATHS_IN_OUT', 'FILE_ORIGIN');

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
