# H3-21 — Diagnóstico do fallback da IA (o app diz o porquê)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-21` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-07` |
| Implementação | `concluída` |

**Ordem:** primeiro fix da fila de correções. Os demais (H3-22, H3-23, H3-24)
dependem deste para confirmar a causa real no backend do usuário antes de
mexer no prompt/timeout.

## Objetivo

Quando o plano vier do fallback local, o app e o log do backend dizem **por
quê** — fim do "caiu no fallback" sem explicação. Hoje é impossível saber se a
causa foi timeout, JSON inválido, ID inventado ou erro HTTP, e isso bloqueia a
correção da causa raiz (H3-22).

## Contexto (estado atual verificado)

- `back/app/main.py` L688–746: qualquer falha da IA (sem chave, HTTP ≠ 200,
  `json.loads` falhando, IDs não resolvíveis em `resolve_plan_ids` L717–722,
  validação do `WorkoutPlanDTO`) cai no **mesmo** `except`, que só imprime no
  stdout do servidor. A resposta ao app não carrega o motivo.
- `GenerateResponse` (L415–423) tem só `source`/`modelUsed`; o app mostra
  "Treino gerado localmente" (`AppViewModel.kt` L579) sem o porquê.
- O `detail` da exceção chega a incluir a resposta bruta da IA (L743), mas só
  no log do servidor, truncado em 500 chars e sem rótulo do tipo de falha.
- Hipóteses de causa a confirmar com este diagnóstico (evidência em código):
  prompt expõe 136 dos 876 IDs (`main.py` L57–73 corta em 8 por músculo) mas
  exige IDs exatos; timeout de 30 s (L690); `max_tokens` 8192 truncando JSON;
  `response_format`/`reasoning` rejeitados pela API.

## Fora de escopo

- Corrigir as causas (prompt, timeout, salvamento parcial) — é a H3-22.
- Retry de chamada à IA.
- Telemetria externa/serviço de monitoramento; o log continua local (stdout).
- Mudar o texto de UI além de acrescentar o motivo.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/main.py` | Extrair `parse_ai_plan(content, catalog) -> dict` (função pura, sem rede) que lança `AiFailure(reason, detail)`; o endpoint captura `fallback_reason`/`fallback_detail` por tipo de falha e loga `"[fallback] reason=<código> detail=<texto ≤300>"` |
| `back/app/main.py` | `GenerateResponse.fallbackReason: str = ""` (aditivo) |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GeneratePlanResponse.fallbackReason: String? = null` (aditivo; backend antigo sem o campo decodifica `null`) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Mensagem de geração passa a citar o motivo quando `source == "local"` (ex.: "Treino gerado localmente — motivo: resposta da IA sem IDs válidos") |
| `contracts/generate_response.json` | Fixture ganha `fallbackReason` (contrato validado dos dois lados) |
| `back/tests/test_fallback_diagnostics.py` (novo) | Testes da função pura e do endpoint sem chave |
| `app/src/test/java/com/treinos/app/ContractTest.kt` | Decodificação do campo novo |

## Modelo de dados / contrato

```text
GenerateResponse (backend) / GeneratePlanResponse (app) — aditivo:
  fallbackReason: str = ""     // vazio quando source == "ai"

Valores fechados de fallbackReason:
  no_api_key        SENSENOVA_API_KEY ausente
  http_error        API respondeu != 200 (detail traz o status)
  timeout           httpx.TimeoutException / erro de rede
  json_parse        conteúdo não é JSON (inclui JSON truncado)
  unresolved_ids    IDs de musculação que não existem no catálogo
  schema_validation WorkoutPlanDTO rejeitou a resposta
  unknown           qualquer outra exceção

Regras do contrato:
  - campo aditivo: app com `ignoreUnknownKeys` continua decodificando
    respostas de backend antigo (fallbackReason = null ⇒ UI omite o motivo);
  - detail nunca contém a chave da API nem dados pessoais; truncado em 300 chars.
```

## Regras e casos de borda

- `source == "ai"` ⇒ `fallbackReason` vazio, sempre.
- `source == "local"` ⇒ `fallbackReason` nunca vazio (no mínimo `unknown`).
- `parse_ai_plan` é pura: recebe o texto e o catálogo, não faz rede — cada tipo
  de falha vira um teste sem HTTP.
- Falha de rede na chamada HTTP e falha de parse são códigos distintos
  (`timeout`/`http_error` vs `json_parse`).
- O log estruturado usa prefixo fixo `[fallback]` para facilitar o grep no
  servidor; nada é gravado no banco.
- A resposta de erro HTTP da IA (`response.text`) só entra no `detail`
  truncado; nunca a chave nem o prompt completo.

## Critérios de aceite

1. **Dado** um backend sem `SENSENOVA_API_KEY`, **quando** `POST /v1/generate-workout`, **então** a resposta tem `source = "local"`, `fallbackReason = "no_api_key"` e o plano local válido de sempre.
2. **Dado** um conteúdo de IA com JSON inválido, **quando** `parse_ai_plan` roda, **então** lança `AiFailure("json_parse", ...)` e o endpoint responde `fallbackReason = "json_parse"` com plano local no lugar.
3. **Dado** uma resposta da IA com IDs de musculação inexistentes no catálogo, **quando** o endpoint valida, **então** `fallbackReason = "unresolved_ids"` e o `detail` lista até 5 IDs.
4. **Dado** uma resposta da IA válida, **quando** o endpoint responde, **então** `source = "ai"` e `fallbackReason` vazio.
5. **Dado** o app recebendo `source = "local"` com `fallbackReason`, **quando** a geração termina, **então** a mensagem da Home/tela de geração cita o motivo em português.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_fallback_diagnostics.py` | `test_sem_chave_responde_no_api_key`, `test_json_invalido_vira_json_parse`, `test_ids_inexistentes_vira_unresolved_ids`, `test_resposta_valida_nao_tem_fallback_reason`, `test_detail_truncado_em_300` |
| `app/src/test/java/com/treinos/app/ContractTest.kt` | `fixture_de_resposta_com_fallback_reason_decodifica`, `resposta_sem_fallback_reason_decodifica_null` |

## Verificação manual

1. Derrubar a chave (`SENSENOVA_API_KEY=` vazio no `back/.env`), gerar um plano no app: a mensagem final cita "motivo: chave da IA não configurada".
2. Restaurar a chave e gerar: mensagem volta a dizer "com IA" e sem motivo.
3. `docker logs` (ou stdout do backend) mostra linha `[fallback] reason=...` quando houver queda.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Campo novo no contrato: aditivo e opcional dos dois lados; fixture atualizado
  no mesmo commit. Rollback = remover o campo e o log estruturado (um commit).
- Risco de vazar conteúdo sensível no `detail`: mitigado por truncamento e
  regra de não incluir chave/prompt; teste cobre o truncamento.
