-- ============================================================================
-- Script: 01-insert-servers.sql
-- Descrição: Dados de teste para servidores SFTP e destinos
-- Sistema: Controle de Arquivos - Test Data
-- ============================================================================

-- ============================================================================
-- Servidores SFTP Externos (Origem)
-- ============================================================================

-- Servidor SFTP Cielo
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_CIELO_TEST', 'vault-test', 'secret/test/sftp/cielo', 'SFTP', 'EXTERNO', 1);

-- Servidor SFTP Rede
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_REDE_TEST', 'vault-test', 'secret/test/sftp/rede', 'SFTP', 'EXTERNO', 1);

-- Servidor SFTP Stone
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_STONE_TEST', 'vault-test', 'secret/test/sftp/stone', 'SFTP', 'EXTERNO', 1);

-- Servidor SFTP GetNet
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_GETNET_TEST', 'vault-test', 'secret/test/sftp/getnet', 'SFTP', 'EXTERNO', 1);

-- ============================================================================
-- Servidores de Destino (Interno)
-- ============================================================================

-- S3 Destino Principal
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'S3_RAW_TEST', 'vault-test', 'secret/test/s3/raw', 'S3', 'INTERNO', 1);

-- SFTP Interno (Backup)
INSERT INTO server (idt_server, cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES (seq_server.NEXTVAL, 'SFTP_INTERNO_TEST', 'vault-test', 'secret/test/sftp/interno', 'SFTP', 'INTERNO', 1);

-- ============================================================================
-- Caminhos de Origem (ORIGIN)
-- ============================================================================

-- Cielo - Adquirente ID 1
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 1, 1, '/incoming/cielo/edi', 'ORIGIN', 1);

-- Rede - Adquirente ID 2
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 2, 2, '/incoming/rede/files', 'ORIGIN', 1);

-- Stone - Adquirente ID 3
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 3, 3, '/data/stone/export', 'ORIGIN', 1);

-- GetNet - Adquirente ID 4
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 4, 4, '/getnet/outbound', 'ORIGIN', 1);

-- ============================================================================
-- Caminhos de Destino (DESTINATION)
-- ============================================================================

-- S3 - Cielo
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 5, 1, 'raw/cielo', 'DESTINATION', 1);

-- S3 - Rede
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 5, 2, 'raw/rede', 'DESTINATION', 1);

-- S3 - Stone
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 5, 3, 'raw/stone', 'DESTINATION', 1);

-- S3 - GetNet
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 5, 4, 'raw/getnet', 'DESTINATION', 1);

-- SFTP Interno - Backup Cielo
INSERT INTO sever_paths (idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (seq_sever_paths.NEXTVAL, 6, 1, '/backup/cielo', 'DESTINATION', 1);

-- ============================================================================
-- Mapeamentos Origem -> Destino
-- ============================================================================

-- Cielo: SFTP -> S3 (Principal)
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 1, 5, 'PRINCIPAL', 1);

-- Cielo: SFTP -> SFTP Interno (Secundário)
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 1, 6, 'SECUNDARIO', 1);

-- Rede: SFTP -> S3 (Principal)
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 2, 5, 'PRINCIPAL', 1);

-- Stone: SFTP -> S3 (Principal)
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 3, 5, 'PRINCIPAL', 1);

-- GetNet: SFTP -> S3 (Principal)
INSERT INTO sever_paths_in_out (idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (seq_sever_paths_in_out.NEXTVAL, 4, 5, 'PRINCIPAL', 1);

COMMIT;

EXIT;
