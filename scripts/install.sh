#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# H3-49: instala/atualiza o APK do celular com um comando.
#
#   ./scripts/install.sh                 # instala dist/treinos.apk
#   ./scripts/install.sh caminho/apk     # instala outro APK
#
# Sempre usa `adb install -r`: atualiza por cima preservando os dados do app.
# Este script NUNCA desinstala nada. Se a assinatura mudou
# (INSTALL_FAILED_UPDATE_INCOMPATIBLE), ele orienta o caminho manual —
# desinstalar apaga os dados locais do app (planos, treinos, séries).
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT/dist/treinos.apk}"

fail() {
  echo "ERRO: $*" >&2
  exit 1
}

command -v adb >/dev/null 2>&1 || fail \
"adb não encontrado no PATH.
Instale o pacote 'platform-tools' do Android e coloque o diretório no PATH:
  - Debian/Ubuntu:  sudo apt install adb
  - macOS:          brew install --cask android-platform-tools
  - Manual:         https://developer.android.com/tools/releases/platform-tools"

if [[ ! -f "$APK" ]]; then
  fail "APK não encontrado: $APK
Gere o APK com ./scripts/verify.sh (ou baixe o release latest) e passe o
caminho se ele não estiver em dist/: ./scripts/install.sh /caminho/treinos.apk"
fi

# Lista de dispositivos realmente prontos (estado "device").
mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')

if [[ ${#DEVICES[@]} -eq 0 ]]; then
  if adb devices | grep -q "unauthorized"; then
    fail "O celular está conectado mas NÃO AUTORIZADO.
No celular, desbloqueie a tela e aceite o diálogo 'Permitir depuração USB'
(marca 'Sempre permitir deste computador') e rode de novo."
  fi
  fail "Nenhum celular Android conectado.
  1. Ligue o celular ao computador com o cabo USB.
  2. No celular: Configurações → Sobre o telefone → toque 7x em 'Número da
     versão' (se ainda não fez) para liberar as Opções do desenvolvedor.
  3. Opções do desenvolvedor → ative 'Depuração USB'.
  4. Conecte o cabo e aceite o diálogo 'Permitir depuração USB'.
Depois rode este script de novo."
fi

if [[ ${#DEVICES[@]} -gt 1 ]]; then
  fail "Mais de um dispositivo conectado (${DEVICES[*]}).
Desconecte os extras ou escolha um com: export ANDROID_SERIAL=<serial>"
fi

SERIAL="${ANDROID_SERIAL:-${DEVICES[0]}}"
echo "==> Instalando $(basename "$APK") em $SERIAL (install -r, dados preservados)"

OUT="$(adb -s "$SERIAL" install -r "$APK" 2>&1)" || true
echo "$OUT"

if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" <<<"$OUT"; then
  cat >&2 <<'EOF'
ERRO: INSTALL_FAILED_UPDATE_INCOMPATIBLE — este APK foi assinado com uma chave
diferente da versão instalada no celular, então o Android recusa atualizar por
cima. Este script NUNCA desinstala automaticamente. Para resolver:

  1. PRIMEIRO exporte o backup: no app, Ajustes → Backup → 'Exportar backup'
     (sem o backup, desinstalar apaga planos, treinos e séries).
  2. Desinstale manualmente: Configurações → Apps → Treinos → Desinstalar.
  3. Rode este script de novo para instalar a versão nova.
  4. No app: Ajustes → Backup → 'Importar backup' e selecione o JSON do passo 1.

Causa definitiva corrigida pela H3-48 (assinatura estável): builds do CI e
locais compartilham a mesma keystore versionada. Depois desta reinstalação
única, os próximos updates instalam por cima sem perder dados.
EOF
  exit 1
fi

if ! grep -q "Success" <<<"$OUT"; then
  fail "A instalação falhou (veja a saída do adb acima).
Se a saída citar espaço insuficiente, libere espaço no celular e rode de novo.
Se citar 'offline', destrave a tela do celular e rode de novo."
fi

echo "==> Instalado com sucesso. Dados locais preservados."
