# E2E - Melhoria de Cobertura de Testes

## Relatório de Análise

### Contexto

Análise realizada sobre os testes E2E existentes em `commons/src/test/java/com/concil/edi/commons/e2e/`
comparando com a lógica de negócio implementada nos módulos `producer` e `consumer`.

---

## Cobertura Atual (Happy Paths bem cobertos)

### FileTransferE2ETest
- ✅ Cenário 1: Transferência SFTP → S3 com validação de integridade (SHA-256 + tamanho)
- ✅ 7 property tests de preservação (SHA-256, S3 existence, DB connection, file_origin validation, content generation, infra config, S3 client)
- ❌ Cenário SFTP → SFTP: **mencionado no README mas nunca implementado**

### LayoutIdentificationE2ETest
- ✅ Cenário 1: Cielo VENDA identificado por FILENAME
- ✅ Cenário 2: Cielo PAGTO identificado por FILENAME
- ✅ Cenário 3: Rede EEVD CSV identificado por HEADER (coluna CSV)
- ✅ Cenário 4: Rede EEVC TXT identificado por HEADER (offset de bytes)
- ✅ Cenário 5: Rede EEFI TXT identificado por HEADER (distingue EEVC vs EEFI)
- ✅ Cenário 6: Arquivo sem layout → Layout 0 (SEM_IDENTIFICACAO)

### CustomerIdentificationE2ETest
- ✅ Cenário 1: Múltiplos clientes identificados por FILENAME com ordenação por peso
- ✅ Cenário 2: Nenhum cliente identificado (processamento continua)
- ✅ Cenário 3: Cliente identificado por HEADER em arquivo TXT
- ✅ Cenário 4: Cliente identificado por HEADER em arquivo CSV

---

## Gaps Identificados

### GAP 1 — Transferência SFTP → SFTP (Alta Prioridade)
**Serviço:** `SftpUploadService`, `FileDownloadService`
**Situação:** Mencionado no README como `testSftpToSftpTransfer` mas nunca implementado.
**Infraestrutura disponível:** `fileExistsInSftpDestination()`, `downloadFromSftpDestination()` já existem em `E2ETestBase`.
**Esforço estimado:** ~1h

Cenários a cobrir:
- Upload de arquivo CSV para SFTP origem
- Producer detecta e registra (COLETA/EM_ESPERA)
- Consumer transfere via streaming para SFTP destino
- Validação de integridade no destino (tamanho + SHA-256)
- Validação do registro final (COLETA/CONCLUIDO)

### GAP 2 — Remoção do Arquivo de Origem após Transferência (Alta Prioridade)
**Serviço:** `RemoveOriginService`
**Situação:** Nenhum teste E2E valida que o arquivo é removido do SFTP de origem após CONCLUIDO.
**Esforço estimado:** ~1h

Cenários a cobrir:
- Após transferência CONCLUIDA, arquivo NÃO deve existir no SFTP de origem
- Validar via `fileExistsInSftpOrigin()` (método a ser adicionado em E2ETestBase)

### GAP 3 — Detecção de Duplicata (Média Prioridade)
**Serviço:** `FileRegistrationService.fileExists()`
**Situação:** Subir o mesmo arquivo duas vezes não deve criar dois registros em `file_origin`.
**Esforço estimado:** ~1h

Cenários a cobrir:
- Upload do mesmo arquivo (mesmo nome + timestamp) duas vezes
- Apenas 1 registro deve existir em `file_origin`
- Segundo upload deve ser ignorado pelo Producer

### GAP 4 — Arquivo Vazio / Muito Pequeno (Média Prioridade)
**Serviço:** `LayoutIdentificationService` (buffer de 7000 bytes)
**Situação:** Edge case do buffer de identificação não testado.
**Esforço estimado:** ~30min

Cenários a cobrir:
- Arquivo com 0 bytes → deve ser tratado sem NPE
- Arquivo com menos de 100 bytes → identificação por FILENAME ainda funciona
- Arquivo com exatamente 7000 bytes (limite do buffer)

### GAP 5 — Cenário de Erro e Retry (Média Prioridade)
**Serviço:** `StatusUpdateService.updateStatusWithError()`, `StatusUpdateService.incrementRetry()`
**Situação:** Nenhum teste E2E valida o fluxo de erro com incremento de `num_retry`.
**Esforço estimado:** ~2-3h

Cenários a cobrir:
- Falha na transferência → status `ERRO` registrado em `file_origin`
- Campo `des_message_error` preenchido com mensagem de erro
- Campo `num_retry` incrementado corretamente
- Arquivo com `num_retry >= max_retry` não é reprocessado

### GAP 6 — Identificação por XML TAG (Baixa Prioridade)
**Serviço:** `XmlTagExtractor`
**Situação:** Extractor existe no código mas zero cenários E2E o exercitam.
**Esforço estimado:** ~2h (requer inserção de layout + regras XML no banco de teste)

Cenários a cobrir:
- Arquivo XML com tag correspondente → layout identificado
- Arquivo XML sem tag correspondente → Layout 0

### GAP 7 — Identificação por JSON KEY (Baixa Prioridade)
**Serviço:** `JsonKeyExtractor`
**Situação:** Extractor existe no código mas zero cenários E2E o exercitam.
**Esforço estimado:** ~2h (requer inserção de layout + regras JSON no banco de teste)

Cenários a cobrir:
- Arquivo JSON com chave correspondente → layout identificado
- Arquivo JSON sem chave correspondente → Layout 0

---

## Melhorias de Output

### Situação atual
Output via `System.out.println` com prefixo `✓`. Funcional mas sem sumário estruturado.

### Melhoria proposta
Adicionar em `E2ETestBase` um mecanismo de sumário por classe de teste:
- Contagem de cenários executados / passados / falhos
- Tempo total de execução por cenário
- Tabela final com resultado de cada step

---

## Resumo do Esforço

| Gap | Cenário | Esforço | Prioridade |
|-----|---------|---------|------------|
| 1 | SFTP → SFTP transfer | ~1h | Alta |
| 2 | Remoção de arquivo de origem | ~1h | Alta |
| 3 | Detecção de duplicata | ~1h | Média |
| 4 | Arquivo vazio / muito pequeno | ~30min | Média |
| 5 | Erro + retry | ~2-3h | Média |
| 6 | Identificação por XML TAG | ~2h | Baixa |
| 7 | Identificação por JSON KEY | ~2h | Baixa |

**Total estimado: 9-10h**

---

## Arquivos Relevantes

### Testes E2E existentes
- `commons/src/test/java/com/concil/edi/commons/e2e/E2ETestBase.java`
- `commons/src/test/java/com/concil/edi/commons/e2e/FileTransferE2ETest.java`
- `commons/src/test/java/com/concil/edi/commons/e2e/LayoutIdentificationE2ETest.java`
- `commons/src/test/java/com/concil/edi/commons/e2e/CustomerIdentificationE2ETest.java`

### Serviços de produção não cobertos por E2E
- `consumer/src/main/java/com/concil/edi/consumer/service/RemoveOriginService.java`
- `consumer/src/main/java/com/concil/edi/consumer/service/StatusUpdateService.java`
- `consumer/src/main/java/com/concil/edi/consumer/service/StreamingService.java`
- `commons/src/main/java/com/concil/edi/commons/service/extractor/XmlTagExtractor.java`
- `commons/src/main/java/com/concil/edi/commons/service/extractor/JsonKeyExtractor.java`
- `producer/src/main/java/com/concil/edi/producer/service/FileRegistrationService.java`

### Scripts DDL de referência
- `scripts/ddl/07_insert_layout_examples.sql`
- `scripts/ddl/11_insert_customer_identification_examples.sql`
