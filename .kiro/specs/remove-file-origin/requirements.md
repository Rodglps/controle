# Documento de Requisitos

## Introdução

A funcionalidade **Remove File Origin** tem como objetivo limpar o arquivo da pasta de origem após a confirmação de que ele foi transferido com sucesso para o destino. O processo inclui validação de integridade por comparação de tamanho em bytes, tratamento de falha na remoção com registro de erro, e suporte a retentativas seletivas — onde o Consumer identifica que o arquivo já foi transferido e executa apenas a etapa de remoção pendente.

## Glossário

- **Consumer**: Serviço responsável por consumir mensagens do RabbitMQ e executar a transferência e remoção de arquivos.
- **Producer**: Serviço responsável por coletar arquivos e publicar mensagens no RabbitMQ, incluindo o reenvio em caso de retentativa.
- **FileOrigin**: Entidade da tabela `file_origin` que rastreia o estado de cada arquivo no pipeline de processamento.
- **RemoveOriginService**: Serviço do módulo consumer responsável por remover o arquivo da pasta de origem via SFTP.
- **StatusUpdateService**: Serviço responsável por atualizar o status e a mensagem de erro do registro `file_origin` no banco de dados.
- **REMOVE_ORIGIN_FILE_ERROR**: Constante de texto usada como marcador na mensagem de erro para identificar falhas específicas de remoção de arquivo na origem.
- **num_file_size**: Campo da tabela `file_origin` que armazena o tamanho em bytes do arquivo registrado no momento da coleta.
- **num_retry**: Campo da tabela `file_origin` que armazena o número de tentativas já realizadas.
- **max_retry**: Campo da tabela `file_origin` que armazena o número máximo de tentativas permitidas.

## Requisitos

### Requisito 1: Validação de Integridade Antes da Remoção

**User Story:** Como operador do sistema, quero que o arquivo só seja removido da origem após a confirmação de que o arquivo no destino possui o mesmo tamanho registrado, para garantir que nenhum arquivo seja perdido por uma transferência incompleta.

#### Critérios de Aceitação

1. WHEN a transferência do arquivo para o destino for concluída, THE Consumer SHALL comparar o tamanho em bytes do arquivo no destino com o valor do campo `num_file_size` da tabela `file_origin`.
2. WHEN o tamanho do arquivo no destino for igual ao valor de `num_file_size`, THE RemoveOriginService SHALL prosseguir com a remoção do arquivo na pasta de origem.
3. IF o tamanho do arquivo no destino divergir do valor de `num_file_size`, THEN THE Consumer SHALL finalizar o processamento com `step = 'COLETA'` e `status = 'ERRO'`, sem executar a remoção.
4. IF o tamanho do arquivo no destino divergir do valor de `num_file_size`, THEN THE StatusUpdateService SHALL registrar a mensagem de erro `"Erro de integridade: tamanho do arquivo no destino difere do esperado"`.

---

### Requisito 2: Remoção do Arquivo da Origem

**User Story:** Como operador do sistema, quero que o arquivo seja removido da pasta de origem após a validação de integridade bem-sucedida, para manter a pasta de origem limpa e evitar reprocessamentos desnecessários.

#### Critérios de Aceitação

1. WHEN a validação de integridade for bem-sucedida, THE RemoveOriginService SHALL remover o arquivo da pasta de origem via SFTP.
2. WHEN a remoção do arquivo for concluída com sucesso, THE StatusUpdateService SHALL atualizar o status do registro `file_origin` para `CONCLUIDO`.
3. IF a remoção do arquivo falhar por qualquer motivo, THEN THE StatusUpdateService SHALL atualizar o registro `file_origin` com `step = 'COLETA'` e `status = 'ERRO'`.
4. IF a remoção do arquivo falhar por qualquer motivo, THEN THE StatusUpdateService SHALL registrar a mensagem de erro `"Erro ao remover arquivo da origem. msg: <err_msg>"`, onde `<err_msg>` é a mensagem da exceção capturada.

---

### Requisito 3: Identificação de Remoção Pendente no Reprocessamento

**User Story:** Como operador do sistema, quero que o Consumer identifique quando um arquivo já foi transferido mas ainda não foi removido da origem, para que a retentativa execute apenas a etapa de remoção sem repetir a transferência.

#### Critérios de Aceitação

1. WHEN o Consumer receber uma mensagem para um arquivo, THE Consumer SHALL verificar no banco de dados se o registro `file_origin` correspondente possui `status = 'ERRO'` com `des_message_error` contendo o texto `REMOVE_ORIGIN_FILE_ERROR`.
2. WHEN o registro `file_origin` possuir `status = 'ERRO'` com mensagem contendo `REMOVE_ORIGIN_FILE_ERROR`, THE Consumer SHALL ignorar todas as etapas de download, identificação de layout, identificação de cliente e upload.
3. WHEN o registro `file_origin` possuir `status = 'ERRO'` com mensagem contendo `REMOVE_ORIGIN_FILE_ERROR`, THE Consumer SHALL executar diretamente a etapa de remoção do arquivo na origem.
4. WHEN o registro `file_origin` não possuir `status = 'ERRO'` com mensagem contendo `REMOVE_ORIGIN_FILE_ERROR`, THE Consumer SHALL executar o fluxo completo de transferência normalmente.

---

### Requisito 4: Controle de Tentativas para Remoção Pendente

**User Story:** Como operador do sistema, quero que o sistema respeite o limite de tentativas configurado para a remoção pendente, para evitar que um arquivo com falha permanente de remoção fique em loop indefinido.

#### Critérios de Aceitação

1. THE Producer SHALL incrementar o campo `num_retry` da tabela `file_origin` a cada reenvio de mensagem para um arquivo com remoção pendente.
2. WHEN o Consumer processar uma retentativa de remoção e `num_retry` for menor que `max_retry`, THE Consumer SHALL executar a tentativa de remoção e, em caso de falha, atualizar o status para `ERRO` com a mensagem de erro correspondente.
3. WHEN `num_retry` for maior ou igual a `max_retry`, THE StatusUpdateService SHALL finalizar o registro com `step = 'COLETA'` e `status = 'ERRO'`.
4. WHEN `num_retry` for maior ou igual a `max_retry`, THE StatusUpdateService SHALL registrar a mensagem `"REMOVE_ORIGIN_FILE_ERROR. arquivo <filename>, motivo: <err_msg>"`, onde `<filename>` é o nome do arquivo e `<err_msg>` é a mensagem da última exceção capturada.

---

### Requisito 5: Criação do RemoveOriginService

**User Story:** Como desenvolvedor, quero um serviço dedicado para a remoção de arquivos na origem, para que a responsabilidade de remoção seja isolada e testável independentemente do fluxo de transferência.

#### Critérios de Aceitação

1. THE RemoveOriginService SHALL ser criado no módulo consumer, no pacote `com.concil.edi.consumer.service`.
2. THE RemoveOriginService SHALL receber como parâmetros o identificador do caminho de origem (`serverPathOriginId`) e o nome do arquivo (`filename`) para executar a remoção.
3. THE RemoveOriginService SHALL utilizar a conexão SFTP existente para executar a operação de remoção do arquivo remoto.
4. IF a operação de remoção via SFTP falhar, THEN THE RemoveOriginService SHALL propagar a exceção para que o chamador possa tratar o erro adequadamente.
