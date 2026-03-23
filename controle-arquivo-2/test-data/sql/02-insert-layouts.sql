-- ============================================================================
-- Script: 02-insert-layouts.sql
-- Descrição: Dados de teste para layouts e regras de identificação
-- Sistema: Controle de Arquivos - Test Data
-- ============================================================================

-- ============================================================================
-- Layouts
-- ============================================================================

-- Layout CSV Cielo
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'CIELO_CSV_TRANSACOES', 'CSV', 'Layout CSV de transações Cielo com separador ponto-vírgula', 1);

-- Layout TXT Rede
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'REDE_TXT_VENDAS', 'TXT', 'Layout TXT posicional de vendas Rede', 1);

-- Layout JSON Stone
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'STONE_JSON_PAGAMENTOS', 'JSON', 'Layout JSON de pagamentos Stone', 1);

-- Layout OFX Cielo
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'CIELO_OFX_EXTRATO', 'OFX', 'Layout OFX de extrato financeiro Cielo', 1);

-- Layout XML GetNet
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'GETNET_XML_CONCILIACAO', 'XML', 'Layout XML de conciliação GetNet', 1);

-- Layout CSV Rede (alternativo)
INSERT INTO layout (idt_layout, des_layout_name, des_layout_type, des_description, flg_active)
VALUES (seq_layout.NEXTVAL, 'REDE_CSV_CANCELAMENTOS', 'CSV', 'Layout CSV de cancelamentos Rede', 1);

-- ============================================================================
-- Regras de Identificação de Layout
-- ============================================================================

-- ----------------------------------------------------------------------------
-- CIELO_CSV_TRANSACOES (Layout ID 1)
-- Cliente: LOJA_SHOPPING (ID 1), Adquirente: Cielo (ID 1)
-- ----------------------------------------------------------------------------

-- Regra 1: Nome do arquivo contém ".csv"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 1, 1, 1,
    'FILENAME', 'CONTEM', 0, 100,
    '.csv', 1
);

-- Regra 2: Header começa com "TIPO;DATA;VALOR;ESTABELECIMENTO"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 1, 1, 1,
    'HEADER', 'COMECA-COM', 0, 100,
    'TIPO;DATA;VALOR;ESTABELECIMENTO', 1
);

-- ----------------------------------------------------------------------------
-- REDE_TXT_VENDAS (Layout ID 2)
-- Cliente: SUPERMERCADO_CENTRAL (ID 2), Adquirente: Rede (ID 2)
-- ----------------------------------------------------------------------------

-- Regra 1: Nome do arquivo termina com ".txt"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 2, 2, 2,
    'FILENAME', 'TERMINA-COM', 0, 100,
    '.txt', 1
);

-- Regra 2: Header contém "REDE VENDAS"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 2, 2, 2,
    'HEADER', 'CONTEM', 0, 200,
    'REDE VENDAS', 1
);

-- ----------------------------------------------------------------------------
-- STONE_JSON_PAGAMENTOS (Layout ID 3)
-- Cliente: RESTAURANTE_GOURMET (ID 3), Adquirente: Stone (ID 3)
-- ----------------------------------------------------------------------------

-- Regra 1: Nome do arquivo contém ".json"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 3, 3, 3,
    'FILENAME', 'CONTEM', 0, 100,
    '.json', 1
);

-- Regra 2: Header contém "\"acquirer\":\"STONE\""
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 3, 3, 3,
    'HEADER', 'CONTEM', 0, 1000,
    '"acquirer":"STONE"', 1
);

-- ----------------------------------------------------------------------------
-- CIELO_OFX_EXTRATO (Layout ID 4)
-- Cliente: FARMACIA_SAUDE (ID 4), Adquirente: Cielo (ID 1)
-- ----------------------------------------------------------------------------

-- Regra 1: Nome do arquivo termina com ".ofx"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 4, 4, 1,
    'FILENAME', 'TERMINA-COM', 0, 100,
    '.ofx', 1
);

-- Regra 2: Header contém "<OFX>"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 4, 4, 1,
    'HEADER', 'CONTEM', 0, 500,
    '<OFX>', 1
);

-- ----------------------------------------------------------------------------
-- GETNET_XML_CONCILIACAO (Layout ID 5)
-- Cliente: POSTO_COMBUSTIVEL (ID 5), Adquirente: GetNet (ID 4)
-- ----------------------------------------------------------------------------

-- Regra 1: Nome do arquivo contém ".xml"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 5, 5, 4,
    'FILENAME', 'CONTEM', 0, 100,
    '.xml', 1
);

-- Regra 2: Header começa com "<?xml"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 5, 5, 4,
    'HEADER', 'COMECA-COM', 0, 50,
    '<?xml', 1
);

-- Regra 3: Header contém "<Conciliacao>"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 5, 5, 4,
    'HEADER', 'CONTEM', 0, 1000,
    '<Conciliacao>', 1
);

-- ----------------------------------------------------------------------------
-- REDE_CSV_CANCELAMENTOS (Layout ID 6)
-- Cliente: SUPERMERCADO_CENTRAL (ID 2), Adquirente: Rede (ID 2)
-- ----------------------------------------------------------------------------

-- Regra 1: Nome do arquivo contém "CANCEL"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 6, 2, 2,
    'FILENAME', 'CONTEM', 0, 100,
    'CANCEL', 1
);

-- Regra 2: Nome do arquivo termina com ".csv"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 6, 2, 2,
    'FILENAME', 'TERMINA-COM', 0, 100,
    '.csv', 1
);

-- Regra 3: Header igual a "TIPO;DATA;NSU;VALOR_CANCELADO"
INSERT INTO layout_identification_rule (
    idt_layout_identification_rule, idt_layout, idt_client, idt_acquirer,
    des_value_origin, des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_layout_identification_rule.NEXTVAL, 6, 2, 2,
    'HEADER', 'IGUAL', 0, 50,
    'TIPO;DATA;NSU;VALOR_CANCELADO', 1
);

COMMIT;

EXIT;
