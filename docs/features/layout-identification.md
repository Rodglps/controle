# Identificação de Layouts

## Visão Geral

A funcionalidade de identificação de layouts permite identificar automaticamente o layout (formato) de cada arquivo EDI processado. A identificação ocorre no Consumer durante a transferência, antes do upload para o destino, utilizando um buffer inicial do arquivo.

## Características Principais

- **First-Match Algorithm**: Retorna o primeiro layout que satisfaz todas as regras
- **Buffer-Based**: Lê apenas os primeiros 7000 bytes (configurável) para identificação
- **Operador AND**: Todas as regras de um layout devem ser satisfeitas para identificação
- **Encoding Detection**: Detecta e converte encoding automaticamente
- **Continuidade**: Falha na identificação não interrompe a transferência (layout 0)

## Momento da Identificação

A identificação do layout acontece no **Consumer durante a transferência**, quando já tivermos a primeira quantidade de bytes do arquivo sendo lido, antes do upload para o destino.

## Tipos de Identificação

### 1. FILENAME
Identifica o layout baseado no nome do arquivo.

**Exemplo:**
```sql
-- Layout identificado se o nome do arquivo contém "015_03" E contém "VENDA"
INSERT INTO layout_identification_rule 
(idt_layout, des_value_origin, des_criteria_type, des_value)
VALUES
(1, 'FILENAME', 'CONTEM', '015_03'),
(1, 'FILENAME', 'CONTEM', 'VENDA');
```

### 2. HEADER (TXT)
Extrai valores de arquivos TXT usando byte offset (0-indexed).

**Exemplo:**
```sql
-- Bytes 0-3 devem ser "EEVD"
INSERT INTO layout_identification_rule 
(idt_layout, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value)
VALUES
(2, 'HEADER', 'IGUAL', 0, 3, 'EEVD');
```

### 3. HEADER (CSV)
Extrai valores de arquivos CSV usando índice de coluna (0-indexed).

**Exemplo:**
```sql
-- Coluna 0 deve ser "EEVD"
INSERT INTO layout_identification_rule 
(idt_layout, des_value_origin, des_criteria_type, num_start_position, des_value)
VALUES
(3, 'HEADER', 'IGUAL', 0, 'EEVD');
```

### 4. TAG (XML)
Extrai valores de arquivos XML usando XPath.

**Exemplo:**
```sql
-- Tag "root/metadata/version" deve ser igual a "1.0"
INSERT INTO layout_identification_rule 
(idt_layout, des_value_origin, des_criteria_type, des_tag, des_value)
VALUES
(4, 'TAG', 'IGUAL', 'root/metadata/version', '1.0');
```

### 5. KEY (JSON)
Extrai valores de arquivos JSON usando notação de ponto.

**Exemplo:**
```sql
-- Chave "metadata.version" deve ser igual a "1.0"
INSERT INTO layout_identification_rule 
(idt_layout, des_value_origin, des_criteria_type, des_key, des_value)
VALUES
(5, 'KEY', 'IGUAL', 'metadata.version', '1.0');
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
INSERT INTO layout_identification_rule 
(idt_layout, des_value_origin, des_criteria_type, des_value, des_function_origin, des_function_dest)
VALUES
(1, 'FILENAME', 'CONTEM', 'venda', 'LOWERCASE', 'LOWERCASE');
```

## Algoritmo de Identificação

1. **Buscar Layouts**: Busca `layout` ativos filtrados por acquirer
2. **Ordenação**: Ordena por `idt_layout DESC` (layouts mais recentes primeiro)
3. **Para cada layout**:
   - Busca todas as regras ativas (`flg_active=1`)
   - Aplica operador AND: TODAS as regras devem ser satisfeitas
   - Se todas satisfeitas, retorna `idt_layout` (first-match wins)
4. **Layout Não Identificado**: Se nenhum layout satisfaz, retorna `idt_layout = 0`
5. **Persistência**: Atualiza `file_origin.idt_layout`
6. **Continuidade**: Arquivo é transferido normalmente mesmo com layout 0

## Layout Especial: SEM_IDENTIFICACAO

Quando nenhum layout é identificado:
- O arquivo recebe `idt_layout = 0` (layout especial "SEM_IDENTIFICACAO")
- O arquivo é transferido normalmente para o destino
- Status final é `CONCLUIDO` (não é erro)
- Indica que novas regras de identificação podem ser necessárias

**Importante**: Apenas arquivos com status `ERRO` são retentados pelo Producer. Arquivos com `idt_layout = 0` e status `CONCLUIDO` não são retentados.

## Estrutura de Dados

### Tabela: layout

Armazena definições de layouts de arquivos EDI.

**Campos principais:**
- `idt_layout`: ID do layout (PK)
- `idt_acquirer`: ID da adquirente (filtro obrigatório)
- `cod_layout`: Código do layout (ex: CIELO_015_03_VENDA)
- `des_layout`: Descrição do layout
- `des_file_type`: Tipo de arquivo (CSV, TXT, JSON, XML, OFX)
- `des_encoding`: Encoding esperado (UTF-8, ISO-8859-1, etc.)
- `des_column_separator`: Separador de colunas para CSV
- `flg_active`: Indica se está ativo (1) ou inativo (0)

### Tabela: layout_identification_rule

Define regras para identificação de layouts.

**Campos principais:**
- `idt_layout_identification_rule`: ID da regra (PK)
- `idt_layout`: FK para layout
- `des_rule`: Descrição da regra
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

## Exemplos de Configuração

### Exemplo 1: Identificação por FILENAME

```sql
-- Layout CIELO_015_03_VENDA
INSERT INTO layout 
(idt_layout, idt_acquirer, cod_layout, des_layout, des_file_type, des_encoding, flg_active)
VALUES (1, 1, 'CIELO_015_03_VENDA', 'Cielo Venda 015.03', 'TXT', 'UTF-8', 1);

INSERT INTO layout_identification_rule 
(idt_layout, des_rule, des_value_origin, des_criteria_type, des_value, flg_active)
VALUES
(1, 'Nome contém 015_03', 'FILENAME', 'CONTEM', '015_03', 1),
(1, 'Nome contém VENDA', 'FILENAME', 'CONTEM', 'VENDA', 1);

-- Arquivo: "cielo_015_03_VENDA_20250101.txt"
-- Resultado: Layout 1 identificado
```

### Exemplo 2: Identificação por HEADER em TXT

```sql
-- Layout REDE_EEVC_TXT
INSERT INTO layout 
(idt_layout, idt_acquirer, cod_layout, des_layout, des_file_type, des_encoding, flg_active)
VALUES (2, 2, 'REDE_EEVC_TXT', 'Rede EEVC TXT', 'TXT', 'ISO-8859-1', 1);

INSERT INTO layout_identification_rule 
(idt_layout, des_rule, des_value_origin, des_criteria_type, 
 num_start_position, num_end_position, des_value, flg_active)
VALUES
(2, 'Bytes 0-3 = EEVC', 'HEADER', 'IGUAL', 0, 3, 'EEVC', 1);
```

### Exemplo 3: Identificação por HEADER em CSV

```sql
-- Layout REDE_EEVD_CSV
INSERT INTO layout 
(idt_layout, idt_acquirer, cod_layout, des_layout, des_file_type, 
 des_encoding, des_column_separator, flg_active)
VALUES (3, 2, 'REDE_EEVD_CSV', 'Rede EEVD CSV', 'CSV', 'UTF-8', ';', 1);

INSERT INTO layout_identification_rule 
(idt_layout, des_rule, des_value_origin, des_criteria_type, 
 num_start_position, des_value, flg_active)
VALUES
(3, 'Coluna 0 = EEVD', 'HEADER', 'IGUAL', 0, 'EEVD', 1);
```

## Encoding Detection

O sistema detecta automaticamente o encoding do arquivo e converte para UTF-8 se necessário:

1. Tenta detectar encoding usando biblioteca de detecção
2. Se detectado, converte buffer para UTF-8
3. Se falha na detecção, usa encoding configurado no layout
4. Se falha na conversão, usa buffer original

**Encodings suportados:**
- UTF-8
- ISO-8859-1 (Latin-1)
- Windows-1252
- US-ASCII

## Queries Úteis

### Listar layouts ativos
```sql
SELECT 
    idt_layout, 
    cod_layout, 
    des_layout, 
    des_file_type 
FROM layout 
WHERE flg_active = 1 
ORDER BY idt_layout DESC;
```

### Listar regras de um layout
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

### Estatísticas de identificação por layout
```sql
SELECT 
    l.cod_layout,
    l.des_layout,
    COUNT(fo.idt_file_origin) as total_files
FROM layout l
LEFT JOIN file_origin fo ON l.idt_layout = fo.idt_layout
WHERE l.flg_active = 1
GROUP BY l.cod_layout, l.des_layout
ORDER BY total_files DESC;
```

## Componentes Técnicos

### LayoutIdentificationService

Serviço principal responsável pela identificação de layouts.

**Localização:** `com.concil.edi.consumer.service.layout.LayoutIdentificationService`

**Método principal:**
```java
public Long identifyLayout(
    InputStream inputStream, 
    String filename, 
    Long acquirerId) throws IOException
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
- **EncodingConverter**: Detecção e conversão de encoding
- **IdentificationRule**: Interface comum para regras

## Integração no Fluxo de Transferência

```
1. FileTransferListener recebe mensagem RabbitMQ
2. Atualiza status para PROCESSAMENTO
3. Abre InputStream do arquivo SFTP
4. ✨ LayoutIdentificationService identifica layout (lê buffer de 7000 bytes)
5. ✨ Atualiza file_origin.idt_layout
6. CustomerIdentificationService identifica clientes (reutiliza buffer)
7. Registra clientes identificados em file_origin_clients
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

1. **Teste 1**: Identificação por FILENAME
   - Arquivo: `cielo_015_03_VENDA_20250101.txt`
   - Resultado: Layout 1 (CIELO_015_03_VENDA) identificado

2. **Teste 2**: Identificação por HEADER em TXT
   - Arquivo TXT com primeira linha: `EEVC...`
   - Resultado: Layout 2 (REDE_EEVC_TXT) identificado

3. **Teste 3**: Identificação por HEADER em CSV
   - Arquivo CSV: `EEVD;20250101;...`
   - Resultado: Layout 3 (REDE_EEVD_CSV) identificado

4. **Teste 4**: Layout não identificado
   - Arquivo sem regras correspondentes
   - Resultado: Layout 0 (SEM_IDENTIFICACAO), status CONCLUIDO

### Executar Testes

```bash
# Testes E2E (requer docker-compose rodando)
make e2e

# Testes unitários
mvn test -Dtest=LayoutIdentificationServiceTest

# Testes de propriedade
mvn test -Dtest=LayoutIdentificationServicePropertyTest
```

## Troubleshooting

### Layout não está sendo identificado

1. Verificar se o layout está ativo: `flg_active = 1`
2. Verificar se as regras estão ativas: `flg_active = 1`
3. Verificar se o acquirer está correto
4. Verificar se TODAS as regras estão sendo satisfeitas (operador AND)
5. Verificar logs do Consumer para detalhes da identificação

### Encoding incorreto

1. Verificar encoding configurado no layout: `des_encoding`
2. Verificar logs de detecção de encoding
3. Testar com encoding diferente
4. Considerar adicionar encoding ao EncodingConverter

### Buffer muito pequeno

Se o arquivo tem headers muito grandes:
1. Aumentar `FILE_ORIGIN_BUFFER_LIMIT` (padrão: 7000)
2. Reiniciar Consumer
3. Testar novamente

## Referências

- [Especificação](.kiro/specs/controle-arquivos-edi/requirements.md)
- [Design Técnico](.kiro/specs/controle-arquivos-edi/design.md)
- [Plano de Implementação](.kiro/specs/controle-arquivos-edi/tasks.md)
- [Scripts DDL](../../scripts/ddl/)
