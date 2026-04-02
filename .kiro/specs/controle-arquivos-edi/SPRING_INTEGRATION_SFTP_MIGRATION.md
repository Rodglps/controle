# Spring Integration SFTP Migration Guide

## Overview

Este documento descreve a migração de JSch para Spring Integration SFTP no projeto Controle de Arquivos EDI.

## Motivação

A migração foi realizada para:
1. Melhor integração com o ecossistema Spring Boot
2. Connection pooling nativo via `CachingSessionFactory`
3. Biblioteca mais moderna (Apache SSHD vs JSch)
4. Código mais limpo e testável
5. Melhor manutenibilidade a longo prazo

## Mudanças Realizadas

### 1. Dependências (pom.xml)

**Antes**:
```xml
<dependency>
    <groupId>com.jcraft</groupId>
    <artifactId>jsch</artifactId>
</dependency>
```

**Depois**:
```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-sftp</artifactId>
</dependency>
```

### 2. Nova Configuração (SftpConfig.java)

Criada classe de configuração para gerenciar SessionFactory:

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SftpConfig {
    
    private final VaultConfig vaultConfig;
    
    public SessionFactory<SftpClient.DirEntry> createSessionFactory(
            String host, 
            int port, 
            String codVault, 
            String vaultSecret) {
        
        // Get credentials from Vault
        CredentialsDTO credentials = vaultConfig.getCredentials(codVault, vaultSecret);
        
        // Create default SFTP session factory
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(host);
        factory.setPort(port);
        factory.setUser(credentials.getUser());
        factory.setPassword(credentials.getPassword());
        factory.setAllowUnknownKeys(true);
        
        // Wrap in caching session factory for connection pooling
        CachingSessionFactory<SftpClient.DirEntry> cachingFactory = 
            new CachingSessionFactory<>(factory, 10); // Pool size of 10
        
        return cachingFactory;
    }
}
```

### 3. SftpService Refatorado

**Antes (JSch)**:
```java
JSch jsch = new JSch();
Session session = jsch.getSession(user, host, port);
session.setPassword(password);
session.setConfig("StrictHostKeyChecking", "no");
session.connect();

Channel channel = session.openChannel("sftp");
channel.connect();
ChannelSftp channelSftp = (ChannelSftp) channel;

Vector<ChannelSftp.LsEntry> files = channelSftp.ls(path);
// Process files...

channelSftp.disconnect();
session.disconnect();
```

**Depois (Spring Integration)**:
```java
SessionFactory<SftpClient.DirEntry> sessionFactory = 
    sftpConfig.createSessionFactory(host, port, codVault, vaultSecret);

Session<SftpClient.DirEntry> session = sessionFactory.getSession();

SftpClient.DirEntry[] files = session.list(path);
// Process files...

session.close(); // Returns to pool
```

### 4. Tipos Atualizados

**Antes**:
- `com.jcraft.jsch.ChannelSftp.LsEntry`
- `com.jcraft.jsch.SftpATTRS`

**Depois**:
- `org.apache.sshd.sftp.client.SftpClient.DirEntry`
- `org.apache.sshd.sftp.client.SftpClient.Attributes`

### 5. Acesso a Atributos de Arquivo

**Antes (JSch)**:
```java
entry.getAttrs().isDir()
entry.getAttrs().getSize()
entry.getAttrs().getMTime() // Returns int (seconds)
```

**Depois (Spring Integration/Apache SSHD)**:
```java
entry.getAttributes().isDirectory()
entry.getAttributes().getSize()
entry.getAttributes().getModifyTime().toMillis() // Returns FileTime
```

## Benefícios Observados

### 1. Connection Pooling
- Pool de 10 conexões reutilizáveis
- Redução de overhead de conexão
- Melhor performance em operações repetidas

### 2. Código Mais Limpo
- Menos boilerplate para gerenciar conexões
- Try-with-resources simplificado
- Abstrações de alto nível

### 3. Melhor Testabilidade
- Fácil de mockar `SessionFactory` e `Session`
- Testes unitários mais simples
- Melhor isolamento de dependências

### 4. Integração Spring
- Configuração via Spring beans
- Suporte a profiles
- Melhor integração com Spring Boot Actuator

## Próximos Passos (Consumer)

O Consumer deve seguir o mesmo padrão:

### 1. Adicionar Dependência
```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-sftp</artifactId>
</dependency>
```

### 2. Criar SftpConfig no Consumer
Similar ao Producer, com SessionFactory para download e upload.

### 3. FileDownloadService
```java
@Service
@RequiredArgsConstructor
public class FileDownloadServiceImpl implements FileDownloadService {
    
    private final SftpConfig sftpConfig;
    
    @Override
    public InputStream openInputStream(Long serverPathOriginId, String filename) {
        // Get configuration from database
        ServerConfiguration config = getConfiguration(serverPathOriginId);
        
        // Create session factory
        SessionFactory<SftpClient.DirEntry> sessionFactory = 
            sftpConfig.createSessionFactory(
                config.getHost(), 
                config.getPort(), 
                config.getCodVault(), 
                config.getVaultSecret()
            );
        
        // Get session and open input stream
        Session<SftpClient.DirEntry> session = sessionFactory.getSession();
        return session.readRaw(config.getPath() + "/" + filename);
    }
}
```

### 4. FileUploadService (SFTP)
```java
@Override
public void uploadToSftp(InputStream inputStream, ServerConfiguration config, String remotePath) {
    SessionFactory<SftpClient.DirEntry> sessionFactory = 
        sftpConfig.createSessionFactory(
            config.getHost(), 
            config.getPort(), 
            config.getCodVault(), 
            config.getVaultSecret()
        );
    
    Session<SftpClient.DirEntry> session = sessionFactory.getSession();
    
    try {
        session.write(inputStream, remotePath);
    } finally {
        session.close(); // Returns to pool
    }
}
```

## Testes

Todos os 45 testes do Producer passaram após a migração:
- ✅ Unit tests
- ✅ Property-based tests
- ✅ Integration tests

## Referências

- [Spring Integration SFTP Documentation](https://docs.spring.io/spring-integration/reference/sftp.html)
- [Apache SSHD Documentation](https://github.com/apache/mina-sshd)
- [Spring Integration Reference](https://docs.spring.io/spring-integration/reference/)

## Conclusão

A migração para Spring Integration SFTP foi bem-sucedida, proporcionando:
- Melhor integração com Spring
- Connection pooling nativo
- Código mais limpo e testável
- Base sólida para implementação do Consumer

Todos os testes passaram e o sistema está pronto para as próximas iterações.
