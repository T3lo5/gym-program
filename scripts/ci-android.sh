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
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
WEAR_APK="$ROOT/wear/build/outputs/apk/debug/wear-debug.apk"
DIST="$ROOT/dist"

export GRADLE_OPTS="${GRADLE_OPTS:--Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -Dorg.gradle.daemon=false}"

TASKS=(clean)
if [[ "${SKIP_TESTS:-0}" != "1" ]]; then
  TASKS+=(":protocol:test")
  TASKS+=(":app:testDebugUnitTest")
  TASKS+=(":wear:testDebugUnitTest")
fi
if [[ "${SKIP_LINT:-0}" != "1" ]]; then
  TASKS+=(":app:lintDebug")
  TASKS+=(":wear:lintDebug")
fi
TASKS+=(":app:assembleDebug")
TASKS+=(":wear:assembleDebug")
# Os testes instrumentados não RODAM aqui (o Docker não tem emulador), mas têm de
# compilar: sem este passo o source set `androidTest` apodrece sem ninguém ver —
# foi assim que arquivos quebrados entraram no repo.
TASKS+=(":app:assembleDebugAndroidTest")
if [[ "${COVERAGE:-0}" == "1" ]]; then
  TASKS+=(":app:createDebugUnitTestCoverageReport")
fi

echo "==> [1/5] Gradle: ${TASKS[*]}"
echo "    versionCode=$VERSION_CODE versionName=$VERSION_NAME"
./gradlew "${TASKS[@]}" \
  --no-daemon \
  --stacktrace \
  --console=plain \
  "-PversionCode=$VERSION_CODE" \
  "-PversionName=$VERSION_NAME"

echo "==> [2/5] Verificando assinatura do APK do app"
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
"$BUILD_TOOLS/apksigner" verify --verbose "$APP_APK"
echo "    app APK assinado OK"

echo "==> [3/5] Verificando assinatura do APK do wear"
"$BUILD_TOOLS/apksigner" verify --verbose "$WEAR_APK"
echo "    wear APK assinado OK"

echo "==> [4/5] Preparando artefatos (dist/)"
rm -rf "$DIST"
mkdir -p "$DIST"
cp "$APP_APK" "$DIST/treinos.apk"
cp "$WEAR_APK" "$DIST/wear.apk"
# Checksums individuais (compatível com o workflow de release)
(cd "$DIST" && sha256sum treinos.apk > treinos.apk.sha256)
(cd "$DIST" && sha256sum wear.apk > wear.apk.sha256)
# Checksum combinado de todos os artefatos
(cd "$DIST" && sha256sum treinos.apk wear.apk > dist.sha256)

echo "==> [5/5] Resumo"
APP_SIZE="$(du -h "$DIST/treinos.apk" | cut -f1)"
WEAR_SIZE="$(du -h "$DIST/wear.apk" | cut -f1)"
echo "    App APK:   dist/treinos.apk ($APP_SIZE)"
echo "    Wear APK:  dist/wear.apk ($WEAR_SIZE)"
echo "    SHA-256:   dist/dist.sha256"
echo "    Testes:    app/build/reports/tests/testDebugUnitTest/index.html"
if [[ "${COVERAGE:-0}" == "1" ]]; then
  echo "    Cobertura: app/build/reports/coverage/test/debug/index.html"
fi
echo "    Lint:      app/build/reports/lint-results-debug.html / wear/build/reports/lint-results-debug.html"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Android verificado"
    echo ""
    echo "- Versão: \`$VERSION_NAME\` (versionCode $VERSION_CODE)"
    echo "- App APK: \`dist/treinos.apk\` ($APP_SIZE)"
    echo "- Wear APK: \`dist/wear.apk\` ($WEAR_SIZE)"
    echo "- SHA-256: \`dist/dist.sha256\`"
    echo "- Testes unitários: executados (:protocol, :app, :wear)"
    echo "- Android Lint: executado (:app, :wear)"
  } >> "$GITHUB_STEP_SUMMARY"
fi
