# Guia de Execução Local - Controle de Arquivos

Este guia mostra como executar o sistema completo localmente no seu ambiente Windows.

## 📋 Pré-requisitos

Antes de começar, você precisa ter instalado:

1. ✅ **Java 17+** (você já tem)
2. ✅ **Docker Desktop** (você já tem)
3. ❌ **Maven 3.8+** (precisa instalar - veja Passo 1)

**Recursos necessários**:
- Mínimo 8GB RAM disponível
- 10GB espaço em disco
- Conexão com internet (primeira execução)

---

## 🔧 Passo 1: Instalar Maven

### Opção A: Via Chocolatey (Recomendado)

Se você tem Chocolatey instalado:

```powershell
choco install maven
```

### Opção B: Instalação Manual

1. **Baixar Maven**:
   - Acesse: https://maven.apache.org/download.cgi
   - Baixe o arquivo `apache-maven-3.9.6-bin.zip`

2. **Extrair**:
   - Extraia para: `C:\Program Files\Apache\maven`

3. **Configurar PATH**:
   ```powershell
   # Adicionar ao PATH do sistema
   [Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Apache\maven\bin", "Machine")
   ```

4. **Verificar instalação** (abra um novo terminal):
   ```powershell
   mvn --version
   ```

---

## 🐳 Passo 2: Iniciar Infraestrutura Local

### Opção A: Usar Script Automatizado (Recomendado)

```powershell
# No diretório raiz do projeto
.\scripts\start-local-env.bat
```

Este script irá:
- ✅ Verificar se Docker está rodando
- ✅ Criar arquivo .env se não existir
- ✅ Criar diretórios necessários
- ✅ Iniciar todos os serviços
- ✅ Aguardar serviços ficarem prontos
- ✅ Mostrar informações de acesso

### Opção B: Iniciar Manualmente

```powershell
# No diretório raiz do projeto
docker-compose up -d
```

Isso iniciará:
- **Oracle XE 21.3.0** (porta 1521, Enterprise Manager 5500)
- **RabbitMQ 3.12** (AMQP 5672, Management UI 15672)
- **LocalStack 3.0** (S3 na porta 4566)
- **SFTP Server** (porta 2222)

### 2.1 Verificar Status dos Serviços

```powershell
docker-compose ps
```

**Saída esperada**:
```
NAME                          STATUS              PORTS
controle-arquivos-oracle      Up (healthy)        0.0.0.0:1521->1521/tcp, 0.0.0.0:5500->5500/tcp
controle-arquivos-rabbitmq    Up (healthy)        0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
controle-arquivos-localstack  Up (healthy)        0.0.0.0:4566->4566/tcp
controle-arquivos-sftp        Up (healthy)        0.0.0.0:2222->22/tcp
```

⏱️ **Aguarde até que todos os serviços estejam com status `healthy`** (pode levar 2-3 minutos na primeira execução).

### 2.2 Verificar Logs (Opcional)

```powershell
# Ver logs de todos os serviços
docker-compose logs -f

# Ver logs de um serviço específico
docker-compose logs -f oracle
docker-compose logs -f rabbitmq
docker-compose logs -f localstack
docker-compose logs -f sftp
```

### 2.3 Acessar Interfaces Web

**RabbitMQ Management**:
- URL: http://localhost:15672
- Username: `admin`
- Password: `admin123`

**Oracle Enterprise Manager** (opcional):
- URL: https://localhost:5500/em
- Username: `system`
- Password: `Oracle123`

---

## 🗄️ Passo 3: Aplicar Scripts DDL no Oracle

### 3.1 Opções de Conexão ao Oracle

Você pode usar qualquer cliente SQL. Aqui estão as opções:

#### Opção A: SQL*Plus (se instalado)

```powershell
sqlplus system/Oracle123@localhost:1521/XE
```

#### Opção B: DBeaver (Recomendado)

1. Baixe: https://dbeaver.io/download/
2. Crie nova conexão Oracle:
   - **Host**: `localhost`
   - **Port**: `1521`
   - **Database**: `XE`
   - **Username**: `system`
   - **Password**: `Oracle123`

#### Opção C: SQL Developer

1. Baixe: https://www.oracle.com/tools/downloads/sqldev-downloads.html
2. Configure conexão com os mesmos parâmetros acima

### 3.2 Executar Scripts DDL

Os scripts estão em `scripts/ddl/` e devem ser executados **nesta ordem**:

```sql
-- 1. Criar sequences
@scripts/ddl/01-create-sequences.sql

-- 2. Criar tabelas
@scripts/ddl/02-create-tables.sql

-- 3. Criar índices
@scripts/ddl/03-create-indexes.sql

-- 4. Criar constraints
@scripts/ddl/04-create-constraints.sql

-- 5. Inserir dados de teste (IMPORTANTE para testes locais)
@scripts/ddl/05-insert-test-data.sql
```

**💡 Dica**: Se estiver usando DBeaver ou SQL Developer, você pode abrir cada arquivo e executar com F5 ou Ctrl+Enter.

### 3.3 Verificar Criação das Tabelas

```sql
-- Verificar tabelas criadas
SELECT table_name FROM user_tables ORDER BY table_name;

-- Deve retornar 11 tabelas:
-- CUSTOMER_IDENTIFICATION
-- CUSTOMER_IDENTIFICATION_RULE
-- FILE_ORIGIN
-- FILE_ORIGIN_CLIENT
-- FILE_ORIGIN_CLIENT_PROCESSING
-- JOB_CONCURRENCY_CONTROL
-- LAYOUT
-- LAYOUT_IDENTIFICATION_RULE
-- SERVER
-- SEVER_PATHS
-- SEVER_PATHS_IN_OUT
```

### 3.4 Verificar Dados de Teste (Opcional)

```sql
-- Verificar servidores SFTP configurados
SELECT * FROM server;

-- Verificar clientes cadastrados
SELECT * FROM customer_identification;

-- Verificar layouts cadastrados
SELECT * FROM layout;
```

---

## 🏗️ Passo 4: Compilar o Projeto

```powershell
# No diretório raiz do projeto
mvn clean install
```

Isso irá:
- ✅ Compilar todos os módulos (common, orchestrator, processor, integration-tests)
- ✅ Executar testes unitários
- ✅ Executar testes de propriedade (jqwik)
- ✅ Gerar relatórios de cobertura (JaCoCo)
- ✅ Gerar os JARs executáveis

**⏱️ Tempo estimado**: 5-10 minutos (primeira vez)

**📊 Saída esperada**:
```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] Controle de Arquivos - Parent .................. SUCCESS [  0.123 s]
[INFO] common ......................................... SUCCESS [ 45.678 s]
[INFO] orchestrator ................................... SUCCESS [ 23.456 s]
[INFO] processor ...................................... SUCCESS [ 34.567 s]
[INFO] integration-tests .............................. SUCCESS [ 56.789 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### 4.1 Verificar Cobertura de Testes (Opcional)

```powershell
# Gerar relatório de cobertura
mvn clean test jacoco:report

# Abrir relatórios no navegador
start common/target/site/jacoco/index.html
start orchestrator/target/site/jacoco/index.html
start processor/target/site/jacoco/index.html
```

**Meta de cobertura**: 80% linha, 75% branch

---

## 🚀 Passo 5: Executar o Orquestrador

Abra um terminal PowerShell e execute:

```powershell
cd orchestrator
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**📝 Aguarde até ver a mensagem**:
```
Started OrquestradorApplication in X.XXX seconds (JVM running for Y.YYY)
```

### 5.1 O que o Orquestrador faz?

- 🔄 Executa ciclos periódicos de coleta (a cada 5 minutos por padrão)
- 📂 Lista arquivos em servidores SFTP externos
- 💾 Registra novos arquivos no banco de dados Oracle
- 📨 Publica mensagens no RabbitMQ para processamento
- 🔒 Controla concorrência via `job_concurrency_control`

### 5.2 Verificar Health do Orquestrador

Em **outro terminal**:
```powershell
curl http://localhost:8080/actuator/health
```

**Resposta esperada**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "Oracle",
        "validationQuery": "isValid()"
      }
    },
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.12.0"
      }
    }
  }
}
```

### 5.3 Logs do Orquestrador

Você verá logs estruturados em JSON como:

```json
{
  "timestamp": "2026-03-23T10:30:45.123Z",
  "level": "INFO",
  "logger": "OrquestradorService",
  "message": "Iniciando ciclo de coleta",
  "context": {
    "correlationId": "abc-123-def"
  }
}
```

---

## ⚙️ Passo 6: Executar o Processador

Abra **outro terminal PowerShell** e execute:

```powershell
cd processor
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**📝 Aguarde até ver a mensagem**:
```
Started ProcessadorApplication in X.XXX seconds (JVM running for Y.YYY)
```

### 6.1 O que o Processador faz?

- 📥 Consome mensagens do RabbitMQ
- ⬇️ Baixa arquivos via streaming (sem carregar em memória)
- 🔍 Identifica cliente usando regras baseadas no nome do arquivo
- 📋 Identifica layout usando regras baseadas no nome ou conteúdo (header)
- ⬆️ Faz upload para destino (S3 ou SFTP) via streaming
- 📊 Atualiza rastreabilidade em todas as etapas

### 6.2 Verificar Health do Processador

Em **outro terminal**:
```powershell
curl http://localhost:8081/actuator/health
```

**Resposta esperada**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP"
    }
  }
}
```

### 6.3 Logs do Processador

Você verá logs estruturados como:

```json
{
  "timestamp": "2026-03-23T10:35:12.456Z",
  "level": "INFO",
  "logger": "ProcessadorService",
  "message": "Arquivo processado com sucesso",
  "context": {
    "correlationId": "abc-123-def",
    "fileName": "CIELO_20240115.txt",
    "cliente": "LOJA_SHOPPING",
    "layout": "CIELO_CSV_TRANSACOES"
  }
}
```

---

## ✅ Passo 7: Verificar Sistema Funcionando

Neste ponto, você deve ter:

- ✅ 4 containers Docker rodando (Oracle, RabbitMQ, LocalStack, SFTP)
- ✅ Orquestrador rodando (porta 8080)
- ✅ Processador rodando (porta 8081)
- ✅ Ambos com status "UP" nos health checks

**🎉 Parabéns! O sistema está rodando localmente!**

---

## 🧪 Passo 8: Testar o Fluxo End-to-End

Agora vamos testar o sistema completo colocando um arquivo no SFTP e acompanhando todo o processamento.

### 8.1 Preparar Arquivo de Teste

O projeto já tem arquivos de teste prontos em `test-data/files/`. Vamos usar um exemplo:

```powershell
# Ver arquivos de teste disponíveis
ls test-data/files/positive-cases/
```

### 8.2 Conectar ao SFTP e Fazer Upload

**Opção A: Via Cliente SFTP (linha de comando)**

```powershell
# Conectar ao SFTP
sftp -P 2222 sftpuser@localhost
# Senha quando solicitado: sftppass

# Fazer upload de um arquivo de teste
put test-data/files/positive-cases/LOJA_SHOPPING_20240115.csv upload/

# Listar arquivos
ls upload/

# Sair
exit
```

**Opção B: Via WinSCP ou FileZilla**

- **Host**: `localhost`
- **Port**: `2222`
- **Protocol**: SFTP
- **Username**: `sftpuser`
- **Password**: `sftppass`

Arraste o arquivo para o diretório `upload/`

### 8.3 Acompanhar Processamento

#### No Orquestrador (Terminal 1)

Você verá logs como:

```
INFO  OrquestradorService - Iniciando ciclo de coleta
INFO  SFTPClient - Conectando ao servidor SFTP localhost:2222
INFO  OrquestradorService - Arquivo encontrado: LOJA_SHOPPING_20240115.csv
INFO  OrquestradorService - Arquivo registrado no banco: idt_file_origin=1
INFO  RabbitMQPublisher - Mensagem publicada: correlationId=abc-123-def
INFO  OrquestradorService - Ciclo de coleta concluído: 1 arquivo(s) processado(s)
```

#### No Processador (Terminal 2)

Você verá logs como:

```
INFO  RabbitMQConsumer - Mensagem recebida: correlationId=abc-123-def
INFO  ProcessadorService - Iniciando processamento: LOJA_SHOPPING_20240115.csv
INFO  ClienteIdentificationService - Cliente identificado: LOJA_SHOPPING
INFO  LayoutIdentificationService - Layout identificado: CIELO_CSV_TRANSACOES
INFO  StreamingTransferService - Upload iniciado para S3: s3://controle-arquivos-local/raw/cielo/
INFO  StreamingTransferService - Upload concluído: 1024 bytes
INFO  RastreabilidadeService - Etapa PROCESSED registrada com sucesso
INFO  ProcessadorService - Arquivo processado com sucesso
```

### 8.4 Verificar Rastreabilidade no Banco

```sql
-- Ver arquivo coletado
SELECT 
    idt_file_origin,
    des_file_name,
    num_file_size,
    dat_timestamp_file,
    dat_created
FROM file_origin 
WHERE des_file_name = 'LOJA_SHOPPING_20240115.csv';

-- Ver cliente identificado
SELECT 
    fo.des_file_name,
    ci.des_customer_name,
    l.des_layout_name,
    fo.des_file_type
FROM file_origin fo
JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
JOIN customer_identification ci ON foc.idt_client = ci.idt_customer_identification
LEFT JOIN layout l ON fo.idt_layout = l.idt_layout
WHERE fo.des_file_name = 'LOJA_SHOPPING_20240115.csv';

-- Ver todas as etapas de processamento
SELECT 
    focp.des_step,
    focp.des_status,
    focp.dat_step_start,
    focp.dat_step_end,
    focp.des_message_error
FROM file_origin fo
JOIN file_origin_client foc ON fo.idt_file_origin = foc.idt_file_origin
JOIN file_origin_client_processing focp ON foc.idt_file_origin_client = focp.idt_file_origin_client
WHERE fo.des_file_name = 'LOJA_SHOPPING_20240115.csv'
ORDER BY focp.dat_created;
```

**Etapas esperadas**:
1. COLETA → EM_ESPERA
2. COLETA → PROCESSAMENTO
3. STAGING → CONCLUIDO (cliente identificado)
4. ORDINATION → CONCLUIDO (layout identificado)
5. PROCESSING → PROCESSAMENTO (upload iniciado)
6. PROCESSED → CONCLUIDO (upload concluído)

### 8.5 Verificar Arquivo no Destino

**LocalStack S3**:

```powershell
# Listar buckets
aws --endpoint-url=http://localhost:4566 s3 ls

# Listar arquivos no bucket
aws --endpoint-url=http://localhost:4566 s3 ls s3://controle-arquivos-local/raw/cielo/

# Baixar arquivo para verificar
aws --endpoint-url=http://localhost:4566 s3 cp s3://controle-arquivos-local/raw/cielo/LOJA_SHOPPING_20240115.csv ./downloaded.csv
```

### 8.6 Verificar no RabbitMQ Management

Acesse http://localhost:15672 (admin/admin123) e verifique:

- **Queues**: Fila de processamento deve estar vazia (mensagem foi consumida)
- **Connections**: Orquestrador e Processador devem estar conectados
- **Channels**: Canais ativos para publicação e consumo

---

## 🌐 Passo 9: Acessar Interfaces Web

### RabbitMQ Management UI

- **URL**: http://localhost:15672
- **Username**: `admin`
- **Password**: `admin123`

**O que você pode fazer**:
- 📊 Ver filas e mensagens
- 📈 Monitorar throughput
- 🔌 Ver conexões ativas (Orquestrador e Processador)
- 📨 Publicar mensagens manualmente (para testes)
- 🔍 Ver detalhes de exchanges e bindings

### Oracle Enterprise Manager (Opcional)

- **URL**: https://localhost:5500/em
- **Username**: `system`
- **Password**: `Oracle123`

**O que você pode fazer**:
- 📊 Monitorar performance do banco
- 🔍 Ver sessões ativas
- 📈 Ver estatísticas de queries
- 💾 Gerenciar tablespaces

---

## 🛠️ Comandos Úteis

### Gerenciar Infraestrutura Docker

```powershell
# Ver status de todos os serviços
docker-compose ps

# Ver logs em tempo real
docker-compose logs -f

# Ver logs de um serviço específico
docker-compose logs -f oracle
docker-compose logs -f rabbitmq
docker-compose logs -f localstack
docker-compose logs -f sftp

# Reiniciar um serviço específico
docker-compose restart oracle

# Reiniciar todos os serviços
docker-compose restart

# Parar todos os serviços
docker-compose stop

# Parar e remover containers
docker-compose down

# Parar e remover containers + volumes (APAGA DADOS!)
docker-compose down -v
```

### Gerenciar Aplicações Spring Boot

```powershell
# Parar aplicação
# Pressione Ctrl+C no terminal onde está rodando

# Ver logs com mais detalhes
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dlogging.level.root=DEBUG

# Executar em background (não recomendado para desenvolvimento)
start mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Verificar Health Checks

```powershell
# Orquestrador
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness

# Processador
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness

# Ver métricas Prometheus
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8081/actuator/prometheus
```

### Executar Testes

```powershell
# Testes unitários
mvn test

# Testes de propriedade (jqwik)
mvn test -Dtest="*PropertyTest"

# Testes de integração
mvn verify

# Testes com cobertura
mvn clean test jacoco:report

# Verificar se cobertura >= 80%
mvn clean test jacoco:check
```

### Limpar e Reconstruir

```powershell
# Limpar build anterior
mvn clean

# Compilar sem executar testes
mvn clean install -DskipTests

# Recompilar apenas um módulo
cd common
mvn clean install
```

---

## 🔧 Troubleshooting

### ❌ Problema: Maven não reconhecido

**Erro**: `mvn : O termo 'mvn' não é reconhecido`

**Solução**:
1. Instale Maven (veja Passo 1)
2. Feche e abra um novo terminal PowerShell
3. Verifique: `mvn --version`

---

### ❌ Problema: Porta já em uso

**Erro**: `Bind for 0.0.0.0:1521 failed: port is already allocated`

**Solução**:
```powershell
# Ver o que está usando a porta
netstat -ano | findstr :1521

# Parar o processo ou mudar a porta no docker-compose.yml
# Exemplo: mudar "1521:1521" para "1522:1521"
```

**Portas usadas pelo sistema**:
- 1521 (Oracle)
- 5500 (Oracle EM)
- 5672 (RabbitMQ AMQP)
- 15672 (RabbitMQ Management)
- 4566 (LocalStack S3)
- 2222 (SFTP)
- 8080 (Orquestrador)
- 8081 (Processador)

---

### ❌ Problema: Oracle não inicia

**Sintomas**: Container Oracle fica em status "starting" ou "unhealthy"

**Solução**:
```powershell
# Ver logs do Oracle
docker-compose logs oracle

# Verificar se há espaço em disco suficiente
docker system df

# Reiniciar apenas o Oracle
docker-compose restart oracle

# Se persistir, remover e recriar
docker-compose down
docker volume rm controle-arquivos-oracle-data
docker-compose up -d oracle
```

---

### ❌ Problema: Orquestrador não coleta arquivos

**Sintomas**: Arquivos no SFTP mas não aparecem no banco

**Verificações**:

1. **Scheduler está habilitado?**
   ```yaml
   # Verificar em common/src/main/resources/application-local.yml
   app:
     scheduler:
       enabled: true  # Deve ser true
       cron: "0 */5 * * * *"  # A cada 5 minutos
   ```

2. **Configurações estão cadastradas no banco?**
   ```sql
   -- Verificar servidores SFTP
   SELECT * FROM server;
   
   -- Verificar caminhos configurados
   SELECT * FROM sever_paths_in_out;
   ```

3. **Arquivos estão no diretório correto?**
   - Devem estar em `/home/sftpuser/upload/` no container SFTP
   - Ou no caminho configurado em `sever_paths`

4. **Ver logs do Orquestrador**:
   ```powershell
   # No terminal onde o Orquestrador está rodando
   # Procure por mensagens de erro ou warnings
   ```

---

### ❌ Problema: Processador não consome mensagens

**Sintomas**: Mensagens ficam na fila mas não são processadas

**Verificações**:

1. **RabbitMQ está rodando?**
   ```powershell
   docker-compose ps rabbitmq
   # Status deve ser "Up (healthy)"
   ```

2. **Fila existe e tem mensagens?**
   - Acesse http://localhost:15672
   - Vá em "Queues"
   - Verifique se a fila existe e tem mensagens

3. **Credenciais estão corretas?**
   ```yaml
   # Verificar em common/src/main/resources/application-local.yml
   spring:
     rabbitmq:
       host: localhost
       port: 5672
       username: admin
       password: admin123
   ```

4. **Ver logs do Processador**:
   ```powershell
   # Procure por erros de conexão ou autenticação
   ```

---

### ❌ Problema: Cliente não identificado

**Sintomas**: Arquivo processado mas erro "Cliente não identificado"

**Verificações**:

1. **Regras de identificação estão cadastradas?**
   ```sql
   SELECT * FROM customer_identification_rule 
   WHERE idt_acquirer = <id_adquirente>
   AND flg_active = 1;
   ```

2. **Regras estão ativas?**
   ```sql
   -- flg_active deve ser 1
   UPDATE customer_identification_rule 
   SET flg_active = 1 
   WHERE idt_customer_identification_rule = <id>;
   ```

3. **Nome do arquivo corresponde às regras?**
   - Verifique o critério (COMECA-COM, TERMINA-COM, CONTEM, IGUAL)
   - Verifique as posições de substring se aplicável
   - **TODAS** as regras do cliente devem retornar true (AND lógico)

4. **Ver logs detalhados**:
   ```powershell
   # Logs mostram quais regras foram aplicadas e o resultado
   ```

---

### ❌ Problema: Layout não identificado

**Sintomas**: Cliente identificado mas erro "Layout não identificado"

**Verificações**:

1. **Regras de layout estão cadastradas?**
   ```sql
   SELECT * FROM layout_identification_rule 
   WHERE idt_client = <id_cliente>
   AND idt_acquirer = <id_adquirente>
   AND flg_active = 1;
   ```

2. **Regras HEADER estão corretas?**
   - Sistema lê apenas os primeiros 7000 bytes do arquivo
   - Verifique se o valor esperado está no início do arquivo

3. **Arquivo tem conteúdo?**
   ```sql
   -- Verificar tamanho do arquivo
   SELECT des_file_name, num_file_size 
   FROM file_origin 
   WHERE des_file_name = '<nome_arquivo>';
   ```

---

### ❌ Problema: Erro de conexão com Vault

**Sintomas**: Erro ao obter credenciais do Vault

**Solução**:

Para ambiente local, o Vault está **desabilitado** por padrão:

```yaml
# Em application-local.yml
vault:
  enabled: false  # Vault desabilitado em local
```

Credenciais SFTP são configuradas diretamente:

```yaml
sftp:
  default:
    host: localhost
    port: 2222
    username: sftpuser
    password: sftppass
```

---

### ❌ Problema: Arquivo não aparece no S3

**Sintomas**: Processamento concluído mas arquivo não está no LocalStack

**Verificações**:

1. **LocalStack está rodando?**
   ```powershell
   docker-compose ps localstack
   curl http://localhost:4566/_localstack/health
   ```

2. **Bucket existe?**
   ```powershell
   aws --endpoint-url=http://localhost:4566 s3 ls
   
   # Se não existir, criar:
   aws --endpoint-url=http://localhost:4566 s3 mb s3://controle-arquivos-local
   ```

3. **Credenciais AWS estão corretas?**
   ```yaml
   # Em application-local.yml
   aws:
     credentials:
       access-key: test
       secret-key: test
   ```

4. **Ver logs de upload**:
   ```powershell
   # Procure por mensagens de StreamingTransferService
   ```

---

### ❌ Problema: Testes falhando

**Sintomas**: `mvn test` falha com erros

**Soluções comuns**:

1. **Testcontainers não consegue iniciar Docker**:
   ```powershell
   # Verificar se Docker está rodando
   docker info
   ```

2. **Falta de memória**:
   ```powershell
   # Aumentar memória do Maven
   set MAVEN_OPTS=-Xmx2048m
   mvn test
   ```

3. **Porta em uso durante testes**:
   ```powershell
   # Parar aplicações antes de rodar testes
   # Ctrl+C nos terminais do Orquestrador e Processador
   ```

---

### 💡 Dicas Gerais

1. **Sempre verifique os logs primeiro** - eles são estruturados e contêm informações detalhadas
2. **Use o RabbitMQ Management UI** - visualize filas e mensagens em tempo real
3. **Verifique o banco de dados** - a rastreabilidade registra todas as etapas
4. **Teste com arquivos simples primeiro** - use os arquivos em `test-data/files/positive-cases/`
5. **Aguarde o scheduler** - o Orquestrador coleta a cada 5 minutos por padrão

---

## 📚 Próximos Passos

Após rodar localmente com sucesso, você pode:

### 1. Explorar Mais Cenários de Teste

```powershell
# Ver todos os arquivos de teste disponíveis
ls test-data/files/

# Testar casos positivos (devem funcionar)
ls test-data/files/positive-cases/

# Testar casos negativos (devem falhar com erro esperado)
ls test-data/files/negative-cases/

# Testar casos extremos
ls test-data/files/edge-cases/
```

Consulte `test-data/README.md` para detalhes de cada cenário.

### 2. Executar Todos os Testes

```powershell
# Testes unitários
mvn test

# Testes de propriedade (jqwik) - 35 propriedades, 7000+ iterações
mvn test -Dtest="*PropertyTest"

# Testes de integração com Testcontainers
mvn verify

# Todos os testes com relatório de cobertura
mvn clean verify jacoco:report
```

### 3. Verificar Cobertura de Código

```powershell
# Gerar relatórios
mvn clean test jacoco:report

# Abrir no navegador
start common/target/site/jacoco/index.html
start orchestrator/target/site/jacoco/index.html
start processor/target/site/jacoco/index.html
```

**Meta**: 80% linha, 75% branch

### 4. Explorar a Documentação

- **[README.md](README.md)** - Visão geral do sistema
- **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)** - Configurações detalhadas
- **[docs/OPERATIONS.md](docs/OPERATIONS.md)** - Guia de operações e monitoramento
- **[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)** - Guia de desenvolvimento
- **[k8s/README.md](k8s/README.md)** - Deploy no Kubernetes
- **[test-data/README.md](test-data/README.md)** - Cenários de teste

### 5. Customizar Regras de Identificação

Adicione suas próprias regras no banco de dados:

```sql
-- Adicionar novo cliente
INSERT INTO customer_identification (
    idt_customer_identification,
    des_customer_name,
    idt_acquirer,
    flg_active
) VALUES (
    seq_customer_identification.NEXTVAL,
    'MEU_CLIENTE',
    1,  -- Cielo
    1
);

-- Adicionar regra de identificação
INSERT INTO customer_identification_rule (
    idt_customer_identification_rule,
    idt_customer_identification,
    idt_acquirer,
    des_criteria_type,
    des_value,
    num_starting_position,
    num_ending_position,
    num_processing_weight,
    flg_active
) VALUES (
    seq_customer_identification_rule.NEXTVAL,
    <id_cliente>,
    1,
    'COMECA-COM',
    'MEU_ARQUIVO_',
    NULL,
    NULL,
    100,
    1
);
```

### 6. Preparar para Deploy em Staging/Produção

1. **Revisar configurações de produção**:
   - `common/src/main/resources/application-prod.yml`
   - Configurar URLs reais (Oracle, RabbitMQ, S3, Vault)

2. **Configurar Secrets no Kubernetes**:
   ```bash
   kubectl create secret generic controle-arquivos-secrets \
     --from-literal=db-password='<senha_real>' \
     --from-literal=rabbitmq-password='<senha_real>' \
     --from-literal=vault-token='<token_real>'
   ```

3. **Aplicar manifests K8s**:
   ```bash
   kubectl apply -f k8s/configmap-prod.yaml
   kubectl apply -f k8s/orchestrator-deployment.yaml
   kubectl apply -f k8s/processor-deployment.yaml
   ```

Consulte [k8s/README.md](k8s/README.md) para instruções completas.

---

## 📖 Referências Rápidas

### Credenciais Locais

| Serviço | Acesso | Credenciais |
|---------|--------|-------------|
| Oracle | localhost:1521/XE | system / Oracle123 |
| RabbitMQ | localhost:5672 | admin / admin123 |
| RabbitMQ UI | http://localhost:15672 | admin / admin123 |
| SFTP | localhost:2222 | sftpuser / sftppass |
| LocalStack S3 | http://localhost:4566 | test / test |
| Orquestrador | http://localhost:8080 | - |
| Processador | http://localhost:8081 | - |

### Endpoints Úteis

| Endpoint | Descrição |
|----------|-----------|
| http://localhost:8080/actuator/health | Health do Orquestrador |
| http://localhost:8081/actuator/health | Health do Processador |
| http://localhost:8080/actuator/prometheus | Métricas do Orquestrador |
| http://localhost:8081/actuator/prometheus | Métricas do Processador |
| http://localhost:15672 | RabbitMQ Management UI |
| https://localhost:5500/em | Oracle Enterprise Manager |

### Comandos Rápidos

```powershell
# Iniciar tudo
.\scripts\start-local-env.bat

# Parar tudo
docker-compose down

# Ver logs
docker-compose logs -f

# Compilar
mvn clean install

# Executar Orquestrador
cd orchestrator && mvn spring-boot:run -Dspring-boot.run.profiles=local

# Executar Processador
cd processor && mvn spring-boot:run -Dspring-boot.run.profiles=local

# Testes
mvn test

# Cobertura
mvn clean test jacoco:report
```

---

## 🆘 Suporte

Se encontrar problemas:

1. ✅ **Verifique os logs** dos serviços e aplicações
2. ✅ **Consulte a seção Troubleshooting** acima
3. ✅ **Revise a documentação** em `docs/`
4. ✅ **Verifique o banco de dados** para rastreabilidade
5. ✅ **Use o RabbitMQ Management UI** para ver filas
6. ✅ **Crie uma issue** no repositório com detalhes do erro

---

## ✅ Checklist de Validação

Use este checklist para garantir que tudo está funcionando:

- [ ] Maven instalado e funcionando (`mvn --version`)
- [ ] Docker Desktop rodando
- [ ] 4 containers Docker com status "healthy"
- [ ] Scripts DDL executados com sucesso
- [ ] 11 tabelas criadas no Oracle
- [ ] Dados de teste inseridos
- [ ] Projeto compilado sem erros (`mvn clean install`)
- [ ] Orquestrador iniciado (porta 8080)
- [ ] Processador iniciado (porta 8081)
- [ ] Health checks retornando "UP"
- [ ] Arquivo de teste colocado no SFTP
- [ ] Arquivo coletado pelo Orquestrador
- [ ] Mensagem publicada no RabbitMQ
- [ ] Mensagem consumida pelo Processador
- [ ] Cliente identificado corretamente
- [ ] Layout identificado corretamente
- [ ] Arquivo enviado para destino (S3)
- [ ] Rastreabilidade completa no banco
- [ ] Logs estruturados visíveis

**🎉 Se todos os itens estão marcados, seu ambiente local está 100% funcional!**

---

**Última atualização**: 23 de Março de 2026  
**Versão do Sistema**: 1.0.0-SNAPSHOT  
**Documentação completa**: [README.md](README.md)
