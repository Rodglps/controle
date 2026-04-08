# Identificação de Clientes

## Visão Geral

A funcionalidade de identificação de clientes permite identificar automaticamente qual(is) cliente(s) é(são) proprietário(s) de cada arquivo EDI processado. A identificação ocorre no Consumer durante a transferência, logo após a identificação do layout, utilizando o mesmo buffer de 7000 bytes já carregado.

## Características Principais

- **Múltiplos Clientes**: Um arquivo pode pertencer a múltiplos clientes simultaneamente
- **Reutilização de Buffer**: Usa o mesmo buffer já carregado para identificação de layout
- **Operador AND**: Todas as regras de um cliente devem ser satisfeitas para identificação
- **Continuidade**: Falha na identificação não interrompe a transferência do arquivo
- **Filtro por Adquirente**: Apenas regras da adquirente correspondente são avaliadas

## Momento da Identificação

A identificação do cliente acontece no **Consumer durante a transferência**, quando já tivermos a primeira quantidade de bytes do arquivo sendo lido, logo após a identificação do layout.

**Dependência do Layout:**
- Se o layout NÃO foi identificado: apenas regras com `des_value_origin = 'FILENAME'` são avaliadas
- Se o layout foi identificado: regras FILENAME + regras de conteúdo (HEADER, TAG, KEY) são avaliadas

## Tipos de Identificação

### 1. FILENAME
Identifica o cliente baseado no nome do arquivo.

**Exemplo:**
```sql
-- Cliente identificado se o nome do arquivo contém "1234567890" E começa com "cielo"
INSERT INTO customer_identification_rule 
(des_value_origin, des_criteria_type, des_value)
VALUES
('FILENAME', 'CONTEM', '1234567890'),
('FILENAME', 'COMECA_COM', 'cielo');
```

### 2. HEADER (TXT)
Extrai valores de arquivos TXT usando byte offset (0-indexed).

**Exemplo:**
```sql
-- Bytes 0-4 devem ser "VENDA" E bytes 10-19 devem conter "1525"
INSERT INTO customer_identification_rule 
(des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value)
VALUES
('HEADER', 'IGUAL', 0, 4, 'VENDA'),
('HEADER', 'CONTEM', 10, 19, '1525');
```

### 3. HEADER (CSV)
Extrai valores de arquivos CSV usando índice de coluna (0-indexed).

**Exemplo:**
```sql
-- Coluna 0 deve ser "EEVD" E coluna 2 deve conter "1530"
INSERT INTO customer_identification_rule 
(des_value_origin, des_criteria_type, num_start_position, des_value)
VALUES
('HEADER', 'IGUAL', 0, 'EEVD'),
('HEADER', 'CONTEM', 2, '1530');
```

### 4. TAG (XML)
Extrai valores de arquivos XML usando XPath.

**Exemplo:**
```sql
-- Tag "root/metadata/version" deve ser igual a "1.0"
INSERT INTO customer_identification_rule 
(des_value_origin, des_criteria_type, des_tag, des_value)
VALUES
('TAG', 'IGUAL', 'root/metadata/version', '1.0');
```

### 5. KEY (JSON)
Extrai valores de arquivos JSON usando notação de ponto.

**Exemplo:**
```sql
-- Chave "metadata.version" deve ser igual a "1.0"
INSERT INTO customer_identification_rule 
(des_value_origin, des_criteria_type, des_key, des_value)
VALUES
('KEY', 'IGUAL', 'metadata.version', '1.0');
```

## Critérios de Comparação

- **COMECA_COM**: Valor extraído começa com o valor esperado
- **TERMINA_COM**: Valor extraído termina com o valor esperado
- **CONTEM**: Valor extraído contém o valor esperado
- **CONTIDO**: Valor esperado contém o valor extraído
- **IGUAL**: Valor extraído é exatamente igual ao valor esperado

## Funções de Transformação

Permitem comparações mais flexíveis aplicando transformações antes da comparação:

- **UPPERCASE**: Converte para maiúsculas
- **LOWERCASE**: Converte para minúsculas
- **INITCAP**: Primeira letra maiúscula, demais minúsculas
- **TRIM**: Remove espaços em branco no início e fim
- **NONE**: Nenhuma transformação (case-sensitive)

**Exemplo de uso:**
```sql
-- Comparação case-insensitive
INSERT INTO customer_identification_rule 
(des_value_origin, des_criteria_type, des_value, des_function_origin, des_function_dest)
VALUES
('FILENAME', 'CONTEM', 'cielo', 'LOWERCASE', 'LOWERCASE');
```

## Algoritmo de Identificação

1. **Buscar Identificações**: Busca `customer_identification` ativos filtrados por acquirer (e layout se identificado)
2. **Ordenação**: Ordena por `num_process_weight DESC` (mais relevantes primeiro)
3. **Para cada identificação**:
   - Busca todas as regras ativas (`flg_active=1`)
   - Aplica operador AND: TODAS as regras devem ser satisfeitas
   - Se todas satisfeitas, adiciona `idt_client` à lista de identificados
4. **Persistência**: Registra todos os clientes identificados em `file_origin_clients`
5. **Continuidade**: Se nenhum cliente identificado, continua processamento normalmente

## Estrutura de Dados

### Tabela: customer_identification

Armazena configurações de identificação de clientes.

**Campos principais:**
- `idt_identification`: ID da identificação (PK)
- `idt_client`: ID do cliente (referência externa)
- `idt_acquirer`: ID da adquirente (filtro obrigatório)
- `idt_layout`: ID do layout (opcional, para regras de conteúdo)
- `num_process_weight`: Peso para ordenação (maior = mais relevante)
- `flg_active`: Indica se está ativa (1) ou inativa (0)

### Tabela: customer_identification_rule

Define regras para identificação de clientes.

**Campos principais:**
- `idt_rule`: ID da regra (PK)
- `idt_identification`: FK para customer_identification
- `des_value_origin`: Tipo de origem (FILENAME, HEADER, TAG, KEY)
- `des_criteria_type`: Tipo de critério (COMECA_COM, TERMINA_COM, etc.)
- `des_value`: Valor esperado
- `des_tag`: Caminho XML (para TAG)
- `des_key`: Caminho JSON (para KEY)
- `num_start_position`: Posição inicial (TXT/CSV)
- `num_end_position`: Posição final (TXT)
- `des_function_origin`: Função de transformação na origem
- `des_function_dest`: Função de transformação no destino
- `flg_active`: Indica se está ativa (1) ou inativa (0)

### Tabela: file_origin_clients

Rastreia clientes identificados para cada arquivo.

**Campos principais:**
- `idt_client_identified`: ID do registro (PK)
- `idt_file_origin`: FK para file_origin
- `idt_client`: ID do cliente identificado
- `dat_creation`: Data de criação

**Constraint única:** `(idt_file_origin, idt_client)` - Previne duplicação

## Exemplos de Configuração

### Exemplo 1: Múltiplos Clientes por FILENAME

```sql
-- Cliente 15: Arquivo deve conter "1234567890" E começar com "cielo"
INSERT INTO customer_identification 
(idt_client, idt_acquirer, num_process_weight, flg_active)
VALUES (15, 1, 100, 1);

INSERT INTO customer_identification_rule 
(idt_identification, des_rule, des_value_origin, des_criteria_type, des_value, flg_active)
VALUES
(1, 'Contém CNPJ', 'FILENAME', 'CONTEM', '1234567890', 1),
(1, 'Começa com cielo', 'FILENAME', 'COMECA_COM', 'cielo', 1);

-- Cliente 20: Arquivo deve conter "premium"
INSERT INTO customer_identification 
(idt_client, idt_acquirer, num_process_weight, flg_active)
VALUES (20, 1, 200, 1);

INSERT INTO customer_identification_rule 
(idt_identification, des_rule, des_value_origin, des_criteria_type, des_value, flg_active)
VALUES
(2, 'Contém premium', 'FILENAME', 'CONTEM', 'premium', 1);

-- Arquivo: "cielo_1234567890_premium_20250101.txt"
-- Resultado: Ambos os clientes (15 e 20) são identificados
```

### Exemplo 2: Identificação por HEADER em TXT

```sql
-- Cliente 25: Layout VENDA (idt_layout=1), bytes 0-4 = "VENDA" E bytes 10-19 contém "1525"
INSERT INTO customer_identification 
(idt_client, idt_acquirer, idt_layout, num_process_weight, flg_active)
VALUES (25, 1, 1, 150, 1);

INSERT INTO customer_identification_rule 
(idt_identification, des_rule, des_value_origin, des_criteria_type, 
 num_start_position, num_end_position, des_value, flg_active)
VALUES
(3, 'Tipo VENDA', 'HEADER', 'IGUAL', 0, 4, 'VENDA', 1),
(3, 'Merchant 1525', 'HEADER', 'CONTEM', 10, 19, '1525', 1);
```

### Exemplo 3: Identificação por HEADER em CSV

```sql
-- Cliente 30: Layout EEVD (idt_layout=3), coluna 0 = "EEVD" E coluna 2 contém "1530"
INSERT INTO customer_identification 
(idt_client, idt_acquirer, idt_layout, num_process_weight, flg_active)
VALUES (30, 2, 3, 180, 1);

INSERT INTO customer_identification_rule 
(idt_identification, des_rule, des_value_origin, des_criteria_type, 
 num_start_position, des_value, flg_active)
VALUES
(4, 'Tipo EEVD', 'HEADER', 'IGUAL', 0, 'EEVD', 1),
(4, 'Merchant 1530', 'HEADER', 'CONTEM', 2, '1530', 1);
```

## Tratamento de Erros

### Erros de Extração (TAG/KEY)

Quando ocorre erro ao extrair TAG ou KEY (XML inválido, JSON inválido, caminho não encontrado):
- O erro é registrado em log com informações suficientes para identificar a falha
- O processamento continua para a próxima regra de identificação
- Se não houver mais regras, o cliente não será identificado

### Duplicação de Clientes

Tentativas de inserir o mesmo cliente para o mesmo arquivo:
- A constraint única `(idt_file_origin, idt_client)` previne a duplicação
- O erro é registrado em log como warning
- O processamento continua normalmente

### Nenhum Cliente Identificado

Quando nenhum cliente é identificado:
- O processamento continua normalmente
- Nenhum registro é inserido em `file_origin_clients`
- O arquivo é finalizado com `step='COLETA'` e `status='CONCLUIDO'`

## Queries Úteis

### Listar identificações ativas
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

### Listar regras de um cliente
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

### Verificar clientes de um arquivo
```sql
SELECT 
    foc.idt_client,
    foc.dat_creation,
    fo.des_file_name
FROM file_origin_clients foc
JOIN file_origin fo ON foc.idt_file_origin = fo.idt_file_origin
WHERE foc.idt_file_origin = 1;
```

### Arquivos com múltiplos clientes
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

## Componentes Técnicos

### CustomerIdentificationService

Serviço principal responsável pela identificação de clientes.

**Localização:** `com.concil.edi.consumer.service.customer.CustomerIdentificationService`

**Método principal:**
```java
public List<Long> identifyCustomers(
    byte[] buffer, 
    String filename, 
    Long acquirerId, 
    Long layoutId)
```

### Componentes Compartilhados (Commons)

Reutilizados entre identificação de layout e identificação de clientes:

- **ValueExtractor**: Interface para estratégias de extração
  - FilenameExtractor
  - HeaderTxtExtractor
  - HeaderCsvExtractor
  - XmlTagExtractor
  - JsonKeyExtractor
- **CriteriaComparator**: Lógica de comparação de critérios
- **TransformationApplier**: Aplicação de funções de transformação
- **RuleValidator**: Validação de configuração de regras
- **IdentificationRule**: Interface comum para regras

## Integração no Fluxo de Transferência

```
1. FileTransferListener recebe mensagem RabbitMQ
2. Atualiza status para PROCESSAMENTO
3. Abre InputStream do arquivo SFTP
4. LayoutIdentificationService identifica layout (lê buffer de 7000 bytes)
5. Atualiza file_origin.idt_layout
6. ✨ CustomerIdentificationService identifica clientes (reutiliza buffer)
7. ✨ Registra clientes identificados em file_origin_clients
8. Reabre InputStream para upload
9. FileUploadService faz upload para destino
10. Atualiza status para CONCLUIDO
```

## Configuração

### Variável de Ambiente

- **FILE_ORIGIN_BUFFER_LIMIT**: Tamanho do buffer em bytes para leitura inicial
  - Padrão: `7000`
  - Usado tanto para identificação de layout quanto de clientes

## Testes

### Testes E2E Implementados

1. **Teste 1**: Múltiplos clientes por FILENAME
   - Arquivo: `cielo_1234567890_premium_20250101_venda.txt`
   - Resultado: Clientes 15 e 20 identificados

2. **Teste 2**: Nenhum cliente identificado
   - Arquivo: `rede_9999999999_standard_20250101.txt`
   - Resultado: Nenhum cliente, processamento continua

3. **Teste 3**: Identificação por HEADER em TXT
   - Arquivo TXT com primeira linha: `VENDA     1525      20250101...`
   - Resultado: Cliente 25 identificado

4. **Teste 4**: Identificação por HEADER em CSV
   - Arquivo CSV: `EEVD;20250101;1530;100.00;APROVADO`
   - Resultado: Cliente 30 identificado

### Executar Testes

```bash
# Testes E2E (requer docker-compose rodando)
make e2e

# Testes unitários
mvn test -Dtest=CustomerIdentificationServiceTest

# Testes de propriedade
mvn test -Dtest=CustomerIdentificationServicePropertyTest
```

## Referências

- [Especificação](.kiro/specs/identificacao_clientes/prompt.md)
- [Design Técnico](.kiro/specs/identificacao_clientes/design.md)
- [Plano de Implementação](.kiro/specs/identificacao_clientes/tasks.md)
- [Scripts DDL](../../scripts/ddl/)
