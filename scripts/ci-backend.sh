#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# QUALIDADE DO BACKEND: roda lint, import e testes do FastAPI em um container
# limpo. Não grava nada no host (source montado como somente leitura).
#
# Uso: ./scripts/ci-backend.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACK_DIR="$ROOT/back"
IMAGE="${BACKEND_CI_IMAGE:-python:3.11-slim}"

if [[ ! -d "$BACK_DIR/app" ]]; then
  echo "ERRO: diretório back/app não encontrado." >&2
  exit 1
fi

echo "==> Backend: instalando dependências e rodando checks em $IMAGE"
docker run --rm \
  -v "$BACK_DIR:/src:ro" \
  -w /src \
  -e PYTHONDONTWRITEBYTECODE=1 \
  -e PYTHONUNBUFFERED=1 \
  -e DATABASE_PATH=/tmp/treinos-test.db \
  -e MDNS_ENABLED=false \
  -e RUFF_CACHE_DIR=/tmp/ruff-cache \
  "$IMAGE" \
  bash -lc '
    set -euo pipefail
    pip install --disable-pip-version-check -q \
      -r /src/requirements.txt \
      -r /src/requirements-dev.txt

    echo "--> ruff"
    ruff check /src/app /src/tests

    echo "--> import do app"
    PYTHONPATH=/src python -c "from app.main import app; print(\"import ok:\", app.title)"

    echo "--> pytest"
    PYTHONPATH=/src pytest -q -p no:cacheprovider /src/tests
  '

echo "==> Backend OK"
