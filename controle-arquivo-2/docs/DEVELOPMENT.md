# Guia de Desenvolvimento - Controle de Arquivos

Informações para desenvolvedores sobre estrutura, padrões e extensibilidade do sistema.

## Estrutura do Projeto

### Módulos Maven

- **common**: Código compartilhado (entidades, repositórios, serviços, clientes)
- **orchestrator**: Pod de coleta e orquestração
- **processor**: Pod de processamento e upload
- **integration-tests**: Testes end-to-end com Testcontainers

### Pacotes Principais (common)

```
com.controle.arquivos.common/
├── client/              # SFTPClient, VaultClient
├── config/              # Properties classes
├── domain/
│   ├── entity/          # JPA entities
│   └── enums/           # Enums de domínio
├── logging/             # Logging estruturado, MDC
├── repository/          # Spring Data JPA repositories
└── service/             # Serviços compartilhados
    ├── ClienteIdentificationService
    ├── LayoutIdentificationService
    ├── RastreabilidadeService
    ├── StreamingTransferService
    └── JobConcurrencyService
```

### Pacotes Principais (orchestrator)

```
com.controle.arquivos.orchestrator/
├── config/              # RabbitMQ, Scheduler config
├── messaging/           # RabbitMQPublisher
├── scheduler/           # CollectionScheduler
└── service/             # OrquestradorService
```

### Pacotes Principais (processor)

```
com.controle.arquivos.processor/
├── config/              # RabbitMQ, S3 config
├── health/              # Custom health indicators
├── messaging/           # RabbitMQConsumer
└── service/             # ProcessadorService
```

## Padrões de Código

### Convenções Java

- **Java 17** features (records, pattern matching, text blocks)
- **Lombok** para reduzir boilerplate (`@Data`, `@Builder`, `@Slf4j`)
- **Spring Boot** best practices
- **Naming**: Classes em PascalCase, métodos em camelCase, constantes em UPPER_SNAKE_CASE

### Estrutura de Service

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class ExemploService {
    
    private final DependencyRepository repository;
    private final ExternalClient client;
    
    @Transactional
    public Result processarAlgo(Input input) {
        log.info("Iniciando processamento", input);
        
        try {
            // Lógica de negócio
            Result result = doSomething(input);
            
            log.info("Processamento concluído", result);
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao processar", e);
            throw new ProcessingException("Falha no processamento", e);
        }
    }
}
```

### Logging Estruturado

Use `LoggingUtils` para logs estruturados:

```java
LoggingUtils.logInfo(
    "Arquivo processado com sucesso",
    Map.of(
        "fileName", fileName,
        "fileSize", fileSize,
        "duration", duration
    )
);

LoggingUtils.logError(
    "Falha ao processar arquivo",
    exception,
    Map.of(
        "fileName", fileName,
        "step", "DOWNLOAD"
    )
);
```

### Exception Handling

Hierarquia de exceções:

```java
// Base exception
public class ControleArquivosException extends RuntimeException {
    private final boolean recoverable;
}

// Exceções específicas
public class ClienteNaoIdentificadoException extends ControleArquivosException {
    public ClienteNaoIdentificadoException(String fileName) {
        super("Cliente não identificado: " + fileName, false);
    }
}

public class SftpConnectionException extends ControleArquivosException {
    public SftpConnectionException(String message, Throwable cause) {
        super(message, true, cause);  // Recuperável
    }
}
```

### Transações

Use `@Transactional` apropriadamente:

```java
@Transactional(readOnly = true)
public List<Entity> buscar() { }

@Transactional
public void salvar(Entity entity) { }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void salvarIndependente(Entity entity) { }
```

## Adicionando Novos Critérios

### 1. Adicionar Enum

```java
// TipoCriterio.java
public enum TipoCriterio {
    COMECA_COM,
    TERMINA_COM,
    CONTEM,
    IGUAL,
    REGEX,           // NOVO
    TAMANHO_MINIMO   // NOVO
}
```

### 2. Implementar Lógica

```java
// ClienteIdentificationService.java
private boolean aplicarCriterio(TipoCriterio criterio, String valor, String substring) {
    return switch (criterio) {
        case COMECA_COM -> substring.startsWith(valor);
        case TERMINA_COM -> substring.endsWith(valor);
        case CONTEM -> substring.contains(valor);
        case IGUAL -> substring.equals(valor);
        case REGEX -> substring.matches(valor);  // NOVO
        case TAMANHO_MINIMO -> substring.length() >= Integer.parseInt(valor);  // NOVO
    };
}
```

### 3. Adicionar Testes

```java
@Test
void deveIdentificarClienteComCriterioRegex() {
    // Arrange
    RegraIdentificacaoCliente regra = RegraIdentificacaoCliente.builder()
        .criterio(TipoCriterio.REGEX)
        .posicaoInicio(0)
        .posicaoFim(10)
        .valor("CIELO_\\d{8}")
        .build();
    
    // Act
    boolean match = service.aplicarRegra(regra, "CIELO_20240115.txt");
    
    // Assert
    assertThat(match).isTrue();
}
```

### 4. Property-Based Test

```java
@Property
void criterioRegexDeveValidarPadrao(
    @ForAll @Pattern("[A-Z]{5}_\\d{8}") String fileName) {
    
    RegraIdentificacaoCliente regra = RegraIdentificacaoCliente.builder()
        .criterio(TipoCriterio.REGEX)
        .valor("[A-Z]{5}_\\d{8}")
        .build();
    
    boolean match = service.aplicarRegra(regra, fileName);
    assertThat(match).isTrue();
}
```

### 5. Documentar

Atualizar `design.md` com nova propriedade de corretude.

## Adicionando Novos Destinos

### 1. Adicionar Enum

```java
// TipoServidor.java
public enum TipoServidor {
    SFTP,
    S3,
    NFS,
    BLOB_STORAGE,
    OBJECT_STORAGE,
    AZURE_BLOB,    // NOVO
    GCS            // NOVO
}
```

### 2. Criar Client

```java
@Component
@Slf4j
public class AzureBlobClient {
    
    private final BlobServiceClient blobServiceClient;
    
    public void upload(InputStream inputStream, String containerName, String blobName, long size) {
        BlobClient blobClient = blobServiceClient
            .getBlobContainerClient(containerName)
            .getBlobClient(blobName);
        
        blobClient.upload(inputStream, size, true);
        log.info("Upload concluído para Azure Blob", Map.of("blob", blobName));
    }
}
```

### 3. Estender StreamingTransferService

```java
@Service
public class StreamingTransferService {
    
    private final S3Client s3Client;
    private final SFTPClient sftpClient;
    private final AzureBlobClient azureBlobClient;  // NOVO
    
    public void transferir(InputStream source, Server destino, String path, long size) {
        switch (destino.getTipo()) {
            case S3 -> transferirParaS3(source, destino, path, size);
            case SFTP -> transferirParaSFTP(source, destino, path, size);
            case AZURE_BLOB -> transferirParaAzureBlob(source, destino, path, size);  // NOVO
            default -> throw new UnsupportedOperationException("Tipo não suportado: " + destino.getTipo());
        }
    }
    
    private void transferirParaAzureBlob(InputStream source, Server destino, String path, long size) {
        String containerName = destino.getContainerName();
        azureBlobClient.upload(source, containerName, path, size);
    }
}
```

### 4. Configuração

```yaml
# application.yml
azure:
  storage:
    connection-string: ${AZURE_STORAGE_CONNECTION_STRING}
    container-name: ${AZURE_CONTAINER_NAME:controle-arquivos}
```

### 5. Testes

```java
@Test
void deveTransferirParaAzureBlob() {
    // Arrange
    InputStream source = new ByteArrayInputStream("test".getBytes());
    Server destino = Server.builder()
        .tipo(TipoServidor.AZURE_BLOB)
        .containerName("test-container")
        .build();
    
    // Act
    service.transferir(source, destino, "test.txt", 4L);
    
    // Assert
    verify(azureBlobClient).upload(any(), eq("test-container"), eq("test.txt"), eq(4L));
}
```

## Testes

### Estrutura de Testes

```
src/test/java/
├── unit/                    # Testes unitários
│   └── service/
├── property/                # Property-based tests (jqwik)
│   └── *PropertyTest.java
└── integration/             # Testes de integração (Testcontainers)
    └── *IntegrationTest.java
```

### Testes Unitários

```java
@ExtendWith(MockitoExtension.class)
class ClienteIdentificationServiceTest {
    
    @Mock
    private CustomerIdentificationRepository repository;
    
    @InjectMocks
    private ClienteIdentificationService service;
    
    @Test
    void deveIdentificarClienteComRegraSimples() {
        // Arrange
        CustomerIdentification cliente = createCliente();
        when(repository.findByAcquirer(anyLong())).thenReturn(List.of(cliente));
        
        // Act
        Optional<CustomerIdentification> result = service.identificar("CIELO_20240115.txt", 1L);
        
        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(cliente.getId());
    }
}
```

### Property-Based Tests

```java
class ClienteIdentificationPropertyTest {
    
    /**
     * Feature: controle-de-arquivos, Property 14
     * Para qualquer arquivo e regras, se TODAS as regras retornam true,
     * então o cliente deve ser identificado.
     */
    @Property(tries = 100)
    void todasRegrasDevemRetornarTrueParaIdentificar(
        @ForAll("nomeArquivo") String fileName,
        @ForAll("regrasValidas") List<CustomerIdentificationRule> regras) {
        
        // Todas as regras retornam true
        boolean todasMatch = regras.stream()
            .allMatch(regra -> service.aplicarRegra(regra, fileName));
        
        Optional<CustomerIdentification> result = service.identificar(fileName, 1L);
        
        if (todasMatch) {
            assertThat(result).isPresent();
        }
    }
    
    @Provide
    Arbitrary<String> nomeArquivo() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .ofMinLength(10)
            .ofMaxLength(50)
            .map(s -> s + ".txt");
    }
}
```

### Testes de Integração

```java
@SpringBootTest
@Testcontainers
class EndToEndFlowIntegrationTest extends BaseIntegrationTest {
    
    @Test
    void deveProcessarFluxoCompleto() {
        // Arrange: Upload arquivo para SFTP
        uploadFileToSftp("CIELO_20240115.txt", "HEADER|CIELO|20240115\n");
        
        // Act: Executar orquestrador
        orchestradorService.executarCicloColeta();
        
        // Assert: Verificar arquivo no S3
        await().atMost(60, SECONDS).untilAsserted(() -> {
            boolean exists = s3Client.doesObjectExist("bucket", "CIELO_20240115.txt");
            assertThat(exists).isTrue();
        });
    }
}
```

### Executar Testes

```bash
# Todos os testes
mvn test

# Apenas unitários
mvn test -Dtest="*Test"

# Apenas property tests
mvn test -Dtest="*PropertyTest"

# Apenas integração
mvn verify -Dit.test="*IntegrationTest"

# Com cobertura
mvn clean verify
open target/site/jacoco/index.html
```

## Debugging

### Local com IntelliJ

1. Configurar Run Configuration:
   - Main class: `OrquestradorApplication` ou `ProcessadorApplication`
   - VM options: `-Dspring.profiles.active=local`
   - Environment variables: Carregar de `.env`

2. Breakpoints:
   - Colocar breakpoints em pontos de interesse
   - Debug → Run

### Remote Debugging (Kubernetes)

1. Adicionar JVM options no deployment:
```yaml
env:
- name: JAVA_TOOL_OPTIONS
  value: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

2. Port forward:
```bash
kubectl port-forward <pod-name> 5005:5005
```

3. IntelliJ → Run → Edit Configurations → Remote JVM Debug:
   - Host: localhost
   - Port: 5005

### Logs de Debug

```yaml
logging:
  level:
    com.controle.arquivos: DEBUG
    org.hibernate.SQL: DEBUG
```

### Actuator Endpoints

```bash
# Thread dump
curl http://localhost:8080/actuator/threaddump

# Heap dump
curl http://localhost:8080/actuator/heapdump -o heapdump.hprof

# Métricas
curl http://localhost:8080/actuator/metrics

# Environment
curl http://localhost:8080/actuator/env
```

## CI/CD

### Pipeline GitHub Actions

```yaml
name: CI/CD

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run tests
        run: mvn verify
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Docker image
        run: |
          docker build -t controle-arquivos-processor:${{ github.sha }} processor/
          docker build -t controle-arquivos-orchestrator:${{ github.sha }} orchestrator/
      - name: Push to registry
        run: |
          docker push controle-arquivos-processor:${{ github.sha }}
          docker push controle-arquivos-orchestrator:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/processor processor=controle-arquivos-processor:${{ github.sha }}
          kubectl set image deployment/orchestrator orchestrator=controle-arquivos-orchestrator:${{ github.sha }}
```

### Quality Gates

- **Cobertura**: Mínimo 80% linha, 75% branch
- **SonarQube**: Sem code smells críticos
- **Testes**: Todos passando
- **Build**: Sem warnings

## Boas Práticas

### Segurança

- Nunca commitar credenciais
- Usar Vault para secrets
- Validar inputs
- Sanitizar logs (sem credenciais)
- Usar HTTPS para comunicação externa

### Performance

- Usar streaming para arquivos grandes
- Configurar pool de conexões apropriadamente
- Usar índices em queries frequentes
- Cache quando apropriado (Vault credentials)
- Monitorar métricas

### Manutenibilidade

- Código limpo e legível
- Comentários apenas quando necessário
- Testes abrangentes
- Documentação atualizada
- Logs estruturados

### Escalabilidade

- Stateless services
- Idempotência em operações
- Retry com backoff exponencial
- Circuit breakers para dependências
- Horizontal scaling via HPA

### Observabilidade

- Logs estruturados em JSON
- Correlation ID em todas as operações
- Métricas customizadas
- Health checks robustos
- Distributed tracing (futuro)

## Recursos Adicionais

- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [jqwik User Guide](https://jqwik.net/docs/current/user-guide.html)
- [Testcontainers](https://www.testcontainers.org/)
- [Effective Java](https://www.oreilly.com/library/view/effective-java/9780134686097/)
- [Clean Code](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)



