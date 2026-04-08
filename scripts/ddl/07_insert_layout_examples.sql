-- ============================================================================
-- Script: 07_insert_layout_examples.sql
-- Description: Insert example layout data for EDI file identification
-- Author: Controle de Arquivos EDI Team
-- Date: 2026-04-04
-- ============================================================================

-- Delete existing data if present (for idempotency)
DELETE FROM layout_identification_rule WHERE idt_layout IN (0, 1, 2, 3, 4, 5);
DELETE FROM layout WHERE idt_layout IN (0, 1, 2, 3, 4, 5);

-- ============================================================================
-- Layout 0: SEM_IDENTIFICACAO (Special layout for unidentified files)
-- This layout is used when no identification rules match the file
-- Files with this layout are still transferred successfully (status CONCLUIDO)
-- but marked as unidentified for manual review
-- ============================================================================

INSERT INTO layout (
    idt_layout, cod_layout, idt_acquirer, des_version, des_file_type,
    des_column_separator, des_transaction_type, des_distribution_type,
    des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
)
VALUES (
    0, 'SEM_IDENTIFICACAO', 0, NULL, 'TXT',
    NULL, 'AUXILIAR', 'SAZONAL',
    NULL, SYSDATE, NULL, 'SYSTEM', 1
);

-- ============================================================================
-- Layout 1: CIELO VENDAS Versão 15
-- Identificação: FILENAME com 3 regras AND (termina com 'venda', começa com 'cielo', contém 'v15')
-- Exemplo de arquivo: cielo_v15_venda.txt
-- ============================================================================

INSERT INTO layout (
    idt_layout, cod_layout, idt_acquirer, des_version, des_file_type,
    des_column_separator, des_transaction_type, des_distribution_type,
    des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
)
VALUES (
    1, 'CIELO_015_03_VENDA', 1, '015', 'TXT',
    NULL, 'CAPTURA', 'DIARIO',
    'utf-8', SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    11, 1, 'Arquivo de captura', 'FILENAME', 'TERMINA_COM',
    NULL, NULL, 'venda', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    12, 1, 'Adquirente', 'FILENAME', 'COMECA_COM',
    NULL, NULL, 'cielo', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    13, 1, 'Versão 15', 'FILENAME', 'CONTEM',
    NULL, NULL, 'v15', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

-- ============================================================================
-- Layout 2: CIELO FINANCEIRO Versão 15
-- Identificação: FILENAME com 3 regras AND (termina com 'pagto', começa com 'cielo', contém 'v15')
-- Exemplo de arquivo: cielo_v15_pagto.txt
-- ============================================================================

INSERT INTO layout (
    idt_layout, cod_layout, idt_acquirer, des_version, des_file_type,
    des_column_separator, des_transaction_type, des_distribution_type,
    des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
)
VALUES (
    2, 'CIELO_015_04_PAGTO', 1, '015', 'TXT',
    NULL, 'FINANCEIRO', 'DIARIO',
    'utf-8', SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    21, 2, 'Arquivo de captura', 'FILENAME', 'TERMINA_COM',
    NULL, NULL, 'pagto', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    22, 2, 'Adquirente', 'FILENAME', 'COMECA_COM',
    NULL, NULL, 'cielo', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    23, 2, 'Versão 15', 'FILENAME', 'CONTEM',
    NULL, NULL, 'v15', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

-- ============================================================================
-- Layout 3: REDE EEVD Versão 02 (CSV)
-- Identificação: HEADER CSV com 4 regras AND
-- Exemplo de cabeçalho CSV: 00,123,456,789,REDECARD,Movimentação diária - Cartões de débito,20231201,20231231,20240101,V2.00 - 05/2023 EEVD
-- ============================================================================

INSERT INTO layout (
    idt_layout, cod_layout, idt_acquirer, des_version, des_file_type,
    des_column_separator, des_transaction_type, des_distribution_type,
    des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
)
VALUES (
    3, 'REDE_EEVD_02', 1, 'V2.00', 'CSV',
    ',', 'COMPLETO', 'DIARIO',
    'utf-8', SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    31, 3, 'Tipo de Registro', 'HEADER', 'IGUAL',
    0, NULL, '00', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    32, 3, 'Nome do arquirente', 'HEADER', 'IGUAL',
    5, NULL, 'REDECARD', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    33, 3, 'Descritivo do tipo de movimentação', 'HEADER', 'IGUAL',
    4, NULL, 'Movimentação diária - Cartões de débito', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    34, 3, 'Versão do arquivo', 'HEADER', 'IGUAL',
    9, NULL, 'V2.00 - 05/2023 EEVD', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

-- ============================================================================
-- Layout 4: REDE EEVC Versão 03 (TXT)
-- Identificação: HEADER TXT com 4 regras AND (byte offset, 1-based)
-- Posições: primeiro caractere = posição 1
-- Exemplo de cabeçalho TXT: 002        REDECARD  EXTRATO DE MOVIMENTO DE VENDAS  ...  V3.00 - 05/2023 EEVC
-- ============================================================================

INSERT INTO layout (
    idt_layout, cod_layout, idt_acquirer, des_version, des_file_type,
    des_column_separator, des_transaction_type, des_distribution_type,
    des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
)
VALUES (
    4, 'REDE_EEVC_03', 1, 'V3.00', 'TXT',
    NULL, 'CAPTURA', 'DIARIO',
    'utf-8', SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    41, 4, 'Tipo de Registro', 'HEADER', 'IGUAL',
    1, 3, '002', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    42, 4, 'Nome do arquirente', 'HEADER', 'IGUAL',
    12, 19, 'REDECARD', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    43, 4, 'Descritivo do tipo de movimentação', 'HEADER', 'IGUAL',
    22, 51, 'EXTRATO DE MOVIMENTO DE VENDAS', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    44, 4, 'Versão do arquivo', 'HEADER', 'IGUAL',
    110, 129, 'V3.00 - 05/2023 EEVC', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

-- ============================================================================
-- Layout 5: REDE EEFI Versão 04 (TXT)
-- Identificação: HEADER TXT com 4 regras AND (byte offset, 1-based)
-- Posições: primeiro caractere = posição 1
-- Exemplo de cabeçalho TXT: 030        REDECARD  EXTRATO DE MOVIMENTACAO FINANCEIRA  ...  V4.00 - 05/2023 EEFI
-- ============================================================================

INSERT INTO layout (
    idt_layout, cod_layout, idt_acquirer, des_version, des_file_type,
    des_column_separator, des_transaction_type, des_distribution_type,
    des_encoding, dat_creation, dat_update, nam_change_agent, flg_active
)
VALUES (
    5, 'REDE_EEFI_04', 1, 'V4.00', 'TXT',
    NULL, 'FINANCEIRO', 'DIARIO',
    'utf-8', SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    51, 5, 'Tipo de Registro', 'HEADER', 'IGUAL',
    1, 3, '030', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    52, 5, 'Nome do arquirente', 'HEADER', 'IGUAL',
    12, 19, 'REDECARD', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    53, 5, 'Descritivo do tipo de movimentação', 'HEADER', 'IGUAL',
    22, 55, 'EXTRATO DE MOVIMENTACAO FINANCEIRA', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

INSERT INTO layout_identification_rule (
    idt_rule, idt_layout, des_rule, des_value_origin, des_criteria_type,
    num_start_position, num_end_position, des_value, des_tag, des_key,
    des_function_origin, des_function_dest, dat_creation, dat_update,
    nam_change_agent, flg_active
)
VALUES (
    54, 5, 'Versão do arquivo', 'HEADER', 'IGUAL',
    108, 127, 'V4.00 - 05/2023 EEFI', NULL, NULL,
    NULL, NULL, SYSDATE, NULL, 'SETUP', 1
);

COMMIT;

-- Verification
SELECT 'Layout examples inserted successfully' AS status FROM dual;
SELECT COUNT(*) AS layout_count FROM layout WHERE idt_layout IN (0, 1, 2, 3, 4, 5);
SELECT COUNT(*) AS rule_count FROM layout_identification_rule WHERE idt_layout IN (1, 2, 3, 4, 5);
