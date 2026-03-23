#!/bin/bash

# Script para parar o ambiente local de desenvolvimento

set -e

echo "=========================================="
echo "Controle de Arquivos - Parar Ambiente"
echo "=========================================="
echo ""

# Verificar se há containers rodando
if ! docker-compose ps | grep -q "Up"; then
    echo "ℹ️  Nenhum serviço está rodando"
    exit 0
fi

echo "🛑 Parando serviços Docker..."
docker-compose down

echo ""
echo "✅ Serviços parados com sucesso!"
echo ""
echo "ℹ️  Os dados foram preservados nos volumes Docker"
echo ""
echo "Para remover também os dados (volumes), execute:"
echo "   docker-compose down -v"
echo ""
