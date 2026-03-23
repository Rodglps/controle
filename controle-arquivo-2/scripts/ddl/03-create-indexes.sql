-- ============================================================================
-- Script: 03-create-indexes.sql
-- Descrição: Criação de índices para otimização de consultas
-- Sistema: Controle de Arquivos
-- ============================================================================

-- ============================================================================
-- Índices para job_concurrency_control
-- ============================================================================
CREATE INDEX idx_job_concurrency_job_name ON job_concurrency_control(des_job_name, flg_active);
CREATE INDEX idx_job_concurrency_status ON job_concurrency_control(des_status, flg_active);

-- ============================================================================
-- Índices para server
-- ============================================================================
CREATE INDEX idx_server_cod_server ON server(cod_server, flg_active);
CREATE INDEX idx_server_type ON server(des_server_type, flg_active);

-- ============================================================================
-- Índices para sever_paths
-- ============================================================================
CREATE INDEX idx_sever_paths_server ON sever_paths(idt_server, flg_active);
CREATE INDEX idx_sever_paths_acquirer ON sever_paths(idt_acquirer, flg_active);
CREATE INDEX idx_sever_paths_type ON sever_paths(des_path_type, flg_active);

-- ============================================================================
-- Índices para sever_paths_in_out
-- ============================================================================
CREATE INDEX idx_sever_paths_in_out_origin ON sever_paths_in_out(idt_sever_path_origin, flg_active);
CREATE INDEX idx_sever_paths_in_out_dest ON sever_paths_in_out(idt_sever_destination, flg_active);

-- ============================================================================
-- Índices para layout
-- ============================================================================
CREATE INDEX idx_layout_name ON layout(des_layout_name, flg_active);
CREATE INDEX idx_layout_type ON layout(des_layout_type, flg_active);

-- ============================================================================
-- Índices para layout_identification_rule
-- ============================================================================
CREATE INDEX idx_layout_rule_layout ON layout_identification_rule(idt_layout, flg_active);
CREATE INDEX idx_layout_rule_client ON layout_identification_rule(idt_client, flg_active);
CREATE INDEX idx_layout_rule_acquirer ON layout_identification_rule(idt_acquirer, flg_active);
CREATE INDEX idx_layout_rule_client_acq ON layout_identification_rule(idt_client, idt_acquirer, flg_active);

-- ============================================================================
-- Índices para customer_identification
-- ============================================================================
CREATE INDEX idx_customer_name ON customer_identification(des_customer_name, flg_active);
CREATE INDEX idx_customer_weight ON customer_identification(num_processing_weight DESC, flg_active);

-- ============================================================================
-- Índices para customer_identification_rule
-- ============================================================================
CREATE INDEX idx_customer_rule_customer ON customer_identification_rule(idt_customer_identification, flg_active);
CREATE INDEX idx_customer_rule_acquirer ON customer_identification_rule(idt_acquirer, flg_active);

-- ============================================================================
-- Índices para file_origin
-- ============================================================================
-- Índice único para garantir que não haja duplicatas de arquivos
CREATE UNIQUE INDEX idx_file_origin_unique ON file_origin(des_file_name, idt_acquirer, dat_timestamp_file, flg_active);

-- Índices para busca por configuração e adquirente
CREATE INDEX idx_file_origin_sever_paths ON file_origin(idt_sever_paths_in_out, flg_active);
CREATE INDEX idx_file_origin_acquirer ON file_origin(idt_acquirer, flg_active);
CREATE INDEX idx_file_origin_layout ON file_origin(idt_layout, flg_active);

-- Índice para busca por data de criação (útil para consultas temporais)
CREATE INDEX idx_file_origin_created ON file_origin(dat_created DESC);
CREATE INDEX idx_file_origin_timestamp ON file_origin(dat_timestamp_file DESC);

-- ============================================================================
-- Índices para file_origin_client
-- ============================================================================
CREATE INDEX idx_file_origin_client_file ON file_origin_client(idt_file_origin, flg_active);
CREATE INDEX idx_file_origin_client_client ON file_origin_client(idt_client, flg_active);

-- ============================================================================
-- Índices para file_origin_client_processing
-- ============================================================================
-- Índice principal para rastreabilidade por arquivo-cliente
CREATE INDEX idx_file_origin_proc_client ON file_origin_client_processing(idt_file_origin_client, flg_active);

-- Índices para busca por etapa e status
CREATE INDEX idx_file_origin_proc_step ON file_origin_client_processing(des_step, flg_active);
CREATE INDEX idx_file_origin_proc_status ON file_origin_client_processing(des_status, flg_active);
CREATE INDEX idx_file_origin_proc_step_status ON file_origin_client_processing(des_step, des_status, flg_active);

-- Índice para busca por data de criação (útil para monitoramento)
CREATE INDEX idx_file_origin_proc_created ON file_origin_client_processing(dat_created DESC);

COMMIT;

EXIT;
