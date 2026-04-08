-- =====================================================
-- Script: 08_create_customer_identification_table.sql
-- Description: Creates customer_identification table and sequence
-- Requirements: 13.1-13.12
-- =====================================================

-- Drop existing objects if they exist
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE customer_identification CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE customer_identification_seq';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

-- Create sequence for customer_identification
CREATE SEQUENCE customer_identification_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create customer_identification table
CREATE TABLE customer_identification (
    idt_identification NUMBER(19) NOT NULL,
    idt_client NUMBER(19) NOT NULL,
    idt_acquirer NUMBER(19) NOT NULL,
    idt_layout NUMBER(19),
    idt_merchant NUMBER(19),
    dat_start DATE,
    dat_end DATE,
    idt_plan NUMBER(19),
    flg_is_priority NUMBER(1),
    num_process_weight NUMBER(10),
    dat_creation DATE DEFAULT SYSDATE NOT NULL,
    dat_update DATE,
    nam_change_agent VARCHAR2(50) NOT NULL,
    flg_active NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_customer_identification PRIMARY KEY (idt_identification),
    CONSTRAINT fk_customer_identification_layout FOREIGN KEY (idt_layout) 
        REFERENCES layout(idt_layout)
);

-- Create indexes for performance
CREATE INDEX idx_customer_identification_acquirer ON customer_identification(idt_acquirer);
CREATE INDEX idx_customer_identification_layout ON customer_identification(idt_layout);
CREATE INDEX idx_customer_identification_client ON customer_identification(idt_client);
CREATE INDEX idx_customer_identification_active ON customer_identification(flg_active);

-- Add comments
COMMENT ON TABLE customer_identification IS 'Stores customer identification configurations for EDI files';
COMMENT ON COLUMN customer_identification.idt_identification IS 'Primary key - Customer identification ID';
COMMENT ON COLUMN customer_identification.idt_client IS 'Client ID (external reference)';
COMMENT ON COLUMN customer_identification.idt_acquirer IS 'Acquirer ID (required)';
COMMENT ON COLUMN customer_identification.idt_layout IS 'Layout ID (optional - null for FILENAME-only rules)';
COMMENT ON COLUMN customer_identification.idt_merchant IS 'Merchant ID (optional)';
COMMENT ON COLUMN customer_identification.dat_start IS 'Start date for identification validity (optional)';
COMMENT ON COLUMN customer_identification.dat_end IS 'End date for identification validity (optional)';
COMMENT ON COLUMN customer_identification.idt_plan IS 'Plan ID (optional)';
COMMENT ON COLUMN customer_identification.flg_is_priority IS 'Priority flag (optional)';
COMMENT ON COLUMN customer_identification.num_process_weight IS 'Processing weight for ordering results (DESC)';
COMMENT ON COLUMN customer_identification.dat_creation IS 'Creation timestamp';
COMMENT ON COLUMN customer_identification.dat_update IS 'Last update timestamp';
COMMENT ON COLUMN customer_identification.nam_change_agent IS 'User/system that made the change';
COMMENT ON COLUMN customer_identification.flg_active IS 'Active flag (1=active, 0=inactive)';

COMMIT;
