-- ============================================================================
-- Script: 01-create-sequences.sql
-- Descrição: Criação de sequences para geração de IDs primários
-- Sistema: Controle de Arquivos
-- ============================================================================

-- Sequence para job_concurrency_control
CREATE SEQUENCE seq_job_concurrency_control
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para server
CREATE SEQUENCE seq_server
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para sever_paths
CREATE SEQUENCE seq_sever_paths
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para sever_paths_in_out
CREATE SEQUENCE seq_sever_paths_in_out
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para layout
CREATE SEQUENCE seq_layout
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para layout_identification_rule
CREATE SEQUENCE seq_layout_identification_rule
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para customer_identification
CREATE SEQUENCE seq_customer_identification
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para customer_identification_rule
CREATE SEQUENCE seq_customer_identification_rule
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para file_origin
CREATE SEQUENCE seq_file_origin
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para file_origin_client
CREATE SEQUENCE seq_file_origin_client
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence para file_origin_client_processing
CREATE SEQUENCE seq_file_origin_client_processing
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

COMMIT;

EXIT;
