-- =====================================================
-- Script: 10_create_file_origin_clients_table.sql
-- Description: Creates file_origin_clients table and sequence
-- Requirements: 15.1-15.7
-- =====================================================

-- Drop existing objects if they exist
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE file_origin_clients CASCADE CONSTRAINTS';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE file_origin_clients_seq';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -2289 THEN
            RAISE;
        END IF;
END;
/

-- Create sequence for file_origin_clients
CREATE SEQUENCE file_origin_clients_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create file_origin_clients table
CREATE TABLE file_origin_clients (
    idt_client_identified NUMBER(19) NOT NULL,
    idt_file_origin NUMBER(19) NOT NULL,
    idt_client NUMBER(19) NOT NULL,
    dat_creation TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    dat_update TIMESTAMP,
    CONSTRAINT pk_file_origin_clients PRIMARY KEY (idt_client_identified),
    CONSTRAINT fk_file_origin_clients_file_origin FOREIGN KEY (idt_file_origin) 
        REFERENCES file_origin(idt_file_origin),
    CONSTRAINT uk_file_origin_clients_file_client UNIQUE (idt_file_origin, idt_client)
);

-- Create indexes for performance
CREATE INDEX idx_file_origin_clients_file_origin ON file_origin_clients(idt_file_origin);
CREATE INDEX idx_file_origin_clients_client ON file_origin_clients(idt_client);

-- Add comments
COMMENT ON TABLE file_origin_clients IS 'Stores identified customers for each file';
COMMENT ON COLUMN file_origin_clients.idt_client_identified IS 'Primary key - Client identification record ID';
COMMENT ON COLUMN file_origin_clients.idt_file_origin IS 'Foreign key to file_origin';
COMMENT ON COLUMN file_origin_clients.idt_client IS 'Client ID that owns the file';
COMMENT ON COLUMN file_origin_clients.dat_creation IS 'Creation timestamp';
COMMENT ON COLUMN file_origin_clients.dat_update IS 'Last update timestamp';

COMMIT;
