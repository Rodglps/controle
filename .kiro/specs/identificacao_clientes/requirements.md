# Requirements Document

## Introduction

Este documento especifica os requisitos para a funcionalidade de identificação de clientes em arquivos EDI. O sistema deve identificar automaticamente qual(is) cliente(s) é(são) proprietário(s) de cada arquivo EDI processado, baseando-se em regras configuráveis que analisam o nome do arquivo e seu conteúdo. Um arquivo pode pertencer a múltiplos clientes simultaneamente.

A identificação ocorre durante a transferência no Consumer, logo após a identificação do layout, utilizando os primeiros 7000 bytes do arquivo. O processo suporta quatro tipos de identificação: FILENAME (nome do arquivo), HEADER (conteúdo posicional), TAG (caminhos XML) e KEY (caminhos JSON).

## Glossary

- **System**: O sistema de transferência automatizada de arquivos EDI
- **Consumer**: Módulo que consome mensagens RabbitMQ e realiza transferência de arquivos
- **Client_Identification_Service**: Serviço responsável pela identificação de clientes
- **Layout_Identification_Service**: Serviço responsável pela identificação de layouts
- **File_Transfer_Service**: Serviço responsável pela transferência de arquivos
- **Database**: Banco de dados Oracle que armazena configurações e estado
- **Buffer**: Primeiros 7000 bytes do arquivo lidos para identificação
- **Identification_Rule**: Regra configurada para identificar um cliente
- **Acquirer**: Adquirente de cartão de crédito (Cielo, Rede, etc.)
- **Layout**: Formato estrutural do arquivo EDI
- **FILENAME**: Tipo de identificação baseado no nome do arquivo
- **HEADER**: Tipo de identificação baseado no conteúdo do arquivo (posicional)
- **TAG**: Tipo de identificação baseado em caminhos XML (XPath)
- **KEY**: Tipo de identificação baseado em caminhos JSON (notação de ponto)

## Requirements

### Requirement 1: Identificação de Clientes Durante Transferência

**User Story:** Como operador do sistema, eu quero que o sistema identifique automaticamente os clientes proprietários de cada arquivo EDI, para que os arquivos sejam corretamente associados aos seus donos durante o processamento.

#### Acceptance Criteria

1. WHEN THE Consumer inicia a transferência de um arquivo, THE Client_Identification_Service SHALL executar a identificação de clientes após a identificação do layout
2. WHEN a identificação do layout falha, THE Client_Identification_Service SHALL executar identificação apenas para regras com des_value_origin igual a FILENAME
3. WHEN a identificação do layout é bem-sucedida, THE Client_Identification_Service SHALL executar identificação para regras com des_value_origin igual a FILENAME e para regras com des_value_origin em HEADER, TAG ou KEY
4. THE Client_Identification_Service SHALL utilizar os primeiros 7000 bytes do arquivo para identificação
5. WHEN nenhum cliente é identificado, THE System SHALL continuar o processamento normalmente
6. WHEN nenhum cliente é identificado, THE File_Transfer_Service SHALL finalizar com step igual a COLETA e status igual a CONCLUIDO
7. WHEN nenhum cliente é identificado, THE System SHALL NOT registrar dados na tabela file_origin_clients

### Requirement 2: Múltiplos Clientes por Arquivo

**User Story:** Como operador do sistema, eu quero que um arquivo possa ser associado a múltiplos clientes simultaneamente, para que arquivos compartilhados sejam corretamente distribuídos.

#### Acceptance Criteria

1. THE Client_Identification_Service SHALL identificar todos os clientes cujas regras ativas correspondem ao arquivo
2. WHEN múltiplos clientes são identificados, THE System SHALL registrar um registro na tabela file_origin_clients para cada cliente identificado
3. THE Database SHALL prevenir duplicação através de constraint única em idt_file_origin e idt_client
4. THE Client_Identification_Service SHALL ordenar resultados por num_process_weight em ordem decrescente

### Requirement 3: Aplicação de Regras com Operador AND

**User Story:** Como administrador do sistema, eu quero que todas as regras ativas de um cliente sejam satisfeitas para identificação, para que a identificação seja precisa e evite falsos positivos.

#### Acceptance Criteria

1. THE Client_Identification_Service SHALL aplicar operador AND entre todas as regras ativas de um customer_identification
2. THE Client_Identification_Service SHALL considerar um cliente identificado quando TODAS as suas regras ativas retornam verdadeiro
3. THE Client_Identification_Service SHALL considerar apenas registros com flg_active igual a 1 na tabela customer_identification
4. THE Client_Identification_Service SHALL considerar apenas registros com flg_active igual a 1 na tabela customer_identification_rule

### Requirement 4: Filtro por Adquirente

**User Story:** Como administrador do sistema, eu quero que a identificação considere apenas regras da adquirente correspondente, para que o processamento seja eficiente e evite conflitos entre adquirentes.

#### Acceptance Criteria

1. THE Client_Identification_Service SHALL obter idt_acquirer da tabela SERVER_PATHS
2. THE Client_Identification_Service SHALL filtrar customer_identification por idt_acquirer obtido
3. WHEN o layout é identificado, THE Client_Identification_Service SHALL executar query para des_value_origin igual a FILENAME com filtro por idt_acquirer
4. WHEN o layout é identificado, THE Client_Identification_Service SHALL executar query para des_value_origin em HEADER, TAG ou KEY com filtro por idt_acquirer e idt_layout
5. WHEN o layout é identificado, THE Client_Identification_Service SHALL unir resultados das duas queries
6. WHEN o layout não é identificado, THE Client_Identification_Service SHALL executar apenas query para des_value_origin igual a FILENAME com filtro por idt_acquirer

### Requirement 5: Identificação por FILENAME

**User Story:** Como administrador do sistema, eu quero identificar clientes baseado no nome do arquivo, para que arquivos possam ser identificados sem necessidade de ler seu conteúdo.

#### Acceptance Criteria

1. WHEN des_value_origin é FILENAME e des_criteria_type é COMECA_COM, THE Client_Identification_Service SHALL verificar se o nome do arquivo inicia com des_value
2. WHEN des_value_origin é FILENAME e des_criteria_type é TERMINA_COM, THE Client_Identification_Service SHALL verificar se o nome do arquivo termina com des_value
3. WHEN des_value_origin é FILENAME e des_criteria_type é CONTEM, THE Client_Identification_Service SHALL verificar se o nome do arquivo contém des_value
4. WHEN des_value_origin é FILENAME e des_criteria_type é CONTIDO, THE Client_Identification_Service SHALL verificar se o nome do arquivo está contido em des_value
5. WHEN des_value_origin é FILENAME e des_criteria_type é IGUAL, THE Client_Identification_Service SHALL verificar se o nome do arquivo é igual a des_value
6. THE Client_Identification_Service SHALL aplicar des_function_origin ao nome do arquivo antes da comparação
7. THE Client_Identification_Service SHALL aplicar des_function_dest ao valor de des_value antes da comparação

### Requirement 6: Identificação por HEADER em Arquivos TXT

**User Story:** Como administrador do sistema, eu quero identificar clientes baseado em posições específicas de bytes em arquivos TXT, para que arquivos de texto posicional possam ser identificados pelo conteúdo.

#### Acceptance Criteria

1. WHEN des_value_origin é HEADER e des_file_type do layout é TXT, THE Client_Identification_Service SHALL extrair bytes da posição num_start_position até num_end_position
2. WHEN num_end_position é NULL, THE Client_Identification_Service SHALL extrair bytes da posição num_start_position até o fim da primeira linha ou até o fim do buffer
3. THE Client_Identification_Service SHALL considerar os primeiros 7000 bytes como uma única linha quando não existe caractere de quebra de linha
4. THE Client_Identification_Service SHALL usar índice zero-based para num_start_position
5. THE Client_Identification_Service SHALL aplicar des_criteria_type ao valor extraído comparando com des_value
6. THE Client_Identification_Service SHALL aplicar des_function_origin ao valor extraído antes da comparação
7. THE Client_Identification_Service SHALL aplicar des_function_dest ao valor de des_value antes da comparação

### Requirement 7: Identificação por HEADER em Arquivos CSV

**User Story:** Como administrador do sistema, eu quero identificar clientes baseado em colunas específicas em arquivos CSV, para que arquivos delimitados possam ser identificados pelo conteúdo.

#### Acceptance Criteria

1. WHEN des_value_origin é HEADER e des_file_type do layout é CSV, THE Client_Identification_Service SHALL extrair valor da coluna com índice num_start_position
2. THE Client_Identification_Service SHALL usar des_column_separator do layout para separar colunas
3. THE Client_Identification_Service SHALL usar índice zero-based para num_start_position
4. THE Client_Identification_Service SHALL processar linha por linha dentro dos primeiros 7000 bytes
5. THE Client_Identification_Service SHALL considerar os primeiros 7000 bytes como uma única linha quando não existe caractere de quebra de linha
6. THE Client_Identification_Service SHALL aplicar des_criteria_type ao valor extraído comparando com des_value
7. THE Client_Identification_Service SHALL aplicar des_function_origin ao valor extraído antes da comparação
8. THE Client_Identification_Service SHALL aplicar des_function_dest ao valor de des_value antes da comparação

### Requirement 8: Identificação por TAG em Arquivos XML

**User Story:** Como administrador do sistema, eu quero identificar clientes baseado em caminhos XML, para que arquivos XML possam ser identificados por elementos específicos.

#### Acceptance Criteria

1. WHEN des_value_origin é TAG, THE Client_Identification_Service SHALL extrair valor usando o caminho XPath especificado em des_tag
2. THE Client_Identification_Service SHALL suportar caminhos aninhados em des_tag
3. THE Client_Identification_Service SHALL validar dentro dos primeiros 7000 bytes do arquivo
4. THE Client_Identification_Service SHALL aplicar des_criteria_type ao valor extraído comparando com des_value
5. THE Client_Identification_Service SHALL aplicar des_function_origin ao valor extraído antes da comparação
6. THE Client_Identification_Service SHALL aplicar des_function_dest ao valor de des_value antes da comparação
7. IF erro ocorre ao ler a TAG, THEN THE Client_Identification_Service SHALL registrar log com informações da falha
8. IF erro ocorre ao ler a TAG, THEN THE Client_Identification_Service SHALL continuar para a próxima regra de customer_identification

### Requirement 9: Identificação por KEY em Arquivos JSON

**User Story:** Como administrador do sistema, eu quero identificar clientes baseado em caminhos JSON, para que arquivos JSON possam ser identificados por propriedades específicas.

#### Acceptance Criteria

1. WHEN des_value_origin é KEY, THE Client_Identification_Service SHALL extrair valor usando o caminho de notação de ponto especificado em des_key
2. THE Client_Identification_Service SHALL suportar caminhos aninhados em des_key
3. THE Client_Identification_Service SHALL validar dentro dos primeiros 7000 bytes do arquivo
4. THE Client_Identification_Service SHALL aplicar des_criteria_type ao valor extraído comparando com des_value
5. THE Client_Identification_Service SHALL aplicar des_function_origin ao valor extraído antes da comparação
6. THE Client_Identification_Service SHALL aplicar des_function_dest ao valor de des_value antes da comparação
7. IF erro ocorre ao ler a KEY, THEN THE Client_Identification_Service SHALL registrar log com informações da falha
8. IF erro ocorre ao ler a KEY, THEN THE Client_Identification_Service SHALL continuar para a próxima regra de customer_identification

### Requirement 10: Funções de Transformação

**User Story:** Como administrador do sistema, eu quero aplicar transformações aos valores antes da comparação, para que identificações sejam flexíveis e independentes de formatação.

#### Acceptance Criteria

1. WHEN des_function_origin é UPPERCASE, THE Client_Identification_Service SHALL converter o valor de origem para maiúsculas antes da comparação
2. WHEN des_function_origin é LOWERCASE, THE Client_Identification_Service SHALL converter o valor de origem para minúsculas antes da comparação
3. WHEN des_function_origin é INITCAP, THE Client_Identification_Service SHALL converter a primeira letra para maiúscula e demais para minúsculas no valor de origem
4. WHEN des_function_origin é TRIM, THE Client_Identification_Service SHALL remover espaços em branco no início e fim do valor de origem
5. WHEN des_function_origin é NONE, THE Client_Identification_Service SHALL NOT aplicar transformação ao valor de origem
6. WHEN des_function_dest é UPPERCASE, THE Client_Identification_Service SHALL converter des_value para maiúsculas antes da comparação
7. WHEN des_function_dest é LOWERCASE, THE Client_Identification_Service SHALL converter des_value para minúsculas antes da comparação
8. WHEN des_function_dest é INITCAP, THE Client_Identification_Service SHALL converter a primeira letra para maiúscula e demais para minúsculas em des_value
9. WHEN des_function_dest é TRIM, THE Client_Identification_Service SHALL remover espaços em branco no início e fim de des_value
10. WHEN des_function_dest é NONE, THE Client_Identification_Service SHALL NOT aplicar transformação a des_value
11. THE Client_Identification_Service SHALL realizar comparações case-sensitive quando nenhuma função de transformação é aplicada

### Requirement 11: Reutilização de Buffer Existente

**User Story:** Como operador do sistema, eu quero que a identificação de clientes utilize o mesmo buffer já carregado para identificação de layout, para que não haja leitura duplicada do arquivo e o processamento seja eficiente.

#### Acceptance Criteria

1. THE Client_Identification_Service SHALL utilizar o mesmo buffer já carregado pelo Layout_Identification_Service
2. THE Client_Identification_Service SHALL utilizar a variável de ambiente FILE_ORIGIN_BUFFER_LIMIT existente para determinar o tamanho do buffer
3. THE System SHALL usar o valor configurado em FILE_ORIGIN_BUFFER_LIMIT sem modificações
4. THE Client_Identification_Service SHALL NOT realizar nova leitura do arquivo quando o buffer já está disponível

### Requirement 12: Persistência de Clientes Identificados

**User Story:** Como operador do sistema, eu quero que os clientes identificados sejam registrados no banco de dados, para que haja rastreabilidade de propriedade dos arquivos.

#### Acceptance Criteria

1. WHEN um ou mais clientes são identificados, THE System SHALL inserir um registro na tabela file_origin_clients para cada cliente
2. THE System SHALL registrar idt_file_origin no registro de file_origin_clients
3. THE System SHALL registrar idt_client no registro de file_origin_clients
4. THE System SHALL registrar dat_creation com timestamp atual no registro de file_origin_clients
5. THE Database SHALL prevenir duplicação através de índice único em idt_file_origin e idt_client

### Requirement 13: Estrutura de Dados - Tabela customer_identification

**User Story:** Como administrador do sistema, eu quero armazenar configurações de identificação de clientes, para que o sistema possa identificar automaticamente os proprietários dos arquivos.

#### Acceptance Criteria

1. THE Database SHALL manter tabela customer_identification com coluna idt_identification como chave primária
2. THE Database SHALL manter coluna idt_client em customer_identification referenciando cliente externo
3. THE Database SHALL manter coluna idt_acquirer em customer_identification como obrigatória
4. THE Database SHALL manter coluna idt_layout em customer_identification como opcional
5. THE Database SHALL manter coluna idt_merchant em customer_identification como opcional
6. THE Database SHALL manter coluna dat_start em customer_identification como opcional
7. THE Database SHALL manter coluna dat_end em customer_identification como opcional
8. THE Database SHALL manter coluna idt_plan em customer_identification como opcional
9. THE Database SHALL manter coluna flg_is_priority em customer_identification como opcional
10. THE Database SHALL manter coluna num_process_weight em customer_identification como opcional
11. THE Database SHALL manter coluna flg_active em customer_identification como obrigatória
12. THE Database SHALL manter foreign key de idt_layout em customer_identification para tabela layout

### Requirement 14: Estrutura de Dados - Tabela customer_identification_rule

**User Story:** Como administrador do sistema, eu quero armazenar regras de identificação de clientes, para que o sistema possa aplicar critérios específicos na identificação.

#### Acceptance Criteria

1. THE Database SHALL manter tabela customer_identification_rule com coluna idt_rule como chave primária
2. THE Database SHALL manter coluna idt_identification em customer_identification_rule como obrigatória
3. THE Database SHALL manter coluna des_rule em customer_identification_rule como obrigatória
4. THE Database SHALL manter coluna des_value_origin em customer_identification_rule como obrigatória com valores HEADER, TAG, FILENAME ou KEY
5. THE Database SHALL manter coluna des_criteria_type em customer_identification_rule como obrigatória com valores COMECA_COM, TERMINA_COM, CONTEM, CONTIDO ou IGUAL
6. THE Database SHALL manter coluna num_start_position em customer_identification_rule como opcional
7. THE Database SHALL manter coluna num_end_position em customer_identification_rule como opcional
8. THE Database SHALL manter coluna des_value em customer_identification_rule como opcional
9. THE Database SHALL manter coluna des_tag em customer_identification_rule como opcional
10. THE Database SHALL manter coluna des_key em customer_identification_rule como opcional
11. THE Database SHALL manter coluna des_function_origin em customer_identification_rule como opcional com valores UPPERCASE, LOWERCASE, INITCAP, TRIM ou NONE
12. THE Database SHALL manter coluna des_function_dest em customer_identification_rule como opcional com valores UPPERCASE, LOWERCASE, INITCAP, TRIM ou NONE
13. THE Database SHALL manter coluna flg_active em customer_identification_rule como obrigatória
14. THE Database SHALL manter foreign key de idt_identification em customer_identification_rule para tabela customer_identification

### Requirement 15: Estrutura de Dados - Tabela file_origin_clients

**User Story:** Como operador do sistema, eu quero registrar quais clientes foram identificados para cada arquivo, para que haja rastreabilidade completa de propriedade.

#### Acceptance Criteria

1. THE Database SHALL manter tabela file_origin_clients com coluna idt_client_identified como chave primária
2. THE Database SHALL manter coluna idt_file_origin em file_origin_clients como obrigatória
3. THE Database SHALL manter coluna idt_client em file_origin_clients como obrigatória
4. THE Database SHALL manter coluna dat_creation em file_origin_clients como obrigatória
5. THE Database SHALL manter coluna dat_update em file_origin_clients como opcional
6. THE Database SHALL manter índice único em idt_file_origin e idt_client em file_origin_clients
7. THE Database SHALL manter foreign key de idt_file_origin em file_origin_clients para tabela file_origin

### Requirement 16: Enumerações de Tipos

**User Story:** Como desenvolvedor do sistema, eu quero utilizar enumerações tipadas para valores de domínio, para que o código seja type-safe e evite valores inválidos.

#### Acceptance Criteria

1. THE System SHALL manter enum ValueOrigin com valores HEADER, TAG, FILENAME e KEY
2. THE System SHALL manter enum CriteriaType com valores COMECA_COM, TERMINA_COM, CONTEM, CONTIDO e IGUAL
3. THE System SHALL manter enum FunctionType com valores UPPERCASE, LOWERCASE, INITCAP, TRIM e NONE
4. THE System SHALL validar valores de des_value_origin contra enum ValueOrigin
5. THE System SHALL validar valores de des_criteria_type contra enum CriteriaType
6. THE System SHALL validar valores de des_function_origin e des_function_dest contra enum FunctionType
