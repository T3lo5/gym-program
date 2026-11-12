#!/usr/bin/env bash
# Roda o servidor de memória SEM Docker, em qualquer máquina que tenha Node.
# Usado quando o outro computador não roda Docker (ou quando se quer um
# servidor por projeto sem container).
#
# Uso:
#   ./tools/mcp-memory/run-native.sh                    # usa ./.mcp-memory/memory.jsonl
#   MEMORY_FILE_PATH=/caminho/shared/memory.jsonl \
#     ./tools/mcp-memory/run-native.sh --host 0.0.0.0
#
# Ouvindo em 127.0.0.1:8765 por padrão (só localhost, como o compose).
set -euo pipefail

MEMORY_FILE_PATH="${MEMORY_FILE_PATH:-$(pwd)/.mcp-memory/memory.jsonl}"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-8765}"
mkdir -p "$(dirname "$MEMORY_FILE_PATH")"

# Versões fixas, mesmas do Dockerfile.
SERVER_VER="@modelcontextprotocol/server-memory@2.1.0"
GATEWAY_VER="supergateway@4.2.1"

# Resolução de binários, em ordem de preferência:
#   1. já instalado globalmente na máquina
#   2. em ./.node-tools (instalação local ao projeto, não polui o global)
#   3. npx (baixa sob demanda; lento no primeiro uso)
TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)/.node-tools"
NPM_CACHE="$(cd "$(dirname "$0")" && pwd)/.npm-cache"

ensure_bin() {
  local bin="$1"
  if command -v "$bin" >/dev/null 2>&1; then
    return 0
  fi
  if [ -x "$TOOLS_DIR/.bin/$bin" ]; then
    PATH="$TOOLS_DIR/.bin:$PATH"
    export PATH
    return 0
  fi
  echo "Instalando $bin em $TOOLS_DIR ..." >&2
  mkdir -p "$TOOLS_DIR" "$NPM_CACHE"
  npm install --prefix "$TOOLS_DIR" --no-fund --no-audit \
    --cache "$NPM_CACHE" "$SERVER_VER" "$GATEWAY_VER" >/dev/null
  PATH="$TOOLS_DIR/.bin:$PATH"
  export PATH
}

ensure_bin mcp-server-memory
ensure_bin supergateway

exec supergateway \
  --stdio "mcp-server-memory" \
  --outputTransport streamableHttp \
  --port "$PORT" \
  --host "$HOST" \
  --streamableHttpPath /mcp \
  --healthEndpoint /healthz \
  --logLevel info