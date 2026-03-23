-- ============================================================================
-- Script: 03-insert-clients.sql
-- Descrição: Dados de teste para clientes e regras de identificação
-- Sistema: Controle de Arquivos - Test Data
-- ============================================================================

-- ============================================================================
-- Clientes
-- ============================================================================

-- Cliente 1: Loja de Shopping
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'LOJA_SHOPPING', 100, 1
);

-- Cliente 2: Supermercado Central
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'SUPERMERCADO_CENTRAL', 95, 1
);

-- Cliente 3: Restaurante Gourmet
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'RESTAURANTE_GOURMET', 90, 1
);

-- Cliente 4: Farmácia Saúde
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'FARMACIA_SAUDE', 85, 1
);

-- Cliente 5: Posto de Combustível
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'POSTO_COMBUSTIVEL', 80, 1
);

-- Cliente 6: Loja de Eletrônicos (peso menor para teste de desempate)
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'LOJA_ELETRONICOS', 75, 1
);

-- ============================================================================
-- Regras de Identificação de Cliente
-- ============================================================================

-- ----------------------------------------------------------------------------
-- LOJA_SHOPPING (Cliente ID 1) - Adquirente Cielo (ID 1)
-- Critério: Nome do arquivo COMEÇA COM "LOJA_SHOPPING_"
-- ----------------------------------------------------------------------------
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 1, 1,
    'COMECA-COM', 0, 14,
    'LOJA_SHOPPING_', 1
);

-- ----------------------------------------------------------------------------
-- SUPERMERCADO_CENTRAL (Cliente ID 2) - Adquirente Rede (ID 2)
-- Critério 1: Nome do arquivo CONTÉM "SUPERMERCADO"
-- ----------------------------------------------------------------------------
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 2, 2,
    'CONTEM', 0, 100,
    'SUPERMERCADO', 1
);

-- Critério 2: Nome do arquivo CONTÉM "CENTRAL"
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 2, 2,
    'CONTEM', 0, 100,
    'CENTRAL', 1
);

-- ----------------------------------------------------------------------------
-- RESTAURANTE_GOURMET (Cliente ID 3) - Adquirente Stone (ID 3)
-- Critério: Nome do arquivo TERMINA COM "_RESTAURANTE.json"
-- ----------------------------------------------------------------------------
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 3, 3,
    'TERMINA-COM', 0, 100,
    '_RESTAURANTE.json', 1
);

-- ----------------------------------------------------------------------------
-- FARMACIA_SAUDE (Cliente ID 4) - Adquirente Cielo (ID 1)
-- Critério: Substring nas posições 0-7 é IGUAL a "FARMACIA"
-- ----------------------------------------------------------------------------
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 4, 1,
    'IGUAL', 0, 8,
    'FARMACIA', 1
);

-- ----------------------------------------------------------------------------
-- POSTO_COMBUSTIVEL (Cliente ID 5) - Adquirente GetNet (ID 4)
-- Critério 1: Nome do arquivo COMEÇA COM "POSTO_"
-- ----------------------------------------------------------------------------
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 5, 4,
    'COMECA-COM', 0, 6,
    'POSTO_', 1
);

-- Critério 2: Nome do arquivo CONTÉM "COMBUSTIVEL"
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 5, 4,
    'CONTEM', 0, 100,
    'COMBUSTIVEL', 1
);

-- ----------------------------------------------------------------------------
-- LOJA_ELETRONICOS (Cliente ID 6) - Adquirente Cielo (ID 1)
-- Critério: Nome do arquivo CONTÉM "ELETRONICOS"
-- Nota: Peso menor (75) para teste de desempate com outros clientes
-- ----------------------------------------------------------------------------
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 6, 1,
    'CONTEM', 0, 100,
    'ELETRONICOS', 1
);

-- ============================================================================
-- Regras para Testes de Casos Negativos
-- ============================================================================

-- Cliente com regra que nunca será satisfeita (para teste de erro)
INSERT INTO customer_identification (
    idt_customer_identification, des_customer_name, num_processing_weight, flg_active
) VALUES (
    seq_customer_identification.NEXTVAL, 'CLIENTE_IMPOSSIVEL', 50, 1
);

-- Regra impossível: arquivo deve começar com "IMPOSSIVEL_" E terminar com "_IMPOSSIVEL"
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 7, 1,
    'COMECA-COM', 0, 11,
    'IMPOSSIVEL_', 1
);

INSERT INTO customer_identification_rule (
    idt_customer_identification_rule, idt_customer_identification, idt_acquirer,
    des_criterion_type_enum, num_starting_position, num_ending_position,
    des_value, flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL, 7, 1,
    'TERMINA-COM', 0, 100,
    '_IMPOSSIVEL', 1
);

COMMIT;

EXIT;
