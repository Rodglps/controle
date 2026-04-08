-- ============================================================================
-- Script: 06_create_layout_tables.sql
-- Description: Create layout identification tables with sequences and indexes
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-04-04
-- ============================================================================

-- Drop existing objects if they exist (for idempotency)
BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX idx_file_origin_layout';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE file_origin DROP CONSTRAINT fk_file_origin_layout';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX idx_rule_layout_active';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE layout_identification_rule CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP INDEX idx_layout_acquirer_active';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE layout CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE layout_identification_rule_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE layout_seq';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- ============================================================================
-- SEQUENCES
-- ============================================================================

-- Sequence for layout table primary key
CREATE SEQUENCE layout_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence for layout_identification_rule table primary key
CREATE SEQUENCE layout_identification_rule_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- ============================================================================
-- TABLE: layout
-- ============================================================================
-- Stores layout configurations for EDI files from different acquirers.
-- Each layout represents a specific file format/structure from an acquirer.
-- ============================================================================

CREATE TABLE layout (
    idt_layout              NUMBER(19)      NOT NULL,
    cod_layout              VARCHAR2(100)   NOT NULL,
    idt_acquirer            NUMBER(19)      NOT NULL,
    des_version             VARCHAR2(30),
    des_file_type           VARCHAR2(10)    NOT NULL,
    des_column_separator    VARCHAR2(2),
    des_transaction_type    VARCHAR2(20)    NOT NULL,
    des_distribution_type   VARCHAR2(20)    NOT NULL,
    des_encoding            VARCHAR2(10),
    dat_creation            DATE            NOT NULL,
    dat_update              DATE,
    nam_change_agent        VARCHAR2(50)    NOT NULL,
    flg_active              NUMBER(1)       NOT NULL,
    CONSTRAINT pk_layout PRIMARY KEY (idt_layout),
    CONSTRAINT ck_layout_file_type CHECK (des_file_type IN ('CSV', 'TXT', 'JSON', 'OFX', 'XML')),
    CONSTRAINT ck_layout_transaction_type CHECK (des_transaction_type IN ('FINANCEIRO', 'CAPTURA', 'COMPLETO', 'AUXILIAR')),
    CONSTRAINT ck_layout_distribution_type CHECK (des_distribution_type IN ('DIARIO', 'DIAS_UTEIS', 'SEGUNDA_A_SEXTA', 'DOMINGO', 'SEGUNDA_FEIRA', 'TERCA_FEIRA', 'QUARTA_FEIRA', 'QUINTA_FEIRA', 'SEXTA_FEIRA', 'SABADO', 'SAZONAL')),
    CONSTRAINT ck_layout_flg_active CHECK (flg_active IN (0, 1))
);

-- Index for common query pattern: filter by acquirer and active flag
CREATE INDEX idx_layout_acquirer_active ON layout(idt_acquirer, flg_active);

-- ============================================================================
-- TABLE: layout_identification_rule
-- ============================================================================
-- Stores identification rules for each layout.
-- Multiple rules can be defined per layout (AND operator between rules).
-- Rules specify how to extract and compare values from files.
-- ============================================================================

CREATE TABLE layout_identification_rule (
    idt_rule                NUMBER(19)      NOT NULL,
    idt_layout              NUMBER(19)      NOT NULL,
    des_rule                VARCHAR2(255)   NOT NULL,
    des_value_origin        VARCHAR2(10)    NOT NULL,
    des_criteria_type       VARCHAR2(15)    NOT NULL,
    num_start_position      NUMBER(10),
    num_end_position        NUMBER(10),
    des_value               VARCHAR2(255),
    des_tag                 VARCHAR2(255),
    des_key                 VARCHAR2(255),
    des_function_origin     VARCHAR2(10),
    des_function_dest       VARCHAR2(10),
    dat_creation            DATE            NOT NULL,
    dat_update              DATE,
    nam_change_agent        VARCHAR2(50)    NOT NULL,
    flg_active              NUMBER(1)       NOT NULL,
    CONSTRAINT pk_layout_identification_rule PRIMARY KEY (idt_rule),
    CONSTRAINT fk_rule_layout FOREIGN KEY (idt_layout) REFERENCES layout(idt_layout),
    CONSTRAINT ck_rule_value_origin CHECK (des_value_origin IN ('HEADER', 'TAG', 'FILENAME', 'KEY')),
    CONSTRAINT ck_rule_criteria_type CHECK (des_criteria_type IN ('COMECA_COM', 'TERMINA_COM', 'CONTEM', 'CONTIDO', 'IGUAL')),
    CONSTRAINT ck_rule_function_origin CHECK (des_function_origin IN ('UPPERCASE', 'LOWERCASE', 'INITCAP', 'TRIM', 'NONE') OR des_function_origin IS NULL),
    CONSTRAINT ck_rule_function_dest CHECK (des_function_dest IN ('UPPERCASE', 'LOWERCASE', 'INITCAP', 'TRIM', 'NONE') OR des_function_dest IS NULL),
    CONSTRAINT ck_rule_flg_active CHECK (flg_active IN (0, 1))
);

-- Index for common query pattern: filter by layout and active flag
CREATE INDEX idx_rule_layout_active ON layout_identification_rule(idt_layout, flg_active);

-- ============================================================================
-- ALTER TABLE: file_origin
-- ============================================================================
-- Add foreign key constraint from file_origin.idt_layout to layout.idt_layout
-- Note: The idt_layout column already exists in file_origin entity
-- ============================================================================

ALTER TABLE file_origin
    ADD CONSTRAINT fk_file_origin_layout 
    FOREIGN KEY (idt_layout) 
    REFERENCES layout(idt_layout);

-- Index for foreign key to improve join performance
CREATE INDEX idx_file_origin_layout ON file_origin(idt_layout);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE layout IS '[NOT_SECURITY_APPLY] - Stores layout configurations for EDI files from different acquirers';
COMMENT ON COLUMN layout.idt_layout IS '[NOT_SECURITY_APPLY] - Primary key - Layout identifier';
COMMENT ON COLUMN layout.cod_layout IS '[NOT_SECURITY_APPLY] - Layout code/name (e.g., CIELO_V15_VENDA)';
COMMENT ON COLUMN layout.idt_acquirer IS '[NOT_SECURITY_APPLY] - Acquirer identifier (foreign key to acquirer table)';
COMMENT ON COLUMN layout.des_version IS '[NOT_SECURITY_APPLY] - Layout version (e.g., V15, V2.0)';
COMMENT ON COLUMN layout.des_file_type IS '[NOT_SECURITY_APPLY] - File type: CSV, TXT, JSON, OFX, XML';
COMMENT ON COLUMN layout.des_column_separator IS '[NOT_SECURITY_APPLY] - Column separator for CSV files (e.g., ;, |)';
COMMENT ON COLUMN layout.des_transaction_type IS '[NOT_SECURITY_APPLY] - Transaction type: FINANCEIRO, CAPTURA, COMPLETO, AUXILIAR';
COMMENT ON COLUMN layout.des_distribution_type IS '[NOT_SECURITY_APPLY] - Distribution frequency type';
COMMENT ON COLUMN layout.des_encoding IS '[NOT_SECURITY_APPLY] - Expected file encoding (e.g., UTF-8, ISO-8859-1)';
COMMENT ON COLUMN layout.dat_creation IS '[NOT_SECURITY_APPLY] - Record creation date';
COMMENT ON COLUMN layout.dat_update IS '[NOT_SECURITY_APPLY] - Last update date';
COMMENT ON COLUMN layout.nam_change_agent IS '[NOT_SECURITY_APPLY] - User who made the change';
COMMENT ON COLUMN layout.flg_active IS '[NOT_SECURITY_APPLY] - Active flag: 1=active, 0=inactive';

COMMENT ON TABLE layout_identification_rule IS '[NOT_SECURITY_APPLY] - Stores identification rules for layouts - multiple rules per layout with AND operator';
COMMENT ON COLUMN layout_identification_rule.idt_rule IS '[NOT_SECURITY_APPLY] - Primary key - Rule identifier';
COMMENT ON COLUMN layout_identification_rule.idt_layout IS '[NOT_SECURITY_APPLY] - Foreign key to layout table';
COMMENT ON COLUMN layout_identification_rule.des_rule IS '[NOT_SECURITY_APPLY] - Rule description';
COMMENT ON COLUMN layout_identification_rule.des_value_origin IS '[NOT_SECURITY_APPLY] - Value origin: FILENAME, HEADER, TAG, KEY';
COMMENT ON COLUMN layout_identification_rule.des_criteria_type IS '[NOT_SECURITY_APPLY] - Comparison criteria: COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL';
COMMENT ON COLUMN layout_identification_rule.num_start_position IS '[NOT_SECURITY_APPLY] - Start position for HEADER extraction (0-indexed byte offset or column index)';
COMMENT ON COLUMN layout_identification_rule.num_end_position IS '[NOT_SECURITY_APPLY] - End position for HEADER TXT extraction (NULL means until end of line)';
COMMENT ON COLUMN layout_identification_rule.des_value IS '[NOT_SECURITY_APPLY] - Expected value for comparison';
COMMENT ON COLUMN layout_identification_rule.des_tag IS '[NOT_SECURITY_APPLY] - XPath for TAG extraction in XML files';
COMMENT ON COLUMN layout_identification_rule.des_key IS '[NOT_SECURITY_APPLY] - JSON path for KEY extraction in JSON files';
COMMENT ON COLUMN layout_identification_rule.des_function_origin IS '[NOT_SECURITY_APPLY] - Transformation function for origin value: UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE';
COMMENT ON COLUMN layout_identification_rule.des_function_dest IS '[NOT_SECURITY_APPLY] - Transformation function for expected value: UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE';
COMMENT ON COLUMN layout_identification_rule.dat_creation IS '[NOT_SECURITY_APPLY] - Record creation date';
COMMENT ON COLUMN layout_identification_rule.dat_update IS '[NOT_SECURITY_APPLY] - Last update date';
COMMENT ON COLUMN layout_identification_rule.nam_change_agent IS '[NOT_SECURITY_APPLY] - User who made the change';
COMMENT ON COLUMN layout_identification_rule.flg_active IS '[NOT_SECURITY_APPLY] - Active flag: 1=active, 0=inactive';

-- Verification
SELECT 'Tables LAYOUT and LAYOUT_IDENTIFICATION_RULE created successfully' AS status FROM dual;
