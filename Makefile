.PHONY: help up down restart test e2e build clean logs status rebuild full-rebuild

# Default target
.DEFAULT_GOAL := help

# Colors for output
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m # No Color

##@ General

help: ## Exibe esta mensagem de ajuda
	@echo "$(BLUE)=========================================="
	@echo "EDI File Control System - Makefile"
	@echo "==========================================$(NC)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf "Usage:\n  make $(GREEN)<target>$(NC)\n"} /^[a-zA-Z_0-9-]+:.*?##/ { printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2 } /^##@/ { printf "\n$(BLUE)%s$(NC)\n", substr($$0, 5) } ' $(MAKEFILE_LIST)
	@echo ""

##@ Docker Compose

up: build ## Inicia todos os serviços do docker-compose
	@echo "$(BLUE)🚀 Iniciando serviços...$(NC)"
	@docker-compose up -d oracle rabbitmq localstack sftp-origin sftp-destination sftp-client
	@echo "$(YELLOW)⏳ Aguardando serviços ficarem saudáveis (pode levar 2-3 minutos)...$(NC)"
	@$(MAKE) wait-healthy
	@echo "$(BLUE)🗄️  Inicializando banco de dados...$(NC)"
	@$(MAKE) init-db
	@echo "$(BLUE)🚀 Iniciando Producer e Consumer...$(NC)"
	@docker-compose up -d --build producer consumer
	@echo ""
	@$(MAKE) status
	@echo ""
	@echo "$(GREEN)✅ Sistema iniciado com sucesso!$(NC)"
	@echo ""
	@echo "URLs dos serviços:"
	@echo "  - Producer Health:     http://localhost:8080/actuator/health"
	@echo "  - Consumer Health:     http://localhost:8081/actuator/health"
	@echo "  - RabbitMQ Management: http://localhost:15672 (admin/admin)"
	@echo "  - Oracle Database:     localhost:1521/XEPDB1 (edi_user/edi_pass)"
	@echo "  - LocalStack S3:       http://localhost:4566"
	@echo ""
	@echo "Servidores SFTP:"
	@echo "  - Origin:      sftp://cielo@localhost:2222 (password: admin-1-2-3)"
	@echo "  - Destination: sftp://internal@localhost:2223 (password: internal-pass)"
	@echo "  - Client:      sftp://client@localhost:2224 (password: client-pass)"
	@echo ""

down: ## Para e remove todos os containers (mantém volumes)
	@echo "$(YELLOW)🛑 Parando todos os serviços...$(NC)"
	@docker-compose down
	@echo "$(GREEN)✅ Serviços parados$(NC)"

down-volumes: ## Para e remove todos os containers e volumes (limpa dados)
	@echo "$(RED)🗑️  Parando e removendo todos os serviços e dados...$(NC)"
	@docker-compose down -v
	@echo "$(GREEN)✅ Serviços e dados removidos$(NC)"

restart: ## Reinicia todos os serviços
	@echo "$(YELLOW)🔄 Reiniciando serviços...$(NC)"
	@$(MAKE) down
	@$(MAKE) up

##@ Build & Test

build: ## Compila o projeto (sem testes)
	@echo "$(BLUE)📦 Compilando projeto...$(NC)"
	@mvn clean install -Dmaven.test.skip=true
	@echo "$(GREEN)✅ Build concluído$(NC)"

test: ## Executa testes unitários
	@echo "$(BLUE)🧪 Executando testes unitários...$(NC)"
	@mvn test -Dtest=*Test
	@echo "$(GREEN)✅ Testes concluídos$(NC)"

e2e: ## Executa testes E2E (requer docker-compose rodando)
	@echo "$(BLUE)🧪 Executando testes E2E...$(NC)"
	@echo "$(YELLOW)⚠️  Certifique-se de que o docker-compose está rodando$(NC)"
	@echo "$(BLUE)⚙️  Configurando QUEUE_DELAY=20 no Consumer...$(NC)"
	@QUEUE_DELAY=20 docker-compose up -d consumer
	@echo "$(YELLOW)⏳ Aguardando Consumer reiniciar (5 segundos)...$(NC)"
	@sleep 5
	@echo "$(BLUE)🧪 Executando testes...$(NC)"
	@mvn test -Dtest=FileTransferE2ETest -pl commons
	@echo "$(BLUE)⚙️  Restaurando QUEUE_DELAY=0 no Consumer...$(NC)"
	@QUEUE_DELAY=0 docker-compose up -d consumer
	@echo "$(GREEN)✅ Testes E2E concluídos$(NC)"

rebuild: ## Faz build completo, executa testes unitários e reinicia docker-compose
	@echo "$(BLUE)🔨 Iniciando rebuild completo...$(NC)"
	@echo ""
	@echo "$(BLUE)📦 Passo 1/3: Build do projeto$(NC)"
	@mvn clean install
	@echo ""
	@echo "$(BLUE)🛑 Passo 2/3: Parando serviços$(NC)"
	@$(MAKE) down
	@echo ""
	@echo "$(BLUE)🚀 Passo 3/3: Iniciando serviços$(NC)"
	@$(MAKE) up
	@echo ""
	@echo "$(GREEN)✅ Rebuild completo concluído!$(NC)"

full-rebuild: ## Build completo incluindo reconstrução das imagens Docker
	@echo "$(BLUE)🔨 Iniciando rebuild completo com reconstrução de imagens...$(NC)"
	@echo ""
	@echo "$(BLUE)📦 Passo 1/4: Build do projeto$(NC)"
	@mvn clean install
	@echo ""
	@echo "$(BLUE)🛑 Passo 2/4: Parando e removendo containers$(NC)"
	@docker-compose down
	@echo ""
	@echo "$(BLUE)🏗️  Passo 3/4: Reconstruindo imagens Docker$(NC)"
	@docker-compose build --no-cache producer consumer
	@echo ""
	@echo "$(BLUE)🚀 Passo 4/4: Iniciando serviços$(NC)"
	@$(MAKE) up
	@echo ""
	@echo "$(GREEN)✅ Rebuild completo com imagens concluído!$(NC)"

##@ Utilities

logs: ## Exibe logs de todos os serviços (use CTRL+C para sair)
	@docker-compose logs -f

logs-producer: ## Exibe logs do Producer
	@docker-compose logs -f producer

logs-consumer: ## Exibe logs do Consumer
	@docker-compose logs -f consumer

logs-infra: ## Exibe logs da infraestrutura (Oracle, RabbitMQ, LocalStack)
	@docker-compose logs -f oracle rabbitmq localstack

status: ## Exibe status de todos os containers
	@echo "$(BLUE)📊 Status dos containers:$(NC)"
	@docker-compose ps

clean: ## Remove arquivos de build do Maven
	@echo "$(YELLOW)🧹 Limpando arquivos de build...$(NC)"
	@mvn clean
	@echo "$(GREEN)✅ Limpeza concluída$(NC)"

shell-producer: ## Abre shell no container do Producer
	@docker exec -it edi-producer /bin/sh

shell-consumer: ## Abre shell no container do Consumer
	@docker exec -it edi-consumer /bin/sh

shell-oracle: ## Abre SQL*Plus no Oracle
	@docker exec -it edi-oracle sqlplus edi_user/edi_pass@XEPDB1

init-db: ## Inicializa o banco de dados com os scripts DDL
	@echo "$(BLUE)🗄️  Inicializando banco de dados...$(NC)"
	@docker cp scripts/ddl edi-oracle:/tmp/
	@docker exec -u root edi-oracle chmod -R 755 /tmp/ddl
	@docker exec edi-oracle bash -c "cd /tmp/ddl && sqlplus -s edi_user/edi_pass@//localhost/XEPDB1 @00_run_all.sql"
	@echo "$(GREEN)✅ Banco de dados inicializado$(NC)"

rabbitmq-queues: ## Lista filas do RabbitMQ
	@docker exec edi-rabbitmq rabbitmqctl list_queues

s3-list: ## Lista buckets e objetos no LocalStack S3
	@docker exec edi-localstack awslocal s3 ls
	@echo ""
	@docker exec edi-localstack awslocal s3 ls s3://edi-files/ --recursive

sftp-copy: ## Copia arquivo para SFTP origin (uso: make sftp-copy FILE=arquivo.txt)
	@if [ -z "$(FILE)" ]; then \
		echo "$(RED)❌ Erro: Especifique o arquivo com FILE=caminho/arquivo$(NC)"; \
		echo "Exemplo: make sftp-copy FILE=test.txt"; \
		exit 1; \
	fi
	@if [ ! -f "$(FILE)" ]; then \
		echo "$(RED)❌ Erro: Arquivo '$(FILE)' não encontrado$(NC)"; \
		exit 1; \
	fi
	@echo "$(BLUE)📤 Copiando $(FILE) para SFTP origin...$(NC)"
	@docker cp "$(FILE)" edi-sftp-origin:/home/cielo/upload/
	@echo "$(GREEN)✅ Arquivo copiado para /home/cielo/upload/$(notdir $(FILE))$(NC)"

sftp-list: ## Lista arquivos no SFTP origin
	@echo "$(BLUE)📂 Arquivos no SFTP origin (/home/cielo/upload):$(NC)"
	@docker exec edi-sftp-origin ls -lah /home/cielo/upload/

sftp-list-dest: ## Lista arquivos no SFTP destination
	@echo "$(BLUE)📂 Arquivos no SFTP destination (/home/internal/destination):$(NC)"
	@docker exec edi-sftp-destination ls -lah /home/internal/destination/

sftp-clean: ## Remove todos os arquivos do SFTP origin
	@echo "$(YELLOW)🧹 Limpando arquivos do SFTP origin...$(NC)"
	@docker exec edi-sftp-origin sh -c "rm -f /home/cielo/upload/*"
	@echo "$(GREEN)✅ SFTP origin limpo$(NC)"

sftp-create-test: ## Cria arquivo de teste no SFTP origin
	@echo "$(BLUE)📝 Criando arquivo de teste...$(NC)"
	@docker exec edi-sftp-origin sh -c "echo 'Test file created at $(shell date)' > /home/cielo/upload/test-$(shell date +%Y%m%d-%H%M%S).txt"
	@echo "$(GREEN)✅ Arquivo de teste criado$(NC)"
	@$(MAKE) sftp-list

##@ Internal Helpers

wait-healthy: ## Aguarda serviços ficarem saudáveis (uso interno)
	@MAX_WAIT=180; \
	ELAPSED=0; \
	INTERVAL=5; \
	while [ $$ELAPSED -lt $$MAX_WAIT ]; do \
		ORACLE_HEALTH=$$(docker inspect --format='{{.State.Health.Status}}' edi-oracle 2>/dev/null || echo "starting"); \
		RABBITMQ_HEALTH=$$(docker inspect --format='{{.State.Health.Status}}' edi-rabbitmq 2>/dev/null || echo "starting"); \
		LOCALSTACK_HEALTH=$$(docker inspect --format='{{.State.Health.Status}}' edi-localstack 2>/dev/null || echo "starting"); \
		if [ "$$ORACLE_HEALTH" = "healthy" ] && [ "$$RABBITMQ_HEALTH" = "healthy" ] && [ "$$LOCALSTACK_HEALTH" = "healthy" ]; then \
			echo "$(GREEN)✓ Todos os serviços de infraestrutura estão saudáveis$(NC)"; \
			break; \
		fi; \
		echo "   Oracle: $$ORACLE_HEALTH | RabbitMQ: $$RABBITMQ_HEALTH | LocalStack: $$LOCALSTACK_HEALTH"; \
		sleep $$INTERVAL; \
		ELAPSED=$$((ELAPSED + INTERVAL)); \
	done; \
	if [ $$ELAPSED -ge $$MAX_WAIT ]; then \
		echo "$(YELLOW)⚠️  Aviso: Serviços não ficaram saudáveis em $$MAX_WAIT segundos$(NC)"; \
		echo "   Continuando mesmo assim... Verifique os logs se as aplicações falharem"; \
	fi

check-docker: ## Verifica se Docker está rodando (uso interno)
	@if ! docker info > /dev/null 2>&1; then \
		echo "$(RED)❌ Erro: Docker não está rodando$(NC)"; \
		echo "Por favor, inicie o Docker e tente novamente"; \
		exit 1; \
	fi
	@echo "$(GREEN)✓ Docker está rodando$(NC)"
