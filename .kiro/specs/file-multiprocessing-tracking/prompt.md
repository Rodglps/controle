## Introdução

Estamos adicionando uma nova funcionalidade chamada file-muiltiprocessing-tracking.
Essa funcionalidade tem como objetivo permitir o rastreio de processamento de multiplos arquivos quando ele for identificado para mais de um cliente.
Esse rastreio vai permitir que futuramente, as próximas etapas após a COLETA possam iteragir atualizando o step e status individuamente, deixando a visibilidade total do processamento centralizado.
As demais etapas após a coleta (ORDENACAO, PROCESSAMENTO), são feitas esplitando o arquivo por cliente, para que caso seja necessário fazer um reprocessamento, ele possa ser feito apenas para um determinado clinte.
Nesse momento não vamos nos preocupar com as próximas etapas, vamos apenas deixar preparado para futura evolução.


## Momento do split multiprocessing

Após a identificação do cliente vamos adicionar o passo para inserir o split.
para isso teremos uma nova entidade de banco de dados chamada 'file_origin_processing'.
Essa nova tabela vai representar o os fluxos de arquivos por cliente em cada etapa do processo.
Vamos seguir as seguintes regras:
1 - Para cada cliente identificado (registros da tabela file_origin_clients) teremos 1 registro na tabela file_origin_processing durante o step de coleta.
2 - Quando o cliente não for identificado, teremos 1 linha na tabela 'file_origin_processing' com idt_cliente = NULL. Isso indica que houve o processo de coleta do arquivo mesmo sem a identificação de um cliente.

## Requisitos
Na etapa de coleta, o step e status devem refletir o mesmo step e status da tabela file_origin, até o final da coleta (processo do consumer).
O campo `jsn_additional_info` não será preenchido nesse momento.
O campo `dat_step_start` deve refletir o inicio da etapa de coleta daquele arquivo desde o momento do consumo da mensagem na fila.
O campo `dat_step_end` deve refletir o final da etapa de coleta daquele arquivo. momento em que o processo finaliza no consumidor e o status é atualizado para `CONCLUIDO`.
O campo `des_message_error` deve refletir o campo `des_message_error` da tabela `file_origin` quando o status final for `ERRO`.
O campo `des_message_alert` não será preenchido nesse momento.




## Observações
steps: As novas etapas que serão adicionadas, vão refletir na criação de novas linhas na tabela 'file_origin_processing' de acordo com cada etapa que for realizada se elas forem referentes ao mesmo arquivo.
       Essas novas linhas podem refletir ao reprocessamento de um arquivo para um único cliente ou para todos os clientes referente ao mesmo 'file_origin'.
       Não se preocupe com essas novas etapas nesse momento, porque serão adicionadas futuramente.

## Estrutura de tabelas e Enums para o multiprocessing

  Enum step_enum {
    "COLETA"      [note: 'Arquivo esta sendo coletado, Job coletor responsavel por esse status.']
    "DELETE"      [note: 'Arquivo está sendo excluído.']
    "RAW"         [note: 'Arquivo está na pipeline de dados brutos.']
    "STAGING"     [note: 'Arquivo está na pipeline de dados em staging.']
    "ORDINATION"  [note: 'Arquivo está na etapa de ordenação.']
    "PROCESSING"  [note: 'Arquivo está na etapa de processamento.']
    "PROCESSED"   [note: 'Arquivo está na etapa de pós processamento']

  }
  Enum status_enum {
    "EM_ESPERA"     [note: 'Arquivo esta aguardando para ser processado no step']
    "PROCESSAMENTO" [note: 'Arquivo esta sendo processado no step']
    "CONCLUIDO"     [note: 'Arquivo foi processado com sucesso no step']
    "ERRO"          [note: 'Ocorreu um erro durante o processamento do arquivo no step']
  }

Table file_origin_processing [note: '[NOT_SECURITY_APPLY] - Tabela relacionada controle de estado do processamento com split por clientes para cada step do processamento dentro do contexto geral do conciliador.']{
    idt_file_origin_processing number(19) [primary key, increment, not null, note: '[NOT_SECURITY_APPLY] - Identificador interno., ex.: 1, 2, 3, ...']
    idt_file_origin number(19) [not null, note: '[NOT_SECURITY_APPLY] - Identificador do arquivo, referencia da tabela file_origin., ex.: 1, 2, 3, ...']
    des_step  step_enum [not null, note: '[NOT_SECURITY_APPLY] - código da etapa em que o registro se encontra (COLETA,PROCESSING,ORDINATION,DELETE,RAW,STAGING)']
    des_status status_enum [not null, note: '[NOT_SECURITY_APPLY] - código da etapa em que o registro se encontra (EM_ESPERA,PROCESSAMENTO,CONCLUIDO,ERRO)']
    idt_client number(20) [null, note: '[NOT_SECURITY_APPLY] - Identificador interno do cliente. Referencia da coluna client_id da tabela tb_client, ex.: 1, 2, 3, ...']
    des_message_error varchar2(4000) [null, note: '[NOT_SECURITY_APPLY] - Mensagem de erro relacionada ao passo, caso haja necessidade de registrar algum erro específico para aquele passo']  
    des_message_alert varchar2(4000) [null, note: '[NOT_SECURITY_APPLY] - Mensagem de alerta relacionada ao passo, caso haja necessidade de registrar algum alerta específico para aquele passo']
    dat_step_start date [null, note: '[NOT_SECURITY_APPLY] - Data e hora que o step iniciou.']
    dat_step_end date [null, note: '[NOT_SECURITY_APPLY] - Data e hora que o step finalizou.']
    jsn_additional_info varchar2(4000) [null, note: '[NOT_SECURITY_APPLY] - Informações adicionais sobre o step.Ex.: qtd. linhas processadas, qtd. linhas geradas por tabela, etc..']
    dat_creation date [not null, note: '[NOT_SECURITY_APPLY] - Data e hora da criação do registro']
    dat_update date [null, note: '[NOT_SECURITY_APPLY] - Data e hora da última atualização do registro']
    nam_change_agent varchar2(50) [not null, note: '[NOT_SECURITY_APPLY] - Nome do usuário que aplicou a última alteração']
} ref: file_origin_processing.idt_file_origin > file_origin.idt_file_origin


## Regras de unicidade e atualização

- Durante o fluxo normal de uma mesma coleta (incluindo retries do Consumer para o mesmo ciclo), deve haver um único registro na `file_origin_processing` para cada combinação de `idt_file_origin` + `idt_client` + `des_step`. Nesse caso, o registro existente deve ser atualizado (não criar um novo).
- Em um eventual reprocessamento total (nova coleta disparada após steps já processados anteriormente), novos registros devem ser inseridos na `file_origin_processing` para o step de COLETA, gerando um novo ciclo de processamento. Os steps posteriores também gerarão novos registros quando forem executados futuramente.
- Resumindo: retry dentro do mesmo ciclo = atualiza registro existente. Nova coleta (reprocessamento completo) = insere novos registros.

## Captura do timestamp de início

O `dat_step_start` deve ser capturado no início do ciclo de processamento no Consumer (início do `handleFileTransfer`), antes de qualquer operação. Esse timestamp será passado adiante para ser utilizado na criação/atualização dos registros da `file_origin_processing`.

## Agente de alteração

O campo `nam_change_agent` deve utilizar o mesmo valor do `StatusUpdateService`: `"consumer-service"`.

## Cenários de exemplo

### Cenário 1: Arquivo sem identificação do cliente (sucesso)

**file_origin:**
| idt_file_origin | des_step | des_status | des_message_error |
|---|---|---|---|
| 1 | COLETA | CONCLUIDO | NULL |

**file_origin_processing:**
| idt_file_origin | des_step | des_status | idt_client | des_message_error | dat_step_start | dat_step_end |
|---|---|---|---|---|---|---|
| 1 | COLETA | CONCLUIDO | NULL | NULL | T1 (início consumo) | T2 (fim coleta) |

### Cenário 2: Arquivo com identificação de 1 cliente (sucesso)

**file_origin:**
| idt_file_origin | des_step | des_status | des_message_error |
|---|---|---|---|
| 2 | COLETA | CONCLUIDO | NULL |

**file_origin_clients:**
| idt_file_origin | idt_client |
|---|---|
| 2 | 15 |

**file_origin_processing:**
| idt_file_origin | des_step | des_status | idt_client | des_message_error | dat_step_start | dat_step_end |
|---|---|---|---|---|---|---|
| 2 | COLETA | CONCLUIDO | 15 | NULL | T1 (início consumo) | T2 (fim coleta) |

### Cenário 3: Arquivo com identificação de 2 clientes (sucesso)

**file_origin:**
| idt_file_origin | des_step | des_status | des_message_error |
|---|---|---|---|
| 3 | COLETA | CONCLUIDO | NULL |

**file_origin_clients:**
| idt_file_origin | idt_client |
|---|---|
| 3 | 15 |
| 3 | 20 |

**file_origin_processing:**
| idt_file_origin | des_step | des_status | idt_client | des_message_error | dat_step_start | dat_step_end |
|---|---|---|---|---|---|---|
| 3 | COLETA | CONCLUIDO | 15 | NULL | T1 (início consumo) | T2 (fim coleta) |
| 3 | COLETA | CONCLUIDO | 20 | NULL | T1 (início consumo) | T2 (fim coleta) |

### Cenário 4: Arquivo com 2 clientes identificados e erro na coleta

**file_origin:**
| idt_file_origin | des_step | des_status | des_message_error |
|---|---|---|---|
| 4 | COLETA | ERRO | Erro de integridade: tamanho do arquivo no destino difere do esperado |

**file_origin_clients:**
| idt_file_origin | idt_client |
|---|---|
| 4 | 15 |
| 4 | 20 |

**file_origin_processing:**
| idt_file_origin | des_step | des_status | idt_client | des_message_error | dat_step_start | dat_step_end |
|---|---|---|---|---|---|---|
| 4 | COLETA | ERRO | 15 | Erro de integridade: tamanho do arquivo no destino difere do esperado | T1 (início consumo) | T2 (fim coleta) |
| 4 | COLETA | ERRO | 20 | Erro de integridade: tamanho do arquivo no destino difere do esperado | T1 (início consumo) | T2 (fim coleta) |

### Cenário 5: Arquivo sem cliente identificado e erro na coleta

**file_origin:**
| idt_file_origin | des_step | des_status | des_message_error |
|---|---|---|---|
| 5 | COLETA | ERRO | Erro de integridade: tamanho do arquivo no destino difere do esperado |

**file_origin_processing:**
| idt_file_origin | des_step | des_status | idt_client | des_message_error | dat_step_start | dat_step_end |
|---|---|---|---|---|---|---|
| 5 | COLETA | ERRO | NULL | Erro de integridade: tamanho do arquivo no destino difere do esperado | T1 (início consumo) | T2 (fim coleta) |

## Cenários de Teste

### Teste 1: Coleta com sucesso sem cliente identificado
**Objetivo:** Validar que 1 registro é criado na `file_origin_processing` com `idt_client=NULL` quando nenhum cliente é identificado.
**Resultado esperado:**
- 1 registro na `file_origin_processing` com `idt_client=NULL`, `des_step=COLETA`, `des_status=CONCLUIDO`
- `dat_step_start` preenchido (momento do consumo da mensagem)
- `dat_step_end` preenchido (momento da finalização)
- `dat_step_start` < `dat_step_end`
- `des_message_error=NULL`
- `nam_change_agent='consumer-service'`

### Teste 2: Coleta com sucesso com 1 cliente identificado
**Objetivo:** Validar que 1 registro é criado na `file_origin_processing` com o `idt_client` do cliente identificado.
**Resultado esperado:**
- 1 registro na `file_origin_processing` com `idt_client=15`, `des_step=COLETA`, `des_status=CONCLUIDO`
- `dat_step_start` e `dat_step_end` preenchidos
- `dat_step_start` < `dat_step_end`
- `des_message_error=NULL`

### Teste 3: Coleta com sucesso com múltiplos clientes identificados
**Objetivo:** Validar que N registros são criados na `file_origin_processing`, um para cada cliente identificado.
**Resultado esperado:**
- 2 registros na `file_origin_processing` (clientes 15 e 20), ambos com `des_step=COLETA`, `des_status=CONCLUIDO`
- Ambos com mesmo `dat_step_start` (mesmo momento de consumo)
- Ambos com `dat_step_end` preenchido
- `des_message_error=NULL` em ambos

### Teste 4: Coleta com erro e clientes identificados
**Objetivo:** Validar que os registros da `file_origin_processing` são atualizados para `ERRO` com a mensagem de erro quando a coleta falha.
**Resultado esperado:**
- Registros na `file_origin_processing` com `des_status=ERRO`
- `des_message_error` preenchido com a mesma mensagem da `file_origin`
- `dat_step_end` preenchido (momento do erro)

### Teste 5: Coleta com erro sem cliente identificado
**Objetivo:** Validar que o registro com `idt_client=NULL` é atualizado para `ERRO` quando a coleta falha sem identificação de cliente.
**Resultado esperado:**
- 1 registro na `file_origin_processing` com `idt_client=NULL`, `des_status=ERRO`
- `des_message_error` preenchido
- `dat_step_end` preenchido

### Teste 6: Verificar unicidade de registros por arquivo/cliente/step
**Objetivo:** Validar que não são criados registros duplicados para a mesma combinação de `idt_file_origin` + `idt_client` + `des_step`.
**Resultado esperado:**
- Ao reprocessar o mesmo arquivo (retry), os registros existentes são atualizados, não duplicados
- A contagem de registros permanece a mesma após retry

### Teste 7: Verificar dat_step_start e dat_step_end
**Objetivo:** Validar que os timestamps são preenchidos corretamente.
**Resultado esperado:**
- `dat_step_start` é capturado no início do processamento (antes de qualquer operação)
- `dat_step_end` é preenchido tanto em cenários de sucesso quanto de erro
- `dat_step_start` <= `dat_step_end`

## O que vamos fazer ?

1 - Criar os scripts com a definição das novas tabelas e adicionar a pasta de ddl.
2 - Ajustar a inicialização do banco de dados para incluir os novos ddls e dmls.
3 - Adaptar o Consumer adicionando um serviço de Split de processamento.