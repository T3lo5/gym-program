# H3-40 — Geração assíncrona: pedir agora, receber pronto

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-40` |
| MoSCoW | `M` |
| Spec aprovada em | 2026-11-10 (aprovada pelo dono) |
| Implementação | `implementado` (gate verde em 2026-11-10) |

**Origem:** pedido do dono em 2026-11-10 — a IA quase sempre estoura o
orçamento síncrono e o usuário recebe o fallback local; quer pedir o treino,
sair do app e receber o plano pronto ao voltar, sem backend sempre rodando.

**Depende de:** [H3-22](H3-22-robustez-geracao-ia.md) (robustez/parse/retry) e
[H3-30](H3-30-geracao-sem-atrito.md) (config persistida, motivo do fallback).
**Substitui parcialmente:** o overlay bloqueante da H3-30 e a invariante
"pior caso < 90 s do app" da H3-22/H3-25 (o POST deixa de esperar a IA).

## Objetivo

O usuário pede o treino e segue a vida; minutos depois volta ao app, toca
"Atualizar" e recebe o plano da IA pronto — sem tela de espera e sem o
fallback estrutural por timeout, preservando o offline-first (tudo continua
salvo no Room; o backend segue sob demanda, só para geração/sync).

## Contexto (estado atual verificado)

- `POST /v1/generate-workout` é síncrono (`back/app/main.py:1135`): tenta a
  SenseNova com orçamento total de 80 s (`AI_TIMEOUT_TOTAL_SECONDS`,
  `main.py:1201`) e cai no fallback local; o app espera com read timeout de
  90 s (`BackendClient.kt:125`) atrás de um overlay bloqueante
  (`GenerateScreen.kt:833`, H3-30).
- Medição de 2026-11-10: request amplo leva ~72 s+ contra o orçamento; o dono
  relata que na prática quase sempre recebe o fallback (`reason=timeout`).
- A chave SenseNova, o catálogo, os prompts e o fallback local moram no
  backend (princípio 4 do roadmap) — o app **não** chama a IA direto.
- Não há WorkManager/foreground service de processamento no app; os únicos
  services são listeners do Wear Data Layer. Esta spec não adiciona nenhum.
- O backend já precisa estar de pé no momento da geração; a mudança é que ele
  precisa continuar de pé *durante* o job (minutos), não 24/7.

## Fora de escopo

- WorkManager, polling periódico em background ou notificação de conclusão no
  app — o poll é só sob demanda (Atualizar / volta ao foreground / abertura).
- Fila ou worker externo (Redis/Celery), multi-usuário, autenticação.
- Chamar a SenseNova direto do app (a chave continua só no backend).
- Streaming/SSE de progresso por estágios.
- Limpeza/expiração de jobs antigos e listagem de jobs por device.
- Mudar `/v1/review-plan` e `/v1/sync` (continuam síncronos).
- Retry automático de job `failed` — o usuário decide ("Tentar de novo").
- Compatibilidade app novo ↔ backend antigo: app e backend shipam juntos;
  resposta fora do contrato vira erro acionável (H3-29), sem shim.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/main.py` | `POST /v1/generate-workout` vira submit: valida, cria job, dispara `asyncio.create_task` e responde 202. Lógica atual (loop de IA + retry + fallback + `enrich_plan` + `save_generation`) extraída para `async def run_generation_job(job_id, request)`. Novos `GET /v1/generate-workout/{job_id}` e `POST /v1/generate-workout/{job_id}/cancel`. Registry `_job_tasks: dict[str, asyncio.Task]` para cancelar. `lifespan`: startup marca jobs interrompidos como `failed`; shutdown cancela tasks em voo |
| `back/app/database.py` | Tabela aditiva `generation_jobs` (`CREATE TABLE IF NOT EXISTS` no `init_db`, mesmo padrão das demais) + helpers `create_generation_job`, `get_generation_job`, `finish_generation_job` (done/failed/cancelled com result/error), `mark_interrupted_jobs` |
| `back/.env.example` | `AI_TIMEOUT_TOTAL_SECONDS` documentado com default 600 (orçamento do job, não mais da resposta síncrona) |
| `back/tests/test_generation_jobs.py` (novo) | Testes do submit/status/cancel/sweep/orçamento |
| `back/tests/test_ai_robustez.py` | Invariante "pior caso < 90 s" removida: POST responde imediato; testes de orçamento passam a dirigir `run_generation_job` com clock falso |
| `back/tests/test_fallback_diagnostics.py` | Testes do endpoint passam a dirigir o runner (mesma lógica de fallback, entorno novo) |
| `back/tests/test_contract_v2.py` | Fixtures novos do contrato de job validados no backend |
| `contracts/generate_job_ack.json`, `contracts/generate_job_status.json` (novos) | Fixtures únicos do contrato, validados nos dois lados (padrão H3-1) |
| `app/src/main/java/com/treinos/app/data/remote/BackendApi.kt` | `generatePlan` → `submitGeneration(request): GenerateJobAck`; novos `generationStatus(jobId): GenerateJobStatus` e `cancelGeneration(jobId): GenerateJobStatus` |
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | Implementa os três endpoints; read timeout de 90 s **mantido** (POST/GET são rápidos; nada espera a IA) |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GenerateJobAck` e `GenerateJobStatus` (`result: GeneratePlanResponse?`); `GeneratePlanResponse` intocado |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `generatePlan` → `submitGeneration` (só rede, não salva); novo `completeGeneration(result, kind)` aplica a regra de nome do complementar (hoje em `generatePlan`) e chama `saveGeneratedPlan` |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | Chave `pending_generation_v1` (envelope JSON do job em andamento) |
| `app/src/main/java/com/treinos/app/ui/PendingGeneration.kt` (novo) | `PendingGeneration(jobId, kind, request, config)` + `PendingGenerationCodec` puro (`ignoreUnknownKeys`, corrompido → null), padrão de `GenerateConfigCodec` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `generatePlan` submete, persiste envelope e vai para Home; `refreshPendingGeneration()` (poll + conclusão/falha); `cancelGeneration()`; `onAppResumed()`; estado `pendingGeneration`; retry de job failed reenvia o request do envelope |
| `app/src/main/java/com/treinos/app/ui/MainActivity.kt` | `onResume` → `viewModel.onAppResumed()` (poll só quando há envelope; sem dependência nova) |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | `PendingGenerationCard` no topo: em andamento (Atualizar/Cancelar) ou falhou (Tentar de novo/Descartar) |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Remove `GenerateOverlay`; CTA vira `BusyButton` durante o submit (rápido) |
| `app/src/main/res/values/strings.xml` e `values-en/strings.xml` | Textos novos nos dois idiomas (`StringParityTest` quebra se faltar) |
| `app/src/test/java/com/treinos/app/ui/PendingGenerationCodecTest.kt` (novo) | Round-trip e corrompido do codec |
| `app/src/test/java/com/treinos/app/ui/GenerationJobFlowTest.kt` (novo) | Fluxo do ViewModel com Room in-memory + fake `BackendApi` (padrão de `NavigationBackTest`) |
| `app/src/test/java/com/treinos/app/ui/screens/GenerateScreenTest.kt` | `overlay_mostra_tempo_e_cancela` substituído por teste do CTA busy no submit |
| `app/src/test/java/com/treinos/app/ui/screens/HomeScreenTest.kt` | Card pendente (em andamento/falho) |
| `app/src/test/java/com/treinos/app/data/remote/ContractTest.kt` | Fixtures `generate_job_ack.json`/`generate_job_status.json` |
| Fakes de `BackendApi` nos testes | Novos métodos da interface em `NavigationBackTest`, `WorkoutRepositorySessionTest`, `BackupImportTest`, `RestoreDownloadTest` |
| `docs/MANUAL.md` | Fluxo de geração assíncrona: pedir → sair do app → voltar e Atualizar |

## Modelo de dados / contrato

```text
POST /v1/generate-workout            (request = GenerateRequest atual, sem mudança)
  202 → GenerateJobAck { "jobId": "<uuid>", "status": "pending" }
  422 → validação de entrada (Pydantic, como hoje)
  500 → catálogo não carregado (como hoje)

GET /v1/generate-workout/{jobId}
  200 → GenerateJobStatus {
          "jobId": "<uuid>",
          "status": "pending|running|done|failed|cancelled",
          "createdAt": "<ISO-8601>",
          "result": <GenerateResponse atual> | null,   // só quando done
          "error": "<mensagem>" | null                 // só quando failed
        }
  404 → jobId desconhecido

POST /v1/generate-workout/{jobId}/cancel
  200 → mesmo corpo do GET (idempotente: done/failed/cancelled não mudam)
  404 → jobId desconhecido

SQLite (backend, aditiva no init_db — sem migration destrutiva):
  generation_jobs(
    id TEXT PRIMARY KEY,          -- uuid
    device_id TEXT NOT NULL,
    request_json TEXT NOT NULL,
    status TEXT NOT NULL,         -- pending|running|done|failed|cancelled
    result_json TEXT,             -- GenerateResponse serializado (done)
    error TEXT,                   -- mensagem (failed)
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  )

Envelope local (AppPreferences, chave pending_generation_v1):
  { "jobId": "...", "kind": "main|extra",
    "request": <GeneratePlanRequest>, "config": <GenerateConfig> | null }

O contrato do plano (GenerateResponse: workout, source, fallbackReason,
planId, nextReviewAt, ...) NÃO muda — vira o payload de result.
Fixture contracts/generate_response.json continua válido como result.
Room: sem mudança de schema. Sync/backup: sem mudança.
```

## Regras e casos de borda

- **Um job em andamento por device:** novo `POST` do mesmo `deviceId` cancela
  o job anterior (`cancelled`) e cria o novo — last-write-wins, sem 409 e sem
  UX de "já existe geração".
- **Orçamento da IA:** `AI_TIMEOUT_TOTAL_SECONDS` default **600 s** (era 80);
  mínimo 3 s; retry único só com orçamento restante e só nos casos já
  aprovados (H3-22/H3-25: `json_parse`, 429, 5xx, rede). O read timeout de
  90 s do app fica — nenhum caminho de rede espera a IA.
- **Job sempre termina com plano quando possível:** IA falha/esgota o
  orçamento → fallback local com `source="local"` + `fallbackReason` do
  contrato H3-21 → status `done`. `failed` só quando nem o fallback é
  possível (ex.: pool vazio do complementar, H3-16 — a mensagem do
  `ValueError` vai em `error`) ou erro inesperado. **Mudança comportamental
  da H3-16:** pool vazio deixa de ser 422 síncrono e vira job `failed` com a
  mesma mensagem; validação Pydantic de entrada continua 422 no submit.
- **Backend reiniciado no meio do job:** startup sweep marca todo
  `pending`/`running` como `failed` com `error="interrupted"`; o app oferece
  "Tentar de novo". Shutdown cancela as tasks em voo (best-effort).
- **Cancel idempotente e best-effort:** job `done` não muda no backend; o app
  limpa o tracking local de qualquer forma (a intent do usuário é descartar)
  e não salva o resultado.
- **Poll só sob demanda:** botão "Atualizar", `MainActivity.onResume` e init
  do ViewModel com envelope presente. Sem polling periódico, sem
  WorkManager, sem permissão nova. Polls sobrepostos são deduzidos
  (`if (polling) return`).
- **Envelope sobrevive a processo morto:** persistido no submit (202);
  restaurado no init; codec corrompido → null (limpa, sem crash). O retry de
  job `failed` reenvia o `request` do envelope — funciona mesmo após morte
  do processo, quando `lastGenerateArgs` já não existe.
- **Conclusão (`done`):** salva via `saveGeneratedPlan` (transação atômica),
  persiste config + `lastFallbackReason` nas regras da H3-30 (IA limpa o
  motivo; local grava o código), limpa o envelope e mostra a mensagem com
  origem/motivo. **Não navega** se o usuário estiver em outra tela — só
  mensagem e selo; o plano aparece via Flow do Room.
- **Nome do complementar:** a regra atual de `generatePlan` ("Complementar —
  Casa/Academia" quando o backend não nomeou) muda para
  `completeGeneration`, sem regressão.
- **404 no status** (banco recriado/job sumido): vira falha com "Geração não
  encontrada — tente de novo"; envelope mantido para o retry.
- **Submit offline:** erro persistente na GenerateScreen com "Tentar de
  novo" (H3-29 preservado); nada é persistido.
- **Toque duplo no CTA:** submit em curso deixa o botão busy/desabilitado;
  mesmo assim o backend aplica last-write-wins.
- **Histórico preservado:** o runner chama `db.save_generation` antes de
  marcar `done` (o `planId` do result vem daí); falha de persistência segue o
  comportamento atual (log + `planId=0`).
- `jobId` não é autenticação (filosofia do repo); backend continua
  LAN/pessoal, sem exposição pública nova.

## Critérios de aceite

1. **Dado** um request válido, **quando** o app chama `POST /v1/generate-workout`, **então** recebe 202 com `jobId` em < 1 s (a IA não roda no caminho da resposta).
2. **Dado** a IA responde dentro do orçamento, **quando** o job termina, **então** o status é `done` com `result.source="ai"` e a geração fica registrada em `plan_generations`.
3. **Dado** a IA estoura o orçamento do job, **quando** o job termina, **então** o status é `done` com `result.source="local"` e `fallbackReason="timeout"` (fallback preservado, sem erro para o usuário).
4. **Dado** um job `running` no SQLite, **quando** o backend reinicia, **então** o startup sweep o marca `failed` com `error="interrupted"` e o app oferece "Tentar de novo".
5. **Dado** um job em andamento, **quando** o mesmo device envia novo POST, **então** o job anterior vira `cancelled` e o novo é criado.
6. **Dado** um job `done`, **quando** o app chama cancel, **então** o status permanece `done` (idempotente) e o app descarta o tracking sem salvar.
7. **Dado** um submit aceito, **quando** o usuário mata o app e reabre, **então** o envelope é restaurado e o status é consultado sem novo submit.
8. **Dado** um job `done`, **quando** o app atualiza, **então** o plano é salvo em transação, config e `lastFallbackReason` persistidos, envelope limpo e a mensagem cita a origem/motivo.
9. **Dado** o backend offline, **quando** o usuário toca em Gerar, **então** a GenerateScreen mostra erro persistente com "Tentar de novo" e nada é persistido.
10. **Dado** um complementar com pool vazio, **quando** o job roda, **então** vira `failed` com a mensagem do `ValueError` e o app mostra "Tentar de novo"/"Descartar".
11. **Dado** o job `done` chega enquanto o usuário está no Histórico, **quando** o poll roda, **então** o plano é salvo e a mensagem aparece sem trocar a tela.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_generation_jobs.py` (novo) | `test_submit_responde_202_imediato_com_job_id`, `test_job_conclui_com_ia_e_persiste_historico`, `test_timeout_da_ia_vira_fallback_local_dentro_do_job`, `test_pool_vazio_marca_job_failed_com_mensagem`, `test_status_de_job_desconhecido_e_404`, `test_novo_submit_cancela_job_em_andamento`, `test_cancel_idempotente_em_job_done`, `test_cancel_de_job_running_marca_cancelled`, `test_startup_sweep_marca_jobs_interrompidos_como_failed`, `test_orcamento_default_600s_e_env_override` |
| `back/tests/test_ai_robustez.py` | Invariante dos 90 s removida; testes de orçamento dirigem o runner com clock falso (parse/retry/fallback já cobertos continuam) |
| `back/tests/test_fallback_diagnostics.py` | Mesmos casos dirigindo `run_generation_job` (códigos H3-21 preservados) |
| `back/tests/test_contract_v2.py` | `job_ack_round_trip`, `job_status_done_round_trip` (fixtures novos) |
| `app/src/test/java/com/treinos/app/ui/PendingGenerationCodecTest.kt` (novo) | `round_trip_com_config`, `round_trip_sem_config`, `corrompido_vira_null` |
| `app/src/test/java/com/treinos/app/ui/GenerationJobFlowTest.kt` (novo, Robolectric + Room in-memory + fake BackendApi) | `submit_persiste_envelope_e_nao_salva_plano`, `refresh_done_salva_plano_limpa_envelope_e_persiste_fallback`, `refresh_failed_mantem_envelope_para_retry`, `retry_reenvia_request_persistido`, `cancel_limpa_envelope`, `init_com_envelope_consulta_status`, `refresh_404_vira_falha_com_retry`, `polls_sobrepostos_sao_deduzidos` |
| `app/src/test/java/com/treinos/app/ui/screens/GenerateScreenTest.kt` | `overlay_mostra_tempo_e_cancela` → `cta_busy_durante_submit` |
| `app/src/test/java/com/treinos/app/ui/screens/HomeScreenTest.kt` | `card_pendente_mostra_atualizar_e_cancelar`, `card_falho_mostra_tentar_de_novo` |
| `app/src/test/java/com/treinos/app/data/remote/ContractTest.kt` | `job_ack_contract`, `job_status_contract` (fixtures únicos dos dois lados) |

## Verificação manual

1. Backend real com chave: gerar um request amplo → sair do app → voltar
   após ~10 min → "Atualizar" → plano "com IA" (meta: fim do fallback
   estrutural por timeout).
2. Matar o backend no meio do job → reabrir o app → "Atualizar" → geração
   interrompida → "Tentar de novo" cria job novo.
3. Modo avião → Gerar → erro persistente; religar → "Tentar de novo" → job
   criado.
4. Cancelar job em andamento → card some da Home; backend marca `cancelled`.
5. Job conclui enquanto o usuário está no Histórico → plano salvo +
   mensagem, sem troca de tela.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Backend precisa ficar de pé durante o job** (minutos): já precisava no
  submit; se o processo morre, o job se perde — mitigado pelo sweep
  (`interrupted`) + retry. Não há persistência de execução através de
  restart (fila externa está fora de escopo).
- **Job longo segura a chamada à SenseNova por até 600 s:** custo/tokens por
  request são os mesmos; só a duração muda. Orçamento configurável por env.
- **App novo com backend antigo:** POST responde 200 com o plano em vez de
  202 → erro de contrato acionável; app e backend shipam juntos (sem shim).
- **TestClient e asyncio:** os testes dirigem `run_generation_job`
  diretamente (clock/cliente falsos), não dependem de timing de thread do
  TestClient.
- **Rollback:** reverter os commits (endpoint síncrono volta); o envelope
  `pending_generation_v1` é ignorado pelo código antigo e a tabela
  `generation_jobs` é aditiva (não atrapalha o banco existente).