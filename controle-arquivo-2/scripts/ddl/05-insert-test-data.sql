-- ============================================================================
-- Script: 05-insert-test-data.sql
-- Descrição: Inserção de dados de teste para desenvolvimento
-- Sistema: Controle de Arquivos
-- ============================================================================

-- ============================================================================
-- Dados de teste para server
-- ============================================================================
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_CIELO', 'vault-dev', 'secret/sftp/cielo', 'SFTP', 'EXTERNO', 1);

INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_REDE', 'vault-dev', 'secret/sftp/rede', 'SFTP', 'EXTERNO', 1);

INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'S3_DESTINO', 'vault-dev', 'secret/s3/destino', 'S3', 'INTERNO', 1);

INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_INTERNO', 'vault-dev', 'secret/sftp/interno', 'SFTP', 'INTERNO', 1);

-- ============================================================================
-- Dados de teste para sever_paths
-- ============================================================================
-- Caminhos de origem (SFTP Cielo - Adquirente ID 1)
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 1, 1, '/incoming/cielo', 'ORIGIN', 1);

-- Caminhos de origem (SFTP Rede - Adquirente ID 2)
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 2, 2, '/incoming/rede', 'ORIGIN', 1);

-- Caminhos de destino (S3)
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 3, 1, 'raw/cielo', 'DESTINATION', 1);

INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 3, 2, 'raw/rede', 'DESTINATION', 1);

-- ============================================================================
-- Dados de teste para sever_paths_in_out
-- ============================================================================
-- Mapeamento Cielo: SFTP origem -> S3 destino
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 1, 3, 'PRINCIPAL', 1);

-- Mapeamento Rede: SFTP origem -> S3 destino
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 2, 3, 'PRINCIPAL', 1);

-- ============================================================================
-- Dados de teste para layout
-- ============================================================================
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'CIELO_CSV_V1', 'CSV', 'Layout CSV padrão Cielo versão 1', 1);

INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'REDE_TXT_V1', 'TXT', 'Layout TXT padrão Rede versão 1', 1);

INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'CIELO_OFX_V1', 'OFX', 'Layout OFX padrão Cielo versão 1', 1);

-- ============================================================================
-- Dados de teste para customer_identification
-- ============================================================================
INSERT INTO customer_identification (idt_customer_identification, des_customer_name, num_processing_weight, flg_active)
VALUES (seq_customer_identification.NEXTVAL, 'CLIENTE_A', 100, 1);

INSERT INTO customer_identification (idt_customer_identification, des_customer_name, num_processing_weight, flg_active)
VALUES (seq_customer_identification.NEXTVAL, 'CLIENTE_B', 90, 1);

INSERT INTO customer_identification (idt_customer_identification, des_customer_name, num_processing_weight, flg_active)
VALUES (seq_customer_identification.NEXTVAL, 'CLIENTE_C', 80, 1);

-- ============================================================================
-- Dados de teste para customer_identification_rule
-- ============================================================================
-- Regras para CLIENTE_A (Adquirente Cielo - ID 1)
-- Regra: Nome do arquivo começa com "CLIENTEA_" nas posições 0-9
INSERT INTO customer_identification_rule (idt_customer_identification_rule, idt_customer_identification, idt_acquirer, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_customer_identification_rule.NEXTVAL, 1, 1, 'COMECA-COM', 0, 9, 'CLIENTEA_', 1);

-- Regras para CLIENTE_B (Adquirente Cielo - ID 1)
-- Regra: Nome do arquivo contém "CLIENTEB" nas posições 0-50
INSERT INTO customer_identification_rule (idt_customer_identification_rule, idt_customer_identification, idt_acquirer, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_customer_identification_rule.NEXTVAL, 2, 1, 'CONTEM', 0, 50, 'CLIENTEB', 1);

-- Regras para CLIENTE_C (Adquirente Rede - ID 2)
-- Regra: Nome do arquivo termina com "_CLIENTEC.txt" nas posições 0-100
INSERT INTO customer_identification_rule (idt_customer_identification_rule, idt_customer_identification, idt_acquirer, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_customer_identification_rule.NEXTVAL, 3, 2, 'TERMINA-COM', 0, 100, '_CLIENTEC.txt', 1);

-- ============================================================================
-- Dados de teste para layout_identification_rule
-- ============================================================================
-- Regras para CIELO_CSV_V1 (Layout ID 1, Cliente A ID 1, Adquirente Cielo ID 1)
-- Regra 1: Nome do arquivo contém ".csv"
INSERT INTO layout_identification_rule (idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer, des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_layout_identification_rule.NEXTVAL, 1, 1, 1, 'FILENAME', 'CONTEM', 0, 100, '.csv', 1);

-- Regra 2: Header começa com "TIPO;DATA;VALOR"
INSERT INTO layout_identification_rule (idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer, des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_layout_identification_rule.NEXTVAL, 1, 1, 1, 'HEADER', 'COMECA-COM', 0, 50, 'TIPO;DATA;VALOR', 1);

-- Regras para REDE_TXT_V1 (Layout ID 2, Cliente C ID 3, Adquirente Rede ID 2)
-- Regra 1: Nome do arquivo contém ".txt"
INSERT INTO layout_identification_rule (idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer, des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_layout_identification_rule.NEXTVAL, 2, 3, 2, 'FILENAME', 'CONTEM', 0, 100, '.txt', 1);

-- Regra 2: Header contém "REDE"
INSERT INTO layout_identification_rule (idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer, des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_layout_identification_rule.NEXTVAL, 2, 3, 2, 'HEADER', 'CONTEM', 0, 100, 'REDE', 1);

-- Regras para CIELO_OFX_V1 (Layout ID 3, Cliente B ID 2, Adquirente Cielo ID 1)
-- Regra 1: Nome do arquivo contém ".ofx"
INSERT INTO layout_identification_rule (idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer, des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_layout_identification_rule.NEXTVAL, 3, 2, 1, 'FILENAME', 'CONTEM', 0, 100, '.ofx', 1);

-- Regra 2: Header contém "<OFX>"
INSERT INTO layout_identification_rule (idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer, des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position, des_value, flg_active)
VALUES (seq_layout_identification_rule.NEXTVAL, 3, 2, 1, 'HEADER', 'CONTEM', 0, 100, '<OFX>', 1);

COMMIT;

EXIT;
