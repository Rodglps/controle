-- ============================================================================
-- Script: 01_create_table_server.sql
-- Description: Create table SERVER with sequences, triggers and indexes
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-03-24
-- ============================================================================

-- Drop existing objects if they exist (for idempotency)
BEGIN
    EXECUTE IMMEDIATE 'DROP TRIGGER server_bir';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE server CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE server_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- Create sequence for auto-increment
CREATE SEQUENCE server_seq 
    START WITH 1 
    INCREMENT BY 1 
    NOCACHE 
    NOCYCLE;

-- Create table SERVER
CREATE TABLE server (
    idt_server NUMBER(19) PRIMARY KEY,
    cod_server VARCHAR2(100) NOT NULL,
    cod_vault VARCHAR2(100) NOT NULL,
    des_vault_secret VARCHAR2(255) NOT NULL,
    des_server_type VARCHAR2(50) NOT NULL CHECK (des_server_type IN ('S3', 'Blob-Storage', 'Object Storage', 'SFTP', 'NFS')),
    des_server_origin VARCHAR2(50) NOT NULL CHECK (des_server_origin IN ('INTERNO', 'EXTERNO')),
    dat_creation DATE NOT NULL,
    dat_update DATE,
    nam_change_agent VARCHAR2(50),
    flg_active NUMBER(1) NOT NULL CHECK (flg_active IN (0, 1)),
    num_min_age_seconds NUMBER(10) DEFAULT 0,
    num_double_check_wait_seconds NUMBER(10) DEFAULT 0,
    CONSTRAINT server_idx_01 UNIQUE (cod_server, flg_active)
);

-- Add comments on table
COMMENT ON TABLE server IS '[NOT_SECURITY_APPLY] - Tabelas com dados relacionados aos servidores(objetos) de arquivos, tanto de origem quanto de destino';

-- Add comments on columns
COMMENT ON COLUMN server.idt_server IS '[NOT_SECURITY_APPLY] - Identificador interno, ex: 1 | 2 | 3';
COMMENT ON COLUMN server.cod_server IS '[NOT_SECURITY_APPLY] - Código interno do destino do arquivo, ex: S3-PAGSEGURO | S3-CIELO | S3-REDE | SFTP-PAGSEGURO | SFTP-CIELO | SFTP-REDE';
COMMENT ON COLUMN server.cod_vault IS '[NOT_SECURITY_APPLY] - Código interno do vault onde esta segredo com dados de acesso, ex: concil_control_arquivos';
COMMENT ON COLUMN server.des_vault_secret IS '[NOT_SECURITY_APPLY] - Estrutura de pasta dentro do vault onde esta o segredo, ex: concil_controle_arquivo/s3_pags';
COMMENT ON COLUMN server.des_server_type IS '[NOT_SECURITY_APPLY] - Indica o tipo de server';
COMMENT ON COLUMN server.des_server_origin IS '[NOT_SECURITY_APPLY] - Indica se a origem é interna ou externa';
COMMENT ON COLUMN server.dat_creation IS '[NOT_SECURITY_APPLY] - Data e hora da geração do registro';
COMMENT ON COLUMN server.dat_update IS '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro';
COMMENT ON COLUMN server.nam_change_agent IS '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração';
COMMENT ON COLUMN server.flg_active IS '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO';
COMMENT ON COLUMN server.num_min_age_seconds IS '[NOT_SECURITY_APPLY] - Minimum age in seconds for files to pass primary filter. 0 = disabled.';
COMMENT ON COLUMN server.num_double_check_wait_seconds IS '[NOT_SECURITY_APPLY] - Wait duration in seconds before re-checking file metadata. 0 = disabled.';

-- Create trigger for auto-increment and dat_creation
CREATE OR REPLACE TRIGGER server_bir
BEFORE INSERT ON server
FOR EACH ROW
BEGIN
    IF :NEW.idt_server IS NULL THEN
        SELECT server_seq.NEXTVAL INTO :NEW.idt_server FROM dual;
    END IF;
    IF :NEW.dat_creation IS NULL THEN
        :NEW.dat_creation := SYSDATE;
    END IF;
END;
/

-- Verification
SELECT 'Table SERVER created successfully' AS status FROM dual;
