## CONTEXTO DO PROJETO

Você é um arquiteto de software sênior especialista em Java. Vamos construir juntos o sistema "Controle de Arquivos" — responsável pela entrada de dados para conciliação de cartão.

O sistema coleta arquivos EDI de adquirentes via SFTP e APIs, identifica que existe um arquivo a ser coletado e os encaminha para um destino (S3 ou SFTP interno), mantendo rastreabilidade completa.

---

## STACK E INFRAESTRUTURA

- 1 Container Job Producer (Java 21 com Spring Boot 3.4)
- 2 Container Job Consumer (Java 21 com Spring Boot 3.4)
- RabbitMQ para comunicação entre os containers onde vamos utilizar filas do tipo Quorum.
- Oracle Database 
- AWS S3 
- SFTP 
- Mono repositório com estrutura commons para compartilhamento de objetos entre os projetos.

- Docker compose para subir e testar em ambiente local.
	-- Oracle XE ou versão lite no docker compose
	-- localstack para simular o AWS S3 com comandos de CLI inclusive.
	-- SFTP versão lite.
	-- cliente de SFTP para possibilitar upload de arquivos manualmente para teste (versão lite)
	-- RabbitMQ 3.12+ como broquer oara comunicação dos containers em ambiente local.
	-- configurar rede em modo bridge para comunicação integrada da infraestrutura com os pods.
	-- 


---

## ARQUITETURA: 2 PODS PRINCIPAIS

### 1 Container Job Producer
Responsabilidades:
- Conectar ao banco e carregar configurações de servidores SFTP (tabelas: server, sever_paths, sever_paths_in_out)
- Se conectar ao SFTP com usuário e senha configurados na busca do banco de dados.
- Listar arquivos nas pastas SFTP configuradas
- Registrar cada arquivo encontrado na tabela file_origin (step/status inicial: COLETA / EM_ESPERA)
- Publicar uma mensagem no RabbitMQ com um único arquivo por mensagem

- Em caso de Exceção atualizar a tabela file_origin após mensagem (step/status inicial: COLETA / EM_ESPERA)


### 2 Container Job Consumer
Responsabilidades:
- Consumir mensagens do RabbitMQ
- Baixar o arquivo do SFTP usando mecanismo de streaming para não sobrecarregar a memória do servidor.
- Fazer upload do arquivo (streaming) para o destino: S3 ou SFTP interno (de acordo com o destino indicado na mensagem).
- Atualizar file_origin, com os dados do processamento.

---

## MODELAGEM DO BANCO (Oracle)

Use exatamente os nomes de tabelas e colunas abaixo:

**server** — servidores de origem/destino (S3, SFTP, NFS, Blob-Storage, Object Storage)

**definição dbml para a tabela server: 
  - Enum server_type_enum {
      "S3" [note: "Sistema de object storage AWS"]
      "Blob-Storage" [note: "Sistema de object storage AZURE"]
      "Object Storage" [note: "Sistema de object storage OCI"]
      "SFTP" [note: "Sistema de transferência de arquivos via SFTP"]
      "NFS" [note: "Sistema de arquivos em rede"]
    }
    Enum server_origin_enum {
      "INTERNO"  [note: 'Origem interna, sobre nossa adm']
      "EXTERNO"  [note: 'Origem disponibilizada por terceiro']
    }
    Table server [note: '[NOT_SECURITY_APPLY] - Tabelas com dados relacionados aos servedores(objetos) de arquivos, tanto de origem quanto de destino'] {
      idt_server number(19) [primary key, increment, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno, ex: 1 | 2 | 3']
      cod_server varchar2(100) [not null, note: '[NOT_SECURITY_APPLY] - Código interno do destino do arquvio, ex: S3-PAGSEGURO | S3-CIELO | S3-REDE | SFTP-PAGSEGURO | SFTP-CIELO | SFTP-REDE']
      cod_vault varchar2(100) [not null, note: '[NOT_SECURITY_APPLY] - Código interno do vault onde esta segredo com dados de acesso, ex: concil_control_arquivos']
      des_vault_secret varchar2(255) [not null, note: '[NOT_SECURITY_APPLY] - Estrutura de pasta dentro do vault onde esta o segredo, ex: concil_controle_arquivo/s3_pags' ]
      des_server_type server_type_enum [not null, note: '[NOT_SECURITY_APPLY] - Indica o tipo de server']
      des_server_origin server_origin_enum [not null, note: '[NOT_SECURITY_APPLY] - Indica se a origem é interna ou externa']
      dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da geração do registro']
      dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
      nam_change_agent varchar2(50) [null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração']
      flg_active number(1) [not null, note: '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO']
      Indexes {
        (cod_server, flg_active) [unique, name:"server_idx_01", type: btree, note: '[NOT_SECURITY_APPLY] - Indice unico para código do server pois nao pode repetir']
      }
    }

  *exemplo: idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin (INTERNO|EXTERNO), flg_active (0=inativo, 1=ativo)
	ex {1, 'SFTP_CIELO', 'SFTP_CIELO_VAULT', '/sftp_cielo_vault', 'SFTP', 'INTERNO', 1 }
           {1, 'S3_CIELO', 'S3_INTERNAL_VAULT', NULL, 'S3', 'INTERNO', 1 }



**sever_paths** — diretórios dentro dos servidores
**definição dbml para a tabela sever_paths: 
  Enum path_type_enum {
    "ORIGIN" [note: 'Caminho de origem, onde o arquivo é inicialmente recebido, ex: pasta de entrada do sftp']
    "DESTINATION" [note: 'Caminho de destino, onde o arquivo é armazenado após processamento, ex: bucket do s3']
  }
  Enum acquirer {
    1 [note: 'CIELO']
    2 [note: 'REDE']
    ...
  }
  Table sever_paths [note: '[NOT_SECURITY_APPLY] - Tabelas com dados relacionados aos diretorios de origem e destino para armazenamento dos arquivos'] {
    idt_sever_path number(19) [primary key, increment, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno, ex: 1 | 2 | 3']
    idt_server number(19) [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do server ex: 1 | 2 | 3']
    idt_acquirer number(19) [not null, increment, note: '[NOT_SECURITY_APPLY] - Identificador interno da adquirente ex: 1 | 2 | 3']
    des_path varchar2(255) [not null, note: '[NOT_SECURITY_APPLY] - Diretorio dentro do server, ex: CIELO/IN | REDE/IN | PAGSEGURO/IN | CIELO/OUT | REDE/OUT | PAGSEGURO/OUT']
    des_path_type path_type_enum [not null, note: '[NOT_SECURITY_APPLY] - Tipo do caminho, ex: ORIGIN | DESTINATION']
    dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da geração do registro']
    dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent varchar2(50) [null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração']
    flg_active number(1) [not null, note: '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO']
  }
  ref: sever_paths.idt_server > server.idt_server

  *exemplo: idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type (ORIGIN|DESTINATION), flg_active
	ex: {1, 1, 1, 'CIELO/IN', 'ORIGIN', 1}
    	  , {2, 2, 1, 'CIELO/', 'DESTINATION', 1}


**sever_paths_in_out** — mapeamento da transferencia de origem para destino
**definição dbml para a tabela sever_paths_in_out: 
  Enum link_type_enum {
    "PRICIPAL" [note: 'Caminho principal, onde o arquivo é inicialmente recebido, ex: pasta de entrada do sftp']
    "SECUNDARIO" [note: 'Caminho secundário, utilizado como alternativa ao caminho principal, ex: pasta de entrada secundária do sftp']
  }
  Table sever_paths_in_out [note: '[NOT_SECURITY_APPLY] - Tabelas com dados relaciona pastas de entrada com pastas de destino'] {
    idt_sever_paths_in_out number(19) [primary key, increment, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro ex: 1 | 2 | 3']
    idt_sever_path_origin number(19) [primary key, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do diretorio do sftp, ex: 1 | 2 | 3']
    idt_sever_destination number(19) [primary key, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do diretorio destino, ex: 1 | 2 | 3']
    des_link_type link_type_enum [not null, note: '[NOT_SECURITY_APPLY] - Tipo do caminho, ex: PRICIPAL | SECUNDARIO']
    dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da geração do registro']
    dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent varchar2(50) [null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração']
    flg_active number(1) [not null, note: '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO']
    Indexes {
      (idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active) [unique, name:"sever_paths_in_out_idx_01", type: btree, note: '[NOT_SECURITY_APPLY] - Indice unico para código da API pois nao pode repetir']
    }
  }
  ref: sever_paths_in_out.idt_sever_path_origin > sever_paths.idt_sever_path
  ref: sever_paths_in_out.idt_sever_destination > sever_paths.idt_sever_path

  *exemplo: - idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type (PRICIPAL|SECUNDARIO), flg_active
	ex: {1, 1, 2, 'CIELO/IN', 'PRICIPAL', 1}


**file_origin** — tabela de estado para acompanhamento do processo de transferencia para cada arquivo.
**definição dbml para a tabela sever_paths_in_out: 

  Enum step_enum {
    "COLETA" [note: 'Arquivo esta sendo coletado, Job coletor responsavel por esse status']
    "DELETE" [note: 'Arquivo foi deletado']
    "RAW"
    "STAGING"
    "ORDINATION"
    "PROCESSING"
    "PROCESSED"
  }
  Enum status_enum {
    "EM_ESPERA" [note: 'Arquivo esta aguardando para ser processado no step']
    "PROCESSAMENTO" [note: 'Arquivo esta sendo processado no step']
    "CONCLUIDO" [note: 'Arquivo foi processado com sucesso no step']
    "ERRO" [note: 'Ocorreu um erro durante o processamento do arquivo no step']
  }

  Table file_origin [note: '[NOT_SECURITY_APPLY] - Tabelas com dados relacionados ao arquivo'] {
    idt_file_origin number(19) [primary key, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do arquivo ex: 1 | 2 | 3']
    idt_acquirer number(19) [ null, note: '[NOT_SECURITY_APPLY] - Identificador interno da adquirente ex: 1 | 2 | 3']
    idt_layout number(19) [null, note: '[NOT_SECURITY_APPLY] - Identificador interno do layout ex: 1 | 2 | 3']
    des_file_name varchar2(255) [not null, note: '[NOT_SECURITY_APPLY] - Nome do arquivo recebido']
    num_file_size number(19) [null, note: '[NOT_SECURITY_APPLY] - Tamanho do arquivo recebido, em bytes']
    des_file_mime_type varchar2(100) [null, note: '[NOT_SECURITY_APPLY] - Tipo mime do arquivo recebido, ex: text/csv | application/json']
    des_file_type file_type_enum [null, note: '[NOT_SECURITY_APPLY] - Tipo do arquivo recebido, ex: csv | json | txt | xml']
    des_step step_enum [not null, note: '[NOT_SECURITY_APPLY] - Descrição do passo realizado no arquivo, ex: LEITURA| COLETA | PROCESSAMENTO']
    des_status status_enum [null, note: '[NOT_SECURITY_APPLY] - Status do passo realizado no arquivo, ex: EM_EXECUCAO | CONCLUIDO | ERRO']
    des_message_error varchar2(4000) [null, note: '[NOT_SECURITY_APPLY] - Mensagem de erro relacionada ao passo, caso haja necessidade de registrar algum erro específico para aquele passo']  
    des_message_alert varchar2(4000) [null, note: '[NOT_SECURITY_APPLY] - Mensagem de alerta relacionada ao passo, caso haja necessidade de registrar algum alerta específico para aquele passo']
    des_transaction_type transaction_type_enum [not null, note: '[NOT_SECURITY_APPLY] - Tipo de transação que o arquivo contem,  ex:   COMPLETO | CAPTURA| FINANCEIRO'] // varchar2(100)
    dat_timestamp_file timestamp [not null, note: '[NOT_SECURITY_APPLY] - Data e hora presente no arquivo, pode ser data de criação do arquivo, no caso de sftp data e hora da criação do arquivo no servidor, no caso de api a data e hora do consumo da api']
    idt_sever_paths_in_out number(19) [not null, note: '[NOT_SECURITY_APPLY] - Identificador interno do registro, popular apenas se for o PRINCIPAL ex: 1 | 2 | 3']
    dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da geração do registro']
    dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent varchar2(50) [null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração']
    flg_active number(1) [not null, note: '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO']
    num_retry number(1) [not null, note: '[NOT_SECURITY_APPLY] - identifica o numero de tentativas feitas para este arquivo']
    max_retry number(1) [not null, note: '[NOT_SECURITY_APPLY] - máximo de tentativas que devem ser feitas antes de desistir']
    Indexes {
      (des_file_name, idt_acquirer, dat_timestamp_file, flg_active) [unique, name:"file_origin_idx_01", type: btree, note: '[NOT_SECURITY_APPLY] - Indice unico para controle de leitura de arquivo']
    }
  }
  ref: file_origin.idt_sever_paths_in_out > sever_paths_in_out.idt_sever_paths_in_out

  *exemplo: -  idt_file_origin, idt_acquirer, idt_layout, des_file_name, num_file_size, des_file_mime_type,  des_file_type, des_transaction_type, dat_timestamp_file, idt_sever_paths_in_out, flg_active
	      ex: {1, 1, 1, 'cielo-teste-edi.txt', 210037, 'text/plain', 'text', 'CAPTURA', 1774319917, 1, '23/03/2026 23:38:37', '23/03/2026 23:38:37', NULL, 1 }

---

## REGRAS DE NEGÓCIO CRÍTICAS

1. Upload com streaming: nunca carregar o arquivo inteiro em memória. Usar InputStream encadeado do SFTP diretamente para o destino (S3 multipart upload ou SFTP output stream).

2. Rastreabilidade: toda mudança de step/status deve atualizar o registro em file_origin.

3. Credenciais de servidores (SFTP, S3) devem ser lidas do Vault usando cod_vault + des_vault_secret da tabela server. Nunca hardcodar credenciais.
   Inicialmente podemos admitir que as credenciais estarão no .env em formato Json com o documento contendo o código especificado na tabela
   Ex: .env:SFTP_CIELO_VAULT = {"user":"cielo", "key":"admin-1-2-3"}

---

## O QUE PRECISO QUE VOCÊ IMPLEMENTE

Implemente de forma incremental, começando por:

1. Estrutura do projeto (mono repo, módulos Maven ou Gradle)
2. Docker Compose com: Oracle XE, RabbitMQ, LocalStack (S3), SFTP server
3. Scripts DDL Oracle para todas as tabelas acima
4. Container Producer: scheduler (rodando a cada 2 minutos) + listagem SFTP + registro no banco + publicação RabbitMQ
5. Container Consumer: consumer RabbitMQ + download streaming + upload streaming + atualização de status da rastreabilidade na tabela file_origin 
   Neste primeiro momento iremos utilizar apenas o step "COLETA" com todas as possibilidades de status.
6. Adicionar os Containers no Docker compose.

A cada etapa, aplique: tratamento de erros, logs estruturados, testes unitários para as regras de identificação, e boas práticas Spring Boot (profiles, externalized config, health checks).

Pergunte se tiver dúvidas antes de implementar. Prefira código limpo e simples a soluções over-engineered.