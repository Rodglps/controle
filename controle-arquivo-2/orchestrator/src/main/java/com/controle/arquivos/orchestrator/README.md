# Orchestrator Application

## OrquestradorApplication

Classe principal do Pod Orquestrador do sistema de Controle de Arquivos.

### Responsabilidades

- Inicializar o contexto Spring Boot
- Habilitar scheduling para coleta periódica de arquivos via `@EnableScheduling`
- Configurar component scan para todos os serviços (orchestrator e common)

### Anotações

- `@SpringBootApplication`: Marca como aplicação Spring Boot
- `@EnableScheduling`: Habilita suporte a tarefas agendadas (scheduler)
- `scanBasePackages`: Configura scan de componentes nos pacotes:
  - `com.controle.arquivos.orchestrator`: Componentes do orquestrador
  - `com.controle.arquivos.common`: Componentes compartilhados (repositórios, serviços, clientes)

### Requisitos Atendidos

- **Requisito 1.1**: Carregar configurações de servidores SFTP (via component scan)

### Execução

```bash
# Via Maven
mvn spring-boot:run -pl orchestrator

# Via JAR
java -jar orchestrator/target/orchestrator-1.0.0-SNAPSHOT.jar
```

### Perfis Disponíveis

- `local`: Ambiente de desenvolvimento local
- `dev`: Ambiente de desenvolvimento
- `staging`: Ambiente de homologação
- `prod`: Ambiente de produção

Exemplo:
```bash
java -jar orchestrator.jar --spring.profiles.active=prod
```
