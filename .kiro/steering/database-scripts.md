# Scripts DDL do Banco de Dados

Este documento descreve os scripts DDL disponíveis para inicialização e gerenciamento do banco de dados Oracle.

## Localização

Todos os scripts DDL estão localizados em: `scripts/ddl/`

## Comando de Execução

Para executar todos os scripts DDL e inicializar o banco de dados:

```bash
make init-db
```

Este comando:
1. Copia os scripts para o container Oracle
2. Executa o script principal `00_run_all.sql` que chama todos os outros scripts na ordem correta
3. Cria todas as tabelas, sequences, triggers e insere dados de exemplo

## Scripts Disponíveis

### 00_run_all.sql
Script principal que executa todos os outros scripts na ordem correta.

**Ordem de execução:**
1. 01_create_table_server.sql
2. 02_create_table_sever_paths.sql
3. 03_create_table_sever_paths_in_out.sql
4. 04_create_table_file_origin.sql
5. 05_insert_initial_data.sql
6. 06_create_layout_tables.sql
7. 07_insert_layout_examples.sql
8. 08_create_customer_identification_table.sql
9. 09_create_customer_identification_rule_table.sql
10. 10_create_file_origin_clients_table.sql
11. 11_insert_customer_identification_examples.sql

### 01_create_table_server.sql
Cria a tabela `server` que armazena informações sobre servidores de origem e destino.

**Campos principais:**
- `idt_server`: ID do servidor (PK)
- `cod_server`: Código/host do servidor
- `des_server_type`: Tipo do servidor (S3, SFTP, NFS, BLOB_STORAGE, OBJECT_STORAGE)
- `des_server_origin`: Origem do servidor (EXTERNO, INTERNO)
- `cod_vault`: Código do vault para credenciais
- `des_vault_secret`: Secret do vault

### 02_create_table_sever_paths.sql
Cria a tabela `sever_paths` que define caminhos/diretórios dentro dos servidores.

**Campos principais:**
- `idt_sever_path`: ID do caminho (PK)
- `idt_server`: FK para server
- `idt_acquirer`: ID da adquirente
- `des_path`: Caminho do diretório
- `des_path_type`: Tipo do caminho (IN, OUT)

### 03_create_table_sever_paths_in_out.sql
Cria a tabela `sever_paths_in_out` que mapeia caminhos de origem para destino.

**Campos principais:**
- `idt_sever_paths_in_out`: ID do mapeamento (PK)
- `idt_sever_path_in`: FK para caminho de entrada
- `idt_sever_path_out`: FK para caminho de saída

### 04_create_table_file_origin.sql
Cria a tabela `file_origin` que rastreia o estado de cada arquivo no processo de transferência.

**Campos principais:**
- `idt_file_origin`: ID do arquivo (PK)
- `idt_acquirer`: ID da adquirente
- `idt_layout`: ID do layout identificado (pode ser NULL)
- `des_file_name`: Nome do arquivo
- `num_file_size`: Tamanho em bytes
- `des_file_type`: Tipo do arquivo (CSV, JSON, TXT, XML, OFX)
- `des_step`: Passo do processo (COLETA, DELETE, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED)
- `des_status`: Status do processamento
- `des_transaction_type`: Tipo de transação (COMPLETO, CAPTURA, FINANCEIRO)
- `num_retry`: Número de tentativas realizadas
- `max_retry`: Número máximo de tentativas

**Status possíveis:**
- `EM_ESPERA`: Arquivo aguardando processamento
- `PROCESSAMENTO`: Arquivo sendo processado no momento
- `CONCLUIDO`: Arquivo transferido com sucesso (layout identificado ou definido como layout 0 se não identificado)
- `ERRO`: Falha na transferência (será retentado pelo Producer)

**Layout especial para arquivos não identificados:**
- `idt_layout = 0`: Layout "SEM_IDENTIFICACAO" usado quando nenhuma regra de identificação corresponde ao arquivo
- Arquivos com layout 0 são transferidos normalmente com status CONCLUIDO
- Indicam que novas regras de identificação podem ser necessárias

**Constraint única:** (des_file_name, idt_acquirer, dat_timestamp_file, flg_active) - Previne duplicação de arquivos

### 05_insert_initial_data.sql
Insere dados iniciais de exemplo para desenvolvimento e testes.

**Dados inseridos:**
- 2 servidores: SFTP origem (Cielo) e S3 destino
- 2 caminhos: origem (/home/cielo/upload) e destino (edi-files/raw)
- 1 mapeamento origem → destino

### 06_create_layout_tables.sql
Cria as tabelas para identificação de layouts de arquivos EDI.

**Tabelas criadas:**

#### layout
Armazena informações sobre layouts de arquivos EDI.

**Campos principais:**
- `idt_layout`: ID do layout (PK)
- `idt_acquirer`: ID da adquirente
- `cod_layout`: Código do layout (ex: CIELO_015_03_VENDA)
- `des_layout`: Descrição do layout
- `des_file_type`: Tipo de arquivo (CSV, TXT, JSON, XML, OFX)
- `des_encoding`: Encoding esperado (UTF-8, ISO-8859-1, etc.)
- `des_column_separator`: Separador de colunas para CSV
- `des_distribution_type`: Tipo de distribuição (DIARIO, DIAS_UTEIS, etc.)

#### layout_identification_rule
Armazena regras para identificação automática de layouts.

**Campos principais:**
- `idt_layout_identification_rule`: ID da regra (PK)
- `idt_layout`: FK para layout
- `des_rule`: Descrição da regra
- `des_value_origin`: Origem do valor (FILENAME, HEADER, TAG, KEY)
- `des_value`: Valor esperado
- `des_criteria_type`: Tipo de critério (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL)
- `des_function_origin`: Função de transformação na origem (UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE)
- `des_function_dest`: Função de transformação no destino
- `num_start_position`: Posição inicial (para TXT)
- `num_end_position`: Posição final (para TXT)
- `num_column_index`: Índice da coluna (para CSV)
- `des_tag_path`: Caminho da tag (para XML)
- `des_key_path`: Caminho da chave (para JSON)

### 07_insert_layout_examples.sql
Insere exemplos de layouts e regras de identificação.

**Layouts inseridos:**
- 2 layouts Cielo (VENDA e PAGTO) com identificação por FILENAME
- 3 layouts Rede (EEVD CSV, EEVC TXT, EEFI TXT) com identificação por HEADER

### 08_create_customer_identification_table.sql
Cria a tabela `customer_identification` que armazena configurações de identificação de clientes.

**Campos principais:**
- `idt_identification`: ID da identificação (PK)
- `idt_client`: ID do cliente (referência externa)
- `idt_acquirer`: ID da adquirente
- `idt_layout`: ID do layout (opcional, FK para layout)
- `idt_merchant`: ID do estabelecimento (opcional)
- `num_process_weight`: Peso da regra para ordenação
- `flg_is_priority`: Flag de prioridade
- `dat_start/dat_end`: Período de vigência

### 09_create_customer_identification_rule_table.sql
Cria a tabela `customer_identification_rule` que define regras para identificação de clientes.

**Campos principais:**
- `idt_rule`: ID da regra (PK)
- `idt_identification`: FK para customer_identification
- `des_rule`: Descrição da regra
- `des_value_origin`: Origem do valor (FILENAME, HEADER, TAG, KEY)
- `des_criteria_type`: Tipo de critério (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL)
- `des_value`: Valor esperado
- `des_tag`: Caminho XML (para TAG)
- `des_key`: Caminho JSON (para KEY)
- `num_start_position/num_end_position`: Posições para extração (TXT/CSV)
- `des_function_origin/des_function_dest`: Funções de transformação

### 10_create_file_origin_clients_table.sql
Cria a tabela `file_origin_clients` que rastreia clientes identificados para cada arquivo.

**Campos principais:**
- `idt_client_identified`: ID do registro (PK)
- `idt_file_origin`: FK para file_origin
- `idt_client`: ID do cliente identificado
- `dat_creation`: Data de criação

**Constraint única:** (idt_file_origin, idt_client) - Previne duplicação de cliente para o mesmo arquivo

### 11_insert_customer_identification_examples.sql
Insere exemplos de identificações de clientes e regras.

**Identificações inseridas:**
- Cliente 15 (Cielo): Identificação por FILENAME com múltiplas regras
- Cliente 20 (Cielo): Identificação por FILENAME com regra premium
- Cliente 25 (Cielo): Identificação por HEADER em TXT (layout VENDA)
- Cliente 30 (Rede): Identificação por HEADER em CSV (layout EEVD)

## Exemplos de Queries Úteis

### Listar todos os servidores
```sql
SELECT idt_server, cod_server, des_server_type, des_server_origin 
FROM server 
WHERE flg_active = 1;
```

### Listar mapeamentos origem → destino
```sql
SELECT 
    spio.idt_sever_paths_in_out,
    sp_in.des_path as origem,
    sp_out.des_path as destino
FROM sever_paths_in_out spio
JOIN sever_paths sp_in ON spio.idt_sever_path_in = sp_in.idt_sever_path
JOIN sever_paths sp_out ON spio.idt_sever_path_out = sp_out.idt_sever_path
WHERE spio.flg_active = 1;
```

### Listar arquivos por status
```sql
SELECT des_status, COUNT(*) as total 
FROM file_origin 
GROUP BY des_status 
ORDER BY des_status;
```

### Listar layouts ativos
```sql
SELECT idt_layout, cod_layout, des_layout, des_file_type 
FROM layout 
WHERE flg_active = 1 
ORDER BY idt_layout DESC;
```

### Listar regras de identificação de um layout
```sql
SELECT 
    lir.des_rule,
    lir.des_value_origin,
    lir.des_value,
    lir.des_criteria_type
FROM layout_identification_rule lir
WHERE lir.idt_layout = 1 
AND lir.flg_active = 1;
```

### Verificar arquivos com layout não identificado
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

### Listar identificações de clientes ativas
```sql
SELECT 
    ci.idt_identification,
    ci.idt_client,
    ci.idt_acquirer,
    ci.idt_layout,
    ci.num_process_weight
FROM customer_identification ci
WHERE ci.flg_active = 1
ORDER BY ci.num_process_weight DESC NULLS LAST;
```

### Listar regras de identificação de um cliente
```sql
SELECT 
    cir.des_rule,
    cir.des_value_origin,
    cir.des_value,
    cir.des_criteria_type,
    cir.des_function_origin,
    cir.des_function_dest
FROM customer_identification_rule cir
WHERE cir.idt_identification = 1
AND cir.flg_active = 1;
```

### Verificar clientes identificados para um arquivo
```sql
SELECT 
    foc.idt_client_identified,
    foc.idt_file_origin,
    foc.idt_client,
    foc.dat_creation,
    fo.des_file_name
FROM file_origin_clients foc
JOIN file_origin fo ON foc.idt_file_origin = fo.idt_file_origin
WHERE foc.idt_file_origin = 1;
```

### Listar arquivos com múltiplos clientes identificados
```sql
SELECT 
    fo.idt_file_origin,
    fo.des_file_name,
    COUNT(foc.idt_client) as total_clients
FROM file_origin fo
JOIN file_origin_clients foc ON fo.idt_file_origin = foc.idt_file_origin
GROUP BY fo.idt_file_origin, fo.des_file_name
HAVING COUNT(foc.idt_client) > 1
ORDER BY total_clients DESC;
```

## Reinicialização do Banco

Para reinicializar completamente o banco de dados:

```bash
# Parar e remover volumes (apaga todos os dados)
make down-volumes

# Subir novamente (recria tudo do zero)
make up
```

## Troubleshooting

### Erro ao executar scripts
Se houver erro ao executar os scripts DDL:

1. Verificar se o Oracle está saudável:
```bash
docker ps
```

2. Verificar logs do Oracle:
```bash
make logs-infra
```

3. Executar manualmente:
```bash
make shell-oracle
# Dentro do SQL*Plus:
@/tmp/ddl/00_run_all.sql
```

### Verificar se as tabelas foram criadas
```bash
make shell-oracle
# Dentro do SQL*Plus:
SELECT table_name FROM user_tables ORDER BY table_name;
```
