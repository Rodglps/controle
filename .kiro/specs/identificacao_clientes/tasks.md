# Plano de Implementação: Identificação de Clientes em Arquivos EDI

## Visão Geral

Este plano implementa a funcionalidade de identificação automática de clientes proprietários de arquivos EDI. A implementação segue uma abordagem incremental, começando pela refatoração de componentes compartilhados para o módulo commons, seguida pela criação da estrutura de dados, implementação do serviço de identificação e integração no fluxo de transferência.

A funcionalidade suporta quatro tipos de identificação: FILENAME (nome do arquivo), HEADER (conteúdo posicional), TAG (caminhos XML) e KEY (caminhos JSON). Um arquivo pode pertencer a múltiplos clientes simultaneamente.

## Tarefas

- [x] 1. Refatorar componentes compartilhados para commons
  - [x] 1.1 Criar interface IdentificationRule em commons
    - Criar interface `com.concil.edi.commons.service.extractor.IdentificationRule`
    - Definir métodos getters para todos os campos de regra (des_value_origin, des_criteria_type, etc.)
    - _Requirements: Design - Interface Comum_
  
  - [x] 1.2 Mover ValueExtractor e implementações para commons
    - Mover interface `ValueExtractor` de `consumer.service.layout.extractor` para `commons.service.extractor`
    - Atualizar assinatura do método `extractValue` para usar `IdentificationRule` em vez de `LayoutIdentificationRule`
    - Mover todas as implementações: FilenameExtractor, HeaderTxtExtractor, HeaderCsvExtractor, XmlTagExtractor, JsonKeyExtractor
    - Atualizar imports em todas as classes movidas
    - _Requirements: Design - ValueExtractor Strategy_
  
  - [x] 1.3 Mover CriteriaComparator para commons
    - Mover classe `CriteriaComparator` de `consumer.service.layout` para `commons.service`
    - Atualizar imports em classes dependentes
    - _Requirements: Design - CriteriaComparator_
  
  - [x] 1.4 Mover TransformationApplier para commons
    - Mover classe `TransformationApplier` de `consumer.service.layout` para `commons.service`
    - Atualizar imports em classes dependentes
    - _Requirements: Design - TransformationApplier_
  
  - [x] 1.5 Mover RuleValidator para commons e atualizar para usar IdentificationRule
    - Mover classe `RuleValidator` de `consumer.service.layout` para `commons.service`
    - Atualizar método `validate` para aceitar `IdentificationRule` em vez de `LayoutIdentificationRule`
    - Atualizar imports em classes dependentes
    - _Requirements: Design - RuleValidator_
  
  - [x] 1.6 Atualizar LayoutIdentificationRule para implementar IdentificationRule
    - Adicionar `implements IdentificationRule` na classe `LayoutIdentificationRule`
    - Verificar que todos os métodos da interface já existem via Lombok
    - _Requirements: Design - Interface Comum_
  
  - [x] 1.7 Atualizar LayoutIdentificationService para usar componentes da nova localização
    - Atualizar imports para usar `commons.service.extractor.ValueExtractor`
    - Atualizar imports para usar `commons.service.CriteriaComparator`
    - Atualizar imports para usar `commons.service.TransformationApplier`
    - Atualizar imports para usar `commons.service.RuleValidator`
    - Verificar que tudo compila sem erros
    - _Requirements: Design - Refatoração para Commons_

- [x] 2. Checkpoint - Verificar refatoração
  - Executar `mvn clean compile` para garantir que não há erros de compilação
  - Executar testes existentes de identificação de layout: `mvn test -Dtest=LayoutIdentificationE2ETest`
  - Perguntar ao usuário se há dúvidas ou problemas

- [x] 3. Criar scripts DDL para novas tabelas
  - [x] 3.1 Criar script para tabela customer_identification
    - Criar arquivo `scripts/ddl/08_create_customer_identification_table.sql`
    - Definir tabela com todas as colunas conforme requirements 13
    - Criar sequence `customer_identification_seq`
    - Criar foreign key para tabela layout
    - _Requirements: 13.1-13.12_
  
  - [x] 3.2 Criar script para tabela customer_identification_rule
    - Criar arquivo `scripts/ddl/09_create_customer_identification_rule_table.sql`
    - Definir tabela com todas as colunas conforme requirements 14
    - Criar sequence `customer_identification_rule_seq`
    - Criar foreign key para tabela customer_identification
    - _Requirements: 14.1-14.14_
  
  - [x] 3.3 Criar script para tabela file_origin_clients
    - Criar arquivo `scripts/ddl/10_create_file_origin_clients_table.sql`
    - Definir tabela com todas as colunas conforme requirements 15
    - Criar sequence `file_origin_clients_seq`
    - Criar índice único em (idt_file_origin, idt_client)
    - Criar foreign key para tabela file_origin
    - _Requirements: 15.1-15.7_
  
  - [x] 3.4 Criar script com dados de exemplo para os 4 cenários de teste
    - Criar arquivo `scripts/ddl/11_insert_customer_identification_examples.sql`
    - Inserir dados para Teste 1: Múltiplos clientes CIELO (clientes 15 e 20)
    - Inserir dados para Teste 3: Cliente 25 com identificação por HEADER TXT
    - Inserir dados para Teste 4: Cliente 30 com identificação por HEADER CSV
    - _Requirements: Prompt - Cenários de Teste_
  
  - [x] 3.5 Atualizar script 00_run_all.sql
    - Adicionar chamadas para os novos scripts (08, 09, 10, 11) na ordem correta
    - _Requirements: Design - Scripts DDL_

- [x] 4. Criar entidades JPA
  - [x] 4.1 Criar entidade CustomerIdentification
    - Criar classe `com.concil.edi.commons.entity.CustomerIdentification`
    - Adicionar anotações JPA (@Entity, @Table, @Id, @GeneratedValue, @Column)
    - Adicionar todos os campos conforme design
    - Adicionar Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor)
    - Adicionar `implements IdentificationRule` (retornar null para métodos de regra)
    - _Requirements: 13.1-13.12_
  
  - [x] 4.2 Criar entidade CustomerIdentificationRule
    - Criar classe `com.concil.edi.commons.entity.CustomerIdentificationRule`
    - Adicionar anotações JPA com enums (@Enumerated)
    - Adicionar todos os campos conforme design
    - Adicionar Lombok
    - Adicionar `implements IdentificationRule`
    - _Requirements: 14.1-14.14_
  
  - [x] 4.3 Criar entidade FileOriginClients
    - Criar classe `com.concil.edi.commons.entity.FileOriginClients`
    - Adicionar anotações JPA com unique constraint
    - Adicionar todos os campos conforme design
    - Adicionar Lombok
    - _Requirements: 15.1-15.7_

- [x] 5. Criar repositories JPA
  - [x] 5.1 Criar CustomerIdentificationRepository
    - Criar interface `com.concil.edi.commons.repository.CustomerIdentificationRepository`
    - Estender JpaRepository<CustomerIdentification, Long>
    - Adicionar query findByAcquirerAndValueOrigin com @Query
    - Adicionar query findByAcquirerAndLayoutAndValueOrigins com @Query
    - _Requirements: Design - Repository: CustomerIdentificationRepository_
  
  - [x] 5.2 Criar CustomerIdentificationRuleRepository
    - Criar interface `com.concil.edi.commons.repository.CustomerIdentificationRuleRepository`
    - Estender JpaRepository<CustomerIdentificationRule, Long>
    - Adicionar método findByIdtIdentificationAndFlgActive
    - _Requirements: Design - Repository: CustomerIdentificationRuleRepository_
  
  - [x] 5.3 Criar FileOriginClientsRepository
    - Criar interface `com.concil.edi.commons.repository.FileOriginClientsRepository`
    - Estender JpaRepository<FileOriginClients, Long>
    - _Requirements: Design - Repository: FileOriginClientsRepository_

- [x] 6. Checkpoint - Verificar estrutura de dados
  - Executar `make init-db` para criar as novas tabelas
  - Verificar que as tabelas foram criadas: `make shell-oracle` e executar `SELECT table_name FROM user_tables WHERE table_name LIKE 'CUSTOMER%' OR table_name = 'FILE_ORIGIN_CLIENTS';`
  - Verificar que os dados de exemplo foram inseridos
  - Perguntar ao usuário se há dúvidas ou problemas

- [x] 7. Implementar CustomerIdentificationService
  - [x] 7.1 Criar classe CustomerIdentificationService
    - Criar classe `com.concil.edi.consumer.service.customer.CustomerIdentificationService`
    - Adicionar anotação @Service
    - Adicionar injeção de dependências (repositories, extractors, criteriaComparator, ruleValidator)
    - Adicionar logger
    - _Requirements: Design - CustomerIdentificationService_
  
  - [x] 7.2 Implementar método identifyCustomers
    - Implementar método público `identifyCustomers(byte[] buffer, String filename, Long acquirerId, Long layoutId)`
    - Retornar List<Long> com IDs dos clientes identificados
    - Adicionar logs de início e fim do processo
    - _Requirements: 1.1, Design - Main Identification Algorithm_
  
  - [x] 7.3 Implementar método retrieveIdentifications
    - Implementar método privado para buscar customer_identification baseado em layoutId
    - Se layoutId é NULL: buscar apenas regras FILENAME
    - Se layoutId não é NULL: buscar regras FILENAME + união com regras HEADER/TAG/KEY
    - Aplicar filtro por acquirerId
    - Ordenar por num_process_weight DESC
    - _Requirements: 1.2, 1.3, 4.2, 4.3, 4.4, 4.5, 4.6_
  
  - [x] 7.4 Implementar método matchesIdentification
    - Implementar método privado para verificar se todas as regras de um identification são satisfeitas
    - Buscar regras ativas (flg_active=1)
    - Aplicar operador AND entre todas as regras
    - Retornar true apenas se TODAS as regras forem satisfeitas
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [x] 7.5 Implementar método extractValue
    - Implementar método privado para extrair valor baseado na regra
    - Delegar para ValueExtractor apropriado usando strategy pattern
    - Tratar erros de TAG/KEY com log e retorno null
    - _Requirements: 5.1-5.7, 6.1-6.7, 7.1-7.8, 8.1-8.8, 9.1-9.9_
  
  - [x] 7.6 Implementar método applyRuleComparison
    - Implementar método privado para aplicar transformações e comparar valores
    - Usar TransformationApplier para des_function_origin e des_function_dest
    - Usar CriteriaComparator para comparação
    - _Requirements: 10.1-10.11_
  
  - [ ]* 7.7 Escrever testes unitários para CustomerIdentificationService
    - Testar cenário com layout não identificado (apenas FILENAME)
    - Testar cenário com layout identificado (FILENAME + HEADER/TAG/KEY)
    - Testar operador AND entre múltiplas regras
    - Testar filtro por acquirer
    - Testar múltiplos clientes identificados
    - Testar nenhum cliente identificado
    - Testar regras inativas (flg_active=0)
    - _Requirements: Design - Unit Testing_

- [x] 8. Integrar CustomerIdentificationService no FileTransferListener
  - [x] 8.1 Adicionar injeção de CustomerIdentificationService no FileTransferListener
    - Adicionar campo privado final para CustomerIdentificationService
    - Adicionar ao construtor
    - _Requirements: Design - Integration Point_
  
  - [x] 8.2 Adicionar injeção de FileOriginClientsRepository no FileTransferListener
    - Adicionar campo privado final para FileOriginClientsRepository
    - Adicionar ao construtor
    - _Requirements: 12.1-12.5_
  
  - [x] 8.3 Chamar identifyCustomers após identificação de layout
    - Adicionar chamada para `customerIdentificationService.identifyCustomers(buffer, filename, acquirerId, layoutId)`
    - Passar o mesmo buffer usado para identificação de layout
    - Capturar List<Long> de clientes identificados
    - _Requirements: 1.1, 11.1, 11.2_
  
  - [x] 8.4 Persistir clientes identificados em file_origin_clients
    - Para cada clientId na lista retornada, criar registro FileOriginClients
    - Definir idt_file_origin, idt_client, dat_creation
    - Salvar usando fileOriginClientsRepository
    - Tratar DataIntegrityViolationException para duplicatas (log warning e continuar)
    - Se lista vazia, não persistir nada
    - _Requirements: 2.2, 2.3, 12.1-12.5_
  
  - [x] 8.5 Garantir que processamento continua sem identificação
    - Verificar que lista vazia não causa exceção
    - Verificar que step=COLETA e status=CONCLUIDO são mantidos
    - _Requirements: 1.5, 1.6, 1.7_

- [x] 9. Checkpoint - Verificar integração
  - Executar `mvn clean compile` para garantir que não há erros
  - Executar testes unitários: `mvn test -Dtest=CustomerIdentificationServiceTest`
  - Perguntar ao usuário se há dúvidas ou problemas

- [ ]* 10. Implementar testes de propriedade (property-based tests)
  - [ ]* 10.1 Criar CustomerIdentificationServicePropertyTest
    - **Property 1: Layout-based rule filtering**
    - **Validates: Requirements 1.2**
  
  - [ ]* 10.2 Adicionar teste para Property 2
    - **Property 2: Content-based rules require layout**
    - **Validates: Requirements 1.3**
  
  - [ ]* 10.3 Adicionar teste para Property 3
    - **Property 3: Buffer size limit**
    - **Validates: Requirements 1.4**
  
  - [ ]* 10.4 Adicionar teste para Property 4
    - **Property 4: Processing continues without identification**
    - **Validates: Requirements 1.5, 1.6**
  
  - [ ]* 10.5 Adicionar teste para Property 5
    - **Property 5: No persistence without identification**
    - **Validates: Requirements 1.7**
  
  - [ ]* 10.6 Adicionar teste para Property 6
    - **Property 6: All matching clients identified**
    - **Validates: Requirements 2.1**
  
  - [ ]* 10.7 Adicionar teste para Property 7
    - **Property 7: Multiple client persistence**
    - **Validates: Requirements 2.2**
  
  - [ ]* 10.8 Adicionar teste para Property 8
    - **Property 8: Duplicate prevention**
    - **Validates: Requirements 2.3**
  
  - [ ]* 10.9 Adicionar teste para Property 9
    - **Property 9: Result ordering by weight**
    - **Validates: Requirements 2.4**
  
  - [ ]* 10.10 Adicionar teste para Property 10
    - **Property 10: AND operator for rules**
    - **Validates: Requirements 3.1, 3.2**
  
  - [ ]* 10.11 Adicionar teste para Property 11
    - **Property 11: Active flag filtering**
    - **Validates: Requirements 3.3, 3.4**
  
  - [ ]* 10.12 Adicionar teste para Property 12
    - **Property 12: Acquirer filtering**
    - **Validates: Requirements 4.2**
  
  - [ ]* 10.13 Criar CriteriaComparatorPropertyTest para Properties 13-17 e 27
    - **Property 13: Criteria type - starts with**
    - **Property 14: Criteria type - ends with**
    - **Property 15: Criteria type - contains**
    - **Property 16: Criteria type - contained in**
    - **Property 17: Criteria type - equals**
    - **Property 27: Case-sensitive comparison by default**
    - **Validates: Requirements 5.1-5.5, 10.11**
  
  - [ ]* 10.14 Criar FunctionTransformerPropertyTest para Property 18
    - **Property 18: Function transformation round-trip**
    - **Validates: Requirements 5.6, 5.7, 10.1-10.11**
  
  - [ ]* 10.15 Criar HeaderExtractorPropertyTest para Properties 19-22
    - **Property 19: TXT header byte extraction**
    - **Property 20: Buffer as single line without newlines**
    - **Property 21: CSV column extraction**
    - **Property 22: CSV line-by-line processing**
    - **Validates: Requirements 6.1-6.4, 7.1-7.5**
  
  - [ ]* 10.16 Criar TagExtractorPropertyTest para Properties 23-24
    - **Property 23: XML tag extraction**
    - **Property 24: TAG extraction error handling**
    - **Validates: Requirements 8.1, 8.2, 8.7, 8.8**
  
  - [ ]* 10.17 Criar KeyExtractorPropertyTest para Properties 25-26
    - **Property 25: JSON key extraction**
    - **Property 26: KEY extraction error handling**
    - **Validates: Requirements 9.1, 9.2, 9.7, 9.8**
  
  - [ ]* 10.18 Adicionar teste para Property 28
    - **Property 28: Persistence field population**
    - **Validates: Requirements 12.2, 12.3, 12.4**
  
  - [ ]* 10.19 Criar RuleValidatorPropertyTest para Property 29
    - **Property 29: Enum validation**
    - **Validates: Requirements 16.4, 16.5, 16.6**

- [x] 11. Implementar testes E2E para os 4 cenários
  - [x] 11.1 Criar CustomerIdentificationE2ETest
    - Criar classe de teste E2E estendendo E2ETestBase
    - Configurar @SpringBootTest e @ActiveProfiles("docker")
    - _Requirements: Design - E2E Testing_
  
  - [x] 11.2 Implementar Teste 1: Múltiplos clientes por FILENAME
    - Criar arquivo `cielo_1234567890_premium_20250101_venda.txt`
    - Enviar para SFTP origem
    - Aguardar processamento
    - Verificar que clientes 15 e 20 foram identificados
    - Verificar ordenação por num_process_weight (20 antes de 15)
    - Verificar 2 registros em file_origin_clients
    - _Requirements: Prompt - Teste 1_
  
  - [x] 11.3 Implementar Teste 2: Nenhum cliente identificado
    - Criar arquivo `rede_9999999999_standard_20250101.txt`
    - Enviar para SFTP origem
    - Aguardar processamento
    - Verificar que nenhum cliente foi identificado
    - Verificar que não há registros em file_origin_clients
    - Verificar step=COLETA e status=CONCLUIDO
    - _Requirements: Prompt - Teste 2_
  
  - [x] 11.4 Implementar Teste 3: Identificação por HEADER em TXT
    - Criar arquivo TXT com primeira linha: `VENDA     1525      20250101...`
    - Enviar para SFTP origem
    - Aguardar processamento
    - Verificar que cliente 25 foi identificado
    - Verificar 1 registro em file_origin_clients
    - _Requirements: Prompt - Teste 3_
  
  - [x] 11.5 Implementar Teste 4: Identificação por HEADER em CSV
    - Criar arquivo CSV com linhas: `EEVD;20250101;1530;100.00;APROVADO`
    - Enviar para SFTP origem
    - Aguardar processamento
    - Verificar que cliente 30 foi identificado
    - Verificar 1 registro em file_origin_clients
    - _Requirements: Prompt - Teste 4_

- [x] 12. Checkpoint final - Executar todos os testes
  - Executar testes unitários: `mvn test`
  - Executar testes E2E: `make e2e`
  - Verificar cobertura de código (mínimo 80% de linhas)
  - Perguntar ao usuário se há dúvidas ou se está pronto para finalizar

## Notas

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada tarefa referencia os requirements específicos para rastreabilidade
- Checkpoints garantem validação incremental do progresso
- Testes de propriedade usam jqwik com mínimo 100 iterações por teste
- Testes E2E requerem docker-compose rodando (`make up`)
