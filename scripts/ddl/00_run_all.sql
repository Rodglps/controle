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
PROMPT [1/5] Creating table SERVER...
@@01_create_table_server.sql

PROMPT
PROMPT [2/5] Creating table SEVER_PATHS...
@@02_create_table_sever_paths.sql

PROMPT
PROMPT [3/5] Creating table SEVER_PATHS_IN_OUT...
@@03_create_table_sever_paths_in_out.sql

PROMPT
PROMPT [4/5] Creating table FILE_ORIGIN...
@@04_create_table_file_origin.sql

PROMPT
PROMPT [5/5] Inserting test data...
@@05_insert_test_data.sql

PROMPT
PROMPT ========================================================================
PROMPT DDL execution completed successfully!
PROMPT ========================================================================

-- Display summary
SELECT 'Database Objects Summary:' AS info FROM dual;
SELECT object_type, COUNT(*) as count 
FROM user_objects 
WHERE object_name IN ('SERVER', 'SEVER_PATHS', 'SEVER_PATHS_IN_OUT', 'FILE_ORIGIN',
                      'SERVER_SEQ', 'SEVER_PATHS_SEQ', 'SEVER_PATHS_IN_OUT_SEQ', 'FILE_ORIGIN_SEQ',
                      'SERVER_BIR', 'SEVER_PATHS_BIR', 'SEVER_PATHS_IN_OUT_BIR', 'FILE_ORIGIN_BIR')
GROUP BY object_type
ORDER BY object_type;

EXIT;
