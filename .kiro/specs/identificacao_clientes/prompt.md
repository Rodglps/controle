## Introdução

Estamos adicionando uma nova funcionalidade chamada identificação de clientes.
Essa funcionalidade tem como objetivo identificar de qual cliente pertence o arquivo que está sendo lido.
O arquivo pode pertencer a mais de um cliente de acordo com a configuração que foi cadastrada.
O processo de identificação do cliente é bem semelhante ao processo de identificação do layout, porém o objetivo será identificar o cliente.

## Momento da Identificação

A identificação do cliente deve acontecer no **Consumer durante a transferência**, quando já tivermos a primeira quantidade de bytes do arquivo sendo lido para o processo de transferência, logo após a identificação do layout.
Caso o Layout não tenha sido identificado, a identificação do cliente pode acontecer, porém apenas para o des_value_origin = 'FILENAME'.
Os demais des_value_origin 'HEADER', 'TAG' e 'KEY' precisam do layout_id para serem executados.

**Tratamento de falha na identificação:**
- Se o cliente do arquivo não for identificado, devemos:
  - Continuar o processamento da coleta.
  - Finalizar o processamento com `step = 'COLETA'` e `status = 'CONCLUIDO'`
  - Não registrar a tabela file_origin_clients.

## Algoritmo de Identificação

A identificação do cliente será feita aplicando as regras cadastradas na tabela `customer_identification_rule` levando em consideração:
- `des_criteria_type`: Tipo de critério (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL)
- `des_value_origin`: Forma de obtenção do texto na string de origem (HEADER, TAG, FILENAME, KEY)

**Tipos de citérios**
- "COMECA_COM": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) inicia com a string cadastrada na coluna des_value.
- "TERMINA_COM": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) termina com a string cadastrada na coluna des_value.
- "CONTEM": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) contém com a string cadastrada na coluna des_value.
- "CONTIDO": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) está contido na string cadastrada na coluna des_value.
- "IGUAL": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) é igual ao valor da string cadastrada na coluna des_value.

**Regras de composição:**
- Utilizar o operador **AND** entre todas as regras cadastradas para o mesmo adquirente
- A identificação deve satisfazer **TODAS** as regras ativas (`flg_active=1`) para que o cliente seja considerado como identificado
- Apenas customer_identification e customer_identification_rule com `flg_active=1` devem ser considerados
- Todos os customer_identification cujo sua regras atendam aos valores contidos no arquivo devem ser identificados.
- Ordenação: `num_process_weight DESC` (customer_identification mais relevantes primeiro)

**Filtro por adquirente:**
- O `idt_acquirer` é obtido da leitura da tabela `SERVER_PATHS`
- Esse `idt_acquirer` sempre será utilizado como filtro para buscar apenas customer_identification_rule da adquirente correspondente na tabela `customer_identification`.

**Filtro por adquirente e layout:**

A lógica de filtro varia conforme o layout foi ou não identificado:

**SE layout identificado:**
- Query 1: Buscar `customer_identification` com filtro `idt_acquirer` + `des_value_origin='FILENAME'`
- Query 2: Buscar `customer_identification` com filtro `idt_acquirer` + `idt_layout` + `des_value_origin IN ('HEADER','TAG','KEY')`
- Resultado final: UNION de Query 1 e Query 2

**SE layout NÃO identificado:**
- Query única: Buscar `customer_identification` com filtro `idt_acquirer` + `des_value_origin='FILENAME'`

**Relacionamento para buscas:**
- Utilizaremos o seguinte relacionamento para as queries: `layout` > `customer_identification` > `customer_identification_rule`
- O registro do layout não é obrigatório, permitindo a identificação através de um valor contido no nome do arquivo
- Quando o layout for identificado, podemos encontrar o cliente através de uma informação contida no conteúdo do arquivo (HEADER, TAG, KEY), esse método é complementar e não deve interferir na identificação através do FILENAME

## Tipos de Origem para Identificação

### 1. FILENAME
- A regra é aplicada no **nome do arquivo**
- Compara com o valor do campo `des_value`
- Não requer leitura do conteúdo do arquivo

### 2. HEADER
- Para arquivos **TXT**: Extração posicional por **byte offset**
  - `num_start_position`: Posição inicial do byte (0-indexed)
  - `num_end_position`: Posição final do byte (se NULL, extrai até o fim da linha ou dos 7000 bytes)
  - Lêa primeira linha dentro da extração dos primeiros 7000 bytes e tenta a identificação.
  - Caso durante a extração não exista o caractere de quebra de linha, entenderemos que os 7000 bytes representam uma única linha.
- Para arquivos **CSV**: Extração por **índice de coluna**
  - `num_start_position`: Índice da coluna (0-indexed)
  - `num_end_position`: Não utilizado para CSV
  - Usa o separador definido em `des_column_separator` da tabela `layout`
  - Lê linha por linha dentro da extração dos primeiros 7000 bytes e tenta a identificação a cada linha.
  - Caso durante a extração não exista o caractere de quebra de linha, entenderemos que os 7000 bytes representam uma única linha.
- Compara com o valor do campo `des_value`

### 3. TAG (arquivos XML)
- Suporta **caminhos aninhados** usando XPath ou notação de caminho
- O caminho para extração da informação está declarado na coluna `des_tag`
- Exemplo: `des_tag = "root/metadata/version"` ou `des_tag = "//version"`
- Valida dentro dos primeiros 7000 bytes iniciais
- Compara o valor extraído com o campo `des_value`
- **Tratamento de erro**: Se houver erro ao ler a TAG (XML inválido, caminho não encontrado), registrar log com informações suficientes para identificar a falha e continuar para a próxima regra de `customer_identification`. Se não houver mais regras, o cliente não será identificado.

### 4. KEY (arquivos JSON)
- Suporta **caminhos aninhados** usando notação de ponto
- O caminho para extração está declarado na coluna `des_key`
- Exemplo: `des_key = "metadata.version"` ou `des_key = "header.acquirer.name"`
- Valida dentro dos primeiros 7000 bytes iniciais
- Compara o valor extraído com o campo `des_value`
- **Tratamento de erro**: Se houver erro ao ler a KEY (JSON inválido, caminho não encontrado), registrar log com informações suficientes para identificar a falha e continuar para a próxima regra de `customer_identification`. Se não houver mais regras, o cliente não será identificado.

## Configuração de Buffer

Será utilizado o buffer configurado na mesma variável de ambiente já utilizada para a transferencia e identificação do layout:
- **Nome**: `FILE_ORIGIN_BUFFER_LIMIT`
- **Valor inicial**: `7000`

## Funções de Transformação

Para permitir comparações mais flexíveis, temos duas colunas na tabela `customer_identification_rule`:
- `des_function_origin`: Função a ser aplicada no valor de origem antes da comparação
- `des_function_dest`: Função a ser aplicada no valor de destino (`des_value`) antes da comparação

**Funções suportadas:**
- `UPPERCASE`: Converte para maiúsculas
- `LOWERCASE`: Converte para minúsculas
- `INITCAP`: Primeira letra maiúscula, demais minúsculas
- `TRIM`: Remove espaços em branco no início e fim
- `NULL`: Nenhuma transformação

**Exemplo de uso:**
- Regra com `des_function_origin='LOWERCASE'` e `des_function_dest='LOWERCASE'` permite comparação case-insensitive
- Regra com `des_function_origin='TRIM'` remove espaços antes da comparação

**Nota:** As comparações são **case-sensitive** por padrão (quando nenhuma função é aplicada).

## Retorno do Serviço de Identificação

O serviço deve retornar:
- `idt_client`: Identificador do cliente
- Esse valor será utilizado para preencher os registros na tabela file_origin_clients.

**Nota:** O campo `idt_acquirer` já é obtido da leitura da tabela `SERVER_PATHS` e não precisa ser retornado pelo serviço de identificação.

## Estrutura de tabelas e Enums para identificação do cliente

Table customer_identification [note: '[NOT_SECURITY_APPLY] - Tabela relacionada a indetificação de clientes donos dos arquivos EDI.']{
    idt_identification number(20) [primary key, increment, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro de identificação, ex.: 1, 2, 3, ...']
    idt_client number(20) [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do cliente. Referencia da coluna client_id da tabela tb_client que pertence ao domínio de clientes (Recon customer, dependencia externa), ex.: 1, 2, 3, ...']
    idt_acquirer number(20) [not null, note: '[NOT_SECURITY_APPLY] - Código interno da adquirente a qual o layout pertence. Referencia da coluna idt_acquirer da tabela acquirer que pertence ao domínio do OPT-IN (dependência externa)']
    idt_layout   number(19) [null, note: '[NOT_SECURITY_APPLY] - Identificador interno do layout, referencia a tabela layout , ex.: 1, 2, 3, ...']
    idt_merchant number(20) [null, note: '[NOT_SECURITY_APPLY] - identificador interno do estabelecimento. Referencia externa (merchant_id da tabela tb_merchants, domínio externo)']
    dat_start date [null, note: '[NOT_SECURITY_APPLY] - Data de inicio da vingencia da identificação. Para casos que existe a necessidade de ter identificações ativas por um período.']
    dat_end date [null, note: '[NOT_SECURITY_APPLY] - Data fim da vingencia da identificação. Para casos que existe a necessidade de ter identificações ativas por um período.']
    idt_plan number(20) [null, note: '[NOT_SECURITY_APPLY] - identificador interno do plano, caso haja necessidade de regras específicas pelo plano contratado.']
    flg_is_priority number(1) [null, note: '[NOT_SECURITY_APPLY] - flag para indicar se aquela regra é prioritária em relação as demais regras do mesmo cliente. 0 = NÃO | 1 = SIM']
    num_process_weight number(5) [null, note: '[NOT_SECURITY_APPLY] - Peso da regra de identificação. Quanto maior o peso, maior a probabilidade de acerto para essa regra.']
    dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da criação do registro']
    dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent varchar2(50) [not null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a última alteração']
    flg_active number(1) [not null, note: '[NOT_SECURITY_APPLY] - Indica se a configuração do layout está ativa (1) ou inativa (0)']
} ref: customer_identification.idt_layout > layout.idt_layout

Enum value_origin {
    "HEADER"   [note: 'A identificação é feita pelo cabeçalho do arquivo (primeira linha ou primeiros 7000 bytes)']
    "TAG"      [note: 'A identificação é feita por uma tag/caminho XML. Suporta caminhos aninhados']
    "FILENAME" [note: 'A identificação é feita pelo nome do arquivo']
    "KEY"      [note: 'A identificação é feita por uma chave JSON. Suporta caminhos aninhados']
}

Enum criteria_type {
    "COMECA_COM"  [note: 'Verifica se o valor de origem começa com o valor esperado']
    "TERMINA_COM" [note: 'Verifica se o valor de origem termina com o valor esperado']
    "CONTEM"      [note: 'Verifica se o valor de origem contém o valor esperado']
    "CONTIDO"     [note: 'Verifica se o valor de origem está contido no valor esperado']
    "IGUAL"       [note: 'Verifica se o valor de origem é exatamente igual ao valor esperado']
}

Enum function_type {
    "UPPERCASE" [note: 'Converte para maiúsculas']
    "LOWERCASE" [note: 'Converte para minúsculas']
    "INITCAP"   [note: 'Primeira letra maiúscula, demais minúsculas']
    "TRIM"      [note: 'Remove espaços em branco no início e fim']
    "NONE"      [note: 'Nenhuma transformação']
}

Table customer_identification_rule [note: '[NOT_SECURITY_APPLY] - Tabela com dados relacionados a identificação do cliente comparando informações de extração do arquivo.'] {
    idt_rule             number(20)     [primary key, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro']
    idt_identification   number(20)     [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro de identificação do cliente, faz referência à tabela customer_identification']
    des_rule             varchar2(255)  [not null, note: '[NOT_SECURITY_APPLY] - Explicação do critério de identificação.']
    des_value_origin     value_origin   [not null, note: '[NOT_SECURITY_APPLY] - Tipo de origem da informação: HEADER | TAG | FILENAME | KEY']
    des_criteria_type    criteria_type  [not null, note: '[NOT_SECURITY_APPLY] - Tipo de critério para comparação: COMECA_COM | TERMINA_COM | CONTEM | CONTIDO | IGUAL']
    num_start_position   number(5)      [null, note: '[NOT_SECURITY_APPLY] - Para TXT: byte offset inicial (0-indexed). Para CSV: índice da coluna (0-indexed). Para JSON/XML: não utilizado']
    num_end_position     number(5)      [null, note: '[NOT_SECURITY_APPLY] - Para TXT: byte offset final. Para CSV: não utilizado. Para JSON/XML: não utilizado']
    des_value            varchar2(255)  [null, note: '[NOT_SECURITY_APPLY] - Valor esperado para comparação (usado com FILENAME e HEADER)']
    des_tag              varchar2(255)  [null, note: '[NOT_SECURITY_APPLY] - Caminho XPath para extração em arquivos XML. Suporta caminhos aninhados. Ex: root/metadata/version']
    des_key              varchar2(255)  [null, note: '[NOT_SECURITY_APPLY] - Caminho JSON para extração em arquivos JSON. Suporta caminhos aninhados. Ex: metadata.version']
    des_function_origin  function_type  [null, note: '[NOT_SECURITY_APPLY] - Função a ser aplicada no valor de origem antes da comparação: UPPERCASE | LOWERCASE | INITCAP | TRIM | NONE']
    des_function_dest    function_type  [null, note: '[NOT_SECURITY_APPLY] - Função a ser aplicada no valor de destino antes da comparação: UPPERCASE | LOWERCASE | INITCAP | TRIM | NONE']
    dat_creation         date           [not null, note: '[NOT_SECURITY_APPLY] - Data e hora de criação do registro']
    dat_update           date           [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent     varchar2(50)   [not null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a última alteração no registro']
    flg_active           number(1)      [not null, note: '[NOT_SECURITY_APPLY] - Indica se a regra está ativa (1) ou inativa (0)']
} ref: customer_identification_rule.idt_identification > customer_identification.idt_identification

Table file_origin_clients [note: '[NOT_SECURITY_APPLY] - Tabela com dados relacionados clientes que foram identificados como donos do arquivo.'] {
    idt_client_identified number(20)     [increment, primary key, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro']
    idt_file_origin       number(19)     [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do arquivo ex: 1 | 2 | 3']
    idt_client            number(20)     [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do cliente. Referencia da coluna client_id da tabela tb_client que pertence ao domínio de clientes (Recon customer, dependencia externa), ex.: 1, 2, 3, ...']
    dat_creation          date           [not null, note: '[NOT_SECURITY_APPLY] - Data e hora de criação do registro']
    dat_update            date           [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    
    Indexes {
        (idt_file_origin, idt_client) [unique, note: 'Previne duplicação de cliente para o mesmo arquivo']
    }
} ref clientes_identificados: file_origin_clients.idt_file_origin > file_origin.idt_file_origin



## Exemplos de configuração

**Identificação pelo filename CIELO**
insert into customer_identification 
(idt_identification ,idt_client ,idt_acquirer ,idt_layout ,idt_merchant ,dat_start ,dat_end ,idt_plan ,flg_is_priority ,num_process_weight ,dat_creation ,dat_update ,nam_change_agent ,flg_active)
values 
(1, 15, 1, NULL, 1515, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 100, SYSDATE, NULL, 'SETUP', 1);


insert into customer_identification_rule 
(idt_rule ,idt_identification ,des_rule ,des_value_origin ,des_criteria_type ,num_start_position ,num_end_position ,des_value ,des_tag ,des_key ,des_function_origin ,des_function_dest ,dat_creation ,dat_update ,nam_change_agent ,flg_active )
values
(1, 1, 'Identificação pelo filename para arquivo CIELO', 'FILENAME', 'CONTEM', NULL, NULL, '1234567890', NULL, NULL, NULL,NULL, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule ,idt_identification ,des_rule ,des_value_origin ,des_criteria_type ,num_start_position ,num_end_position ,des_value ,des_tag ,des_key ,des_function_origin ,des_function_dest ,dat_creation ,dat_update ,nam_change_agent ,flg_active )
values
(2, 1, 'Identificação pelo filename para arquivo CIELO', 'FILENAME', 'COMECA_COM', NULL, NULL, 'cielo', NULL, NULL, NULL,NULL, SYSDATE, NULL, 'SETUP', 1);

**Identificação pelo filename REDE**
insert into customer_identification 
(idt_identification ,idt_client ,idt_acquirer ,idt_layout ,idt_merchant ,dat_start ,dat_end ,idt_plan ,flg_is_priority ,num_process_weight ,dat_creation ,dat_update ,nam_change_agent ,flg_active)
values 
(2, 15, 2, NULL, 1525, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 100, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule ,idt_identification ,des_rule ,des_value_origin ,des_criteria_type ,num_start_position ,num_end_position ,des_value ,des_tag ,des_key ,des_function_origin ,des_function_dest ,dat_creation ,dat_update ,nam_change_agent ,flg_active )
values
(3, 2, 'Identificação pelo filename para arquivo REDE', 'FILENAME', 'COMECA_COM', NULL, NULL, 'rede', NULL, NULL, NULL,NULL, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule ,idt_identification ,des_rule ,des_value_origin ,des_criteria_type ,num_start_position ,num_end_position ,des_value ,des_tag ,des_key ,des_function_origin ,des_function_dest ,dat_creation ,dat_update ,nam_change_agent ,flg_active )
values
(4, 2, 'Identificação pelo filename para arquivo REDE', 'FILENAME', 'CONTEM', NULL, NULL, '1098765432', NULL, NULL, NULL,NULL, SYSDATE, NULL, 'SETUP', 1);

## Cenários de Teste

### Teste 1: Identificação por FILENAME - Múltiplos Clientes
**Objetivo:** Validar que um arquivo pode ser identificado por múltiplos clientes simultaneamente

**Configuração:**
```sql
-- Cliente 1 (idt_client=15) - CIELO
insert into customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
values 
(1, 15, 1, NULL, 1515, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 100, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(1, 1, 'Identificação pelo filename para arquivo CIELO - Cliente 15', 'FILENAME', 'CONTEM', NULL, NULL, '1234567890', NULL, NULL, 'NONE', 'NONE', SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(2, 1, 'Identificação pelo filename para arquivo CIELO - Cliente 15', 'FILENAME', 'COMECA_COM', NULL, NULL, 'cielo', NULL, NULL, 'LOWERCASE', 'LOWERCASE', SYSDATE, NULL, 'SETUP', 1);

-- Cliente 2 (idt_client=20) - CIELO (mesmo adquirente, cliente diferente)
insert into customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
values 
(3, 20, 1, NULL, 1520, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 1, 200, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(5, 3, 'Identificação pelo filename para arquivo CIELO - Cliente 20', 'FILENAME', 'CONTEM', NULL, NULL, 'premium', NULL, NULL, 'LOWERCASE', 'LOWERCASE', SYSDATE, NULL, 'SETUP', 1);
```

**Arquivo de teste:** `cielo_1234567890_premium_20250101_venda.txt`

**Resultado esperado:** 
- Ambos os clientes (15 e 20) devem ser identificados
- Dois registros na tabela `file_origin_clients`
- Cliente 20 aparece primeiro na ordenação (num_process_weight=200 > 100)

### Teste 2: Identificação por FILENAME - Nenhum Cliente Identificado
**Objetivo:** Validar que o processamento continua quando nenhum cliente é identificado

**Configuração:** Usar as mesmas configurações do Teste 1

**Arquivo de teste:** `rede_9999999999_standard_20250101.txt`

**Resultado esperado:**
- Nenhum cliente identificado
- Nenhum registro na tabela `file_origin_clients`
- Processamento finaliza com `step='COLETA'` e `status='CONCLUIDO'`

### Teste 3: Identificação por HEADER - Arquivo TXT com Layout Identificado
**Objetivo:** Validar identificação por byte offset em arquivo TXT

**Pré-requisito:** Layout CIELO_015_03_VENDA (idt_layout=1) deve estar identificado

**Configuração:**
```sql
-- Cliente 3 (idt_client=25) - CIELO com identificação por HEADER em TXT
insert into customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
values 
(4, 25, 1, 1, 1525, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 150, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(6, 4, 'Identificação por HEADER TXT - bytes 0-4 devem ser VENDA', 'HEADER', 'IGUAL', 0, 4, 'VENDA', NULL, NULL, 'TRIM', 'TRIM', SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(7, 4, 'Identificação por HEADER TXT - bytes 10-19 devem conter merchant', 'HEADER', 'CONTEM', 10, 19, '1525', NULL, NULL, 'NONE', 'NONE', SYSDATE, NULL, 'SETUP', 1);
```

**Arquivo de teste:** Arquivo TXT com primeira linha: `VENDA     1525      20250101...`

**Resultado esperado:**
- Cliente 25 identificado
- Um registro na tabela `file_origin_clients`

### Teste 4: Identificação por HEADER - Arquivo CSV com Layout Identificado
**Objetivo:** Validar identificação por índice de coluna em arquivo CSV

**Pré-requisito:** Layout REDE_EEVD_CSV (idt_layout=3) deve estar identificado

**Configuração:**
```sql
-- Cliente 4 (idt_client=30) - REDE com identificação por HEADER em CSV
insert into customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
values 
(5, 30, 2, 3, 1530, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 180, SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(8, 5, 'Identificação por HEADER CSV - coluna 0 deve ser EEVD', 'HEADER', 'IGUAL', 0, NULL, 'EEVD', NULL, NULL, 'UPPERCASE', 'UPPERCASE', SYSDATE, NULL, 'SETUP', 1);

insert into customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
values
(9, 5, 'Identificação por HEADER CSV - coluna 2 deve conter merchant', 'HEADER', 'CONTEM', 2, NULL, '1530', NULL, NULL, 'NONE', 'NONE', SYSDATE, NULL, 'SETUP', 1);
```

**Arquivo de teste:** Arquivo CSV com linhas:
```
EEVD;20250101;1530;100.00;APROVADO
EEVD;20250101;1530;200.00;APROVADO
```

**Resultado esperado:**
- Cliente 30 identificado
- Um registro na tabela `file_origin_clients`

## O que vamos fazer ?

1 - Criar os scripts com a definição das novas tabelas e adicionar a pasta de ddl.
2 - Ajustar a inicialização do banco de dados para incluir os novos ddls e dmls.
3 - Adaptar o Consumer adicionando um serviço de identificação do cliente.
4 - Implementar testes E2E para os 4 cenários descritos acima.