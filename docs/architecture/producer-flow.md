# Fluxo de Inicialização e Chamadas do Producer

## 1. Classe Principal: ProducerApplication

```java
@SpringBootApplication
@EnableScheduling
@EnableRetry
@EntityScan(basePackages = "com.concil.edi.commons.entity")
@EnableJpaRepositories(basePackages = "com.concil.edi.commons.repository")
public class ProducerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }
}
```

### O que acontece na inicialização:

1. **`main()` é executado** → Inicia o Spring Boot
2. **Spring Boot escaneia os pacotes** definidos em `@SpringBootApplication`:
   - `com.concil.edi.producer` (classes do Producer)
   - `com.concil.edi.commons` (classes compartilhadas)

3. **Anotações especiais ativam funcionalidades**:
   - `@EnableScheduling` → Ativa o agendamento de tarefas
   - `@EnableRetry` → Ativa o mecanismo de retry (@Retryable)
   - `@EntityScan` → Diz ao Spring onde estão as entidades JPA
   - `@EnableJpaRepositories` → Diz ao Spring onde estão os repositories

## 2. Injeção de Dependências (Dependency Injection)

O Spring Boot usa **Injeção de Dependências** para criar e conectar as classes automaticamente.

### Como funciona:

```java
@Component  // Diz ao Spring: "Crie uma instância desta classe"
@RequiredArgsConstructor  // Lombok: Cria construtor com todos os campos final
public class FileCollectionScheduler {
    
    // Spring vai INJETAR automaticamente estas dependências
    private final ConfigurationService configurationService;
    private final SftpService sftpService;
    private final FileRegistrationService fileRegistrationService;
    private final MessagePublisherService messagePublisherService;
    private final FileOriginRepository fileOriginRepository;
    private final ServerPathInOutRepository serverPathInOutRepository;
}
```

### Ordem de Instanciação (Spring gerencia automaticamente):

```
1. Spring Boot inicia
   ↓
2. Spring escaneia e encontra todas as classes com anotações:
   - @Component
   - @Service
   - @Repository
   - @Configuration
   ↓
3. Spring cria instâncias na ordem correta (resolvendo dependências):
   
   a) Repositories (não têm dependências):
      - FileOriginRepository
      - ServerPathInOutRepository
      - ServerPathRepository
      - ServerRepository
   
   b) Configurações básicas:
      - VaultConfig (sem dependências)
      - RabbitMQConfig (sem dependências)
   
   c) Services (dependem de repositories e configs):
      - ConfigurationService (depende de repositories)
      - SftpService (depende de VaultConfig)
      - FileRegistrationService (depende de FileOriginRepository)
      - MessagePublisherService (depende de RabbitTemplate e FileOriginRepository)
   
   d) Scheduler (depende de tudo):
      - FileCollectionScheduler (depende de todos os services e repositories)
   
   ↓
4. Spring injeta as dependências via construtor
   ↓
5. Aplicação está pronta e rodando
```

## 3. Fluxo de Execução Principal

### Ponto de Entrada: Método Agendado

```java
@Scheduled(fixedDelay = 120000) // Executa a cada 2 minutos
public void collectFiles() {
    // Este método é chamado AUTOMATICAMENTE pelo Spring
    retryFailedPublications();
    processNewFiles();
}
```

### Fluxo Completo de Chamadas:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. SPRING BOOT INICIA                                       │
│    ProducerApplication.main()                               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. SPRING CRIA E INJETA TODAS AS DEPENDÊNCIAS              │
│    - Repositories                                           │
│    - Services                                               │
│    - Scheduler                                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. SPRING AGENDA O MÉTODO @Scheduled                       │
│    FileCollectionScheduler.collectFiles()                   │
│    (executa automaticamente a cada 2 minutos)              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. MÉTODO collectFiles() É EXECUTADO                       │
│                                                             │
│    Step 1: retryFailedPublications()                       │
│    ├─→ fileOriginRepository.findFailedPublications()       │
│    ├─→ messagePublisherService.publishFileTransferMessage()│
│    └─→ fileOriginRepository.save()                         │
│                                                             │
│    Step 2: processNewFiles()                               │
│    ├─→ configurationService.loadActiveConfigurations()     │
│    │   ├─→ serverPathRepository.findByFlgActiveAndDesPathType()
│    │   └─→ serverPathInOutRepository.findByFlgActive()     │
│    │                                                        │
│    └─→ Para cada configuração:                             │
│        └─→ processConfiguration(config)                    │
│            ├─→ sftpService.listFiles(config)               │
│            │   ├─→ vaultConfig.getCredentials()            │
│            │   └─→ Spring Integration SFTP                 │
│            │                                                │
│            └─→ Para cada arquivo:                          │
│                └─→ processFile(file, config)               │
│                    ├─→ fileRegistrationService.fileExists()│
│                    ├─→ fileRegistrationService.registerFile()
│                    │   └─→ fileOriginRepository.save()     │
│                    │                                        │
│                    └─→ messagePublisherService.publishFileTransferMessage()
│                        └─→ rabbitTemplate.convertAndSend() │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. AGUARDA 2 MINUTOS E REPETE                              │
└─────────────────────────────────────────────────────────────┘
```

## 4. Como as Referências São Feitas

### Não há `new` manual - Spring gerencia tudo!

❌ **ERRADO** (não fazemos isso):
```java
ConfigurationService configService = new ConfigurationService();
SftpService sftpService = new SftpService();
```

✅ **CORRETO** (Spring faz automaticamente):
```java
@Component
@RequiredArgsConstructor  // Lombok cria o construtor
public class FileCollectionScheduler {
    
    // Spring injeta automaticamente via construtor
    private final ConfigurationService configurationService;
    private final SftpService sftpService;
    
    // Equivalente a (mas Lombok gera automaticamente):
    // public FileCollectionScheduler(
    //     ConfigurationService configurationService,
    //     SftpService sftpService
    // ) {
    //     this.configurationService = configurationService;
    //     this.sftpService = sftpService;
    // }
}
```

### Anotações que Dizem ao Spring "Crie uma Instância":

- `@Component` → Componente genérico
- `@Service` → Camada de serviço (lógica de negócio)
- `@Repository` → Camada de acesso a dados
- `@Configuration` → Classe de configuração
- `@Controller` / `@RestController` → Controladores web (não usamos no Producer)

## 5. Exemplo Prático: Como ConfigurationService é Usado

### Definição do Service:
```java
@Service  // Spring cria uma instância
@RequiredArgsConstructor  // Lombok cria construtor
public class ConfigurationService {
    
    // Spring injeta estes repositories
    private final ServerPathRepository serverPathRepository;
    private final ServerPathInOutRepository serverPathInOutRepository;
    
    public List<ServerConfigurationDTO> loadActiveConfigurations() {
        // Usa os repositories injetados
        List<ServerPath> originPaths = serverPathRepository.findByFlgActiveAndDesPathType(1, PathType.ORIGIN);
        List<ServerPathInOut> activeMappings = serverPathInOutRepository.findByFlgActive(1);
        // ... lógica de negócio
    }
}
```

### Uso no Scheduler:
```java
@Component
@RequiredArgsConstructor
public class FileCollectionScheduler {
    
    // Spring injeta o ConfigurationService (que já tem seus repositories injetados)
    private final ConfigurationService configurationService;
    
    private void processNewFiles() {
        // Simplesmente chama o método - a instância já existe!
        List<ServerConfigurationDTO> configurations = configurationService.loadActiveConfigurations();
    }
}
```

## 6. Resumo: Princípios Chave

1. **Inversão de Controle (IoC)**: 
   - Você não cria objetos com `new`
   - Spring cria e gerencia tudo

2. **Injeção de Dependências (DI)**:
   - Você declara o que precisa (via construtor)
   - Spring injeta automaticamente

3. **Anotações são Instruções**:
   - `@Component`, `@Service`, `@Repository` → "Crie uma instância"
   - `@Scheduled` → "Execute este método periodicamente"
   - `@Retryable` → "Tente novamente em caso de erro"

4. **Ordem de Instanciação**:
   - Spring resolve automaticamente a ordem correta
   - Classes sem dependências primeiro
   - Classes com dependências depois

5. **Singleton por Padrão**:
   - Spring cria UMA instância de cada classe
   - Essa instância é reutilizada em toda a aplicação

## 7. Diagrama Visual Simplificado

```
ProducerApplication (main)
         ↓
    Spring Boot
         ↓
    ┌────────────────────────────────────┐
    │   Spring Container (IoC)           │
    │                                    │
    │  ┌──────────────────────────────┐ │
    │  │ FileCollectionScheduler      │ │
    │  │  (recebe todas as deps)      │ │
    │  └──────────────────────────────┘ │
    │         ↓         ↓         ↓      │
    │  ┌──────────┐ ┌──────────┐ ┌────┐│
    │  │Services  │ │Services  │ │Repos││
    │  └──────────┘ └──────────┘ └────┘│
    │         ↓                          │
    │  ┌──────────────────────────────┐ │
    │  │ Repositories                 │ │
    │  └──────────────────────────────┘ │
    └────────────────────────────────────┘
```

Tudo é gerenciado automaticamente pelo Spring Framework!
