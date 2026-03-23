# Docker Compose - Checklist de Validação

Este documento contém um checklist para validar que o ambiente Docker Compose está funcionando corretamente.

## ✅ Checklist de Validação

### 1. Pré-requisitos

- [ ] Docker Engine instalado (versão 20.10+)
- [ ] Docker Compose instalado (versão 2.0+)
- [ ] Mínimo 8GB RAM disponível
- [ ] Mínimo 20GB espaço em disco disponível
- [ ] Portas necessárias disponíveis: 1521, 5500, 5672, 15672, 4566, 2222

### 2. Inicialização

- [ ] Arquivo `.env` criado a partir de `.env.example`
- [ ] Diretórios `scripts/ddl`, `scripts/localstack`, `scripts/sftp` criados
- [ ] Comando `docker-compose up -d` executado com sucesso
- [ ] Todos os 4 containers iniciados

### 3. Health Checks

Verificar com `docker-compose ps`:

- [ ] Container `controle-arquivos-oracle` com status "healthy"
- [ ] Container `controle-arquivos-rabbitmq` com status "healthy"
- [ ] Container `controle-arquivos-localstack` com status "healthy"
- [ ] Container `controle-arquivos-sftp` com status "healthy"

### 4. Validação Oracle Database

**Teste de Conexão:**
```bash
docker exec -it controle-arquivos-oracle sqlplus system/Oracle123@XE
```

- [ ] Conexão estabelecida com sucesso
- [ ] Consegue executar: `SELECT 1 FROM DUAL;`
- [ ] Enterprise Manager acessível em https://localhost:5500/em

**Teste JDBC:**
```bash
# URL: jdbc:oracle:thin:@localhost:1521:XE
# User: system
# Password: Oracle123
```

- [ ] Conexão JDBC funciona
- [ ] Consegue criar tabela de teste
- [ ] Consegue inserir e consultar dados

### 5. Validação RabbitMQ

**Teste Management UI:**
- [ ] Acesso a http://localhost:15672 funciona
- [ ] Login com admin/admin123 funciona
- [ ] Dashboard carrega corretamente

**Teste AMQP:**
```bash
docker exec -it controle-arquivos-rabbitmq rabbitmq-diagnostics ping
```

- [ ] Comando retorna "Ping succeeded"
- [ ] Porta 5672 acessível: `nc -zv localhost 5672`

**Teste de Fila:**
- [ ] Consegue criar fila via Management UI
- [ ] Consegue publicar mensagem de teste
- [ ] Consegue consumir mensagem de teste

### 6. Validação LocalStack (S3)

**Teste Health:**
```bash
curl http://localhost:4566/_localstack/health
```

- [ ] Retorna status 200
- [ ] Serviço S3 aparece como "running"

**Teste AWS CLI:**
```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
aws --endpoint-url=http://localhost:4566 s3 ls
```

- [ ] Comando executa sem erros
- [ ] Buckets criados aparecem na listagem:
  - [ ] controle-arquivos-dev
  - [ ] controle-arquivos-staging
  - [ ] controle-arquivos-prod

**Teste Upload/Download:**
```bash
echo "test" > test.txt
aws --endpoint-url=http://localhost:4566 s3 cp test.txt s3://controle-arquivos-dev/
aws --endpoint-url=http://localhost:4566 s3 ls s3://controle-arquivos-dev/
```

- [ ] Upload funciona
- [ ] Arquivo aparece na listagem
- [ ] Download funciona

### 7. Validação SFTP Server

**Teste Conexão:**
```bash
sftp -P 2222 sftpuser@localhost
# Password: sftppass
```

- [ ] Conexão estabelecida
- [ ] Consegue listar diretório: `ls`
- [ ] Diretório `/upload` existe

**Teste Upload:**
```bash
echo "test" > test.txt
sftp -P 2222 sftpuser@localhost <<EOF
put test.txt /upload/test.txt
ls /upload
bye
EOF
```

- [ ] Upload funciona
- [ ] Arquivo aparece na listagem

**Teste Download:**
```bash
sftp -P 2222 sftpuser@localhost <<EOF
get /upload/test.txt downloaded.txt
bye
EOF
```

- [ ] Download funciona
- [ ] Conteúdo do arquivo está correto

### 8. Validação de Rede

**Teste Comunicação entre Containers:**
```bash
docker exec -it controle-arquivos-rabbitmq ping -c 1 oracle
docker exec -it controle-arquivos-rabbitmq ping -c 1 localstack
docker exec -it controle-arquivos-rabbitmq ping -c 1 sftp
```

- [ ] Todos os pings funcionam
- [ ] Resolução de nomes funciona

### 9. Validação de Volumes

**Verificar Volumes Criados:**
```bash
docker volume ls | grep controle-arquivos
```

- [ ] Volume `controle-arquivos-oracle-data` existe
- [ ] Volume `controle-arquivos-rabbitmq-data` existe
- [ ] Volume `controle-arquivos-rabbitmq-logs` existe
- [ ] Volume `controle-arquivos-localstack-data` existe
- [ ] Volume `controle-arquivos-sftp-data` existe

**Teste Persistência:**
1. Criar dados de teste em cada serviço
2. Parar containers: `docker-compose down`
3. Iniciar novamente: `docker-compose up -d`
4. Verificar se dados persistiram

- [ ] Dados do Oracle persistiram
- [ ] Filas do RabbitMQ persistiram
- [ ] Buckets do LocalStack persistiram
- [ ] Arquivos do SFTP persistiram

### 10. Validação de Logs

**Verificar Logs:**
```bash
docker-compose logs oracle
docker-compose logs rabbitmq
docker-compose logs localstack
docker-compose logs sftp
```

- [ ] Logs do Oracle não mostram erros críticos
- [ ] Logs do RabbitMQ não mostram erros críticos
- [ ] Logs do LocalStack não mostram erros críticos
- [ ] Logs do SFTP não mostram erros críticos

### 11. Validação de Performance

**Recursos:**
```bash
docker stats --no-stream
```

- [ ] Uso de memória dentro dos limites esperados
- [ ] Uso de CPU razoável
- [ ] Sem containers reiniciando constantemente

### 12. Testes de Integração

- [ ] Aplicação Spring Boot consegue conectar ao Oracle
- [ ] Aplicação Spring Boot consegue conectar ao RabbitMQ
- [ ] Aplicação Spring Boot consegue conectar ao LocalStack S3
- [ ] Aplicação Spring Boot consegue conectar ao SFTP

## 🔧 Troubleshooting

Se algum item falhar, consulte a seção "Troubleshooting" em [DOCKER_SETUP.md](DOCKER_SETUP.md).

## 📊 Resultado

- **Total de Itens**: 60+
- **Itens Validados**: ___
- **Status**: ⬜ Pendente | ✅ Aprovado | ❌ Reprovado

## 📝 Observações

Anote aqui quaisquer problemas encontrados ou observações importantes:

```
[Espaço para observações]
```

## ✅ Aprovação

- [ ] Todos os itens críticos validados
- [ ] Ambiente pronto para desenvolvimento
- [ ] Documentação revisada e atualizada

**Validado por**: _______________  
**Data**: _______________
