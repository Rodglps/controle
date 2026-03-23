-- ============================================================================
-- Script: 04-insert-acquirers.sql
-- Descrição: Dados de teste para adquirentes
-- Sistema: Controle de Arquivos - Test Data
-- ============================================================================
-- 
-- NOTA: Este script assume que existe uma tabela 'acquirer' ou similar.
-- Como a tabela não foi definida nos scripts DDL originais, este script
-- documenta os IDs de adquirentes usados nos outros scripts de teste.
-- 
-- Se a tabela 'acquirer' existir, descomente e ajuste os INSERTs abaixo.
-- ============================================================================

-- ============================================================================
-- Adquirentes Utilizados nos Scripts de Teste
-- ============================================================================
-- 
-- ID 1: CIELO
-- ID 2: REDE
-- ID 3: STONE
-- ID 4: GETNET
-- 
-- ============================================================================

/*
-- Descomente se a tabela 'acquirer' existir:

INSERT INTO acquirer (idt_acquirer, des_acquirer_name, cod_acquirer, flg_active)
VALUES (1, 'CIELO', 'CIELO', 1);

INSERT INTO acquirer (idt_acquirer, des_acquirer_name, cod_acquirer, flg_active)
VALUES (2, 'REDE', 'REDE', 1);

INSERT INTO acquirer (idt_acquirer, des_acquirer_name, cod_acquirer, flg_active)
VALUES (3, 'STONE', 'STONE', 1);

INSERT INTO acquirer (idt_acquirer, des_acquirer_name, cod_acquirer, flg_active)
VALUES (4, 'GETNET', 'GETNET', 1);

COMMIT;
*/

-- ============================================================================
-- Referências de Adquirentes nos Testes
-- ============================================================================
-- 
-- Os seguintes arquivos de teste estão associados aos adquirentes:
-- 
-- CIELO (ID 1):
--   - LOJA_SHOPPING_20240115.csv
--   - FARMACIA_20240115_EXTRATO.ofx
--   - LOJA_ELETRONICOS_VENDAS_20240115.csv
-- 
-- REDE (ID 2):
--   - SUPERMERCADO_CENTRAL_VENDAS_20240115.txt
--   - SUPERMERCADO_CENTRAL_CANCEL_20240115.csv
-- 
-- STONE (ID 3):
--   - STONE_PAGAMENTOS_20240115_RESTAURANTE.json
-- 
-- GETNET (ID 4):
--   - POSTO_COMBUSTIVEL_CONCILIACAO_20240115.xml
-- 
-- ============================================================================

EXIT;
