-- ============================================================================
-- Script: 04-create-constraints.sql
-- Descrição: Criação de constraints de chave estrangeira
-- Sistema: Controle de Arquivos
-- ============================================================================

-- ============================================================================
-- Constraints para sever_paths
-- ============================================================================
ALTER TABLE sever_paths
    ADD CONSTRAINT fk_sever_paths_server
    FOREIGN KEY (idt_server)
    REFERENCES server(idt_server);

-- ============================================================================
-- Constraints para sever_paths_in_out
-- ============================================================================
ALTER TABLE sever_paths_in_out
    ADD CONSTRAINT fk_sever_paths_in_out_origin
    FOREIGN KEY (idt_sever_path_origin)
    REFERENCES sever_paths(idt_sever_path);

ALTER TABLE sever_paths_in_out
    ADD CONSTRAINT fk_sever_paths_in_out_dest
    FOREIGN KEY (idt_sever_destination)
    REFERENCES server(idt_server);

-- ============================================================================
-- Constraints para layout_identification_rule
-- ============================================================================
ALTER TABLE layout_identification_rule
    ADD CONSTRAINT fk_layout_rule_layout
    FOREIGN KEY (idt_layout)
    REFERENCES layout(idt_layout);

-- ============================================================================
-- Constraints para customer_identification_rule
-- ============================================================================
ALTER TABLE customer_identification_rule
    ADD CONSTRAINT fk_customer_rule_customer
    FOREIGN KEY (idt_customer_identification)
    REFERENCES customer_identification(idt_customer_identification);

-- ============================================================================
-- Constraints para file_origin
-- ============================================================================
ALTER TABLE file_origin
    ADD CONSTRAINT fk_file_origin_layout
    FOREIGN KEY (idt_layout)
    REFERENCES layout(idt_layout);

ALTER TABLE file_origin
    ADD CONSTRAINT fk_file_origin_sever_paths
    FOREIGN KEY (idt_sever_paths_in_out)
    REFERENCES sever_paths_in_out(idt_sever_paths_in_out);

-- ============================================================================
-- Constraints para file_origin_client
-- ============================================================================
ALTER TABLE file_origin_client
    ADD CONSTRAINT fk_file_origin_client_file
    FOREIGN KEY (idt_file_origin)
    REFERENCES file_origin(idt_file_origin);

ALTER TABLE file_origin_client
    ADD CONSTRAINT fk_file_origin_client_customer
    FOREIGN KEY (idt_client)
    REFERENCES customer_identification(idt_customer_identification);

-- ============================================================================
-- Constraints para file_origin_client_processing
-- ============================================================================
ALTER TABLE file_origin_client_processing
    ADD CONSTRAINT fk_file_origin_proc_client
    FOREIGN KEY (idt_file_origin_client)
    REFERENCES file_origin_client(idt_file_origin_client);

COMMIT;

EXIT;
