## Introdução

Estamos adicionando uma nova funcionalidade chamada Remove File Origin.
Essa funcionalidade tem como objetivo limpar a pasta de origem após a confirmação de que o arquivo foi transferido com sucesso para o destino.

## Momento da Remoção do Arquivo da Pasta de Origem

A remoção do arquivo deve ser feita após a transferência completa do arquivo para a pasta de destino.

**Validação de integridade antes da remoção:**
- Comparar o tamanho em bytes do arquivo no destino com o valor registrado em `num_file_size` na tabela `file_origin`.
- Se os tamanhos forem iguais, o arquivo está íntegro e a remoção pode prosseguir.
- Se os tamanhos divergirem, a remoção não deve ocorrer e o processo deve finalizar com erro.

**Tratamento de falha na remoção:**
- Se o arquivo não foi removido da origem por algum motivo, devemos:
  - Finalizar o processamento com `step = 'COLETA'` e `status = 'ERRO'`
  - Registrar a mensagem: `"Erro ao remover arquivo da origem. msg: <err_msg>"`

## Retentativa

Quando houver falha no processo de remoção, mas o arquivo já foi copiado para o destino e validado, o producer fará nova tentativa para esse arquivo e enviará nova mensagem.

**Identificação do estado de remoção pendente:**
- O consumer verifica no banco se o arquivo já possui `status = ERRO` com mensagem contendo `REMOVE_ORIGIN_FILE_ERROR`.
- Se sim, o consumer pula toda a etapa de transferência e executa apenas a remoção do arquivo na origem.

**Controle de tentativas:**
- Reutilizar os campos existentes `num_retry` e `max_retry` da tabela `file_origin`.
- O producer incrementa `num_retry` a cada reenvio da mensagem.
- Ao atingir `num_retry >= max_retry`, o arquivo finaliza definitivamente com:
  - `step = 'COLETA'`, `status = 'ERRO'`
  - Mensagem: `"REMOVE_ORIGIN_FILE_ERROR. arquivo <filename>, motivo: <err_msg>"`

## Escopo da Implementação

1. Criar o serviço de remoção do arquivo da origem no módulo consumer.
2. Integrar o serviço ao final do processo de transferência, após a validação de integridade.
3. Implementar a lógica de identificação de remoção pendente no consumer para reprocessamento seletivo.
4. Atualizar a documentação para refletir o processo adicional.
