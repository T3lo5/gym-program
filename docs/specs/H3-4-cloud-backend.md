# H3-4 — Backend na nuvem grátis com token

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-4` |
| MoSCoW | `W` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `pendente` |
| Depende de | H1-4 (backup local antes de expor dados) |

## Objetivo

Permitir usar o backend fora de casa (Oracle Always Free/Codespaces +
Cloudflare Tunnel) com um token explícito, sem transformar o `deviceId` em
autenticação nem expor os dados a quem descobrir a URL.

## Fora de escopo

- Contas, login, OAuth, multiusuário ou banco por usuário.
- Painel de administração, cobrança ou rate limit sofisticado.
- TLS dentro do app ou certificado próprio.
- Automação da criação da conta Oracle/Cloudflare.
- Migrar automaticamente dados locais para a nuvem.
- Alta disponibilidade, backups do servidor ou observabilidade.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/auth.py` | **Novo.** `is_authorized(...)` em tempo constante |
| `back/app/main.py` | Middleware Bearer em `/v1/*`; `/health` sem dados sensíveis |
| `back/tests/test_auth.py` | **Novo.** Token obrigatório, correto, errado e vazio |
| `back/.env.example` | `API_TOKEN=` documentado |
| `docker-compose.yml` | Passar `API_TOKEN` e `PUBLIC_BASE_URL` ao container |
| `docs/DEPLOY-CLOUD.md` | **Novo.** Oracle/Codespaces + Cloudflare Tunnel |
| `README.md` | Link do deploy e aviso de segurança |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | `useRemoteBackend`, `remoteBackendUrl`, `apiToken`, `effectiveBackendUrl` |
| `app/src/main/java/com/treinos/app/data/remote/BackendAuth.kt` | **Novo.** Header, validação de URL e seleção (puro) |
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | `tokenProvider` + header `Authorization` em todas as chamadas |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | Passar URL efetiva e token ao cliente |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Estados do token/toggle, teste de conexão e erros 401 |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Repassar estados/callbacks |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Switch remoto, URL, token (password) e status |
| `app/src/test/java/com/treinos/app/data/remote/BackendAuthTest.kt` | **Novo.** Header e URL |
| `app/src/test/java/com/treinos/app/data/remote/BackendClientAuthTest.kt` | **Novo.** Header real com MockWebServer |
| `app/src/test/java/com/treinos/app/data/prefs/BackendSelectionTest.kt` | **Novo.** URL efetiva (puro) |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Variáveis de ambiente

```bash
# back/.env
API_TOKEN=cole-um-token-aleatorio-de-32-caracteres-ou-mais
PUBLIC_BASE_URL=https://treinos.exemplo.workers.dev
```

- `API_TOKEN` vazio/ausente → autenticação **desligada** (uso local).
- `API_TOKEN` definido → todo `/v1/*` exige
  `Authorization: Bearer <API_TOKEN>`.
- `/health` é público, mas responde apenas:
  ```json
  { "status": "healthy", "timestamp": "...", "model": "...", "catalogSize": 876 }
  ```
  **Não** incluir `database` nem caminho de arquivo.
- Token errado/ausente → `401 {"detail": "Unauthorized"}`.
- Comparação com `secrets.compare_digest` (tempo constante).
- `/docs` e `/openapi.json` também ficam atrás do middleware quando o token
  estiver ativo (evita descoberta), ou são desabilitados em produção:
  `FastAPI(docs_url=None, redoc_url=None, openapi_url=None)` quando
  `API_TOKEN` existir.

### App

```kotlin
// BackendAuth.kt
fun authHeader(token: String?): String? =
    token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

fun validateRemoteUrl(url: String): String?   // null = ok; string = erro
fun effectiveBackendUrl(
    useRemote: Boolean,
    remoteUrl: String,
    localUrl: String,
): String
```

Regras:

- `useRemoteBackend = true` exige `https://`; `http://` é rejeitado com
  “Backend remoto precisa usar HTTPS”.
- `useRemoteBackend = false` usa o `backendUrl` local atual (inclusive
  `http://10.0.2.2:3000` e IP da LAN).
- Token é enviado em **todas** as chamadas (`/health`, `/v1/*`), inclusive no
  catálogo de H1-3.
- Token em branco → não envia header.
- 401 no app → mensagem “Token inválido ou ausente. Confira em Ajustes.”, sem
  apagar dados locais.
- Token fica em `SharedPreferences` privado (`treinos_prefs`); **não** logar,
  não sincronizar, não incluir no backup JSON.
- O `deviceId` continua sendo identificador de restore, não credencial.
- Timeout do cliente permanece o atual (15 s connect / 90 s read).

## Regras e casos de borda

1. Backend local sem `API_TOKEN` → app sem token continua funcionando.
2. Backend público sem `API_TOKEN` → não subir; o deploy deve falhar com aviso
   se `PUBLIC_BASE_URL` começar com `https://` e `API_TOKEN` estiver vazio.
3. Token com espaços nas pontas → `trim` antes de comparar.
4. Header `Bearer` sem valor ou com esquema diferente → 401.
5. URL remota com barra final → normalizar removendo `/`.
6. URL remota sem `https` → não salvar; mostrar erro no campo.
7. Token errado → 401; a UI não limpa URL nem dados.
8. `GET /v1/sync/download?deviceId=...` sem token → 401 (não vazar snapshot).
9. `/health` sem token → 200 e sem caminho do banco.
10. Cloudflare Tunnel cai → app mostra erro de rede e mantém modo local.
11. Usuário desliga “Usar backend remoto” → volta para a URL local sem apagar o
    token salvo.
12. Token no backup JSON → proibido; o export não inclui `AppPreferences`.
13. Logs do backend não imprimem o header `Authorization`.
14. `PUBLIC_BASE_URL` é só informativo; não é usado para autenticar.

## Critérios de aceite

1. **Dado** `API_TOKEN=abc` e uma chamada `POST /v1/generate-workout` sem header,
   **então** a resposta é 401.
2. **Dado** o mesmo backend, **quando** envia
   `Authorization: Bearer abc`, **então** a resposta é 200 (ou 422 de validação,
   nunca 401).
3. **Dado** `Authorization: Bearer errado`, **então** 401.
4. **Dado** `GET /health` sem token, **então** 200 e o JSON não contém
   `database`.
5. **Dado** o app com token configurado, **quando** testa a conexão, **então** o
   header aparece no MockWebServer.
6. **Dado** o app sem token, **então** nenhum header `Authorization` é enviado.
7. **Dado** “Usar backend remoto” ligado e URL `http://...`, **então** a UI
   bloqueia com erro de HTTPS.
8. **Dado** token errado no app, **então** vê a mensagem de 401 e os dados
   locais continuam.
9. **Dado** `API_TOKEN` vazio no `.env`, **então** o backend local continua
   acessível sem header.
10. **Dado** `docs/DEPLOY-CLOUD.md`, **então** o passo a passo cobre Oracle,
    Docker, Cloudflare Tunnel e teste com `curl`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_auth.py` | `test_health_publico_sem_token`, `test_health_nao_expoe_database`, `test_v1_exige_token`, `test_token_correto_libera`, `test_token_errado_401`, `test_token_com_espacos_e_normalizado`, `test_token_vazio_desabilita_auth`, `test_sync_download_sem_token_401` |
| `app/src/test/java/com/treinos/app/data/remote/BackendAuthTest.kt` | `auth_header_com_token`, `auth_header_sem_token`, `auth_header_normaliza_espacos`, `url_remota_exige_https`, `url_remota_com_barra_e_normalizada` |
| `app/src/test/java/com/treinos/app/data/prefs/BackendSelectionTest.kt` | `usa_remota_quando_ligada`, `usa_local_quando_desligada`, `remota_vazia_cai_para_local` |
| `app/src/test/java/com/treinos/app/data/remote/BackendClientAuthTest.kt` | `envia_bearer_quando_token_existe`, `nao_envia_header_sem_token` |

O teste com MockWebServer usa `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`.

## Verificação manual

1. Suba o backend local sem `API_TOKEN`; confirme que o app funciona.
2. Defina `API_TOKEN=teste123` e reinicie; confirme 401 sem header:
   ```bash
   curl -i -X POST http://localhost:3000/v1/generate-workout \
     -H 'Content-Type: application/json' -d '{}'
   ```
3. Repita com `-H 'Authorization: Bearer teste123'` e confirme que não é 401.
4. Confira `curl http://localhost:3000/health` sem caminho de banco.
5. No app, ligue “Usar backend remoto”, tente `http://` e confirme o bloqueio.
6. Configure uma URL `https://` do túnel + token e teste a conexão.
7. Gere um treino e sincronize pelo backend remoto.
8. Desligue o túnel e confirme a mensagem de erro e o modo local intacto.
9. Confira que o backup JSON exportado não contém o token.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Expor dados pessoais:** token é obrigatório para qualquer URL pública; sem
  ele, o backend não deve ser publicado.
- **Vazamento do token:** não logar, não incluir no backup, não commitar
  `back/.env`; rotacionar o token se exposto.
- **Cloudflare Tunnel gratuito:** a URL muda se o túnel for recriado; o usuário
  precisa atualizar a URL no app.
- **SenseNova:** a chave continua só no backend; um token vazado ainda pode
  consumir sua cota — rotacione a chave em caso de abuso.
- **SQLite:** um escritor só; uso pessoal, sem concorrência alta.
- **Rollback:** remover o middleware, o toggle e o token; o app volta ao
  backend local. Nenhum dado do usuário é apagado.
