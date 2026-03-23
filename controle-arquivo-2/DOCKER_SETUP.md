# Docker Compose - Ambiente Local de Desenvolvimento

Este documento descreve como configurar e usar o ambiente local de desenvolvimento com Docker Compose.

## Serviços Incluídos

O ambiente local inclui todos os serviços necessários para desenvolvimento e testes:

1. **Oracle XE 21c** - Banco de dados relacional
2. **RabbitMQ 3.12** - Message broker com interface de gerenciamento
3. **LocalStack 3.0** - Simulação de AWS S3 para testes locais
4. **SFTP Server** - Servidor SFTP para simular servidores de adquirentes

## Pré-requisitos

- Docker Engine 20.10+
- Docker Compose 2.0+
- Mínimo 8GB RAM disponível
- Mínimo 20GB espaço em disco

## Configuração Inicial

### 1. Copiar arquivo de variáveis de ambiente

```bash
cp .env.example .env
```

### 2. Criar diretórios necessários

Os diretórios são criados automaticamente, mas você pode criá-los manualmente se necessário:

```bash
mkdir -p scripts/ddl
mkdir -p scripts/localstack
mkdir -p scripts/sftp
```

### 3. Iniciar os serviços

```bash
docker-compose up -d
```

### 4. Verificar status dos serviços

```bash
docker-compose ps
```

Todos os serviços devem estar com status "healthy" após alguns minutos.

## Acesso aos Serviços

### Oracle Database

- **Host**: localhost
- **Port**: 1521
- **SID**: XE
- **Service Name**: XE
- **System User**: system
- **System Password**: Oracle123
- **Connection String**: `jdbc:oracle:thin:@localhost:1521:XE`

**Conectar via SQL*Plus:**
```bash
docker exec -it controle-arquivos-oracle sqlplus system/Oracle123@XE
```

**Enterprise Manager:**
- URL: https://localhost:5500/em
- User: system
- Password: Oracle123

### RabbitMQ

- **Host**: localhost
- **AMQP Port**: 5672
- **Management UI**: http://localhost:15672
- **Username**: admin
- **Password**: admin123

**Acessar Management UI:**
Abra http://localhost:15672 no navegador e faça login com admin/admin123

### LocalStack (S3)

- **Endpoint**: http://localhost:4566
- **Region**: us-east-1
- **Access Key**: test
- **Secret Key**: test

**Buckets criados automaticamente:**
- controle-arquivos-dev
- controle-arquivos-staging
- controle-arquivos-prod

**Testar com AWS CLI:**
```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

**Configurar AWS CLI para LocalStack:**
```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
alias awslocal="aws --endpoint-url=http://localhost:4566"
```

### SFTP Server

- **Host**: localhost
- **Port**: 2222
- **Username**: sftpuser
- **Password**: sftppass
- **Upload Directory**: /upload

**Conectar via SFTP:**
```bash
sftp -P 2222 sftpuser@localhost
```

**Copiar arquivo de teste:**
```bash
echo "Test file" > test.txt
sftp -P 2222 sftpuser@localhost <<EOF
put test.txt /upload/test.txt
ls /upload
bye
EOF
```

## Comandos Úteis

### Iniciar todos os serviços
```bash
docker-compose up -d
```

### Parar todos os serviços
```bash
docker-compose down
```

### Parar e remover volumes (limpar dados)
```bash
docker-compose down -v
```

### Ver logs de todos os serviços
```bash
docker-compose logs -f
```

### Ver logs de um serviço específico
```bash
docker-compose logs -f oracle
docker-compose logs -f rabbitmq
docker-compose logs -f localstack
docker-compose logs -f sftp
```

### Reiniciar um serviço específico
```bash
docker-compose restart oracle
```

### Verificar health checks
```bash
docker-compose ps
```

### Executar comando em um container
```bash
docker exec -it controle-arquivos-oracle bash
docker exec -it controle-arquivos-rabbitmq bash
```

## Aplicação de Scripts DDL

Os scripts DDL devem ser colocados no diretório `scripts/ddl/`. Eles serão executados automaticamente quando o container Oracle for iniciado pela primeira vez.

**Estrutura recomendada:**
```
scripts/ddl/
├── 01-create-sequences.sql
├── 02-create-tables.sql
├── 03-create-indexes.sql
├── 04-create-constraints.sql
└── 05-insert-test-data.sql
```

**Executar scripts manualmente:**
```bash
docker exec -i controle-arquivos-oracle sqlplus system/Oracle123@XE < scripts/ddl/01-create-sequences.sql
```

## Troubleshooting

### Oracle não inicia

**Problema**: Container Oracle fica reiniciando ou não passa no health check.

**Solução**:
1. Verificar logs: `docker-compose logs oracle`
2. Aumentar memória disponível para Docker (mínimo 4GB para Oracle)
3. Remover volume e reiniciar: `docker-compose down -v && docker-compose up -d oracle`

### RabbitMQ não aceita conexões

**Problema**: Erro de conexão ao tentar conectar no RabbitMQ.

**Solução**:
1. Verificar se o serviço está healthy: `docker-compose ps`
2. Verificar logs: `docker-compose logs rabbitmq`
3. Aguardar alguns segundos após o start (pode levar até 30s)

### LocalStack S3 não responde

**Problema**: Erro ao tentar acessar endpoint LocalStack.

**Solução**:
1. Verificar health check: `curl http://localhost:4566/_localstack/health`
2. Verificar logs: `docker-compose logs localstack`
3. Reiniciar serviço: `docker-compose restart localstack`

### SFTP não aceita conexões

**Problema**: Erro ao conectar via SFTP.

**Solução**:
1. Verificar se porta 2222 está disponível: `netstat -an | grep 2222`
2. Verificar logs: `docker-compose logs sftp`
3. Testar conexão: `nc -zv localhost 2222`

### Porta já em uso

**Problema**: Erro "port is already allocated".

**Solução**:
1. Identificar processo usando a porta: `lsof -i :1521` (substituir pela porta)
2. Parar o processo ou alterar a porta no docker-compose.yml
3. Reiniciar: `docker-compose up -d`

## Volumes e Persistência

Os dados são persistidos em volumes Docker nomeados:

- `controle-arquivos-oracle-data` - Dados do Oracle
- `controle-arquivos-rabbitmq-data` - Dados do RabbitMQ
- `controle-arquivos-rabbitmq-logs` - Logs do RabbitMQ
- `controle-arquivos-localstack-data` - Dados do LocalStack
- `controle-arquivos-sftp-data` - Arquivos do SFTP

**Listar volumes:**
```bash
docker volume ls | grep controle-arquivos
```

**Remover todos os volumes (CUIDADO: apaga todos os dados):**
```bash
docker-compose down -v
```

**Backup de volume Oracle:**
```bash
docker run --rm -v controle-arquivos-oracle-data:/data -v $(pwd):/backup alpine tar czf /backup/oracle-backup.tar.gz /data
```

## Configuração de Rede

Todos os serviços estão na mesma rede Docker (`controle-arquivos-network`), permitindo comunicação entre containers usando os nomes dos serviços:

- `oracle:1521` - Acesso ao Oracle de dentro de outros containers
- `rabbitmq:5672` - Acesso ao RabbitMQ de dentro de outros containers
- `localstack:4566` - Acesso ao LocalStack de dentro de outros containers
- `sftp:22` - Acesso ao SFTP de dentro de outros containers

## Próximos Passos

1. Aplicar scripts DDL (Task 1.3)
2. Configurar aplicações Spring Boot para usar estes serviços
3. Executar testes de integração
4. Desenvolver e testar funcionalidades

## Referências

- [Oracle Database Express Edition](https://www.oracle.com/database/technologies/appdev/xe.html)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [atmoz/sftp Docker Image](https://github.com/atmoz/sftp)
