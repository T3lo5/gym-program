# H3-25 — Seleção por nome canônico, resolução determinística e calibração da geração por IA

> Evolui a H3-22 para reduzir fallback por timeout/ID inválido **sem perder qualidade
> nem cobertura do catálogo elegível**. Evidência-base: experimento controlado em
> 2026-11-10 (`back/data/ai_prompt_experiment.json`,
> `ai_generation_experiment_report.pdf`, 1 rodada/variante, SenseNova
> `sensenova-6.8-flash-lite`, chamadas espaçadas ~25s).

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-25` |
| MoSCoW | `C` (prioridade de execução elevada pelos achados do experimento: fallback estrutural por timeout) |
| Spec aprovada em | `2026-11-10` |
| Implementação | `pendente` |

**Depende de:** [H3-22](H3-22-robustez-geracao-ia.md) (salvamento parcial,
check ID↔nome, parse tolerante) e [H3-21](H3-21-diagnostico-fallback-ia.md)
(`source`/`fallbackReason`/log `[fallback]`).  
**Não altera contrato de API/DTO do app** (campos e semântica de
`/v1/generate-workout` permanecem os da H3-21).

## Objetivo

Fazer a IA entregar plano válido na maioria das tentativas, usando **todo o
catálogo elegível do request**, com latência compatível com o app: a IA escolhe
pelo **nome canônico listado** (não por ID sintético), o backend resolve
nome→ID deterministicamente, o prompt fica compacto sem perder elegibilidade e os
timeouts/retries são calibrados para não descartar respostas corretas porém
lentas nem retornar resposta vazia.

## Evidências do experimento (2026-11-10, n=1 por variante)

| Teste | Prompt (chars / tok in) | Tempo | HTTP/finish | parse_ai_plan | Exerc. | IDs inválidos | Nome resolvível* | Resgate por nome* |
|---|---:|---:|---|---|---:|---:|---:|---:|
| T1 padrão atual (principal, pool completo c/ IDs) | 37.819 / 12.809 | 73,98s | 200/stop | OK | 28 | 0 | 2 | 0 |
| T2 padrão complementar c/ IDs | 6.025 / 2.047 | 89,82s | 200/stop | OK | 12 | 0 | 0 | 0 |
| T3 nome-only shortlist (~15/grupo), sem IDs | 10.774 / 3.790 | 70,09s | 200/stop | falha** | 24 | 0 | 0 | 0 |
| T4 nome-only pool completo, sem IDs | 24.317 / 8.382 | 87,67s | 200/stop | falha** | 24 | 0 | 2 | 2 |
| T5 shortlist c/ IDs | 11.223 / 3.911 | 49,90s | 200/stop | n/a | — | — | — | — |
| T6 mínimo, sem catálogo (nomes livres) | 1.136 / 461 | 75,56s | 200/stop | falha** | 24 | 0 | 2 | 2 |

\* Simulação de resolução `exerciseName` → catálogo (`normalize` + fuzzy cutoff
0,72 + substring, base de `resolve_plan_ids`).  
\** Falha no parser/contrato atual por exigir `exerciseId` resolvível; “nome
resolvível/resgate” indica o que um resolvedor nome→ID aceitaria.

**Conclusões que viram requisito:**
- T1/T2 são corretos mas lentos (74s/90s): com timeout agressivo viram fallback
  indevido → **timeouts/retry calibrados** e teto ≤ read timeout do app (~90s).
- T5: `content` vazio, `completion_tokens` todos em `reasoning_tokens`
  (`finish=stop`) → **detectar resposta inútil e 1 retry com parâmetros
  ajustados** antes de fallback.
- Nomes livres não alinham ao catálogo (T3/T4/T6 baixos) → **IA deve copiar o
  nome canônico listado**; resolução determinística casa contra o mesmo canônico.
- Prompt grande pesa, mas latência tem variância de provider (T6 pequeno = 75s);
  mesmo assim reduzir tokens ajuda (T5 50s) → **prompt compacto por request,
  mantendo todo o pool elegível**.

## Fora de escopo

- Trocar modelo/provedor, streaming, cache de respostas da IA.
- Mudar schema de entrada/saída do endpoint ou campos do app (H3-21 define).
- Foco muscular no plano principal (H3-23), mídia/instruções ricas (H3-24).
- Shortlist que **exclua** exercícios elegíveis por padrão. Se medição futura
  exigir corte, só via mudança explícita desta spec (o default aqui é cobertura
  total do pool elegível).
- “Menu em dois níveis” (Tier A/B) da H3-22: permanece plano B condicionado a
  medição; esta spec pode satisfazer o gatilho com formato compacto por nome
  antes de ativá-lo.

## Definições

- **Pool elegível do request:** exercícios de força (`_is_strength_exercise`)
  que passam no filtro de equipamento/nível do request (H3-22). Complementar
  (`focusMuscles`) = foco exclusivo; principal = `targetMuscles` primeiro, demais
  grupos do split presentes (regras atuais de `_build_pool`/H3-22 preservadas).
  **Nunca** inclui equipamento que o usuário não informou.
- **Nome canônico listado:** nome exibido para o exercício na linha do
  `catalog_summary_for_request` no idioma do request (`_localized(..., namePt,
  name, language)` hoje). É a chave que a IA deve copiar exatamente.
- **Resolução determinística:** mesma família de matching já usada
  (`normalize`, `get_close_matches` cutoff 0,72, substring, índice estável).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/plan_builder.py` | Nova `resolve_exercise_names(plan, catalog) -> Tuple[dict, List[dict]]`: preenche `exerciseId` a partir de `exerciseName` (ou corrige ID ausente/inválido) quando o nome resolve; retorna plano e lista de drops `{"exerciseName","exerciseId","reason","declared"}` para log. Reutiliza/expõe índice por nome canônico EN/PT e por formas normalizadas. |
| `back/app/plan_builder.py` | `catalog_summary_for_request` passa a formato **compacto por nome canônico**: linha `  - <nome canônico> (<eq>)` (sem ID inline), cabeçalho instruindo “copie o nome exatamente como listado; o backend resolve o ID; não invente nomes”. Mantém **todos** os exercícios do pool elegível, ordem por relevância (foco/target primeiro). Expor helper puro para medir `chars/tokens_aprox/linhas` (log/PR). |
| `back/app/plan_builder.py` | Índice/lookup de resolução passa a casar `exerciseName` declarado contra **nome canônico listado no idioma do request**, `name`, `namePt` e forma normalizada de todos; empate/tie-break determinístico (ordem estável do catálogo). |
| `back/app/main.py` | `build_*_prompt`/regras: seleção primária por **nome canônico da lista**; `exerciseId` torna-se opcional na resposta da IA (pode omitir/vazio). Exigir `exerciseName` exato da lista para exercícios de força. Manter IDs sintéticos `run_*`/`mobility_*` inalterados. |
| `back/app/main.py` | `parse_ai_plan`: (1) `resolve_exercise_names` **antes** de `resolve_plan_ids`/validação do DTO; (2) preencher `exerciseId` resolvido; (3) aplicar salvamento parcial/reconstrução H3-22 para nome/ID não resolvível e para `id_name_mismatch`; (4) nunca derrubar plano inteiro por itens parciais. |
| `back/app/main.py` | Chamada à IA: **orçamento total (deadline)** de `AI_TIMEOUT_TOTAL_SECONDS` default **80.0** (env, mín. 3s; `connect` 5s, `write`/`pool` 10/5s). Cada attempt usa o orçamento restante; retry só com ≥3s restantes — **pior caso ≈ 80s < 90s** (read timeout do app) em qualquer modo de falha. |
| `back/app/main.py` | Retry (máx. 1) em: `json_parse`, HTTP 5xx, HTTP 429, `httpx.TimeoutException`/erro de rede, e **resposta inútil** (HTTP 200 mas `content` vazio/ausente ou não parseável após extração). Retry de resposta inútil usa parâmetros seguros (ex.: sem `reasoning`, temperatura/payload inalterados salvo necessidade explícita) e timeout de retry. **Sem retry** em 4xx de autenticação/parâmetro. |
| `back/app/main.py` | Log/observabilidade: manter `[fallback] reason=…`; registrar `generationTimeMs`, `dropped_ids`/nomes com motivo (`unresolved_name`, `id_name_mismatch`, `unresolved_id`), contagem de resoluções por nome e tentativas. (Sem vazar segredos/conteúdo sensível.) |
| `back/tests/test_selecao_por_nome.py` (novo) | Testes puros e de parse para resolução por nome, formato compacto, resgate por nome e fallback total. |
| `back/tests/test_ai_robustez.py` | Regressão: parse tolerante, salvamento parcial, retry 429/json_parse, check ID↔nome; ajustar aos novos defaults de timeout/formato quando necessário. |
| `back/tests/test_hybrid_plan.py` | Regressão: fallback local e invariantes híbridos/IDs sintéticos preservados. |
| `docs/specs/H3-25-selecao-por-nome.md` | Esta spec (fonte de verdade da implementação). |

## Modelo de dados / contrato

```text
Endpoint: POST /v1/generate-workout — SEM mudança de schema request/response.

Request (inalterado): GenerateRequest atual (deviceId, goal, daysPerWeek,
availableEquipment, targetMuscles, focusMuscles, modalities, language, etc.).

Response (inalterado, H3-21): GenerateResponse
  - source: "ai" | "local"
  - fallbackReason: "" quando ai; código fechado quando local
    (no_api_key, http_error, timeout, json_parse, unresolved_ids, unknown)
  - workout: WorkoutPlanDTO válido (ExerciseDTO.exerciseId SEMPRE preenchido)

Comportamental (mudança interna, compatível com o app):
  - IA pode responder exercícios de força com exerciseName canônico e
    exerciseId ausente/vazio/desconhecido → backend resolve e preenche
    exerciseId antes da validação do DTO.
  - Nome resolvível ⇒ exercício mantido (source "ai" se o plano seguir válido).
  - Nome/ID não resolvível ou par ID↔nome divergente ⇒ exercício removido;
    sessão < 3 reconstruída com build_strength_session no pool do request
    (salvamento parcial H3-22). Plano segue "ai" se sobrar conteúdo aproveitável.
  - Nenhum exercício de força aproveitável ⇒ fallback total source "local",
    fallbackReason "unresolved_ids" (invariante H3-21 preservada).
  - IDs sintéticos run_* / mobility_* nunca passam por resolução de nome do
    catálogo (comportamento atual preservado).
  - HTTP 200 com content vazio/inútil ⇒ 1 retry; se persistir, fallback com
    reason "json_parse" (conteúdo não utilizável) ou "timeout"/"http_error"
    conforme a falha, nunca plano "ai" inválido.
```

## Regras e casos de borda

### 1) Catálogo no prompt (cobertura + compacidade)
- Listar **todos** os exercícios do pool elegível do request, sem cap por grupo
  e sem excluir equipamento elegível (diretriz: usar o catálogo possível do
  pedido). Filtragem apenas por elegibilidade do request (`_build_pool`,
  `strict_equipment`/foco quando aplicável).
- Formato compacto: `  - <nome canônico> (<eq>)` por linha, agrupado por músculo
  na ordem de relevância (foco/target primeiro; complementar = só foco). Cabeçalho
  curto: catálogo é a fonte de verdade, **copiar o nome exatamente como listado**,
  não inventar nomes, backend resolve o ID.
- Nomes exibidos no idioma do request (`_localized`), consistentes com
  H3-36 (`namePt` quando `pt`, senão `name`). O canônico listado é a string que
  a IA deve ecoar.
- Registrar (log/debug) `chars`, linhas e `~tokens (chars/4)` do resumo montado
  para comparar com o baseline (~32k chars / ~8k tok no caso principal medido).

### 2) Resolução nome→ID (dicionário determinístico)
- Ordem de resolução, documentada e estável: (1) match exato normalizado contra
  nome canônico listado e contra `name`/`namePt`; (2) `get_close_matches`
  cutoff **0,72** sobre chaves normalizadas; (3) contenção de substring
  normalizada. Empate → primeira ocorrência na ordem estável do catálogo.
- Casar `exerciseName` declarado (idioma/forma retornada pela IA) contra índice
  que inclui `name`, `namePt` e canônicos normalizados, para tolerar PT/EN e
  variações de acento/caixa/pontuação sem aceitar exercício errado.
- `exerciseId` válido já preenchido pela IA é preservado; se vier ID + nome
  divergente, aplica regra H3-22 (`id_name_mismatch`) — remover e reconstruir
  (prioriza correção sobre “confiar no ID”).
- `exerciseId` ausente/vazio/inválido + `exerciseName` resolvível → preencher ID
  resolvido.
- Nome não resolvível → **não** derruba o plano; vira drop com motivo
  `unresolved_name` e entra no salvamento parcial/reconstrução da sessão.
- Todos não resolvíveis/sem aproveitáveis → fallback total
  `fallbackReason = "unresolved_ids"`.
- `run_*` / `mobility_*`: intocados pela resolução de catálogo; mantêm regras
  híbridas existentes.

### 3) Resposta da IA e retries
- Orçamento **total** da IA: **80s** (env `AI_TIMEOUT_TOTAL_SECONDS`, mín. 3s),
  connect 5s. Cada attempt usa o que resta do orçamento; com <3s restantes não
  há novo attempt. Pior caso ≈ 80s, abaixo do read timeout do app (90s) com
  folga para connect, fallback local e rede. (2026-11-10: os timeouts fixos
  por attempt — 45s+25s, depois 90s+45s — foram substituídos pelo deadline;
  90s+45s=135s estourava os 90s do app e o usuário via "O servidor demorou
  demais para responder" sem receber o plano.)
- 1 retry em: `json_parse`, HTTP 5xx, HTTP 429, timeout/erro de rede, e
  **content vazio ou não utilizável** após HTTP 200 (inclui caso de tokens
  consumidos em reasoning sem conteúdo). Na falha final, mapear `fallbackReason`
  existente (`json_parse`/`timeout`/`http_error`/`unknown`) — sem criar código
  novo no contrato.
- Nunca retry em 4xx de autenticação/parâmetro.
- Parse tolerante (cercas ```json, extração de objeto balanceado) e salvamento
  parcial/reconstrução H3-22 permanecem obrigatórios.

### 4) Qualidade e segurança
- Resolução nunca escolhe exercício fora do pool elegível do request (nome que
  caseie com item inelegível é tratado como não resolvível para este request).
- Sessão reconstruída mantém objetivo/nível/equipamento/foco/duração e mínimo de
  exercícios conforme H3-22/builder.
- Não logar prompt completo sensível em produção além do necessário; logar apenas
  métricas e identificadores/nomes descartados de forma truncada/segura.

## Critérios de aceite

1. **Nome canônico resolve:** **Dado** plano da IA com `exerciseName` canônico
   listado e `exerciseId` vazio/ausente, **quando** `parse_ai_plan` processa,
   **então** `exerciseId` é preenchido com o ID correto do catálogo, o plano
   valida no `WorkoutPlanDTO` e `source="ai"`, `fallbackReason=""`.
2. **Variação tolerada, determinística:** **Dado** nome com variação próxima
   (acento/caixa/pontuação/espaço) equivalente a um canônico do pool,
   **quando** resolve, **então** usa ordem exato→fuzzy(0,72)→substring, resultado
   estável entre execuções e dentro do pool elegível.
3. **Nome não resolvível não derruba plano:** **Dado** 1 nome inexistente entre
   exercícios válidos, **quando** processa, **então** o item é removido, a sessão
   é reconstruída se < 3, o plano sai `source="ai"` com log do drop; **dado**
   todos não resolvíveis, **então** `source="local"` e
   `fallbackReason="unresolved_ids"`.
4. **Divergência ID↔nome preservada:** **Dado** `exerciseId` válido mas
   `exerciseName` claramente divergente do exercício do ID, **quando** processa,
   **então** aplica `id_name_mismatch` (remoção/reconstrução) como na H3-22.
5. **Cobertura do pool:** **Dado** request com equipamento/músculos/foco,
   **quando** `catalog_summary_for_request` monta o prompt, **então** inclui
   **todos** os exercícios do pool elegível (sem cap por grupo), sem equipamento
   não informado, em formato compacto por nome canônico com instrução de cópia
   exata.
6. **Resposta vazia/inútil:** **Dado** HTTP 200 com `content` vazio ou
   inutilizável, **quando** gera, **então** ocorre exatamente 1 retry com
   parâmetros ajustados e, se persistir, cai em fallback com `fallbackReason`
   coerente (`json_parse`/`timeout`/`http_error`) — nunca `source="ai"` inválido.
7. **Latência/timeout calibrados:** **Dado** configuração default,
   **quando** a IA falha ou demora, **então** o pior caso ≈ 80s < 90s
   (app) em qualquer modo de falha — inclusive resposta lenta com JSON
   inválido (retry só com orçamento restante). Valor sobrescrito por
   `AI_TIMEOUT_TOTAL_SECONDS` (≥3s) sem mudar contrato.
8. **Híbrido preservado:** **Dado** plano híbrido, **quando** resolve nomes/IDs,
   **então** `run_*`/`mobility_*` e invariantes de `normalize_hybrid_plan`
   permanecem válidos.
9. **Contrato:** Resposta do endpoint mantém os mesmos campos/semântica; app
   antigo consome sem alteração.

## Testes a criar/atualizar

| Arquivo | Testes |
|---|---|
| `back/tests/test_selecao_por_nome.py` | `test_nome_canonico_exato_preenche_exercise_id`, `test_nome_variacao_acentuacao_case_resolve_deterministico`, `test_resolve_considera_name_namept_e_canônico_listado`, `test_nome_nao_resolvivel_remove_e_reconstroi_sessao`, `test_todos_nomes_invalidos_fallback_unresolved_ids`, `test_id_ausente_com_nome_resolvivel_valida_no_dto`, `test_id_valido_nome_divergente_mantem_id_name_mismatch`, `test_resolucao_nao_escolhe_exercicio_fora_do_pool`, `test_sinteticos_run_mobility_nao_resolvidos_por_nome_catalogo`, `test_catalog_summary_compacto_lista_todos_elegiveis_sem_id_inline`, `test_catalog_summary_instrui_nome_canonico_e_nao_lista_equipamento_fora` |
| `back/tests/test_ai_robustez.py` | regressão: `test_parse_remove_cercas_de_codigo`, `test_parse_extrai_objeto_balanceado`, salvamento parcial/reconstrução, `test_retry_unico_em_429`, `test_retry_em_json_parse`, `test_retry_em_content_vazio_http200`, `test_timeouts_default_somam_abaixo_de_90s`; ajustar expectativas de formato do resumo (nome canônico) onde aplicável |
| `back/tests/test_hybrid_plan.py` | regressão existente (sem quebra de modalidade/sintéticos) |

> Testes de lógica/prompt/parsing são puros ou com cliente HTTP falso (sem
> SenseNova real). Não exigir chave de IA no CI.

## Verificação manual

1. Com chave real, gerar 5 planos (principal e complementar, PT/EN): ≥ 4/5 com
   `source="ai"`, sem fallback indevido por timeout no cenário típico; conferir
   `generationTimeMs` e `[fallback]` no log.
2. Conferir no app que exercícios correspondem aos nomes canônicos pedidos (sem
   troca silenciosa de variante/equipamento).
3. Conferir que `dropped_ids`/nomes registram motivo (`unresolved_name`/`id_name_mismatch`)
   quando houver descarte parcial.
4. Comparar tamanho do resumo de prompt (chars/~tokens) com baseline do
   experimento e anotar no PR/commit de implementação.

## Comando de verificação

```bash
./scripts/verify.sh
```

Critério: exit code `0` e linha `VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`.

## Riscos e rollback

- **Ambiguidade de nome (ex.: incline/decline, mesmo nome em equipamento
  distinto):** mitigado por cópia do nome canônico listado, resolução restrita ao
  pool elegível, ordem determinística e check `id_name_mismatch`; testes cobrem
  estabilidade e fora-do-pool. Se surgir troca silenciosa, endurecer chave do
  canônico (incluir eq/grupo na linha e no match) sem mudar contrato.
- **Latência/variância do provider:** prompt compacto + timeouts calibrados
  reduzem, mas não eliminam; métricas (n>1) devem guiar ajuste fino dos envs.
  Rollback rápido = ajustar `AI_TIMEOUT_TOTAL_SECONDS` ou restaurar o default.
- **Resposta vazia/reasoning-only:** tratada por retry de conteúdo inútil; se
  recorrente, desabilitar `reasoning` no retry (e documentar) antes de fallback.
- **Queda de qualidade de escolha:** medir nos 5 planos manuais; rollback do
  comportamento = voltar prompt/parse para modo por ID da H3-22 (1 commit),
  mantendo salvamento parcial — contrato de saída inalterado.
- **Prompt ainda grande em pools extremos:** registrar tamanho; se estourar
  contexto/latência de forma comprovada, acionar plano B (Tier A/B) ou shortlist
  determinística **com spec atualizada** — não aplicar exclusão silenciosa.

## Notas de implementação (ordem sugerida)

1. Índice/`resolve_exercise_names` (puro) + testes de resolução.
2. `catalog_summary_for_request` compacto por nome + testes de cobertura/formato.
3. `parse_ai_plan` (resolver nome → validar) + tratamento de content vazio/retry.
4. Ajuste de prompts, defaults de timeout e logs.
5. `./scripts/verify.sh` verde; só então considerar concluída.
