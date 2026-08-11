#!/usr/bin/env bash
# =============================================================================
# FleetOps — verificação local (o "CI da sua máquina").
#
# Roda exatamente o que o contrato de qualidade exige antes de considerar uma
# funcionalidade pronta: compilação sem avisos novos, lint limpo, testes verdes
# e cobertura mínima nas camadas de domínio e aplicação.
#
#   ./scripts/verificar.sh              # tudo
#   ./scripts/verificar.sh backend      # apenas backend
#   ./scripts/verificar.sh frontend     # apenas frontend
#
# O backend é executado dentro de um container Gradle, de modo que não é preciso
# ter Java instalado no host. Os testes de integração usam Testcontainers, que
# precisa do socket do Docker — daí o mapeamento e o `--group-add`.
# =============================================================================
set -Eeuo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALVO="${1:-tudo}"

VERMELHO=$'\033[0;31m'; VERDE=$'\033[0;32m'; AMARELO=$'\033[0;33m'
AZUL=$'\033[0;36m'; NEGRITO=$'\033[1m'; FIM=$'\033[0m'

IMAGEM_GRADLE="gradle:8.14-jdk21"
IMAGEM_NODE="node:24-alpine"

etapa()  { printf '\n%s%s▸ %s%s\n' "$NEGRITO" "$AZUL" "$1" "$FIM"; }
ok()     { printf '%s✔ %s%s\n' "$VERDE" "$1" "$FIM"; }
aviso()  { printf '%s! %s%s\n' "$AMARELO" "$1" "$FIM"; }
falhar() { printf '\n%s✘ %s%s\n' "$VERMELHO" "$1" "$FIM"; exit 1; }

exigir_docker() {
  command -v docker >/dev/null 2>&1 || falhar "Docker não encontrado. Ele é necessário para a verificação."
  docker info >/dev/null 2>&1 || falhar "O daemon do Docker não está acessível para o seu usuário."
}

# GID do grupo dono do socket do Docker: sem ele, o Testcontainers não consegue
# criar o container do PostgreSQL a partir de dentro do container do Gradle.
gid_do_socket_docker() {
  stat -c '%g' /var/run/docker.sock 2>/dev/null || echo ""
}

verificar_backend() {
  etapa "Backend — compilação, lint, testes e cobertura"

  local gid_docker; gid_docker="$(gid_do_socket_docker)"
  local args_grupo=()
  if [[ -n "$gid_docker" ]]; then
    args_grupo=(--group-add "$gid_docker")
  else
    aviso "Socket do Docker não localizado; os testes de integração podem falhar."
  fi

  # O cache do Gradle fica fora de backend/, para que limpar artefatos de build
  # não obrigue a rebaixar todas as dependências.
  mkdir -p "$RAIZ/.docker-data/gradle"

  # Usa o Gradle já embutido na imagem em vez de `./gradlew`: o wrapper baixaria
  # uma distribuição inteira a cada cache limpo, tornando a verificação refém da rede.
  #
  # `--project-cache-dir` aponta para fora de backend/: sem isso, a verificação disputaria
  # o lock de `backend/.gradle` com o container de desenvolvimento e falharia sempre que o
  # ambiente estivesse de pé.
  docker run --rm \
    -u "$(id -u):$(id -g)" \
    "${args_grupo[@]}" \
    -v "$RAIZ/backend":/app \
    -v "$RAIZ/.docker-data/gradle":/gradle-home \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -w /app \
    -e GRADLE_USER_HOME=/gradle-home \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    --add-host=host.docker.internal:host-gateway \
    "$IMAGEM_GRADLE" \
    gradle --no-daemon --console=plain --project-cache-dir=/gradle-home/project-cache check \
    || falhar "Verificação do backend falhou. Relatórios em backend/build/reports/."

  ok "Backend aprovado (compilação sem avisos, Checkstyle limpo, testes e cobertura ≥ 80%)"
}

verificar_frontend() {
  etapa "Frontend — tipos, lint, testes e build de produção"

  mkdir -p "$RAIZ/.docker-data/npm" "$RAIZ/.docker-data/frontend-node-modules"

  # `node_modules` do container é separado do da máquina: pacotes com binário
  # nativo (rollup, esbuild) são compilados para glibc ou musl, e reaproveitar a
  # instalação do host dentro da imagem Alpine quebra o build.
  docker run --rm \
    -u "$(id -u):$(id -g)" \
    -v "$RAIZ/frontend":/app \
    -v "$RAIZ/.docker-data/frontend-node-modules":/app/node_modules \
    -v "$RAIZ/.docker-data/npm":/cache-npm \
    -w /app \
    -e npm_config_cache=/cache-npm \
    "$IMAGEM_NODE" \
    sh -eu -c '
      # Reinstala quando o lockfile é mais novo que a instalação: sem esta comparação,
      # acrescentar uma dependência deixaria a verificação rodando contra a árvore antiga.
      if [ ! -f node_modules/.package-lock.json ] || [ package-lock.json -nt node_modules/.package-lock.json ]; then
        echo "Instalando dependências…"
        npm ci --no-audit --no-fund
      fi
      npm run typecheck
      npm run lint
      npm run test
      npm run build
    ' || falhar "Verificação do frontend falhou."

  ok "Frontend aprovado (TypeScript estrito, ESLint limpo, testes verdes, build gerado)"
}

verificar_composicao() {
  etapa "Docker Compose — validação da composição"
  [[ -f "$RAIZ/.env" ]] || falhar "Arquivo .env ausente. Copie .env.example para .env e ajuste os segredos."
  (cd "$RAIZ" && docker compose --profile dev --profile prod config >/dev/null) \
    || falhar "docker-compose.yml inválido."
  ok "Composição válida nos perfis dev e prod"
}

exigir_docker

case "$ALVO" in
  backend)  verificar_backend ;;
  frontend) verificar_frontend ;;
  compose)  verificar_composicao ;;
  tudo)
    verificar_composicao
    verificar_backend
    verificar_frontend
    ;;
  *) falhar "Alvo desconhecido: '$ALVO'. Use: tudo | backend | frontend | compose" ;;
esac

printf '\n%s%s✔ Verificação concluída com sucesso.%s\n' "$NEGRITO" "$VERDE" "$FIM"
