-- ============================================================================
-- Script: 02_create_table_sever_paths.sql
-- Description: Create table SEVER_PATHS with sequences, triggers and indexes
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-03-24
-- ============================================================================

-- Drop existing objects if they exist (for idempotency)
BEGIN
    EXECUTE IMMEDIATE 'DROP TRIGGER sever_paths_bir';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE sever_paths CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE sever_paths_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- Create sequence for auto-increment
CREATE SEQUENCE sever_paths_seq 
    START WITH 1 
    INCREMENT BY 1 
    NOCACHE 
    NOCYCLE;

-- Create table SEVER_PATHS
CREATE TABLE sever_paths (
    idt_sever_path NUMBER(19) PRIMARY KEY,
    idt_server NUMBER(19) NOT NULL,
    idt_acquirer NUMBER(19) NOT NULL,
    des_path VARCHAR2(255) NOT NULL,
    des_path_type VARCHAR2(50) NOT NULL CHECK (des_path_type IN ('ORIGIN', 'DESTINATION')),
    dat_creation DATE NOT NULL,
    dat_update DATE,
    nam_change_agent VARCHAR2(50),
    flg_active NUMBER(1) NOT NULL CHECK (flg_active IN (0, 1)),
    CONSTRAINT fk_sever_paths_server FOREIGN KEY (idt_server) REFERENCES server(idt_server)
);

-- Add comments on table
COMMENT ON TABLE sever_paths IS '[NOT_SECURITY_APPLY] - Tabelas com dados relacionados aos diretorios de origem e destino para armazenamento dos arquivos';

-- Add comments on columns
COMMENT ON COLUMN sever_paths.idt_sever_path IS '[NOT_SECURITY_APPLY] - Identificador interno, ex: 1 | 2 | 3';
COMMENT ON COLUMN sever_paths.idt_server IS '[NOT_SECURITY_APPLY] - Identificador interno do server ex: 1 | 2 | 3';
COMMENT ON COLUMN sever_paths.idt_acquirer IS '[NOT_SECURITY_APPLY] - Identificador interno da adquirente ex: 1 | 2 | 3';
COMMENT ON COLUMN sever_paths.des_path IS '[NOT_SECURITY_APPLY] - Diretorio dentro do server, ex: CIELO/IN | REDE/IN | PAGSEGURO/IN | CIELO/OUT | REDE/OUT | PAGSEGURO/OUT';
COMMENT ON COLUMN sever_paths.des_path_type IS '[NOT_SECURITY_APPLY] - Tipo do caminho, ex: ORIGIN | DESTINATION';
COMMENT ON COLUMN sever_paths.dat_creation IS '[NOT_SECURITY_APPLY] - Data e hora da geração do registro';
COMMENT ON COLUMN sever_paths.dat_update IS '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro';
COMMENT ON COLUMN sever_paths.nam_change_agent IS '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração';
COMMENT ON COLUMN sever_paths.flg_active IS '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO';

-- Create trigger for auto-increment and dat_creation
CREATE OR REPLACE TRIGGER sever_paths_bir
BEFORE INSERT ON sever_paths
FOR EACH ROW
BEGIN
    IF :NEW.idt_sever_path IS NULL THEN
        SELECT sever_paths_seq.NEXTVAL INTO :NEW.idt_sever_path FROM dual;
    END IF;
    IF :NEW.dat_creation IS NULL THEN
        :NEW.dat_creation := SYSDATE;
    END IF;
END;
/

-- Verification
SELECT 'Table SEVER_PATHS created successfully' AS status FROM dual;
