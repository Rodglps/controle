# Documento de Requisitos - Identificação de Layouts de Arquivos EDI

## Introdução

Este documento especifica os requisitos para a funcionalidade de identificação automática de layouts de arquivos EDI durante o processo de transferência no Consumer. O sistema deve identificar qual layout e adquirente corresponde ao arquivo sendo processado, aplicando regras configuráveis baseadas em múltiplas origens (nome do arquivo, cabeçalho, tags XML, chaves JSON).

## Glossário

- **Layout**: Estrutura e formato específico de um arquivo EDI definido por uma adquirente
- **Adquirente**: Empresa processadora de pagamentos (ex: Cielo, Rede, PagSeguro)
- **Consumer**: Módulo do sistema responsável por consumir mensagens e transferir arquivos
- **Layout_Identification_Service**: Serviço responsável por identificar o layout de um arquivo
- **File_Origin_Buffer**: Buffer de bytes lidos do início do arquivo para identificação (padrão 7000 bytes)
- **Identification_Rule**: Regra configurável para identificação de layout
- **Value_Origin**: Tipo de origem da informação para identificação (FILENAME, HEADER, TAG, KEY)
- **Criteria_Type**: Tipo de critério de comparação (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL)
- **Function_Type**: Função de transformação aplicada antes da comparação (UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE)
- **Transfer_Process**: Processo de transferência de arquivo do Consumer
- **Database**: Oracle Database usado para armazenar configurações e estado
- **Origin_Value**: Valor extraído do arquivo pela regra de identificação
- **Expected_Value**: Valor esperado configurado em des_value para comparação

## Tipos de Critérios de Comparação

Esta seção detalha o comportamento de cada tipo de critério (des_criteria_type) usado para comparar Origin_Value com Expected_Value.

### COMECA_COM
Verifica se Origin_Value começa com Expected_Value.

Exemplos:
- Origin_Value: "cielo_v15_venda.txt", Expected_Value: "cielo" → TRUE
- Origin_Value: "rede_arquivo.txt", Expected_Value: "cielo" → FALSE

### TERMINA_COM
Verifica se Origin_Value termina com Expected_Value.

Exemplos:
- Origin_Value: "cielo_v15_venda.txt", Expected_Value: "venda.txt" → TRUE
- Origin_Value: "cielo_v15_pagto.txt", Expected_Value: "venda.txt" → FALSE

### CONTEM
Verifica se Origin_Value contém Expected_Value em qualquer posição.

Exemplos:
- Origin_Value: "cielo_v15_venda.txt", Expected_Value: "v15" → TRUE
- Origin_Value: "cielo_v14_venda.txt", Expected_Value: "v15" → FALSE

### CONTIDO
Verifica se Origin_Value está contido em Expected_Value (inverso de CONTEM).

Exemplos:
- Origin_Value: "v15", Expected_Value: "cielo_v15_venda.txt" → TRUE
- Origin_Value: "cielo_v15_venda.txt", Expected_Value: "v15" → FALSE

### IGUAL
Verifica se Origin_Value é exatamente igual a Expected_Value.

Exemplos:
- Origin_Value: "REDECARD", Expected_Value: "REDECARD" → TRUE
- Origin_Value: "REDECARD ", Expected_Value: "REDECARD" → FALSE (espaço extra)
- Origin_Value: "redecard", Expected_Value: "REDECARD" → FALSE (case-sensitive sem função de transformação)

## Requisitos

### Requisito 1: Armazenamento de Configuração de Layouts

**User Story:** Como administrador do sistema, eu quero armazenar configurações de layouts de arquivos EDI no banco de dados, para que o sistema possa identificar automaticamente os arquivos recebidos.

#### Acceptance Criteria

1. THE Database SHALL armazenar informações de layouts na tabela layout com os campos: idt_layout, cod_layout, idt_acquirer, des_version, des_file_type, des_column_separator, des_transaction_type, des_distribution_type, des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
2. THE Database SHALL armazenar regras de identificação na tabela layout_identification_rule com os campos: idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active
3. THE Database SHALL estabelecer uma foreign key entre layout_identification_rule.idt_layout e layout.idt_layout
4. THE Database SHALL estabelecer uma foreign key entre file_origin.idt_layout e layout.idt_layout
5. THE Database SHALL suportar os enums: file_type (CSV, TXT, JSON, OFX, XML), transaction_type (FINANCEIRO, CAPTURA, COMPLETO, AUXILIAR), distribution_type (DIARIO, DIAS-UTEIS, SEGUNDA-A-SEXTA, DOMINGO, SEGUNDA-FEIRA, TERCA-FEIRA, QUARTA-FEIRA, QUINTA-FEIRA, SEXTA-FEIRA, SABADO, SAZONAL), value_origin (HEADER, TAG, FILENAME, KEY), criteria_type (COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL), function_type (UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE)

### Requisito 2: Entidades JPA e Repositories

**User Story:** Como desenvolvedor, eu quero entidades JPA e repositories para as tabelas de layout, para que o sistema possa acessar as configurações de identificação.

#### Acceptance Criteria

1. THE Commons SHALL fornecer a entidade Layout mapeada para a tabela layout
2. THE Commons SHALL fornecer a entidade LayoutIdentificationRule mapeada para a tabela layout_identification_rule
3. THE Commons SHALL fornecer o LayoutRepository com método findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc
4. THE Commons SHALL fornecer o LayoutIdentificationRuleRepository com método findByIdtLayoutAndFlgActive
5. THE FileOrigin SHALL incluir o campo idt_layout como foreign key para Layout

### Requisito 3: Identificação Durante Transferência

**User Story:** Como sistema, eu quero identificar o layout do arquivo durante a transferência no Consumer, para que o arquivo seja corretamente classificado antes de ser armazenado.

#### Acceptance Criteria

1. WHEN THE Transfer_Process inicia a transferência de um arquivo, THE Layout_Identification_Service SHALL ser invocado após a leitura dos primeiros bytes
2. WHEN THE Layout_Identification_Service é invocado, THE Consumer SHALL ler até FILE_ORIGIN_BUFFER_LIMIT bytes do início do arquivo
3. THE FILE_ORIGIN_BUFFER_LIMIT SHALL ter valor padrão de 7000 bytes
4. THE FILE_ORIGIN_BUFFER_LIMIT SHALL ser configurável via variável de ambiente FILE_ORIGIN_BUFFER_LIMIT
5. IF o layout não for identificado, THEN THE Transfer_Process SHALL interromper a transferência
6. IF o layout não for identificado, THEN THE Transfer_Process SHALL atualizar file_origin com step igual a COLETA e status igual a ERRO
7. IF o layout não for identificado, THEN THE Transfer_Process SHALL registrar a mensagem "Layout do arquivo não foi identificado"

### Requisito 4: Aplicação de Regras de Identificação

**User Story:** Como sistema, eu quero aplicar regras de identificação configuráveis, para que diferentes layouts possam ser identificados de forma flexível.

#### Acceptance Criteria

1. THE Layout_Identification_Service SHALL buscar layouts ativos (flg_active igual a 1) filtrados por idt_acquirer
2. THE Layout_Identification_Service SHALL ordenar layouts por idt_layout DESC
3. THE Layout_Identification_Service SHALL buscar regras ativas (flg_active igual a 1) para cada layout
4. THE Layout_Identification_Service SHALL aplicar operador AND entre todas as regras de um mesmo layout
5. THE Layout_Identification_Service SHALL considerar um layout identificado quando todas as suas regras forem satisfeitas
6. THE Layout_Identification_Service SHALL retornar o primeiro layout identificado (first-match wins)
7. THE Layout_Identification_Service SHALL retornar idt_layout do layout identificado

### Requisito 5: Tipos de Critérios de Comparação

**User Story:** Como sistema, eu quero aplicar diferentes tipos de critérios de comparação, para que a identificação seja flexível e suporte diversos padrões de nomenclatura e conteúdo.

#### Acceptance Criteria

1. WHEN des_criteria_type é COMECA_COM, THE Layout_Identification_Service SHALL verificar se Origin_Value começa com Expected_Value
2. WHEN des_criteria_type é TERMINA_COM, THE Layout_Identification_Service SHALL verificar se Origin_Value termina com Expected_Value
3. WHEN des_criteria_type é CONTEM, THE Layout_Identification_Service SHALL verificar se Origin_Value contém Expected_Value em qualquer posição
4. WHEN des_criteria_type é CONTIDO, THE Layout_Identification_Service SHALL verificar se Origin_Value está contido em Expected_Value
5. WHEN des_criteria_type é IGUAL, THE Layout_Identification_Service SHALL verificar se Origin_Value é exatamente igual a Expected_Value
6. THE Layout_Identification_Service SHALL aplicar funções de transformação antes de executar a comparação do critério
7. THE Layout_Identification_Service SHALL realizar comparações case-sensitive quando nenhuma função de transformação é aplicada

### Requisito 6: Identificação por Nome de Arquivo

**User Story:** Como administrador, eu quero configurar regras baseadas no nome do arquivo, para que layouts possam ser identificados sem ler o conteúdo.

#### Acceptance Criteria

1. WHEN des_value_origin é FILENAME, THE Layout_Identification_Service SHALL aplicar a regra no nome do arquivo
2. WHEN des_criteria_type é COMECA_COM, THE Layout_Identification_Service SHALL verificar se Origin_Value começa com Expected_Value
3. WHEN des_criteria_type é TERMINA_COM, THE Layout_Identification_Service SHALL verificar se Origin_Value termina com Expected_Value
4. WHEN des_criteria_type é CONTEM, THE Layout_Identification_Service SHALL verificar se Origin_Value contém Expected_Value
5. WHEN des_criteria_type é CONTIDO, THE Layout_Identification_Service SHALL verificar se Origin_Value está contido em Expected_Value
6. WHEN des_criteria_type é IGUAL, THE Layout_Identification_Service SHALL verificar se Origin_Value é exatamente igual a Expected_Value

### Requisito 7: Identificação por Cabeçalho TXT

**User Story:** Como administrador, eu quero configurar regras baseadas em posições de bytes em arquivos TXT, para que layouts de arquivos posicionais possam ser identificados.

#### Acceptance Criteria

1. WHEN des_value_origin é HEADER e des_file_type é TXT, THE Layout_Identification_Service SHALL usar extração posicional por byte offset
2. THE Layout_Identification_Service SHALL usar num_start_position como posição inicial do byte (0-indexed)
3. WHEN num_end_position é NULL, THE Layout_Identification_Service SHALL extrair até o fim da linha ou até o fim do File_Origin_Buffer
4. WHEN num_end_position não é NULL, THE Layout_Identification_Service SHALL usar como posição final do byte
5. THE Layout_Identification_Service SHALL ler linha por linha dentro do File_Origin_Buffer
6. THE Layout_Identification_Service SHALL tentar identificação a cada linha lida
7. WHEN não existe caractere de quebra de linha no File_Origin_Buffer, THE Layout_Identification_Service SHALL considerar o buffer completo como uma única linha
8. THE Layout_Identification_Service SHALL aplicar des_criteria_type para comparar Origin_Value extraído com Expected_Value

### Requisito 8: Identificação por Cabeçalho CSV

**User Story:** Como administrador, eu quero configurar regras baseadas em colunas de arquivos CSV, para que layouts de arquivos delimitados possam ser identificados.

#### Acceptance Criteria

1. WHEN des_value_origin é HEADER e des_file_type é CSV, THE Layout_Identification_Service SHALL usar extração por índice de coluna
2. THE Layout_Identification_Service SHALL usar num_start_position como índice da coluna (0-indexed)
3. THE Layout_Identification_Service SHALL usar des_column_separator da tabela layout como separador de colunas
4. THE Layout_Identification_Service SHALL ler linha por linha dentro do File_Origin_Buffer
5. THE Layout_Identification_Service SHALL tentar identificação a cada linha lida
6. WHEN não existe caractere de quebra de linha no File_Origin_Buffer, THE Layout_Identification_Service SHALL considerar o buffer completo como uma única linha
7. THE Layout_Identification_Service SHALL aplicar des_criteria_type para comparar Origin_Value extraído com Expected_Value

### Requisito 8: Identificação por Tag XML

**User Story:** Como administrador, eu quero configurar regras baseadas em tags XML, para que layouts de arquivos XML possam ser identificados.

#### Acceptance Criteria

1. WHEN des_value_origin é TAG, THE Layout_Identification_Service SHALL usar des_tag como caminho XPath
2. THE Layout_Identification_Service SHALL suportar caminhos aninhados em XML
3. THE Layout_Identification_Service SHALL validar dentro dos primeiros bytes do File_Origin_Buffer
4. THE Layout_Identification_Service SHALL extrair o valor da tag usando o caminho especificado
5. THE Layout_Identification_Service SHALL comparar o valor extraído com des_value

### Requisito 9: Identificação por Chave JSON

**User Story:** Como administrador, eu quero configurar regras baseadas em chaves JSON, para que layouts de arquivos JSON possam ser identificados.

#### Acceptance Criteria

1. WHEN des_value_origin é KEY, THE Layout_Identification_Service SHALL usar des_key como caminho JSON
2. THE Layout_Identification_Service SHALL suportar caminhos aninhados usando notação de ponto
3. THE Layout_Identification_Service SHALL validar dentro dos primeiros bytes do File_Origin_Buffer
4. THE Layout_Identification_Service SHALL extrair o valor da chave usando o caminho especificado
5. THE Layout_Identification_Service SHALL comparar o valor extraído com des_value

### Requisito 10: Detecção e Conversão de Encoding

**User Story:** Como sistema, eu quero detectar e converter automaticamente o encoding dos arquivos, para que arquivos com diferentes codificações possam ser identificados corretamente.

#### Acceptance Criteria

1. THE Layout_Identification_Service SHALL detectar automaticamente o encoding do File_Origin_Buffer
2. THE Layout_Identification_Service SHALL comparar o encoding detectado com des_encoding da tabela layout
3. WHEN o encoding detectado é diferente de des_encoding, THE Layout_Identification_Service SHALL tentar converter para des_encoding
4. IF a conversão para des_encoding falhar, THEN THE Layout_Identification_Service SHALL tentar converter para UTF-8
5. IF a conversão para UTF-8 falhar, THEN THE Layout_Identification_Service SHALL tentar identificação usando o encoding detectado

### Requisito 11: Funções de Transformação

**User Story:** Como administrador, eu quero aplicar funções de transformação nos valores antes da comparação, para que a identificação seja mais flexível e tolerante a variações.

#### Acceptance Criteria

1. WHEN des_function_origin é UPPERCASE, THE Layout_Identification_Service SHALL converter o valor de origem para maiúsculas antes da comparação
2. WHEN des_function_origin é LOWERCASE, THE Layout_Identification_Service SHALL converter o valor de origem para minúsculas antes da comparação
3. WHEN des_function_origin é INITCAP, THE Layout_Identification_Service SHALL converter a primeira letra para maiúscula e demais para minúsculas no valor de origem
4. WHEN des_function_origin é TRIM, THE Layout_Identification_Service SHALL remover espaços em branco no início e fim do valor de origem
5. WHEN des_function_origin é NONE ou NULL, THE Layout_Identification_Service SHALL usar o valor de origem sem transformação
6. WHEN des_function_dest é UPPERCASE, THE Layout_Identification_Service SHALL converter des_value para maiúsculas antes da comparação
7. WHEN des_function_dest é LOWERCASE, THE Layout_Identification_Service SHALL converter des_value para minúsculas antes da comparação
8. WHEN des_function_dest é INITCAP, THE Layout_Identification_Service SHALL converter a primeira letra para maiúscula e demais para minúsculas em des_value
9. WHEN des_function_dest é TRIM, THE Layout_Identification_Service SHALL remover espaços em branco no início e fim de des_value
10. WHEN des_function_dest é NONE ou NULL, THE Layout_Identification_Service SHALL usar des_value sem transformação
11. THE Layout_Identification_Service SHALL realizar comparações case-sensitive quando nenhuma função de transformação é aplicada

### Requisito 12: Parser e Pretty Printer para Configurações

**User Story:** Como desenvolvedor, eu quero parsers e pretty printers para as configurações de identificação, para que as regras possam ser validadas e depuradas facilmente.

#### Acceptance Criteria

1. WHEN uma configuração de layout é carregada do banco, THE Layout_Identification_Service SHALL parsear a configuração em objetos de domínio
2. WHEN uma configuração de layout é inválida, THE Layout_Identification_Service SHALL retornar um erro descritivo
3. THE Layout_Identification_Service SHALL fornecer um Pretty_Printer para formatar configurações de layout
4. FOR ALL configurações de layout válidas, parsear então formatar então parsear SHALL produzir um objeto equivalente (round-trip property)

### Requisito 13: Integração com Fluxo de Transferência

**User Story:** Como sistema, eu quero integrar a identificação de layout no fluxo de transferência existente, para que o processo seja transparente e automático.

#### Acceptance Criteria

1. THE Transfer_Process SHALL invocar Layout_Identification_Service após ler o File_Origin_Buffer
2. THE Transfer_Process SHALL atualizar file_origin.idt_layout com o valor retornado pelo Layout_Identification_Service
3. WHEN Layout_Identification_Service retorna NULL, THE Transfer_Process SHALL tratar como falha de identificação
4. THE Transfer_Process SHALL continuar a transferência após identificação bem-sucedida
5. THE Transfer_Process SHALL usar streaming para não carregar o arquivo completo em memória

### Requisito 14: Scripts DDL e DML

**User Story:** Como administrador de banco de dados, eu quero scripts DDL e DML para criar as tabelas e popular dados de exemplo, para que o sistema possa ser configurado rapidamente.

#### Acceptance Criteria

1. THE Database SHALL fornecer script DDL para criar tabela layout
2. THE Database SHALL fornecer script DDL para criar tabela layout_identification_rule
3. THE Database SHALL fornecer script DDL para adicionar foreign key idt_layout em file_origin
4. THE Database SHALL fornecer scripts DML com 5 exemplos de configuração (2 layouts Cielo e 3 layouts Rede)
5. THE Database SHALL incluir exemplos de identificação por FILENAME (layouts Cielo)
6. THE Database SHALL incluir exemplos de identificação por HEADER para CSV (layout Rede EEVD)
7. THE Database SHALL incluir exemplos de identificação por HEADER para TXT (layouts Rede EEVC e EEFI)

### Requisito 15: Testes Unitários

**User Story:** Como desenvolvedor, eu quero testes unitários para o serviço de identificação, para garantir que as regras são aplicadas corretamente.

#### Acceptance Criteria

1. THE Layout_Identification_Service SHALL ter testes unitários para cada tipo de Value_Origin
2. THE Layout_Identification_Service SHALL ter testes unitários para cada tipo de Criteria_Type
3. THE Layout_Identification_Service SHALL ter testes unitários para cada tipo de Function_Type
4. THE Layout_Identification_Service SHALL ter testes unitários para combinação de múltiplas regras com operador AND
5. THE Layout_Identification_Service SHALL ter testes unitários para ordenação first-match wins
6. THE Layout_Identification_Service SHALL ter testes unitários para detecção e conversão de encoding
7. THE Layout_Identification_Service SHALL ter testes unitários para falha de identificação

### Requisito 16: Testes de Integração

**User Story:** Como desenvolvedor, eu quero testes de integração com banco de dados, para garantir que as queries e mapeamentos JPA funcionam corretamente.

#### Acceptance Criteria

1. THE Layout_Identification_Service SHALL ter testes de integração que carregam configurações do banco de dados
2. THE Layout_Identification_Service SHALL ter testes de integração que verificam filtro por idt_acquirer
3. THE Layout_Identification_Service SHALL ter testes de integração que verificam ordenação por idt_layout DESC
4. THE Layout_Identification_Service SHALL ter testes de integração que verificam filtro por flg_active
5. THE Layout_Identification_Service SHALL ter testes de integração que atualizam file_origin.idt_layout

### Requisito 17: Testes End-to-End

**User Story:** Como desenvolvedor, eu quero testes E2E que simulam o fluxo completo de transferência com identificação, para garantir que a integração funciona corretamente.

#### Acceptance Criteria

1. THE System SHALL ter teste E2E que simula transferência de arquivo Cielo identificado por FILENAME
2. THE System SHALL ter teste E2E que simula transferência de arquivo Rede CSV identificado por HEADER
3. THE System SHALL ter teste E2E que simula transferência de arquivo Rede TXT identificado por HEADER
4. THE System SHALL ter teste E2E que simula falha de identificação e interrupção da transferência
5. THE System SHALL ter teste E2E que verifica atualização correta de file_origin.idt_layout
