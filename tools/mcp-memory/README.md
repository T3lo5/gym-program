# Memória compartilhada dos agentes (MCP `memory`)

Um único servidor de memória (knowledge graph) roda em container e expõe
**Streamable HTTP** em `http://127.0.0.1:8765/mcp`. Todos os agentes que abrem
este projeto leem e escrevem o **mesmo** grafo, persistido em
`.mcp-memory/memory.jsonl` (ignorado pelo git).

## Por que um servidor HTTP e não `npx` por agente

O `@modelcontextprotocol/server-memory` só fala stdio. Se cada agente subisse
o seu próprio processo apontando para o mesmo `.json`:

- cada processo carrega o grafo inteiro na memória no boot e regrava o arquivo
  inteiro ao mutar, então o último a escrever apagaria o trabalho dos outros;
- não haveria um "lugar" único para inspecionar o que os agentes aprenderam.

Aqui o `supergateway` converte stdio → Streamable HTTP. O `server-memory` v2
usa **lock de arquivo por mutação** e relê o JSONL sob o lock, então vários
processos concorrentes não perdem escrita. O modo é stateless de propósito:
sessões stateful manteriam cópias do grafo em memória por cliente.

## Subir / parar / inspecionar

```bash
docker compose up -d mcp-memory      # sobe (e builda na primeira vez)
docker compose ps mcp-memory         # deve mostrar (healthy)
docker compose logs -f mcp-memory    # log do gateway
docker compose down                  # derruba backend + memória
```

O container usa `restart: unless-stopped`, então volta com o Docker.

Estado e reset:

```bash
cat .mcp-memory/memory.jsonl         # grafo legível (1 registro JSON por linha)
rm .mcp-memory/memory.jsonl          # reset — o servidor recria vazio
```

O arquivo é gravado com o UID do host (`user:` no compose). Se
`id -u`/`id -g` não forem `1000`, exporte `MCP_MEMORY_UID`/`MCP_MEMORY_GID`
antes do `docker compose up`.

## Configuração por ferramenta

| Ferramenta | Arquivo | Formato |
|---|---|---|
| OpenCode | `opencode.json` (raiz) | `mcp.servers.memory` (remote) |
| Claude Code | `.mcp.json` (raiz) | `mcpServers.memory` (`"type": "http"`) |
| VS Code / Copilot | `.vscode/mcp.json` | `servers.memory` (`"type": "http"`) |
| Cursor | `~/.cursor/mcp.json` | `mcpServers.memory` com `"url"` |
| Gemini CLI | `~/.gemini/settings.json` | `mcpServers.memory` com `"httpUrl"` |

O alcance é o projeto: outro repositório não deve herdar esta memória. Para
usar em todos os projetos, registre o mesmo servidor no config **global** da
ferramenta e aponte `MEMORY_FILE_PATH` para um diretório compartilhado.

Verificação rápida sem agente:

```bash
curl -sS -X POST http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | head -c 300
```

## Ferramentas expostas

`create_entities`, `create_relations`, `add_observations`, `delete_entities`,
`delete_observations`, `delete_relations`, `search_nodes`, `read_graph`,
`open_nodes` (o agente prefixa com o nome do servidor: `memory_search_nodes`
no OpenCode, `mcp__memory__search_nodes` no Claude Code).

Regras de uso ficam no `AGENTS.md` (§ Protocolo de memória). Não grave
segredos, tokens, dados pessoais ou saída de build nesse arquivo.
