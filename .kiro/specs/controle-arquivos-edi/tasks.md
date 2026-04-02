# Implementation Plan: Controle de Arquivos EDI

## Overview

Este plano implementa um sistema de transferência automatizada de arquivos EDI para conciliação de cartão de crédito. O sistema utiliza arquitetura baseada em mensageria assíncrona com Producer (coleta SFTP) e Consumer (transferência streaming), priorizando eficiência de memória, rastreabilidade completa e resiliência com retry automático.

## Tasks

- [x] 1. Configurar estrutura do projeto Maven mono repositório
  - Criar pom.xml raiz com módulos commons, producer e consumer
  - Configurar Java 21, Spring Boot 3.4 e dependências compartilhadas
  - Definir estrutura de diretórios para cada módulo
  - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 18.1, 18.2_

- [-] 2. Criar scripts DDL Oracle para modelo de dados
  - [x] 2.1 Criar script DDL para tabela server
    - Definir sequence server_seq
    - Criar tabela com colunas, tipos, constraints e checks
    - Adicionar índice único server_idx_01 em (cod_server, flg_active)
    - Criar trigger server_bir para auto-increment e dat_creation
    - Adicionar comments em tabela e colunas
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 16.1_
  
  - [ ] 2.2 Escrever property test para validação de tipos de servidor
    - **Property 1: Server type validation**
    - **Valida: Requirements 1.2**
  
  - [ ] 2.3 Escrever property test para validação de origem de servidor
    - **Property 2: Server origin validation**
    - **Valida: Requirements 1.3**
  
  - [ ] 2.4 Escrever property test para constraint de unicidade de servidor
    - **Property 3: Server uniqueness constraint**
    - **Valida: Requirements 1.4**
  
  - [x] 2.5 Criar script DDL para tabela sever_paths
    - Definir sequence sever_paths_seq
    - Criar tabela com colunas, tipos, constraints e checks
    - Adicionar foreign key fk_sever_paths_server para server
    - Criar trigger sever_paths_bir para auto-increment e dat_creation
    - Adicionar comments em tabela e colunas
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 16.2_
  
  - [ ] 2.6 Escrever property test para validação de tipo de path
    - **Property 6: Path type validation**
    - **Valida: Requirements 2.2**
  
  - [x] 2.7 Criar script DDL para tabela sever_paths_in_out
    - Definir sequence sever_paths_in_out_seq
    - Criar tabela com colunas, tipos, constraints e checks
    - Adicionar índice único sever_paths_in_out_idx_01
    - Adicionar foreign keys fk_spio_origin e fk_spio_destination
    - Criar trigger sever_paths_in_out_bir para auto-increment
    - Adicionar comments em tabela e colunas
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 16.3_
  
  - [ ] 2.8 Escrever property test para validação de tipo de link
    - **Property 8: Link type validation**
    - **Valida: Requirements 3.2**
  
  - [x] 2.9 Criar script DDL para tabela file_origin
    - Definir sequence file_origin_seq
    - Criar tabela com todas as colunas, tipos, constraints e checks
    - Adicionar índice único file_origin_idx_01 em (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)
    - Adicionar foreign key fk_file_origin_spio
    - Criar trigger file_origin_bir para auto-increment
    - Adicionar comments em tabela e colunas
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 16.4_
  
  - [ ] 2.10 Escrever property test para validação de step de arquivo
    - **Property 11: File step validation**
    - **Valida: Requirements 4.2**
  
  - [ ] 2.11 Escrever property test para validação de status de arquivo
    - **Property 12: File status validation**
    - **Valida: Requirements 4.3**
  
  - [ ] 2.12 Escrever property test para validação de tipo de arquivo
    - **Property 13: File type validation**
    - **Valida: Requirements 4.4**
  
  - [x] 2.13 Criar script de inicialização com dados de teste
    - Inserir configurações de servidor (SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION)
    - Inserir paths de origem e destino
    - Inserir mapeamentos sever_paths_in_out (PRINCIPAL)
    - _Requirements: 16.5_

- [x] 3. Implementar módulo commons com entidades e DTOs compartilhados
  - [x] 3.1 Criar enums compartilhados
    - Implementar ServerType (S3, Blob-Storage, Object Storage, SFTP, NFS)
    - Implementar ServerOrigin (INTERNO, EXTERNO)
    - Implementar PathType (ORIGIN, DESTINATION)
    - Implementar LinkType (PRINCIPAL, SECUNDARIO)
    - Implementar Step (COLETA, DELETE, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED)
    - Implementar Status (EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO)
    - Implementar FileType (csv, json, txt, xml)
    - Implementar TransactionType (COMPLETO, CAPTURA, FINANCEIRO)
    - _Requirements: 1.2, 1.3, 2.2, 3.2, 4.2, 4.3, 4.4, 4.5_
  
  - [x] 3.2 Criar entidades JPA
    - Implementar Server.java com anotações JPA e mapeamento para tabela server
    - Implementar ServerPath.java com anotações JPA e mapeamento para tabela sever_paths
    - Implementar ServerPathInOut.java com anotações JPA e mapeamento para tabela sever_paths_in_out
    - Implementar FileOrigin.java com anotações JPA e mapeamento para tabela file_origin
    - Configurar relacionamentos @ManyToOne e @OneToMany
    - _Requirements: 1.1, 2.1, 3.1, 4.1_
  
  - [x] 3.3 Criar DTO FileTransferMessage
    - Implementar FileTransferMessage.java com campos idtFileOrigin, filename, idtServerPathOrigin, idtServerPathDestination
    - Adicionar anotações Serializable para RabbitMQ
    - Adicionar construtores, getters e setters
    - _Requirements: 9.2, 9.3, 9.4, 9.5_
  
  - [x] 3.4 Criar configuração RabbitMQ compartilhada
    - Implementar RabbitMQConfig.java com declaração de exchange, queue e binding
    - Configurar Quorum Queue com durabilidade
    - Definir MessageConverter para JSON
    - _Requirements: 9.1, 15.2_

- [x] 4. Checkpoint - Verificar estrutura base
  - Garantir que todos os testes passem, perguntar ao usuário se há dúvidas.

- [-] 5. Implementar módulo Producer - Serviços de configuração e SFTP
  - [x] 5.1 Criar ProducerApplication.java
    - Implementar classe principal com @SpringBootApplication
    - Habilitar @EnableScheduling e @EnableRetry
    - _Requirements: 18.1_
  
  - [x] 5.2 Criar repositories JPA para Producer
    - Implementar ServerRepository extends JpaRepository
    - Implementar ServerPathRepository extends JpaRepository
    - Implementar ServerPathInOutRepository extends JpaRepository
    - Implementar FileOriginRepository extends JpaRepository
    - Adicionar query methods customizados (findByFlgActive, findFailedPublications)
    - _Requirements: 5.2_
  
  - [x] 5.3 Implementar ConfigurationService
    - Criar ConfigurationService.java com método loadActiveConfigurations
    - Implementar lógica de join entre server, sever_paths e sever_paths_in_out
    - Filtrar apenas registros com flg_active=1 e des_path_type=ORIGIN
    - Retornar lista de ServerConfiguration DTOs
    - _Requirements: 5.2, 5.3, 5.4_
  
  - [ ]* 5.4 Escrever property test para filtragem de configurações ativas
    - **Property 18: Active configuration filtering**
    - **Valida: Requirements 5.3**
  
  - [x] 5.5 Implementar VaultConfig para obtenção de credenciais
    - Criar VaultConfig.java com lógica de fallback para variáveis de ambiente
    - Implementar método getCredentials(codVault, vaultSecret)
    - Parsear JSON de variável de ambiente com campos user e password
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  
  - [ ]* 5.6 Escrever property test para parsing de credenciais JSON
    - **Property 21: Environment credentials JSON parsing**
    - **Valida: Requirements 6.4, 6.5**
  
  - [x] 5.7 Implementar SftpService
    - Criar SftpService.java com métodos listFiles e getCredentials
    - Usar Spring Integration SFTP para conexão
    - Criar SftpConfig com SessionFactory e CachingSessionFactory para pool de conexões
    - Implementar listagem de arquivos em diretório configurado
    - Capturar des_file_name, num_file_size e dat_timestamp_file
    - Validar des_file_type contra tipos permitidos (csv, json, txt, xml)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [ ] 5.8 Escrever property test para validação de tipo de arquivo durante coleta
    - **Property 23: File type validation during collection**
    - **Valida: Requirements 7.4, 7.5**
  
  - [x] 5.9 Escrever unit tests para SftpService
    - Testar listagem de arquivos com tipos válidos
    - Testar rejeição de arquivos com tipos inválidos
    - Testar tratamento de erros de conexão SFTP
    - _Requirements: 19.1_

- [-] 6. Implementar módulo Producer - Registro e publicação
  - [x] 6.1 Implementar FileRegistrationService
    - Criar FileRegistrationService.java com métodos fileExists e registerFile
    - Implementar verificação de duplicatas usando file_origin_idx_01
    - Implementar registerFile com inserção de FileOrigin (des_step=COLETA, des_status=EM_ESPERA, num_retry=0, max_retry=5)
    - Adicionar anotação @Retryable com maxAttempts=5, backoff exponencial (1s, 2s, 4s, 8s, 16s)
    - Definir idt_acquirer=1 e idt_layout=1 (MVP)
    - Obter idt_sever_paths_in_out com des_link_type=PRINCIPAL
    - _Requirements: 7.6, 7.7, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10_
  
  - [x] 6.2 Escrever property test para detecção de duplicatas
    - **Property 24: Duplicate file detection**
    - **Valida: Requirements 7.6, 7.7**
  
  - [x] 6.3 Escrever property test para estado inicial de registro
    - **Property 25: Initial file registration state**
    - **Valida: Requirements 8.2, 8.3**
  
  - [x] 6.4 Escrever property test para configuração inicial de retry
    - **Property 26: Initial retry configuration**
    - **Valida: Requirements 8.6, 8.7**
  
  - [x] 6.5 Escrever unit tests para FileRegistrationService
    - Testar criação de registro com valores iniciais corretos
    - Testar detecção de duplicatas (mesmo filename, acquirer, timestamp)
    - Testar edge case: mesmo filename, timestamp diferente
    - Testar edge case: mesmo filename, acquirer diferente
    - _Requirements: 19.2_
  
  - [x] 6.6 Implementar MessagePublisherService
    - Criar MessagePublisherService.java com método publishFileTransferMessage
    - Usar RabbitTemplate para publicação
    - Adicionar anotação @Retryable com maxAttempts=5, backoff exponencial (1s, 2s, 4s, 8s, 16s)
    - Implementar método @Recover para atualizar file_origin com status ERRO após 5 tentativas
    - No @Recover: definir des_status=ERRO, des_message_error, num_retry=1
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 10.2, 10.3, 10.4_
  
  - [ ] 6.7 Escrever property test para estrutura de mensagem RabbitMQ
    - **Property 29: RabbitMQ message structure completeness**
    - **Valida: Requirements 9.2, 9.3, 9.4, 9.5**
  
  - [ ] 6.8 Escrever property test para uma mensagem por arquivo
    - **Property 30: One message per file**
    - **Valida: Requirements 9.6**
  
  - [x] 6.9 Escrever unit tests para MessagePublisherService
    - Testar publicação de mensagem com todos os campos
    - Testar serialização/deserialização round-trip
    - Testar comportamento de retry com falhas transientes
    - Testar método @Recover após 5 falhas
    - _Requirements: 19.3_

- [x] 7. Implementar módulo Producer - Scheduler
  - [x] 7.1 Implementar FileCollectionScheduler
    - Criar FileCollectionScheduler.java com @Scheduled(fixedDelay = 120000)
    - Implementar método collectFiles com lógica de retry de publicações falhas
    - Implementar retryFailedPublications para arquivos com des_step=COLETA, des_status=ERRO, num_retry < max_retry
    - Carregar configurações ativas via ConfigurationService
    - Para cada configuração: listar arquivos SFTP, validar tipo, verificar duplicatas
    - Registrar arquivo via FileRegistrationService (com @Retryable)
    - Publicar mensagem via MessagePublisherService (com @Retryable)
    - Implementar tratamento de erros: log e continuar com próximo arquivo
    - _Requirements: 5.1, 5.2, 10.1, 10.7_
  
  - [x] 7.2 Escrever property test para isolamento de erros
    - **Property 35: Error isolation**
    - **Valida: Requirements 10.7**
  
  - [x] 7.3 Escrever unit tests para FileCollectionScheduler
    - Testar ciclo completo de coleta com múltiplos arquivos
    - Testar retry de publicações falhas
    - Testar continuação após erro em um arquivo
    - Testar comportamento quando num_retry < max_retry
    - _Requirements: 19.3_
  
  - [x] 7.4 Criar application.yml para Producer
    - Configurar datasource Oracle com variáveis de ambiente
    - Configurar RabbitMQ com variáveis de ambiente
    - Configurar scheduler com fixedDelay=120000
    - Configurar retry com maxAttempts=5, initialInterval=1000, multiplier=2
    - Configurar logging estruturado
    - Configurar health checks
    - _Requirements: 18.3, 18.5, 18.7_

- [x] 8. Checkpoint - Verificar Producer completo
  - Garantir que todos os testes passem, perguntar ao usuário se há dúvidas.

- [x] 9. Implementar módulo Consumer - Serviços de download e upload
  - [x] 9.1 Criar ConsumerApplication.java
    - Implementar classe principal com @SpringBootApplication
    - Habilitar @EnableRabbit
    - _Requirements: 18.2_
  
  - [x] 9.2 Criar repositories JPA para Consumer
    - Implementar FileOriginRepository extends JpaRepository
    - Adicionar query methods customizados para atualização de status
    - _Requirements: 11.6, 14.1, 14.4_
  
  - [x] 9.3 Implementar StatusUpdateService
    - Criar StatusUpdateService.java com métodos updateStatus, updateStatusWithError, incrementRetry
    - Implementar lógica de atualização de file_origin
    - Implementar updateStatus: atualizar des_status, dat_update, nam_change_agent
    - Implementar updateStatusWithError: atualizar des_status, des_message_error, dat_update
    - Implementar incrementRetry: incrementar num_retry
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_
  
  - [x] 9.4 Escrever property test para atualização de status de sucesso
    - **Property 44: Success status update**
    - **Valida: Requirements 14.1, 14.2, 14.3**
  
  - [x] 9.5 Escrever unit tests para StatusUpdateService
    - Testar atualização de status para CONCLUIDO
    - Testar atualização de status para ERRO com mensagem
    - Testar incremento de num_retry
    - Testar população de dat_update e nam_change_agent
    - _Requirements: 19.5_
  
  - [x] 9.6 Implementar StreamingService
    - Criar StreamingService.java com método transferFile
    - Implementar com buffer de 8KB
    - Implementar transferência via loop read/write sem carregar arquivo completo em memória
    - Garantir fechamento de streams com try-with-resources
    - _Requirements: 13.5, 13.6_
  
  - [x] 9.7 Escrever property test para fechamento de streams
    - **Property 42: Stream closure after transfer**
    - **Valida: Requirements 13.6**
  
  - [x] 9.8 Escrever unit tests para StreamingService
    - Testar transferência de arquivo pequeno (< 1KB)
    - Testar transferência de arquivo médio (1MB)
    - Testar transferência de arquivo grande (100MB) sem OutOfMemoryError
    - Testar fechamento de streams em caso de sucesso
    - Testar fechamento de streams em caso de exceção
    - _Requirements: 19.4_
  
  - [x] 9.9 Implementar FileDownloadService
    - Criar FileDownloadService.java com método openInputStream
    - Usar Spring Integration SFTP
    - Criar SftpConfig no Consumer (similar ao Producer) com SessionFactory
    - Obter configuração de server e sever_paths usando idt_sever_path_origin
    - Obter credenciais via VaultConfig
    - Abrir InputStream para arquivo sem carregar conteúdo completo
    - _Requirements: 12.1, 12.2, 12.3, 12.4_
  
  - [x] 9.10 Escrever property test para lookup de configuração de download
    - **Property 38: Configuration lookup for download**
    - **Valida: Requirements 12.1**
  
  - [x] 9.11 Escrever unit tests para FileDownloadService
    - Testar abertura de InputStream com configuração válida
    - Testar exceção com idt_sever_path_origin inválido
    - Testar obtenção de credenciais corretas
    - Testar conexão SFTP com host/port/path corretos
    - _Requirements: 19.4_
  
  - [x] 9.12 Implementar FileUploadService
    - Criar FileUploadService.java com métodos uploadToS3 e uploadToSftp
    - Usar AWS SDK para S3 e Spring Integration SFTP
    - Implementar uploadToS3 usando PutObjectRequest com RequestBody.fromInputStream
    - Implementar uploadToSftp usando Session.write com InputStream
    - Obter configuração de destino usando idt_sever_path_destination
    - _Requirements: 13.1, 13.2, 13.3, 13.4_
  
  - [x] 9.13 Escrever property test para lookup de configuração de upload
    - **Property 41: Configuration lookup for upload**
    - **Valida: Requirements 13.1**
  
  - [x] 9.14 Escrever unit tests para FileUploadService
    - Testar upload S3 com bucket e key corretos
    - Testar upload SFTP com host/port/path corretos
    - Testar configuração de multipart upload para S3
    - Testar gerenciamento de OutputStream
    - _Requirements: 19.4_

- [x] 10. Implementar módulo Consumer - Listener RabbitMQ
  - [x] 10.1 Implementar FileTransferListener
    - Criar FileTransferListener.java com @RabbitListener
    - Implementar handleFileTransfer para consumir mensagens da fila
    - Extrair campos da mensagem: idt_file_origin, filename, idt_sever_path_origin, idt_sever_path_destination
    - Atualizar status para PROCESSAMENTO via StatusUpdateService
    - Abrir InputStream via FileDownloadService
    - Obter configuração de destino
    - Fazer upload para S3 ou SFTP via FileUploadService
    - Atualizar status para CONCLUIDO em caso de sucesso
    - Implementar handleError: atualizar status para ERRO, incrementar num_retry
    - Implementar lógica de NACK se num_retry < max_retry
    - Implementar lógica de ACK se num_retry >= max_retry
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 14.7, 14.8_
  
  - [x] 10.2 Escrever property test para extração de campos de mensagem
    - **Property 36: Message field extraction**
    - **Valida: Requirements 11.2, 11.3, 11.4, 11.5**
  
  - [x] 10.3 Escrever property test para atualização de status ao consumir
    - **Property 37: Processing status update on consumption**
    - **Valida: Requirements 11.6**
  
  - [x] 10.4 Escrever property test para comportamento de retry RabbitMQ
    - **Property 45: RabbitMQ retry behavior**
    - **Valida: Requirements 14.7**
  
  - [x] 10.5 Escrever property test para acknowledgment no max retry
    - **Property 46: RabbitMQ acknowledgment at max retry**
    - **Valida: Requirements 14.8**
  
  - [x] 10.6 Escrever unit tests para FileTransferListener
    - Testar fluxo completo com mensagem válida
    - Testar tratamento de mensagem com estrutura inválida
    - Testar atualização de banco após exceção
    - Testar lógica de acknowledgment RabbitMQ (NACK/ACK)
    - _Requirements: 19.6_
  
  - [x] 10.7 Criar application.yml para Consumer
    - Configurar datasource Oracle com variáveis de ambiente
    - Configurar RabbitMQ com variáveis de ambiente
    - Configurar listener com retry (3 tentativas, backoff exponencial)
    - Configurar AWS S3 com region e endpoint (LocalStack)
    - Configurar SFTP com timeouts
    - Configurar logging estruturado
    - Configurar health checks
    - _Requirements: 18.4, 18.6, 18.8_

- [x] 11. Checkpoint - Verificar Consumer completo
  - Garantir que todos os testes passem, perguntar ao usuário se há dúvidas.

- [x] 12. Configurar infraestrutura Docker Compose
  - [x] 12.1 Criar docker-compose.yml
    - Configurar serviço oracle com gvenzl/oracle-xe:21-slim
    - Configurar serviço rabbitmq com rabbitmq:3.12-management
    - Configurar serviço localstack com localstack/localstack
    - Configurar serviço sftp-origin com atmoz/sftp
    - Configurar serviço sftp-destination com atmoz/sftp
    - Configurar serviço sftp-client com atmoz/sftp
    - Configurar serviço producer com build context
    - Configurar serviço consumer com build context
    - Configurar rede bridge edi-network
    - Configurar volumes para persistência
    - Configurar health checks para oracle, rabbitmq e localstack
    - Configurar depends_on com conditions
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7, 15.8, 15.9_
  
  - [x] 12.2 Criar Dockerfile para Producer
    - Usar base image eclipse-temurin:21-jre-alpine
    - Copiar JAR do target
    - Expor porta 8080
    - Definir ENTRYPOINT
    - _Requirements: 15.6_
  
  - [x] 12.3 Criar Dockerfile para Consumer
    - Usar base image eclipse-temurin:21-jre-alpine
    - Copiar JAR do target
    - Expor porta 8081
    - Definir ENTRYPOINT
    - _Requirements: 15.7_
  
  - [x] 12.4 Criar script de inicialização LocalStack
    - Criar scripts/localstack/init-s3.sh
    - Criar bucket edi-files com awslocal
    - Habilitar versionamento no bucket
    - _Requirements: 15.3_
  
  - [x] 12.5 Criar script de inicialização Oracle
    - Copiar scripts DDL para /docker-entrypoint-initdb.d
    - Garantir execução automática na inicialização do container
    - _Requirements: 15.1, 16.6_

- [x] 13. Implementar teste E2E completo
  - [x] 13.1 Criar estrutura de teste E2E
    - Configurar TestContainers para todos os serviços
    - Criar classe E2ETest com setup e teardown
    - Configurar cliente SFTP para upload de arquivos de teste
    - Configurar cliente S3 para verificação de arquivos
    - _Requirements: 20.1_
  
  - [x] 13.2 Implementar cenário E2E: SFTP para S3
    - Adicionar arquivo de teste na pasta SFTP monitorada
    - Aguardar ciclo do scheduler (máximo 2 minutos)
    - Validar registro em file_origin com des_step=COLETA, des_status=EM_ESPERA
    - Validar mensagem publicada no RabbitMQ
    - Validar atualização de status para PROCESSAMENTO
    - Validar download via streaming do SFTP origem
    - Validar upload via streaming para LocalStack S3
    - Validar atualização de status para CONCLUIDO
    - Validar existência do arquivo no destino
    - Validar integridade: comparar num_file_size
    - Validar integridade: comparar conteúdo (SHA-256)
    - _Requirements: 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 20.10, 20.11, 20.12, 20.13, 20.14_
  
  - [x] 13.3 Implementar cenário E2E: SFTP para SFTP
    - Adicionar arquivo de teste na pasta SFTP monitorada
    - Aguardar ciclo do scheduler (máximo 2 minutos)
    - Validar registro em file_origin
    - Validar mensagem publicada no RabbitMQ
    - Validar atualização de status para PROCESSAMENTO
    - Validar download via streaming do SFTP origem
    - Validar upload via streaming para SFTP destino
    - Validar atualização de status para CONCLUIDO
    - Validar existência do arquivo no SFTP destino
    - Validar integridade: comparar num_file_size e conteúdo
    - _Requirements: 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.9, 20.10, 20.11, 20.12, 20.13, 20.15_

- [x] 14. Checkpoint final - Verificar sistema completo
  - Garantir que todos os testes passem, perguntar ao usuário se há dúvidas.

## Notes

- Tasks marcadas com `*` são opcionais e podem ser puladas para MVP mais rápido
- Cada task referencia requirements específicos para rastreabilidade
- Checkpoints garantem validação incremental
- Property tests validam propriedades universais de corretude
- Unit tests validam exemplos específicos e casos extremos
- Teste E2E valida fluxo completo ponta a ponta
