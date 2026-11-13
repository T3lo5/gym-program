#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# COMANDO ÚNICO DE VERIFICAÇÃO — obrigatório antes de qualquer commit/PR.
#
# O que faz:
#   1. Constrói a mesma imagem Docker usada pelo CI local;
#   2. Roda DENTRO dela os mesmos passos do GitHub Actions:
#      clean -> testes unitários -> Android Lint -> assembleDebug ->
#      verificação de assinatura -> artifact + sha256;
#   3. Roda os checks do backend FastAPI em container limpo.
#
# Tudo roda contra o código atual do working tree (bind mount) e com clean,
# então cache/build antigo do host NÃO consegue mascarar erro de compilação.
#
# Uso:
#   ./scripts/verify.sh              # verificação completa
#   SKIP_BACKEND=1 ./scripts/verify.sh
#   SKIP_LINT=1 ./scripts/verify.sh  # atalho (não aceito como prova final)
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

IMAGE="${CI_IMAGE:-treinos-ci-local}"
CACHE_DIR="$ROOT/.gradle-docker"
DIST="$ROOT/dist"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERRO: Docker não encontrado. Instale o Docker antes de validar o repo." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERRO: o daemon do Docker não está acessível." >&2
  exit 1
fi

mkdir -p "$CACHE_DIR"

echo "==> [1/3] Construindo imagem do CI local ($IMAGE)"
docker build -t "$IMAGE" "$ROOT"

echo "==> [2/3] Rodando CI Android dentro do container"
# VERSION_CODE/VERSION_NAME só são repassados quando definidos explicitamente
# (o CI define os dois). Sem override, scripts/ci-android.sh deriva o
# versionCode local de `git rev-list --count HEAD` (H3-48) — forçar 1 aqui
# anularia essa derivação.
DOCKER_ENV=()
if [[ -n "${VERSION_CODE:-}" ]]; then
  DOCKER_ENV+=(-e "VERSION_CODE=$VERSION_CODE")
fi
if [[ -n "${VERSION_NAME:-}" ]]; then
  DOCKER_ENV+=(-e "VERSION_NAME=$VERSION_NAME")
fi
# --user evita arquivos root-owned em app/build e dist no host.
docker run --rm \
  --user "$(id -u):$(id -g)" \
  "${DOCKER_ENV[@]}" \
  -e HOME=/tmp \
  -e ANDROID_USER_HOME=/tmp/.android \
  -e XDG_CACHE_HOME=/tmp/.cache \
  -e GRADLE_USER_HOME=/home/ci/.gradle \
  -e ANDROID_HOME=/opt/android-sdk \
  -e SKIP_TESTS="${SKIP_TESTS:-0}" \
  -e SKIP_LINT="${SKIP_LINT:-0}" \
  -e COVERAGE="${COVERAGE:-0}" \
  -v "$ROOT:/app" \
  -v "$CACHE_DIR:/home/ci/.gradle" \
  -w /app \
  "$IMAGE" \
  ./scripts/ci-android.sh

if [[ "${SKIP_BACKEND:-0}" != "1" ]]; then
  echo "==> [3/3] Rodando checks do backend"
  "$ROOT/scripts/ci-backend.sh"
else
  echo "==> [3/3] Backend ignorado (SKIP_BACKEND=1)"
fi

echo ""
echo "============================================================"
echo " VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura"
echo "============================================================"
