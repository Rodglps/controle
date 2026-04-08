# Documento de Requisitos — File Multiprocessing Tracking

## Introdução

Esta funcionalidade adiciona o rastreamento granular do processamento de arquivos por cliente e por etapa (step) do pipeline EDI. Uma nova tabela `file_origin_processing` registra o estado de processamento para cada combinação de arquivo × cliente × etapa, permitindo visibilidade individual e preparando o sistema para que etapas futuras (ORDENACAO, PROCESSAMENTO) possam atualizar status de forma independente por cliente.

O escopo atual cobre apenas a etapa de COLETA no Consumer, criando registros na `file_origin_processing` após a identificação de clientes e mantendo step/status espelhados com a tabela `file_origin`.

## Glossário

- **Consumer**: Serviço que consome mensagens da fila RabbitMQ e executa a transferência de arquivos (download SFTP → upload S3/SFTP), incluindo identificação de layout e clientes.
- **FileTransferListener**: Listener RabbitMQ no Consumer responsável por orquestrar o fluxo de coleta de arquivos.
- **StatusUpdateService**: Serviço do Consumer que atualiza step/status na tabela `file_origin`, utilizando `nam_change_agent = "consumer-service"`.
- **ProcessingSplitService**: Novo serviço do Consumer responsável por criar e atualizar registros na tabela `file_origin_processing`.
- **file_origin**: Tabela principal que rastreia o estado geral de cada arquivo no pipeline.
- **file_origin_clients**: Tabela que armazena os clientes identificados para cada arquivo.
- **file_origin_processing**: Nova tabela que rastreia o estado de processamento por arquivo × cliente × etapa.
- **Step**: Enum que representa a etapa do pipeline (COLETA, DELETE, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED).
- **Status**: Enum que representa o status dentro de uma etapa (EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO).
- **COLETA**: Etapa de coleta de arquivos, executada pelo Consumer.
- **Ciclo_de_Coleta**: Uma execução completa da etapa COLETA para um arquivo, desde o consumo da mensagem até a finalização (sucesso ou erro).
- **Retry**: Reprocessamento dentro do mesmo Ciclo_de_Coleta causado por falha temporária (NACK na fila).
- **Reprocessamento_Total**: Nova execução da etapa COLETA para um arquivo que já passou por etapas posteriores, gerando um novo Ciclo_de_Coleta.

## Requisitos

### Requisito 1: Criação do script DDL para a tabela file_origin_processing

**User Story:** Como desenvolvedor, eu quero que a tabela `file_origin_processing` seja criada no banco Oracle via script DDL, para que o sistema possa persistir o rastreamento de processamento por cliente e etapa.

#### Critérios de Aceite

1. THE DDL_Script SHALL criar a tabela `file_origin_processing` com as colunas: `idt_file_origin_processing` (NUMBER(19), PK, NOT NULL), `idt_file_origin` (NUMBER(19), NOT NULL), `des_step` (VARCHAR2, NOT NULL), `des_status` (VARCHAR2, NOT NULL), `idt_client` (NUMBER(20), NULL), `des_message_error` (VARCHAR2(4000), NULL), `des_message_alert` (VARCHAR2(4000), NULL), `dat_step_start` (DATE, NULL), `dat_step_end` (DATE, NULL), `jsn_additional_info` (VARCHAR2(4000), NULL), `dat_creation` (DATE, NOT NULL), `dat_update` (DATE, NULL), `nam_change_agent` (VARCHAR2(50), NOT NULL).
2. THE DDL_Script SHALL criar uma sequence `file_origin_processing_seq` para geração do `idt_file_origin_processing`.
3. THE DDL_Script SHALL criar uma foreign key de `idt_file_origin` referenciando `file_origin(idt_file_origin)`.
4. THE DDL_Script SHALL criar índices de performance nas colunas `idt_file_origin` e `idt_client`.
5. THE DDL_Script SHALL seguir o padrão dos scripts DDL existentes (drop condicional antes da criação, comentários nas colunas, COMMIT ao final).

### Requisito 2: Inclusão do novo DDL na inicialização do banco de dados

**User Story:** Como desenvolvedor, eu quero que o script DDL da tabela `file_origin_processing` seja executado automaticamente na inicialização do banco, para que o ambiente esteja sempre atualizado.

#### Critérios de Aceite

1. THE Script_00_run_all SHALL incluir a execução do novo script DDL da tabela `file_origin_processing` na sequência correta, após a criação da tabela `file_origin_clients` (script 10) e antes da inserção de dados de exemplo (script 11).
2. THE Script_00_run_all SHALL atualizar a contagem total de scripts e o resumo de objetos do banco para incluir a nova tabela e sequence.

### Requisito 3: Criação da entidade JPA FileOriginProcessing

**User Story:** Como desenvolvedor, eu quero uma entidade JPA que mapeie a tabela `file_origin_processing`, para que o sistema possa persistir e consultar registros de processamento via Spring Data JPA.

#### Critérios de Aceite

1. THE Entidade_FileOriginProcessing SHALL mapear todas as colunas da tabela `file_origin_processing` utilizando anotações JPA (Entity, Table, Column, Id, GeneratedValue, SequenceGenerator, Enumerated).
2. THE Entidade_FileOriginProcessing SHALL utilizar os enums `Step` e `Status` existentes no módulo commons para os campos `des_step` e `des_status`.
3. THE Entidade_FileOriginProcessing SHALL permitir valor NULL no campo `idt_client` para representar arquivos sem cliente identificado.
4. THE Entidade_FileOriginProcessing SHALL ser criada no pacote `com.concil.edi.commons.entity` seguindo o padrão das entidades existentes (uso de Lombok @Data, @NoArgsConstructor, @AllArgsConstructor).

### Requisito 4: Criação do repositório JPA FileOriginProcessingRepository

**User Story:** Como desenvolvedor, eu quero um repositório JPA para a entidade `FileOriginProcessing`, para que o serviço de split possa consultar e persistir registros.

#### Critérios de Aceite

1. THE FileOriginProcessingRepository SHALL estender JpaRepository para a entidade FileOriginProcessing.
2. THE FileOriginProcessingRepository SHALL fornecer um método de consulta para buscar registros por `idt_file_origin`, `idt_client` e `des_step`.
3. THE FileOriginProcessingRepository SHALL ser criado no pacote `com.concil.edi.commons.repository` seguindo o padrão dos repositórios existentes.

### Requisito 5: Criação do ProcessingSplitService

**User Story:** Como desenvolvedor, eu quero um serviço que crie e atualize registros na `file_origin_processing` durante a etapa de COLETA, para que o processamento por cliente seja rastreado individualmente.

#### Critérios de Aceite

1. WHEN clientes são identificados para um arquivo, THE ProcessingSplitService SHALL criar um registro na `file_origin_processing` para cada cliente identificado, com `des_step=COLETA`, `des_status` espelhando o status da `file_origin`, e `idt_client` preenchido com o ID do cliente.
2. WHEN nenhum cliente é identificado para um arquivo, THE ProcessingSplitService SHALL criar um único registro na `file_origin_processing` com `idt_client=NULL`, `des_step=COLETA` e `des_status` espelhando o status da `file_origin`.
3. THE ProcessingSplitService SHALL preencher o campo `dat_step_start` com o timestamp capturado no início do consumo da mensagem (início do método `handleFileTransfer`), antes de qualquer operação.
4. THE ProcessingSplitService SHALL preencher o campo `dat_creation` com a data atual no momento da criação do registro.
5. THE ProcessingSplitService SHALL utilizar `nam_change_agent = "consumer-service"`, o mesmo valor utilizado pelo StatusUpdateService.
6. THE ProcessingSplitService SHALL ser criado no pacote `com.concil.edi.consumer.service` seguindo o padrão do StatusUpdateService (uso de @Service, @RequiredArgsConstructor, @Transactional).

### Requisito 6: Atualização dos registros de processing ao finalizar a coleta com sucesso

**User Story:** Como desenvolvedor, eu quero que os registros da `file_origin_processing` sejam atualizados quando a coleta finaliza com sucesso, para que o rastreamento reflita o estado final correto.

#### Critérios de Aceite

1. WHEN a coleta de um arquivo finaliza com status CONCLUIDO, THE ProcessingSplitService SHALL atualizar todos os registros da `file_origin_processing` referentes ao arquivo e step COLETA, definindo `des_status=CONCLUIDO`, `dat_step_end` com o timestamp atual e `dat_update` com a data atual.
2. WHEN a coleta de um arquivo finaliza com status CONCLUIDO, THE ProcessingSplitService SHALL manter `des_message_error=NULL` nos registros da `file_origin_processing`.

### Requisito 7: Atualização dos registros de processing ao finalizar a coleta com erro

**User Story:** Como desenvolvedor, eu quero que os registros da `file_origin_processing` sejam atualizados quando a coleta finaliza com erro, para que o rastreamento reflita a falha e a mensagem de erro.

#### Critérios de Aceite

1. WHEN a coleta de um arquivo finaliza com status ERRO, THE ProcessingSplitService SHALL atualizar todos os registros da `file_origin_processing` referentes ao arquivo e step COLETA, definindo `des_status=ERRO`, `dat_step_end` com o timestamp atual e `dat_update` com a data atual.
2. WHEN a coleta de um arquivo finaliza com status ERRO, THE ProcessingSplitService SHALL preencher o campo `des_message_error` com a mesma mensagem de erro registrada na `file_origin.des_message_error`.

### Requisito 8: Unicidade de registros durante retry no mesmo ciclo de coleta

**User Story:** Como desenvolvedor, eu quero que retries dentro do mesmo ciclo de coleta atualizem os registros existentes ao invés de criar duplicatas, para manter a integridade dos dados.

#### Critérios de Aceite

1. WHEN um retry ocorre dentro do mesmo Ciclo_de_Coleta, THE ProcessingSplitService SHALL buscar registros existentes na `file_origin_processing` pela combinação de `idt_file_origin` + `idt_client` + `des_step=COLETA` e atualizar os registros encontrados ao invés de criar novos.
2. WHEN um registro existente é encontrado durante retry, THE ProcessingSplitService SHALL atualizar `des_status`, `dat_step_start`, `dat_update` e `nam_change_agent` do registro existente.

### Requisito 9: Inserção de novos registros em reprocessamento total

**User Story:** Como desenvolvedor, eu quero que um reprocessamento total (nova coleta após etapas já concluídas) crie novos registros na `file_origin_processing`, para manter o histórico de cada ciclo de processamento.

#### Critérios de Aceite

1. WHEN um Reprocessamento_Total é disparado para um arquivo que já possui registros de steps posteriores à COLETA na `file_origin_processing`, THE ProcessingSplitService SHALL inserir novos registros para o step COLETA, gerando um novo Ciclo_de_Coleta.

### Requisito 10: Integração do ProcessingSplitService no FileTransferListener

**User Story:** Como desenvolvedor, eu quero que o FileTransferListener invoque o ProcessingSplitService nos momentos corretos do fluxo de coleta, para que o rastreamento de processamento seja executado automaticamente.

#### Critérios de Aceite

1. THE FileTransferListener SHALL capturar o timestamp de início (`dat_step_start`) no início do método `handleFileTransfer`, antes de qualquer operação de negócio.
2. WHEN a identificação de clientes é concluída, THE FileTransferListener SHALL invocar o ProcessingSplitService para criar os registros iniciais na `file_origin_processing` com `des_status=PROCESSAMENTO`.
3. WHEN a coleta finaliza com sucesso (status CONCLUIDO), THE FileTransferListener SHALL invocar o ProcessingSplitService para atualizar os registros da `file_origin_processing` com `des_status=CONCLUIDO` e `dat_step_end`.
4. WHEN a coleta finaliza com erro, THE FileTransferListener SHALL invocar o ProcessingSplitService para atualizar os registros da `file_origin_processing` com `des_status=ERRO`, `des_message_error` e `dat_step_end`.
5. THE FileTransferListener SHALL manter o campo `jsn_additional_info` como NULL durante a etapa de COLETA.
6. THE FileTransferListener SHALL manter o campo `des_message_alert` como NULL durante a etapa de COLETA.
