# Requirements Document

## Introduction

O sistema "Controle de Arquivos EDI" é responsável pela entrada de dados para conciliação de cartão de crédito. O sistema coleta arquivos EDI de adquirentes via SFTP, identifica arquivos a serem processados e os transfere para destinos configurados (S3 ou SFTP interno), mantendo rastreabilidade completa de cada operação através de registro de estados.

## Glossary

- **Producer**: Container Java responsável por agendar coletas, listar arquivos SFTP, registrar no banco de dados e publicar mensagens RabbitMQ
- **Consumer**: Container Java responsável por consumir mensagens RabbitMQ, baixar arquivos via streaming e fazer upload para destino
- **File_Origin**: Tabela Oracle que registra o estado e rastreabilidade de cada arquivo processado
- **Server**: Tabela Oracle que armazena configurações de servidores de origem e destino (SFTP, S3, NFS, Blob-Storage, Object Storage)
- **Sever_Paths**: Tabela Oracle que define diretórios dentro dos servidores
- **Sever_Paths_In_Out**: Tabela Oracle que mapeia origem para destino (PRINCIPAL ou SECUNDARIO)
- **Vault**: Sistema de gerenciamento de credenciais seguras
- **Streaming**: Técnica de transferência de dados sem carregar arquivo completo em memória
- **Step**: Etapa do processamento do arquivo (COLETA, DELETE, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED)
- **Status**: Estado atual do arquivo em um step (EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO)
- **Acquirer**: Adquirente de cartão (CIELO=1, REDE=2)
- **RabbitMQ_Message**: Mensagem contendo idt_file_origin, filename, idt_sever_path_origin, idt_sever_path_destination

## Requirements

### Requirement 1: Configuração de Servidores

**User Story:** Como administrador do sistema, eu quero configurar servidores de origem e destino no banco de dados, para que o sistema saiba onde coletar e armazenar arquivos.

#### Acceptance Criteria

1. THE Server SHALL armazenar configurações com idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin e flg_active
2. THE Server SHALL suportar tipos S3, Blob-Storage, Object Storage, SFTP e NFS
3. THE Server SHALL distinguir origem INTERNO ou EXTERNO
4. THE Server SHALL garantir unicidade através do índice server_idx_01 em (cod_server, flg_active)
5. WHEN um registro é criado, THE Server SHALL registrar dat_creation
6. WHEN um registro é atualizado, THE Server SHALL registrar dat_update e nam_change_agent

### Requirement 2: Configuração de Diretórios

**User Story:** Como administrador do sistema, eu quero configurar diretórios dentro dos servidores, para que o sistema saiba quais pastas monitorar e onde armazenar arquivos.

#### Acceptance Criteria

1. THE Sever_Paths SHALL armazenar configurações com idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type e flg_active
2. THE Sever_Paths SHALL suportar tipos ORIGIN e DESTINATION
3. THE Sever_Paths SHALL referenciar Server através de idt_server
4. WHEN um registro é criado, THE Sever_Paths SHALL registrar dat_creation
5. WHEN um registro é atualizado, THE Sever_Paths SHALL registrar dat_update e nam_change_agent

### Requirement 3: Mapeamento Origem-Destino

**User Story:** Como administrador do sistema, eu quero mapear diretórios de origem para diretórios de destino, para que o sistema saiba para onde transferir cada arquivo coletado.

#### Acceptance Criteria

1. THE Sever_Paths_In_Out SHALL armazenar mapeamentos com idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type e flg_active
2. THE Sever_Paths_In_Out SHALL suportar tipos PRINCIPAL e SECUNDARIO
3. THE Sever_Paths_In_Out SHALL garantir unicidade através do índice sever_paths_in_out_idx_01 em (idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
4. THE Sever_Paths_In_Out SHALL referenciar Sever_Paths através de idt_sever_path_origin e idt_sever_destination
5. WHEN um registro é criado, THE Sever_Paths_In_Out SHALL registrar dat_creation

### Requirement 4: Rastreabilidade de Arquivos

**User Story:** Como operador do sistema, eu quero rastrear o estado de cada arquivo processado, para que eu possa monitorar e auditar todas as transferências.

#### Acceptance Criteria

1. THE File_Origin SHALL armazenar rastreamento com idt_file_origin, idt_acquirer, idt_layout, des_file_name, num_file_size, des_file_mime_type, des_file_type, des_step, des_status, dat_timestamp_file, idt_sever_paths_in_out e flg_active
2. THE File_Origin SHALL suportar steps COLETA, DELETE, RAW, STAGING, ORDINATION, PROCESSING e PROCESSED
3. THE File_Origin SHALL suportar status EM_ESPERA, PROCESSAMENTO, CONCLUIDO e ERRO
4. THE File_Origin SHALL suportar tipos de arquivo csv, json, txt e xml
5. THE File_Origin SHALL suportar tipos de transação COMPLETO, CAPTURA e FINANCEIRO
6. THE File_Origin SHALL garantir unicidade através do índice file_origin_idx_01 em (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)
7. THE File_Origin SHALL armazenar num_retry e max_retry para controle de tentativas
8. WHEN um erro ocorre, THE File_Origin SHALL registrar des_message_error
9. WHEN um alerta é gerado, THE File_Origin SHALL registrar des_message_alert
10. WHEN um registro é atualizado, THE File_Origin SHALL registrar dat_update e nam_change_agent

### Requirement 5: Agendamento de Coleta

**User Story:** Como sistema automatizado, eu quero executar coletas periodicamente, para que novos arquivos sejam identificados e processados continuamente.

#### Acceptance Criteria

1. THE Producer SHALL executar scheduler a cada 2 minutos
2. WHEN o scheduler é acionado, THE Producer SHALL carregar configurações ativas de Server, Sever_Paths e Sever_Paths_In_Out do banco de dados
3. WHEN configurações são carregadas, THE Producer SHALL filtrar apenas registros com flg_active igual a 1
4. WHEN configurações são carregadas, THE Producer SHALL filtrar apenas Sever_Paths com des_path_type igual a ORIGIN

### Requirement 6: Autenticação em Servidores

**User Story:** Como sistema seguro, eu quero obter credenciais do Vault, para que senhas não sejam armazenadas em código ou configurações inseguras.

#### Acceptance Criteria

1. WHEN o Producer precisa conectar a um servidor, THE Producer SHALL ler cod_vault e des_vault_secret da tabela Server
2. WHERE Vault está disponível, THE Producer SHALL obter credenciais do Vault usando cod_vault e des_vault_secret
3. WHERE Vault não está disponível (MVP), THE Producer SHALL ler credenciais de variável de ambiente no formato JSON
4. WHEN credenciais de ambiente são usadas, THE Producer SHALL buscar variável com nome igual a cod_vault
5. WHEN credenciais de ambiente são usadas, THE Producer SHALL parsear JSON contendo user e password

### Requirement 7: Listagem de Arquivos SFTP

**User Story:** Como Producer, eu quero listar arquivos em diretórios SFTP configurados, para que eu possa identificar novos arquivos a serem coletados.

#### Acceptance Criteria

1. WHEN o Producer se conecta ao SFTP, THE Producer SHALL usar credenciais obtidas conforme Requirement 6
2. WHEN o Producer lista diretório SFTP, THE Producer SHALL obter des_path de Sever_Paths
3. WHEN o Producer encontra um arquivo, THE Producer SHALL capturar des_file_name, num_file_size e dat_timestamp_file
4. WHEN o Producer encontra um arquivo, THE Producer SHALL validar des_file_type contra tipos permitidos (csv, json, txt, xml)
5. IF des_file_type não é permitido, THEN THE Producer SHALL ignorar o arquivo
6. WHEN o Producer valida arquivo, THE Producer SHALL verificar se já existe em File_Origin usando índice file_origin_idx_01
7. IF arquivo já existe em File_Origin, THEN THE Producer SHALL ignorar o arquivo

### Requirement 8: Registro de Arquivo para Coleta

**User Story:** Como Producer, eu quero registrar arquivos identificados no banco de dados, para que o Consumer possa processá-los e para manter rastreabilidade.

#### Acceptance Criteria

1. WHEN o Producer identifica novo arquivo, THE Producer SHALL inserir registro em File_Origin
2. WHEN o Producer insere registro, THE Producer SHALL definir des_step como COLETA
3. WHEN o Producer insere registro, THE Producer SHALL definir des_status como EM_ESPERA
4. WHEN o Producer insere registro, THE Producer SHALL definir idt_acquirer como 1 (MVP)
5. WHEN o Producer insere registro, THE Producer SHALL definir idt_layout como 1 (MVP)
6. WHEN o Producer insere registro, THE Producer SHALL definir num_retry como 0
7. WHEN o Producer insere registro, THE Producer SHALL definir max_retry como 5
8. WHEN o Producer insere registro, THE Producer SHALL definir flg_active como 1
9. WHEN o Producer insere registro, THE Producer SHALL registrar dat_creation
10. WHEN o Producer insere registro, THE Producer SHALL obter idt_sever_paths_in_out com des_link_type igual a PRINCIPAL

### Requirement 9: Publicação de Mensagem RabbitMQ

**User Story:** Como Producer, eu quero publicar mensagens no RabbitMQ, para que o Consumer possa processar arquivos de forma assíncrona e escalável.

#### Acceptance Criteria

1. WHEN o Producer registra arquivo em File_Origin, THE Producer SHALL publicar mensagem em fila RabbitMQ Quorum
2. WHEN o Producer publica mensagem, THE RabbitMQ_Message SHALL conter idt_file_origin
3. WHEN o Producer publica mensagem, THE RabbitMQ_Message SHALL conter filename
4. WHEN o Producer publica mensagem, THE RabbitMQ_Message SHALL conter idt_sever_path_origin
5. WHEN o Producer publica mensagem, THE RabbitMQ_Message SHALL conter idt_sever_path_destination
6. WHEN o Producer publica mensagem, THE Producer SHALL publicar uma mensagem por arquivo

### Requirement 10: Tratamento de Erros no Producer

**User Story:** Como Producer, eu quero tratar erros durante coleta, para que falhas sejam registradas e o sistema continue operando.

#### Acceptance Criteria

1. IF exceção ocorre durante listagem SFTP, THEN THE Producer SHALL registrar log de erro
2. IF exceção ocorre após inserção em File_Origin, THEN THE Producer SHALL atualizar des_status para ERRO
3. IF exceção ocorre após inserção em File_Origin, THEN THE Producer SHALL registrar des_message_error com detalhes da exceção
4. IF exceção ocorre após inserção em File_Origin, THEN THE Producer SHALL incrementar num_retry
5. IF num_retry é menor que max_retry, THEN THE Producer SHALL manter des_step como COLETA para nova tentativa
6. IF num_retry atinge max_retry, THEN THE Producer SHALL registrar alerta em des_message_alert
7. WHEN erro é tratado, THE Producer SHALL continuar processamento de outros arquivos

### Requirement 11: Consumo de Mensagens RabbitMQ

**User Story:** Como Consumer, eu quero consumir mensagens do RabbitMQ, para que eu possa processar arquivos de forma assíncrona.

#### Acceptance Criteria

1. THE Consumer SHALL consumir mensagens de fila RabbitMQ Quorum
2. WHEN o Consumer recebe mensagem, THE Consumer SHALL extrair idt_file_origin
3. WHEN o Consumer recebe mensagem, THE Consumer SHALL extrair filename
4. WHEN o Consumer recebe mensagem, THE Consumer SHALL extrair idt_sever_path_origin
5. WHEN o Consumer recebe mensagem, THE Consumer SHALL extrair idt_sever_path_destination
6. WHEN o Consumer inicia processamento, THE Consumer SHALL atualizar File_Origin definindo des_status como PROCESSAMENTO

### Requirement 12: Download Streaming de Arquivo

**User Story:** Como Consumer, eu quero baixar arquivos usando streaming, para que o sistema não sobrecarregue memória com arquivos grandes.

#### Acceptance Criteria

1. WHEN o Consumer processa mensagem, THE Consumer SHALL obter configuração de Server e Sever_Paths usando idt_sever_path_origin
2. WHEN o Consumer obtém configuração, THE Consumer SHALL obter credenciais conforme Requirement 6
3. WHEN o Consumer conecta ao SFTP origem, THE Consumer SHALL abrir InputStream para o arquivo
4. THE Consumer SHALL manter InputStream aberto sem carregar arquivo completo em memória
5. IF erro ocorre durante abertura de InputStream, THEN THE Consumer SHALL registrar exceção e atualizar File_Origin conforme Requirement 14

### Requirement 13: Upload Streaming para Destino

**User Story:** Como Consumer, eu quero fazer upload de arquivos usando streaming, para que o sistema transfira dados eficientemente sem sobrecarregar memória.

#### Acceptance Criteria

1. WHEN o Consumer obtém InputStream de origem, THE Consumer SHALL obter configuração de destino usando idt_sever_path_destination
2. WHEN o Consumer obtém configuração de destino, THE Consumer SHALL obter credenciais conforme Requirement 6
3. WHERE destino é S3, THE Consumer SHALL usar multipart upload com InputStream
4. WHERE destino é SFTP, THE Consumer SHALL abrir OutputStream e transferir dados do InputStream
5. WHEN o Consumer transfere dados, THE Consumer SHALL usar buffer para streaming sem carregar arquivo completo em memória
6. WHEN upload é concluído, THE Consumer SHALL fechar InputStream e OutputStream
7. IF erro ocorre durante upload, THEN THE Consumer SHALL registrar exceção e atualizar File_Origin conforme Requirement 14

### Requirement 14: Atualização de Status após Processamento

**User Story:** Como Consumer, eu quero atualizar o status do arquivo após processamento, para que a rastreabilidade reflita o estado atual.

#### Acceptance Criteria

1. WHEN upload é concluído com sucesso, THE Consumer SHALL atualizar File_Origin definindo des_status como CONCLUIDO
2. WHEN upload é concluído com sucesso, THE Consumer SHALL registrar dat_update
3. WHEN upload é concluído com sucesso, THE Consumer SHALL definir nam_change_agent como identificador do Consumer
4. IF exceção ocorre durante processamento, THEN THE Consumer SHALL atualizar File_Origin definindo des_status como ERRO
5. IF exceção ocorre durante processamento, THEN THE Consumer SHALL registrar des_message_error com detalhes da exceção
6. IF exceção ocorre durante processamento, THEN THE Consumer SHALL incrementar num_retry
7. IF num_retry é menor que max_retry, THEN THE Consumer SHALL rejeitar mensagem RabbitMQ para reprocessamento
8. IF num_retry atinge max_retry, THEN THE Consumer SHALL reconhecer mensagem RabbitMQ e registrar alerta em des_message_alert

### Requirement 15: Infraestrutura Docker Compose

**User Story:** Como desenvolvedor, eu quero ambiente local completo via Docker Compose, para que eu possa desenvolver e testar o sistema localmente.

#### Acceptance Criteria

1. THE Docker_Compose SHALL provisionar container Oracle XE ou versão lite
2. THE Docker_Compose SHALL provisionar container RabbitMQ 3.12 ou superior
3. THE Docker_Compose SHALL provisionar container LocalStack para simular AWS S3
4. THE Docker_Compose SHALL provisionar container SFTP server versão lite
5. THE Docker_Compose SHALL provisionar container SFTP client para testes manuais
6. THE Docker_Compose SHALL provisionar container Producer
7. THE Docker_Compose SHALL provisionar container Consumer
8. THE Docker_Compose SHALL configurar rede em modo bridge
9. WHEN containers são iniciados, THE Docker_Compose SHALL garantir comunicação entre todos os containers

### Requirement 16: Scripts DDL Oracle

**User Story:** Como desenvolvedor, eu quero scripts DDL para criar estrutura do banco, para que o ambiente possa ser provisionado automaticamente.

#### Acceptance Criteria

1. THE DDL_Script SHALL criar tabela server com todas as colunas, tipos, constraints e índices especificados
2. THE DDL_Script SHALL criar tabela sever_paths com todas as colunas, tipos, constraints, índices e foreign keys especificados
3. THE DDL_Script SHALL criar tabela sever_paths_in_out com todas as colunas, tipos, constraints, índices e foreign keys especificados
4. THE DDL_Script SHALL criar tabela file_origin com todas as colunas, tipos, constraints, índices e foreign keys especificados
5. THE DDL_Script SHALL criar sequences para campos auto-incrementais
6. THE DDL_Script SHALL ser idempotente permitindo execução múltipla sem erros

### Requirement 17: Estrutura Mono Repositório Maven

**User Story:** Como desenvolvedor, eu quero estrutura mono repositório Maven, para que código comum seja compartilhado entre Producer e Consumer.

#### Acceptance Criteria

1. THE Maven_Project SHALL ter módulo commons para código compartilhado
2. THE Maven_Project SHALL ter módulo producer para container Producer
3. THE Maven_Project SHALL ter módulo consumer para container Consumer
4. THE Maven_Project SHALL ter pom.xml raiz gerenciando dependências comuns
5. WHEN módulo producer ou consumer é compilado, THE Maven_Project SHALL incluir dependências do módulo commons

### Requirement 18: Configuração Spring Boot

**User Story:** Como desenvolvedor, eu quero configuração externalizada via Spring Boot, para que o sistema seja configurável sem recompilação.

#### Acceptance Criteria

1. THE Producer SHALL usar Spring Boot 3.4 com Java 21
2. THE Consumer SHALL usar Spring Boot 3.4 com Java 21
3. THE Producer SHALL suportar profiles (dev, prod)
4. THE Consumer SHALL suportar profiles (dev, prod)
5. THE Producer SHALL expor endpoint health check
6. THE Consumer SHALL expor endpoint health check
7. THE Producer SHALL usar logs estruturados
8. THE Consumer SHALL usar logs estruturados

### Requirement 19: Testes Unitários

**User Story:** Como desenvolvedor, eu quero testes unitários para regras de negócio, para que o sistema tenha qualidade e confiabilidade.

#### Acceptance Criteria

1. THE Producer SHALL ter testes unitários para validação de tipo de arquivo
2. THE Producer SHALL ter testes unitários para verificação de duplicatas
3. THE Producer SHALL ter testes unitários para tratamento de erros
4. THE Consumer SHALL ter testes unitários para lógica de streaming
5. THE Consumer SHALL ter testes unitários para atualização de status
6. THE Consumer SHALL ter testes unitários para tratamento de erros
7. WHEN testes são executados, THE Maven_Project SHALL gerar relatório de cobertura

### Requirement 20: Teste de Integração End-to-End

**User Story:** Como desenvolvedor, eu quero teste integrado ponta a ponta, para que o fluxo completo do sistema seja validado desde a detecção do arquivo até a transferência final.

#### Acceptance Criteria

1. THE E2E_Test SHALL usar todos os containers do Docker Compose (Oracle, RabbitMQ, LocalStack S3, SFTP, Producer, Consumer)
2. WHEN o E2E_Test inicia, THE E2E_Test SHALL adicionar arquivo de teste na pasta SFTP monitorada
3. WHEN arquivo é adicionado, THE E2E_Test SHALL aguardar próximo ciclo do scheduler (máximo 2 minutos)
4. WHEN Producer detecta arquivo, THE E2E_Test SHALL validar que registro foi criado em File_Origin com des_step igual a COLETA e des_status igual a EM_ESPERA
5. WHEN Producer publica mensagem, THE E2E_Test SHALL validar que mensagem foi publicada no RabbitMQ contendo idt_file_origin, filename, idt_sever_path_origin e idt_sever_path_destination
6. WHEN Consumer consome mensagem, THE E2E_Test SHALL validar que des_status foi atualizado para PROCESSAMENTO em File_Origin
7. WHEN Consumer baixa arquivo, THE E2E_Test SHALL validar que download via streaming foi executado do SFTP origem
8. WHERE destino é S3, THE E2E_Test SHALL validar que upload via streaming foi executado para LocalStack S3
9. WHERE destino é SFTP, THE E2E_Test SHALL validar que upload via streaming foi executado para SFTP destino
10. WHEN Consumer completa upload, THE E2E_Test SHALL validar que des_status foi atualizado para CONCLUIDO em File_Origin
11. WHEN processamento é concluído, THE E2E_Test SHALL validar que arquivo existe no destino
12. WHEN processamento é concluído, THE E2E_Test SHALL validar integridade do arquivo comparando num_file_size entre origem e destino
13. WHEN processamento é concluído, THE E2E_Test SHALL validar integridade do arquivo comparando conteúdo entre origem e destino
14. THE E2E_Test SHALL executar cenário completo para destino S3
15. THE E2E_Test SHALL executar cenário completo para destino SFTP
