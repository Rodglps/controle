# Clients

Este diretório contém clientes para integração com sistemas externos.

## VaultClient

Cliente para obter credenciais do HashiCorp Vault.

**Características:**
- Cache de credenciais com TTL configurável (padrão: 5 minutos)
- Renovação automática de tokens
- Nunca expõe credenciais em logs
- Suporte a Vault KV v1 e v2

**Uso:**
```java
@Autowired
private VaultClient vaultClient;

VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(
    "sftp-server-1", 
    "sftp/production/server1"
);
```

## SFTPClient

Cliente para conexão, listagem e download de arquivos via SFTP.

**Características:**
- Conexão SFTP usando JSch
- Listagem de arquivos com metadados (nome, tamanho, timestamp)
- Download streaming via InputStream
- Tratamento de erros de conexão e timeout
- Configuração via SftpProperties

**Uso:**
```java
@Autowired
private SFTPClient sftpClient;

@Autowired
private VaultClient vaultClient;

// Obter credenciais do Vault
VaultClient.Credenciais credenciais = vaultClient.obterCredenciais(
    "sftp-server-1", 
    "sftp/production/server1"
);

// Conectar ao servidor SFTP
sftpClient.conectar("sftp.example.com", 22, credenciais);

try {
    // Listar arquivos
    List<SFTPClient.ArquivoMetadata> arquivos = sftpClient.listarArquivos("/incoming");
    
    for (SFTPClient.ArquivoMetadata arquivo : arquivos) {
        System.out.println("Arquivo: " + arquivo.getNome());
        System.out.println("Tamanho: " + arquivo.getTamanho());
        System.out.println("Timestamp: " + arquivo.getTimestamp());
        
        // Obter InputStream para download streaming
        try (InputStream inputStream = sftpClient.obterInputStream("/incoming/" + arquivo.getNome())) {
            // Processar arquivo via streaming
            // ...
        }
    }
} finally {
    // Sempre desconectar após uso
    sftpClient.desconectar();
}
```

**Configuração (application.yml):**
```yaml
sftp:
  timeout: 30000                      # Timeout de conexão (ms)
  session-timeout: 120000             # Timeout de sessão (ms)
  channel-timeout: 60000              # Timeout de canal (ms)
  strict-host-key-checking: true      # Verificar chave do host
  known-hosts-file: /app/config/known_hosts  # Arquivo known_hosts
```

**Tratamento de Erros:**
- `SFTPException`: Lançada em caso de erro de conexão, listagem ou download
- Logs estruturados com contexto completo
- Liberação automática de recursos em caso de falha

**Requisitos Validados:**
- Requisito 2.1: Conectar usando credenciais do Vault
- Requisito 2.2: Listar arquivos com metadados
- Requisito 2.4: Coletar nome, tamanho e timestamp
- Requisito 2.5: Tratamento de erros de conexão
- Requisito 7.1: Obter InputStream para download streaming
- Requisito 7.2: Processar em chunks sem carregar em memória
