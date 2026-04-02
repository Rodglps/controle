-- ============================================================================
-- Script: 04_create_table_file_origin.sql
-- Description: Create table FILE_ORIGIN with sequences, triggers and indexes
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-03-24
-- ============================================================================

-- Drop existing objects if they exist (for idempotency)
BEGIN
    EXECUTE IMMEDIATE 'DROP TRIGGER file_origin_bir';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE file_origin CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE file_origin_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- Create sequence for auto-increment
CREATE SEQUENCE file_origin_seq 
    START WITH 1 
    INCREMENT BY 1 
    NOCACHE 
    NOCYCLE;

-- Create table FILE_ORIGIN
CREATE TABLE file_origin (
    idt_file_origin NUMBER(19) PRIMARY KEY,
    idt_acquirer NUMBER(19),
    idt_layout NUMBER(19),
    des_file_name VARCHAR2(255) NOT NULL,
    num_file_size NUMBER(19),
    des_file_mime_type VARCHAR2(100),
    des_file_type VARCHAR2(50) CHECK (des_file_type IN ('csv', 'json', 'txt', 'xml')),
    des_step VARCHAR2(50) NOT NULL CHECK (des_step IN ('COLETA', 'DELETE', 'RAW', 'STAGING', 'ORDINATION', 'PROCESSING', 'PROCESSED')),
    des_status VARCHAR2(50) CHECK (des_status IN ('EM_ESPERA', 'PROCESSAMENTO', 'CONCLUIDO', 'ERRO')),
    des_message_error VARCHAR2(4000),
    des_message_alert VARCHAR2(4000),
    des_transaction_type VARCHAR2(100) NOT NULL CHECK (des_transaction_type IN ('COMPLETO', 'CAPTURA', 'FINANCEIRO')),
    dat_timestamp_file TIMESTAMP NOT NULL,
    idt_sever_paths_in_out NUMBER(19) NOT NULL,
    dat_creation DATE NOT NULL,
    dat_update DATE,
    nam_change_agent VARCHAR2(50),
    flg_active NUMBER(1) NOT NULL CHECK (flg_active IN (0, 1)),
    num_retry NUMBER(1) NOT NULL,
    max_retry NUMBER(1) NOT NULL,
    CONSTRAINT fk_file_origin_spio FOREIGN KEY (idt_sever_paths_in_out) REFERENCES sever_paths_in_out(idt_sever_paths_in_out),
    CONSTRAINT file_origin_idx_01 UNIQUE (des_file_name, idt_acquirer, dat_timestamp_file, flg_active)
);

-- Add comments on table
COMMENT ON TABLE file_origin IS '[NOT_SECURITY_APPLY] - Tabelas com dados relacionados ao arquivo';

-- Add comments on columns
COMMENT ON COLUMN file_origin.idt_file_origin IS '[NOT_SECURITY_APPLY] - Identificador interno do arquivo ex: 1 | 2 | 3';
COMMENT ON COLUMN file_origin.idt_acquirer IS '[NOT_SECURITY_APPLY] - Identificador interno da adquirente ex: 1 | 2 | 3';
COMMENT ON COLUMN file_origin.idt_layout IS '[NOT_SECURITY_APPLY] - Identificador interno do layout ex: 1 | 2 | 3';
COMMENT ON COLUMN file_origin.des_file_name IS '[NOT_SECURITY_APPLY] - Nome do arquivo recebido';
COMMENT ON COLUMN file_origin.num_file_size IS '[NOT_SECURITY_APPLY] - Tamanho do arquivo recebido, em bytes';
COMMENT ON COLUMN file_origin.des_file_mime_type IS '[NOT_SECURITY_APPLY] - Tipo mime do arquivo recebido, ex: text/csv | application/json';
COMMENT ON COLUMN file_origin.des_file_type IS '[NOT_SECURITY_APPLY] - Tipo do arquivo recebido, ex: csv | json | txt | xml';
COMMENT ON COLUMN file_origin.des_step IS '[NOT_SECURITY_APPLY] - Descrição do passo realizado no arquivo, ex: LEITURA| COLETA | PROCESSAMENTO';
COMMENT ON COLUMN file_origin.des_status IS '[NOT_SECURITY_APPLY] - Status do passo realizado no arquivo, ex: EM_EXECUCAO | CONCLUIDO | ERRO';
COMMENT ON COLUMN file_origin.des_message_error IS '[NOT_SECURITY_APPLY] - Mensagem de erro relacionada ao passo, caso haja necessidade de registrar algum erro específico para aquele passo';
COMMENT ON COLUMN file_origin.des_message_alert IS '[NOT_SECURITY_APPLY] - Mensagem de alerta relacionada ao passo, caso haja necessidade de registrar algum alerta específico para aquele passo';
COMMENT ON COLUMN file_origin.des_transaction_type IS '[NOT_SECURITY_APPLY] - Tipo de transação que o arquivo contem, ex: COMPLETO | CAPTURA| FINANCEIRO';
COMMENT ON COLUMN file_origin.dat_timestamp_file IS '[NOT_SECURITY_APPLY] - Data e hora presente no arquivo, pode ser data de criação do arquivo, no caso de sftp data e hora da criação do arquivo no servidor, no caso de api a data e hora do consumo da api';
COMMENT ON COLUMN file_origin.idt_sever_paths_in_out IS '[NOT_SECURITY_APPLY] - Identificador interno do registro, popular apenas se for o PRINCIPAL ex: 1 | 2 | 3';
COMMENT ON COLUMN file_origin.dat_creation IS '[NOT_SECURITY_APPLY] - Data e hora da geração do registro';
COMMENT ON COLUMN file_origin.dat_update IS '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro';
COMMENT ON COLUMN file_origin.nam_change_agent IS '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a alteração';
COMMENT ON COLUMN file_origin.flg_active IS '[NOT_SECURITY_APPLY] - Flag que indica se o registro esta ativo, inativo ou em carregamento, valores 0 = INATIVO | 1 = ATIVO';
COMMENT ON COLUMN file_origin.num_retry IS '[NOT_SECURITY_APPLY] - identifica o numero de tentativas feitas para este arquivo';
COMMENT ON COLUMN file_origin.max_retry IS '[NOT_SECURITY_APPLY] - máximo de tentativas que devem ser feitas antes de desistir';

-- Create trigger for auto-increment and dat_creation
CREATE OR REPLACE TRIGGER file_origin_bir
BEFORE INSERT ON file_origin
FOR EACH ROW
BEGIN
    IF :NEW.idt_file_origin IS NULL THEN
        SELECT file_origin_seq.NEXTVAL INTO :NEW.idt_file_origin FROM dual;
    END IF;
    IF :NEW.dat_creation IS NULL THEN
        :NEW.dat_creation := SYSDATE;
    END IF;
END;
/

-- Verification
SELECT 'Table FILE_ORIGIN created successfully' AS status FROM dual;
