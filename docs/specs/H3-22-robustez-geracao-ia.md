# H3-22 — Robustez da geração por IA (fim do fallback estrutural)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-22` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

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

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/plan_builder.py` | Nova `catalog_summary_for_request(request, catalog) -> str`: resumo por músculo com **cap 20 por grupo**, ordenado por relevância (grupos de `targetMuscles`/`focusMuscles` primeiro), só exercícios de força (`_is_strength_exercise`) do pool de equipamento/nível do request |
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
  - resposta da IA sem nenhum exercício aproveitável
    ⇒ fallback total (source = "local"), fallbackReason = "unresolved_ids".
```

## Regras e casos de borda

- IDs sintéticos `run_*`/`mobility_*` continuam fora da validação de catálogo
  (comportamento atual de `resolve_plan_ids`, preservado).
- Dia de força reconstruído localmente usa o pool do request (equipamento +
  nível + foco), nunca exercício fora do filtro de equipamento.
- `catalog_summary_for_request` nunca lista exercício de equipamento que o
  usuário não informou (senão a IA passa a usá-lo).
- Retry: no máximo 1; só em `json_parse`, HTTP 5xx e 429; 4xx de
  autenticação/parametro não repete (falha determinística).
- Timeout total da geração continua abaixo do read timeout do app (60 s +
  retry ≤ ~125 s < 90 s do app? **não** — com retry o pior caso passa de 90 s:
  o retry só vale para `json_parse` (resposta já recebida) e o retry de rede
  usa timeout de 30 s; pior caso ≈ 60 + 30 = 90 s. Documentar o limite no
  código; se estourar, o app mostra erro de conexão e o usuário repete).
- Prompt novo medido antes do merge: ≤ ~6k tokens de catálogo; se estourar,
  reduzir cap por músculo antes de mudar de abordagem.
- `normalize_hybrid_plan` continua rodando depois do salvamento parcial
  (invariantes de sessão por modalidade preservados).

## Critérios de aceite

1. **Dado** um conteúdo de IA embrulhado em ```json ... ```, **quando** o parse roda, **então** o plano é extraído sem fallback.
2. **Dado** uma resposta com 1 ID inventado entre 6 exercícios de força, **quando** o endpoint processa, **então** o plano é aceito com 5 exercícios, o ID inválido é removido e o log registra `dropped_ids`.
3. **Dado** uma sessão que fica com 2 exercícios após a remoção, **quando** o salvamento parcial roda, **então** a sessão é reconstruída localmente com ≥ 3 exercícios do pool do request.
4. **Dado** uma resposta em que **todos** os exercícios de força têm ID inválido, **quando** o endpoint processa, **então** fallback total com `fallbackReason = "unresolved_ids"`.
5. **Dado** o resumo do prompt, **quando** o request tem `targetMuscles = ["abs"]`, **então** os grupos do foco aparecem primeiro e cada grupo lista até 20 IDs (não 8).
6. **Dado** um HTTP 429 na primeira tentativa, **quando** o endpoint gera, **então** há exatamente 1 retry e, se a segunda tentativa passar, `source = "ai"`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_ai_robustez.py` | `test_parse_remove_cercas_de_codigo`, `test_parse_extrai_objeto_balanceado`, `test_id_invalido_e_removido_e_sessao_mantida`, `test_sessao_com_menos_de_tres_e_reconstruida`, `test_todos_invalidos_vira_fallback_total`, `test_resumo_prioriza_foco_e_cap_vinte`, `test_resumo_nao_lista_equipamento_fora_do_request`, `test_retry_unico_em_429` |
| `back/tests/test_hybrid_plan.py` | regressão do fallback local (existente, sem mudança) |

## Verificação manual

1. Com a chave real, gerar 5 planos seguidos (main e complementar): contar quantos vêm "com IA" — meta ≥ 4 de 5.
2. Forçar uma resposta inválida (mock temporário ou chave inválida) e conferir o `[fallback] reason=` no log.
3. Conferir no app que o plano vindo de IA tem exercícios coerentes com o pedido (não aleatórios do catálogo).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Prompt maior pode degradar a latência do flash-lite: medir `generationTimeMs`
  antes/depois; rollback = voltar o cap para 8 por músculo (um commit).
- Salvamento parcial pode produzir sessões "costuradas" (IA + local) com
  desequilíbrio de volume: a reconstrução usa o mesmo pool e o mesmo nº alvo
  por duração; teste cobre o caso limite.
- Pior caso de latência com retry pode passar do read timeout do app (90 s):
  regra de retry documentada no código e limitada; se o log mostrar estouro,
  reduzir o timeout da primeira tentativa para 45 s.
