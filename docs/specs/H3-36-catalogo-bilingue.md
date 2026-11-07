# H3-36 — Catálogo bilíngue PT/EN (mesmos IDs)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-36` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

**Origem:** avaliação de 2026-11-07 do dataset
[gugeldev/exercicios-bd-ptbr](https://github.com/gugeldev/exercicios-bd-ptbr)
(fork CC0 do free-exercise-db) + auditoria UX/UI ([relatório](../auditoria-ux-ui-2026-11-07.md) §P4.9/P4.11).

## Objetivo

Exercícios têm nome e instruções em português (e continuam em inglês quando
pedido), com o **mesmo `exerciseId`** — planos novos nascem no idioma do
usuário sem quebrar contrato, backup, sync ou imagens.

## Contexto (estado atual verificado)

- O dataset PT-BR é tradução do **mesmo** dataset do backend
  (`back/app/exercise_catalog.json`, 876 exercícios); paridade medida:
  **873/876 IDs idênticos** (faltam `Kettlebell_Halo`,
  `Kettlebell_Halo_With_Overhead_Extension`,
  `Kettlebell_Overhead_Triceps_Extension`); paths de imagem idênticos.
- A versão **partial-translation** mantém `equipment`/`level`/`primaryMuscles`
  em inglês — necessário, pois `_build_pool`, `MUSCLE_ALIASES` e os filtros do
  complementar (H3-16) comparam valores em inglês. A versão *full* quebraria
  o pool silenciosamente.
- Imagens continuam no `IMAGE_BASE_URL` do yuhonas
  (`ExerciseMediaRepository.kt:53`) — o fork não redistribui imagens.
- Nomes são **dados** persistidos no plano (`exerciseName` em
  `Entities.kt:72,137`) — a chave em todo o sistema é o ID.
- `id_name_mismatches` (H3-22) compara `exerciseName` com o nome do catálogo.

## Fora de escopo

- Traduzir a UI do app (H3-37); retrotraduzir planos antigos (histórico
  preserva o idioma da época); revisar as 873 traduções uma a uma
  (amostragem do pool de força apenas); Wear.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/tools/build_bilingual_catalog.py` (novo) | baixa o partial PT-BR, faz merge por ID, valida paridade, escreve o catálogo |
| `back/app/exercise_catalog.json` | regenerado: `namePt: string|null`, `instructionsPt: string[]|null` aditivos |
| `back/app/main.py` | `GenerateRequest.language: Optional[Literal["pt","en"]]` (ausente ⇒ `"en"`); prompt/fallback no idioma pedido; `id_name_mismatches` compara EN **e** PT |
| `back/app/plan_builder.py` | `build_strength_session` usa `namePt` quando `language="pt"`; `catalog_summary_for_request` lista nomes no idioma |
| `contracts/generate_request.json`, `generate_request_complementary.json` | campo `language` no fixture |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GeneratePlanRequest.language`; catálogo com `namePt`/`instructionsPt` (nullable) |
| `app/src/main/java/com/treinos/app/data/media/ExerciseMediaRepository.kt` | resolver instruções por idioma da preferência |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | `language` (default `"pt"`) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | enviar `language` na geração |

## Modelo de dados / contrato

- Catálogo (aditivo, retrocompatível): `name`/`instructions` continuam EN
  canônicos; `namePt`/`instructionsPt` são `null` nos 3 exercícios sem
  tradução (nunca inventar).
- `GenerateRequest.language`: ausente ⇒ `"en"` (comportamento atual
  preservado para backend antigo/app antigo); `"pt"` ⇒ `exerciseName` e
  `instructions` do plano em português.
- App↔backend: nenhum campo removido; `ignoreUnknownKeys` já tolera as duas
  pontas durante o rollout. Backup/sync: sem mudança (nome é dado).

## Regras e casos de borda

- Merge determinístico por ID: presente no PT-BR → campos PT preenchidos;
  ausente → `null`. O script **valida**: 876 IDs, nenhum ID novo/renomeado,
  campos de filtro ainda em inglês, zero `namePt` vazio-não-nulo.
- Prompt da IA lista nomes no idioma do request; a checagem
  `id_name_mismatches` aceita o nome em EN **ou** PT do mesmo ID (mapa
  duplo) — sem isso, todo plano em PT viraria falso positivo.
- Fallback local (`build_strength_session`) usa `namePt` quando
  `language="pt"` — planos sem IA também nascem em PT.
- App: preferência `language` default `"pt"` — já entrega conteúdo PT mesmo
  antes do seletor (H3-37); catálogo cacheado decodifica `namePt` ausente
  como `null` → usa EN (backend antigo).
- Imagens: inalteradas (paths idênticos).

## Critérios de aceite

1. **Dado** `language = "pt"`, **quando** gera um plano (IA ou fallback),
   **então** os exercícios chegam com nome PT ("Supino Reto com Barra - Pegada
   Média") e o `exerciseId` inalterado.
2. **Dado** `language` ausente (app antigo), **quando** gera, **então** os
   nomes continuam em inglês (comportamento atual).
3. **Dado** o catálogo regenerado, **quando** o script roda, **então** valida
   876 IDs, 873 com PT, 3 com `null`, filtros em EN — e falha se algo divergir.
4. **Dado** plano gerado em PT, **quando** abre o detalhe do exercício,
   **então** as instruções aparecem em português (catálogo ou plano).
5. **Dado** IA respondendo nome em PT, **quando** `id_name_mismatches` roda,
   **então** não há falso positivo (o ID bate com o mapa duplo).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_catalog_bilingual.py` | `paridade_876_ids`, `tres_sem_traducao_null`, `campos_de_filtro_em_ingles` |
| `back/tests/test_language_param.py` | `language_pt_fallback_nomes_pt`, `language_ausente_en`, `mismatch_aceita_nome_pt` |
| `app/src/test/java/.../ContractTest.kt` | `language_no_request_fixture` |
| `app/src/test/java/.../ExerciseMediaTest.kt` | `instrucoes_por_idioma`, `namept_ausente_cai_para_en` |

## Verificação manual

1. Subir o backend atualizado → gerar plano no app → nomes/instruções em PT.
2. Botão "Atualizar catálogo" em Ajustes → detalhe do exercício em PT.
3. Conferir um plano antigo → permanece como estava (idioma da época).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Tradução comunitária pode ter erros pontuais — amostrar o pool de força e
  corrigir no JSON (o script permite override local). O fork pode defasar do
  original — o script é re-rodável. Rollback: regenerar o catálogo sem os
  campos PT (script com flag) ou reverter o JSON; contrato é aditivo.
