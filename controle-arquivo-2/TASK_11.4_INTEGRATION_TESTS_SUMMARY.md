# Task 11.4 - Testes de Integração para Orquestrador

## Resumo

Implementados testes de integração completos para o Pod Orquestrador, validando o fluxo completo de orquestração desde o carregamento de configurações até a publicação de mensagens no RabbitMQ.

## Arquivos Criados

### 1. OrquestradorIntegrationTest.java
**Localização**: `orchestrator/src/test/java/com/controle/arquivos/orchestrator/integration/OrquestradorIntegrationTest.java`

Teste de integração completo usando **Testcontainers** para:
- Oracle Database (gvenzl/oracle-xe:21-slim-faststart)
- RabbitMQ (rabbitmq:3.12-management-alpine)
- SFTP Server (atmoz/sftp:alpine)

**Cenários de Teste**:
1. ✅ **Fluxo Completo de Orquestração** - Valida todo o ciclo de coleta
2. ✅ **Deduplicação de Arquivos** - Verifica que arquivos duplicados são ignorados
3. ✅ **Controle de Concorrência** - Testa bloqueio de execuções simultâneas
4. ✅ **Validação de Configurações** - Verifica tratamento de configurações inválidas
5. ✅ **Tratamento de Erro SFTP** - Testa recuperação após falha de conexão

### 2. OrquestradorServiceIntegrationTest.java
**Localização**: `orchestrator/src/test/java/com/controle/arquivos/orchestrator/service/OrquestradorServiceIntegrationTest.java`

Teste de integração usando **mocks** (alternativa sem Docker):
- Não requer containers Docker
- Execução mais rápida
- Valida mesma lógica de negócio

**Cenários de Teste**:
1. ✅ **Fluxo Completo com Mocks** - Valida orquestração com dependências mockadas
2. ✅ **Deduplicação com Mocks** - Testa lógica de deduplicação
3. ✅ **Bloqueio de Concorrência** - Verifica que execução ativa bloqueia novas
4. ✅ **Erro SFTP com Mocks** - Testa tratamento de erro de conexão

### 3. TestConfiguration.java
**Localização**: `orchestrator/src/test/java/com/controle/arquivos/orchestrator/integration/TestConfiguration.java`

Configuração de teste que fornece:
- Mock do VaultClient retornando credenciais de teste
- Permite testes sem servidor Vault real

### 4. README.md
**Localização**: `orchestrator/src/test/java/com/controle/arquivos/orchestrator/integration/README.md`

Documentação completa dos testes de integração incluindo:
- Visão geral dos containers
- Descrição de cada cenário de teste
- Instruções de execução
- Troubleshooting
- Melhorias futuras

## Dependências Adicionadas

### orchestrator/pom.xml
```xml
<!-- Testcontainers JUnit Jupiter -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Awaitility para testes assíncronos -->
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
    <scope>test</scope>
</dependency>

<!-- Oracle JDBC Driver -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <scope>test</scope>
</dependency>
```

### pom.xml (parent)
```xml
<!-- Testcontainers BOM -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.19.3</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

## Requisitos Validados

### Requisito 1.1 - Carregar Configurações
✅ Testes verificam que configurações são carregadas corretamente do banco de dados

### Requisito 2.1 - Listar Arquivos SFTP
✅ Testes validam conexão SFTP e listagem de arquivos

### Requisito 2.3 - Deduplicação
✅ Testes verificam que arquivos duplicados são ignorados

### Requisito 2.5 - Recuperação de Falhas SFTP
✅ Testes validam que erro de conexão não interrompe processamento

### Requisito 3.1 - Registrar Arquivos
✅ Testes verificam que arquivos são registrados em `file_origin`

### Requisito 4.1 - Publicar Mensagens RabbitMQ
✅ Testes validam que mensagens são publicadas corretamente

### Requisito 5.1, 5.2, 5.3 - Controle de Concorrência
✅ Testes verificam controle via `job_concurrency_control`

## Cobertura de Testes

### Fluxo Completo
- ✅ Carregamento de configurações do banco
- ✅ Validação de configurações (servidor origem e destino)
- ✅ Obtenção de credenciais do Vault (mockado)
- ✅ Conexão SFTP e listagem de arquivos
- ✅ Verificação de deduplicação
- ✅ Registro de arquivos em `file_origin`
- ✅ Publicação de mensagens no RabbitMQ
- ✅ Controle de concorrência via `job_concurrency_control`

### Cenários de Erro
- ✅ Configurações inválidas (servidor não encontrado)
- ✅ Erro de conexão SFTP
- ✅ Execução bloqueada por concorrência
- ✅ Arquivos duplicados

## Execução dos Testes

### Com Testcontainers (requer Docker)
```bash
mvn test -Dtest=OrquestradorIntegrationTest -pl orchestrator
```

### Com Mocks (sem Docker)
```bash
mvn test -Dtest=OrquestradorServiceIntegrationTest -pl orchestrator
```

### Todos os testes do Orquestrador
```bash
mvn test -pl orchestrator
```

## Tempo de Execução

- **Testcontainers** (primeira vez): ~2-3 minutos (download de imagens)
- **Testcontainers** (subsequente): ~30-60 segundos
- **Mocks**: ~5-10 segundos

## Observações Técnicas

### Testcontainers
- Containers são iniciados automaticamente antes dos testes
- Portas são mapeadas dinamicamente via `@DynamicPropertySource`
- Containers são destruídos após os testes
- Requer Docker instalado e em execução

### SFTP Container
- Usa imagem `atmoz/sftp:alpine`
- Credenciais: `testuser:testpass`
- Arquivos de teste são criados em `@BeforeAll`
- Diretório: `/upload/test`

### Oracle Container
- Usa imagem `gvenzl/oracle-xe:21-slim-faststart` (mais rápida que oficial)
- JPA configurado com `create-drop` para limpar entre testes
- Banco é recriado para cada execução de teste

### RabbitMQ Container
- Usa imagem com management console
- Fila é criada automaticamente pela aplicação
- Mensagens são verificadas com `awaitility` para testes assíncronos

## Melhorias Futuras

1. **Testes de Performance**
   - Medir throughput com grande volume de arquivos
   - Testar limites de memória com arquivos grandes

2. **Testes de Resiliência**
   - Simular falhas de rede intermitentes
   - Testar recuperação após crash do container

3. **Testes de Segurança**
   - Validar que credenciais não aparecem em logs
   - Testar autenticação SFTP com chaves SSH

4. **Testes de Carga**
   - Múltiplas instâncias do Orquestrador
   - Concorrência real com threads

## Conclusão

Os testes de integração fornecem cobertura completa do fluxo de orquestração, validando todos os requisitos especificados. Duas abordagens foram implementadas:

1. **Testcontainers**: Testes end-to-end com dependências reais
2. **Mocks**: Testes rápidos sem dependências externas

Ambas as abordagens são complementares e garantem a qualidade do código.
