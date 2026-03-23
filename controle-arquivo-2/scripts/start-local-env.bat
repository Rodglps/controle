@echo off
REM Script para iniciar o ambiente local de desenvolvimento (Windows)

echo ==========================================
echo Controle de Arquivos - Ambiente Local
echo ==========================================
echo.

REM Verificar se Docker está rodando
docker info >nul 2>&1
if errorlevel 1 (
    echo X Erro: Docker nao esta rodando
    echo Por favor, inicie o Docker e tente novamente
    exit /b 1
)

echo [OK] Docker esta rodando
echo.

REM Verificar se arquivo .env existe
if not exist .env (
    echo [!] Arquivo .env nao encontrado
    echo Criando .env a partir de .env.example...
    copy .env.example .env
    echo [OK] Arquivo .env criado
    echo.
)

REM Criar diretórios necessários
echo [*] Criando diretorios necessarios...
if not exist scripts\ddl mkdir scripts\ddl
if not exist scripts\localstack mkdir scripts\localstack
if not exist scripts\sftp mkdir scripts\sftp
echo [OK] Diretorios criados
echo.

REM Iniciar serviços
echo [*] Iniciando servicos Docker...
docker-compose up -d

echo.
echo [*] Aguardando servicos ficarem prontos...
echo     (Isso pode levar alguns minutos na primeira execucao)
echo.

REM Aguardar 30 segundos
timeout /t 30 /nobreak >nul

echo.
echo ==========================================
echo Ambiente Local Iniciado!
echo ==========================================
echo.
echo Servicos disponiveis:
echo.
echo   Oracle Database
echo      URL: jdbc:oracle:thin:@localhost:1521:XE
echo      User: system / Password: Oracle123
echo      Enterprise Manager: https://localhost:5500/em
echo.
echo   RabbitMQ
echo      AMQP: localhost:5672
echo      Management UI: http://localhost:15672
echo      User: admin / Password: admin123
echo.
echo   LocalStack (S3)
echo      Endpoint: http://localhost:4566
echo      Region: us-east-1
echo      Access Key: test / Secret Key: test
echo.
echo   SFTP Server
echo      Host: localhost:2222
echo      User: sftpuser / Password: sftppass
echo.
echo ==========================================
echo.
echo Comandos uteis:
echo    Ver logs:        docker-compose logs -f
echo    Parar servicos:  docker-compose down
echo    Status:          docker-compose ps
echo.
echo Documentacao completa: DOCKER_SETUP.md
echo.
