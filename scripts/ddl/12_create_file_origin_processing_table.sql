-- =====================================================
-- Script: 12_create_file_origin_processing_table.sql
-- Description: Creates file_origin_processing table and sequence
-- Requirements: 1.1, 1.2, 1.3, 1.4, 1.5
-- =====================================================

-- Drop existing objects if they exist
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE file_origin_processing CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE file_origin_processing_seq';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

-- Create sequence for file_origin_processing
CREATE SEQUENCE file_origin_processing_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create file_origin_processing table
CREATE TABLE file_origin_processing (
    idt_file_origin_processing NUMBER(19) NOT NULL,
    idt_file_origin NUMBER(19) NOT NULL,
    des_step VARCHAR2(50) NOT NULL,
    des_status VARCHAR2(50) NOT NULL,
    idt_client NUMBER(20) NULL,
    des_message_error VARCHAR2(4000) NULL,
    des_message_alert VARCHAR2(4000) NULL,
    dat_step_start DATE NULL,
    dat_step_end DATE NULL,
    jsn_additional_info VARCHAR2(4000) NULL,
    dat_creation DATE DEFAULT SYSDATE NOT NULL,
    dat_update DATE NULL,
    nam_change_agent VARCHAR2(50) NOT NULL,
    CONSTRAINT pk_file_origin_processing PRIMARY KEY (idt_file_origin_processing),
    CONSTRAINT fk_fop_file_origin FOREIGN KEY (idt_file_origin)
        REFERENCES file_origin(idt_file_origin)
);

-- Create indexes for performance
CREATE INDEX idx_fop_file_origin ON file_origin_processing(idt_file_origin);
CREATE INDEX idx_fop_client ON file_origin_processing(idt_client);

-- Add comments
COMMENT ON TABLE file_origin_processing IS 'Tracks processing state per file per client per step in the EDI pipeline';
COMMENT ON COLUMN file_origin_processing.idt_file_origin_processing IS 'Primary key - Processing record ID';
COMMENT ON COLUMN file_origin_processing.idt_file_origin IS 'Foreign key to file_origin';
COMMENT ON COLUMN file_origin_processing.des_step IS 'Pipeline step (COLETA, DELETE, RAW, STAGING, ORDINATION, PROCESSING, PROCESSED)';
COMMENT ON COLUMN file_origin_processing.des_status IS 'Status within the step (EM_ESPERA, PROCESSAMENTO, CONCLUIDO, ERRO)';
COMMENT ON COLUMN file_origin_processing.idt_client IS 'Client ID - NULL when no client identified';
COMMENT ON COLUMN file_origin_processing.des_message_error IS 'Error message for the step, if applicable';
COMMENT ON COLUMN file_origin_processing.des_message_alert IS 'Alert message for the step, if applicable';
COMMENT ON COLUMN file_origin_processing.dat_step_start IS 'Step start timestamp';
COMMENT ON COLUMN file_origin_processing.dat_step_end IS 'Step end timestamp';
COMMENT ON COLUMN file_origin_processing.jsn_additional_info IS 'Additional JSON info about the step';
COMMENT ON COLUMN file_origin_processing.dat_creation IS 'Record creation date';
COMMENT ON COLUMN file_origin_processing.dat_update IS 'Last update date';
COMMENT ON COLUMN file_origin_processing.nam_change_agent IS 'Name of the agent that applied the last change';

COMMIT;
