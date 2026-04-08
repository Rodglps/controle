# Plano de Implementação: Remove File Origin

## Visão Geral

Implementação incremental da funcionalidade de remoção do arquivo da pasta de origem após transferência bem-sucedida. O fluxo inclui validação de integridade por tamanho, remoção via SFTP, tratamento de falha com marcador `REMOVE_ORIGIN_FILE_ERROR` e suporte a retentativas seletivas no Consumer.

## Tasks

- [x] 1. Criar RemoveOriginService
  - [x] 1.1 Implementar `RemoveOriginService` em `consumer/src/main/java/com/concil/edi/consumer/service/RemoveOriginService.java`
    - Anotar com `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
    - Injetar `ServerPathRepository` e `SftpConfig`
    - Implementar `removeFile(Long serverPathOriginId, String filename)`:
      - Buscar `ServerPath` pelo `serverPathOriginId` (lançar `IllegalArgumentException` se não encontrado)
      - Obter `SessionFactory` via `sftpConfig.getOrCreateSessionFactory(codVault, desVaultSecret)`
      - Abrir sessão e chamar `session.remove(remotePath)` onde `remotePath = desPath + "/" + filename`
      - Propagar qualquer exceção sem capturar (o chamador trata o erro)
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

  - [ ]* 1.2 Escrever testes unitários para `RemoveOriginService`
    - Criar `consumer/src/test/java/com/concil/edi/consumer/service/RemoveOriginServiceTest.java`
    - Testar chamada SFTP com parâmetros corretos (verificar `remotePath` construído)
    - Testar propagação de exceção quando `session.remove` lança `RuntimeException`
    - Testar `IllegalArgumentException` quando `serverPathOriginId` não existe
    - _Requisitos: 5.3, 5.4_

- [x] 2. Adicionar métodos de verificação de tamanho no FileUploadService
  - [x] 2.1 Implementar `getS3ObjectSize(String bucketName, String key)` em `FileUploadService`
    - Usar `s3Client.headObject(HeadObjectRequest)` para obter metadados sem baixar o arquivo
    - Retornar `headObjectResponse.contentLength()`
    - Lançar `RuntimeException` em caso de falha
    - _Requisitos: 1.1_

  - [x] 2.2 Implementar `getSftpFileSize(ServerConfigurationDTO config, String remotePath)` em `FileUploadService`
    - Obter `SessionFactory` via `sftpConfig.getOrCreateSessionFactory(codVault, desVaultSecret)`
    - Usar `session.list(remotePath)` ou `session.getClientInstance()` para obter atributos do arquivo
    - Retornar o tamanho em bytes do arquivo remoto
    - Lançar `RuntimeException` em caso de falha
    - _Requisitos: 1.1_

  - [ ]* 2.3 Escrever testes unitários para os novos métodos de `FileUploadService`
    - Criar ou atualizar `consumer/src/test/java/com/concil/edi/consumer/service/FileUploadServiceTest.java`
    - Testar `getS3ObjectSize` com mock do `S3Client`
    - Testar `getSftpFileSize` com mock do `SftpConfig`
    - Testar propagação de exceção em ambos os métodos
    - _Requisitos: 1.1_

- [x] 3. Modificar FileTransferListener — métodos auxiliares
  - [x] 3.1 Adicionar constante e método `isPendingRemoval` em `FileTransferListener`
    - Declarar `static final String REMOVE_ORIGIN_FILE_ERROR = "REMOVE_ORIGIN_FILE_ERROR"`
    - Implementar `isPendingRemoval(FileOrigin fileOrigin)`:
      - Retornar `true` se `fileOrigin.getDesStatus() == Status.ERRO` e `desMessageError` contém `REMOVE_ORIGIN_FILE_ERROR`
      - Retornar `false` em qualquer outro caso (incluindo `desMessageError` nulo)
    - _Requisitos: 3.1_

  - [x] 3.2 Implementar `getDestinationFileSize` em `FileTransferListener`
    - Assinatura: `private long getDestinationFileSize(ServerConfigurationDTO destConfig, String filename, String keyOrPath)`
    - Para `ServerType.S3`: extrair `bucketName` e `key` do `desPath` (mesmo parsing já existente no upload) e chamar `fileUploadService.getS3ObjectSize(bucketName, key)`
    - Para `ServerType.SFTP`: chamar `fileUploadService.getSftpFileSize(destConfig, keyOrPath)`
    - Lançar `UnsupportedOperationException` para tipos não suportados
    - _Requisitos: 1.1_

  - [x] 3.3 Implementar `executeRemoval` em `FileTransferListener`
    - Assinatura: `private void executeRemoval(Long fileOriginId, Long serverPathOriginId, String filename, FileOrigin fileOrigin)`
    - Verificar `fileOrigin.getNumRetry() >= fileOrigin.getMaxRetry()`: se verdadeiro, chamar `statusUpdateService.updateStatusWithError` com mensagem `"REMOVE_ORIGIN_FILE_ERROR. arquivo " + filename + ", motivo: limite de tentativas atingido"` e retornar sem executar remoção
    - Caso contrário, chamar `removeOriginService.removeFile(serverPathOriginId, filename)`
    - Em caso de sucesso: chamar `statusUpdateService.updateStatus(fileOriginId, Status.CONCLUIDO)`
    - Em caso de exceção: chamar `statusUpdateService.updateStatusWithError` com `step=COLETA` e mensagem `"REMOVE_ORIGIN_FILE_ERROR. arquivo " + filename + ", motivo: " + e.getMessage()`; atualizar `desStep` para `Step.COLETA`; relançar `ListenerExecutionFailedException` para forçar NACK quando `numRetry < maxRetry`
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 4.2, 4.3, 4.4_

- [x] 4. Integrar remoção no fluxo principal do FileTransferListener
  - [x] 4.1 Adicionar injeção de `RemoveOriginService` e `FileOriginRepository` (se não presente) em `FileTransferListener`
    - Adicionar campo `private final RemoveOriginService removeOriginService`
    - _Requisitos: 5.1_

  - [x] 4.2 Integrar `isPendingRemoval` no início de `handleFileTransfer`
    - Logo após receber a mensagem (antes de `updateStatus(PROCESSAMENTO)`), buscar o `FileOrigin` pelo `fileOriginId`
    - Chamar `isPendingRemoval(fileOrigin)`: se `true`, chamar `executeRemoval(...)` e retornar imediatamente (sem executar download, identificação ou upload)
    - _Requisitos: 3.1, 3.2, 3.3, 3.4_

  - [x] 4.3 Integrar validação de integridade e remoção após upload bem-sucedido
    - Após o bloco de upload (S3 ou SFTP), antes de atualizar status para `CONCLUIDO`:
      - Calcular `keyOrPath` (mesma lógica do upload)
      - Chamar `getDestinationFileSize(destConfig, filename, keyOrPath)`
      - Comparar com `fileSize` (campo `num_file_size` da mensagem)
      - Se divergir: chamar `statusUpdateService.updateStatusWithError(fileOriginId, Status.ERRO, "Erro de integridade: tamanho do arquivo no destino difere do esperado")` e retornar sem remover
      - Se igual: chamar `executeRemoval(fileOriginId, serverPathOriginId, filename, fileOrigin)`
    - Remover a chamada direta a `statusUpdateService.updateStatus(fileOriginId, Status.CONCLUIDO)` que existia antes (agora é responsabilidade de `executeRemoval`)
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2_

- [x] 5. Checkpoint — verificar compilação e testes unitários
  - Garantir que todos os testes unitários passam. Verificar se há erros de compilação. Perguntar ao usuário se houver dúvidas.

- [x] 6. Testes de propriedade com jqwik
  - [x] 6.1 Criar `RemoveFileOriginPropertyTest` e implementar Propriedade 1
    - Criar `consumer/src/test/java/com/concil/edi/consumer/RemoveFileOriginPropertyTest.java`
    - Comentário: `// Feature: remove-file-origin, Propriedade 1: Validação de integridade determina o fluxo`
    - Gerar pares `(long tamanhoDestino, long numFileSize)` aleatórios com `@ForAll @LongRange`
    - Verificar que a comparação `tamanhoDestino == numFileSize` é a única condição que permite chamar `executeRemoval`; quando divergem, `executeRemoval` nunca é invocado
    - Mínimo 100 iterações (`@Property(tries = 100)`)
    - **Propriedade 1: Validação de integridade determina o fluxo**
    - **Valida: Requisitos 1.1, 1.2, 1.3, 1.4**

  - [ ]* 6.2 Implementar Propriedade 2 em `RemoveFileOriginPropertyTest`
    - Comentário: `// Feature: remove-file-origin, Propriedade 2: Remoção bem-sucedida resulta em CONCLUIDO`
    - Gerar `FileOrigin` com `numRetry < maxRetry` e tamanhos iguais; mock SFTP sem exceção
    - Verificar que o status final do `file_origin` é `CONCLUIDO`
    - **Propriedade 2: Remoção bem-sucedida resulta em CONCLUIDO**
    - **Valida: Requisitos 2.1, 2.2**

  - [ ]* 6.3 Implementar Propriedade 3 em `RemoveFileOriginPropertyTest`
    - Comentário: `// Feature: remove-file-origin, Propriedade 3: Falha na remoção registra erro com marcador`
    - Gerar qualquer `RuntimeException` com mensagem aleatória; mock SFTP que lança a exceção
    - Verificar que `desMessageError` contém `REMOVE_ORIGIN_FILE_ERROR` e `desStep == Step.COLETA`
    - **Propriedade 3: Falha na remoção registra erro com marcador**
    - **Valida: Requisitos 2.3, 2.4, 5.4**

  - [ ]* 6.4 Implementar Propriedade 4 em `RemoveFileOriginPropertyTest`
    - Comentário: `// Feature: remove-file-origin, Propriedade 4: Detecção de remoção pendente pula a transferência`
    - Gerar `FileOrigin` com `status=ERRO` e `desMessageError` contendo `REMOVE_ORIGIN_FILE_ERROR`
    - Verificar que os mocks de `FileDownloadService`, `LayoutIdentificationService`, `CustomerIdentificationService` e `FileUploadService` nunca são invocados
    - **Propriedade 4: Detecção de remoção pendente pula a transferência**
    - **Valida: Requisitos 3.1, 3.2, 3.3**

  - [ ]* 6.5 Implementar Propriedade 5 em `RemoveFileOriginPropertyTest`
    - Comentário: `// Feature: remove-file-origin, Propriedade 5: Limite de tentativas encerra o processamento`
    - Gerar pares `(numRetry, maxRetry)` onde `numRetry >= maxRetry` com `@ForAll @IntRange`
    - Verificar que o mock de `RemoveOriginService.removeFile` nunca é invocado e o status final é `ERRO`
    - **Propriedade 5: Limite de tentativas encerra o processamento**
    - **Valida: Requisitos 4.2, 4.3, 4.4**

- [x] 7. Adicionar `findPendingRemovalFiles` ao FileOriginRepository (opcional)
  - Adicionar query em `commons/src/main/java/com/concil/edi/commons/repository/FileOriginRepository.java`:
    ```java
    @Query("SELECT f FROM FileOrigin f WHERE f.desStep = :step AND f.desStatus = :status " +
           "AND f.desMessageError LIKE %:marker% AND f.numRetry < f.maxRetry AND f.flgActive = 1")
    List<FileOrigin> findPendingRemovalFiles(@Param("step") Step step, @Param("status") Status status, @Param("marker") String marker);
    ```
  - _Requisitos: 3.1 (suporte a consultas específicas — o `findFailedPublications` existente já cobre o Producer)_

- [x] 8. Checkpoint final — garantir que todos os testes passam
  - Executar `mvn test -pl consumer` e verificar que todos os testes unitários e de propriedade passam. Perguntar ao usuário se houver dúvidas.

## Notas

- Tasks marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada task referencia os requisitos correspondentes para rastreabilidade
- Os testes de propriedade usam jqwik 1.8.2 (já presente no projeto)
- O `StatusUpdateService` não possui método para atualizar `desStep` isoladamente — a atualização de `step=COLETA` deve ser feita diretamente no `FileOrigin` antes de salvar via `statusUpdateService` ou adicionando um novo método ao `StatusUpdateService`
