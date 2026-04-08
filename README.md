# Controle de Arquivos EDI

Sistema de transferência automatizada de arquivos EDI para conciliação de cartão de crédito.

## 🚀 Início Rápido

```bash
# Ver todos os comandos disponíveis
make help

# Iniciar o sistema completo
make up

# Parar o sistema
make down
```

📘 **[Ver Cheatsheet Completo](docs/makefile/cheatsheet.md)** | 📗 **[Guia Detalhado](docs/makefile/guide.md)** | 📙 **[Exemplos Práticos](docs/makefile/examples.md)** | 📚 **[Toda Documentação](DOCUMENTATION.md)**

## Arquitetura

- **Producer**: Coleta arquivos de servidores SFTP externos, registra no banco Oracle e publica mensagens no RabbitMQ
- **Consumer**: Consome mensagens do RabbitMQ, identifica o layout do arquivo, e transfere via streaming para destinos configurados (S3 ou SFTP interno)
- **Commons**: Módulo compartilhado com entidades JPA, DTOs e configurações

### Identificação de Layouts

O Consumer implementa identificação automática de layouts de arquivos EDI durante o processo de transferência:

- **Estratégias de Identificação**: FILENAME (nome do arquivo), HEADER (cabeçalho TXT/CSV), TAG (tags XML), KEY (chaves JSON)
- **Critérios de Comparação**: COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL
- **Funções de Transformação**: UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE
- **Algoritmo**: First-match wins com ordenação DESC por idt_layout

#### Fluxo de Identificação

1. Consumer recebe mensagem do RabbitMQ
2. Abre InputStream do arquivo SFTP
3. Lê buffer inicial (7000 bytes por padrão, configurável via `FILE_ORIGIN_BUFFER_LIMIT`)
4. Detecta e converte encoding se necessário
5. Busca layouts ativos por acquirer (ordenação DESC)
6. Para cada layout, aplica todas as regras de identificação (operador AND)
7. Retorna o primeiro layout que satisfaz todas as regras

#### Status de Processamento

- **CONCLUIDO**: Arquivo transferido com sucesso (layout identificado ou definido como layout 0)
- **ERRO**: Falha na transferência (será retentado pelo Producer até max_retry)
- **EM_ESPERA**: Aguardando processamento
- **PROCESSAMENTO**: Em processamento no momento

**Importante sobre arquivos não identificados**: 
- Quando o layout não é identificado, o arquivo recebe `idt_layout = 0` (layout especial "SEM_IDENTIFICACAO")
- O arquivo é transferido normalmente para o destino com status `CONCLUIDO`
- Apenas arquivos com status `ERRO` são retentados pelo Producer
- Arquivos com `idt_layout = 0` indicam que precisam de revisão manual para configuração de novas regras

### Identificação de Clientes

O Consumer também implementa identificação automática de clientes proprietários dos arquivos EDI, executada logo após a identificação de layout:

- **Estratégias de Identificação**: FILENAME (nome do arquivo), HEADER (cabeçalho TXT/CSV), TAG (tags XML), KEY (chaves JSON)
- **Critérios de Comparação**: COMECA_COM, TERMINA_COM, CONTEM, CONTIDO, IGUAL
- **Funções de Transformação**: UPPERCASE, LOWERCASE, INITCAP, TRIM, NONE
- **Algoritmo**: All-match - identifica todos os clientes cujas regras são satisfeitas (operador AND entre regras do mesmo cliente)
- **Múltiplos Clientes**: Um arquivo pode pertencer a múltiplos clientes simultaneamente

#### Fluxo de Identificação de Clientes

1. Após identificação de layout, reutiliza o mesmo buffer de 7000 bytes
2. Se layout não identificado: avalia apenas regras FILENAME
3. Se layout identificado: avalia regras FILENAME + regras de conteúdo (HEADER, TAG, KEY)
4. Para cada customer_identification ativo, aplica todas as regras (operador AND)
5. Registra todos os clientes identificados na tabela `file_origin_clients`
6. Se nenhum cliente identificado, continua processamento normalmente (sem registros)

#### Características Importantes

- **Reutilização de Buffer**: Usa o mesmo buffer já carregado para identificação de layout, evitando leitura duplicada
- **Filtro por Adquirente**: Apenas regras da adquirente correspondente são avaliadas
- **Ordenação por Peso**: Resultados ordenados por `num_process_weight DESC` (clientes mais relevantes primeiro)
- **Continuidade**: Falha na identificação não interrompe a transferência do arquivo
- **Tratamento de Erros**: Erros em TAG/KEY são registrados em log e o processamento continua

## Stack Tecnológica

- Java 21
- Spring Boot 3.4
- Maven (mono repositório)
- Oracle Database
- RabbitMQ 3.12+ (Quorum Queues)
- AWS S3
- SFTP (Spring Integration)

## Estrutura do Projeto

```
controle-arquivos-edi/
├── pom.xml                 # Parent POM
├── commons/                # Módulo compartilhado
│   ├── pom.xml
│   └── src/
├── producer/               # Módulo Producer
│   ├── pom.xml
│   └── src/
└── consumer/               # Módulo Consumer
    ├── pom.xml
    └── src/
```

## Requisitos

- Java 21
- Maven 3.8+
- Docker e Docker Compose (para ambiente local)

## Início Rápido

O projeto utiliza um Makefile para facilitar o gerenciamento. Para ver todos os comandos disponíveis:

```bash
make help
```

### Comandos Principais

```bash
# Iniciar todo o sistema (build + docker-compose)
make up

# Parar o sistema
make down

# Rebuild completo (build + testes + restart)
make rebuild

# Executar testes E2E
make e2e

# Ver logs
make logs
```

## Build

```bash
# Build completo com testes
make rebuild

# Build sem testes
make build

# Build manual com Maven
mvn clean install

# Build de um módulo específico
cd producer
mvn clean package
```

## Executar Localmente

### Usando Makefile (Recomendado)

```bash
# Iniciar todos os serviços
make up

# Parar serviços
make down

# Reiniciar serviços
make restart

# Ver status dos containers
make status

# Ver logs em tempo real
make logs
make logs-producer
make logs-consumer
```

### Usando Docker Compose Diretamente

```bash
# Subir infraestrutura (Oracle, RabbitMQ, LocalStack S3, SFTP)
docker-compose up -d

# Parar serviços
docker-compose down
```

### Executar Aplicações Localmente (sem Docker)

```bash
# Executar Producer
cd producer
mvn spring-boot:run

# Executar Consumer
cd consumer
mvn spring-boot:run
```

## Testes

### Usando Makefile (Recomendado)

```bash
# Executar testes unitários
make test

# Executar testes E2E (requer docker-compose rodando)
make e2e
```

### Usando Maven Diretamente

```bash
# Executar todos os testes
mvn test

# Executar testes de um módulo específico
cd producer
mvn test

# Executar apenas testes unitários
mvn test -Dtest=*Test

# Executar apenas property-based tests
mvn test -Dtest=*PropertyTest

# Executar testes E2E
mvn test -Dtest=FileTransferE2ETest -pl commons
```

## Configuração

As configurações são externalizadas via variáveis de ambiente. Veja `application.yml` em cada módulo para detalhes.

### Variáveis de Ambiente Principais

**Producer:**
- `DB_URL`: URL de conexão Oracle
- `DB_USERNAME`: Usuário do banco
- `DB_PASSWORD`: Senha do banco
- `RABBITMQ_HOST`: Host do RabbitMQ
- `SFTP_CIELO_VAULT`: Credenciais SFTP em JSON

**Consumer:**
- `DB_URL`: URL de conexão Oracle
- `RABBITMQ_HOST`: Host do RabbitMQ
- `AWS_ENDPOINT`: Endpoint S3 (LocalStack para dev)
- `AWS_REGION`: Região AWS
- `FILE_ORIGIN_BUFFER_LIMIT`: Tamanho do buffer para identificação de layout (padrão: 7000 bytes)
- `QUEUE_DELAY`: Delay em segundos antes de processar mensagens (usado apenas em testes E2E, padrão: 0)

## Utilitários do Makefile

### Logs e Monitoramento

```bash
make logs              # Todos os logs
make logs-producer     # Logs do Producer
make logs-consumer     # Logs do Consumer
make logs-infra        # Logs da infraestrutura
make status            # Status dos containers
```

### Acesso aos Serviços

```bash
make shell-producer    # Shell no container do Producer
make shell-consumer    # Shell no container do Consumer
make shell-oracle      # SQL*Plus no Oracle
make rabbitmq-queues   # Lista filas do RabbitMQ
make s3-list           # Lista objetos no S3
```

### Limpeza

```bash
make clean             # Remove arquivos de build
make down              # Para containers (mantém dados)
make down-volumes      # Para containers e remove dados
```

## URLs dos Serviços (Ambiente Local)

- **Producer Health**: http://localhost:8080/actuator/health
- **Consumer Health**: http://localhost:8081/actuator/health
- **RabbitMQ Management**: http://localhost:15672 (admin/admin)
- **Oracle Database**: localhost:1521/XEPDB1 (edi_user/edi_pass)
- **LocalStack S3**: http://localhost:4566

### Servidores SFTP

- **Origin**: sftp://cielo@localhost:2222 (password: admin-1-2-3)
- **Destination**: sftp://internal@localhost:2223 (password: internal-pass)
- **Client**: sftp://client@localhost:2224 (password: client-pass)

## Documentação

### 📚 [Índice Completo de Documentação](docs/)

### Especificações do Projeto
- [Especificação de Requisitos](.kiro/specs/controle-arquivos-edi/requirements.md)
- [Design Técnico](.kiro/specs/controle-arquivos-edi/design.md)
- [Plano de Implementação](.kiro/specs/controle-arquivos-edi/tasks.md)

### Guias de Uso do Makefile
- [📘 Cheatsheet](docs/makefile/cheatsheet.md) - Referência rápida
- [📗 Guia Completo](docs/makefile/guide.md) - Documentação detalhada
- [📙 Exemplos Práticos](docs/makefile/examples.md) - Cenários reais de uso
- [📊 Workflows](docs/makefile/workflows.md) - Fluxos de trabalho
- [📕 Migração](docs/makefile/migration.md) - Guia de migração dos scripts .sh

### Setup e Configuração
- [🐳 Docker Setup](docs/setup/docker.md) - Configuração do ambiente Docker

### Testes
- [🧪 Testes E2E](docs/testing/e2e.md) - Implementação de testes E2E
- [▶️ Executar E2E](docs/testing/run-e2e.md) - Como executar testes E2E

### Arquitetura
- [🔄 Fluxo do Producer](docs/architecture/producer-flow.md) - Fluxo do Producer

### Relatórios
- [📊 Relatório de Verificação](docs/reports/verification.md) - Relatório de verificação

## Licença

Proprietary - Concil
