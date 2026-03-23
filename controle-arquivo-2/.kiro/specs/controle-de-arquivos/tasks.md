# Plano de Implementação: Controle de Arquivos

## Visão Geral

Sistema distribuído para coleta, identificação e encaminhamento de arquivos EDI de adquirentes. A implementação será feita de forma incremental, começando pela infraestrutura e estrutura do projeto, seguindo para os componentes principais (Orquestrador e Processador), e finalizando com integração e testes.

A arquitetura é baseada em dois pods principais que se comunicam via RabbitMQ, com processamento streaming para suportar arquivos de qualquer tamanho.

## Tarefas

- [x] 1. Configurar estrutura do projeto e infraestrutura local
  - [x] 1.1 Criar estrutura de mono repositório Maven
    - Criar projeto Maven multi-módulo com módulos: common, orchestrator, processor
    - Configurar pom.xml raiz com dependências Spring Boot 3, Java 17
    - Configurar plugins Maven (compiler, surefire, jacoco)
    - _Requisitos: 17.1, 17.2_
  
  - [x] 1.2 Criar Docker Compose para ambiente local
    - Criar docker-compose.yml com Oracle XE, RabbitMQ, LocalStack (S3), servidor SFTP
    - Configurar volumes para persistência de dados
    - Configurar networks para comunicação entre containers
    - Adicionar healthchecks para todos os serviços
    - _Requisitos: 17.1, 17.2, 17.3, 17.4, 17.5_
  
  - [x] 1.3 Criar scripts DDL Oracle
    - Criar script para tabela job_concurrency_control com sequences e índices
    - Criar scripts para tabelas server, sever_paths, sever_paths_in_out
    - Criar scripts para tabelas layout, layout_identification_rule
    - Criar scripts para tabelas customer_identification, customer_identification_rule
    - Criar scripts para tabelas file_origin, file_origin_client, file_origin_client_processing
    - Adicionar constraints de chave estrangeira e índices (incluindo índice único em file_origin)
    - _Requisitos: 18.1, 18.2, 18.3, 18.4, 18.5_


- [x] 2. Implementar módulo common com entidades e configurações compartilhadas
  - [x] 2.1 Criar entidades de domínio JPA
    - Criar entidades: Server, SeverPaths, SeverPathsInOut com anotações JPA
    - Criar entidades: Layout, LayoutIdentificationRule
    - Criar entidades: CustomerIdentification, CustomerIdentificationRule
    - Criar entidades: FileOrigin, FileOriginClient, FileOriginClientProcessing
    - Criar entidades: JobConcurrencyControl
    - Adicionar enums: TipoServidor, OrigemServidor, TipoCaminho, TipoLink, TipoCriterio, OrigemValor, EtapaProcessamento, StatusProcessamento
    - _Requisitos: 1.1, 3.1, 8.1, 9.1, 12.1_
  
  - [x] 2.2 Escrever testes unitários para entidades
    - Testar validações de campos obrigatórios
    - Testar relacionamentos JPA
    - _Requisitos: 1.1, 3.1_
  
  - [x] 2.3 Criar repositórios JPA
    - Criar FileOriginRepository com query customizada para busca por nome, adquirente e timestamp
    - Criar FileOriginClientRepository
    - Criar FileOriginClientProcessingRepository com query para buscar por idt_file_origin_client
    - Criar CustomerIdentificationRuleRepository com query para buscar regras ativas por adquirente
    - Criar LayoutIdentificationRuleRepository com query para buscar regras ativas por cliente e adquirente
    - Criar ServerRepository, SeverPathsRepository, SeverPathsInOutRepository
    - Criar JobConcurrencyControlRepository
    - _Requisitos: 1.1, 2.3, 8.1, 9.1_
  
  - [x] 2.4 Criar configurações Spring Boot compartilhadas
    - Criar application.yml com perfis: local, dev, staging, prod
    - Configurar datasource Oracle para cada perfil
    - Configurar RabbitMQ connection factory
    - Configurar AWS SDK v2 para S3 (LocalStack em local, AWS real em prod)
    - Configurar logging estruturado JSON com Logback
    - _Requisitos: 19.1, 19.2, 19.3, 19.4, 20.1_

- [x] 3. Implementar cliente Vault para gerenciamento de credenciais
  - [x] 3.1 Criar VaultClient
    - Implementar método obterCredenciais(codVault, secretPath) usando Spring Cloud Vault ou HTTP client
    - Adicionar cache de credenciais com TTL configurável
    - Implementar renovação automática de tokens
    - Adicionar tratamento de erros sem expor credenciais em logs
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5_
  
  - [x] 3.2 Escrever testes unitários para VaultClient
    - Testar obtenção de credenciais com mock do Vault
    - Testar cache de credenciais
    - Testar tratamento de erros
    - _Requisitos: 11.1, 11.5_
  
  - [x] 3.3 Escrever teste de propriedade para VaultClient
    - **Propriedade 2: Obtenção de Credenciais do Vault**
    - **Valida: Requisitos 2.1, 11.1, 11.2, 11.4**
    - Verificar que credenciais nunca são registradas em logs
    - _Requisitos: 11.4_


- [x] 4. Implementar serviço de identificação de cliente
  - [x] 4.1 Criar ClienteIdentificationService
    - Implementar método identificar(nomeArquivo, idAdquirente) que retorna Optional<Cliente>
    - Implementar método aplicarRegra(regra, nomeArquivo) para cada critério: COMECA-COM, TERMINA-COM, CONTEM, IGUAL
    - Implementar lógica de extração de substring usando num_starting_position e num_ending_position
    - Implementar lógica de aplicação de TODAS as regras ativas (AND lógico)
    - Implementar desempate por num_processing_weight quando múltiplos clientes satisfazem regras
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.6_
  
  - [x] 4.2 Escrever testes unitários para ClienteIdentificationService
    - Testar cada critério individualmente (COMECA-COM, TERMINA-COM, CONTEM, IGUAL)
    - Testar extração de substring com posições variadas
    - Testar cenário onde nenhum cliente é identificado
    - Testar cenário de múltiplos clientes com desempate por peso
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.6_
  
  - [x] 4.3 Escrever teste de propriedade para identificação de cliente
    - **Propriedade 14: Aplicação de Regras de Identificação de Cliente**
    - **Valida: Requisitos 8.1, 8.2, 8.3, 8.4**
    - Gerar nomes de arquivo aleatórios e regras variadas
    - Verificar que cliente só é identificado se TODAS as regras retornam true
    - _Requisitos: 8.4_
  
  - [x] 4.4 Escrever teste de propriedade para desempate de clientes
    - **Propriedade 16: Desempate de Múltiplos Clientes**
    - **Valida: Requisitos 8.6**
    - Gerar múltiplos clientes com pesos diferentes
    - Verificar que cliente com maior peso é selecionado
    - _Requisitos: 8.6_

- [x] 5. Implementar serviço de identificação de layout
  - [x] 5.1 Criar LayoutIdentificationService
    - Implementar método identificar(nomeArquivo, headerStream, idCliente, idAdquirente) que retorna Optional<Layout>
    - Implementar método aplicarRegra(regra, origem, conteudo) para critérios: COMECA-COM, TERMINA-COM, CONTEM, IGUAL
    - Implementar lógica para regras com des_value_origin = FILENAME (usar nome do arquivo)
    - Implementar lógica para regras com des_value_origin = HEADER (ler primeiros 7000 bytes via streaming)
    - Implementar lógica de aplicação de TODAS as regras ativas (AND lógico)
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_
  
  - [x] 5.2 Escrever testes unitários para LayoutIdentificationService
    - Testar identificação por FILENAME
    - Testar identificação por HEADER com mock de InputStream
    - Testar cenário onde nenhum layout é identificado
    - Testar leitura de exatamente 7000 bytes do header
    - _Requisitos: 9.1, 9.2, 9.3, 9.5_
  
  - [x] 5.3 Escrever teste de propriedade para identificação de layout
    - **Propriedade 17: Aplicação de Regras de Identificação de Layout**
    - **Valida: Requisitos 9.1, 9.2, 9.3, 9.4, 9.5**
    - Gerar nomes de arquivo e conteúdo de header aleatórios
    - Gerar regras variadas (FILENAME e HEADER)
    - Verificar que layout só é identificado se TODAS as regras retornam true
    - _Requisitos: 9.4_


- [x] 6. Implementar serviço de transferência streaming
  - [x] 6.1 Criar StreamingTransferService
    - Implementar método transferirSFTPparaS3(source, bucket, key, tamanho) usando S3 multipart upload
    - Implementar método transferirSFTPparaSFTP(source, destino, caminho, tamanho) com OutputStream encadeado
    - Implementar lógica de chunks de 5MB para multipart upload
    - Implementar tratamento de erros com abort de multipart upload em caso de falha
    - Implementar validação de tamanho após upload
    - _Requisitos: 7.3, 10.2, 10.3, 10.5_
  
  - [x] 6.2 Escrever testes unitários para StreamingTransferService
    - Testar transferência SFTP para S3 com mock de S3Client
    - Testar transferência SFTP para SFTP com mock de OutputStream
    - Testar tratamento de erro com abort de multipart upload
    - Testar validação de tamanho após upload
    - _Requisitos: 10.2, 10.3, 10.5_
  
  - [x] 6.3 Escrever teste de propriedade para validação de tamanho
    - **Propriedade 22: Validação de Tamanho após Upload**
    - **Valida: Requisitos 10.5, 10.6**
    - Gerar InputStreams com tamanhos variados (1KB a 100MB)
    - Verificar que tamanho no destino corresponde ao tamanho original
    - _Requisitos: 10.5_

- [x] 7. Implementar serviço de rastreabilidade
  - [x] 7.1 Criar RastreabilidadeService
    - Implementar método registrarEtapa(idFileOriginClient, step, status) para inserir registro
    - Implementar método atualizarStatus(idProcessing, status, mensagemErro) para atualizar status
    - Implementar método registrarInicio(idProcessing) para atualizar dat_step_start
    - Implementar método registrarConclusao(idProcessing, infoAdicional) para atualizar dat_step_end e jsn_additional_info
    - Implementar validação de transições de status válidas
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5_
  
  - [x] 7.2 Escrever testes unitários para RastreabilidadeService
    - Testar registro de nova etapa com status EM_ESPERA
    - Testar atualização para PROCESSAMENTO com dat_step_start
    - Testar atualização para CONCLUIDO com dat_step_end
    - Testar atualização para ERRO com des_message_error
    - Testar armazenamento de jsn_additional_info
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5_
  
  - [x] 7.3 Escrever teste de propriedade para máquina de estados
    - **Propriedade 24: Máquina de Estados de Rastreabilidade**
    - **Valida: Requisitos 12.1, 12.2, 12.3, 12.4**
    - Gerar sequências de etapas e status
    - Verificar que todas as transições válidas são permitidas
    - _Requisitos: 12.1, 12.2, 12.3, 12.4_

- [x] 8. Checkpoint - Validar serviços core
  - Executar todos os testes unitários e de propriedade dos serviços core
  - Verificar cobertura de código (mínimo 80%)
  - Perguntar ao usuário se há dúvidas ou ajustes necessários


- [x] 9. Implementar Pod Orquestrador - Parte 1: Configuração e coleta
  - [x] 9.1 Criar OrquestradorService
    - Implementar método carregarConfiguracoes() para carregar server, sever_paths, sever_paths_in_out
    - Implementar validação de configurações (servidor origem e destino válidos)
    - Implementar tratamento de configurações inválidas (registrar erro e pular)
    - _Requisitos: 1.1, 1.2, 1.3, 1.4_
  
  - [x] 9.2 Implementar cliente SFTP
    - Criar SFTPClient usando JSch ou Apache MINA SSHD
    - Implementar método conectar(host, port, credenciais) com credenciais do Vault
    - Implementar método listarArquivos(caminho) que retorna lista de metadados (nome, tamanho, timestamp)
    - Implementar método obterInputStream(caminho) para download streaming
    - Implementar tratamento de erros de conexão e timeout
    - _Requisitos: 2.1, 2.2, 2.4, 2.5, 7.1, 7.2_
  
  - [x] 9.3 Escrever testes unitários para SFTPClient
    - Testar listagem de arquivos com mock de servidor SFTP
    - Testar obtenção de InputStream
    - Testar tratamento de erros de conexão
    - _Requisitos: 2.1, 2.2, 2.5_
  
  - [x] 9.4 Implementar lógica de coleta de arquivos
    - Implementar método executarCicloColeta() que itera sobre configurações
    - Para cada servidor SFTP, listar arquivos e coletar metadados
    - Verificar deduplicação: ignorar arquivos que já existem em file_origin (mesmo nome, adquirente, timestamp)
    - Registrar novos arquivos em file_origin com status COLETA e EM_ESPERA
    - Incluir des_file_name, num_file_size, dat_timestamp_file, idt_sever_paths_in_out
    - Tratar violação de unicidade (registrar alerta e continuar)
    - _Requisitos: 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5_
  
  - [x] 9.5 Escrever testes unitários para coleta de arquivos
    - Testar ciclo completo de coleta com mocks
    - Testar deduplicação de arquivos
    - Testar registro em file_origin
    - Testar tratamento de violação de unicidade
    - _Requisitos: 2.3, 3.1, 3.3, 3.4, 3.5_
  
  - [x] 9.6 Escrever teste de propriedade para deduplicação
    - **Propriedade 3: Deduplicação de Arquivos**
    - **Valida: Requisitos 2.3**
    - Gerar listas de arquivos com duplicatas
    - Verificar que arquivos duplicados são ignorados
    - _Requisitos: 2.3_
  
  - [x] 9.7 Escrever teste de propriedade para garantia de unicidade
    - **Propriedade 7: Garantia de Unicidade**
    - **Valida: Requisitos 3.4, 3.5**
    - Tentar inserir arquivos duplicados
    - Verificar que índice único previne duplicatas e sistema registra alerta
    - _Requisitos: 3.4, 3.5_


- [x] 10. Implementar Pod Orquestrador - Parte 2: Publicação RabbitMQ e controle de concorrência
  - [x] 10.1 Criar RabbitMQPublisher
    - Implementar método publicar(mensagem) usando Spring AMQP
    - Implementar serialização de MensagemProcessamento (idt_file_origin, des_file_name, idt_sever_paths_in_out, correlationId)
    - Implementar confirmação de publicação (publisher confirms)
    - Implementar retry até 3 vezes em caso de falha
    - Registrar log estruturado com detalhes da mensagem publicada
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [x] 10.2 Escrever testes unitários para RabbitMQPublisher
    - Testar publicação de mensagem com mock de RabbitTemplate
    - Testar serialização e deserialização de MensagemProcessamento (round-trip)
    - Testar retry em caso de falha
    - _Requisitos: 4.2, 4.5_
  
  - [x] 10.3 Escrever teste de propriedade para serialização de mensagens
    - **Propriedade 8: Serialização de Mensagens RabbitMQ**
    - **Valida: Requisitos 4.2, 6.2**
    - Gerar mensagens com campos variados
    - Verificar que serialização e deserialização preservam todos os campos (round-trip)
    - _Requisitos: 4.2_
  
  - [x] 10.4 Criar JobConcurrencyService
    - Implementar método iniciarExecucao(jobName) que cria registro com status RUNNING
    - Implementar método verificarExecucaoAtiva(jobName) que verifica se existe execução RUNNING
    - Implementar método finalizarExecucao(jobName, sucesso) que atualiza status para COMPLETED ou PENDING
    - Implementar método registrarDataExecucao(jobName) que atualiza dat_last_execution
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [x] 10.5 Escrever testes unitários para JobConcurrencyService
    - Testar criação de registro com status RUNNING
    - Testar verificação de execução ativa
    - Testar finalização com sucesso (COMPLETED)
    - Testar finalização com falha (PENDING)
    - _Requisitos: 5.3, 5.4, 5.5_
  
  - [x] 10.6 Integrar publicação RabbitMQ no OrquestradorService
    - Após registrar arquivos em file_origin, agrupar arquivos e publicar mensagens
    - Incluir correlationId único para rastreamento
    - Tratar falhas de publicação (registrar erro crítico após 3 tentativas)
    - _Requisitos: 4.1, 4.2, 4.4, 4.5_
  
  - [x] 10.7 Integrar controle de concorrência no OrquestradorService
    - No início de executarCicloColeta(), verificar execução ativa
    - Se existe execução RUNNING, aguardar ou cancelar
    - Criar registro RUNNING ao iniciar
    - Atualizar para COMPLETED ao finalizar com sucesso
    - Atualizar para PENDING em caso de falha
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5_
  
  - [x] 10.8 Criar scheduler para execução periódica
    - Configurar @Scheduled com cron expression configurável
    - Invocar executarCicloColeta() periodicamente
    - Adicionar logs estruturados de início e fim de ciclo
    - _Requisitos: 1.1, 5.3_


- [x] 11. Implementar aplicação Spring Boot do Orquestrador
  - [x] 11.1 Criar classe principal OrquestradorApplication
    - Criar classe com @SpringBootApplication
    - Habilitar scheduling com @EnableScheduling
    - Configurar component scan para todos os serviços
    - _Requisitos: 1.1_
  
  - [x] 11.2 Configurar health checks
    - Adicionar Spring Boot Actuator
    - Expor endpoint /actuator/health
    - Configurar health indicators para banco de dados e RabbitMQ
    - Retornar status UP quando todas as dependências estão disponíveis
    - Retornar status DOWN se alguma dependência crítica estiver indisponível
    - _Requisitos: 16.1, 16.3, 16.4, 16.5_
  
  - [x] 11.3 Configurar logging estruturado
    - Configurar Logback para formato JSON
    - Adicionar campos: timestamp, level, logger, message, context
    - Configurar MDC para incluir correlationId em todos os logs
    - Configurar níveis de log: INFO para operações bem-sucedidas, ERROR para falhas, WARN para situações anômalas
    - _Requisitos: 20.1, 20.2, 20.3, 20.4, 20.5_
  
  - [x] 11.4 Escrever testes de integração para Orquestrador
    - Usar Testcontainers para Oracle, RabbitMQ, SFTP
    - Testar fluxo completo: carregar configurações → listar SFTP → registrar banco → publicar RabbitMQ
    - Testar controle de concorrência com múltiplas execuções
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 5.1_

- [x] 12. Checkpoint - Validar Pod Orquestrador
  - Executar todos os testes unitários, de propriedade e integração do Orquestrador
  - Testar execução local com Docker Compose
  - Verificar logs estruturados e health checks
  - Perguntar ao usuário se há dúvidas ou ajustes necessários


- [x] 13. Implementar Pod Processador - Parte 1: Consumo de mensagens e download
  - [x] 13.1 Criar RabbitMQConsumer
    - Implementar método consumir() com @RabbitListener
    - Implementar deserialização de MensagemProcessamento
    - Implementar validação de mensagem (arquivo existe em file_origin e não foi processado)
    - Se mensagem inválida, descartar e registrar alerta
    - Invocar ProcessadorService.processarArquivo(mensagem)
    - Implementar ACK manual após processamento bem-sucedido
    - Implementar NACK manual em caso de falha para reprocessamento
    - _Requisitos: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  
  - [x] 13.2 Escrever testes unitários para RabbitMQConsumer
    - Testar consumo de mensagem válida
    - Testar validação de mensagem inválida (arquivo não existe)
    - Testar ACK após sucesso
    - Testar NACK após falha
    - _Requisitos: 6.3, 6.4, 6.5, 6.6_
  
  - [x] 13.3 Escrever teste de propriedade para confirmação de mensagens
    - **Propriedade 12: Confirmação de Mensagens**
    - **Valida: Requisitos 6.5, 6.6**
    - Gerar mensagens válidas e inválidas
    - Verificar que mensagens bem-sucedidas recebem ACK
    - Verificar que mensagens com falha recebem NACK
    - _Requisitos: 6.5, 6.6_
  
  - [x] 13.4 Criar ProcessadorService - download streaming
    - Implementar método processarArquivo(mensagem) como orquestrador do fluxo
    - Implementar download streaming: obter credenciais do Vault, conectar SFTP, obter InputStream
    - Manter InputStream aberto apenas durante transferência
    - Implementar tratamento de erro de download (registrar erro, liberar recursos, lançar exceção)
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [x] 13.5 Escrever testes unitários para download streaming
    - Testar obtenção de InputStream do SFTP com mock
    - Testar tratamento de erro de download
    - Testar liberação de recursos em caso de falha
    - _Requisitos: 7.1, 7.2, 7.5_


- [x] 14. Implementar Pod Processador - Parte 2: Identificação e upload
  - [x] 14.1 Integrar identificação de cliente no ProcessadorService
    - Após download, invocar ClienteIdentificationService.identificar(nomeArquivo, idAdquirente)
    - Se cliente não identificado, registrar erro, atualizar status para ERRO, lançar exceção
    - Se cliente identificado, inserir ou atualizar registro em file_origin_client
    - Usar idt_file_origin_client para rastreabilidade subsequente
    - _Requisitos: 8.1, 8.5, 13.1, 13.2, 13.3, 13.4, 13.5_
  
  - [x] 14.2 Integrar identificação de layout no ProcessadorService
    - Após identificação de cliente, invocar LayoutIdentificationService.identificar(nomeArquivo, headerStream, idCliente, idAdquirente)
    - Para regras HEADER, passar InputStream que lê primeiros 7000 bytes
    - Se layout não identificado, registrar erro, atualizar status para ERRO, lançar exceção
    - Se layout identificado, atualizar idt_layout, des_file_type, des_transaction_type em file_origin
    - Atualizar timestamp de última modificação
    - _Requisitos: 9.1, 9.6, 14.1, 14.2, 14.3, 14.4_
  
  - [x] 14.3 Integrar upload streaming no ProcessadorService
    - Após identificação de cliente e layout, determinar destino usando idt_sever_destination
    - Obter credenciais do destino via Vault
    - Se destino é S3, invocar StreamingTransferService.transferirSFTPparaS3()
    - Se destino é SFTP, invocar StreamingTransferService.transferirSFTPparaSFTP()
    - Validar tamanho do arquivo no destino corresponde ao tamanho original
    - Se validação falhar, registrar erro detalhado, manter arquivo na origem, lançar exceção
    - _Requisitos: 10.1, 10.2, 10.3, 10.5, 10.6_
  
  - [x] 14.4 Integrar rastreabilidade no ProcessadorService
    - No início de processarArquivo(), registrar etapa COLETA com status EM_ESPERA
    - Ao iniciar download, atualizar para PROCESSAMENTO e registrar dat_step_start
    - Após identificação de cliente, registrar etapa STAGING
    - Após identificação de layout, registrar etapa ORDINATION
    - Ao iniciar upload, registrar etapa PROCESSING
    - Após upload bem-sucedido, registrar etapa PROCESSED com status CONCLUIDO e dat_step_end
    - Em caso de erro, atualizar status para ERRO com des_message_error e stack trace em jsn_additional_info
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5_
  
  - [x] 14.5 Escrever testes unitários para ProcessadorService
    - Testar fluxo completo com mocks de todos os serviços
    - Testar tratamento de erro em cada etapa (cliente não identificado, layout não identificado, falha de upload)
    - Testar atualização de rastreabilidade em cada etapa
    - _Requisitos: 8.5, 9.6, 10.6, 12.1, 12.2, 12.3, 12.4_


- [x] 15. Implementar tratamento de erros e recuperação
  - [x] 15.1 Criar hierarquia de exceções customizadas
    - Criar ErroRecuperavelException para erros que permitem retry
    - Criar ErroNaoRecuperavelException para erros permanentes
    - Criar exceções específicas: ClienteNaoIdentificadoException, LayoutNaoIdentificadoException, FalhaUploadException
    - _Requisitos: 15.3, 15.4_
  
  - [x] 15.2 Implementar classificação de erros no ProcessadorService
    - Classificar erros recuperáveis: falhas de conexão SFTP, timeouts, falhas de banco transientes, throttling S3
    - Classificar erros não recuperáveis: arquivo não encontrado, cliente não identificado, layout não identificado, credenciais inválidas
    - Para erros recuperáveis, lançar ErroRecuperavelException (NACK para retry)
    - Para erros não recuperáveis, marcar arquivo como ERRO permanente e lançar ErroNaoRecuperavelException (ACK para não reprocessar)
    - _Requisitos: 15.1, 15.2, 15.3, 15.4_
  
  - [x] 15.3 Implementar limite de reprocessamento
    - Adicionar contador de tentativas em jsn_additional_info
    - Verificar contador antes de processar
    - Se contador >= 5, marcar como ERRO permanente e não reprocessar
    - _Requisitos: 15.6_
  
  - [x] 15.4 Escrever testes unitários para tratamento de erros
    - Testar classificação de erros recuperáveis vs não recuperáveis
    - Testar limite de 5 reprocessamentos
    - Testar registro de erro com contexto completo
    - _Requisitos: 15.1, 15.2, 15.3, 15.4, 15.6_
  
  - [x] 15.5 Escrever teste de propriedade para limite de reprocessamento
    - **Propriedade 30: Limite de Reprocessamento**
    - **Valida: Requisitos 15.6**
    - Gerar arquivos com múltiplos erros
    - Verificar que sistema limita tentativas a 5 reprocessamentos
    - _Requisitos: 15.6_
  
  - [x] 15.6 Implementar logging estruturado de erros
    - Para qualquer erro, registrar log JSON com: timestamp, level ERROR, logger, message, correlationId, context (arquivo, adquirente, etapa)
    - Incluir stack trace completo em jsn_additional_info
    - Nunca expor credenciais em logs de erro
    - _Requisitos: 15.1, 15.2, 15.5, 20.1, 20.2, 20.4_


- [x] 16. Implementar aplicação Spring Boot do Processador
  - [x] 16.1 Criar classe principal ProcessadorApplication
    - Criar classe com @SpringBootApplication
    - Habilitar RabbitMQ listener com @EnableRabbit
    - Configurar component scan para todos os serviços
    - _Requisitos: 6.1_
  
  - [x] 16.2 Configurar health checks
    - Adicionar Spring Boot Actuator
    - Expor endpoint /actuator/health
    - Configurar health indicators para banco de dados e RabbitMQ
    - Retornar status UP quando todas as dependências estão disponíveis
    - Retornar status DOWN se alguma dependência crítica estiver indisponível
    - _Requisitos: 16.2, 16.3, 16.4, 16.5_
  
  - [x] 16.3 Configurar logging estruturado
    - Configurar Logback para formato JSON
    - Adicionar campos: timestamp, level, logger, message, context
    - Configurar MDC para incluir correlationId em todos os logs
    - Configurar níveis de log: INFO para operações bem-sucedidas, ERROR para falhas, WARN para situações anômalas
    - _Requisitos: 20.1, 20.2, 20.3, 20.4, 20.5_
  
  - [x] 16.4 Escrever testes de integração para Processador
    - Usar Testcontainers para Oracle, RabbitMQ, LocalStack S3, SFTP
    - Testar fluxo completo: consumir mensagem → download → identificação → upload → rastreabilidade
    - Testar cenários de erro: arquivo não encontrado, cliente não identificado, falha de upload
    - Testar reprocessamento após falha recuperável
    - _Requisitos: 6.1, 7.1, 8.1, 9.1, 10.1, 12.1, 15.3_

- [x] 17. Checkpoint - Validar Pod Processador
  - Executar todos os testes unitários, de propriedade e integração do Processador
  - Testar execução local com Docker Compose
  - Verificar logs estruturados e health checks
  - Testar integração completa: Orquestrador → RabbitMQ → Processador
  - Perguntar ao usuário se há dúvidas ou ajustes necessários


- [x] 18. Implementar testes de propriedade restantes
  - [x] 18.1 Escrever testes de propriedade para validação de configurações
    - **Propriedade 1: Validação de Configurações**
    - **Valida: Requisitos 1.2, 1.3**
    - Gerar configurações válidas e inválidas
    - Verificar que configurações inválidas são registradas como erro e puladas
    - _Requisitos: 1.2, 1.3_
  
  - [x] 18.2 Escrever testes de propriedade para coleta de metadados
    - **Propriedade 4: Coleta de Metadados**
    - **Valida: Requisitos 2.4**
    - Gerar arquivos com metadados variados
    - Verificar que nome, tamanho e timestamp são coletados corretamente
    - _Requisitos: 2.4_
  
  - [x] 18.3 Escrever testes de propriedade para recuperação de falhas SFTP
    - **Propriedade 5: Recuperação de Falhas de Conexão SFTP**
    - **Valida: Requisitos 2.5**
    - Simular falhas de conexão SFTP
    - Verificar que sistema registra erro e continua com próximo servidor
    - _Requisitos: 2.5_
  
  - [x] 18.4 Escrever testes de propriedade para registro de arquivo novo
    - **Propriedade 6: Registro de Arquivo Novo**
    - **Valida: Requisitos 3.1, 3.2, 3.3**
    - Gerar arquivos novos
    - Verificar que registro é inserido com status COLETA e EM_ESPERA
    - _Requisitos: 3.1, 3.2, 3.3_
  
  - [x] 18.5 Escrever testes de propriedade para retry de publicação
    - **Propriedade 9: Retry de Publicação**
    - **Valida: Requisitos 4.5**
    - Simular falhas de publicação RabbitMQ
    - Verificar que sistema tenta reenviar até 3 vezes
    - _Requisitos: 4.5_
  
  - [x] 18.6 Escrever testes de propriedade para controle de concorrência
    - **Propriedade 10: Controle de Concorrência**
    - **Valida: Requisitos 5.3, 5.4, 5.5**
    - Simular múltiplas execuções concorrentes
    - Verificar que status é atualizado corretamente (RUNNING → COMPLETED ou PENDING)
    - _Requisitos: 5.3, 5.4, 5.5_
  
  - [x] 18.7 Escrever testes de propriedade para validação de mensagem
    - **Propriedade 11: Validação de Mensagem Recebida**
    - **Valida: Requisitos 6.3, 6.4**
    - Gerar mensagens válidas e inválidas
    - Verificar que mensagens inválidas são descartadas com alerta
    - _Requisitos: 6.3, 6.4_
  
  - [x] 18.8 Escrever testes de propriedade para download streaming
    - **Propriedade 13: Download com Streaming**
    - **Valida: Requisitos 7.1, 7.2, 7.5**
    - Gerar arquivos de tamanhos variados
    - Verificar que download usa streaming e libera recursos em caso de falha
    - _Requisitos: 7.1, 7.2, 7.5_
  
  - [x] 18.9 Escrever testes de propriedade para tratamento de falha de identificação
    - **Propriedade 15: Tratamento de Falha de Identificação de Cliente**
    - **Valida: Requisitos 8.5**
    - Gerar arquivos onde nenhum cliente é identificado
    - Verificar que sistema registra erro e atualiza status para ERRO
    - _Requisitos: 8.5_
  
  - [x] 18.10 Escrever testes de propriedade para tratamento de falha de layout
    - **Propriedade 18: Tratamento de Falha de Identificação de Layout**
    - **Valida: Requisitos 9.6**
    - Gerar arquivos onde nenhum layout é identificado
    - Verificar que sistema registra erro e atualiza status para ERRO
    - _Requisitos: 9.6_


  - [x] 18.11 Escrever testes de propriedade para determinação de destino
    - **Propriedade 19: Determinação de Destino**
    - **Valida: Requisitos 10.1**
    - Gerar mapeamentos origem-destino variados
    - Verificar que destino é determinado corretamente usando idt_sever_destination
    - _Requisitos: 10.1_
  
  - [x] 18.12 Escrever testes de propriedade para upload S3
    - **Propriedade 20: Upload para S3 com Multipart**
    - **Valida: Requisitos 10.2**
    - Gerar arquivos de tamanhos variados
    - Verificar que upload usa multipart com InputStream encadeado
    - _Requisitos: 10.2_
  
  - [x] 18.13 Escrever testes de propriedade para upload SFTP
    - **Propriedade 21: Upload para SFTP com Streaming**
    - **Valida: Requisitos 10.3**
    - Gerar arquivos de tamanhos variados
    - Verificar que upload usa OutputStream encadeado diretamente do InputStream
    - _Requisitos: 10.3_
  
  - [x] 18.14 Escrever testes de propriedade para segurança de credenciais
    - **Propriedade 23: Segurança de Credenciais**
    - **Valida: Requisitos 11.5**
    - Simular erros ao recuperar credenciais
    - Verificar que erro é registrado sem expor informações sensíveis
    - _Requisitos: 11.5_
  
  - [x] 18.15 Escrever testes de propriedade para armazenamento de informações adicionais
    - **Propriedade 25: Armazenamento de Informações Adicionais**
    - **Valida: Requisitos 12.5**
    - Gerar etapas com informações adicionais variadas
    - Verificar que dados estruturados são armazenados em jsn_additional_info
    - _Requisitos: 12.5_
  
  - [x] 18.16 Escrever testes de propriedade para associação arquivo-cliente
    - **Propriedade 26: Associação Arquivo-Cliente**
    - **Valida: Requisitos 13.1, 13.2, 13.3, 13.4, 13.5**
    - Gerar identificações de cliente bem-sucedidas
    - Verificar que registro é inserido/atualizado em file_origin_client
    - _Requisitos: 13.1, 13.2, 13.3, 13.4, 13.5_
  
  - [x] 18.17 Escrever testes de propriedade para atualização de layout
    - **Propriedade 27: Atualização de Layout em File Origin**
    - **Valida: Requisitos 14.1, 14.2, 14.3, 14.4**
    - Gerar identificações de layout
    - Verificar que idt_layout, des_file_type, des_transaction_type são atualizados
    - _Requisitos: 14.1, 14.2, 14.3, 14.4_
  
  - [x] 18.18 Escrever testes de propriedade para registro de erros
    - **Propriedade 28: Registro Completo de Erros**
    - **Valida: Requisitos 15.1, 15.2, 15.5**
    - Gerar erros variados durante processamento
    - Verificar que log estruturado é registrado com contexto completo
    - _Requisitos: 15.1, 15.2, 15.5_
  
  - [x] 18.19 Escrever testes de propriedade para classificação de erros
    - **Propriedade 29: Classificação de Erros Recuperáveis**
    - **Valida: Requisitos 15.3, 15.4**
    - Gerar erros recuperáveis e não recuperáveis
    - Verificar que erros recuperáveis permitem reprocessamento (NACK)
    - Verificar que erros não recuperáveis marcam arquivo como ERRO permanente
    - _Requisitos: 15.3, 15.4_


  - [x] 18.20 Escrever testes de propriedade para health checks
    - **Propriedade 31: Health Check com Dependências**
    - **Valida: Requisitos 16.3, 16.4, 16.5**
    - Simular disponibilidade e indisponibilidade de dependências
    - Verificar que status UP é retornado quando todas as dependências estão disponíveis
    - Verificar que status DOWN é retornado se alguma dependência crítica estiver indisponível
    - _Requisitos: 16.3, 16.4, 16.5_
  
  - [x] 18.21 Escrever testes de propriedade para validação de configurações obrigatórias
    - **Propriedade 32: Validação de Configurações Obrigatórias**
    - **Valida: Requisitos 19.5**
    - Simular inicialização com configurações faltando
    - Verificar que sistema falha a inicialização se configuração obrigatória está faltando
    - _Requisitos: 19.5_
  
  - [x] 18.22 Escrever testes de propriedade para formato de logs
    - **Propriedade 33: Formato de Logs Estruturados**
    - **Valida: Requisitos 20.1**
    - Gerar logs variados
    - Verificar que formato JSON contém campos: timestamp, level, logger, message, context
    - _Requisitos: 20.1_
  
  - [x] 18.23 Escrever testes de propriedade para correlation ID
    - **Propriedade 34: Correlation ID em Logs**
    - **Valida: Requisitos 20.2**
    - Gerar logs relacionados ao processamento de arquivos
    - Verificar que correlation_id está presente em todos os logs
    - _Requisitos: 20.2_
  
  - [x] 18.24 Escrever testes de propriedade para níveis de log
    - **Propriedade 35: Níveis de Log Apropriados**
    - **Valida: Requisitos 20.3, 20.4, 20.5**
    - Gerar operações bem-sucedidas, falhas e situações anômalas
    - Verificar que nível INFO é usado para sucesso
    - Verificar que nível ERROR é usado para falhas com stack trace
    - Verificar que nível WARN é usado para situações anômalas
    - _Requisitos: 20.3, 20.4, 20.5_

- [x] 19. Checkpoint - Validar testes de propriedade
  - Executar todos os 35 testes de propriedade com jqwik
  - Verificar que todas as propriedades de corretude passam
  - Analisar cobertura de código (mínimo 80%)
  - Perguntar ao usuário se há dúvidas ou ajustes necessários


- [x] 20. Criar manifests Kubernetes
  - [x] 20.1 Criar Deployment para Orquestrador
    - Criar orquestrador-deployment.yaml com configuração de replicas, resources, probes
    - Configurar liveness probe em /actuator/health
    - Configurar readiness probe em /actuator/health
    - Configurar resources (requests e limits de CPU e memória)
    - _Requisitos: 16.1_
  
  - [x] 20.2 Criar Deployment para Processador
    - Criar processador-deployment.yaml com configuração de replicas, resources, probes
    - Configurar liveness probe em /actuator/health
    - Configurar readiness probe em /actuator/health
    - Configurar resources (requests e limits de CPU e memória)
    - Configurar HPA (Horizontal Pod Autoscaler) baseado em CPU e mensagens RabbitMQ
    - _Requisitos: 16.2_
  
  - [x] 20.3 Criar Services
    - Criar orquestrador-service.yaml (ClusterIP)
    - Criar processador-service.yaml (ClusterIP)
    - Expor porta 8080 para health checks
    - _Requisitos: 16.1, 16.2_
  
  - [x] 20.4 Criar ConfigMaps
    - Criar configmap.yaml com configurações não sensíveis: URLs de serviços, timeouts, tamanhos de chunk
    - Separar ConfigMaps por perfil (dev, staging, prod)
    - _Requisitos: 19.4_
  
  - [x] 20.5 Criar Secrets
    - Criar secrets.yaml template para credenciais: banco de dados, RabbitMQ, Vault token
    - Documentar que valores devem ser preenchidos via CI/CD ou manualmente
    - _Requisitos: 19.4_
  
  - [x] 20.6 Criar documentação de deploy
    - Criar README.md com instruções de deploy no Kubernetes
    - Documentar ordem de aplicação dos manifests
    - Documentar como configurar Secrets
    - Documentar como verificar status dos pods
    - _Requisitos: 19.4_


- [x] 21. Criar scripts de dados de teste
  - [x] 21.1 Criar scripts SQL de dados de teste
    - Criar script para inserir servidores SFTP de teste (server, sever_paths, sever_paths_in_out)
    - Criar script para inserir layouts de teste (layout, layout_identification_rule)
    - Criar script para inserir clientes de teste (customer_identification, customer_identification_rule)
    - Criar script para inserir adquirentes de teste
    - Incluir exemplos de regras variadas: COMECA-COM, TERMINA-COM, CONTEM, IGUAL
    - _Requisitos: 8.1, 9.1_
  
  - [x] 21.2 Criar arquivos de teste para SFTP
    - Criar arquivos EDI de exemplo com diferentes formatos (CSV, TXT, JSON, OFX, XML)
    - Criar arquivos que correspondem às regras de identificação de teste
    - Criar arquivos que não correspondem a nenhuma regra (para testar erro)
    - _Requisitos: 8.1, 9.1_
  
  - [x] 21.3 Documentar cenários de teste
    - Documentar cenários de teste end-to-end
    - Documentar dados de entrada e saída esperada
    - Documentar como executar testes manuais localmente
    - _Requisitos: 17.1_

- [x] 22. Implementar testes de integração end-to-end
  - [x] 22.1 Criar teste de integração para fluxo completo
    - Usar Testcontainers para todos os serviços
    - Testar fluxo: Orquestrador coleta → publica RabbitMQ → Processador consome → identifica → upload S3/SFTP → rastreabilidade
    - Verificar que arquivo é processado corretamente do início ao fim
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 6.1, 7.1, 8.1, 9.1, 10.1, 12.1_
  
  - [x] 22.2 Criar teste de integração para cenários de erro
    - Testar arquivo não encontrado no SFTP
    - Testar cliente não identificado
    - Testar layout não identificado
    - Testar falha de upload
    - Verificar que rastreabilidade registra erros corretamente
    - _Requisitos: 8.5, 9.6, 10.6, 15.1, 15.2_
  
  - [x] 22.3 Criar teste de integração para concorrência
    - Testar múltiplas instâncias do Orquestrador
    - Verificar que controle de concorrência previne processamento duplicado
    - _Requisitos: 5.1, 5.2, 5.3_
  
  - [x] 22.4 Criar teste de integração para reprocessamento
    - Testar reprocessamento após falha recuperável
    - Verificar que arquivo é reprocessado corretamente
    - Verificar que limite de 5 tentativas é respeitado
    - _Requisitos: 15.3, 15.6_
  
  - [x] 22.5 Criar teste de performance para streaming
    - Testar processamento de arquivo grande (1GB+)
    - Verificar que memória não é esgotada
    - Medir tempo de processamento
    - _Requisitos: 7.3, 10.2, 10.3_


- [x] 23. Criar documentação do projeto
  - [x] 23.1 Criar README.md principal
    - Documentar visão geral do sistema
    - Documentar arquitetura (2 pods, RabbitMQ, Oracle, S3/SFTP)
    - Documentar pré-requisitos (Java 17, Maven, Docker)
    - Documentar como executar localmente com Docker Compose
    - Documentar como executar testes
    - _Requisitos: 17.1_
  
  - [x] 23.2 Criar documentação de configuração
    - Documentar todas as propriedades de configuração (application.yml)
    - Documentar perfis Spring Boot (local, dev, staging, prod)
    - Documentar variáveis de ambiente necessárias
    - Documentar integração com Vault
    - _Requisitos: 19.1, 19.2, 19.3, 19.4_
  
  - [x] 23.3 Criar documentação de operação
    - Documentar como monitorar o sistema (health checks, logs)
    - Documentar como investigar erros (rastreabilidade, logs estruturados)
    - Documentar como reprocessar arquivos com erro
    - Documentar troubleshooting comum
    - _Requisitos: 15.1, 16.1, 16.2, 20.1_
  
  - [x] 23.4 Criar documentação de desenvolvimento
    - Documentar estrutura do projeto (módulos Maven)
    - Documentar padrões de código e boas práticas
    - Documentar como adicionar novos critérios de identificação
    - Documentar como adicionar novos tipos de destino
    - _Requisitos: 1.1_

- [~] 24. Checkpoint final - Validação completa do sistema
  - Executar todos os testes (unitários, propriedade, integração)
  - Verificar cobertura de código (mínimo 80%)
  - Executar sistema localmente com Docker Compose
  - Testar fluxo completo end-to-end manualmente
  - Revisar documentação
  - Perguntar ao usuário se há dúvidas ou ajustes necessários


## Notas

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada tarefa referencia requisitos específicos para rastreabilidade
- Checkpoints garantem validação incremental do sistema
- Testes de propriedade validam as 35 propriedades de corretude do documento de design
- Testes unitários validam exemplos específicos e casos extremos
- Testes de integração validam fluxos completos com Testcontainers
- A implementação usa Java 17 com Spring Boot 3 conforme especificado no design
- O sistema suporta processamento streaming para arquivos de qualquer tamanho
- Todas as credenciais são gerenciadas via Vault (nunca hardcoded)
- Logs estruturados em JSON facilitam observabilidade
- Health checks permitem monitoramento no Kubernetes
