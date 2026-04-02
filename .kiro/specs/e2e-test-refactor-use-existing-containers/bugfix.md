# Bugfix Requirements Document

## Introduction

O teste E2E `FileTransferE2ETest` atualmente cria novos containers usando TestContainers através da anotação `@Testcontainers` e da classe base `E2ETestBase` que implementa `setupInfrastructure()`. Este comportamento está incorreto porque duplica a infraestrutura que já está disponível via `make up` (docker-compose), causando desperdício de recursos, lentidão na execução dos testes e potenciais conflitos de portas.

O teste deve ser refatorado para utilizar os containers existentes do docker-compose (Oracle, RabbitMQ, LocalStack S3, SFTP Origin, SFTP Destination) que já estão configurados e saudáveis após a execução de `make up`.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN o teste E2E `FileTransferE2ETest` é executado THEN o sistema cria novos containers TestContainers (Oracle, RabbitMQ, LocalStack, SFTP Origin, SFTP Destination) através do método `E2ETestBase.setupInfrastructure()`

1.2 WHEN o teste E2E é executado com `make e2e` após `make up` THEN o sistema ignora os containers docker-compose já em execução e cria containers duplicados

1.3 WHEN o teste E2E inicializa a infraestrutura THEN o sistema executa DDL scripts e insere dados de teste em um banco de dados isolado ao invés de usar o banco já inicializado pelo docker-compose

1.4 WHEN o teste E2E tenta conectar aos serviços THEN o sistema usa portas mapeadas dinamicamente pelos TestContainers ao invés das portas fixas do docker-compose (1521, 5672, 4566, 2222, 2223)

1.5 WHEN o teste E2E finaliza THEN o sistema destrói os containers TestContainers através do método `teardownInfrastructure()`, perdendo logs e dificultando debugging

### Expected Behavior (Correct)

2.1 WHEN o teste E2E `FileTransferE2ETest` é executado após `make up` THEN o sistema SHALL conectar aos containers docker-compose existentes usando configurações de variáveis de ambiente

2.2 WHEN o teste E2E precisa conectar ao Oracle THEN o sistema SHALL ler as variáveis de ambiente DB_URL, DB_USERNAME, DB_PASSWORD para obter jdbc:oracle:thin:@localhost:1521/XEPDB1, "edi_user", "edi_pass"

2.3 WHEN o teste E2E precisa conectar ao RabbitMQ THEN o sistema SHALL ler as variáveis de ambiente RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USERNAME, RABBITMQ_PASSWORD para obter "localhost", 5672, "admin", "admin"

2.4 WHEN o teste E2E precisa conectar ao servidor com cod_server="SFTP_CIELO_ORIGIN" THEN o sistema SHALL ler a variável de ambiente SFTP_CIELO_ORIGIN e parsear o JSON para obter {"host":"localhost", "port":"2222", "user":"cielo", "password":"admin-1-2-3"}

2.5 WHEN o teste E2E precisa conectar ao servidor com cod_server="S3_DESTINATION" THEN o sistema SHALL ler a variável de ambiente S3_DESTINATION e parsear o JSON para obter {"endpoint":"http://localhost:4566", "region":"us-east-1", "accessKey":"test", "secretKey":"test"}

2.6 WHEN o teste E2E precisa conectar ao servidor com cod_server="SFTP_DESTINATION" THEN o sistema SHALL ler a variável de ambiente SFTP_DESTINATION e parsear o JSON para obter {"host":"localhost", "port":"2223", "user":"internal", "password":"internal-pass"}

2.7 WHEN o teste E2E inicializa THEN o sistema SHALL assumir que o banco de dados já foi inicializado pelo `make init-db` e SHALL usar os dados de teste existentes (servers com cod_server: SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION)

2.8 WHEN o teste E2E cria arquivos no SFTP Origin THEN o sistema SHALL usar as configurações obtidas da variável de ambiente SFTP_CIELO_ORIGIN

2.9 WHEN o teste E2E valida arquivos no SFTP Destination THEN o sistema SHALL usar as configurações obtidas da variável de ambiente SFTP_DESTINATION

2.10 WHEN o teste E2E valida arquivos no S3 THEN o sistema SHALL usar as configurações obtidas da variável de ambiente S3_DESTINATION

2.11 WHEN o teste E2E finaliza THEN o sistema SHALL manter os containers docker-compose em execução para permitir debugging e inspeção de logs

### Unchanged Behavior (Regression Prevention)

3.1 WHEN o teste E2E executa o cenário SFTP-to-S3 THEN o sistema SHALL CONTINUE TO validar o fluxo completo: upload para SFTP origin, detecção pelo Producer, criação do registro file_origin, publicação no RabbitMQ, consumo pelo Consumer, transferência para S3, e validação de integridade (SHA-256)

3.2 WHEN o teste E2E executa o cenário SFTP-to-SFTP THEN o sistema SHALL CONTINUE TO validar o fluxo completo: upload para SFTP origin, detecção pelo Producer, criação do registro file_origin, publicação no RabbitMQ, consumo pelo Consumer, transferência para SFTP destination, e validação de integridade (SHA-256)

3.3 WHEN o teste E2E valida o registro file_origin THEN o sistema SHALL CONTINUE TO verificar os campos: des_step (COLETA), des_status (EM_ESPERA → PROCESSAMENTO → CONCLUIDO), num_file_size, idt_acquirer, idt_layout, flg_active, num_retry, max_retry

3.4 WHEN o teste E2E valida campos de auditoria THEN o sistema SHALL CONTINUE TO verificar que dat_update e nam_change_agent foram atualizados após o processamento

3.5 WHEN o teste E2E valida integridade de arquivos THEN o sistema SHALL CONTINUE TO comparar tamanho (bytes) e hash SHA-256 entre o arquivo original e o arquivo transferido

3.6 WHEN o teste E2E usa métodos auxiliares (uploadToSftpOrigin, downloadFromSftpDestination, downloadFromS3, fileExistsInS3, fileExistsInSftpDestination, calculateSHA256) THEN o sistema SHALL CONTINUE TO fornecer essas funcionalidades com a mesma interface

3.7 WHEN o teste E2E aguarda detecção de arquivo pelo Producer THEN o sistema SHALL CONTINUE TO usar timeout de 150 segundos (2.5 minutos) para permitir o ciclo do scheduler

3.8 WHEN o teste E2E aguarda processamento pelo Consumer THEN o sistema SHALL CONTINUE TO usar timeout de 120 segundos (2 minutos) para permitir a conclusão da transferência

3.9 WHEN o teste E2E gera conteúdo de teste THEN o sistema SHALL CONTINUE TO gerar arquivos com tamanhos realistas (1MB para S3, 500KB para SFTP) com conteúdo aleatório

3.10 WHEN o teste E2E executa com timeout global THEN o sistema SHALL CONTINUE TO usar @Timeout de 5 minutos para prevenir testes travados
