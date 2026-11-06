# Servidor de memória (knowledge graph) do projeto, exposto por Streamable
# HTTP para que todos os agentes (OpenCode, Claude Code, VS Code, Cursor…)
# compartilhem o MESMO grafo, e não um arquivo por agente.
#
# Versões fixas de propósito: o gate deste repositório exige dependência
# travada. Para subir: `docker compose up -d mcp-memory`.
FROM node:24-alpine

# server-memory >= 2 grava JSONL e usa lock de arquivo por mutação (#4797),
# então processos concorrentes compartilhando o mesmo arquivo não perdem
# escrita. O supergateway só converte stdio -> Streamable HTTP.
RUN npm install -g --no-fund --no-audit \
      @modelcontextprotocol/server-memory@2.1.0 \
      supergateway@4.2.1

ENV MEMORY_FILE_PATH=/data/memory.jsonl \
    NODE_ENV=production

EXPOSE 8765

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8765/healthz >/dev/null || exit 1

# Sem `--stateful`: no modo stateless o gateway cria um processo do servidor
# por requisição, sempre relendo o JSONL sob lock. Sessões stateful manteriam
# cópias do grafo em memória por cliente, que é justamente o que queremos
# evitar com vários agentes.
CMD ["supergateway", \
     "--stdio", "mcp-server-memory", \
     "--outputTransport", "streamableHttp", \
     "--port", "8765", \
     "--host", "0.0.0.0", \
     "--streamableHttpPath", "/mcp", \
     "--healthEndpoint", "/healthz", \
     "--logLevel", "info"]
