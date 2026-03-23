-- ============================================================================
-- Script: 02-create-tables.sql
-- Descrição: Criação de todas as tabelas do sistema
-- Sistema: Controle de Arquivos
-- ============================================================================

-- ============================================================================
-- Tabela: job_concurrency_control
-- Descrição: Controle de concorrência de execução de jobs
-- ============================================================================
CREATE TABLE job_concurrency_control (
    idt_job_concurrency_control NUMBER(19) NOT NULL,
    des_job_name VARCHAR2(100) NOT NULL,
    des_status VARCHAR2(20) NOT NULL, -- RUNNING, COMPLETED, PENDING
    dat_last_execution TIMESTAMP,
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_job_concurrency_control PRIMARY KEY (idt_job_concurrency_control)
);

COMMENT ON TABLE job_concurrency_control IS 'Controle de concorrência de execução de jobs';
COMMENT ON COLUMN job_concurrency_control.idt_job_concurrency_control IS 'Identificador único do registro';
COMMENT ON COLUMN job_concurrency_control.des_job_name IS 'Nome do job';
COMMENT ON COLUMN job_concurrency_control.des_status IS 'Status da execução: RUNNING, COMPLETED, PENDING';
COMMENT ON COLUMN job_concurrency_control.dat_last_execution IS 'Data da última execução';
COMMENT ON COLUMN job_concurrency_control.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: server
-- Descrição: Servidores SFTP, S3, NFS, etc.
-- ============================================================================
CREATE TABLE server (
    idt_server NUMBER(19) NOT NULL,
    cod_server VARCHAR2(50) NOT NULL,
    cod_vault VARCHAR2(100) NOT NULL,
    des_vault_secret VARCHAR2(200) NOT NULL,
    des_server_type VARCHAR2(20) NOT NULL, -- S3, SFTP, NFS, BLOB_STORAGE, OBJECT_STORAGE
    des_server_origin VARCHAR2(20) NOT NULL, -- INTERNO, EXTERNO
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_server PRIMARY KEY (idt_server)
);

COMMENT ON TABLE server IS 'Servidores de origem e destino de arquivos';
COMMENT ON COLUMN server.idt_server IS 'Identificador único do servidor';
COMMENT ON COLUMN server.cod_server IS 'Código identificador do servidor';
COMMENT ON COLUMN server.cod_vault IS 'Código do Vault para obter credenciais';
COMMENT ON COLUMN server.des_vault_secret IS 'Caminho do secret no Vault';
COMMENT ON COLUMN server.des_server_type IS 'Tipo do servidor: S3, SFTP, NFS, BLOB_STORAGE, OBJECT_STORAGE';
COMMENT ON COLUMN server.des_server_origin IS 'Origem do servidor: INTERNO, EXTERNO';
COMMENT ON COLUMN server.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: sever_paths
-- Descrição: Caminhos de diretórios em servidores
-- ============================================================================
CREATE TABLE sever_paths (
    idt_sever_path NUMBER(19) NOT NULL,
    idt_server NUMBER(19) NOT NULL,
    idt_acquirer NUMBER(19) NOT NULL,
    des_path VARCHAR2(500) NOT NULL,
    des_path_type VARCHAR2(20) NOT NULL, -- ORIGIN, DESTINATION
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_sever_paths PRIMARY KEY (idt_sever_path)
);

COMMENT ON TABLE sever_paths IS 'Caminhos de diretórios em servidores';
COMMENT ON COLUMN sever_paths.idt_sever_path IS 'Identificador único do caminho';
COMMENT ON COLUMN sever_paths.idt_server IS 'Identificador do servidor';
COMMENT ON COLUMN sever_paths.idt_acquirer IS 'Identificador do adquirente';
COMMENT ON COLUMN sever_paths.des_path IS 'Caminho do diretório';
COMMENT ON COLUMN sever_paths.des_path_type IS 'Tipo do caminho: ORIGIN, DESTINATION';
COMMENT ON COLUMN sever_paths.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: sever_paths_in_out
-- Descrição: Mapeamento entre caminhos de origem e destino
-- ============================================================================
CREATE TABLE sever_paths_in_out (
    idt_sever_paths_in_out NUMBER(19) NOT NULL,
    idt_sever_path_origin NUMBER(19) NOT NULL,
    idt_sever_destination NUMBER(19) NOT NULL,
    des_link_type VARCHAR2(20) NOT NULL, -- PRINCIPAL, SECUNDARIO
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_sever_paths_in_out PRIMARY KEY (idt_sever_paths_in_out)
);

COMMENT ON TABLE sever_paths_in_out IS 'Mapeamento entre caminhos de origem e destino';
COMMENT ON COLUMN sever_paths_in_out.idt_sever_paths_in_out IS 'Identificador único do mapeamento';
COMMENT ON COLUMN sever_paths_in_out.idt_sever_path_origin IS 'Identificador do caminho de origem';
COMMENT ON COLUMN sever_paths_in_out.idt_sever_destination IS 'Identificador do servidor de destino';
COMMENT ON COLUMN sever_paths_in_out.des_link_type IS 'Tipo do link: PRINCIPAL, SECUNDARIO';
COMMENT ON COLUMN sever_paths_in_out.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: layout
-- Descrição: Layouts de arquivos (CSV, TXT, JSON, OFX, XML)
-- ============================================================================
CREATE TABLE layout (
    idt_layout NUMBER(19) NOT NULL,
    des_layout_name VARCHAR2(100) NOT NULL,
    des_layout_type VARCHAR2(20) NOT NULL, -- CSV, TXT, JSON, OFX, XML
    des_description VARCHAR2(500),
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_layout PRIMARY KEY (idt_layout)
);

COMMENT ON TABLE layout IS 'Layouts de arquivos';
COMMENT ON COLUMN layout.idt_layout IS 'Identificador único do layout';
COMMENT ON COLUMN layout.des_layout_name IS 'Nome do layout';
COMMENT ON COLUMN layout.des_layout_type IS 'Tipo do layout: CSV, TXT, JSON, OFX, XML';
COMMENT ON COLUMN layout.des_description IS 'Descrição do layout';
COMMENT ON COLUMN layout.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: layout_identification_rule
-- Descrição: Regras para identificação de layout
-- ============================================================================
CREATE TABLE layout_identification_rule (
    idt_layout_identification_rule NUMBER(19) NOT NULL,
    idt_layout NUMBER(19) NOT NULL,
    idt_client NUMBER(19) NOT NULL,
    idt_acquirer NUMBER(19) NOT NULL,
    des_value_origin VARCHAR2(20) NOT NULL, -- HEADER, TAG, FILENAME, KEY
    des_criterion_type_enum VARCHAR2(20) NOT NULL, -- COMECA-COM, TERMINA-COM, CONTEM, IGUAL
    num_starting_position NUMBER(10),
    num_ending_position NUMBER(10),
    des_value VARCHAR2(500) NOT NULL,
    des_tag VARCHAR2(100),
    des_key VARCHAR2(100),
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_layout_identification_rule PRIMARY KEY (idt_layout_identification_rule)
);

COMMENT ON TABLE layout_identification_rule IS 'Regras para identificação de layout';
COMMENT ON COLUMN layout_identification_rule.idt_layout_identification_rule IS 'Identificador único da regra';
COMMENT ON COLUMN layout_identification_rule.idt_layout IS 'Identificador do layout';
COMMENT ON COLUMN layout_identification_rule.idt_client IS 'Identificador do cliente';
COMMENT ON COLUMN layout_identification_rule.idt_acquirer IS 'Identificador do adquirente';
COMMENT ON COLUMN layout_identification_rule.des_value_origin IS 'Origem do valor: HEADER, TAG, FILENAME, KEY';
COMMENT ON COLUMN layout_identification_rule.des_criterion_type_enum IS 'Tipo de critério: COMECA-COM, TERMINA-COM, CONTEM, IGUAL';
COMMENT ON COLUMN layout_identification_rule.num_starting_position IS 'Posição inicial para extração de substring';
COMMENT ON COLUMN layout_identification_rule.num_ending_position IS 'Posição final para extração de substring';
COMMENT ON COLUMN layout_identification_rule.des_value IS 'Valor esperado para comparação';
COMMENT ON COLUMN layout_identification_rule.des_tag IS 'Tag XML/HTML para busca';
COMMENT ON COLUMN layout_identification_rule.des_key IS 'Chave JSON para busca';
COMMENT ON COLUMN layout_identification_rule.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: customer_identification
-- Descrição: Clientes do sistema
-- ============================================================================
CREATE TABLE customer_identification (
    idt_customer_identification NUMBER(19) NOT NULL,
    des_customer_name VARCHAR2(200) NOT NULL,
    num_processing_weight NUMBER(10) DEFAULT 0 NOT NULL,
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_customer_identification PRIMARY KEY (idt_customer_identification)
);

COMMENT ON TABLE customer_identification IS 'Clientes do sistema';
COMMENT ON COLUMN customer_identification.idt_customer_identification IS 'Identificador único do cliente';
COMMENT ON COLUMN customer_identification.des_customer_name IS 'Nome do cliente';
COMMENT ON COLUMN customer_identification.num_processing_weight IS 'Peso para desempate quando múltiplos clientes satisfazem regras';
COMMENT ON COLUMN customer_identification.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: customer_identification_rule
-- Descrição: Regras para identificação de cliente
-- ============================================================================
CREATE TABLE customer_identification_rule (
    idt_customer_identification_rule NUMBER(19) NOT NULL,
    idt_customer_identification NUMBER(19) NOT NULL,
    idt_acquirer NUMBER(19) NOT NULL,
    des_criterion_type_enum VARCHAR2(20) NOT NULL, -- COMECA-COM, TERMINA-COM, CONTEM, IGUAL
    num_starting_position NUMBER(10),
    num_ending_position NUMBER(10),
    des_value VARCHAR2(500) NOT NULL,
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_customer_identification_rule PRIMARY KEY (idt_customer_identification_rule)
);

COMMENT ON TABLE customer_identification_rule IS 'Regras para identificação de cliente';
COMMENT ON COLUMN customer_identification_rule.idt_customer_identification_rule IS 'Identificador único da regra';
COMMENT ON COLUMN customer_identification_rule.idt_customer_identification IS 'Identificador do cliente';
COMMENT ON COLUMN customer_identification_rule.idt_acquirer IS 'Identificador do adquirente';
COMMENT ON COLUMN customer_identification_rule.des_criterion_type_enum IS 'Tipo de critério: COMECA-COM, TERMINA-COM, CONTEM, IGUAL';
COMMENT ON COLUMN customer_identification_rule.num_starting_position IS 'Posição inicial para extração de substring do nome do arquivo';
COMMENT ON COLUMN customer_identification_rule.num_ending_position IS 'Posição final para extração de substring do nome do arquivo';
COMMENT ON COLUMN customer_identification_rule.des_value IS 'Valor esperado para comparação';
COMMENT ON COLUMN customer_identification_rule.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: file_origin
-- Descrição: Arquivos coletados de servidores SFTP
-- ============================================================================
CREATE TABLE file_origin (
    idt_file_origin NUMBER(19) NOT NULL,
    idt_acquirer NUMBER(19) NOT NULL,
    idt_layout NUMBER(19),
    des_file_name VARCHAR2(500) NOT NULL,
    num_file_size NUMBER(19) NOT NULL,
    des_file_type VARCHAR2(50),
    des_transaction_type VARCHAR2(50),
    dat_timestamp_file TIMESTAMP NOT NULL,
    idt_sever_paths_in_out NUMBER(19) NOT NULL,
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_file_origin PRIMARY KEY (idt_file_origin)
);

COMMENT ON TABLE file_origin IS 'Arquivos coletados de servidores SFTP';
COMMENT ON COLUMN file_origin.idt_file_origin IS 'Identificador único do arquivo';
COMMENT ON COLUMN file_origin.idt_acquirer IS 'Identificador do adquirente';
COMMENT ON COLUMN file_origin.idt_layout IS 'Identificador do layout (preenchido após identificação)';
COMMENT ON COLUMN file_origin.des_file_name IS 'Nome do arquivo';
COMMENT ON COLUMN file_origin.num_file_size IS 'Tamanho do arquivo em bytes';
COMMENT ON COLUMN file_origin.des_file_type IS 'Tipo do arquivo (preenchido após identificação)';
COMMENT ON COLUMN file_origin.des_transaction_type IS 'Tipo de transação (preenchido após identificação)';
COMMENT ON COLUMN file_origin.dat_timestamp_file IS 'Timestamp do arquivo no servidor de origem';
COMMENT ON COLUMN file_origin.idt_sever_paths_in_out IS 'Identificador do mapeamento origem-destino';
COMMENT ON COLUMN file_origin.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: file_origin_client
-- Descrição: Associação entre arquivo e cliente identificado
-- ============================================================================
CREATE TABLE file_origin_client (
    idt_file_origin_client NUMBER(19) NOT NULL,
    idt_file_origin NUMBER(19) NOT NULL,
    idt_client NUMBER(19) NOT NULL,
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_file_origin_client PRIMARY KEY (idt_file_origin_client)
);

COMMENT ON TABLE file_origin_client IS 'Associação entre arquivo e cliente identificado';
COMMENT ON COLUMN file_origin_client.idt_file_origin_client IS 'Identificador único da associação';
COMMENT ON COLUMN file_origin_client.idt_file_origin IS 'Identificador do arquivo';
COMMENT ON COLUMN file_origin_client.idt_client IS 'Identificador do cliente';
COMMENT ON COLUMN file_origin_client.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

-- ============================================================================
-- Tabela: file_origin_client_processing
-- Descrição: Rastreabilidade de processamento de arquivos
-- ============================================================================
CREATE TABLE file_origin_client_processing (
    idt_file_origin_processing NUMBER(19) NOT NULL,
    idt_file_origin_client NUMBER(19) NOT NULL,
    des_step VARCHAR2(50) NOT NULL, -- COLETA, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED, DELETE
    des_status VARCHAR2(50) NOT NULL, -- EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO
    des_message_error CLOB,
    des_message_alert CLOB,
    dat_step_start TIMESTAMP,
    dat_step_end TIMESTAMP,
    jsn_additional_info CLOB,
    dat_created TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dat_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_file_origin_client_processing PRIMARY KEY (idt_file_origin_processing)
);

COMMENT ON TABLE file_origin_client_processing IS 'Rastreabilidade de processamento de arquivos';
COMMENT ON COLUMN file_origin_client_processing.idt_file_origin_processing IS 'Identificador único do registro de processamento';
COMMENT ON COLUMN file_origin_client_processing.idt_file_origin_client IS 'Identificador da associação arquivo-cliente';
COMMENT ON COLUMN file_origin_client_processing.des_step IS 'Etapa do processamento: COLETA, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED, DELETE';
COMMENT ON COLUMN file_origin_client_processing.des_status IS 'Status da etapa: EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO';
COMMENT ON COLUMN file_origin_client_processing.des_message_error IS 'Mensagem de erro (se houver)';
COMMENT ON COLUMN file_origin_client_processing.des_message_alert IS 'Mensagem de alerta (se houver)';
COMMENT ON COLUMN file_origin_client_processing.dat_step_start IS 'Data/hora de início da etapa';
COMMENT ON COLUMN file_origin_client_processing.dat_step_end IS 'Data/hora de fim da etapa';
COMMENT ON COLUMN file_origin_client_processing.jsn_additional_info IS 'Informações adicionais em formato JSON';
COMMENT ON COLUMN file_origin_client_processing.flg_active IS 'Flag de registro ativo (1=ativo, 0=inativo)';

COMMIT;

EXIT;
