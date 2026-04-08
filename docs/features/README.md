# Funcionalidades do Sistema

Este diretório contém a documentação detalhada das funcionalidades específicas do sistema de controle de arquivos EDI.

## Funcionalidades Disponíveis

### 1. Identificação de Layouts

**Arquivo:** [layout-identification.md](layout-identification.md)

Identifica automaticamente o layout (formato) de cada arquivo EDI processado durante a transferência.

**Características principais:**
- First-match algorithm (retorna o primeiro layout que satisfaz todas as regras)
- Buffer-based (lê apenas 7000 bytes iniciais)
- Operador AND entre regras
- Encoding detection automático
- Layout 0 para arquivos não identificados (não é erro)

**Tipos de identificação:**
- FILENAME: Nome do arquivo
- HEADER: Conteúdo posicional (TXT/CSV)
- TAG: Caminhos XML (XPath)
- KEY: Caminhos JSON (dot notation)

### 2. Identificação de Clientes

**Arquivo:** [customer-identification.md](customer-identification.md)

Identifica automaticamente qual(is) cliente(s) é(são) proprietário(s) de cada arquivo EDI processado.

**Características principais:**
- All-match algorithm (identifica todos os clientes cujas regras são satisfeitas)
- Reutiliza buffer da identificação de layout
- Operador AND entre regras do mesmo cliente
- Suporta múltiplos clientes por arquivo
- Continuidade mesmo sem identificação

**Tipos de identificação:**
- FILENAME: Nome do arquivo
- HEADER: Conteúdo posicional (TXT/CSV)
- TAG: Caminhos XML (XPath)
- KEY: Caminhos JSON (dot notation)

## Comparação entre Funcionalidades

| Aspecto | Layout Identification | Customer Identification |
|---------|----------------------|------------------------|
| **Algoritmo** | First-match (retorna primeiro) | All-match (retorna todos) |
| **Momento** | Durante transferência, antes do upload | Logo após identificação de layout |
| **Buffer** | Lê 7000 bytes iniciais | Reutiliza buffer do layout |
| **Operador** | AND entre regras do mesmo layout | AND entre regras do mesmo cliente |
| **Resultado** | Um único layout (ou 0) | Lista de clientes (pode ser vazia) |
| **Falha** | Layout 0, status CONCLUIDO | Lista vazia, status CONCLUIDO |
| **Persistência** | file_origin.idt_layout | file_origin_clients (múltiplos registros) |
| **Dependência** | Independente | Depende do layout para regras de conteúdo |

## Componentes Compartilhados

Ambas as funcionalidades compartilham componentes no módulo commons:

### Interfaces e Abstrações
- **IdentificationRule**: Interface comum para regras de identificação
- **ValueExtractor**: Interface para estratégias de extração de valores

### Implementações de Extração
- **FilenameExtractor**: Extrai nome do arquivo
- **HeaderTxtExtractor**: Extrai valores de TXT por byte offset
- **HeaderCsvExtractor**: Extrai valores de CSV por índice de coluna
- **XmlTagExtractor**: Extrai valores de XML por XPath
- **JsonKeyExtractor**: Extrai valores de JSON por dot notation

### Serviços Compartilhados
- **CriteriaComparator**: Lógica de comparação de critérios
- **TransformationApplier**: Aplicação de funções de transformação
- **RuleValidator**: Validação de configuração de regras
- **EncodingConverter**: Detecção e conversão de encoding

## Fluxo Integrado

```
1. FileTransferListener recebe mensagem RabbitMQ
2. Atualiza status para PROCESSAMENTO
3. Abre InputStream do arquivo SFTP
4. ✨ LayoutIdentificationService identifica layout
   - Lê buffer de 7000 bytes
   - Detecta encoding
   - Aplica regras de layout (AND, first-match)
   - Retorna idt_layout (ou 0)
5. Atualiza file_origin.idt_layout
6. ✨ CustomerIdentificationService identifica clientes
   - Reutiliza buffer de 7000 bytes
   - Filtra por acquirer (e layout se identificado)
   - Aplica regras de cliente (AND, all-match)
   - Retorna lista de idt_client
7. Registra clientes em file_origin_clients
8. Reabre InputStream para upload
9. FileUploadService faz upload para destino
10. Atualiza status para CONCLUIDO
```

## Critérios de Comparação

Ambas as funcionalidades suportam os mesmos critérios:

- **COMECA_COM**: Valor extraído começa com o valor esperado
- **TERMINA_COM**: Valor extraído termina com o valor esperado
- **CONTEM**: Valor extraído contém o valor esperado
- **CONTIDO**: Valor esperado contém o valor extraído
- **IGUAL**: Valor extraído é exatamente igual ao valor esperado

## Funções de Transformação

Ambas as funcionalidades suportam as mesmas funções:

- **UPPERCASE**: Converte para maiúsculas
- **LOWERCASE**: Converte para minúsculas
- **INITCAP**: Primeira letra maiúscula, demais minúsculas
- **TRIM**: Remove espaços em branco no início e fim
- **NONE**: Nenhuma transformação (case-sensitive)

## Configuração

### Variável de Ambiente Compartilhada

- **FILE_ORIGIN_BUFFER_LIMIT**: Tamanho do buffer em bytes
  - Padrão: `7000`
  - Usado por ambas as identificações
  - Configurável via environment variable

### Tabelas de Configuração

**Layout Identification:**
- `layout`: Definições de layouts
- `layout_identification_rule`: Regras de identificação

**Customer Identification:**
- `customer_identification`: Configurações de identificação
- `customer_identification_rule`: Regras de identificação
- `file_origin_clients`: Rastreamento de clientes identificados

## Testes

### Testes E2E

Ambas as funcionalidades possuem testes E2E completos:

**Layout Identification:**
- Identificação por FILENAME
- Identificação por HEADER (TXT e CSV)
- Layout não identificado (layout 0)

**Customer Identification:**
- Múltiplos clientes por FILENAME
- Nenhum cliente identificado
- Identificação por HEADER (TXT e CSV)

### Executar Testes

```bash
# Todos os testes E2E
make e2e

# Testes específicos de layout
mvn test -Dtest=LayoutIdentificationE2ETest

# Testes específicos de clientes
mvn test -Dtest=CustomerIdentificationE2ETest
```

## Casos de Uso

### Caso 1: Arquivo Cielo com Layout e Cliente Identificados

**Arquivo:** `cielo_015_03_VENDA_1234567890_20250101.txt`

**Resultado:**
- Layout: CIELO_015_03_VENDA (idt_layout=1)
- Cliente: Cliente 15 (identificado por CNPJ no filename)
- Status: CONCLUIDO

### Caso 2: Arquivo com Layout Identificado mas Sem Cliente

**Arquivo:** `cielo_015_03_VENDA_unknown_20250101.txt`

**Resultado:**
- Layout: CIELO_015_03_VENDA (idt_layout=1)
- Cliente: Nenhum (lista vazia)
- Status: CONCLUIDO
- Observação: Arquivo transferido normalmente

### Caso 3: Arquivo Sem Layout mas Com Cliente

**Arquivo:** `cielo_1234567890_custom_format.txt`

**Resultado:**
- Layout: SEM_IDENTIFICACAO (idt_layout=0)
- Cliente: Cliente 15 (identificado por CNPJ no filename)
- Status: CONCLUIDO
- Observação: Apenas regras FILENAME são avaliadas para cliente

### Caso 4: Arquivo com Múltiplos Clientes

**Arquivo:** `cielo_1234567890_premium_20250101.txt`

**Resultado:**
- Layout: Identificado ou não
- Clientes: Cliente 15 e Cliente 20 (ambos identificados)
- Status: CONCLUIDO
- Observação: Dois registros em file_origin_clients

## Troubleshooting

### Layout não está sendo identificado

1. Verificar se layout está ativo: `flg_active = 1`
2. Verificar se regras estão ativas: `flg_active = 1`
3. Verificar acquirer correto
4. Verificar operador AND (todas as regras devem ser satisfeitas)
5. Consultar logs do Consumer

### Cliente não está sendo identificado

1. Verificar se customer_identification está ativo: `flg_active = 1`
2. Verificar se regras estão ativas: `flg_active = 1`
3. Verificar acquirer correto
4. Se layout não identificado, verificar se regras são FILENAME
5. Verificar operador AND (todas as regras devem ser satisfeitas)
6. Consultar logs do Consumer

### Buffer muito pequeno

Se headers são muito grandes:
1. Aumentar `FILE_ORIGIN_BUFFER_LIMIT`
2. Reiniciar Consumer
3. Testar novamente

## Referências

### Documentação Detalhada
- [Layout Identification](layout-identification.md)
- [Customer Identification](customer-identification.md)

### Especificações
- [Layout Spec](../../.kiro/specs/controle-arquivos-edi/)
- [Customer Spec](../../.kiro/specs/identificacao_clientes/)

### Código Fonte
- Commons: `commons/src/main/java/com/concil/edi/commons/`
- Consumer: `consumer/src/main/java/com/concil/edi/consumer/`

### Scripts DDL
- [Database Scripts](../../.kiro/steering/database-scripts.md)
- DDL Files: `scripts/ddl/`
