# H3-25 — Seleção de exercícios por nome com resolução determinística

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-25` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

**Depende da [H3-22](H3-22-robustez-geracao-ia.md)** (pool completo no prompt,
salvamento parcial e check ID↔nome — a resolução por nome reutiliza os três).

## Objetivo

Tirar do prompt a exigência de IDs exatos: a IA escolhe exercícios **pelo
nome** (o que LLMs erram menos que IDs sintéticos) e o backend resolve
nome→ID deterministicamente — cobertura total do catálogo sem depender de a
IA copiar 876 identificadores sem escorregar.

## Contexto (estado atual verificado)

- Mesmo com o pool completo (H3-22), o prompt continua pedindo `exerciseId`
  exato; o ID é a parte mais fácil de errar (underscore, variação, tradução).
- `resolve_plan_ids` (`plan_builder.py` L426–475) já resolve por **nome**:
  índice normalizado de IDs e nomes (`catalog_index`), `get_close_matches`
  com cutoff 0,72 e contenção de substring — a máquina de resolução existe.
- O check ID↔nome da H3-22 pega o caso "ID válido, exercício errado"; com a
  seleção por nome, esse check vira o validador primário do par.
- `ExerciseDTO.exerciseId` é obrigatório no schema de saída: a resolução por
  nome precisa acontecer **antes** da validação do `WorkoutPlanDTO`, dentro
  do caminho puro do parse.

## Fora de escopo

- Shortlist determinístico por sessão (40–80 candidatos): avaliação separada,
  só ganha spec própria se a H3-22 + H3-25 não bastarem.
- Menu em dois níveis (Tier A/B): plano B da H3-22, condicionado a medição.
- Trocar de modelo/provedor ou mudar o schema do plano de saída.
- Foco muscular (H3-23) e mídia (H3-24).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/plan_builder.py` | Nova `resolve_exercise_names(plan, catalog) -> Tuple[Dict[str, Any], List[str]]`: para exercício com `exerciseId` ausente/não resolvível e `exerciseName` presente, resolve nome→ID pela ordem determinística (exato normalizado → fuzzy ≥ cutoff → substring); devolve o plano preenchido e a lista de nomes não resolvíveis |
| `back/app/main.py` | `parse_ai_plan` roda `resolve_exercise_names` **antes** de `resolve_plan_ids`/validação do DTO; nome não resolvível segue o salvamento parcial da H3-22 (remoção + reconstrução da sessão) |
| `back/app/main.py` | `catalog_summary_for_request` passa a destacar o **nome canônico** como chave de seleção ("copie o nome exatamente como listado; o ID é opcional") |
| `back/tests/test_selecao_por_nome.py` (novo) | Testes da resolução pura e do parse tolerante a IDs ausentes |

## Modelo de dados / contrato

```text
Sem mudança no contrato de entrada/saída do endpoint (os campos são os mesmos).
Comportamental:
  - resposta da IA com exercícios só por nome (exerciseId ausente ou
    desconhecido) ⇒ nome resolvível vira exerciseId do catálogo e o plano
    segue com IA (source = "ai"), fallbackReason vazio;
  - nome não resolvível ⇒ exercício removido e sessão reconstruída
    (salvamento parcial da H3-22); só cai para fallback total se nada
    aproveitável sobrar (fallbackReason = "unresolved_ids");
  - exerciseId válido + exerciseName divergente ⇒ regra da H3-22
    (id_name_mismatch) continua valendo.
```

## Regras e casos de borda

- Ordem de resolução determinística e documentada: (1) nome exato normalizado;
  (2) `get_close_matches` com o mesmo cutoff 0,72 do `resolve_plan_ids`;
  (3) contenção de substring. Empate no fuzzy ⇒ primeira ocorrência no
  catálogo (ordem estável, nunca aleatória).
- Nome não resolvível **não** derruba o plano: entra no salvamento parcial
  (H3-22); a sessão reconstruída usa o pool do request.
- IDs sintéticos `run_*`/`mobility_*` continuam fora da resolução por nome
  (comportamento atual de `resolve_plan_ids`, preservado).
- O prompt instrui a copiar o nome **exatamente como listado**; nomes
  abreviados/traduzidos passam pelo fuzzy — o mesmo critério tolerante do
  check ID↔nome da H3-22, para não criar falso positivo em cima do próprio
  mecanismo de resolução.
- Plano em que **todos** os exercícios têm nome não resolvível ⇒ fallback
  total com `fallbackReason = "unresolved_ids"` (invariante da H3-21: local ⇒
  motivo sempre presente).

## Critérios de aceite

1. **Dado** um plano da IA com `exerciseName` correto e `exerciseId` vazio, **quando** `parse_ai_plan` processa, **então** o `exerciseId` é preenchido com o ID do catálogo e a resposta sai com `source = "ai"`.
2. **Dado** um nome com variação próxima (ex.: sem acento/abreviado), **quando** a resolução roda, **então** o match segue a ordem determinística (exato → fuzzy → substring) e é estável entre execuções.
3. **Dado** um nome que não existe no catálogo, **quando** o parse processa, **então** o exercício é removido, a sessão é reconstruída com ≥ 3 exercícios do pool e o log registra o nome descartado.
4. **Dado** um plano em que todos os nomes são não resolvíveis, **quando** o endpoint processa, **então** fallback total com `fallbackReason = "unresolved_ids"`.
5. **Dado** o resumo do prompt novo, **quando** a IA responde misturando exercícios com e sem `exerciseId`, **então** o plano sai completo e válido, sem mudança no contrato de saída.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_selecao_por_nome.py` | `test_nome_exato_resolve_para_id`, `test_nome_aproximado_segue_ordem_deterministica`, `test_nome_nao_resolvivel_e_removido_e_sessao_reconstruida`, `test_todos_nomes_invalidos_vira_fallback_total`, `test_plano_somente_por_nome_valida_no_dto`, `test_id_valido_com_nome_divergente_continua_sendo_descartado` |
| `back/tests/test_ai_robustez.py` | regressão do check ID↔nome e do salvamento parcial (existentes, sem mudança) |

## Verificação manual

1. Com a chave real, gerar 5 planos (main e complementar): conferir que
   exercícios escolhidos "por nome" chegam com `exerciseId` do catálogo e que
   a taxa "com IA" se mantém (meta ≥ 4 de 5, igual à H3-22).
2. Conferir no app que os exercícios do plano batem com os nomes pedidos
   (sem troca silenciosa de variante).
3. Conferir no log que nomes não resolvíveis aparecem no `dropped_ids` com o
   nome declarado pela IA.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Ambiguidade de nomes (variantes incline/decline, equipamentos diferentes com
  nome parecido): mitigado por mandar a IA copiar o nome canônico do resumo e
  pela ordem determinística de resolução; teste cobre o empate.
- Qualidade da escolha pode piorar se o nome canônico for genérico demais:
  medir nas 5 gerações da verificação manual antes de considerar pronto.
- Rollback = voltar o parse para o modo por ID da H3-22 (um commit); o
  contrato de saída não muda, então o app não precisa de rollback.
