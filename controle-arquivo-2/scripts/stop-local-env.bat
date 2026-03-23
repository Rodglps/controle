@echo off
REM Script para parar o ambiente local de desenvolvimento (Windows)

echo ==========================================
echo Controle de Arquivos - Parar Ambiente
echo ==========================================
echo.

REM Verificar se há containers rodando
docker-compose ps | findstr "Up" >nul 2>&1
if errorlevel 1 (
    echo [i] Nenhum servico esta rodando
    exit /b 0
)

echo [*] Parando servicos Docker...
docker-compose down

echo.
echo [OK] Servicos parados com sucesso!
echo.
echo [i] Os dados foram preservados nos volumes Docker
echo.
echo Para remover tambem os dados (volumes), execute:
echo    docker-compose down -v
echo.
