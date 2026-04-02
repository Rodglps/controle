-- ============================================================================
-- Script: 05_insert_test_data.sql
-- Description: Insert test data for development and testing
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-03-24
-- ============================================================================

-- Insert Server configurations
-- SFTP Origin (CIELO)
INSERT INTO server (cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES ('SFTP_CIELO_ORIGIN', 'SFTP_CIELO_VAULT', '/sftp_cielo', 'SFTP', 'EXTERNO', 1);

-- S3 Destination (Internal)
INSERT INTO server (cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES ('S3_DESTINATION', 'S3_VAULT', '/s3_bucket', 'S3', 'INTERNO', 1);

-- SFTP Destination (Internal)
INSERT INTO server (cod_server, cod_vault, des_vault_secret, des_server_type, des_server_origin, flg_active)
VALUES ('SFTP_DESTINATION', 'SFTP_DEST_VAULT', '/sftp_dest', 'SFTP', 'INTERNO', 1);

-- Insert Server Paths
-- Origin path for CIELO (idt_server=1, idt_acquirer=1 for CIELO)
INSERT INTO sever_paths (idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (1, 1, 'upload', 'ORIGIN', 1);

-- Destination path for S3 (idt_server=2, idt_acquirer=1 for CIELO)
INSERT INTO sever_paths (idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (2, 1, 'edi-files/cielo', 'DESTINATION', 1);

-- Destination path for SFTP (idt_server=3, idt_acquirer=1 for CIELO)
INSERT INTO sever_paths (idt_server, idt_acquirer, des_path, des_path_type, flg_active)
VALUES (3, 1, 'destination', 'DESTINATION', 1);

-- Insert Server Path Mappings (PRINCIPAL only for MVP)
-- SFTP Origin -> S3 Destination (idt_sever_path_origin=1, idt_sever_destination=2)
INSERT INTO sever_paths_in_out (idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
VALUES (1, 2, 'PRINCIPAL', 1);

-- Commit changes
COMMIT;

-- Verification
SELECT 'Test data inserted successfully' AS status FROM dual;

-- Display inserted data
SELECT 'Servers:' AS info FROM dual;
SELECT idt_server, cod_server, des_server_type, des_server_origin, flg_active FROM server;

SELECT 'Server Paths:' AS info FROM dual;
SELECT idt_sever_path, idt_server, idt_acquirer, des_path, des_path_type, flg_active FROM sever_paths;

SELECT 'Server Path Mappings:' AS info FROM dual;
SELECT idt_sever_paths_in_out, idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active FROM sever_paths_in_out;
