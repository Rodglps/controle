# SFTP Test Files

Este diretório contém arquivos de teste para o servidor SFTP local.

## Estrutura

Coloque arquivos EDI de teste neste diretório para simular arquivos de adquirentes.

## Credenciais SFTP

- **Host**: localhost
- **Port**: 2222
- **Username**: sftpuser
- **Password**: sftppass
- **Upload Directory**: /upload

## Exemplo de Conexão

```bash
sftp -P 2222 sftpuser@localhost
# Password: sftppass
```

## Arquivos de Teste Sugeridos

Crie arquivos de teste com nomes que correspondam às regras de identificação:
- `CIELO_20240115_001.txt`
- `REDE_20240115_001.csv`
- `GETNET_20240115_001.xml`
