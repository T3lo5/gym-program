#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# H3-49: instala/atualiza o APK do relógio (Wear OS) com um comando.
#
#   ./scripts/install-wear.sh <ip-do-relógio>      # rota Wi-Fi (recomendada)
#   ./scripts/install-wear.sh --bt                 # rota Bluetooth (via celular)
#   ./scripts/install-wear.sh <ip> caminho/apk     # APK fora do padrão
#
# Sempre usa `adb install -r`: atualiza por cima preservando os dados. Este
# script NUNCA desinstala nada; em erro de assinatura ele orienta o caminho
# manual (desinstalar apaga os dados locais do app).
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/dist/wear.apk"
MODE="wifi"
HOST=""

fail() {
  echo "ERRO: $*" >&2
  exit 1
}

usage() {
  sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bt)
      MODE="bt"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      fail "Opção desconhecida: $1
Uso: ./scripts/install-wear.sh <ip-do-relógio> [caminho/wear.apk]
     ./scripts/install-wear.sh --bt [caminho/wear.apk]"
      ;;
    *)
      if [[ -z "$HOST" ]]; then
        HOST="$1"          # Wi-Fi: primeiro argumento é o IP do relógio
      else
        APK="$1"           # segundo argumento é o APK
      fi
      shift
      ;;
  esac
done

command -v adb >/dev/null 2>&1 || fail \
"adb não encontrado no PATH.
Instale o pacote 'platform-tools' do Android e coloque o diretório no PATH:
  - Debian/Ubuntu:  sudo apt install adb
  - macOS:          brew install --cask android-platform-tools
  - Manual:         https://developer.android.com/tools/releases/platform-tools"

if [[ ! -f "$APK" ]]; then
  fail "APK não encontrado: $APK
Gere o APK com ./scripts/verify.sh (ou baixe o release latest) e passe o
caminho: ./scripts/install-wear.sh <ip> /caminho/wear.apk"
fi

# ---------------------------------------------------------------------------
# Preparar o relógio (uma vez): no relógio, Configurações → Sistema → Sobre →
# toque 7x em 'Número da versão' para liberar as Opções do desenvolvedor;
# dentro delas, ative 'Depuração ADB' e 'Depuração por Wi-Fi' (o relógio mostra
# o IP/porta na tela) ou 'Depuração por Bluetooth' para a rota --bt.
# ---------------------------------------------------------------------------

if [[ "$MODE" == "bt" ]]; then
  # Rota Bluetooth clássica do Wear OS: o tráfego ADB passa pelo celular,
  # ligado ao computador por USB, com o app Wear OS do celular com
  # 'Depuração por Bluetooth' ativa também no celular.
  HOST="localhost:4444"
  echo "==> Rota Bluetooth: encaminhando tcp:4444 → adb-hub do relógio"
  adb forward tcp:4444 localabstract:/adb-hub
  adb connect localhost:4444 >/dev/null 2>&1 || true
else
  [[ -n "$HOST" ]] || fail "Informe o IP do relógio (mostrado na tela em
Opções do desenvolvedor → Depuração por Wi-Fi), ex.: ./scripts/install-wear.sh 192.168.0.42
Sem IP mas com o relógio no mesmo Wi-Fi? Use a rota Bluetooth: ./scripts/install-wear.sh --bt"
  case "$HOST" in
    *:*) ;;                      # já veio ip:porta
    *) HOST="$HOST:5555" ;;      # porta padrão da depuração por Wi-Fi
  esac
  echo "==> Conectando ao relógio em $HOST"
  adb connect "$HOST" >/dev/null 2>&1 || true
fi

# Espera curta e explícita: o relógio pode levar alguns segundos para aceitar.
READY=0
for _ in $(seq 1 10); do
  if adb devices | awk 'NR>1 {print $1, $2}' | grep -q "^$HOST device$"; then
    READY=1
    break
  fi
  sleep 1
done

if [[ "$READY" != "1" ]]; then
  if [[ "$MODE" == "bt" ]]; then
    fail "O relógio não apareceu via Bluetooth em $HOST.
  1. No relógio: Opções do desenvolvedor → ative 'Depuração ADB' e
     'Depuração por Bluetooth'.
  2. No celular (ligado ao computador por USB): abra o app Wear OS,
     Configurações avançadas → ative 'Depuração por Bluetooth'.
  3. Rode este script de novo. Se persistir, use a rota Wi-Fi:
     ./scripts/install-wear.sh <ip-do-relógio>"
  fi
  fail "O relógio não respondeu em $HOST (timeout de 10 s).
  1. Confirme que o relógio e o computador estão na mesma rede Wi-Fi.
  2. No relógio: Opções do desenvolvedor → 'Depuração ADB' e
     'Depuração por Wi-Fi' ativados (a tela mostra o IP atual).
  3. Toque em 'Depuração por Wi-Fi' de novo no relógio para refrescar a
     conexão e rode este script de novo."
fi

echo "==> Instalando $(basename "$APK") no relógio ($HOST) — install -r, dados preservados"
OUT="$(adb -s "$HOST" install -r "$APK" 2>&1)" || true
echo "$OUT"

if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<<"$OUT"; then
  cat >&2 <<'EOF'
ERRO: INSTALL_FAILED_UPDATE_INCOMPATIBLE — este APK foi assinado com uma chave
diferente da versão instalada no relógio. Este script NUNCA desinstala
automaticamente. Para resolver:

  1. Exporte o backup no celular (Ajustes → Backup → 'Exportar backup') — o
     relógio refaz os dados pelo Data Layer após reinstalar.
  2. Desinstale manualmente no relógio: Configurações → Apps → Treinos →
     Desinstalar (ou desinstale pelo celular: apps do relógio aparecem lá).
  3. Rode este script de novo para instalar a versão nova.

Causa definitiva corrigida pela H3-48 (assinatura estável): o `:wear` usa a
mesma keystore versionada do celular; depois desta reinstalação única, os
próximos updates instalam por cima sem perder dados.
EOF
  exit 1
fi

if ! grep -q "Success" <<<"$OUT"; then
  fail "A instalação no relógio falhou (veja a saída do adb acima).
Se a saída citar 'offline', destrave a tela do relógio e rode de novo."
fi

echo "==> Instalado com sucesso no relógio. Dados locais preservados."
