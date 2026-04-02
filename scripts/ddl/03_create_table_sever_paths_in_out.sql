-- ============================================================================
-- Script: 03_create_table_sever_paths_in_out.sql
-- Description: Create table SEVER_PATHS_IN_OUT with sequences, triggers and indexes
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-03-24
-- ============================================================================

-- Drop existing objects if they exist (for idempotency)
BEGIN
    EXECUTE IMMEDIATE 'DROP TRIGGER sever_paths_in_out_bir';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE sever_paths_in_out CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE sever_paths_in_out_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- Create sequence for auto-increment
CREATE SEQUENCE sever_paths_in_out_seq 
    START WITH 1 
    INCREMENT BY 1 
    NOCACHE 
    NOCYCLE;

-- Create table SEVER_PATHS_IN_OUT
CREATE TABLE sever_paths_in_out (
    idt_sever_paths_in_out NUMBER(19) PRIMARY KEY,
    idt_sever_path_origin NUMBER(19) NOT NULL,
    idt_sever_destination NUMBER(19) NOT NULL,
    des_link_type VARCHAR2(50) NOT NULL CHECK (des_link_type IN ('PRINCIPAL', 'SECUNDARIO')),
    dat_creation DATE NOT NULL,
    dat_update DATE,
    nam_change_agent VARCHAR2(50),
    flg_active NUMBER(1) NOT NULL CHECK (flg_active IN (0, 1)),
    CONSTRAINT fk_spio_origin FOREIGN KEY (idt_sever_path_origin) REFERENCES sever_paths(idt_sever_path),
    CONSTRAINT fk_spio_destination FOREIGN KEY (idt_sever_destination) REFERENCES sever_paths(idt_sever_path),
    CONSTRAINT sever_paths_in_out_idx_01 UNIQUE (idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active)
);

-- Add comments on table
COMMENT ON TABLE sever_paths_in_out IS '[NOT_SECURITY_APPLY] - Tabelas com dados relaciona pastas de entrada com pastas de destino';

-- Add comments on columns
COMMENT ON COLUMN sever_paths_in_out.idt_sever_paths_in_out IS '[NOT_SECURITY_APPLY] - Identificador interno do registro ex: 1 | 2 | 3';
COMMENT ON COLUMN sever_paths_in_out.idt_sever_path_origin IS '[NOT_SECURITY_APPLY] - Identificador interno do diretorio do sftp, ex: 1 | 2 | 3';
COMMENT ON COLUMN sever_paths_in_out.idt_sever_destination IS '[NOT_SECURITY_APPLY] - Identificador interno do diretorio destino, ex: 1 | 2 | 3';
COMMENT ON COLUMN sever_paths_in_out.des_link_type IS '[NOT_SECURITY_APPLY] - Tipo do caminho, ex: PRINCIPAL | SECUNDARIO';
COMMENT ON COLUMN sever_paths_in_out.dat_creation IS '[NOT_SECURITY_APPLY] - Data e hora da geração do registro';
COMMENT ON COLUMN sever_paths_in_out.dat_update IS '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro';
COMMENT ON COLUMN sever_paths_in_out.nam_change_agent IS '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração';
COMMENT ON COLUMN sever_paths_in_out.flg_active IS '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO';

-- Create trigger for auto-increment and dat_creation
CREATE OR REPLACE TRIGGER sever_paths_in_out_bir
BEFORE INSERT ON sever_paths_in_out
FOR EACH ROW
BEGIN
    IF :NEW.idt_sever_paths_in_out IS NULL THEN
        SELECT sever_paths_in_out_seq.NEXTVAL INTO :NEW.idt_sever_paths_in_out FROM dual;
    END IF;
    IF :NEW.dat_creation IS NULL THEN
        :NEW.dat_creation := SYSDATE;
    END IF;
END;
/

-- Verification
SELECT 'Table SEVER_PATHS_IN_OUT created successfully' AS status FROM dual;
