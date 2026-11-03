#!/bin/bash
# Build local do APK via Docker — replica o CI do GitHub Actions sem precisar fazer push.
# Uso: ./scripts/build-docker.sh [tarefa gradle]
# Exemplo: ./scripts/build-docker.sh :app:assembleDebug

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IMAGE_NAME="treinos-ci-local"
TASK="${1:-:app:assembleDebug}"

echo "==> Criando imagem Docker para build do Android..."
docker build -t "$IMAGE_NAME" "$PROJECT_DIR"

echo "==> Executando build: $TASK"
docker run --rm \
  -v "$PROJECT_DIR":/app \
  -v "$HOME/.gradle":/root/.gradle \
  -e GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -Dorg.gradle.daemon=false" \
  "$IMAGE_NAME" \
  "$TASK"

echo "==> Build concluído. APK em: app/build/outputs/apk/debug/"
