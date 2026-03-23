# Documento de Requisitos

## Introdução

O sistema "Controle de Arquivos" é responsável pela entrada de dados para conciliação de cartão. O sistema coleta arquivos EDI de adquirentes via SFTP e APIs, identifica o cliente e layout de cada arquivo, e os encaminha para um destino (S3 ou SFTP interno), mantendo rastreabilidade completa do processamento.

A arquitetura é composta por dois pods principais:
- **Pod Orquestrador**: responsável por listar arquivos em servidores SFTP, registrá-los no banco de dados e publicar mensagens para processamento
- **Pod Processador**: responsável por consumir mensagens, baixar arquivos via streaming, identificar cliente e layout, fazer upload para destino e atualizar rastreabilidade

## Glossário

- **Sistema**: O sistema "Controle de Arquivos" como um todo
- **Orquestrador**: Pod responsável pela coleta e orquestração de arquivos
- **Processador**: Pod responsável pelo processamento e encaminhamento de arquivos
- **Arquivo_EDI**: Arquivo de intercâmbio eletrônico de dados proveniente de adquirentes
- **Cliente**: Empresa ou entidade que possui arquivos a serem processados
- **Layout**: Formato estrutural de um arquivo (CSV, TXT, JSON, OFX, XML)
- **Adquirente**: Instituição financeira que processa transações de cartão
- **Regra_Identificacao**: Critério usado para identificar cliente ou layout (COMECA-COM, TERMINA-COM, CONTEM, IGUAL)
- **Streaming**: Técnica de processamento de dados em fluxo contínuo sem carregar todo o conteúdo em memória
- **Vault**: Sistema de gerenciamento seguro de credenciais e segredos
- **Rastreabilidade**: Capacidade de registrar e acompanhar todas as etapas do processamento de um arquivo

## Requisitos

### Requisito 1: Carregar Configurações de Servidores SFTP

**User Story:** Como Orquestrador, eu quero carregar as configurações de servidores SFTP do banco de dados, para que eu possa saber quais servidores e diretórios monitorar.

#### Acceptance Criteria

1. WHEN o Orquestrador inicia, THE Orquestrador SHALL carregar todas as configurações ativas das tabelas server, sever_paths e sever_paths_in_out
2. THE Orquestrador SHALL validar que cada configuração possui servidor de origem e destino válidos
3. IF uma configuração estiver incompleta ou inválida, THEN THE Orquestrador SHALL registrar um erro estruturado e pular essa configuração
4. THE Orquestrador SHALL armazenar as configurações em memória para uso durante o ciclo de execução

### Requisito 2: Listar Arquivos em Servidores SFTP

**User Story:** Como Orquestrador, eu quero listar arquivos nos diretórios SFTP configurados, para que eu possa identificar novos arquivos a serem processados.

#### Acceptance Criteria

1. WHEN o Orquestrador executa um ciclo de coleta, THE Orquestrador SHALL conectar-se a cada servidor SFTP usando credenciais do Vault
2. THE Orquestrador SHALL listar todos os arquivos em cada diretório configurado como ORIGIN
3. WHEN um arquivo já existe na tabela file_origin com mesmo nome, adquirente e timestamp, THE Orquestrador SHALL ignorar esse arquivo
4. THE Orquestrador SHALL coletar metadados de cada arquivo novo (nome, tamanho, timestamp)
5. IF a conexão SFTP falhar, THEN THE Orquestrador SHALL registrar o erro e tentar o próximo servidor

### Requisito 3: Registrar Arquivos Coletados no Banco de Dados

**User Story:** Como Orquestrador, eu quero registrar cada arquivo novo encontrado no banco de dados, para que eu possa manter um histórico completo de coleta.

#### Acceptance Criteria

1. WHEN o Orquestrador identifica um arquivo novo, THE Orquestrador SHALL inserir um registro na tabela file_origin
2. THE Orquestrador SHALL definir o status inicial como COLETA e EM_ESPERA
3. THE Orquestrador SHALL registrar des_file_name, num_file_size, dat_timestamp_file e idt_sever_paths_in_out
4. THE Orquestrador SHALL garantir unicidade usando o índice (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)
5. IF a inserção falhar por violação de unicidade, THEN THE Orquestrador SHALL registrar um alerta e continuar

### Requisito 4: Publicar Mensagens no RabbitMQ

**User Story:** Como Orquestrador, eu quero publicar mensagens no RabbitMQ com informações dos arquivos coletados, para que o Processador possa consumi-las e processar os arquivos.

#### Acceptance Criteria

1. WHEN o Orquestrador registra arquivos no banco, THE Orquestrador SHALL agrupar um ou mais arquivos por mensagem
2. THE Orquestrador SHALL incluir na mensagem: idt_file_origin, des_file_name, idt_sever_paths_in_out
3. THE Orquestrador SHALL publicar a mensagem em uma fila RabbitMQ dedicada
4. WHEN a publicação for bem-sucedida, THE Orquestrador SHALL registrar log estruturado com detalhes da mensagem
5. IF a publicação falhar, THEN THE Orquestrador SHALL registrar o erro e tentar reenviar até 3 vezes

### Requisito 5: Controlar Concorrência de Execução

**User Story:** Como Orquestrador, eu quero controlar a concorrência de execução usando a tabela job_concurrency_control, para que múltiplas instâncias não processem os mesmos arquivos simultaneamente.

#### Acceptance Criteria

1. WHEN o Orquestrador inicia um ciclo de coleta, THE Orquestrador SHALL verificar se existe execução RUNNING na tabela job_concurrency_control
2. IF existe execução RUNNING, THEN THE Orquestrador SHALL aguardar ou cancelar a execução atual
3. WHEN o Orquestrador inicia processamento, THE Orquestrador SHALL criar registro com status RUNNING
4. WHEN o Orquestrador finaliza processamento, THE Orquestrador SHALL atualizar o status para COMPLETED e registrar dat_last_execution
5. IF o Orquestrador falhar durante execução, THEN THE Orquestrador SHALL atualizar o status para PENDING para permitir nova tentativa

### Requisito 6: Consumir Mensagens do RabbitMQ

**User Story:** Como Processador, eu quero consumir mensagens do RabbitMQ, para que eu possa processar os arquivos indicados.

#### Acceptance Criteria

1. THE Processador SHALL conectar-se à fila RabbitMQ e aguardar mensagens
2. WHEN uma mensagem é recebida, THE Processador SHALL extrair idt_file_origin e idt_sever_paths_in_out
3. THE Processador SHALL validar que o arquivo existe na tabela file_origin
4. IF o arquivo não existe ou já foi processado, THEN THE Processador SHALL descartar a mensagem e registrar alerta
5. WHEN o processamento é concluído com sucesso, THE Processador SHALL confirmar (ACK) a mensagem
6. IF o processamento falhar, THEN THE Processador SHALL rejeitar (NACK) a mensagem para reprocessamento

### Requisito 7: Baixar Arquivo via Streaming

**User Story:** Como Processador, eu quero baixar arquivos do SFTP usando streaming, para que eu possa processar arquivos grandes sem esgotar a memória.

#### Acceptance Criteria

1. WHEN o Processador inicia o download, THE Processador SHALL obter credenciais do Vault usando cod_vault e des_vault_secret
2. THE Processador SHALL abrir uma conexão SFTP e obter um InputStream do arquivo
3. THE Processador SHALL processar o arquivo em chunks sem carregar o conteúdo completo em memória
4. THE Processador SHALL manter o InputStream aberto apenas durante o tempo necessário para transferência
5. IF o download falhar, THEN THE Processador SHALL registrar o erro com detalhes e liberar recursos

### Requisito 8: Identificar Cliente por Nome do Arquivo

**User Story:** Como Processador, eu quero identificar o cliente usando regras baseadas no nome do arquivo, para que eu possa associar o arquivo ao cliente correto.

#### Acceptance Criteria

1. WHEN o Processador processa um arquivo, THE Processador SHALL carregar todas as regras ativas de customer_identification_rule
2. THE Processador SHALL aplicar cada regra ao nome do arquivo usando os critérios COMECA-COM, TERMINA-COM, CONTEM ou IGUAL
3. WHERE uma regra especifica num_starting_position e num_ending_position, THE Processador SHALL extrair substring do nome do arquivo
4. THE Processador SHALL considerar o cliente identificado apenas se TODAS as regras ativas retornarem true
5. IF nenhum cliente for identificado, THEN THE Processador SHALL registrar erro e atualizar status para ERRO
6. WHEN múltiplos clientes satisfazem as regras, THE Processador SHALL selecionar o cliente com maior num_processing_weight

### Requisito 9: Identificar Layout do Arquivo

**User Story:** Como Processador, eu quero identificar o layout do arquivo usando regras baseadas no nome ou conteúdo, para que eu possa processar o arquivo corretamente.

#### Acceptance Criteria

1. WHEN o Processador identifica o cliente, THE Processador SHALL carregar todas as regras ativas de layout_identification_rule
2. WHERE des_value_origin é FILENAME, THE Processador SHALL aplicar a regra ao nome do arquivo
3. WHERE des_value_origin é HEADER, THE Processador SHALL ler os primeiros 7000 bytes via streaming e aplicar a regra
4. THE Processador SHALL aplicar os critérios COMECA-COM, TERMINA-COM, CONTEM ou IGUAL conforme des_criterion_type_enum
5. THE Processador SHALL considerar o layout identificado apenas se TODAS as regras ativas retornarem true
6. IF nenhum layout for identificado, THEN THE Processador SHALL registrar erro e atualizar status para ERRO

### Requisito 10: Fazer Upload via Streaming para Destino

**User Story:** Como Processador, eu quero fazer upload do arquivo para o destino usando streaming, para que eu possa transferir arquivos grandes sem esgotar a memória.

#### Acceptance Criteria

1. WHEN o Processador identifica cliente e layout, THE Processador SHALL determinar o destino usando idt_sever_destination de sever_paths_in_out
2. WHERE o destino é S3, THE Processador SHALL usar multipart upload com InputStream encadeado
3. WHERE o destino é SFTP, THE Processador SHALL usar OutputStream encadeado diretamente do InputStream de origem
4. THE Processador SHALL transferir o arquivo em chunks sem carregar o conteúdo completo em memória
5. WHEN o upload é concluído, THE Processador SHALL validar que o tamanho do arquivo no destino corresponde ao tamanho original
6. IF o upload falhar, THEN THE Processador SHALL registrar erro detalhado e manter o arquivo na origem

### Requisito 11: Obter Credenciais do Vault

**User Story:** Como Sistema, eu quero obter credenciais de servidores do Vault, para que eu possa conectar-me a servidores SFTP e S3 de forma segura.

#### Acceptance Criteria

1. WHEN o Sistema precisa de credenciais, THE Sistema SHALL usar cod_vault e des_vault_secret da tabela server
2. THE Sistema SHALL conectar-se ao Vault e recuperar as credenciais
3. THE Sistema SHALL armazenar credenciais em memória apenas durante a operação necessária
4. THE Sistema SHALL nunca registrar credenciais em logs ou banco de dados
5. IF a recuperação de credenciais falhar, THEN THE Sistema SHALL registrar erro sem expor informações sensíveis

### Requisito 12: Registrar Rastreabilidade do Processamento

**User Story:** Como Sistema, eu quero registrar cada etapa do processamento na tabela file_origin_client_processing, para que eu possa rastrear o histórico completo de cada arquivo.

#### Acceptance Criteria

1. WHEN o Processador inicia uma etapa, THE Processador SHALL inserir registro em file_origin_client_processing com des_step e des_status EM_ESPERA
2. WHEN o Processador inicia processamento de uma etapa, THE Processador SHALL atualizar des_status para PROCESSAMENTO e registrar dat_step_start
3. WHEN o Processador conclui uma etapa com sucesso, THE Processador SHALL atualizar des_status para CONCLUIDO e registrar dat_step_end
4. IF uma etapa falhar, THEN THE Processador SHALL atualizar des_status para ERRO e registrar des_message_error
5. WHERE informações adicionais são relevantes, THE Processador SHALL armazenar dados estruturados em jsn_additional_info
6. THE Processador SHALL registrar as etapas: COLETA, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED

### Requisito 13: Associar Arquivo ao Cliente Identificado

**User Story:** Como Processador, eu quero associar o arquivo ao cliente identificado na tabela file_origin_client, para que eu possa manter o vínculo entre arquivo e cliente.

#### Acceptance Criteria

1. WHEN o Processador identifica o cliente com sucesso, THE Processador SHALL inserir registro em file_origin_client
2. THE Processador SHALL registrar idt_file_origin e idt_client
3. THE Processador SHALL definir flg_active como true
4. IF já existe associação ativa para o mesmo arquivo, THEN THE Processador SHALL atualizar o registro existente
5. WHEN a associação é criada, THE Processador SHALL usar idt_file_origin_client para rastreabilidade subsequente

### Requisito 14: Atualizar Informações do Arquivo após Identificação

**User Story:** Como Processador, eu quero atualizar a tabela file_origin com informações de layout identificado, para que o registro do arquivo esteja completo.

#### Acceptance Criteria

1. WHEN o Processador identifica o layout, THE Processador SHALL atualizar idt_layout na tabela file_origin
2. THE Processador SHALL atualizar des_file_type e des_transaction_type com base no layout identificado
3. WHEN todas as identificações são concluídas, THE Processador SHALL atualizar o timestamp de última modificação
4. IF a atualização falhar, THEN THE Processador SHALL registrar erro mas continuar o processamento

### Requisito 15: Tratar Erros de Processamento

**User Story:** Como Sistema, eu quero tratar erros de processamento de forma consistente, para que falhas sejam registradas e o sistema possa se recuperar.

#### Acceptance Criteria

1. WHEN ocorre um erro durante processamento, THE Sistema SHALL registrar log estruturado com contexto completo
2. THE Sistema SHALL atualizar file_origin_client_processing com des_status ERRO e des_message_error
3. WHERE o erro é recuperável, THE Sistema SHALL permitir reprocessamento via NACK no RabbitMQ
4. WHERE o erro não é recuperável, THE Sistema SHALL marcar o arquivo como ERRO permanente
5. THE Sistema SHALL incluir stack trace em jsn_additional_info para debugging
6. IF múltiplos erros ocorrem para o mesmo arquivo, THEN THE Sistema SHALL limitar tentativas a 5 reprocessamentos

### Requisito 16: Fornecer Health Checks

**User Story:** Como operador do sistema, eu quero health checks nos pods, para que eu possa monitorar a saúde do sistema no Kubernetes.

#### Acceptance Criteria

1. THE Orquestrador SHALL expor endpoint /actuator/health que retorna status UP quando operacional
2. THE Processador SHALL expor endpoint /actuator/health que retorna status UP quando operacional
3. THE Sistema SHALL verificar conectividade com banco de dados no health check
4. THE Sistema SHALL verificar conectividade com RabbitMQ no health check
5. IF alguma dependência crítica estiver indisponível, THEN THE Sistema SHALL retornar status DOWN

### Requisito 17: Configurar Ambiente Local de Desenvolvimento

**User Story:** Como desenvolvedor, eu quero um ambiente local completo via Docker Compose, para que eu possa desenvolver e testar o sistema localmente.

#### Acceptance Criteria

1. THE Sistema SHALL fornecer Docker Compose com Oracle XE, RabbitMQ, LocalStack (S3) e servidor SFTP
2. WHEN o desenvolvedor executa docker-compose up, THE Sistema SHALL inicializar todos os serviços necessários
3. THE Sistema SHALL aplicar scripts DDL automaticamente no Oracle ao inicializar
4. THE Sistema SHALL configurar LocalStack para simular S3 com endpoint local
5. THE Sistema SHALL configurar servidor SFTP com usuário e senha para testes

### Requisito 18: Aplicar Scripts DDL no Banco de Dados

**User Story:** Como Sistema, eu quero aplicar scripts DDL no banco de dados Oracle, para que todas as tabelas necessárias sejam criadas.

#### Acceptance Criteria

1. THE Sistema SHALL fornecer scripts DDL para todas as tabelas: job_concurrency_control, server, sever_paths, sever_paths_in_out, layout, layout_identification_rule, customer_identification, customer_identification_rule, file_origin, file_origin_client, file_origin_client_processing
2. THE Sistema SHALL criar índices necessários incluindo índice único em file_origin
3. THE Sistema SHALL criar sequences para geração de IDs primários
4. THE Sistema SHALL definir constraints de chave estrangeira entre tabelas relacionadas
5. WHERE aplicável, THE Sistema SHALL criar índices em colunas de busca frequente

### Requisito 19: Suportar Múltiplos Perfis de Configuração

**User Story:** Como Sistema, eu quero suportar múltiplos perfis Spring Boot, para que eu possa usar configurações diferentes em dev, staging e produção.

#### Acceptance Criteria

1. THE Sistema SHALL fornecer perfis: local, dev, staging, prod
2. WHERE perfil é local, THE Sistema SHALL usar LocalStack, RabbitMQ local e Oracle local
3. WHERE perfil é prod, THE Sistema SHALL usar AWS S3 real, RabbitMQ gerenciado e Oracle RDS
4. THE Sistema SHALL externalizar todas as configurações sensíveis via ConfigMaps e Secrets do Kubernetes
5. THE Sistema SHALL validar configurações obrigatórias na inicialização

### Requisito 20: Gerar Logs Estruturados

**User Story:** Como operador do sistema, eu quero logs estruturados em formato JSON, para que eu possa analisar logs facilmente em ferramentas de observabilidade.

#### Acceptance Criteria

1. THE Sistema SHALL gerar logs em formato JSON com campos: timestamp, level, logger, message, context
2. THE Sistema SHALL incluir correlation_id em todos os logs relacionados ao processamento de um arquivo
3. THE Sistema SHALL registrar logs de nível INFO para operações bem-sucedidas
4. THE Sistema SHALL registrar logs de nível ERROR para falhas com stack trace
5. THE Sistema SHALL registrar logs de nível WARN para situações anômalas que não impedem processamento

