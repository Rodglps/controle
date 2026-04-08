# Prompt — Implementação da Melhoria de Cobertura E2E

## Objetivo

Implementar os cenários de teste E2E ausentes identificados na análise de cobertura,
cobrindo todos os gaps listados em `requirements.md`, priorizando do maior para o menor valor.

---

## Contexto do Projeto

- Mono repositório Maven com módulos `commons`, `producer`, `consumer`
- Testes E2E em `commons/src/test/java/com/concil/edi/commons/e2e/`
- Infraestrutura via docker-compose (Oracle, RabbitMQ, LocalStack S3, SFTP origem, SFTP destino)
- Base class `E2ETestBase` fornece helpers para SFTP, S3, banco de dados e integridade de arquivos
- Testes usam JUnit 5 + jqwik 1.8.2 para property-based tests
- Java 21, Spring Boot 3.4.0

Leia os arquivos de referência antes de implementar:
- `#[[file:commons/src/test/java/com/concil/edi/commons/e2e/E2ETestBase.java]]`
- `#[[file:commons/src/test/java/com/concil/edi/commons/e2e/FileTransferE2ETest.java]]`
- `#[[file:commons/src/test/java/com/concil/edi/commons/e2e/LayoutIdentificationE2ETest.java]]`
- `#[[file:commons/src/test/java/com/concil/edi/commons/e2e/CustomerIdentificationE2ETest.java]]`
- `#[[file:consumer/src/main/java/com/concil/edi/consumer/service/RemoveOriginService.java]]`
- `#[[file:consumer/src/main/java/com/concil/edi/consumer/service/StatusUpdateService.java]]`
- `#[[file:commons/src/main/java/com/concil/edi/commons/service/extractor/XmlTagExtractor.java]]`
- `#[[file:commons/src/main/java/com/concil/edi/commons/service/extractor/JsonKeyExtractor.java]]`
- `#[[file:producer/src/main/java/com/concil/edi/producer/service/FileRegistrationService.java]]`

---

## Tarefas

### Tarefa 1 — Transferência SFTP → SFTP (Alta Prioridade)

**Arquivo:** `FileTransferE2ETest.java`

Implementar o método `testSftpToSftpTransfer()` que já está documentado no README mas nunca foi criado.

Requisitos:
1. Gerar arquivo CSV de 500KB com conteúdo aleatório legível
2. Fazer upload para SFTP origem (`/upload`)
3. Aguardar Producer detectar e registrar em `file_origin` (COLETA/EM_ESPERA)
4. Aguardar Consumer processar e status chegar a CONCLUIDO
5. Validar que o arquivo existe no SFTP destino (`/destination`)
6. Validar integridade: tamanho e SHA-256 devem ser idênticos ao original
7. Validar registro final em `file_origin` (COLETA/CONCLUIDO)
8. Output com `✓` em cada step, igual ao padrão existente

Adicionar em `E2ETestBase` o método auxiliar:
```java
protected boolean fileExistsInSftpOrigin(String filename) throws Exception
```

---

### Tarefa 2 — Remoção do Arquivo de Origem (Alta Prioridade)

**Arquivo:** Novo arquivo `RemoveOriginE2ETest.java`

Implementar teste que valida o comportamento do `RemoveOriginService` no fluxo completo.

Requisitos:
1. Fazer upload de arquivo para SFTP origem
2. Aguardar Producer detectar e Consumer processar até CONCLUIDO
3. Validar que o arquivo **não existe mais** no SFTP de origem após CONCLUIDO
4. Usar o método `fileExistsInSftpOrigin()` adicionado na Tarefa 1
5. Validar que o arquivo existe no destino (S3 ou SFTP destino)

Adicionar em `E2ETestBase` o método auxiliar:
```java
protected boolean fileExistsInSftpOrigin(String filename) throws Exception
```
(se não foi adicionado na Tarefa 1)

---

### Tarefa 3 — Detecção de Duplicata (Média Prioridade)

**Arquivo:** Novo arquivo `DuplicateDetectionE2ETest.java`

Implementar teste que valida que o Producer não registra o mesmo arquivo duas vezes.

Requisitos:
1. Fazer upload do arquivo para SFTP origem
2. Aguardar Producer detectar e registrar (1º registro)
3. Fazer upload do **mesmo arquivo** novamente (mesmo nome)
4. Aguardar 1 ciclo completo do scheduler (150 segundos)
5. Consultar `file_origin` e validar que existe **apenas 1 registro** para aquele filename + acquirer + timestamp
6. Validar que o status do registro único chegou a CONCLUIDO

Observação: a constraint única em `file_origin` é `(des_file_name, idt_acquirer, dat_timestamp_file, flg_active)`.
O teste deve verificar a contagem de registros via query direta no banco.

---

### Tarefa 4 — Arquivo Vazio e Muito Pequeno (Média Prioridade)

**Arquivo:** Novo arquivo `EdgeCaseFileTransferE2ETest.java`

Implementar cenários de edge case para arquivos fora do padrão.

Cenário 4a — Arquivo muito pequeno (< 100 bytes):
1. Gerar arquivo com 50 bytes de conteúdo
2. Nome do arquivo deve corresponder a um layout conhecido (ex: `cielo_v15_venda_tiny.txt`)
3. Validar que identificação por FILENAME ainda funciona corretamente
4. Validar que transferência completa com CONCLUIDO

Cenário 4b — Arquivo no limite do buffer (exatamente 7000 bytes):
1. Gerar arquivo com exatamente 7000 bytes
2. Nome do arquivo deve corresponder a um layout conhecido
3. Validar que identificação funciona no limite do buffer
4. Validar que transferência completa com CONCLUIDO

Cenário 4c — Arquivo vazio (0 bytes):
1. Gerar arquivo com 0 bytes
2. Validar comportamento do sistema (CONCLUIDO com layout 0, ou ERRO — documentar o comportamento esperado)
3. Não deve lançar NullPointerException ou erro não tratado

---

### Tarefa 5 — Cenário de Erro e Retry (Média Prioridade)

**Arquivo:** Novo arquivo `ErrorAndRetryE2ETest.java`

Implementar testes que validam o fluxo de erro e incremento de retry.

Observação: para simular falha de transferência sem modificar o código de produção,
usar uma abordagem de dados inválidos ou configuração de destino indisponível.
Avaliar a melhor estratégia disponível no ambiente docker-compose atual.

Cenário 5a — Registro de erro em `file_origin`:
1. Provocar falha na transferência (estratégia a definir)
2. Validar que `des_status` = `ERRO` em `file_origin`
3. Validar que `des_message_error` está preenchido
4. Validar que `num_retry` foi incrementado

Cenário 5b — Arquivo com max_retry atingido:
1. Simular arquivo que já atingiu `num_retry = max_retry`
2. Validar que o Producer não republica mensagem para esse arquivo
3. Validar que o status permanece `ERRO`

---

### Tarefa 6 — Identificação por XML TAG (Baixa Prioridade)

**Arquivo:** `LayoutIdentificationE2ETest.java` (adicionar cenários) + script DDL

Pré-requisito: inserir no banco de dados um layout com regra de identificação por XML TAG.

Criar script DDL `scripts/ddl/12_insert_xml_json_layout_examples.sql` com:
- Layout `XML_TEST_01` para acquirer 1
- Regra: `des_value_origin = 'TAG'`, `des_tag_path = 'root/type'`, `des_value = 'EDI_XML'`, `des_criteria_type = 'IGUAL'`
- Layout `JSON_TEST_01` para acquirer 1
- Regra: `des_value_origin = 'KEY'`, `des_key_path = 'header.type'`, `des_value = 'EDI_JSON'`, `des_criteria_type = 'IGUAL'`

Cenário 6a — Arquivo XML identificado por TAG:
1. Gerar arquivo XML com tag `<root><type>EDI_XML</type>...</root>`
2. Nome do arquivo: `arquivo_xml_test_<timestamp>.xml`
3. Validar que layout `XML_TEST_01` é identificado
4. Validar transferência com CONCLUIDO

Cenário 6b — Arquivo XML sem tag correspondente → Layout 0:
1. Gerar arquivo XML sem a tag esperada
2. Validar que `idt_layout = 0` (SEM_IDENTIFICACAO)
3. Validar transferência com CONCLUIDO

---

### Tarefa 7 — Identificação por JSON KEY (Baixa Prioridade)

**Arquivo:** `LayoutIdentificationE2ETest.java` (adicionar cenários)

Pré-requisito: script DDL da Tarefa 6 já executado.

Cenário 7a — Arquivo JSON identificado por KEY:
1. Gerar arquivo JSON com `{"header": {"type": "EDI_JSON"}, "data": [...]}`
2. Nome do arquivo: `arquivo_json_test_<timestamp>.json`
3. Validar que layout `JSON_TEST_01` é identificado
4. Validar transferência com CONCLUIDO

Cenário 7b — Arquivo JSON sem chave correspondente → Layout 0:
1. Gerar arquivo JSON sem a chave esperada
2. Validar que `idt_layout = 0` (SEM_IDENTIFICACAO)
3. Validar transferência com CONCLUIDO

---

### Tarefa 8 — Melhoria de Output (Todas as classes)

**Arquivos:** `E2ETestBase.java` + todas as classes de teste

Adicionar em `E2ETestBase` um mecanismo de sumário de execução:

```java
// Registrar início de cenário
protected void startScenario(String name) { ... }

// Registrar step com resultado
protected void logStep(int stepNumber, String description, boolean passed) { ... }

// Imprimir sumário ao final
protected void printSummary() { ... }
```

O sumário deve exibir ao final de cada classe de teste:
```
╔══════════════════════════════════════════════════════╗
║           E2E TEST SUMMARY - FileTransferE2ETest     ║
╠══════════════════════════════════════════════════════╣
║  Scenario 1: SFTP to S3 Transfer          ✓ PASSED  ║
║  Scenario 2: SFTP to SFTP Transfer        ✓ PASSED  ║
╠══════════════════════════════════════════════════════╣
║  Total: 2 passed, 0 failed                           ║
║  Duration: 3m 42s                                    ║
╚══════════════════════════════════════════════════════╝
```

---

## Padrões a Seguir

### Estrutura de cada teste
```java
@Test
@Order(N)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public void testNomeDoScenario() throws Exception {
    System.out.println("\n=== E2E Test: Descrição do Cenário ===\n");
    
    // Arrange
    String filename = "prefixo_" + System.currentTimeMillis() + ".ext";
    byte[] content = generateXxxContent();
    
    // Act & Assert via steps numerados com output ✓
    System.out.println("\n[Step 1] Descrição...");
    // ...
    System.out.println("✓ Validação concluída");
    
    System.out.println("\n=== E2E Test PASSED: Descrição ===\n");
}
```

### Timeouts padrão
- Detecção pelo Producer: 150 segundos
- Processamento pelo Consumer: 120 segundos
- Timeout total do teste: 5 minutos

### Geração de conteúdo de teste
- Usar `System.currentTimeMillis()` no nome do arquivo para evitar colisões
- Conteúdo deve ser determinístico para validação de SHA-256
- Métodos `generateXxxContent()` privados na própria classe de teste

### Validações obrigatórias em todo cenário de transferência bem-sucedida
1. Registro em `file_origin` com COLETA/EM_ESPERA (estado inicial)
2. Status final COLETA/CONCLUIDO
3. Arquivo existe no destino (S3 ou SFTP)
4. Integridade: tamanho e SHA-256

---

## Critérios de Aceite

- [ ] Todos os 7 gaps cobertos com pelo menos 1 cenário E2E cada
- [ ] Nenhum teste usa `Thread.sleep()` fixo — usar `waitForFileStatus()` e `waitForFileOriginRecord()`
- [ ] Output legível com `✓` em cada step e sumário final por classe
- [ ] Novos métodos auxiliares adicionados em `E2ETestBase` (não duplicar lógica nas subclasses)
- [ ] Script DDL `12_insert_xml_json_layout_examples.sql` criado e referenciado em `00_run_all.sql`
- [ ] README.md do pacote e2e atualizado com os novos cenários
