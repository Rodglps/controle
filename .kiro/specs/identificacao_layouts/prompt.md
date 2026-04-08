## Introdução

Estamos adicionando uma nova funcionalidade chamada identificação de layouts.
Essa funcionalidade tem como objetivo identificar de qual layout e qual adquirente pertence o arquivo que está sendo lido.

## Momento da Identificação

A identificação do layout deve acontecer no **Consumer durante a transferência**, quando já tivermos a primeira quantidade de bytes do arquivo sendo lido para o processo de transferência.

**Tratamento de falha na identificação:**
- Se o layout do arquivo não for identificado, devemos:
  - Interromper a transferência do arquivo
  - Finalizar o processamento com `step = 'COLETA'` e `status = 'ERRO'`
  - Registrar a mensagem: "Layout do arquivo não foi identificado"

## Algoritmo de Identificação

A identificação do layout será feita aplicando as regras cadastradas na tabela `layout_identification_rule` levando em consideração:
- `des_criteria_type`: Tipo de critério (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL)
- `des_value_origin`: Forma de obtenção do texto na string de origem (HEADER, TAG, FILENAME, KEY)

**Tipos de citérios**
- "COMECA_COM": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) inicia com a string cadastrada na coluna des_value.
- "TERMINA_COM": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) termina com a string cadastrada na coluna des_value.
- "CONTEM": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) contém com a string cadastrada na coluna des_value.
- "CONTIDO": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) está contido na string cadastrada na coluna des_value.
- "IGUAL": devemos verificar se o valor obtido pela origem (HEADER,TAG,FILENAME,KEY) é igual ao valor da string cadastrada na coluna des_value.

**Regras de composição:**
- Utilizar o operador **AND** entre todas as regras cadastradas para o mesmo layout
- A identificação deve satisfazer **TODAS** as regras ativas (`flg_active=1`) para que o layout seja considerado como identificado
- Apenas layouts com `flg_active=1` devem ser considerados
- O primeiro layout identificado será utilizado (**first-match wins**)
- Ordenação: `idt_layout DESC` (layouts mais recentes primeiro)

**Filtro por adquirente:**
- O `idt_acquirer` é obtido da leitura da tabela `SERVER_PATHS`
- Esse `idt_acquirer` será utilizado como filtro para buscar apenas layouts da adquirente correspondente na tabela `layout`

## Tipos de Origem para Identificação

### 1. FILENAME
- A regra é aplicada no **nome do arquivo**
- Compara com o valor do campo `des_value`
- Não requer leitura do conteúdo do arquivo

### 2. HEADER
- Para arquivos **TXT**: Extração posicional por **byte offset**
  - `num_start_position`: Posição inicial do byte (0-indexed)
  - `num_end_position`: Posição final do byte (se NULL, extrai até o fim da linha ou dos 7000 bytes)
  - Lê linha por linha dentro da extração dos primeiros 7000 bytes e tenta a identificação a cada linha.
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

### 4. KEY (arquivos JSON)
- Suporta **caminhos aninhados** usando notação de ponto
- O caminho para extração está declarado na coluna `des_key`
- Exemplo: `des_key = "metadata.version"` ou `des_key = "header.acquirer.name"`
- Valida dentro dos primeiros 7000 bytes iniciais
- Compara o valor extraído com o campo `des_value`

## Configuração de Buffer

A quantidade de bytes a serem extraídos deve ficar em uma nova variável de ambiente:
- **Nome**: `FILE_ORIGIN_BUFFER_LIMIT`
- **Valor inicial**: `7000`

## Detecção e Conversão de Encoding

O serviço deve implementar a seguinte estratégia de encoding:

1. **Detecção automática**: Usar biblioteca de detecção automática de encoding (Apache Tika, ICU4J ou juniversalchardet)
2. **Validação**: Comparar o encoding detectado com `des_encoding` da tabela `layout`
3. **Conversão (se necessário)**:
   - Se o encoding detectado for diferente do cadastrado, tentar converter para o formato cadastrado em `des_encoding`
   - Se a conversão falhar, tentar converter para `UTF-8`
   - Se ainda assim falhar, tentar fazer a identificação sem conversão (usando o encoding detectado)

## Funções de Transformação

Para permitir comparações mais flexíveis, adicionar duas novas colunas na tabela `layout_identification_rule`:
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
- `idt_layout`: Identificador do layout identificado
- Esse valor será utilizado para preencher a coluna `idt_layout` na tabela `file_origin`

**Nota:** O campo `idt_acquirer` já é obtido da leitura da tabela `SERVER_PATHS` e não precisa ser retornado pelo serviço de identificação.

## Estrutura de tabelas e Enums para identificação do layout

Enum file_type {
    "CSV"
    "TXT"
    "JSON"
    "OFX"
    "XML"
}

Enum transaction_type {
    "FINANCEIRO" [note: 'Arquivos que contém transações financeiras que vão refletir no extrato bancário do cliente']
    "CAPTURA"    [note: 'Arquivos que contém transações de captura (Vendas e avisos de ajustes diversos)']
    "COMPLETO"   [note: 'Arquivos que contém transações de captura e financeiro no mesmo arquivo']
    "AUXILIAR"   [note: 'Arquivos que contém apenas transações auxiliares, ex.: arquivos de saldos']
}

Enum distribution_type {
    "DIARIO"          [note: 'Arquivos são enviados todos os dias, inclusive finais de semana e feriados mesmo que não tenha transação.']
    "DIAS-UTEIS"      [note: 'Arquivos são enviados apenas em dias úteis mesmo que não tenha transações.']
    "SEGUNTA-A-SEXTA" [note: 'Arquivos são enviados de segunda-feira a sexta-feira inclusive feriados mesmo que não tenha transação.']
    "DOMINGO"         [note: 'Arquivos são enviados apenas aos domingos inclusive feriado mesmo que não contenha transação.']
    "SEGUNDA-FEIRA"   [note: 'Arquivos são enviados nas segunda-feira inclusive feriado mesmo que não contenha transação.']
    "TERCA-FEIRA"     [note: 'Arquivos são enviados nas terça-feira inclusive feriado mesmo que não contenha transação.']
    "QUARTA-FEIRA"    [note: 'Arquivos são enviados nas quarta-feira inclusive feriado mesmo que não contenha transação.']
    "QUITA-FEIRA"     [note: 'Arquivos são enviados nas quinta-feira inclusive feriado mesmo que não contenha transação.']
    "SEXTA-FEIRA"     [note: 'Arquivos são enviados nas sexta-feira inclusive feriado mesmo que não contenha transação.']
    "SABADO"          [note: 'Arquivos são enviados aos sabados inclusive feriado mesmo que não contenha transação.']
    "SAZONAL"         [note: 'Arquivos que não tem uma frequencia certa de envio, podem ser enviados a quarquer dia da semana ou do mês.']
}

Table layout [note: '[NOT_SECURITY_APPLY] - Tabela com dados relacionados aos layouts de arquivos das adquirentes']{
    idt_layout number(19) [primary key, increment, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do layout, ex.: 1, 2, 3, ...']
    cod_layout varchar2(100) [not null, note: '[NOT_SECURITY_APPLY] - Código interno do layout, ex.: REDE_EEVD_01, CIELO_15_PIX, etc...']
    idt_acquirer number(19) [not null, note: '[NOT_SECURITY_APPLY] - Código interno da adquirente a qual o layout pertence. Referencia da coluna idt_acquirer da tabela acquirer que pertence ao domínio do OPT-IN (dependência externa)']
    des_version varchar2(30) [null, note: '[NOT_SECURITY_APPLY] - Versão do layout. Valor gerenciado pela adquirente']
    des_file_type file_type [not null, note: '[NOT_SECURITY_APPLY] - Extensão do arquivo: CSV | TXT | JSON | OFX | XML']
    des_column_separator varchar2(2) [null, note: '[NOT_SECURITY_APPLY] - Separador de colunas do arquivo. Ex: ; | ,']
    des_transaction_type transaction_type [not null, note: '[NOT_SECURITY_APPLY] - Tipo de informações contidas no arquivo: COMPLETO | CAPTURA | FINANCEIRO | AUXILIAR']
    des_distribution_type distribution_type [not null, note: '[NOT_SECURITY_APPLY] - Frequência de recebimento do arquivo']
    des_encoding varchar2(10) [null, note: '[NOT_SECURITY_APPLY] - Codificação de caracteres do arquivo. Ex.: utf-8 | utf-16 | windows-1252 | ISO-8859-1']
    dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da criação do registro']
    dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent varchar2(50) [not null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a última alteração']
    flg_active number(1) [not null, note: '[NOT_SECURITY_APPLY] - Indica se a configuração do layout está ativa (1) ou inativa (0)']
}

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

Table layout_identification_rule [note: '[NOT_SECURITY_APPLY] - Setup de regras para identificação de layouts das adquirentes'] {
    idt_rule             number(20)     [primary key, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro']
    idt_layout           number(20)     [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do layout, faz referência à tabela layout']
    des_rule             varchar2(255)  [not null, note: '[NOT_SECURITY_APPLY] - Descritivo de explicação da regra']
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
} ref: layout_identification_rule.idt_layout > layout.idt_layout

## Exemplos de configuração

**Arquivo CIELO VENDAS Versão 15**
insert into 
layout (idt_layout,cod_layout,idt_acquirer,des_version,des_file_type,des_column_separator, des_transaction_type,des_distribution_type,des_encoding,dat_creation,dat_update,nam_change_agent,flg_active)
values (1,'CIELO_015_03_VENDA',1,'015','txt',NULL,'CAPTURA','DIARIO','utf-8',sysdate, null, 'SETUP',1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(11,1,'Arquivo de captura', 'FILENAME', 'TERMINA_COM', NULL, NULL, 'venda', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(12,1,'Adquirente', 'FILENAME', 'COMECA_COM', NULL, NULL, 'cielo', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(13,1,'Versão 15', 'FILENAME', 'CONTEM', NULL, NULL, 'v15', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

**Arquivo CIELO FINANCEIRO Versão 15**

insert into 
layout (idt_layout,cod_layout,idt_acquirer,des_version,des_file_type,des_column_separator,des_transaction_type,des_distribution_type,des_encoding,dat_creation,dat_update,nam_change_agent,flg_active)
values (2,'CIELO_015_04_PAGTO',1,'015','txt',NULL,'FINANCEIRO','DIARIO','utf-8',sysdate, null, 'SETUP',1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(21,2,'Arquivo de captura', 'FILENAME', 'TERMINA_COM', NULL, NULL, 'pagto', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(22,2,'Adquirente', 'FILENAME', 'COMECA_COM', NULL, NULL, 'cielo', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(23,2,'Versão 15', 'FILENAME', 'CONTEM', NULL, NULL, 'v15', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

**Arquivo EEVD da Rede Versão 02**

insert into 
layout (idt_layout,cod_layout,idt_acquirer,des_version,des_file_type,des_column_separator,des_transaction_type,des_distribution_type,des_encoding,dat_creation,dat_update,nam_change_agent,flg_active)
values (3,'REDE_EEVD_02',2,'V2.00','csv',',','COMPLETO','DIARIO','utf-8',sysdate, null, 'SETUP',1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(31,3,'Tipo de Registro', 'HEADER', 'IGUAL', 0, NULL, '00', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(32,3,'Nome do arquirente', 'HEADER', 'IGUAL', 5, NULL, 'REDECARD', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(33,3,'Descritivo do tipo de movimentação', 'HEADER', 'IGUAL', 4, NULL, 'Movimentação diária - Cartões de débito', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(34,3,'Versão do arquivo', 'HEADER', 'IGUAL', 9, NULL, 'V2.00 - 05/2023 EEVD', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

**Arquivo EEVC da Rede Versão 03**
insert into 
layout (idt_layout,cod_layout,idt_acquirer,des_version,des_file_type,des_column_separator,des_transaction_type,des_distribution_type,des_encoding,dat_creation,dat_update,nam_change_agent,flg_active)
values (4,'REDE_EEVC_03',2,'V3.00','txt',NULL,'CAPTURA','DIARIO','utf-8',sysdate, null, 'SETUP',1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(41,4,'Tipo de Registro', 'HEADER', 'IGUAL', 0, 3, '002', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(42,4,'Nome do arquirente', 'HEADER', 'IGUAL', 11, 19, 'REDECARD', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(43,4,'Descritivo do tipo de movimentação', 'HEADER', 'IGUAL', 19, 49, 'EXTRATO DE MOVIMENTO DE VENDAS', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(44,4,'Versão do arquivo', 'HEADER', 'IGUAL', 105, 125, 'V3.00 - 05/2023 EEVC', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

**Arquivo EEFI da Rede Versão 04**

insert into 
layout (idt_layout,cod_layout,idt_acquirer,des_version,des_file_type,des_column_separator,des_transaction_type,des_distribution_type,des_encoding,dat_creation,dat_update,nam_change_agent,flg_active)
values (5,'REDE_EEFI_04',2,'V4.00','txt',NULL,'FINANCEIRO','DIARIO','utf-8',sysdate, null, 'SETUP',1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(51,5,'Tipo de Registro', 'HEADER', 'IGUAL', 0, 3, '030', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(52,5,'Nome do arquirente', 'HEADER', 'IGUAL', 11, 19, 'REDECARD', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(53,5,'Descritivo do tipo de movimentação', 'HEADER', 'IGUAL', 19, 53, 'EXTRATO DE MOVIMENTACAO FINANCEIRA', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);

insert into layout_identification_rule
(idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, dat_creation, dat_update, nam_change_agent, flg_active)
values
(54,5,'Versão do arquivo', 'HEADER', 'IGUAL', 105, 125, 'V4.00 - 05/2023 EEFI', NULL, NULL, SYSDATE, NULL, 'SETUP', 1);


## O que vamos fazer ?

1 - Criar os scripts com a definição das novas tabelas e adicionar a pasta de dll.
2 - Modificar o script existente adicionando uma foreing key na abela file_origin para o campo idt_layout referenciando a nova tabela layout.
3 - Ajustar a inicialização do banco de dados para incluir os novos ddls e dmls.
4 - Adaptar o Consumer adicionando um serviço de identificação do layout.
5 - Testar a identificação com um arquivo de layout com id 1 fazendo a identificação pela nomenclatura do arquivo.