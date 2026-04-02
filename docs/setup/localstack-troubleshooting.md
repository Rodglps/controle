# LocalStack Troubleshooting Guide

## Problema Resolvido: Container não inicia (Device or resource busy)

### Sintoma
```
ERROR: 'rm -rf "/tmp/localstack"': exit code 1
OSError: [Errno 16] Device or resource busy: '/tmp/localstack'
```

### Causa
O LocalStack estava tentando limpar o diretório `/tmp/localstack` que estava montado como volume Docker, causando conflito.

### Solução Aplicada

**Antes (configuração problemática)**:
```yaml
localstack:
  environment:
    DATA_DIR: /tmp/localstack/data
  volumes:
    - localstack-data:/tmp/localstack
    - ./scripts/localstack:/docker-entrypoint-initaws.d
```

**Depois (configuração corrigida)**:
```yaml
localstack:
  environment:
    PERSISTENCE: 1
    LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT: 1
  volumes:
    - localstack-data:/var/lib/localstack
    - ./scripts/localstack:/etc/localstack/init/ready.d
```

### Mudanças Realizadas

1. **Volume path**: `/tmp/localstack` → `/var/lib/localstack`
   - Evita conflito com diretório temporário do LocalStack
   - Usa o caminho padrão de persistência

2. **Init scripts path**: `/docker-entrypoint-initaws.d` → `/etc/localstack/init/ready.d`
   - Usa o novo caminho de inicialização do LocalStack
   - Scripts executam quando o LocalStack está "ready"

3. **Variável PERSISTENCE**: Adicionada `PERSISTENCE: 1`
   - Habilita persistência de dados entre restarts
   - Dados do S3 são mantidos no volume

4. **Variável LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT**: Adicionada
   - Permite executar sem autenticação até abril de 2026
   - Resolve erro de "No credentials found"

## Problema: LocalStack requer autenticação (2026+)

### Sintoma
```
No credentials were found in the environment. 
Please make sure to either set the LOCALSTACK_AUTH_TOKEN variable
LocalStack requires an account to run.
```

### Causa
A partir de 2026, o LocalStack passou a exigir autenticação ou acknowledgment explícito.

### Solução

**Opção 1: Acknowledgment (Temporário até abril 2026)**
```yaml
environment:
  LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT: 1
```

**Opção 2: Autenticação (Permanente)**
```yaml
environment:
  LOCALSTACK_AUTH_TOKEN: your-auth-token-here
```

Para obter um token:
1. Crie uma conta em https://www.localstack.cloud/pricing
2. Acesse https://app.localstack.cloud/settings/auth-tokens
3. Copie o token e adicione ao docker-compose.yml

**Opção 3: Usar versão antiga**
```yaml
image: localstack/localstack:3.0.0  # Versão sem exigência de auth
```

## Verificação de Saúde

### 1. Verificar se o container está rodando
```bash
docker ps | grep localstack
```

Deve mostrar: `Up X seconds (healthy)`

### 2. Verificar logs
```bash
docker logs edi-localstack --tail 50
```

Deve mostrar:
```
LocalStack S3 initialization complete!
Ready.
```

### 3. Testar endpoint
```bash
curl http://localhost:4566/_localstack/health
```

Deve retornar:
```json
{
  "services": {
    "s3": "running"
  }
}
```

### 4. Verificar bucket
```bash
docker exec edi-localstack awslocal s3 ls
```

Deve mostrar:
```
2026-03-29 19:46:47 edi-files
```

### 5. Listar objetos no bucket
```bash
docker exec edi-localstack awslocal s3 ls s3://edi-files/
```

## Comandos Úteis

### Reiniciar LocalStack
```bash
docker-compose restart localstack
```

### Parar e remover (limpa dados)
```bash
docker-compose down localstack
docker volume rm kiro-teste_localstack-data
docker-compose up -d localstack
```

### Executar script de inicialização manualmente
```bash
docker exec edi-localstack sh -c "cd /etc/localstack/init/ready.d && ./init-s3.sh"
```

### Acessar shell do container
```bash
docker exec -it edi-localstack /bin/bash
```

### Verificar arquivos no volume
```bash
docker exec edi-localstack ls -la /var/lib/localstack
```

## Testes de Integração

### Upload de arquivo via AWS CLI
```bash
# Criar arquivo de teste
echo "test content" > test.txt

# Upload para S3
aws --endpoint-url=http://localhost:4566 \
    --region us-east-1 \
    s3 cp test.txt s3://edi-files/test.txt

# Verificar
aws --endpoint-url=http://localhost:4566 \
    --region us-east-1 \
    s3 ls s3://edi-files/
```

### Upload via aplicação Java
```java
S3Client s3Client = S3Client.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();

s3Client.putObject(PutObjectRequest.builder()
    .bucket("edi-files")
    .key("test.txt")
    .build(), RequestBody.fromString("test content"));
```

## Problemas Conhecidos

### 1. Bucket não criado automaticamente

**Sintoma**: Bucket `edi-files` não existe

**Solução**:
```bash
# Verificar se script de init existe
docker exec edi-localstack ls -la /etc/localstack/init/ready.d/

# Executar manualmente
docker exec edi-localstack sh -c "cd /etc/localstack/init/ready.d && ./init-s3.sh"
```

### 2. Permissão negada no script de init

**Sintoma**: `Permission denied` ao executar init-s3.sh

**Solução**:
```bash
# Dar permissão de execução
chmod +x scripts/localstack/init-s3.sh

# Reiniciar LocalStack
docker-compose restart localstack
```

### 3. Dados não persistem entre restarts

**Sintoma**: Bucket desaparece após restart

**Verificar**:
```bash
# Verificar se PERSISTENCE está habilitado
docker exec edi-localstack env | grep PERSISTENCE

# Verificar volume
docker volume inspect kiro-teste_localstack-data
```

**Solução**: Garantir que `PERSISTENCE: 1` está no docker-compose.yml

### 4. Porta 4566 já em uso

**Sintoma**: `Error starting userland proxy: listen tcp4 0.0.0.0:4566: bind: address already in use`

**Solução**:
```bash
# Encontrar processo usando a porta
lsof -i :4566

# Matar processo
kill -9 <PID>

# Ou mudar porta no docker-compose.yml
ports:
  - "4567:4566"  # Usar porta diferente
```

## Referências

- [LocalStack Documentation](https://docs.localstack.cloud/)
- [LocalStack Docker Hub](https://hub.docker.com/r/localstack/localstack)
- [LocalStack Init Hooks](https://docs.localstack.cloud/references/init-hooks/)
- [LocalStack Persistence](https://docs.localstack.cloud/references/persistence-mechanism/)
- [LocalStack Authentication](https://docs.localstack.cloud/getting-started/auth-token/)

## Histórico de Mudanças

- **2026-03-29**: Corrigido problema de "Device or resource busy"
- **2026-03-29**: Adicionado LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT
- **2026-03-29**: Mudado volume path para /var/lib/localstack
- **2026-03-29**: Mudado init scripts path para /etc/localstack/init/ready.d
