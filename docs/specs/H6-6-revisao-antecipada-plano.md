# H6-6 — Revisão antecipada do plano com base na evolução

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-6` (Horizon 6; pedido do dono em 2026-11-18) |
| MoSCoW | `M` |
| Versão alvo | `1.11.0` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-18) |
| Implementação | `pendente` |
| Depende de | Nada (reusa o fluxo de revisão da H2-6 e o histórico da H3-5/H3-42) |

## Objetivo

O usuário pode pedir uma revisão do treino **quando quiser** — não só quando
as semanas do plano vencem — e a IA recebe o que ele realmente fez até ali
(sessões, aderência, RPE, cargas-alvo) para propor o próximo ciclo com base na
evolução real, antes do prazo.

## Fora de escopo

- Trocar o contrato `/v1/review-plan` além do campo aditivo opcional
  (`exerciseWeights`, ver Regra 4) — o endpoint e a proposta/aceite existem.
- Progressão automática de carga (H4-12 é opt-in por exercício e continua).
- Gerar plano do zero (o fluxo de geração nova é o da H3-30/H4-10).
- Revisão do plano complementar (`extra`) — a revisão continua apontando para
  o `main` (decisão da H3-15).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `reviewPlan()`: janela de histórico passa a ser **desde o início do plano** (corte = `plan.startedAt`, teto de 28 dias por chamada em janelas menores quando o plano é mais novo); `acceptReview()` computa `nextReviewAt = now + reviewAfterWeeks` em vez de reaproveitar a data antiga; novo `reviewNow()` wrapper público (ou `reviewPlan()` já é público — expor à UI) |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | `ReviewPlanCard` visível sempre que houver plano `main`: quando `reviewIsDue` mantém a copy atual; quando **antecipada**, copy própria ("Revisão antecipada — o plano vence em X") e o card continua com Revisar/Adiar; "Revisar agora" também no menu ⋮ do `ActivePlanCard` |
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | `ReviewPlanRequest` ganha `exerciseWeights: Map<String, Int>?` opcional (H3-42) quando houver pesos registrados — aditivo |
| `back/app/main.py` | `GenerateReviewRequest.exerciseWeights: Optional[Dict[str, int]] = None`; quando presente, `catalog_summary`/prompt da revisão ordena os grupos pelos pesos (mesma regra da H3-42 na geração) |
| `back/tests/test_review_plan.py` (ou o arquivo existente do review) | Casos do campo novo + janela de histórico |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | `review_early_title`, `review_early_desc`, `review_menu_now`, mensagens de resultado reusadas — paridade obrigatória |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | Card de revisão antecipada visível e acionável antes do prazo |

## Modelo de dados / contrato

```text
POST /v1/review-plan (existente)
GenerateReviewRequest {
  ... (deviceId, plan, history — já existem)
  exerciseWeights: Optional[Dict[str, int]] = None   # NOVO, aditivo
}
GenerateReviewResponse { workout, changes, source }   # intocado
```

- Compatibilidade: campo novo é opcional; backend antigo ignora
  (`ignoreUnknownKeys` no app; Pydantic ignora extra no backend antigo).
- Room: nenhuma migration. `nextReviewAt` do novo plano é calculado no app
  (`now + reviewAfterWeeks`) — sem campo novo no contrato de resposta.

## Regras e casos de borda

1. **Gatilho sempre disponível**: com plano `main` ativo, "Revisar agora"
   aparece no menu ⋮ do `ActivePlanCard` e o `ReviewPlanCard` fica visível
   (copy de antecipada quando `!reviewIsDue`); sem plano, nada aparece.
2. **Janela de histórico**: corte = início do plano atual (`startedAt` do
   plano; se ausente, 28 dias); a aderência e as sessões cobrem o que foi
   feito **desde o plano vigente** — é isso que dá a "evolução" para a IA.
3. **Aceite reagenda**: `acceptReview()` grava o novo `main` com
   `nextReviewAt = now + reviewAfterWeeks` (do novo plano) — hoje reaproveita
   a data antiga e a revisão nasceria "atrasada" (bug corrigido aqui).
4. **Pesos de uso**: se `usageWeightsForCatalog` (H3-42) tiver pesos > 0,
   o request envia `exerciseWeights`; vazio/ausente = campo omitido.
5. **Conflito com geração**: revisão e geração assíncrona (H3-40) não rodam
   ao mesmo tempo — se houver job pendente, "Revisar agora" mostra o estado
   ocupado (mesmo guard de `_isReviewing`).
6. **Descartar**: `discardReview()` continua; descartar NÃO reagenda
   `nextReviewAt` (o plano segue com o prazo original).
7. **Adiar**: `postponeReview` (H3-5) continua disponível no card.
8. **Offline**: falha de rede vira `uiErrorMessage` com "Tentar de novo"
   (padrão H3-29); nada é alterado no plano sem aceite explícito.

## Critérios de aceite

1. **Dado** um plano `main` com revisão marcada para daqui a 2 semanas,
   **quando** abro a Home e toco "Revisar agora", **então** a revisão roda
   com o histórico desde o início do plano e a proposta aparece.
2. **Dado** a proposta de revisão, **quando** aceito, **então** o novo `main`
   fica ativo com `nextReviewAt = hoje + reviewAfterWeeks` semanas e o card
   de revisão atrasada desaparece.
3. **Dado** que descarto a proposta, **quando** volto à Home, **então** o
   plano original segue intacto com o prazo original.
4. **Dado** um plano com 10 dias de uso, **quando** reviso antecipadamente,
   **então** o `history` enviado cobre exatamente esses 10 dias (não 28
   vazios) e a aderência reflete as sessões desse período.
5. **Dado** pesos de uso registrados (H3-42), **quando** reviso,
   **então** o request inclui `exerciseWeights` e o backend os usa no
   summary; sem pesos, o campo não vai.
6. **Dado** backend fora do ar, **quando** reviso, **então** erro acionável
   com retry e plano intacto.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | `card_revisao_antecipada_visivel_antes_do_prazo`, `menu_revisar_agora_dispara_revisao` |
| `app/src/test/java/com/treinos/app/ui/AppViewModelReviewTest.kt` (novo ou o existente do review) | `janela_de_historico_cobre_desde_o_inicio_do_plano`, `accept_reagenda_next_review_at`, `discard_mantem_prazo_original`, `exercise_weights_vao_no_request_quando_existem` |
| `back/tests/test_review_plan.py` | `exercise_weights_opcional_Ordena_summary`, `request_sem_weights_ignora` |
| `app/src/test/java/com/treinos/app/ui/ContractTest.kt` | fixture do review com `exerciseWeights` (dos dois lados, se o contrato de fixtures cobrir) |

## Verificação manual

1. Plano novo (1 semana de uso): Home → "Revisar agora" → proposta coerente
   com o que foi feito → aceitar → conferir novo prazo na Home.
2. Descartar a proposta e conferir o plano original.
3. Revisão antecipada com backend offline: erro com retry.
4. Com pesos de uso (pular/trocar exercícios na semana): revisar e conferir
   no log do backend que o summary ordenou pelos pesos.
5. Adiar revisão pelo card e conferir a nova data.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **IA propondo mudança ruim em revisão antecipada**: o fluxo é proposto →
  aceitável → descartável; nada muda sem aceite (mesma proteção de hoje).
- **Bug do `nextReviewAt` reusado**: a correção muda o comportamento do
  aceite também na revisão no prazo (para o correto: reagenda do zero) —
  documentado na spec e coberto por teste.
- **Campo aditivo no backend**: versão antiga do app não envia; versão nova
  com backend antigo envia campo ignorado — sem quebra.
- Rollback: remover o gatilho da UI; o fluxo de revisão volta ao estado
  atual (só quando due). Nenhuma migration.
