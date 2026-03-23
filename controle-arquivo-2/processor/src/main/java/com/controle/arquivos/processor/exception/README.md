# Hierarquia de Exceções - Processador de Arquivos

## Visão Geral

Este pacote contém a hierarquia de exceções customizadas para o sistema de controle de arquivos. As exceções são classificadas em duas categorias principais: **recuperáveis** e **não recuperáveis**.

## Hierarquia de Classes

```
RuntimeException
├── ErroRecuperavelException (base para erros recuperáveis)
│   └── FalhaUploadException
└── ErroNaoRecuperavelException (base para erros não recuperáveis)
    ├── ClienteNaoIdentificadoException
    └── LayoutNaoIdentificadoException
```

## Exceções Base

### ErroRecuperavelException

**Tipo:** Recuperável (permite retry)

**Descrição:** Exceção base para erros causados por falhas temporárias ou transientes.

**Comportamento do Sistema:**
- NACK da mensagem RabbitMQ para reprocessamento
- Registrar erro com contexto completo
- Permitir até 5 tentativas de reprocessamento
- Manter arquivo na origem

**Exemplos de Erros Recuperáveis:**
- Falhas de conexão SFTP temporárias
- Timeouts de rede
- Falhas de conexão com banco de dados (transientes)
- Falhas de publicação no RabbitMQ
- Erros de throttling do S3

### ErroNaoRecuperavelException

**Tipo:** Não Recuperável (permanente)

**Descrição:** Exceção base para erros que não podem ser resolvidos através de retry.

**Comportamento do Sistema:**
- ACK da mensagem RabbitMQ (não reprocessar)
- Marcar arquivo como ERRO permanente
- Registrar erro com contexto completo e stack trace
- Atualizar status em file_origin_client_processing

**Exemplos de Erros Não Recuperáveis:**
- Arquivo não encontrado no SFTP
- Cliente não identificado (nenhuma regra match)
- Layout não identificado (nenhuma regra match)
- Credenciais inválidas no Vault
- Violação de constraint de banco de dados

## Exceções Específicas

### FalhaUploadException

**Herda de:** ErroRecuperavelException

**Descrição:** Lançada quando ocorre falha durante o upload de arquivo para o destino (S3 ou SFTP).

**Cenários:**
- Falha ao conectar ao servidor de destino
- Timeout durante upload
- Erro de validação de tamanho após upload
- Falha ao completar multipart upload no S3
- Erro ao escrever no SFTP de destino

**Comportamento:**
- Permite retry (até 5 tentativas)
- Mantém arquivo na origem
- Registra erro detalhado com informações do destino

### ClienteNaoIdentificadoException

**Herda de:** ErroNaoRecuperavelException

**Descrição:** Lançada quando nenhum cliente é identificado para um arquivo.

**Cenários:**
- Nenhuma regra de identificação retornou match
- Arquivo não pertence a nenhum cliente configurado
- Regras de identificação incompletas ou incorretas
- Nome do arquivo não segue padrão esperado

**Comportamento:**
- Não permite retry (erro permanente)
- Marca arquivo como ERRO
- Registra detalhes do arquivo e regras aplicadas

### LayoutNaoIdentificadoException

**Herda de:** ErroNaoRecuperavelException

**Descrição:** Lançada quando nenhum layout é identificado para um arquivo.

**Cenários:**
- Nenhuma regra de identificação de layout retornou match
- Arquivo não corresponde a nenhum layout configurado
- Regras de identificação incompletas ou incorretas
- Conteúdo do arquivo não segue padrão esperado

**Comportamento:**
- Não permite retry (erro permanente)
- Marca arquivo como ERRO
- Registra detalhes do arquivo, cliente e regras aplicadas

## Uso no Código

### Lançando Exceções

```java
// Erro recuperável - permite retry
throw new FalhaUploadException("Falha ao fazer upload para S3: timeout", cause);

// Erro não recuperável - não permite retry
throw new ClienteNaoIdentificadoException("Nenhuma regra de identificação retornou match para arquivo: " + nomeArquivo);
```

### Tratando Exceções

```java
try {
    processarArquivo(mensagem);
} catch (ErroRecuperavelException e) {
    // Registrar erro e fazer NACK para retry
    log.error("Erro recuperável ao processar arquivo: {}", e.getMessage(), e);
    rastreabilidadeService.registrarErro(idProcessing, e.getMessage());
    throw e; // Propaga para RabbitMQ fazer NACK
} catch (ErroNaoRecuperavelException e) {
    // Registrar erro permanente e fazer ACK
    log.error("Erro não recuperável ao processar arquivo: {}", e.getMessage(), e);
    rastreabilidadeService.marcarComoErroPermanente(idProcessing, e.getMessage());
    // Não propaga - RabbitMQ fará ACK
}
```

## Limite de Reprocessamento

O sistema implementa um limite de **5 tentativas** para erros recuperáveis. Após 5 falhas consecutivas:
- O arquivo é marcado como ERRO permanente
- A mensagem é confirmada (ACK) para evitar loop infinito
- Um alerta é gerado para a equipe de operações

## Rastreabilidade

Todos os erros são registrados na tabela `file_origin_client_processing` com:
- `des_status`: ERRO
- `des_message_error`: Mensagem de erro
- `jsn_additional_info`: Stack trace e contexto adicional
- `dat_step_end`: Timestamp do erro

## Referências

- **Requisito 15.3:** Classificação de erros recuperáveis
- **Requisito 15.4:** Classificação de erros não recuperáveis
- **Requisito 15.6:** Limite de reprocessamento
- **Design - Tratamento de Erros:** Seção completa sobre estratégia de erros
