#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# PARIDADE COM O CI: executa exatamente os passos do job "Build APK" do
# GitHub Actions (.github/workflows/android.yml).
#
# Roda tanto no runner do GitHub quanto dentro da imagem Docker local
# (scripts/verify.sh). Se você mudar o CI, mude ESTE arquivo — o workflow
# chama este script para não existirem duas receitas diferentes.
#
# Uso:
#   ./scripts/ci-android.sh
#   SKIP_LINT=1 SKIP_TESTS=1 ./scripts/ci-android.sh   # só build rápido
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-0.0.0-local}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
DIST="$ROOT/dist"

export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -Dorg.gradle.daemon=false}"

TASKS=(clean)
if [[ "${SKIP_TESTS:-0}" != "1" ]]; then
  TASKS+=(":app:testDebugUnitTest")
fi
if [[ "${SKIP_LINT:-0}" != "1" ]]; then
  TASKS+=(":app:lintDebug")
fi
TASKS+=(":app:assembleDebug")

echo "==> [1/4] Gradle: ${TASKS[*]}"
echo "    versionCode=$VERSION_CODE versionName=$VERSION_NAME"
./gradlew "${TASKS[@]}" \
  --no-daemon \
  --stacktrace \
  --console=plain \
  "-PversionCode=$VERSION_CODE" \
  "-PversionName=$VERSION_NAME"

echo "==> [2/4] Verificando assinatura do APK"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  echo "ERRO: ANDROID_HOME/ANDROID_SDK_ROOT não definido." >&2
  exit 1
fi
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
if [[ -z "$BUILD_TOOLS" || ! -x "$BUILD_TOOLS/apksigner" ]]; then
  echo "ERRO: apksigner não encontrado em $SDK/build-tools." >&2
  exit 1
fi
"$BUILD_TOOLS/apksigner" verify --verbose "$APK"

echo "==> [3/4] Preparando artefato (dist/)"
rm -rf "$DIST"
mkdir -p "$DIST"
cp "$APK" "$DIST/treinos.apk"
(cd "$DIST" && sha256sum treinos.apk > treinos.apk.sha256)

echo "==> [4/4] Resumo"
APK_SIZE="$(du -h "$DIST/treinos.apk" | cut -f1)"
CHECKSUM="$(cut -d' ' -f1 "$DIST/treinos.apk.sha256")"
echo "    APK:      dist/treinos.apk ($APK_SIZE)"
echo "    SHA-256:  $CHECKSUM"
echo "    Testes:   app/build/reports/tests/testDebugUnitTest/index.html"
echo "    Lint:     app/build/reports/lint-results-debug.html"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Android verificado"
    echo ""
    echo "- Versão: \`$VERSION_NAME\` (versionCode $VERSION_CODE)"
    echo "- APK: \`dist/treinos.apk\` ($APK_SIZE)"
    echo "- SHA-256: \`$CHECKSUM\`"
    echo "- Testes unitários: executados"
    echo "- Android Lint: executado"
  } >> "$GITHUB_STEP_SUMMARY"
fi
