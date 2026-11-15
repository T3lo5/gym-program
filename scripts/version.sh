#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# FONTE ÚNICA de versionCode/versionName (distribuição e atualização).
#
# Usada por scripts/ci-android.sh (gate local e CI) e pelo workflow
# .github/workflows/android.yml. Assim o APK publicado no release e o build
# local têm SEMPRE a mesma numeração, e o updater in-app (H3-51) enxerga a
# atualização em vez de dizer "já atualizado".
#
# versionCode (opção B, 2026-11-15): minutos desde 2020-01-01T00:00:00Z.
#   - monotônico no tempo, imune a branch, histórico e `run_number`;
#   - idêntico no CI e no build local;
#   - cabe em int32 (limite ~ano 6000);
#   - dois builds no mesmo minuto compartilham o código — instalar por cima
#     com o mesmo versionCode é permitido; só downgrade é recusado.
#
# versionName: lido de version.properties (raiz), fonte única do rótulo.
#
# Uso:
#   ./scripts/version.sh          # "versionCode=... versionName=..."
#   ./scripts/version.sh code     # só o versionCode
#   ./scripts/version.sh name     # só o versionName
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EPOCH_2020_SECONDS=1577836800

version_code() {
  local now
  now="$(date -u +%s)"
  echo $(( (now - EPOCH_2020_SECONDS) / 60 ))
}

version_name() {
  local file="$ROOT/version.properties" name
  if [[ ! -f "$file" ]]; then
    echo "ERRO: $file não encontrado (fonte única do versionName)." >&2
    exit 1
  fi
  name="$(grep -E '^versionName=' "$file" | head -1 | cut -d= -f2- | tr -d '[:space:]' || true)"
  if [[ -z "$name" ]]; then
    echo "ERRO: versionName vazio em $file." >&2
    exit 1
  fi
  echo "$name"
}

case "${1:-all}" in
  code) version_code ;;
  name) version_name ;;
  all)  echo "versionCode=$(version_code) versionName=$(version_name)" ;;
  *)    echo "Uso: $0 [code|name]" >&2; exit 2 ;;
esac
