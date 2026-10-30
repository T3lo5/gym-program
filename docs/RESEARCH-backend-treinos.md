# Pesquisa de viabilidade — App pessoal de treinos (sem back obrigatório)

**Data de acesso das fontes:** 2026-10-30
**Contexto:** App Android pessoal, dados 100% locais. O "backend" é opcional: só é usado quando o usuário quer gerar/atualizar treinos (via LLM). Celular Android (Galaxy) + Galaxy Watch FE. API de LLM: chave da sensenova/StepFun (usuário já tem chave com bom limite gratuito).

---

## Resumo executivo (recomendações finais)

1. **Arquitetura: local-first + "back sob demanda".** O app guarda tudo em banco local (Room/SQLite). O backend é um micro-serviço HTTP de ~1 endpoint (`POST /gerar-treino`) que chama a API da StepFun e devolve o treino em JSON. O app só fala com ele quando você clica em "Atualizar treinos".
2. **Banco de exercícios:** [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) — 800+ exercícios em JSON, **domínio público (Unlicense)**, com schema, instruções passo a passo, músculos, equipamento, nível e imagens. Dá para **bater o JSON no próprio APK** (sem depender de rede para nunca) e o back só precisa usar ele como "catálogo" na hora de gerar treinos.
3. **Onde rodar o back grátis (TOP 2):**
   - **GitHub Codespaces** (120 h de compute + 15 GB-mês grátis/mês no plano Free) — modelo perfeito para o seu caso: você "sobe" o codespace quando quiser atualizar treinos, expõe a porta e **fecha depois** (só paga compute enquanto ativo, dentro da cota). [fonte](https://docs.github.com/en/billing/concepts/product-billing/github-codespaces)
   - **Oracle Cloud Always Free** (2 OCPU Ampere A1 + 12 GB RAM **para sempre**, ou 2 micros AMD; 200 GB disco; 10 TB egress/mês) — para um back 24/7 sem custo, com o cuidado de manter a instância com algum tráfego para não ser rejeitada como idle. [fonte](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
   - Alternativa sem nenhuma infra: rodar o back **no próprio PC via Docker** (a cena "aponto o PC e ele sobe" que você descreveu).
4. **Galaxy Watch FE: dá para conectar, sim.** O caminho é: app no celular (Kotlin + Jetpack Compose) + app no relógio (Compose for Wear OS) usando a **Wear OS Data Layer** para enviar "próximo exercício / timer de descanso / HR" ao relógio. O relógio é Wear OS, então apps de terceiros rodam nele e têm acesso a saúde via Health Services (ex.: frequência cardíaca) — detalhes e incertezas na Frente 5.

**Custo total estimado: R$ 0/mês** (tiers grátis + sua chave StepFun dentro do limite).

---

## Frente 1 — Banco de dados de exercícios gratuito

### 🏆 yuhonas/free-exercise-db (verificado)

- Repo: [github.com/yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) — *"Open Public Domain Exercise Dataset in JSON format, over 800 exercises"*.
- **Licença: Unlicense (domínio público)** — uso comercial/pessoal livre, sem atribuição. [fonte](https://github.com/yuhonas/free-exercise-db/blob/main/LICENSE.md)
- **Formato**: um JSON por exercício em `exercises/` + arquivo combinado pronto em [`dist/exercises.json`](https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json) (verifiquei o download — responde HTTP 200 e é um array JSON). Imagens em `exercises/<Id>/0.jpg`, servidas direto do GitHub: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/...` (o próprio README recomenda esse prefixo). [fonte README](https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/README.md)
- **Schema (verificado em `schema.json`)**:

```json
{
  "id": "Alternate_Hammer_Curl",
  "name": "Alternate Hammer Curl",
  "force": "push",              // push | pull | static | null
  "level": "beginner",          // beginner | intermediate | expert
  "mechanic": "isolation",      // isolation | compound | null
  "equipment": "dumbbell",      // body only, dumbbell, barbell, cable, bands,
                                // kettlebells, machine, medicine ball, foam roll, ...
  "primaryMuscles": ["biceps"],
  "secondaryMuscles": ["forearms"],
  "instructions": ["..."],      // passo a passo em inglês
  "category": "strength",       // strength | cardio | stretching | powerlifting | plyometrics | ...
  "images": ["Alternate_Hammer_Curl/0.jpg"]
}
```

- **Por que é ideal:** o back de IA recebe esse JSON como "catálogo de exercícios válidos" (com id estável) e só pode referenciar ids existentes → o LLM não inventa exercício. O app pode embutir o JSON inteiro no APK (é pequeno, ~1-2 MB) e funcionar 100% offline para consultar nomes/instruções/fotos.
- **Gap:** dados em inglês. Se quiser nomes PT-BR, o próprio back (LLM) pode gerar uma tradução/normalização por lote e você salva no banco local — custo trivial na cota gratuita.

### Outras opções avaliadas

| Opção | Status | Observação |
|---|---|---|
| [wger](https://github.com/wger-project/wger) | Verificado (repo existe) | Tracker completo de treinos/nutrição self-hosted (Django + API). **Muito pesado** para "só gerar treino" — mas se um dia quiser histórico multi-dispositivo no back, é uma opção FLOSS. |
| "Home Lab API" | [INCERTO] | Não encontrei repo ativo (404 nos caminhos provados). Possivelmente renomeado/removido. |
| exrx.net | [INCERTO] | Site protege acesso (403/anti-bot); não consegui confirmar API pública. |
| ExerciseDB/exercisedb | [INCERTO] | Não encontrei repositório ativo com esse nome; provavelmente confusão com wger/free-exercise-db. |

---

## Frente 2 — Onde hospedar/rodar o micro-backend (grátis)

Premissa do projeto: o back **não precisa estar 24/7**. Ideal: sobe sob demanda, atende 1-2 chamadas (gerar treino), pode dormir.

| Opção | Free tier (verificado em 30/10/2026) | Cabe no caso? |
|---|---|---|
| **GitHub Codespaces** | Plano Free pessoal: **120 h de compute/mês + 15 GB-mês** de storage. Excedente: 2-core a $0.18/h. Portas públicas disponíveis. [fonte](https://docs.github.com/en/billing/concepts/product-billing/github-codespaces) | ✅ **Melhor para "ligar/desligar".** Abre um codespace, roda `docker compose up` (ou `node server.js`), o app no Wi-FI/celular acessa a porta pública, você fecha. 120 h/mês = sobra para uso pessoal. |
| **Oracle Cloud Always Free** | **Para sempre:** 2× VM.Standard.E2.1.Micro (AMD, 1/8 OCPU + 1 GB) **ou** Ampere A1 Flex (2 OCPU + 12 GB RAM), 200 GB block volume, 20 GB object storage, 10 TB egress/mês, load balancer free. Exige cartão no signup (verificação, não cobrança). **Cuidado:** instâncias idle (CPU/rede <20% por 7 dias) podem ser recolhidas. [fonte](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) | ✅ **Melhor para 24/7 grátis.** Dá até para rodar o app em modo desktop + back. Descontos: signup é burocrático (região com capacidade) e o tráfego do celular precisa de internet (não é LAN). |
| Render Free | Docs de instâncias free existem ([render.com/docs/free](https://render.com/docs/free)), mas **não consegui extrair os números atuais** da página (conteúdo renderizado via JS). Historicamente: 512 MB RAM, 750 h/mês, spin-down após 15 min sem tráfego. [INCERTO — confirmar na doc] | ⚠️ Provavelmente serve, mas o spin-down de 15 min dá ~30-60 s de "cold start" a cada uso. |
| Fly.io | **Sem free tier real mais**: billing por segundo, shared-cpu-1x (256 MB) = $2.19/mês se rodar 24/7; máquina parada = $0.15/mês por GB de rootfs. [fonte](https://fly.io/pricing/) | ❌ Paga-se. Só se quiser pagar ~R$ 10/mês sem pensar. |
| Koyeb | Free tier de compute **acabou**: plano Pro = $29/mês + $10 de compute. Fica Postgres serverless com 5 h grátis/mês. [fonte](https://koyeb.com/pricing) | ❌ |
| Hugging Face Spaces | Page de preços não detalha o free de Spaces na parte visível; PRO ($9/mês) lista "Host ZeroGPU, Gradio & Docker Spaces" como benefício → implica que **Spaces básicos (CPU) continuam gratuitos** no plano free, mas [INCERTO — confirmar] e Spaces públicos ficam "dormindo" (CPU basic tem limit de uso). | ⚠️ Possível, mas menos controle (app roda em container público, sem auth forte). |
| Vercel / Cloudflare Workers / Deno Deploy | [INCERTO — não verificado nesta sessão] | ⚠️ Ótimos para endpoint **stateless** "gerar treino" (sem banco no servidor): CF Workers tem free tier generoso de requests; Vercel hobby tem banda limitada. Como o back só precisa de 1 endpoint sem estado, **serverless é tecnicamente a opção mais simples** — mas exige publicar em repositório e a chave StepFun fica no servidor (ok, pois é a sua chave). |
| PC do usuário (a sua ideia) | — | ✅ Zero custo, zero cadastro. O app no celular faz HTTP para `192.168.x.x:3000` da LAN. Detalhes na Frente 4. |

### Recomendação

- **Fase 1 (agora):** back roda **no seu PC** (Docker Compose com 1 container Node) — é exatamente o fluxo "clicar no app → apontar o back → ele sobe".
- **Fase 2 (opcional):** **Oracle Always Free A1** se você quiser o back 24/7 acessível de qualquer rede, ou **Codespaces** se preferir "sobe quando quero".
- **Fase 3 (opcional):** migrar o endpoint para **Cloudflare Workers/Vercel** se quiser zerar até o passo de subir o back.

---

## Frente 3 — API sensenova (documentação oficial verificada)

**Fonte:** [platform.sensenova.ai/docs](https://platform.sensenova.ai/docs) (bundle JS da página Next.js, acessado em 30/10/2026)

### ✅ Especificações técnicas confirmadas

| Item | Valor |
|---|---|
| **Base URL** | `https://token.sensenova.ai/v1` |
| **Auth** | `Authorization: Bearer $SENSENOVA_API_KEY` |
| **Endpoint OpenAI-compatible** | `POST https://token.sensenova.ai/v1/chat/completions` |
| **Endpoint Anthropic-compatible** | `POST https://token.sensenova.ai/v1/messages` |
| **Endpoint Responses API** | `POST https://token.sensenova.ai/v1/responses` |
| **Modelo principal** | `sensenova-6.8-flash-lite` |
| **Context window** | 256.000 tokens |
| **Max output** | 65.536 tokens |
| **Modalidades** | Input: text, image | Output: text |
| **Recursos** | Reasoning mode (4 níveis + off), function calling, structured output (JSON mode), streaming (SSE) |

### 📋 Modelos disponíveis (do `GET /v1/models`)

O endpoint lista modelos com campos como `id`, `name`, `context_length`, `max_output_length`, `input_modalities`, `output_modalities`, `pricing`, `supported_features` (ex.: `function_calls`, `json_mode`, `reasoning`). O modelo que aparece na doc principal é **`sensenova-6.8-flash-lite`** (multimodal, reasoning, function calls, json_mode).

### 🔑 Compatibilidade SDKs

A API é **totalmente compatível** com:
- **OpenAI SDK** (Chat Completions, Responses API) — base URL: `https://token.sensenova.ai/v1`
- **Anthropic SDK** (Messages API) — base URL: `https://token.sensenova.ai` (sem `/v1` no final pro SDK Anthropic)
- **Codex, Cline, Continue, OpenCode, OpenClaw, Hermes, Claude Code** — todos têm presets/docs pro sensenova

### ⚙️ Parâmetros relevantes pro seu caso

```json
// Exemplo mínimo pro back gerar treino (JSON mode)
POST https://token.sensenova.ai/v1/chat/completions
Authorization: Bearer $SENSENOVA_API_KEY
Content-Type: application/json

{
  "model": "sensenova-6.8-flash-lite",
  "messages": [
    {"role": "system", "content": "Você é um gerador de treinos..."},
    {"role": "user", "content": "Gere um treino de 4 dias para hipertrofia..."}
  ],
  "response_format": {"type": "json_object"},
  "max_tokens": 4096,
  "temperature": 0.3,
  "reasoning": {"effort": "none"}  // desliga reasoning pra ser mais rápido/barato
}
```

> **Nota importante da doc:** *"Enabling `thinking.type=enabled` and `response_format.type=json_object` at the same time is not recommended"* — então para JSON estruturado, **desligue reasoning** (`"reasoning": {"effort": "none"}` ou `"thinking": "disabled"`).

### 💰 Preços / cota gratuita

A documentação **não expõe tabela de preços pública** no bundle acessado (o console `platform.sensenova.ai/console/keys` mostra seu saldo/cota). Como você já tem a chave e afirma ter "bom limite", a arquitetura segue **agnóstica**: o back usa a chave como variável de ambiente e, se um dia trocar de provedor, é só mudar `base_url` e `model_id`.

**Consumo estimado por geração de treino:**
- Input: ~500-800 tokens (prompt + catálogo de exercícios resumido)
- Output: ~1.500-2.500 tokens (treino completo em JSON)
- Total: ~2-3k tokens/call — **muito baixo** para qualquer cota gratuita razoável.

### 🛡️ Fallback zero-código

Se a sensenova mudar regras/limites, o back aponta para **qualquer API OpenAI-compatible** (OpenRouter free tier, DeepSeek, Groq, etc.) trocando apenas:
```env
SENSENOVA_API_KEY=sk-xxx
OPENAI_BASE_URL=https://token.sensenova.ai/v1
MODEL_ID=sensenova-6.8-flash-lite
```
→ O app não muda nada.

---

## Frente 4 — Android falando com o backend na LAN (PC)

### O que o app precisa (docs oficiais Android)

1. **Cleartext HTTP** para o IP da LAN. Por padrão o Android **bloqueia** tráfego não-TLS.
   - Solução A (simples, uso pessoal): `android:usesCleartextTraffic="true"` no `AndroidManifest`.
   - Solução B (mais limpa): `res/xml/network_security_config.xml` com `cleartextTrafficPermitted="true"` restrito a `192.168.*`, `10.*` e `*.local`. [doc oficial](https://developer.android.com/privacy-and-security/security-config)
   - Solução C (mais segura, um pouco mais trabalho): emitir um certificado auto-assinado no back (ex.: mkcert) e adicionar à trust store do app. Para uso pessoal na sua casa, A já resolve.
2. **Android 14+ exige permissão de runtime `ACCESS_LOCAL_NETWORK`** quando o app fala com *outros* dispositivos na rede local. É a mesma UX das permissões de localização: pop-up único. [mudança de comportamento Android 14](https://developer.android.com/about/versions/14/behavior-changes-14) / [permissão](https://developer.android.com/reference/android/Manifest.permission#ACCESS_LOCAL_NETWORK)
3. **Descoberta automática (opcional, conforto):** mDNS/NSD — o back anuncia `_treinos._tcp` e o app varre a rede sem você digitar IP. [doc NSD](https://developer.android.com/guide/topics/connectivity/mdns). Alternativa zero-libs: digitar `192.168.0.10:3000` uma vez e salvar.
4. **Em desenvolvimento:** `adb reverse tcp:3000 tcp:3000` — o app chama `localhost:3000` e o adb encaminha para o PC, sem depender de LAN. [doc adb](https://developer.android.com/tools/adb)

### Limitações reais (leia antes de achar que é mágica)

- Celular e PC precisam estar na **mesma LAN/Wi-Fi**. Fora de casa (4G), a LAN não existe → esse modo não funciona; para uso fora, o back precisa estar num lugar com IP público (Frente 2).
- Roteadores com AP isolation (Wi-Fi de guest) bloqueiam tráfego dispositivo-a-dispositivo.
- Se o PC hibernar/dormir, o back cai (óbvio, mas vale registrar).
- **Alternativa sem PC:** Termux no próprio celular rodando o back — viável tecnicamente, mas para um endpoint só não compensa; o Codespaces/Oracle cobre melhor.

### O fluxo "aponto o back" que você descreveu (fact check: funciona bem)

1. App detecta "back não encontrado" → tela: "Suba o back no PC (Docker) e informe o endereço".
2. Você: `docker compose up -d` no PC → app pinga `/health` → conecta.
3. Clique em "Atualizar treinos" → `POST /gerar-treino` com contexto (músculos-alvo, equipamentos disponíveis, últimos treinos do banco local) → back chama StepFun → devolve JSON do treino → app salva em Room e **sugere o novo microciclo**.

---

## Frente 5 — Galaxy Watch FE (Wear OS)

### O relógio

- Galaxy Watch FE (2023) roda **Wear OS** (4 no lançamento) com chipset Exynos W920. [INCERTO — não consegui carregar a página de spec oficial nesta sessão (Samsung/GSMArena sem o SKU direto); confirme em Configurações → Sobre no seu relógio] — mas como é um Wear OS, **toda a stack abaixo se aplica**.
- Sensores típicos da linha: frequência cardíaca óptica, SpO2, acelerômetro/gyro.

### Como "conectar" relógio e app (o caminho padrão e suportado)

1. **2 módulos no mesmo projeto:** app celular (Jetpack Compose) + app de relógio (**Compose for Wear OS**). Nota: **Flutter não suporta Wear OS** — Kotlin nativo é a stack.
2. **Wear OS Data Layer** (APIs de sincronização celular↔relógio via Bluetooth):
   - `DataItemClient`/`DataMessageClient` para o celular **empurrar** para o relógio: "próximo exercício: Agachamento 4×8 — descanso 90s".
   - `WearableMessageClient` para eventos pontuais (troca de set, fim de descanso, "boa").
   - O relógio funciona **autônomo** (a fila é entregue quando o BT reconecta) e roda mesmo sem o celular por perto, desde que a sessão de treino tenha sido enviada. [doc oficial: Sync data](https://developer.android.com/training/wearables/data/sync)
3. **Health Services no Wear OS** — o Google documenta acesso de **apps de terceiros** a dados de saúde no relógio (ex.: ler frequência cardíaca durante a atividade). [doc: Health Services on Wear OS](https://developer.android.com/health-and-fitness/health-services) + [permissões](https://developer.android.com/health-and-fitness/health-services/permissions)
   - [INCERTO] Especificamente em Galaxy Watch: dados do **Samsung Health** (HR coletada pela Samsung) sincronizam para o **Health Connect do celular**; se o seu app no celular ler `HEART_RATE` do Health Connect, ele pega o HR do relógio **sem depender** do app do relógio. É a via mais confiável.
   - [INCERTO] HR em tempo real direto do relógio para app de 3º via via Health Services: o doc existe, mas em dispositivos Samsung a Samsung mantém restrições próprias em alguns fluxos. **Recomendo tratar HR como "nice-to-have"**, não como requisito.
4. **Modo recomendado (simples e robusto):**
   - Celular: motor do treino (timer de sets/descansos, rep counter, som de vibração).
   - Relógio: **display + timer** — mostra o exercício atual, séries restantes e conta o descanso. HR opcional (via Health Connect no celular, exibida na tela do relógio via Data Layer).
   - Isso elimina 100% da incerteza do item 3 e ainda é o que a maioria dos apps de treino faz.

### O que **não** dá para fazer (para não sonhar alto)

- Relógio **sem** o celular: o Wear OS não permite Wi-Fi/BT server independente para receber HTTP do seu back. Toda geração de treino continua no app/celular (+ back); o relógio só consome.
- Publicar na Play Store do relógio: apps Wear OS são instalados como companion — para uso pessoal, o `aapt`/Play Store não importa, você instala o APK do relógio direto (ou pela Play Store lateral do app).

---

## O que precisamos (checklist concreto)

**Já resolvido (R$ 0):**
- [x] Banco de exercícios: `free-exercise-db` (domínio público, JSON + imagens, 800+ exercícios)
- [x] Chave de LLM (sensenova/StepFun) — você já tem
- [x] Modelo de hospedagem grátis: LAN → Codespaces/Oracle Always Free

**Ações para você (requisitos externos):**
- [x] **API sensenova confirmada:** base URL `https://token.sensenova.ai/v1`, modelo `sensenova-6.8-flash-lite`, auth Bearer, JSON mode compatível (Frente 3 — doc oficial verificada)
- [ ] (Se Oracle Always Free) criar conta no [oracle.com/cloud/free](https://www.oracle.com/cloud/free/) — exige cartão para verificação
- [ ] (Se Codespaces) nada — você já tem GitHub
- [ ] Confirmar versão do Wear OS no seu relógio (Config → Sobre)

**Ferramentas de desenvolvimento (uma vez):**
- [ ] Android Studio + JDK 17
- [ ] Docker Desktop (no PC, para rodar o back)
- [ ] (Opcional) `mkcert` para TLS local

**Stack técnica recomendada:**
- [ ] App: **Kotlin + Jetpack Compose**, Room (banco local), Retrofit/Ktor client, Wear OS Data Layer
- [ ] Back: **Node.js (Fastify) ou Python (FastAPI)** + 1 endpoint `POST /gerar-treino` + `GET /health`, `Dockerfile` + `docker-compose.yml`
- [ ] Contratos: schema de treino (JSON) compartilhado entre app e back (ex.: `treino.json` com `id_exercicio` referenciando o catálogo free-exercise-db)

---

## Riscos / incertezas assumidas

| Risco | Mitigação |
|---|---|
| ~~[INCERTO] Detalhes de preço/limite StepFun não confirmáveis daqui~~ **RESOLVIDO** | Doc oficial verificada: base URL, modelo, endpoints, parâmetros |
| [INCERTO] Free tier do Render atual (números não extraídos) | Ir de Codespaces/Oracle, já verificados |
| [INCERTO] HR em tempo real do Galaxy Watch para app de 3º via | HR via Health Connect no celular (via robusta) |
| Instância Oracle "idle" pode ser recolhida | Cron job de tráfego mínimo / usar Codespaces no lugar |
| LAN diferente (fora de casa) | Back em local com IP público (Frente 2, fase 2) |
