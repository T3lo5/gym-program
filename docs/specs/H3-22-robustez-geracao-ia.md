# H3-22 — Robustez da geração por IA (fim do fallback estrutural)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-22` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-07` (diretriz do usuário: expor todos IDs; plano B condicionado) |
| Implementação | `ativa (plano B disparado por medição em 2026-11-10)` |

**Depende da [H3-21](H3-21-diagnostico-fallback-ia.md)** (o motivo do fallback
precisa estar visível para validar quais causas dominam no uso real).

## Objetivo

A IA passa a entregar plano de verdade na maioria das tentativas; o fallback
local vira exceção rara, não regra. Ataca as causas estruturais identificadas
no código, não os sintomas.

## Contexto (estado atual verificado)

- **Prompt impossível de cumprir:** `CATALOG_SUMMARY` (`main.py` L57–73) mostra
  **8 exercícios por músculo — 136 dos 876 IDs** — mas a regra 1 do prompt
  exige "use APENAS exercícios do catálogo acima (IDs exatos)". A IA não consegue
  obedecer fora dos 136 IDs expostos.
- **Um ID ruim derruba o plano inteiro:** `resolve_plan_ids` retorna
  `unresolved`; o endpoint levanta `ValueError` (`main.py` L717–722) e descarta
  **toda** a resposta da IA — inclusive os 95% de exercícios válidos. Não há
  salvamento parcial.
- **Timeout apertado:** 30 s (`main.py` L690) para gerar uma semana híbrida
  completa em JSON com `sensenova-6.8-flash-lite`; o app espera 90 s
  (`BackendClient.kt` L95) — há folga para aumentar.
- **Parse frágil:** `json.loads(content)` (L716) sem remoção de cercas de
  código (```json); `max_tokens: 8192` pode truncar o JSON de uma semana cheia
  e virar `json_parse`.
- **Sem retry:** uma única tentativa; qualquer 5xx/429 transitório = fallback.

## Fora de escopo

- Trocar de modelo/provedor ou adicionar streaming.
- Mudar o contrato de saída (`fallbackReason` é da H3-21).
- Foco muscular no plano principal (H3-23) e mídia (H3-24).
- Cache de respostas da IA.
- Seleção por nome com resolução determinística — evolução própria, [H3-25](H3-25-selecao-por-nome.md).
- Shortlist determinístico por sessão (40–80 candidatos): só ganha spec própria
  se a H3-22 e a H3-25 não bastarem.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/plan_builder.py` | Nova `catalog_summary_for_request(request, catalog) -> str`: **pool completo do request, sem cap por grupo** — todos os exercícios de força (`_is_strength_exercise`) do filtro de equipamento/nível, um por linha (`ID (nome, equipamento)`), ordenados por relevância (grupos de `targetMuscles`/`focusMuscles` primeiro). Diretriz do usuário (2026-11-07): expor todos os IDs, porque treino "morre por padrão" quando o ID correto não foi exposto |
| `back/app/plan_builder.py` | Nova `id_name_mismatches(plan, catalog)`: para cada exercício com `exerciseId` resolvido e `exerciseName` declarado pela IA, confere o par contra o catálogo com a mesma normalização/fuzzy do `resolve_plan_ids`; só marca divergência quando o nome declarado **não** corresponde a nenhuma variação próxima do nome oficial do ID (evita falso positivo com nome abreviado/traduzido) |
| `back/app/main.py` | `parse_ai_plan` remove o exercício com par ID↔nome divergente (mesmo caminho do salvamento parcial de ID inválido) e o log registra `dropped_ids` com o motivo `id_name_mismatch` |
| `back/app/plan_builder.py` | `resolve_plan_ids` passa a retornar também `dropped: List[str]`; **não** é mais fatal — exercício não resolvível é removido do dia |
| `back/app/main.py` | Prompt usa `catalog_summary_for_request`; regra 1 reescrita: "prefira sempre os IDs listados; IDs desconhecidos serão descartados e a sessão completada localmente" |
| `back/app/main.py` | Salvamento parcial após `resolve_plan_ids`: dia de força com < 3 exercícios é reconstruído com `build_strength_session` (mesmas restrições); fallback total só quando **nada** aproveitável sobrar |
| `back/app/main.py` | Parse tolerante: remover cercas de código e extrair o primeiro objeto `{...}` balanceado antes de `json.loads`; `timeout` 30 → 60 s; **1 retry** em `json_parse`/HTTP 5xx/429 (sem retry em 4xx de autenticação) |
| `back/tests/test_ai_robustez.py` (novo) | Testes das funções puras (`catalog_summary_for_request`, parse tolerante, salvamento parcial) |
| `back/tests/test_hybrid_plan.py` | Regressão: fallback local continua determinístico |

## Modelo de dados / contrato

```text
Sem mudança de contrato de entrada/saída (os campos são os da H3-21).
Comportamental:
  - resposta da IA com k IDs inválidos (k < total de exercícios de força)
    ⇒ plano com IA (source = "ai"), exercícios inválidos removidos,
    fallbackReason vazio, log registra dropped_ids;
  - resposta da IA com par ID↔nome divergente ⇒ exercício removido pelo
    mesmo salvamento parcial, log registra dropped_ids com id_name_mismatch;
  - resposta da IA sem nenhum exercício aproveitável
    ⇒ fallback total (source = "local"), fallbackReason = "unresolved_ids".
```

## Regras e casos de borda

- IDs sintéticos `run_*`/`mobility_*` continuam fora da validação de catálogo
  (comportamento atual de `resolve_plan_ids`, preservado).
- Dia de força reconstruído localmente usa o pool do request (equipamento +
  nível + foco), nunca exercício fora do filtro de equipamento.
- `catalog_summary_for_request` nunca lista exercício de equipamento que o
  usuário não informou (senão a IA passa a usá-lo) — é a exceção explícita da
  diretriz: só o que o usuário marcou entra no resumo.
- Grupos musculares no resumo (decisão aprovada pelo usuário em 2026-11-07):
  no **complementar** (`focusMuscles`), só os grupos do foco (foco exclusivo,
  coerente com H3-16); no **principal**, os grupos de `targetMuscles` vêm
  primeiro e os demais grupos do split continuam listados, porque o plano
  principal ainda se apoia no split (H3-23 mantém isso). Filtro estrito de
  grupos no principal ficou descartado: deixaria a IA sem IDs para completar
  o split além do foco.
- Sem cap por grupo: a IA recebe todos os IDs do pool filtrado — fim dos
  "treinos que morrem por padrão" porque o ID correto não foi exposto.
- Par ID↔nome: a IA devolve `exerciseName` junto do `exerciseId`; divergência
  clara (o nome declarado não bate com o nome do catálogo do ID resolvido, nem
  com variação próxima pela mesma normalização/fuzzy do `resolve_plan_ids`)
  vira exercício descartado — é o caso "ID válido, exercício errado"
  (`Barbell_Bench_Press` no lugar de `Incline_Barbell_Bench_Press`), que a
  validação de IDs sozinha não pega. Nome abreviado/traduzido que faça match
  tolerante **não** é divergência.
- Retry: no máximo 1; só em `json_parse`, HTTP 5xx e 429; 4xx de
  autenticação/parametro não repete (falha determinística).
- Timeout total da geração continua abaixo do read timeout do app (60 s +
  retry ≤ ~125 s < 90 s do app? **não** — com retry o pior caso passa de 90 s:
  o retry só vale para `json_parse` (resposta já recebida) e o retry de rede
  usa timeout de 30 s; pior caso ≈ 60 + 30 = 90 s. Documentar o limite no
  código; se estourar, o app mostra erro de conexão e o usuário repete).
- Prompt novo medido antes do merge: o pool completo cresce o resumo
  (~800+ linhas); medir tokens reais com o formato compacto de uma linha por
  exercício. Se estourar o contexto do flash-lite, aplicar cap documentado
  como **exceção** (não default) — a diretriz manda expor tudo; o cap só
  volta com evidência de limite técnico.
- `normalize_hybrid_plan` continua rodando depois do salvamento parcial
  (invariantes de sessão por modalidade preservados).

## Plano B condicionado a medição (menu em dois níveis)

Não é o default da H3-22; só entra em execução se um gatilho de medição
disparar. Propriedade obrigatória: **nenhum ID elegível sai do prompt** — o
que muda é o nível de detalhe, nunca a cobertura.

- **Tier A (detalhado):** exercícios dos grupos marcados no request + seu
  equipamento, com linhas completas (`ID (nome, equipamento)`).
- **Tier B (índice):** todo o resto do pool elegível, só `ID` agrupado por
  músculo (os IDs são auto-descritivos), com instrução explícita de preferir
  o Tier A e usar o Tier B como menu de variedade/complemento do split.
- **Gatilhos de ativação:** catálogo do prompt acima do limite confortável do
  modelo (medido no merge da H3-22) ou queda de qualidade na verificação
  manual (5 gerações com escolhas incoerentes). Ativação = emitir os dois
  tiers dentro de `catalog_summary_for_request`; um commit, sem mudança de
  contrato.

## Critérios de aceite

1. **Dado** um conteúdo de IA embrulhado em ```json ... ```, **quando** o parse roda, **então** o plano é extraído sem fallback.
2. **Dado** uma resposta com 1 ID inventado entre 6 exercícios de força, **quando** o endpoint processa, **então** o plano é aceito com 5 exercícios, o ID inválido é removido e o log registra `dropped_ids`.
3. **Dado** uma sessão que fica com 2 exercícios após a remoção, **quando** o salvamento parcial roda, **então** a sessão é reconstruída localmente com ≥ 3 exercícios do pool do request.
4. **Dado** uma resposta em que **todos** os exercícios de força têm ID inválido, **quando** o endpoint processa, **então** fallback total com `fallbackReason = "unresolved_ids"`.
5. **Dado** o resumo do prompt, **quando** o request tem `targetMuscles = ["abs"]`, **então** o grupo focado aparece primeiro e cada grupo lista **todos** os IDs do pool do request (não 8, nem 20).
6. **Dado** um HTTP 429 na primeira tentativa, **quando** o endpoint gera, **então** há exatamente 1 retry e, se a segunda tentativa passar, `source = "ai"`.
7. **Dado** um par ID↔nome divergente na resposta da IA, **quando** o `parse_ai_plan` processa, **então** o exercício é removido, o log registra `id_name_mismatch` e o plano segue com IA se sobrar exercício aproveitável.
8. **Dado** o prompt montado com o pool completo, **quando** o merge da H3-22, **então** o tamanho do catálogo em tokens fica registrado (log ou PR) com a folga/estouro do contexto do modelo — estouro ativa o plano B (dois níveis) antes de qualquer cap voltar.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_ai_robustez.py` | `test_parse_remove_cercas_de_codigo`, `test_parse_extrai_objeto_balanceado`, `test_id_invalido_e_removido_e_sessao_mantida`, `test_sessao_com_menos_de_tres_e_reconstruida`, `test_todos_invalidos_vira_fallback_total`, `test_resumo_prioriza_foco_e_lista_pool_completo`, `test_resumo_nao_lista_equipamento_fora_do_request`, `test_retry_unico_em_429`, `test_par_id_nome_divergente_e_removido`, `test_nome_aproximado_nao_e_divergencia` |
| `back/tests/test_hybrid_plan.py` | regressão do fallback local (existente, sem mudança) |

## Verificação manual

1. Com a chave real, gerar 5 planos seguidos (main e complementar): contar quantos vêm "com IA" — meta ≥ 4 de 5.
2. Forçar uma resposta inválida (mock temporário ou chave inválida) e conferir o `[fallback] reason=` no log.
3. Conferir no app que o plano vindo de IA tem exercícios coerentes com o pedido (não aleatórios do catálogo).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Execução do plano B (2026-11-10)

O gatilho 1 disparou com evidência real: request amplo (8 equipamentos + 8 músculos) gerava `chars=29067 linhas=670 tokens_aprox=7266` no catálogo do prompt e a IA levava `generationTimeMs=74500` contra timeout de 45s (fallback `reason=timeout`).

Ativação em `catalog_summary_for_request` (`back/app/plan_builder.py`):

- **Tier A (detalhado):** 8 exercícios detalhados por músculo do request, com `ID (nome, equipamento)`. Cada ID entra uma única vez (no primeiro grupo relevante).
- **Tier B (índice):** todo o restante do pool elegível como `ID` agrupado por músculo, com header instruindo preferir o Tier A.
- **Gatilho:** `tier_a_chars > CATALOG_PROMPT_TIER_B_CHARS` (22.000 chars, medido do Tier A completo).
- **Cobertura:** 100% dos IDs do pool elegível do request — verificado por teste (`test_plano_b_nenhum_id_elegivel_sai_do_prompt`): pool=585, Tier A=118, Tier B=467, missing=0.
- **Tamanho final:** ~14.000 chars (Tier A ~4.700 + Tier B ~9.300), abaixo do baseline de 23.019 chars.

Testes do plano B em `back/tests/test_ai_robustez.py`:
`test_plano_b_tier_b_entra_quando_tier_a_estoura_limite`, `test_plano_b_nao_entra_em_request_estreito`, `test_plano_b_entra_quando_cap_por_musulo_e_excedido`, `test_plano_b_nenhum_id_elegivel_sai_do_prompt`, `test_plano_b_exercício_atende_a_varios_grupos`, `test_plano_b_tamaho_prompt_abaixo_do_baseline`.

## Riscos e rollback

- Prompt maior pode degradar a latência do flash-lite: medir `generationTimeMs`
  antes/depois; rollback = voltar o cap por músculo (um commit), mantendo o
  filtro de equipamento/grupo da diretriz.
- ID válido mas errado passa na validação de IDs: mitigado pelo check ID↔nome
  (par divergente é descartado); falso positivo mitigado pela comparação
  tolerante (mesma normalização/fuzzy do `resolve_plan_ids`).
- Salvamento parcial pode produzir sessões "costuradas" (IA + local) com
  desequilíbrio de volume: a reconstrução usa o mesmo pool e o mesmo nº alvo
  por duração; teste cobre o caso limite.
- Pior caso de latência com retry pode passar do read timeout do app (90 s):
   mitigado estruturalmente pelo **orçamento total (deadline)** da IA —
   `AI_TIMEOUT_TOTAL_SECONDS` default 80s, retry só com orçamento restante
   (H3-25, revisão 2026-11-10). Timeouts fixos por attempt não garantem a
   invariante: 90s+45s chegou a 135s e o app desistia antes do fallback.

## Execução da recuperação de JSON truncado (2026-11-11)

Mesmo com o plano B reduzindo o prompt, a IA pode atingir o `max_tokens` e
devolver um JSON cortado no meio de uma string, objeto ou array — o erro
`Unterminated string starting at: line N column M (char K)` que vinha
saindo nos logs (`[fallback] reason=json_parse detail=...`). O retry não
ajuda: a IA produz a mesma resposta longa toda vez.

Dois reparos:

1. `max_tokens` 8192 → 16384 (`_generate_plan_response`, main.py) — dá mais
   folga para a resposta completa sem estourar o contexto do flash-lite.
2. Nova `_recover_truncated_json(content)` em `back/app/main.py`:
   - Extrai o primeiro objeto JSON (`_extract_first_json_object`) e tenta
     `json.loads`; se já for válido, retorna sem alteração.
   - Coleta os **pontos seguros** de corte (após cada valor completo: fim de
     string, `,`, `}`, `]`) e, do mais longo para o mais curto, corta o texto,
     remove a vírgula final e fecha os contêineres e strings em aberto usando
     uma **pilha** (ordem correta, de dentro para fora).
   - Se o resultado for `json.loads` válido, retorna; caso contrário,
     repete com o ponto seguro anterior.
   - `parse_ai_plan` chama `_recover_truncated_json` no `except` de
     `json.loads` antes de lançar `AiFailure("json_parse", ...)`. O plano
     resultante é parcial, mas o salvamento parcial de `parse_ai_plan`
     (drop de exercícios inválidos, reconstrução de sessões) completa o que
     sobrou — o plano sai com `source = "ai"` em vez de cair no fallback
     total.

Cobertura: `back/tests/test_fallback_diagnostics.py` com
`test_recupera_json_truncado_em_string`, `test_recupera_json_truncado_em_array`,
`test_recupera_json_truncado_em_objeto`, `test_recupera_json_ja_valido_nao_muda`,
`test_recupera_json_sem_ponto_seguro` e `test_parse_ai_plan_recupera_truncado`.

## Riscos e rollback

- Recuperação pode manter exercícios parciais (ex.: `{"exerciseId": "def"`
  sem os demais campos) — o salvamento parcial de `parse_ai_plan` remove os
  inválidos e reconstrói as sessões, então o plano final é válido.
- Se a recuperação não encontrar corte válido, retorna o texto original e o
  comportamento é o anterior (`json_parse` → fallback total).

## Execução da normalização de campos (2026-11-11)

Mesmo com a recuperação de JSON truncado, a IA pode devolver um JSON **válido**
com valores fora do range dos campos do `ExerciseDTO`. Exemplo real do log:
`[fallback] reason=schema_validation detail=plano rejeitado pelo schema: 6
validation errors for WorkoutPlanDTO days.2.exercises.0.targetRpe Input should be
greater than or equal to 5 [type=greater_than_equal, input_value=4.0, ...]` — a IA
devolveu `targetRpe = 4.0` e o DTO exige `ge=5.0`. O plano inteiro caiu no
fallback local.

Reparo em `back/app/main.py`:

- Nova `_normalize_exercise_fields(raw_plan)` — clampa valores fora do range
  antes da validação pelo schema:
  - `targetRpe`: < 5.0 → 5.0; > 10.0 → 10.0.
  - `restSeconds`: < 0 → 0; > 300 → 300.
  - `sets`: < 1 → 1; > 20 → 20.
- Chamada em `parse_ai_plan` imediatamente antes de `WorkoutPlanDTO(**raw_plan)`.
- Mutação in-place; valores já válidos são mantidos.

Cobertura: `back/tests/test_fallback_diagnostics.py` com
`test_normalize_exercise_fields_clamp_target_rpe`,
`test_normalize_exercise_fields_clamp_rest_seconds`,
`test_normalize_exercise_fields_clamp_sets`,
`test_normalize_exercise_fields_nao_muda_validos` e
`test_parse_ai_plan_recupera_rpe_invalido`.

## Riscos e rollback

- Clampar valores pode mascarar um erro de lógica da IA (ex.: RPE 4 em um
  treino de força) — o plano sai com RPE 5, que é o mínimo aceitável. Se a IA
  repetir valores estranhos, o diagnóstico do fallback (H3-21) continua
  registrando `schema_validation` quando o clamp não bastar.
- Rollback: remover a chamada a `_normalize_exercise_fields` e o comportamento
  volta ao anterior (schema_validation → fallback total).
