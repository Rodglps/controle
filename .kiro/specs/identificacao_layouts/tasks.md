# Plano de Implementação - Identificação de Layouts de Arquivos EDI

## Visão Geral

Este plano implementa a funcionalidade de identificação automática de layouts de arquivos EDI durante o processo de transferência no Consumer. O sistema identifica qual layout e adquirente corresponde ao arquivo sendo processado, aplicando regras configuráveis baseadas em múltiplas origens (nome do arquivo, cabeçalho TXT/CSV, tags XML, chaves JSON).

A implementação segue uma abordagem incremental, começando pela camada de dados (DDL/DML), seguindo para as entidades JPA no módulo commons, depois os componentes de serviço no consumer, e finalmente a integração com o fluxo de transferência existente.

## Tasks

- [x] 1. Criar scripts DDL para tabelas de layout
  - Criar script `scripts/ddl/06_create_layout_tables.sql`
  - Definir tabela `layout` com todos os campos e constraints
  - Definir tabela `layout_identification_rule` com todos os campos e constraints
  - Criar sequences `layout_seq` e `layout_identification_rule_seq`
  - Adicionar foreign key `layout_identification_rule.idt_layout -> layout.idt_layout`
  - Adicionar foreign key `file_origin.idt_layout -> layout.idt_layout`
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 15.1, 15.2, 15.3_

- [x] 2. Criar scripts DML com dados de exemplo
  - Criar script `scripts/ddl/07_insert_layout_examples.sql`
  - Inserir 2 layouts Cielo (VENDA e PAGTO) com identificação por FILENAME
  - Inserir 3 layouts Rede (EEVD CSV, EEVC TXT, EEFI TXT) com identificação por HEADER
  - Inserir regras de identificação para cada layout conforme exemplos do design
  - Atualizar `scripts/ddl/00_run_all.sql` para incluir os novos scripts
  - Atualizar `scripts/ddl/README.md` com documentação dos novos scripts
  - _Requirements: 15.4, 15.5, 15.6, 15.7_

- [x] 3. Criar enums no módulo commons
  - [x] 3.1 Criar enum ValueOrigin
    - Criar `commons/src/main/java/com/concil/edi/commons/enums/ValueOrigin.java`
    - Definir valores: FILENAME, HEADER, TAG, KEY
    - _Requirements: 1.5_
  
  - [x] 3.2 Criar enum CriteriaType
    - Criar `commons/src/main/java/com/concil/edi/commons/enums/CriteriaType.java`
    - Definir valores: COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL
    - _Requirements: 1.5_
  
  - [x] 3.3 Criar enum FunctionType
    - Criar `commons/src/main/java/com/concil/edi/commons/enums/FunctionType.java`
    - Definir valores: UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE
    - _Requirements: 1.5_
  
  - [x] 3.4 Criar enum DistributionType
    - Criar `commons/src/main/java/com/concil/edi/commons/enums/DistributionType.java`
    - Definir valores: DIARIO, DIAS_UTEIS, SEGUNDA_A_SEXTA, DOMINGO, SEGUNDA_FEIRA, TERCA_FEIRA, QUARTA_FEIRA, QUINTA_FEIRA, SEXTA_FEIRA, SABADO, SAZONAL
    - _Requirements: 1.5_
  
  - [x] 3.5 Atualizar enum FileType
    - Adicionar valor OFX ao enum existente `commons/src/main/java/com/concil/edi/commons/enums/FileType.java`
    - _Requirements: 1.5_

- [x] 4. Criar entidades JPA no módulo commons
  - [x] 4.1 Criar entidade Layout
    - Criar `commons/src/main/java/com/concil/edi/commons/entity/Layout.java`
    - Mapear todos os campos da tabela layout
    - Configurar sequence generator e relacionamentos
    - _Requirements: 2.1_
  
  - [x] 4.2 Criar entidade LayoutIdentificationRule
    - Criar `commons/src/main/java/com/concil/edi/commons/entity/LayoutIdentificationRule.java`
    - Mapear todos os campos da tabela layout_identification_rule
    - Configurar sequence generator e relacionamentos
    - _Requirements: 2.2_

- [x] 5. Criar repositories no módulo commons
  - [x] 5.1 Criar LayoutRepository
    - Criar `commons/src/main/java/com/concil/edi/commons/repository/LayoutRepository.java`
    - Implementar método `findByIdtAcquirerAndFlgActiveOrderByIdtLayoutDesc`
    - _Requirements: 2.3_
  
  - [x] 5.2 Criar LayoutIdentificationRuleRepository
    - Criar `commons/src/main/java/com/concil/edi/commons/repository/LayoutIdentificationRuleRepository.java`
    - Implementar método `findByIdtLayoutAndFlgActive`
    - _Requirements: 2.4_

- [x] 6. Implementar componentes de transformação e comparação
  - [x] 6.1 Criar TransformationApplier
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/TransformationApplier.java`
    - Implementar aplicação de funções UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8, 12.9, 12.10_
  
  - [ ]* 6.2 Escrever testes unitários para TransformationApplier
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/TransformationApplierTest.java`
    - Testar cada função de transformação com exemplos específicos
    - _Requirements: 16.3_
  
  - [x] 6.3 Escrever testes de propriedade para TransformationApplier
    - **Property 18: Transformação UPPERCASE**
    - **Property 19: Transformação LOWERCASE**
    - **Property 20: Transformação INITCAP**
    - **Property 21: Transformação TRIM**
    - **Property 22: Transformação NONE**
    - **Validates: Requirements 12.1-12.10**
  
  - [x] 6.4 Criar CriteriaComparator
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/CriteriaComparator.java`
    - Implementar comparações: COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL
    - Aplicar transformações antes da comparação
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_
  
  - [ ]* 6.5 Escrever testes unitários para CriteriaComparator
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/CriteriaComparatorTest.java`
    - Testar cada tipo de critério com exemplos específicos
    - Testar case-sensitivity
    - _Requirements: 16.2_
  
  - [x] 6.6 Escrever testes de propriedade para CriteriaComparator
    - **Property 1: Critério COMECA_COM**
    - **Property 2: Critério TERMINA_COM**
    - **Property 3: Critério CONTEM**
    - **Property 4: Critério CONTIDO**
    - **Property 5: Critério IGUAL**
    - **Property 6: Transformações aplicadas antes da comparação**
    - **Property 7: Comparação case-sensitive por padrão**
    - **Validates: Requirements 5.1-5.7**

- [x] 7. Implementar componentes de detecção e conversão de encoding
  - [x] 7.1 Criar EncodingConverter
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/EncodingConverter.java`
    - Implementar detecção automática de encoding usando Apache Tika ou ICU4J
    - Implementar conversão com fallback: des_encoding → UTF-8 → encoding detectado
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_
  
  - [ ]* 7.2 Escrever testes unitários para EncodingConverter
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/EncodingConverterTest.java`
    - Testar detecção de encoding com diferentes codificações
    - Testar conversão com fallback
    - _Requirements: 16.6_
  
  - [ ]* 7.3 Escrever testes de propriedade para EncodingConverter
    - **Property 16: Detecção automática de encoding**
    - **Property 17: Conversão de encoding com fallback**
    - **Validates: Requirements 11.1-11.5**

- [x] 8. Checkpoint - Validar componentes base
  - Executar testes unitários e de propriedade dos componentes criados
  - Verificar se todas as transformações e comparações funcionam corretamente
  - Perguntar ao usuário se há dúvidas ou ajustes necessários

- [x] 9. Implementar estratégias de extração de valores
  - [x] 9.1 Criar interface ValueExtractor
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/extractor/ValueExtractor.java`
    - Definir métodos `extractValue` e `supports`
    - _Requirements: 4.4_
  
  - [x] 9.2 Criar FilenameExtractor
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/extractor/FilenameExtractor.java`
    - Implementar extração do nome do arquivo
    - _Requirements: 6.1_
  
  - [ ]* 9.3 Escrever testes unitários para FilenameExtractor
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/extractor/FilenameExtractorTest.java`
    - Testar extração com diferentes nomes de arquivo
    - _Requirements: 16.1_
  
  - [ ]* 9.4 Escrever teste de propriedade para FilenameExtractor
    - **Property 8: Extração por nome de arquivo**
    - **Validates: Requirements 6.1**
  
  - [x] 9.5 Criar HeaderTxtExtractor
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/extractor/HeaderTxtExtractor.java`
    - Implementar extração posicional por byte offset
    - Implementar leitura linha por linha dentro do buffer
    - Tratar caso sem quebra de linha (buffer completo como uma linha)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_
  
  - [ ]* 9.6 Escrever testes unitários para HeaderTxtExtractor
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/extractor/HeaderTxtExtractorTest.java`
    - Testar extração com diferentes posições
    - Testar com e sem quebra de linha
    - Testar end_position NULL
    - _Requirements: 16.1_
  
  - [ ]* 9.7 Escrever testes de propriedade para HeaderTxtExtractor
    - **Property 9: Extração TXT por byte offset**
    - **Property 10: Extração TXT até fim da linha quando end_position é NULL**
    - **Property 11: Processamento linha por linha**
    - **Validates: Requirements 7.1-7.7**
  
  - [x] 9.8 Criar HeaderCsvExtractor
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/extractor/HeaderCsvExtractor.java`
    - Implementar extração por índice de coluna
    - Usar des_column_separator do layout
    - Implementar leitura linha por linha dentro do buffer
    - Tratar caso sem quebra de linha
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  
  - [ ]* 9.9 Escrever testes unitários para HeaderCsvExtractor
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/extractor/HeaderCsvExtractorTest.java`
    - Testar extração com diferentes separadores
    - Testar com e sem quebra de linha
    - _Requirements: 16.1_
  
  - [ ]* 9.10 Escrever teste de propriedade para HeaderCsvExtractor
    - **Property 12: Extração CSV por índice de coluna**
    - **Validates: Requirements 8.1-8.6**
  
  - [x] 9.11 Criar XmlTagExtractor
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/extractor/XmlTagExtractor.java`
    - Implementar extração por XPath
    - Suportar caminhos aninhados
    - Validar dentro do buffer
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [ ]* 9.12 Escrever testes unitários para XmlTagExtractor
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/extractor/XmlTagExtractorTest.java`
    - Testar extração com diferentes caminhos XPath
    - Testar caminhos aninhados
    - _Requirements: 16.1_
  
  - [ ]* 9.13 Escrever teste de propriedade para XmlTagExtractor
    - **Property 13: Extração XML por XPath**
    - **Validates: Requirements 9.1-9.4**
  
  - [x] 9.14 Criar JsonKeyExtractor
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/extractor/JsonKeyExtractor.java`
    - Implementar extração por caminho JSON com notação de ponto
    - Suportar caminhos aninhados
    - Validar dentro do buffer
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  
  - [ ]* 9.15 Escrever testes unitários para JsonKeyExtractor
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/extractor/JsonKeyExtractorTest.java`
    - Testar extração com diferentes caminhos JSON
    - Testar caminhos aninhados
    - _Requirements: 16.1_
  
  - [ ]* 9.16 Escrever teste de propriedade para JsonKeyExtractor
    - **Property 14: Extração JSON por caminho**
    - **Validates: Requirements 10.1-10.4**

- [x] 10. Checkpoint - Validar estratégias de extração
  - Executar testes unitários e de propriedade dos extractors
  - Verificar se todas as estratégias de extração funcionam corretamente
  - Perguntar ao usuário se há dúvidas ou ajustes necessários

- [x] 11. Implementar LayoutIdentificationService
  - [x] 11.1 Criar interface e implementação do serviço
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/LayoutIdentificationService.java`
    - Implementar método `identifyLayout(InputStream, String filename, Long acquirerId)`
    - Implementar leitura do buffer (FILE_ORIGIN_BUFFER_LIMIT)
    - Implementar detecção e conversão de encoding
    - Implementar busca de layouts por acquirer (ordenação DESC)
    - Implementar busca de regras por layout
    - Implementar aplicação de regras com operador AND
    - Implementar first-match wins
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  
  - [x] 11.2 Criar RuleValidator
    - Criar `consumer/src/main/java/com/concil/edi/consumer/service/layout/RuleValidator.java`
    - Implementar validação de configurações de regras
    - Validar campos obrigatórios por tipo de origem
    - Validar posições não negativas
    - _Requirements: 13.2_
  
  - [x] 11.3 Adicionar configuração de buffer limit
    - Adicionar propriedade `FILE_ORIGIN_BUFFER_LIMIT` em `consumer/src/main/resources/application.yml`
    - Valor padrão: 7000
    - _Requirements: 3.3, 3.4_

- [x] 12. Escrever testes para LayoutIdentificationService
  - [ ]* 12.1 Escrever testes unitários
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/LayoutIdentificationServiceTest.java`
    - Testar identificação por FILENAME (Cielo)
    - Testar identificação por HEADER CSV (Rede EEVD)
    - Testar identificação por HEADER TXT (Rede EEVC, EEFI)
    - Testar múltiplas regras com AND
    - Testar first-match wins
    - Testar falha de identificação
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7_
  
  - [ ]* 12.2 Escrever testes de propriedade
    - **Property 15: Leitura limitada ao buffer**
    - **Property 23: Filtro por adquirente e flag ativa**
    - **Property 24: Ordenação por idt_layout DESC**
    - **Property 25: Filtro de regras ativas**
    - **Property 26: Operador AND entre regras**
    - **Property 27: First-match wins**
    - **Property 28: Retorno de idt_layout**
    - **Property 32: Round-trip de configuração**
    - **Property 33: Erro descritivo para configuração inválida**
    - **Validates: Requirements 3.2, 4.1-4.7, 13.2, 13.4**

- [x] 13. Checkpoint - Validar serviço de identificação
  - Executar todos os testes do LayoutIdentificationService
  - Verificar se o algoritmo de identificação funciona corretamente
  - Perguntar ao usuário se há dúvidas ou ajustes necessários

- [x] 14. Integrar com FileTransferListener
  - [x] 14.1 Modificar FileTransferListener
    - Injetar LayoutIdentificationService
    - Após abrir InputStream, invocar identifyLayout
    - Se layout identificado, atualizar file_origin.idt_layout
    - Se layout não identificado, definir idt_layout = 0 (layout especial SEM_IDENTIFICACAO)
    - Continuar transferência normalmente
    - _Requirements: 3.1, 3.5, 3.6, 3.7, 14.1, 14.2, 14.3, 14.4_
  
  - [x] 14.2 Adicionar método em StatusUpdateService
    - Adicionar método `updateLayoutId(Long fileOriginId, Long layoutId)`
    - _Requirements: 14.2_

- [x] 15. Escrever testes de integração
  - [x]* 15.1 Criar testes de integração com banco de dados
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/layout/LayoutIdentificationIntegrationTest.java`
    - Testar carregamento de configurações do banco
    - Testar filtro por idt_acquirer
    - Testar ordenação por idt_layout DESC
    - Testar filtro por flg_active
    - Testar atualização de file_origin.idt_layout
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5_

- [x] 16. Escrever testes end-to-end
  - [x] 16.1 Criar testes E2E para fluxo completo
    - Criar `commons/src/test/java/com/concil/edi/commons/e2e/LayoutIdentificationE2ETest.java`
    - Testar transferência de arquivo Cielo identificado por FILENAME
    - Testar transferência de arquivo Rede CSV identificado por HEADER
    - Testar transferência de arquivo Rede TXT identificado por HEADER
    - Testar falha de identificação e interrupção da transferência
    - Testar atualização correta de file_origin.idt_layout
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5_

- [x] 17. Checkpoint final - Validação completa
  - Executar todos os testes (unitários, propriedade, integração, E2E)
  - Verificar se todas as propriedades de correção são satisfeitas
  - Verificar se todos os requisitos foram implementados
  - Perguntar ao usuário se há ajustes finais necessários

- [x] 18. Atualizar documentação e steering files
  - [x] 18.1 Atualizar README.md do projeto
    - Adicionar seção sobre identificação de layouts
    - Documentar tabelas layout e layout_identification_rule
    - Explicar estratégias de identificação (FILENAME, HEADER, TAG, KEY)
    - Documentar critérios de comparação e funções de transformação
    - _Requirements: Documentação completa da funcionalidade_
  
  - [x] 18.2 Atualizar DOCUMENTATION.md
    - Adicionar seção detalhada sobre o algoritmo de identificação
    - Documentar o fluxo de identificação no Consumer
    - Explicar a integração com FileTransferListener
    - Documentar configuração de encoding e buffer limit
    - _Requirements: Documentação técnica detalhada_
  
  - [x] 18.3 Criar/Atualizar steering file para scripts DDL
    - Criar `.kiro/steering/database-scripts.md`
    - Documentar localização dos scripts: `scripts/ddl/`
    - Documentar comando de execução: `make init-db`
    - Listar todos os scripts DDL disponíveis (00-07)
    - Explicar ordem de execução e dependências
    - _Requirements: Guia para inicialização do banco_
  
  - [x] 18.4 Criar/Atualizar steering file para variáveis de ambiente
    - Criar `.kiro/steering/environment-variables.md`
    - Documentar todas as variáveis de ambiente do sistema:
      - **Producer**: DB_URL, DB_USERNAME, DB_PASSWORD, RABBITMQ_HOST, SFTP_CIELO_VAULT
      - **Consumer**: DB_URL, RABBITMQ_HOST, AWS_ENDPOINT, AWS_REGION, FILE_ORIGIN_BUFFER_LIMIT, QUEUE_DELAY
      - **Infraestrutura**: Variáveis do docker-compose
    - Explicar função de cada variável
    - Documentar valores padrão e exemplos
    - Explicar QUEUE_DELAY para testes E2E
    - _Requirements: Guia completo de configuração_
  
  - [x] 18.5 Atualizar scripts/ddl/README.md
    - Adicionar documentação dos scripts 06 e 07
    - Explicar estrutura das tabelas de layout
    - Documentar exemplos de dados inseridos
    - Adicionar exemplos de queries úteis
    - _Requirements: Documentação dos scripts DDL_

## Notas

- Tasks marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada task referencia os requisitos específicos para rastreabilidade
- Checkpoints garantem validação incremental
- Testes de propriedade validam propriedades universais de correção
- Testes unitários validam exemplos específicos e casos de borda
- A implementação segue o padrão Strategy para extractors, permitindo fácil extensão
- O algoritmo de identificação usa first-match wins com ordenação DESC
- A conversão de encoding usa fallback em cadeia para máxima compatibilidade
