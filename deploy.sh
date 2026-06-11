#!/usr/bin/env bash

# deploy.sh
# Uso:
#   ./deploy.sh up dev       -> Sobe o ambiente de desenvolvimento
#   ./deploy.sh up prod      -> Sobe o ambiente de produção
#   ./deploy.sh down dev     -> Para e remove os containers de desenvolvimento
#   ./deploy.sh down prod    -> Para e remove os containers de produção
#   ./deploy.sh logs prod    -> Mostra logs do ambiente de produção (opcional)

set -e

ACTION="$1"
ENVIRONMENT="$2"

PROJECT_DIR="/c/Sistemas/ws-spring/chronus-api"   # ajuste se necessário
cd "$PROJECT_DIR" || exit 1

print_cyan()  { echo -e "\e[36m$1\e[0m"; }
print_green() { echo -e "\e[32m$1\e[0m"; }
print_red()   { echo -e "\e[31m$1\e[0m"; }

if [ "$ACTION" != "up" ] && [ "$ACTION" != "down" ]; then
    print_red "Erro: ação inválida. Use 'up' ou 'down'."
    print_cyan "Exemplos:"
    echo "  ./deploy.sh up dev"
    echo "  ./deploy.sh down prod"
    exit 1
fi

if [ "$ENVIRONMENT" != "dev" ] && [ "$ENVIRONMENT" != "prod" ]; then
    print_red "Erro: ambiente inválido. Use 'dev' ou 'prod'."
    exit 1
fi

case "$ACTION" in
    up)
        if [ "$ENVIRONMENT" == "dev" ]; then
            print_cyan "Subindo ambiente de desenvolvimento..."
            docker compose up --build
        else
            print_cyan "Construindo imagem de produção..."
            docker build -t chronus-api:prod .
            if [ $? -ne 0 ]; then
                print_red "Erro no build da imagem."
                exit 1
            fi
            print_cyan "Subindo containers de produção em background..."
            docker compose -f docker-compose.prod.yml up -d
            if [ $? -eq 0 ]; then
                print_green "Ambiente de produção iniciado. Logs:"
                print_green "  docker compose -f docker-compose.prod.yml logs -f"
            else
                print_red "Falha ao iniciar produção."
                exit 1
            fi
        fi
        ;;
    down)
        if [ "$ENVIRONMENT" == "dev" ]; then
            print_cyan "Parando e removendo ambiente de desenvolvimento..."
            docker compose down
        else
            print_cyan "Parando e removendo ambiente de produção..."
            docker compose -f docker-compose.prod.yml down
        fi
        print_green "Ambiente '$ENVIRONMENT' derrubado com sucesso."
        ;;
esac