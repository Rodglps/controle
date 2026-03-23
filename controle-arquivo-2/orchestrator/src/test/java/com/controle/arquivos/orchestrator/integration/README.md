# Testes de Integração do Orquestrador

## Visão Geral

Este pacote contém testes de integração completos para o Pod Orquestrador usando **Testcontainers**.

Os testes validam o fluxo completo de orquestração:
1. Carregar configurações do banco de dados Oracle
2. Listar arquivos em servidor SFTP
3. Registrar arquivos no banco de dados
4. Publicar mensagens no RabbitMQ
5. Controlar concorrência com múltiplas execuções

## Containers Utilizados

### Oracle Database
- **Imagem**: `gvenzl/oracle-xe:21-slim-faststart`
- **Uso**: Banco de dados para armazenar configurações e arquivos coletados
- **Porta**: Mapeada dinamicamente pelo Testcontainers

### RabbitMQ
- **Imagem**: `rabbitmq:3.12-management-alpine`
- **Uso**: Message broker para publicação de mensagens de processamento
- **Porta AMQP**: Mapeada dinamicamente pelo Testcontainers

### SFTP Server
- **Imagem**: `atmoz/sftp:alpine`
- **Uso**: Servidor SFTP simulado para testes de coleta de arquivos
- **Credenciais**: `testuser:testpass`
- **Diretório**: `/upload/test`
- **Porta**: 22 (mapeada dinamicamente)

## Cenários de Teste

### 1. Fluxo Completo de Orquestração
**Teste**: `deveExecutarFluxoCompletoDeOrquestracao()`

Valida o ciclo completo:
- Configurações são carregadas do banco
- Arquivos são listados no SFTP
- Arquivos são registrados em `file_origin`
- Mensagens são publicadas no RabbitMQ
- Controle de concorrência é atualizado

**Valida Requisitos**: 1.1, 2.1, 3.1, 4.1

### 2. Deduplicação de Arquivos
**Teste**: `deveIgnorarArquivosDuplicados()`

Valida que:
- Arquivos já registrados não são processados novamente
- Índice único em `file_origin` previne duplicatas
- Sistema continua processando outros arquivos

**Valida Requisito**: 2.3

### 3. Controle de Concorrência
**Teste**: `deveControlarConcorrenciaDeExecucoes()`

Valida que:
- Execução RUNNING bloqueia novas execuções
- Sistema verifica `job_concurrency_control` antes de iniciar
- Múltiplas instâncias não processam simultaneamente

**Valida Requisitos**: 5.1, 5.2, 5.3

### 4. Validação de Configurações
**Teste**: `deveValidarConfiguracoes()`

Valida que:
- Configurações inválidas são ignoradas
- Sistema registra erros estruturados
- Processamento continua com configurações válidas

**Valida Requisitos**: 1.2, 1.3

### 5. Tratamento de Erro SFTP
**Teste**: `deveContinuarAposErroDeConexaoSFTP()`

Valida que:
- Erro de conexão SFTP não interrompe processamento
- Sistema registra erro e continua com próximo servidor
- Job é marcado como COMPLETED mesmo com erros parciais

**Valida Requisito**: 2.5

## Arquivos de Teste

Os testes criam automaticamente arquivos no servidor SFTP:
- `CIELO_20240115_001.txt` - Arquivo de teste 1
- `CIELO_20240115_002.txt` - Arquivo de teste 2
- `REDE_20240115_001.txt` - Arquivo de teste 3

## Configuração de Teste

### VaultClient Mock
O `TestConfiguration` fornece um mock do `VaultClient` que retorna credenciais de teste:
- Username: `testuser`
- Password: `testpass`

Isso permite que os testes funcionem sem um servidor Vault real.

### Propriedades Dinâmicas
As propriedades de conexão são configuradas dinamicamente via `@DynamicPropertySource`:
- URLs e portas dos containers são injetadas automaticamente
- Vault é desabilitado (`app.vault.enabled=false`)
- JPA usa `create-drop` para limpar banco entre testes

## Executando os Testes

### Pré-requisitos
- Docker instalado e em execução
- Java 17+
- Maven 3.8+

### Comando
```bash
mvn test -Dtest=OrquestradorIntegrationTest -pl orchestrator
```

### Tempo de Execução
- Primeira execução: ~2-3 minutos (download de imagens Docker)
- Execuções subsequentes: ~30-60 segundos

## Troubleshooting

### Containers não iniciam
- Verifique se o Docker está em execução
- Verifique se há espaço em disco suficiente
- Verifique logs do Docker: `docker logs <container-id>`

### Timeout ao conectar SFTP
- O teste aguarda 3 segundos para o SFTP inicializar
- Se necessário, aumente o sleep em `setupSFTP()`

### Mensagens não aparecem no RabbitMQ
- Verifique se a fila foi criada corretamente
- Verifique configuração do exchange e routing key
- Use `await()` com timeout maior se necessário

## Melhorias Futuras

1. **Testes de Performance**
   - Medir throughput de coleta
   - Testar com grande volume de arquivos

2. **Testes de Resiliência**
   - Simular falhas de rede
   - Testar recuperação após crash

3. **Testes de Segurança**
   - Validar que credenciais não são expostas em logs
   - Testar autenticação SFTP com chaves SSH

## Referências

- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Oracle Testcontainers Module](https://www.testcontainers.org/modules/databases/oraclexe/)
- [RabbitMQ Testcontainers Module](https://www.testcontainers.org/modules/rabbitmq/)
- [SFTP Docker Image](https://hub.docker.com/r/atmoz/sftp)
