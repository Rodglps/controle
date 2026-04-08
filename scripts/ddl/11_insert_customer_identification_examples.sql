-- =====================================================
-- Script: 11_insert_customer_identification_examples.sql
-- Description: Inserts example data for customer identification test scenarios
-- Requirements: Prompt - Cenários de Teste
-- =====================================================

-- =====================================================
-- Teste 1: Identificação por FILENAME - Múltiplos Clientes
-- Objetivo: Validar que um arquivo pode ser identificado por múltiplos clientes simultaneamente
-- Arquivo de teste: cielo_1234567890_premium_20250101_venda.txt
-- Resultado esperado: Clientes 15 e 20 identificados (ordenados por num_process_weight DESC)
-- =====================================================

-- Cliente 1 (idt_client=15) - CIELO
INSERT INTO customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES 
(customer_identification_seq.NEXTVAL, 15, 1, NULL, 1515, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 100, SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação pelo filename para arquivo CIELO - Cliente 15', 'FILENAME', 'CONTEM', NULL, NULL, '1234567890', NULL, NULL, 'NONE', 'NONE', SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação pelo filename para arquivo CIELO - Cliente 15', 'FILENAME', 'COMECA_COM', NULL, NULL, 'cielo', NULL, NULL, 'LOWERCASE', 'LOWERCASE', SYSDATE, NULL, 'SETUP', 1);

-- Cliente 2 (idt_client=20) - CIELO (mesmo adquirente, cliente diferente, maior peso)
INSERT INTO customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES 
(customer_identification_seq.NEXTVAL, 20, 1, NULL, 1520, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 1, 200, SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação pelo filename para arquivo CIELO - Cliente 20', 'FILENAME', 'CONTEM', NULL, NULL, 'premium', NULL, NULL, 'LOWERCASE', 'LOWERCASE', SYSDATE, NULL, 'SETUP', 1);

-- =====================================================
-- Teste 2: Identificação por FILENAME - Nenhum Cliente Identificado
-- Objetivo: Validar que o processamento continua quando nenhum cliente é identificado
-- Arquivo de teste: rede_9999999999_standard_20250101.txt
-- Resultado esperado: Nenhum cliente identificado, processamento continua normalmente
-- Nota: Usa as mesmas configurações do Teste 1, mas o arquivo não corresponde às regras
-- =====================================================

-- =====================================================
-- Teste 3: Identificação por HEADER - Arquivo TXT com Layout Identificado
-- Objetivo: Validar identificação por byte offset em arquivo TXT
-- Pré-requisito: Layout CIELO_015_03_VENDA (idt_layout=1) deve estar identificado
-- Arquivo de teste: TXT com primeira linha "VENDA     1525      20250101..."
-- Resultado esperado: Cliente 25 identificado
-- =====================================================

-- Cliente 3 (idt_client=25) - CIELO com identificação por HEADER em TXT
INSERT INTO customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES 
(customer_identification_seq.NEXTVAL, 25, 1, 1, 1525, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 150, SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação por HEADER TXT - posições 1-5 devem ser VENDA', 'HEADER', 'IGUAL', 1, 5, 'VENDA', NULL, NULL, 'TRIM', 'TRIM', SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação por HEADER TXT - posições 11-20 devem conter merchant', 'HEADER', 'CONTEM', 11, 20, '1525', NULL, NULL, 'NONE', 'NONE', SYSDATE, NULL, 'SETUP', 1);

-- =====================================================
-- Teste 4: Identificação por HEADER - Arquivo CSV com Layout Identificado
-- Objetivo: Validar identificação por índice de coluna em arquivo CSV
-- Pré-requisito: Layout REDE_EEVD_CSV (idt_layout=3) deve estar identificado
-- Arquivo de teste: CSV com header "00,123,1530,789,Movimentação diária...,REDECARD,...,V2.00 - 05/2023 EEVD"
-- Resultado esperado: Cliente 30 identificado (coluna 9 CONTEM 'EEVD' e coluna 2 CONTEM '1530')
-- =====================================================

-- Cliente 4 (idt_client=30) - REDE com identificação por HEADER em CSV
INSERT INTO customer_identification 
(idt_identification, idt_client, idt_acquirer, idt_layout, idt_merchant, dat_start, dat_end, idt_plan, flg_is_priority, num_process_weight, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES 
(customer_identification_seq.NEXTVAL, 30, 1, 3, 1530, TO_DATE('01012025','ddmmyyyy'), NULL, NULL, 0, 180, SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação por HEADER CSV - coluna 9 deve conter EEVD', 'HEADER', 'CONTEM', 9, NULL, 'EEVD', NULL, NULL, 'UPPERCASE', 'UPPERCASE', SYSDATE, NULL, 'SETUP', 1);

INSERT INTO customer_identification_rule 
(idt_rule, idt_identification, des_rule, des_value_origin, des_criteria_type, num_start_position, num_end_position, des_value, des_tag, des_key, des_function_origin, des_function_dest, dat_creation, dat_update, nam_change_agent, flg_active)
VALUES
(customer_identification_rule_seq.NEXTVAL, customer_identification_seq.CURRVAL, 'Identificação por HEADER CSV - coluna 2 deve conter merchant', 'HEADER', 'CONTEM', 2, NULL, '1530', NULL, NULL, 'NONE', 'NONE', SYSDATE, NULL, 'SETUP', 1);

COMMIT;

-- =====================================================
-- Verification queries (commented out - for manual testing)
-- =====================================================

-- SELECT * FROM customer_identification ORDER BY idt_identification;
-- SELECT * FROM customer_identification_rule ORDER BY idt_rule;
-- 
-- -- Verify Teste 1 configuration (Cliente 15 and 20)
-- SELECT ci.idt_client, ci.num_process_weight, cir.des_rule, cir.des_value
-- FROM customer_identification ci
-- JOIN customer_identification_rule cir ON ci.idt_identification = cir.idt_identification
-- WHERE ci.idt_client IN (15, 20)
-- ORDER BY ci.num_process_weight DESC, cir.idt_rule;
-- 
-- -- Verify Teste 3 configuration (Cliente 25)
-- SELECT ci.idt_client, ci.idt_layout, cir.des_rule, cir.des_value_origin, cir.num_start_position, cir.num_end_position
-- FROM customer_identification ci
-- JOIN customer_identification_rule cir ON ci.idt_identification = cir.idt_identification
-- WHERE ci.idt_client = 25
-- ORDER BY cir.idt_rule;
-- 
-- -- Verify Teste 4 configuration (Cliente 30)
-- SELECT ci.idt_client, ci.idt_layout, cir.des_rule, cir.des_value_origin, cir.num_start_position
-- FROM customer_identification ci
-- JOIN customer_identification_rule cir ON ci.idt_identification = cir.idt_identification
-- WHERE ci.idt_client = 30
-- ORDER BY cir.idt_rule;
