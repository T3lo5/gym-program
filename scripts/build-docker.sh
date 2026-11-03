#!/bin/bash
# Build local via Docker — replica o CI do GitHub Actions sem precisar de push.
#
# Uso recomendado (verificação completa = obrigatória antes de commit):
#   ./scripts/verify.sh
#
# Uso rápido (apenas uma tarefa Gradle, SEM lint/testes/assinatura):
#   ./scripts/build-docker.sh :app:assembleDebug
#
# Sem argumentos, delega para ./scripts/verify.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IMAGE_NAME="treinos-ci-local"

if [[ $# -eq 0 ]]; then
  exec "$SCRIPT_DIR/verify.sh"
fi

TASK="$*"

echo "AVISO: modo tarefa única ('$TASK'). Isso NÃO substitui ./scripts/verify.sh"
echo "       (não roda testes, lint nem verifica assinatura)."
echo "==> Criando imagem Docker para build do Android..."
docker build -t "$IMAGE_NAME" "$PROJECT_DIR"

echo "==> Executando build: $TASK"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e ANDROID_USER_HOME=/tmp/.android \
  -e XDG_CACHE_HOME=/tmp/.cache \
  -e GRADLE_USER_HOME=/home/ci/.gradle \
  -e ANDROID_HOME=/opt/android-sdk \
  -e GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -Dorg.gradle.daemon=false" \
  -v "$PROJECT_DIR":/app \
  -v "$PROJECT_DIR/.gradle-docker":/home/ci/.gradle \
  -w /app \
  "$IMAGE_NAME" \
  ./gradlew clean "$TASK" --no-daemon --stacktrace --console=plain

echo "==> Build concluído. APK em: app/build/outputs/apk/debug/"
