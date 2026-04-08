-- ============================================================================
-- Script: 00_run_all.sql
-- Description: Master script to execute all DDL scripts in order
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-03-24
-- Usage: Executed automatically on container startup
-- ============================================================================

-- Connect to the pluggable database and switch to APP_USER schema
ALTER SESSION SET CONTAINER = XEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = edi_user;

SET ECHO ON
SET FEEDBACK ON
SET SERVEROUTPUT ON

PROMPT ========================================================================
PROMPT Starting DDL execution for Controle de Arquivos EDI
PROMPT ========================================================================

PROMPT
PROMPT [1/12] Creating table SERVER...
@@01_create_table_server.sql

PROMPT
PROMPT [2/12] Creating table SEVER_PATHS...
@@02_create_table_sever_paths.sql

PROMPT
PROMPT [3/12] Creating table SEVER_PATHS_IN_OUT...
@@03_create_table_sever_paths_in_out.sql

PROMPT
PROMPT [4/12] Creating table FILE_ORIGIN...
@@04_create_table_file_origin.sql

PROMPT
PROMPT [5/12] Inserting test data...
@@05_insert_test_data.sql

PROMPT
PROMPT [6/12] Creating layout identification tables...
@@06_create_layout_tables.sql

PROMPT
PROMPT [7/12] Inserting layout examples...
@@07_insert_layout_examples.sql

PROMPT
PROMPT [8/12] Creating table CUSTOMER_IDENTIFICATION...
@@08_create_customer_identification_table.sql

PROMPT
PROMPT [9/12] Creating table CUSTOMER_IDENTIFICATION_RULE...
@@09_create_customer_identification_rule_table.sql

PROMPT
PROMPT [10/12] Creating table FILE_ORIGIN_CLIENTS...
@@10_create_file_origin_clients_table.sql

PROMPT
PROMPT [11/12] Inserting customer identification examples...
@@11_insert_customer_identification_examples.sql

PROMPT
PROMPT [12/12] Creating table FILE_ORIGIN_PROCESSING...
@@12_create_file_origin_processing_table.sql

PROMPT
PROMPT ========================================================================
PROMPT DDL execution completed successfully!
PROMPT ========================================================================

-- Display summary
SELECT 'Database Objects Summary:' AS info FROM dual;
SELECT object_type, COUNT(*) as count 
FROM user_objects 
WHERE object_name IN ('SERVER', 'SEVER_PATHS', 'SEVER_PATHS_IN_OUT', 'FILE_ORIGIN',
                      'LAYOUT', 'LAYOUT_IDENTIFICATION_RULE',
                      'CUSTOMER_IDENTIFICATION', 'CUSTOMER_IDENTIFICATION_RULE', 'FILE_ORIGIN_CLIENTS',
                      'FILE_ORIGIN_PROCESSING',
                      'SERVER_SEQ', 'SEVER_PATHS_SEQ', 'SEVER_PATHS_IN_OUT_SEQ', 'FILE_ORIGIN_SEQ',
                      'LAYOUT_SEQ', 'LAYOUT_IDENTIFICATION_RULE_SEQ',
                      'CUSTOMER_IDENTIFICATION_SEQ', 'CUSTOMER_IDENTIFICATION_RULE_SEQ', 'FILE_ORIGIN_CLIENTS_SEQ',
                      'FILE_ORIGIN_PROCESSING_SEQ',
                      'SERVER_BIR', 'SEVER_PATHS_BIR', 'SEVER_PATHS_IN_OUT_BIR', 'FILE_ORIGIN_BIR')
GROUP BY object_type
ORDER BY object_type;

EXIT;
