# Plano de Implementação: File Multiprocessing Tracking

## Visão Geral

Implementação do rastreamento granular de processamento de arquivos por cliente e etapa, criando a tabela `file_origin_processing` e o serviço `ProcessingSplitService` integrado ao fluxo de coleta do Consumer.

## Tasks

- [x] 1. Criar script DDL e atualizar inicialização do banco
  - [x] 1.1 Criar script `scripts/ddl/12_create_file_origin_processing_table.sql`
    - Drop condicional da tabela e sequence
    - Criar sequence `file_origin_processing_seq`
    - Criar tabela `file_origin_processing` com todas as colunas conforme design (idt_file_origin_processing, idt_file_origin, des_step, des_status, idt_client, des_message_error, des_message_alert, dat_step_start, dat_step_end, jsn_additional_info, dat_creation, dat_update, nam_change_agent)
    - Criar FK para `file_origin(idt_file_origin)`
    - Criar índices em `idt_file_origin` e `idt_client`
    - Adicionar comentários nas colunas e COMMIT ao final
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 Atualizar `scripts/ddl/00_run_all.sql`
    - Incluir execução do script 12 após o script 11 (insert customer identification examples)
    - Atualizar contagem total de scripts e resumo de objetos para incluir `FILE_ORIGIN_PROCESSING` e `FILE_ORIGIN_PROCESSING_SEQ`
    - _Requisitos: 2.1, 2.2_

- [x] 2. Criar entidade JPA e repositório no módulo commons
  - [x] 2.1 Criar entidade `FileOriginProcessing` em `commons/src/main/java/com/concil/edi/commons/entity/FileOriginProcessing.java`
    - Mapear todas as colunas da tabela com anotações JPA (@Entity, @Table, @Column, @Id, @GeneratedValue, @SequenceGenerator, @Enumerated)
    - Usar enums `Step` e `Status` existentes para `des_step` e `des_status`
    - Permitir `idt_client` NULL
    - Seguir padrão de `FileOriginClients` (Lombok @Data, @NoArgsConstructor, @AllArgsConstructor)
    - _Requisitos: 3.1, 3.2, 3.3, 3.4_

  - [x] 2.2 Criar repositório `FileOriginProcessingRepository` em `commons/src/main/java/com/concil/edi/commons/repository/FileOriginProcessingRepository.java`
    - Estender `JpaRepository<FileOriginProcessing, Long>`
    - Método `findByIdtFileOriginAndIdtClientAndDesStep(Long, Long, Step)` para busca com cliente
    - Método `findByIdtFileOriginAndIdtClientIsNullAndDesStep(Long, Step)` para busca sem cliente
    - Método `findByIdtFileOriginAndDesStep(Long, Step)` para listar todos os registros de um arquivo/step
    - _Requisitos: 4.1, 4.2, 4.3_

- [x] 3. Checkpoint - Verificar compilação do módulo commons
  - Garantir que o módulo commons compila sem erros, perguntar ao usuário se houver dúvidas.

- [x] 4. Implementar ProcessingSplitService no módulo consumer
  - [x] 4.1 Criar `ProcessingSplitService` em `consumer/src/main/java/com/concil/edi/consumer/service/ProcessingSplitService.java`
    - Seguir padrão do `StatusUpdateService` (@Service, @RequiredArgsConstructor, @Slf4j)
    - Usar `CHANGE_AGENT = "consumer-service"`
    - Injetar `FileOriginProcessingRepository`
    - Implementar `createInitialRecords(Long fileOriginId, List<Long> clientIds, Date stepStartTime)`:
      - Se `clientIds` vazio: criar 1 registro com `idt_client=NULL`
      - Se `clientIds` não vazio: criar 1 registro por cliente
      - Para cada registro: buscar existente (retry) via `findByIdtFileOriginAndIdtClientAndDesStep` / `findByIdtFileOriginAndIdtClientIsNullAndDesStep`; se encontrar, atualizar; se não, criar novo
      - Definir `des_step=COLETA`, `des_status=PROCESSAMENTO`, `dat_step_start=stepStartTime`, `nam_change_agent="consumer-service"`, `dat_creation=new Date()`
    - Implementar `completeRecords(Long fileOriginId)`:
      - Buscar todos os registros com `findByIdtFileOriginAndDesStep(fileOriginId, COLETA)`
      - Atualizar cada registro: `des_status=CONCLUIDO`, `dat_step_end=new Date()`, `dat_update=new Date()`, `des_message_error=NULL`
    - Implementar `failRecords(Long fileOriginId, String errorMessage)`:
      - Buscar todos os registros com `findByIdtFileOriginAndDesStep(fileOriginId, COLETA)`
      - Atualizar cada registro: `des_status=ERRO`, `des_message_error=errorMessage`, `dat_step_end=new Date()`, `dat_update=new Date()`
    - Envolver chamadas em try-catch para não interromper o fluxo principal (log de erro apenas)
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 6.1, 6.2, 7.1, 7.2, 8.1, 8.2, 9.1_

  - [ ]* 4.2 Escrever teste de propriedade para criação de registros iniciais
    - **Property 1: Criação de registros iniciais preserva a correspondência com clientes**
    - Gerar listas aleatórias de clientIds (0 a 10 elementos) e timestamps aleatórios
    - Verificar que `max(1, clientIds.size())` registros são salvos com campos corretos
    - Mockar `FileOriginProcessingRepository`
    - Tag: `Feature: file-multiprocessing-tracking, Property 1: Criação de registros iniciais preserva a correspondência com clientes`
    - **Valida: Requisitos 5.1, 5.2, 5.3, 5.5**

  - [ ]* 4.3 Escrever teste de propriedade para finalização com sucesso
    - **Property 2: Finalização com sucesso atualiza todos os registros para CONCLUIDO**
    - Gerar N registros existentes (1 a 5), chamar `completeRecords`, verificar que todos têm `des_status=CONCLUIDO`, `dat_step_end` não nulo, `des_message_error=NULL`
    - Tag: `Feature: file-multiprocessing-tracking, Property 2: Finalização com sucesso atualiza todos os registros para CONCLUIDO`
    - **Valida: Requisitos 6.1, 6.2**

  - [ ]* 4.4 Escrever teste de propriedade para finalização com erro
    - **Property 3: Finalização com erro propaga a mensagem para todos os registros**
    - Gerar N registros existentes e mensagens de erro aleatórias, chamar `failRecords`, verificar atualização
    - Tag: `Feature: file-multiprocessing-tracking, Property 3: Finalização com erro propaga a mensagem para todos os registros`
    - **Valida: Requisitos 7.1, 7.2**

  - [ ]* 4.5 Escrever teste de propriedade para idempotência de retry
    - **Property 4: Retry no mesmo ciclo é idempotente em contagem de registros**
    - Gerar clientIds, chamar `createInitialRecords` duas vezes com mesmos parâmetros, verificar que não há duplicatas
    - Tag: `Feature: file-multiprocessing-tracking, Property 4: Retry no mesmo ciclo é idempotente em contagem de registros`
    - **Valida: Requisitos 8.1, 8.2**

  - [ ]* 4.6 Escrever testes unitários para ProcessingSplitService
    - Testar criação com 0 clientes (idt_client=NULL)
    - Testar criação com 1 cliente
    - Testar criação com múltiplos clientes
    - Testar completeRecords atualiza todos os registros
    - Testar failRecords atualiza todos os registros com mensagem de erro
    - Testar retry atualiza registro existente ao invés de criar novo
    - Testar que exceções no repositório são capturadas e logadas sem propagar
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 6.1, 6.2, 7.1, 7.2, 8.1, 8.2_

- [x] 5. Checkpoint - Verificar compilação e testes do ProcessingSplitService
  - Garantir que todos os testes passam, perguntar ao usuário se houver dúvidas.

- [x] 6. Integrar ProcessingSplitService no FileTransferListener
  - [x] 6.1 Modificar `FileTransferListener` para integrar o `ProcessingSplitService`
    - Injetar `ProcessingSplitService` como dependência
    - Capturar `Date stepStartTime = new Date()` no início do `handleFileTransfer`, antes de qualquer operação
    - Após persistência dos `file_origin_clients`: chamar `processingSplitService.createInitialRecords(fileOriginId, identifiedClients, stepStartTime)` dentro de try-catch
    - Após `executeRemoval` com sucesso: chamar `processingSplitService.completeRecords(fileOriginId)` dentro de try-catch
    - No `handleError`: chamar `processingSplitService.failRecords(fileOriginId, e.getMessage())` dentro de try-catch
    - Manter `jsn_additional_info` e `des_message_alert` como NULL
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [ ]* 6.2 Escrever testes unitários para as modificações no FileTransferListener
    - Verificar que `createInitialRecords` é chamado após identificação de clientes
    - Verificar que `completeRecords` é chamado no fluxo de sucesso
    - Verificar que `failRecords` é chamado no fluxo de erro
    - Verificar que falha no `ProcessingSplitService` não interrompe o fluxo principal
    - Verificar que `stepStartTime` é capturado antes de qualquer operação
    - _Requisitos: 10.1, 10.2, 10.3, 10.4_

- [x] 7. Checkpoint final - Verificar compilação e testes completos
  - Garantir que todos os testes passam, perguntar ao usuário se houver dúvidas.

## Notas

- Tasks marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada task referencia requisitos específicos para rastreabilidade
- Checkpoints garantem validação incremental
- Testes de propriedade validam propriedades universais de corretude definidas no design
- Testes unitários validam exemplos específicos e casos de borda
