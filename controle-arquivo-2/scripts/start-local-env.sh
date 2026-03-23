#!/bin/bash

# Script para iniciar o ambiente local de desenvolvimento

set -e

echo "=========================================="
echo "Controle de Arquivos - Ambiente Local"
echo "=========================================="
echo ""

# Verificar se Docker está rodando
if ! docker info > /dev/null 2>&1; then
    echo "❌ Erro: Docker não está rodando"
    echo "Por favor, inicie o Docker e tente novamente"
    exit 1
fi

echo "✅ Docker está rodando"
echo ""

# Verificar se arquivo .env existe
if [ ! -f .env ]; then
    echo "⚠️  Arquivo .env não encontrado"
    echo "Criando .env a partir de .env.example..."
    cp .env.example .env
    echo "✅ Arquivo .env criado"
    echo ""
fi

# Criar diretórios necessários
echo "📁 Criando diretórios necessários..."
mkdir -p scripts/ddl
mkdir -p scripts/localstack
mkdir -p scripts/sftp
echo "✅ Diretórios criados"
echo ""

# Tornar script de inicialização do LocalStack executável
if [ -f scripts/localstack/init-s3.sh ]; then
    chmod +x scripts/localstack/init-s3.sh
    echo "✅ Script de inicialização do LocalStack configurado"
    echo ""
fi

# Iniciar serviços
echo "🚀 Iniciando serviços Docker..."
docker-compose up -d

echo ""
echo "⏳ Aguardando serviços ficarem prontos..."
echo "   (Isso pode levar alguns minutos na primeira execução)"
echo ""

# Aguardar serviços ficarem healthy
MAX_WAIT=180
ELAPSED=0
INTERVAL=5

while [ $ELAPSED -lt $MAX_WAIT ]; do
    HEALTHY=$(docker-compose ps | grep -c "healthy" || true)
    TOTAL=$(docker-compose ps | grep -c "Up" || true)
    
    echo "   Status: $HEALTHY/$TOTAL serviços prontos"
    
    if [ "$HEALTHY" -eq 4 ]; then
        echo ""
        echo "✅ Todos os serviços estão prontos!"
        break
    fi
    
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
    echo ""
    echo "⚠️  Timeout aguardando serviços ficarem prontos"
    echo "Verifique o status com: docker-compose ps"
    echo "Verifique os logs com: docker-compose logs"
fi

echo ""
echo "=========================================="
echo "Ambiente Local Iniciado!"
echo "=========================================="
echo ""
echo "📊 Serviços disponíveis:"
echo ""
echo "  🗄️  Oracle Database"
echo "     URL: jdbc:oracle:thin:@localhost:1521:XE"
echo "     User: system / Password: Oracle123"
echo "     Enterprise Manager: https://localhost:5500/em"
echo ""
echo "  🐰 RabbitMQ"
echo "     AMQP: localhost:5672"
echo "     Management UI: http://localhost:15672"
echo "     User: admin / Password: admin123"
echo ""
echo "  ☁️  LocalStack (S3)"
echo "     Endpoint: http://localhost:4566"
echo "     Region: us-east-1"
echo "     Access Key: test / Secret Key: test"
echo ""
echo "  📁 SFTP Server"
echo "     Host: localhost:2222"
echo "     User: sftpuser / Password: sftppass"
echo ""
echo "=========================================="
echo ""
echo "📝 Comandos úteis:"
echo "   Ver logs:        docker-compose logs -f"
echo "   Parar serviços:  docker-compose down"
echo "   Status:          docker-compose ps"
echo ""
echo "📖 Documentação completa: DOCKER_SETUP.md"
echo ""
