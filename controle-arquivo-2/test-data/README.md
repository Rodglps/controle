# Test Data - Controle de Arquivos

Este diretório contém dados de teste completos para desenvolvimento e testes locais do sistema de Controle de Arquivos.

## Estrutura

```
test-data/
├── sql/                    # Scripts SQL para popular banco de dados
│   ├── 01-insert-servers.sql
│   ├── 02-insert-layouts.sql
│   ├── 03-insert-clients.sql
│   └── 04-insert-acquirers.sql
├── files/                  # Arquivos EDI de exemplo
│   ├── positive-cases/     # Arquivos que devem ser identificados com sucesso
│   ├── negative-cases/     # Arquivos que devem falhar na identificação
│   └── edge-cases/         # Casos extremos e especiais
└── README.md              # Esta documentação
```

## Como Usar

### 1. Popular o Banco de Dados

Execute os scripts SQL na ordem:

```bash
# Assumindo Oracle SQL*Plus
sqlplus user/password@database @test-data/sql/01-insert-servers.sql
sqlplus user/password@database @test-data/sql/02-insert-layouts.sql
sqlplus user/password@database @test-data/sql/03-insert-clients.sql
sqlplus user/password@database @test-data/sql/04-insert-acquirers.sql
```

### 2. Copiar Arquivos para SFTP

Copie os arquivos de teste para os diretórios SFTP apropriados:

```bash
# Arquivos Cielo (Adquirente ID 1)
cp test-data/files/positive-cases/LOJA_SHOPPING_20240115.csv /incoming/cielo/edi/
cp test-data/files/positive-cases/FARMACIA_20240115_EXTRATO.ofx /incoming/cielo/edi/

# Arquivos Rede (Adquirente ID 2)
cp test-data/files/positive-cases/SUPERMERCADO_CENTRAL_VENDAS_20240115.txt /incoming/rede/files/
cp test-data/files/positive-cases/SUPERMERCADO_CENTRAL_CANCEL_20240115.csv /incoming/rede/files/

# Arquivos Stone (Adquirente ID 3)
cp test-data/files/positive-cases/STONE_PAGAMENTOS_20240115_RESTAURANTE.json /data/stone/export/

# Arquivos GetNet (Adquirente ID 4)
cp test-data/files/positive-cases/POSTO_COMBUSTIVEL_CONCILIACAO_20240115.xml /getnet/outbound/
```

## Cenários de Teste

### Cenário 1: Identificação por COMECA-COM (Filename)

**Arquivo**: `LOJA_SHOPPING_20240115.csv`

**Cliente Esperado**: LOJA_SHOPPING (ID 1)
**Layout Esperado**: CIELO_CSV_TRANSACOES (ID 1)
**Adquirente**: Cielo (ID 1)

**Regras Aplicadas**:
- Cliente: Nome do arquivo COMEÇA COM "LOJA_SHOPPING_" (posições 0-14)
- Layout (Filename): Nome do arquivo CONTÉM ".csv"
- Layout (Header): Header COMEÇA COM "TIPO;DATA;VALOR;ESTABELECIMENTO"

**Resultado Esperado**: ✅ Identificação bem-sucedida
**Destino**: S3 bucket `raw/cielo`

---

### Cenário 2: Identificação por CONTEM (Filename) com Múltiplas Regras

**Arquivo**: `SUPERMERCADO_CENTRAL_VENDAS_20240115.txt`

**Cliente Esperado**: SUPERMERCADO_CENTRAL (ID 2)
**Layout Esperado**: REDE_TXT_VENDAS (ID 2)
**Adquirente**: Rede (ID 2)

**Regras Aplicadas**:
- Cliente Regra 1: Nome do arquivo CONTÉM "SUPERMERCADO"
- Cliente Regra 2: Nome do arquivo CONTÉM "CENTRAL" (TODAS as regras devem ser true)
- Layout (Filename): Nome do arquivo TERMINA COM ".txt"
- Layout (Header): Header CONTÉM "REDE VENDAS"

**Resultado Esperado**: ✅ Identificação bem-sucedida
**Destino**: S3 bucket `raw/rede`

---

### Cenário 3: Identificação por TERMINA-COM (Filename)

**Arquivo**: `STONE_PAGAMENTOS_20240115_RESTAURANTE.json`

**Cliente Esperado**: RESTAURANTE_GOURMET (ID 3)
**Layout Esperado**: STONE_JSON_PAGAMENTOS (ID 3)
**Adquirente**: Stone (ID 3)

**Regras Aplicadas**:
- Cliente: Nome do arquivo TERMINA COM "_RESTAURANTE.json"
- Layout (Filename): Nome do arquivo CONTÉM ".json"
- Layout (Header): Header CONTÉM "\"acquirer\":\"STONE\""

**Resultado Esperado**: ✅ Identificação bem-sucedida
**Destino**: S3 bucket `raw/stone`

---

### Cenário 4: Identificação por IGUAL (Substring)

**Arquivo**: `FARMACIA_20240115_EXTRATO.ofx`

**Cliente Esperado**: FARMACIA_SAUDE (ID 4)
**Layout Esperado**: CIELO_OFX_EXTRATO (ID 4)
**Adquirente**: Cielo (ID 1)

**Regras Aplicadas**:
- Cliente: Substring nas posições 0-8 é IGUAL a "FARMACIA"
- Layout (Filename): Nome do arquivo TERMINA COM ".ofx"
- Layout (Header): Header CONTÉM "<OFX>"

**Resultado Esperado**: ✅ Identificação bem-sucedida
**Destino**: S3 bucket `raw/cielo`

---

### Cenário 5: Identificação XML com Múltiplas Regras de Header

**Arquivo**: `POSTO_COMBUSTIVEL_CONCILIACAO_20240115.xml`

**Cliente Esperado**: POSTO_COMBUSTIVEL (ID 5)
**Layout Esperado**: GETNET_XML_CONCILIACAO (ID 5)
**Adquirente**: GetNet (ID 4)

**Regras Aplicadas**:
- Cliente Regra 1: Nome do arquivo COMEÇA COM "POSTO_"
- Cliente Regra 2: Nome do arquivo CONTÉM "COMBUSTIVEL"
- Layout (Filename): Nome do arquivo CONTÉM ".xml"
- Layout (Header) Regra 1: Header COMEÇA COM "<?xml"
- Layout (Header) Regra 2: Header CONTÉM "<Conciliacao>"

**Resultado Esperado**: ✅ Identificação bem-sucedida
**Destino**: S3 bucket `raw/getnet`

---

### Cenário 6: Layout Alternativo com Critério IGUAL

**Arquivo**: `SUPERMERCADO_CENTRAL_CANCEL_20240115.csv`

**Cliente Esperado**: SUPERMERCADO_CENTRAL (ID 2)
**Layout Esperado**: REDE_CSV_CANCELAMENTOS (ID 6)
**Adquirente**: Rede (ID 2)

**Regras Aplicadas**:
- Cliente: Mesmo do Cenário 2 (CONTÉM "SUPERMERCADO" E "CENTRAL")
- Layout (Filename) Regra 1: Nome do arquivo CONTÉM "CANCEL"
- Layout (Filename) Regra 2: Nome do arquivo TERMINA COM ".csv"
- Layout (Header): Header é IGUAL a "TIPO;DATA;NSU;VALOR_CANCELADO"

**Resultado Esperado**: ✅ Identificação bem-sucedida
**Destino**: S3 bucket `raw/rede`

---

### Cenário 7: Cliente Não Identificado (Caso Negativo)

**Arquivo**: `UNKNOWN_CLIENT_20240115.csv`

**Cliente Esperado**: Nenhum
**Layout Esperado**: Não aplicável
**Adquirente**: Cielo (ID 1)

**Regras Aplicadas**: Nenhuma regra de cliente corresponde

**Resultado Esperado**: ❌ Erro de identificação de cliente
**Status**: ERRO
**Mensagem**: "Nenhuma regra de identificação de cliente retornou match"

---

### Cenário 8: Layout Não Identificado (Caso Negativo)

**Arquivo**: `RANDOM_FILE_NO_MATCH.txt`

**Cliente Esperado**: Nenhum
**Layout Esperado**: Nenhum

**Regras Aplicadas**: Nenhuma regra corresponde

**Resultado Esperado**: ❌ Erro de identificação de cliente e layout
**Status**: ERRO

---

### Cenário 9: Header Incorreto (Caso Negativo)

**Arquivo**: `WRONG_HEADER_FORMAT.json`

**Cliente Esperado**: Nenhum (nome não corresponde a nenhuma regra)
**Layout Esperado**: Nenhum (header não corresponde)

**Regras Aplicadas**: Nenhuma

**Resultado Esperado**: ❌ Erro de identificação
**Status**: ERRO

---

### Cenário 10: Arquivo Vazio (Edge Case)

**Arquivo**: `EMPTY_FILE.csv`

**Cliente Esperado**: Nenhum
**Layout Esperado**: Nenhum

**Resultado Esperado**: ❌ Erro ao processar arquivo vazio
**Status**: ERRO
**Comportamento**: Sistema deve tratar gracefully sem crash

---

### Cenário 11: Arquivo com Apenas Header (Edge Case)

**Arquivo**: `LOJA_SHOPPING_ONLY_HEADER.csv`

**Cliente Esperado**: LOJA_SHOPPING (ID 1)
**Layout Esperado**: CIELO_CSV_TRANSACOES (ID 1)

**Resultado Esperado**: ✅ Identificação bem-sucedida (header válido)
**Observação**: Arquivo sem dados, mas header correto permite identificação

---

### Cenário 12: Desempate por Peso (Edge Case)

**Arquivo**: `LOJA_ELETRONICOS_VENDAS_20240115.csv`

**Clientes Possíveis**:
- LOJA_SHOPPING (peso 100) - se regras corresponderem
- LOJA_ELETRONICOS (peso 75) - regra CONTÉM "ELETRONICOS"

**Cliente Esperado**: LOJA_ELETRONICOS (ID 6)
**Layout Esperado**: CIELO_CSV_TRANSACOES (ID 1)

**Resultado Esperado**: ✅ Cliente com regra mais específica é selecionado
**Observação**: Testa seleção quando múltiplos clientes satisfazem regras

---

## Validações Esperadas

### Validações de Requisitos

| Requisito | Cenário(s) | Validação |
|-----------|-----------|-----------|
| 8.1 | 1-6 | Aplicação correta de critérios de identificação de cliente |
| 8.2 | 1, 4 | Aplicação de critérios COMECA-COM e IGUAL |
| 8.3 | 2, 5 | Aplicação de critério CONTEM |
| 8.4 | 1-6 | Todas as regras ativas devem retornar true |
| 8.5 | 7, 8 | Erro registrado quando cliente não identificado |
| 8.6 | 12 | Desempate por num_processing_weight |
| 9.1 | 1-6 | Aplicação de regras de layout |
| 9.2 | 1-6 | Regras aplicadas ao nome do arquivo (FILENAME) |
| 9.3 | 1-6 | Regras aplicadas aos primeiros 7000 bytes (HEADER) |
| 9.4 | 1-6 | Aplicação de critérios COMECA-COM, TERMINA-COM, CONTEM, IGUAL |
| 9.5 | 1-6 | Todas as regras ativas devem retornar true |
| 9.6 | 8, 9 | Erro registrado quando layout não identificado |
| 17.1 | Todos | Dados de teste para ambiente local |

### Formatos de Arquivo Testados

- ✅ CSV (delimitado por ponto-vírgula)
- ✅ TXT (posicional)
- ✅ JSON
- ✅ OFX
- ✅ XML

### Critérios de Identificação Testados

- ✅ COMECA-COM (Cenários 1, 5)
- ✅ TERMINA-COM (Cenários 2, 3, 4)
- ✅ CONTEM (Cenários 2, 3, 5, 6)
- ✅ IGUAL (Cenários 4, 6)

### Origens de Valor Testadas

- ✅ FILENAME (todos os cenários)
- ✅ HEADER (todos os cenários positivos)

## Testes Manuais

### Teste End-to-End Completo

1. **Preparação**:
   ```bash
   # Popular banco de dados
   ./scripts/populate-test-data.sh
   
   # Iniciar ambiente Docker
   docker-compose up -d
   ```

2. **Copiar arquivos para SFTP**:
   ```bash
   # Copiar arquivos positivos
   ./scripts/copy-test-files.sh positive
   ```

3. **Executar Orquestrador**:
   ```bash
   # Aguardar ciclo de coleta
   # Verificar logs: docker logs orchestrator
   ```

4. **Verificar Banco de Dados**:
   ```sql
   -- Verificar arquivos coletados
   SELECT * FROM file_origin WHERE flg_active = 1;
   
   -- Verificar associações cliente-arquivo
   SELECT fo.des_file_name, ci.des_customer_name, l.des_layout_name
   FROM file_origin fo
   JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
   JOIN customer_identification ci ON foc.idt_client = ci.idt_customer_identification
   JOIN layout l ON fo.idt_layout = l.idt_layout
   WHERE fo.flg_active = 1;
   
   -- Verificar rastreabilidade
   SELECT * FROM file_origin_client_processing
   ORDER BY dat_created DESC;
   ```

5. **Verificar Destino S3**:
   ```bash
   # LocalStack S3
   aws --endpoint-url=http://localhost:4566 s3 ls s3://raw-bucket/raw/cielo/
   aws --endpoint-url=http://localhost:4566 s3 ls s3://raw-bucket/raw/rede/
   aws --endpoint-url=http://localhost:4566 s3 ls s3://raw-bucket/raw/stone/
   aws --endpoint-url=http://localhost:4566 s3 ls s3://raw-bucket/raw/getnet/
   ```

### Teste de Casos Negativos

1. **Copiar arquivos negativos**:
   ```bash
   ./scripts/copy-test-files.sh negative
   ```

2. **Verificar erros no banco**:
   ```sql
   SELECT fo.des_file_name, focp.des_status, focp.des_message_error
   FROM file_origin fo
   LEFT JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
   LEFT JOIN file_origin_client_processing focp ON foc.idt_file_origin_client = focp.idt_file_origin_client
   WHERE focp.des_status = 'ERRO';
   ```

3. **Verificar logs estruturados**:
   ```bash
   docker logs processor | grep ERROR | jq .
   ```

### Teste de Performance

1. **Criar múltiplos arquivos**:
   ```bash
   # Gerar 1000 arquivos de teste
   ./scripts/generate-bulk-test-files.sh 1000
   ```

2. **Medir throughput**:
   ```bash
   # Tempo de processamento
   time ./scripts/wait-for-processing.sh
   ```

3. **Verificar uso de memória**:
   ```bash
   docker stats processor orchestrator
   ```

## Troubleshooting

### Problema: Cliente não identificado

**Sintomas**: Arquivo processado mas cliente não identificado

**Verificações**:
1. Verificar se regras de cliente estão ativas (`flg_active = 1`)
2. Verificar se adquirente está correto
3. Verificar se TODAS as regras do cliente retornam true
4. Verificar posições de substring (`num_starting_position`, `num_ending_position`)

### Problema: Layout não identificado

**Sintomas**: Cliente identificado mas layout não

**Verificações**:
1. Verificar se regras de layout estão ativas
2. Verificar se cliente e adquirente correspondem
3. Para regras HEADER, verificar se primeiros 7000 bytes contêm o valor esperado
4. Verificar se TODAS as regras do layout retornam true

### Problema: Arquivo não aparece no destino

**Sintomas**: Processamento concluído mas arquivo não está no S3/SFTP

**Verificações**:
1. Verificar mapeamento origem-destino (`sever_paths_in_out`)
2. Verificar credenciais do Vault
3. Verificar logs de upload
4. Verificar rastreabilidade (etapa PROCESSING)

## Scripts Auxiliares

Crie scripts auxiliares para facilitar testes:

### `scripts/populate-test-data.sh`
```bash
#!/bin/bash
sqlplus user/password@database @test-data/sql/01-insert-servers.sql
sqlplus user/password@database @test-data/sql/02-insert-layouts.sql
sqlplus user/password@database @test-data/sql/03-insert-clients.sql
sqlplus user/password@database @test-data/sql/04-insert-acquirers.sql
```

### `scripts/copy-test-files.sh`
```bash
#!/bin/bash
CASE_TYPE=$1  # positive, negative, edge

if [ "$CASE_TYPE" = "positive" ]; then
  cp test-data/files/positive-cases/* /path/to/sftp/
elif [ "$CASE_TYPE" = "negative" ]; then
  cp test-data/files/negative-cases/* /path/to/sftp/
elif [ "$CASE_TYPE" = "edge" ]; then
  cp test-data/files/edge-cases/* /path/to/sftp/
fi
```

## Referências

- **Requirements**: `.kiro/specs/controle-de-arquivos/requirements.md`
- **Design**: `.kiro/specs/controle-de-arquivos/design.md`
- **DDL Scripts**: `scripts/ddl/`
- **Docker Compose**: `docker-compose.yml`
