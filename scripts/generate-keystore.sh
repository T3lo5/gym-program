#!/bin/bash
# Gera um keystore fixo para assinar todos os builds do app Treinos.
# Rode este script UMA VEZ no seu PC, commite o arquivo gerado e use as
# mesmas senhas como secrets no GitHub (RELEASE_KEYSTORE_PASSWORD, etc).

KEYSTORE="app/debug.keystore"
ALIAS="treinos-debug"
PASSWORD="treinos123"
VALIDITY=10000

if [ -f "$KEYSTORE" ]; then
  echo "Keystore ja existe em $KEYSTORE. Nao vou sobrescrever."
  exit 1
fi

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=Treinos Debug, OU=App, O=Treinos, L=Sao Paulo, S=SP, C=BR"

echo ""
echo "============================================================"
echo "Keystore gerado em: $KEYSTORE"
echo "Alias: $ALIAS"
echo "Store password: $PASSWORD"
echo "Key password: $PASSWORD"
echo ""
echo "AGORA ADICIONE ESTES SECRETS NO GITHUB (Settings > Secrets and variables > Actions):"
echo "  RELEASE_KEYSTORE_PASSWORD = $PASSWORD"
echo "  RELEASE_KEY_PASSWORD      = $PASSWORD"
echo "  RELEASE_KEY_ALIAS         = $ALIAS"
echo "============================================================"
