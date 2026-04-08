-- =====================================================
-- Script: 09_create_customer_identification_rule_table.sql
-- Description: Creates customer_identification_rule table and sequence
-- Requirements: 14.1-14.14
-- =====================================================

-- Drop existing objects if they exist
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE customer_identification_rule CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE customer_identification_rule_seq';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

-- Create sequence for customer_identification_rule
CREATE SEQUENCE customer_identification_rule_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create customer_identification_rule table
CREATE TABLE customer_identification_rule (
    idt_rule NUMBER(19) NOT NULL,
    idt_identification NUMBER(19) NOT NULL,
    des_rule VARCHAR2(255) NOT NULL,
    des_value_origin VARCHAR2(10) NOT NULL,
    des_criteria_type VARCHAR2(15) NOT NULL,
    num_start_position NUMBER(10),
    num_end_position NUMBER(10),
    des_value VARCHAR2(255),
    des_tag VARCHAR2(255),
    des_key VARCHAR2(255),
    des_function_origin VARCHAR2(10),
    des_function_dest VARCHAR2(10),
    dat_creation DATE DEFAULT SYSDATE NOT NULL,
    dat_update DATE,
    nam_change_agent VARCHAR2(50) NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_customer_identification_rule PRIMARY KEY (idt_rule),
    CONSTRAINT fk_customer_identification_rule_identification FOREIGN KEY (idt_identification) 
        REFERENCES customer_identification(idt_identification),
    CONSTRAINT chk_customer_identification_rule_value_origin 
        CHECK (des_value_origin IN ('HEADER', 'TAG', 'FILENAME', 'KEY')),
    CONSTRAINT chk_customer_identification_rule_criteria_type 
        CHECK (des_criteria_type IN ('COMECA_COM', 'TERMINA_COM', 'CONTEM', 'CONTIDO', 'IGUAL')),
    CONSTRAINT chk_customer_identification_rule_function_origin 
        CHECK (des_function_origin IS NULL OR des_function_origin IN ('UPPERCASE', 'LOWERCASE', 'INITCAP', 'TRIM', 'NONE')),
    CONSTRAINT chk_customer_identification_rule_function_dest 
        CHECK (des_function_dest IS NULL OR des_function_dest IN ('UPPERCASE', 'LOWERCASE', 'INITCAP', 'TRIM', 'NONE'))
);

-- Create indexes for performance
CREATE INDEX idx_customer_identification_rule_identification ON customer_identification_rule(idt_identification);
CREATE INDEX idx_customer_identification_rule_active ON customer_identification_rule(flg_active);
CREATE INDEX idx_customer_identification_rule_value_origin ON customer_identification_rule(des_value_origin);

-- Add comments
COMMENT ON TABLE customer_identification_rule IS 'Stores rules for customer identification';
COMMENT ON COLUMN customer_identification_rule.idt_rule IS 'Primary key - Rule ID';
COMMENT ON COLUMN customer_identification_rule.idt_identification IS 'Foreign key to customer_identification';
COMMENT ON COLUMN customer_identification_rule.des_rule IS 'Rule description';
COMMENT ON COLUMN customer_identification_rule.des_value_origin IS 'Value origin: HEADER, TAG, FILENAME, KEY';
COMMENT ON COLUMN customer_identification_rule.des_criteria_type IS 'Criteria type: COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL';
COMMENT ON COLUMN customer_identification_rule.num_start_position IS 'Start position for HEADER (TXT byte offset or CSV column index)';
COMMENT ON COLUMN customer_identification_rule.num_end_position IS 'End position for HEADER TXT (optional)';
COMMENT ON COLUMN customer_identification_rule.des_value IS 'Expected value for comparison';
COMMENT ON COLUMN customer_identification_rule.des_tag IS 'XML tag path (XPath) for TAG type';
COMMENT ON COLUMN customer_identification_rule.des_key IS 'JSON key path (dot notation) for KEY type';
COMMENT ON COLUMN customer_identification_rule.des_function_origin IS 'Transformation function for origin value';
COMMENT ON COLUMN customer_identification_rule.des_function_dest IS 'Transformation function for destination value';
COMMENT ON COLUMN customer_identification_rule.dat_creation IS 'Creation timestamp';
COMMENT ON COLUMN customer_identification_rule.dat_update IS 'Last update timestamp';
COMMENT ON COLUMN customer_identification_rule.nam_change_agent IS 'User/system that made the change';
COMMENT ON COLUMN customer_identification_rule.flg_active IS 'Active flag (1=active, 0=inactive)';

COMMIT;
