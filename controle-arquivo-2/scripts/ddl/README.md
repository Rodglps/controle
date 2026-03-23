# Scripts DDL Oracle

Este diretório contém os scripts DDL para criação das tabelas do banco de dados Oracle.

## Ordem de Execução

Os scripts são executados automaticamente quando o container Oracle é iniciado pela primeira vez, na ordem alfabética dos nomes dos arquivos.

Estrutura recomendada:

1. `01-create-sequences.sql` - Criação de sequences para IDs
2. `02-create-tables.sql` - Criação de todas as tabelas
3. `03-create-indexes.sql` - Criação de índices
4. `04-create-constraints.sql` - Criação de constraints de chave estrangeira
5. `05-insert-test-data.sql` - Inserção de dados de teste (opcional)

## Tabelas do Sistema

### Controle de Concorrência
- `job_concurrency_control` - Controle de execução de jobs

### Configuração de Servidores
- `server` - Servidores SFTP/S3
- `sever_paths` - Caminhos de origem e destino
- `sever_paths_in_out` - Mapeamento origem-destino

### Identificação
- `layout` - Layouts de arquivos
- `layout_identification_rule` - Regras de identificação de layout
- `customer_identification` - Clientes
- `customer_identification_rule` - Regras de identificação de cliente

### Processamento
- `file_origin` - Arquivos coletados
- `file_origin_client` - Associação arquivo-cliente
- `file_origin_client_processing` - Rastreabilidade de processamento

## Execução Manual

Para executar scripts manualmente:

```bash
docker exec -i controle-arquivos-oracle sqlplus system/Oracle123@XE < scripts/ddl/01-create-sequences.sql
```

Ou conectar ao SQL*Plus e executar:

```bash
docker exec -it controle-arquivos-oracle sqlplus system/Oracle123@XE
SQL> @/docker-entrypoint-initdb.d/startup/01-create-sequences.sql
```

## Notas

- Os scripts devem ser idempotentes (usar `CREATE OR REPLACE` ou `DROP IF EXISTS`)
- Usar `EXIT;` no final de cada script
- Comentar adequadamente as tabelas e colunas
- Seguir convenções de nomenclatura do projeto
