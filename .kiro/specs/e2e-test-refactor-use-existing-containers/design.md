# E2E Test Refactor - Use Existing Containers Bugfix Design

## Overview

O teste E2E `FileTransferE2ETest` atualmente cria containers duplicados usando TestContainers, ignorando a infraestrutura já disponível via `make up` (docker-compose). Esta abordagem causa desperdício de recursos, lentidão na execução, e potenciais conflitos de portas.

A estratégia de correção consiste em refatorar `E2ETestBase` para detectar e conectar aos containers docker-compose existentes através de variáveis de ambiente baseadas em `cod_server`, eliminando a criação de containers TestContainers e reutilizando a infraestrutura já inicializada.

## Glossary

- **Bug_Condition (C)**: A condição que dispara o bug - quando o teste E2E cria containers TestContainers ao invés de usar os containers docker-compose existentes
- **Property (P)**: O comportamento desejado - o teste E2E deve conectar aos containers docker-compose usando variáveis de ambiente
- **Preservation**: Os cenários de teste (SFTP-to-S3, SFTP-to-SFTP) e validações (integridade, auditoria) devem continuar funcionando exatamente como antes
- **E2ETestBase**: Classe base em `commons/src/test/java/com/concil/edi/commons/e2e/E2ETestBase.java` que gerencia a infraestrutura de teste
- **setupInfrastructure()**: Método anotado com `@BeforeAll` que atualmente cria containers TestContainers
- **teardownInfrastructure()**: Método anotado com `@AfterAll` que atualmente destrói containers TestContainers
- **cod_server**: Identificador único do servidor (SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION) usado como chave para variáveis de ambiente

## Bug Details

### Bug Condition

O bug manifesta quando o teste E2E é executado após `make up` ter iniciado os containers docker-compose. O método `E2ETestBase.setupInfrastructure()` cria novos containers TestContainers ao invés de detectar e conectar aos containers existentes, causando duplicação de infraestrutura.

**Formal Specification:**
```
FUNCTION isBugCondition(testExecution)
  INPUT: testExecution of type E2ETestExecution
  OUTPUT: boolean
  
  RETURN testExecution.hasDockerComposeRunning() == true
         AND testExecution.usesTestContainers() == true
         AND NOT testExecution.connectsToExistingContainers()
END FUNCTION
```

### Examples

- **Exemplo 1**: Executar `make up` seguido de `make e2e` → O teste cria containers Oracle, RabbitMQ, LocalStack, SFTP duplicados ao invés de usar os existentes nas portas 1521, 5672, 4566, 2222, 2223
- **Exemplo 2**: Executar `make e2e` com docker-compose rodando → O teste ignora as variáveis de ambiente e cria containers com portas mapeadas dinamicamente
- **Exemplo 3**: Executar `make e2e` após `make init-db` → O teste cria um banco de dados isolado e executa DDL novamente ao invés de usar o banco já inicializado
- **Edge Case**: Executar `make e2e` sem docker-compose rodando → O teste deveria falhar rapidamente com mensagem clara ao invés de tentar criar TestContainers

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Os cenários de teste `testSftpToS3Transfer` e `testSftpToSftpTransfer` devem continuar validando o fluxo completo
- As validações de integridade (tamanho, SHA-256) devem continuar funcionando
- As validações de auditoria (dat_update, nam_change_agent) devem continuar funcionando
- Os métodos auxiliares (uploadToSftpOrigin, downloadFromSftpDestination, downloadFromS3, fileExistsInS3, fileExistsInSftpDestination, calculateSHA256) devem continuar com a mesma interface
- Os timeouts (150s para detecção, 120s para processamento, 5min global) devem permanecer inalterados
- A geração de conteúdo de teste (1MB para S3, 500KB para SFTP) deve permanecer inalterada

**Scope:**
Todas as execuções de teste que NÃO envolvem a inicialização/destruição de infraestrutura devem ser completamente inalteradas. Isso inclui:
- Lógica de validação de registros file_origin
- Lógica de upload/download de arquivos
- Cálculo de hash SHA-256
- Polling de status no banco de dados
- Asserções de integridade

## Hypothesized Root Cause

Baseado na descrição do bug, as causas mais prováveis são:

1. **Uso de TestContainers por Design**: A classe `E2ETestBase` foi originalmente implementada com TestContainers usando a anotação `@Testcontainers` e criação explícita de containers no método `setupInfrastructure()`, sem considerar a possibilidade de reutilizar containers existentes

2. **Falta de Detecção de Ambiente**: O código não verifica se containers docker-compose já estão rodando antes de criar novos containers TestContainers

3. **Configuração Hardcoded**: As configurações de conexão (jdbcUrl, rabbitMQHost, etc.) são obtidas diretamente dos containers TestContainers ao invés de ler variáveis de ambiente

4. **Inicialização de Banco Duplicada**: O método `initializeDatabase()` executa DDL scripts e insere dados de teste, duplicando o trabalho já feito por `make init-db`

## Correctness Properties

Property 1: Bug Condition - E2E Tests Use Docker Compose Containers

_For any_ test execution where docker-compose containers are running (Oracle on port 1521, RabbitMQ on port 5672, LocalStack on port 4566, SFTP Origin on port 2222, SFTP Destination on port 2223), the fixed E2ETestBase SHALL connect to these existing containers using environment variables (DB_URL, RABBITMQ_HOST, SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION) instead of creating new TestContainers.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11**

Property 2: Preservation - Test Scenarios and Validations

_For any_ test scenario execution (testSftpToS3Transfer, testSftpToSftpTransfer), the fixed E2ETestBase SHALL produce exactly the same validation results as the original code, preserving all file integrity checks (size, SHA-256), database validations (step, status, audit fields), and helper method behaviors.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10**

## Fix Implementation

### Changes Required

Assumindo que nossa análise de causa raiz está correta:

**File**: `commons/src/test/java/com/concil/edi/commons/e2e/E2ETestBase.java`

**Class**: `E2ETestBase`

**Specific Changes**:

1. **Remover Anotação @Testcontainers**: Remover a anotação `@Testcontainers` da classe `FileTransferE2ETest` para desabilitar o gerenciamento automático de containers

2. **Refatorar setupInfrastructure()**: Modificar o método para ler configurações de variáveis de ambiente ao invés de criar containers TestContainers
   - Ler `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` para Oracle
   - Ler `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` para RabbitMQ
   - Ler e parsear `SFTP_CIELO_ORIGIN` (JSON) para obter host, port, user, password do SFTP origin
   - Ler e parsear `S3_DESTINATION` (JSON) para obter endpoint, region, accessKey, secretKey do S3
   - Ler e parsear `SFTP_DESTINATION` (JSON) para obter host, port, user, password do SFTP destination
   - Remover criação de containers TestContainers (oracleContainer, rabbitMQContainer, localStackContainer, sftpOriginContainer, sftpDestinationContainer)
   - Remover criação de Network

3. **Remover initializeDatabase()**: Eliminar o método `initializeDatabase()` e suas chamadas, pois o banco já foi inicializado por `make init-db`

4. **Refatorar initializeS3()**: Modificar para usar configurações do JSON parseado da variável `S3_DESTINATION` ao invés de `localStackContainer.getEndpoint()`

5. **Simplificar teardownInfrastructure()**: Modificar para apenas fechar o S3 client, sem destruir containers

6. **Adicionar Método de Parsing JSON**: Criar método auxiliar `parseServerConfig(String envVarName)` que lê e parseia variáveis de ambiente em formato JSON

7. **Adicionar Validação de Pré-requisitos**: Criar método `validatePrerequisites()` que verifica se as variáveis de ambiente necessárias estão definidas e se os containers estão acessíveis

8. **Atualizar Métodos de Upload/Download SFTP**: Modificar `uploadToSftpOrigin()` e `downloadFromSftpDestination()` para usar as configurações parseadas das variáveis de ambiente ao invés de `sftpOriginContainer.getHost()` e `getMappedPort()`

## Testing Strategy

### Validation Approach

A estratégia de teste segue uma abordagem de duas fases: primeiro, executar os testes no código UNFIXED para confirmar que eles criam containers TestContainers duplicados, depois verificar que o código FIXED conecta aos containers docker-compose existentes e preserva todas as validações.

### Exploratory Bug Condition Checking

**Goal**: Demonstrar o bug ANTES de implementar a correção. Confirmar que o código atual cria containers TestContainers duplicados quando docker-compose está rodando.

**Test Plan**: Executar `make up` para iniciar docker-compose, depois executar `make e2e` e observar nos logs que novos containers TestContainers são criados. Verificar com `docker ps` que existem containers duplicados.

**Test Cases**:
1. **Container Duplication Test**: Executar `make up && make e2e` e verificar que containers duplicados são criados (will fail on unfixed code - demonstra o bug)
2. **Port Mapping Test**: Verificar que TestContainers usa portas dinâmicas ao invés das portas fixas do docker-compose (will fail on unfixed code)
3. **Database Initialization Test**: Verificar que DDL scripts são executados novamente ao invés de usar o banco já inicializado (will fail on unfixed code)
4. **Environment Variable Ignored Test**: Verificar que variáveis de ambiente são ignoradas (will fail on unfixed code)

**Expected Counterexamples**:
- Containers TestContainers são criados mesmo com docker-compose rodando
- Portas dinâmicas são usadas ao invés das portas fixas (1521, 5672, 4566, 2222, 2223)
- DDL scripts são executados novamente, duplicando dados de teste
- Possíveis causas: falta de detecção de ambiente, configuração hardcoded, design original com TestContainers

### Fix Checking

**Goal**: Verificar que para todas as execuções onde docker-compose está rodando, o teste E2E refatorado conecta aos containers existentes usando variáveis de ambiente.

**Pseudocode:**
```
FOR ALL testExecution WHERE isBugCondition(testExecution) DO
  result := E2ETestBase_fixed.setupInfrastructure()
  ASSERT result.usesEnvironmentVariables() == true
  ASSERT result.connectsToDockerComposeContainers() == true
  ASSERT result.createsNewContainers() == false
END FOR
```

### Preservation Checking

**Goal**: Verificar que para todas as execuções de cenários de teste, o código refatorado produz os mesmos resultados de validação que o código original.

**Pseudocode:**
```
FOR ALL testScenario WHERE NOT isBugCondition(testScenario) DO
  ASSERT FileTransferE2ETest_original.testSftpToS3Transfer() = FileTransferE2ETest_fixed.testSftpToS3Transfer()
  ASSERT FileTransferE2ETest_original.testSftpToSftpTransfer() = FileTransferE2ETest_fixed.testSftpToSftpTransfer()
END FOR
```

**Testing Approach**: Property-based testing é recomendado para preservation checking porque:
- Gera muitos casos de teste automaticamente através do domínio de entrada
- Captura edge cases que testes unitários manuais podem perder
- Fornece garantias fortes de que o comportamento permanece inalterado para todas as validações

**Test Plan**: Observar comportamento no código UNFIXED primeiro para os cenários de teste, depois escrever property-based tests capturando esse comportamento.

**Test Cases**:
1. **SFTP-to-S3 Scenario Preservation**: Observar que o cenário SFTP-to-S3 funciona corretamente no código unfixed, depois verificar que continua funcionando após a correção
2. **SFTP-to-SFTP Scenario Preservation**: Observar que o cenário SFTP-to-SFTP funciona corretamente no código unfixed, depois verificar que continua funcionando após a correção
3. **File Integrity Validation Preservation**: Observar que validações de tamanho e SHA-256 funcionam no código unfixed, depois verificar que continuam funcionando após a correção
4. **Database Validation Preservation**: Observar que validações de file_origin (step, status, audit) funcionam no código unfixed, depois verificar que continuam funcionando após a correção

### Unit Tests

- Testar parsing de variáveis de ambiente JSON (SFTP_CIELO_ORIGIN, S3_DESTINATION, SFTP_DESTINATION)
- Testar validação de pré-requisitos (variáveis de ambiente definidas, containers acessíveis)
- Testar configuração de S3 client com endpoint customizado
- Testar configuração de conexões SFTP com host/port customizados
- Testar edge cases (variável de ambiente ausente, JSON inválido, container inacessível)

### Property-Based Tests

- Gerar configurações aleatórias de servidor e verificar que o parsing funciona corretamente
- Gerar diferentes estados de docker-compose (rodando, parado, parcialmente rodando) e verificar comportamento apropriado
- Testar que todas as combinações de variáveis de ambiente produzem conexões válidas

### Integration Tests

- Testar fluxo completo com docker-compose rodando e variáveis de ambiente configuradas
- Testar que os cenários SFTP-to-S3 e SFTP-to-SFTP continuam funcionando após a refatoração
- Testar que logs e debugging são mantidos após a execução dos testes (containers não são destruídos)

