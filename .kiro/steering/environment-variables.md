# Variáveis de Ambiente

Este documento descreve todas as variáveis de ambiente utilizadas no sistema de controle de arquivos EDI.

## Variáveis do Producer

### Banco de Dados
- **DB_URL**: URL de conexão JDBC com o Oracle
  - Formato: `jdbc:oracle:thin:@<host>:<port>/<service>`
  - Exemplo: `jdbc:oracle:thin:@oracle:1521/XEPDB1`
  - Padrão (docker): `jdbc:oracle:thin:@oracle:1521/XEPDB1`

- **DB_USERNAME**: Usuário do banco de dados
  - Exemplo: `edi_user`
  - Padrão (docker): `edi_user`

- **DB_PASSWORD**: Senha do banco de dados
  - Exemplo: `edi_pass`
  - Padrão (docker): `edi_pass`

### RabbitMQ
- **RABBITMQ_HOST**: Host do servidor RabbitMQ
  - Exemplo: `rabbitmq`
  - Padrão (docker): `rabbitmq`

- **RABBITMQ_PORT**: Porta do RabbitMQ
  - Exemplo: `5672`
  - Padrão: `5672`

- **RABBITMQ_USERNAME**: Usuário do RabbitMQ
  - Exemplo: `admin`
  - Padrão (docker): `admin`

- **RABBITMQ_PASSWORD**: Senha do RabbitMQ
  - Exemplo: `admin`
  - Padrão (docker): `admin`

### SFTP
- **SFTP_HOST**: Host do servidor SFTP de origem
  - Exemplo: `sftp-origin`
  - Padrão (docker): `sftp-origin`

- **SFTP_PORT**: Porta do servidor SFTP
  - Exemplo: `22`
  - Padrão: `22`

- **SFTP_CIELO_VAULT**: Credenciais SFTP em formato JSON
  - Formato: `{"user":"<username>","password":"<password>"}`
  - Exemplo: `{"user":"cielo","password":"admin-1-2-3"}`
  - Padrão (docker): `{"user":"cielo","password":"admin-1-2-3"}`
  - **Nota**: Em produção, estas credenciais devem vir do Vault usando cod_vault e des_vault_secret da tabela server

### Spring Profile
- **SPRING_PROFILES_ACTIVE**: Profile ativo do Spring Boot
  - Valores: `local`, `docker`, `dev`, `prod`
  - Padrão (docker): `docker`

## Variáveis do Consumer

### Banco de Dados
- **DB_URL**: URL de conexão JDBC com o Oracle
  - Formato: `jdbc:oracle:thin:@<host>:<port>/<service>`
  - Exemplo: `jdbc:oracle:thin:@oracle:1521/XEPDB1`
  - Padrão (docker): `jdbc:oracle:thin:@oracle:1521/XEPDB1`

- **DB_USERNAME**: Usuário do banco de dados
  - Exemplo: `edi_user`
  - Padrão (docker): `edi_user`

- **DB_PASSWORD**: Senha do banco de dados
  - Exemplo: `edi_pass`
  - Padrão (docker): `edi_pass`

### RabbitMQ
- **RABBITMQ_HOST**: Host do servidor RabbitMQ
  - Exemplo: `rabbitmq`
  - Padrão (docker): `rabbitmq`

- **RABBITMQ_PORT**: Porta do RabbitMQ
  - Exemplo: `5672`
  - Padrão: `5672`

- **RABBITMQ_USERNAME**: Usuário do RabbitMQ
  - Exemplo: `admin`
  - Padrão (docker): `admin`

- **RABBITMQ_PASSWORD**: Senha do RabbitMQ
  - Exemplo: `admin`
  - Padrão (docker): `admin`

### AWS S3
- **AWS_ENDPOINT**: Endpoint do serviço S3
  - Exemplo (LocalStack): `http://localstack:4566`
  - Exemplo (AWS): `https://s3.amazonaws.com`
  - Padrão (docker): `http://localstack:4566`

- **AWS_REGION**: Região AWS
  - Exemplo: `us-east-1`
  - Padrão: `us-east-1`

- **AWS_ACCESS_KEY_ID**: Access Key ID da AWS
  - Exemplo (LocalStack): `test`
  - Padrão (docker): `test`
  - **Nota**: Em produção, usar credenciais reais da AWS

- **AWS_SECRET_ACCESS_KEY**: Secret Access Key da AWS
  - Exemplo (LocalStack): `test`
  - Padrão (docker): `test`
  - **Nota**: Em produção, usar credenciais reais da AWS

### SFTP
- **SFTP_CIELO_VAULT**: Credenciais SFTP de origem em formato JSON
  - Formato: `{"host":"<host>","port":"<port>","user":"<username>","password":"<password>"}`
  - Exemplo: `{"host":"sftp-origin","port":"22","user":"cielo","password":"admin-1-2-3"}`
  - Padrão (docker): `{"host":"sftp-origin","port":"22","user":"cielo","password":"admin-1-2-3"}`

- **SFTP_INTERNAL_VAULT**: Credenciais SFTP de destino interno em formato JSON
  - Formato: `{"host":"<host>","port":"<port>","user":"<username>","password":"<password>"}`
  - Exemplo: `{"host":"sftp-destination","port":"22","user":"internal","password":"internal-pass"}`
  - Padrão (docker): `{"host":"sftp-destination","port":"22","user":"internal","password":"internal-pass"}`

### Identificação de Layout
- **FILE_ORIGIN_BUFFER_LIMIT**: Tamanho do buffer em bytes para leitura inicial do arquivo durante identificação de layout
  - Tipo: Integer
  - Exemplo: `7000`
  - Padrão: `7000`
  - **Nota**: Buffer maior permite identificação mais precisa mas consome mais memória. Buffer menor é mais eficiente mas pode falhar em arquivos com headers grandes.

### Testes E2E
- **QUEUE_DELAY**: Delay em segundos antes de processar mensagens da fila
  - Tipo: Integer
  - Exemplo: `20`
  - Padrão: `0`
  - **Uso**: Apenas para testes E2E. Permite que o teste valide o estado inicial antes do Consumer processar a mensagem.
  - **Importante**: Deve ser `0` em produção. Use `make e2e` que configura automaticamente para `20` durante os testes.

### Spring Profile
- **SPRING_PROFILES_ACTIVE**: Profile ativo do Spring Boot
  - Valores: `local`, `docker`, `dev`, `prod`
  - Padrão (docker): `docker`

## Variáveis da Infraestrutura (Docker Compose)

### Oracle Database
- **ORACLE_PASSWORD**: Senha do usuário SYS/SYSTEM
  - Padrão: `oracle`

- **APP_USER**: Nome do usuário da aplicação
  - Padrão: `edi_user`

- **APP_USER_PASSWORD**: Senha do usuário da aplicação
  - Padrão: `edi_pass`

- **TZ**: Timezone do container
  - Padrão: `America/Sao_Paulo`

### RabbitMQ
- **RABBITMQ_DEFAULT_USER**: Usuário padrão do RabbitMQ
  - Padrão: `admin`

- **RABBITMQ_DEFAULT_PASS**: Senha padrão do RabbitMQ
  - Padrão: `admin`

- **TZ**: Timezone do container
  - Padrão: `America/Sao_Paulo`

### LocalStack (S3)
- **SERVICES**: Serviços do LocalStack a serem iniciados
  - Padrão: `s3`

- **DEBUG**: Nível de debug do LocalStack
  - Padrão: `0`

- **PERSISTENCE**: Habilita persistência de dados
  - Padrão: `1`

- **AWS_DEFAULT_REGION**: Região AWS padrão
  - Padrão: `us-east-1`

- **LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT**: Reconhecimento de requisito de conta
  - Padrão: `1`

- **TZ**: Timezone do container
  - Padrão: `America/Sao_Paulo`

### SFTP Servers
- **TZ**: Timezone dos containers SFTP
  - Padrão: `America/Sao_Paulo`

## Configuração por Ambiente

### Desenvolvimento Local (Docker Compose)
Todas as variáveis estão configuradas no `docker-compose.yml` com valores padrão para desenvolvimento.

### Desenvolvimento Local (Sem Docker)
Criar arquivo `.env` na raiz do projeto ou configurar variáveis de ambiente no sistema:

```bash
# Banco de Dados
export DB_URL=jdbc:oracle:thin:@localhost:1521/XEPDB1
export DB_USERNAME=edi_user
export DB_PASSWORD=edi_pass

# RabbitMQ
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=admin
export RABBITMQ_PASSWORD=admin

# AWS S3
export AWS_ENDPOINT=http://localhost:4566
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# SFTP
export SFTP_CIELO_VAULT='{"host":"localhost","port":"2222","user":"cielo","password":"admin-1-2-3"}'
export SFTP_INTERNAL_VAULT='{"host":"localhost","port":"2223","user":"internal","password":"internal-pass"}'

# Layout Identification
export FILE_ORIGIN_BUFFER_LIMIT=7000

# Testes (apenas para E2E)
export QUEUE_DELAY=0
```

### Produção
Em produção, as variáveis devem ser configuradas através de:
- Secrets do Kubernetes
- AWS Systems Manager Parameter Store
- HashiCorp Vault
- Variáveis de ambiente do container/pod

**Importante**: Nunca commitar credenciais reais no código ou docker-compose.yml!

## Validação de Variáveis

### Producer
O Producer valida as seguintes variáveis obrigatórias na inicialização:
- DB_URL
- DB_USERNAME
- DB_PASSWORD
- RABBITMQ_HOST

### Consumer
O Consumer valida as seguintes variáveis obrigatórias na inicialização:
- DB_URL
- DB_USERNAME
- DB_PASSWORD
- RABBITMQ_HOST
- AWS_ENDPOINT (se destino for S3)
- AWS_REGION (se destino for S3)

## Troubleshooting

### Erro de conexão com banco de dados
Verificar:
1. DB_URL está correto
2. DB_USERNAME e DB_PASSWORD estão corretos
3. Oracle está rodando e saudável: `docker ps`
4. Rede Docker está configurada corretamente

### Erro de conexão com RabbitMQ
Verificar:
1. RABBITMQ_HOST está correto
2. RABBITMQ_USERNAME e RABBITMQ_PASSWORD estão corretos
3. RabbitMQ está rodando e saudável: `docker ps`
4. Porta 5672 está acessível

### Erro de conexão com S3
Verificar:
1. AWS_ENDPOINT está correto
2. AWS_REGION está correto
3. LocalStack está rodando (desenvolvimento): `docker ps`
4. Credenciais AWS estão corretas (produção)

### Erro de conexão SFTP
Verificar:
1. SFTP_*_VAULT está em formato JSON válido
2. Host e porta estão corretos
3. Credenciais estão corretas
4. Servidor SFTP está acessível

### Testes E2E falhando
Verificar:
1. QUEUE_DELAY está configurado para 20: `export QUEUE_DELAY=20`
2. Ou usar `make e2e` que configura automaticamente
3. Todos os serviços estão rodando: `make status`

## Referências

- [Docker Compose File](../../docker-compose.yml)
- [Producer application.yml](../../producer/src/main/resources/application.yml)
- [Consumer application.yml](../../consumer/src/main/resources/application.yml)
- [Makefile](../../Makefile)
