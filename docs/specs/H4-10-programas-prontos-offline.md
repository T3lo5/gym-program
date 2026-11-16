# H4-10 — Biblioteca de programas prontos offline

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-10` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |

## Objetivo

Começar a treinar sem IA e sem backend, escolhendo um programa comprovado
(PPL, 5/3/1, GZCLP ou PHUL) que já vem pronto, com faixas de repetição e regra
de progressão definidas, e que vira um plano normal do app.

## Fora de escopo

- Editar o programa antes de aplicar: depois de salvo, valem os fluxos
  existentes (troca de exercício H3-41, geração complementar H3-16, exclusão de
  extra H3-15).
- Novos programas, versões femininas/iniciante, PDFs, vídeos ou textos longos
  de metodologia.
- Cálculo de 1RM ou percentual de carga (H4-1/H4-5): os templates usam faixas de
  reps, RPE alvo e uma nota textual de progressão.
- Baixar/sincronizar programas do backend; nenhuma chamada de rede.
- Mudar o `ProgressionEngine` (H3-5): a progressão embutida é faixa de reps +
  nota; o motor continua derivando do histórico real.
- Programas no Wear OS (o relógio só espelha a sessão em execução).
- Novos exercícios no catálogo (`app/src/main/assets/exercise_swap_catalog.json`).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/programs/ProgramLibrary.kt` | **Novo.** Catálogo puro e determinístico: `ProgramTemplate`, `ProgramExercise`, `ProgramDay`, `programCatalog()`, `buildProgram(id, nextReviewAt, reviewAfterWeeks)` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `applyProgram(templateId: String)`: monta o `GeneratePlanResponse` local, salva via `repository.saveGeneratedPlan(response, kind = "main")`, seleciona o plano e emite snackbar; nunca chama `submitGeneration` |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Seção "Programas prontos" no topo da lista, com um card por template e ação "Usar este programa"; novo parâmetro `onApplyProgram: (String) -> Unit`; diálogo de confirmação quando já existe um `main` ativo |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Liga `onApplyProgram = viewModel::applyProgram` na chamada de `GenerateScreen` e passa `hasMainPlan` |
| `app/src/main/res/values/strings.xml` | Strings da seção, dos 4 programas e da confirmação |
| `app/src/main/res/values-en/strings.xml` | Mesmas chaves (paridade cobrada por `StringParityTest`) |
| `app/src/test/java/com/treinos/app/ui/programs/ProgramLibraryTest.kt` | **Novo.** Determinismo, estrutura e limites dos templates |
| `app/src/test/java/com/treinos/app/ui/programs/ProgramCatalogAssetTest.kt` | **Novo.** Todo `exerciseId` dos templates existe no asset e os campos batem |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | Aplicar programa salva como `main`, desativa o anterior e preserva extras/histórico |
| `app/src/test/java/com/treinos/app/ui/screens/GenerateScreenTest.kt` | Seção visível, callback no toque e cancelamento da confirmação |

Não edite `shared/` nem `com.treinos.shared/` (legado). Sem dependência nova,
sem migration e sem mudança de backend.

## Modelo de dados / contrato

Nada de schema novo: o programa vira um `GeneratePlanResponse` e entra pelo
caminho já existente `WorkoutRepository.saveGeneratedPlan(response, kind)`
(H3-15), que grava `WorkoutPlanEntity` + `WorkoutDayEntity` +
`WorkoutPlanExerciseEntity`.

```kotlin
// app/src/main/java/com/treinos/app/ui/programs/ProgramLibrary.kt — puro, sem Android
data class ProgramTemplate(
    val id: String,              // "ppl" | "531" | "gzclp" | "phul"
    val name: String,            // "Push/Pull/Legs"
    val description: String,     // 1 frase
    val goal: String,            // "hypertrophy" | "strength"
    val level: String,           // "beginner" | "intermediate" | "advanced"
    val daysPerWeek: Int,        // == days.size
    val equipment: List<String>, // equipamento mínimo
    val progressionNote: String, // regra de progressão embutida, em PT
)

data class ProgramExercise(
    val exerciseId: String,
    val exerciseName: String,    // nome do catálogo (asset v1)
    val primaryMuscles: String,  // "chest" ou "chest, triceps"
    val equipment: String,       // "barbell", "dumbbell", "body only"...
    val sets: Int,
    val targetReps: String,      // "5", "3-5", "8-12" — sempre parseável pelo motor
    val targetRpe: Double?,      // 8.0 / 9.0 ou null
    val restSeconds: Int,
    val notes: String = "",      // progressão embutida daquele exercício
)

data class ProgramDay(
    val name: String,
    val dayOrder: Int,
    val exercises: List<ProgramExercise>,
)

fun programCatalog(): List<ProgramTemplate>

fun buildProgram(
    id: String,
    nextReviewAt: String,
    reviewAfterWeeks: Int = 4,
): GeneratePlanResponse?  // null para id desconhecido
```

`buildProgram` devolve `GeneratePlanResponse(workout = WorkoutPlanDto(...),
source = "local", nextReviewAt = nextReviewAt, reviewAfterWeeks =
reviewAfterWeeks)`. O `saveGeneratedPlan` já deriva `generatedByAi = false`
(`source != "ai"`), `exerciseOrder` pela posição e `planId`/`dayId` no Room.

### Conteúdo determinístico dos 4 programas

Ordem fixa do catálogo: `ppl`, `531`, `gzclp`, `phul`.

**PPL — 6 dias, hipertrofia**

| Dia | Exercícios (id — séries × reps, RPE, descanso) |
|---|---|
| Push A | `Barbell_Bench_Press_-_Medium_Grip` 4×6-8 RPE 8 150s; `Standing_Military_Press` 3×8-10 RPE 8 120s; `Barbell_Incline_Bench_Press_-_Medium_Grip` 3×8-10 RPE 8 120s; `Side_Lateral_Raise` 3×12-15 RPE 9 60s; `Triceps_Pushdown` 3×10-12 RPE 9 60s |
| Pull A | `Barbell_Deadlift` 3×5 RPE 8 180s; `Pullups` 3×6-10 RPE 9 120s; `Bent_Over_Barbell_Row` 3×8-10 RPE 8 120s; `Face_Pull` 3×12-15 RPE 9 60s; `Barbell_Curl` 3×10-12 RPE 9 60s |
| Legs A | `Barbell_Squat` 4×6-8 RPE 8 180s; `Romanian_Deadlift` 3×8-10 RPE 8 120s; `Leg_Press` 3×10-12 RPE 9 90s; `Lying_Leg_Curls` 3×10-12 RPE 9 60s; `Standing_Calf_Raises` 4×12-15 RPE 9 60s |
| Push B | `Standing_Military_Press` 4×6-8 RPE 8 150s; `Dumbbell_Bench_Press` 3×8-10 RPE 8 120s; `Cable_Crossover` 3×12-15 RPE 9 60s; `Side_Lateral_Raise` 3×12-15 RPE 9 60s; `Lying_Triceps_Press` 3×10-12 RPE 9 60s |
| Pull B | `Chin-Up` 3×6-10 RPE 9 120s; `Seated_Cable_Rows` 3×8-10 RPE 8 120s; `Face_Pull` 3×12-15 RPE 9 60s; `Hammer_Curls` 3×10-12 RPE 9 60s; `Barbell_Shrug` 3×12-15 RPE 9 60s |
| Legs B | `Front_Squat_Clean_Grip` 4×6-8 RPE 8 180s; `Barbell_Hip_Thrust` 3×8-10 RPE 8 120s; `Leg_Extensions` 3×10-12 RPE 9 60s; `Seated_Leg_Curl` 3×10-12 RPE 9 60s; `Seated_Calf_Raise` 4×12-15 RPE 9 60s |

Progressão embutida: "Adicione 2,5 kg quando fechar o topo da faixa nas duas
últimas sessões; o app sugere a carga no diálogo de série."

**5/3/1 — 4 dias, força**

| Dia | Exercícios |
|---|---|
| Agachamento | `Barbell_Squat` 3×5 RPE 8 180s (nota "Principal 5/3/1: 3×5 na semana 1; +2,5 kg no próximo ciclo"); `Leg_Press` 5×10 RPE 9 90s; `Lying_Leg_Curls` 5×10 RPE 9 60s; `Hanging_Leg_Raise` 5×10 RPE 9 60s |
| Supino | `Barbell_Bench_Press_-_Medium_Grip` 3×5 RPE 8 150s (mesma nota); `Dumbbell_Bench_Press` 5×10 RPE 9 90s; `Seated_Cable_Rows` 5×10 RPE 9 90s; `Triceps_Pushdown` 5×10 RPE 9 60s |
| Levantamento terra | `Barbell_Deadlift` 3×5 RPE 8 180s (mesma nota); `Romanian_Deadlift` 5×10 RPE 9 90s; `Leg_Extensions` 5×10 RPE 9 60s; `Crunches` 5×10 RPE 9 60s |
| Desenvolvimento | `Standing_Military_Press` 3×5 RPE 8 150s (mesma nota); `Dumbbell_Shoulder_Press` 5×10 RPE 9 90s; `Pullups` 5×10 RPE 9 90s; `Barbell_Curl` 5×10 RPE 9 60s |

Progressão embutida: "Suba 2,5 kg no principal a cada ciclo concluído; a
assistência fica em 5×10 e só sobe quando as 5 séries fecharem."

**GZCLP — 4 dias, força/hipertrofia**

| Dia | Exercícios |
|---|---|
| A1 | `Barbell_Squat` 5×3 RPE 8 180s (T1); `Barbell_Bench_Press_-_Medium_Grip` 3×10 RPE 8 120s (T2); `Wide-Grip_Lat_Pulldown` 3×15 RPE 9 60s (T3); `Seated_Cable_Rows` 3×15 RPE 9 60s (T3) |
| B1 | `Standing_Military_Press` 5×3 RPE 8 150s (T1); `Barbell_Deadlift` 3×10 RPE 8 150s (T2); `Dumbbell_Flyes` 3×15 RPE 9 60s (T3); `Face_Pull` 3×15 RPE 9 60s (T3) |
| A2 | `Barbell_Bench_Press_-_Medium_Grip` 5×3 RPE 8 150s (T1); `Barbell_Squat` 3×10 RPE 8 150s (T2); `Triceps_Pushdown` 3×15 RPE 9 60s (T3); `Side_Lateral_Raise` 3×15 RPE 9 60s (T3) |
| B2 | `Barbell_Deadlift` 5×3 RPE 8 180s (T1); `Standing_Military_Press` 3×10 RPE 8 120s (T2); `Barbell_Curl` 3×15 RPE 9 60s (T3); `Hanging_Leg_Raise` 3×15 RPE 9 60s (T3) |

Progressão embutida: "T1 sobe 2,5 kg (5 kg no terra/agachamento) quando fechar
5×3; falhou, reinicia em 5×2 e depois 6×1. T2/T3 sobem quando fecharem as 3
séries no topo."

**PHUL — 4 dias, potência + hipertrofia**

| Dia | Exercícios |
|---|---|
| Upper Power | `Barbell_Bench_Press_-_Medium_Grip` 4×3-5 RPE 8 180s; `Bent_Over_Barbell_Row` 4×3-5 RPE 8 150s; `Barbell_Incline_Bench_Press_-_Medium_Grip` 3×6-10 RPE 8 120s; `Seated_Cable_Rows` 3×6-10 RPE 8 120s; `Standing_Military_Press` 3×6-10 RPE 8 120s; `Barbell_Curl` 3×6-10 RPE 9 60s; `Lying_Triceps_Press` 3×6-10 RPE 9 60s |
| Lower Power | `Barbell_Squat` 4×3-5 RPE 8 180s; `Barbell_Deadlift` 4×3-5 RPE 8 180s; `Leg_Press` 3×6-10 RPE 9 120s; `Lying_Leg_Curls` 3×6-10 RPE 9 60s; `Standing_Calf_Raises` 4×6-10 RPE 9 60s |
| Upper Hypertrophy | `Barbell_Incline_Bench_Press_-_Medium_Grip` 4×8-12 RPE 9 90s; `Dumbbell_Flyes` 4×8-12 RPE 9 60s; `Seated_Cable_Rows` 4×8-12 RPE 9 90s; `Wide-Grip_Lat_Pulldown` 4×8-12 RPE 9 90s; `Side_Lateral_Raise` 3×8-12 RPE 9 60s; `Hammer_Curls` 3×8-12 RPE 9 60s; `Triceps_Pushdown` 3×8-12 RPE 9 60s |
| Lower Hypertrophy | `Barbell_Full_Squat` 4×8-12 RPE 9 120s; `Romanian_Deadlift` 4×8-12 RPE 9 90s; `Leg_Extensions` 4×8-12 RPE 9 60s; `Seated_Leg_Curl` 4×8-12 RPE 9 60s; `Seated_Calf_Raise` 4×8-12 RPE 9 60s |

Progressão embutida: "Nos dias de potência, suba 2,5 kg ao fechar o topo da
faixa (3-5 ou 6-10); nos dias de hipertrofia, suba só quando fechar 8-12 em
todas as séries."

## Regras e casos de borda

- `programCatalog()` devolve exatamente 4 itens na ordem `ppl`, `531`, `gzclp`,
  `phul`, com ids únicos e `daysPerWeek == days.size` (PPL = 6; os outros = 4).
- `buildProgram` é determinístico: com os mesmos argumentos devolve sempre o
  mesmo plano; a única fonte de tempo é o parâmetro `nextReviewAt`, calculado
  pelo chamador (`AppViewModel.nextReviewAt`).
- `dayOrder` vai de `0` a `days.size - 1` sem buracos; a ordem dos exercícios é
  a da tabela e vira `exerciseOrder` no `saveGeneratedPlan`.
- Todo `exerciseId` existe no asset `exercise_swap_catalog.json` (versão 1) e
  `exerciseName`/`primaryMuscles`/`equipment` batem com o catálogo.
- `targetReps` é sempre `"n"` ou `"a-b"` (regex do `ProgressionEngine`), nunca
  `"AMRAP"` nem vazio — assim a sugestão da H3-5 funciona desde a 1ª sessão.
- Limites: `sets` entre 1 e 10; `restSeconds` entre 30 e 300; `targetRpe` é
  `null` ou está em `1.0..10.0`.
- Nenhum exercício repetido dentro do mesmo dia (repetir entre dias é válido).
- Aplicar um programa é `main`: substitui o `main` anterior e mantém os `extra`
  ativos (H3-15); com um `main` já ativo, a UI pede confirmação dizendo qual
  plano será substituído.
- Cancelar a confirmação não cria nem altera plano algum.
- Aplicar não chama o backend, não cria `PendingGeneration`, não mexe em
  `lastFallbackReason` e funciona em modo avião.
- Aplicar não apaga sessões do histórico: o plano antigo é desativado, não
  excluído; sessões não têm FK para plano.
- O plano salvo tem `source = "local"`, `generatedByAi = false` e
  `fallbackReason = null` (a Home não mostra selo de fallback).
- `reviewAfterWeeks = 4` e `nextReviewAt` = agora + 4 semanas (mesmo cálculo de
  `AppViewModel.nextReviewAt`).
- `buildProgram("id-inexistente")` devolve `null`; o ViewModel mostra erro e não
  grava nada.
- Aplicar o mesmo programa duas vezes é permitido (regera o plano) e não
  duplica dias dentro do plano novo.

## Critérios de aceite

1. **Dado** o app sem nenhum plano e em modo avião, **quando** o usuário abre
   "Gerar", toca em "Usar este programa" no PPL e confirma, **então** um plano
   PPL de 6 dias é salvo localmente, selecionado na Home e executável.
2. **Dado** um `main` ativo "Meu treino" e um `extra` ativo, **quando** o
   usuário aplica o 5/3/1 e confirma, **então** o main anterior é desativado, o
   5/3/1 vira o main selecionado, o extra continua ativo e o histórico permanece.
3. **Dado** o catálogo, **quando** `buildProgram("gzclp", ...)` é chamado duas
   vezes com os mesmos argumentos, **então** as duas saídas são idênticas.
4. **Dado** os 4 templates, **quando** o teste varre todos os `exerciseId`,
   **então** todos existem no asset e nome/músculos/equipamento batem.
5. **Dado** o diálogo de confirmação aberto, **quando** o usuário cancela,
   **então** nenhum plano é criado ou alterado.
6. **Dado** `buildProgram("nao-existe", ...)`, **então** retorna `null` e a UI
   mostra erro sem salvar.
7. **Dado** um programa aplicado, **quando** o usuário registra duas sessões no
   topo da faixa, **então** o `ProgressionEngine` sugere progressão normalmente
   (as faixas dos templates são parseáveis).
8. **Dado** o app em português ou inglês, **quando** a seção é aberta, **então**
   todos os textos aparecem no idioma e `StringParityTest` fica verde.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/programs/ProgramLibraryTest.kt` | `catalogo_tem_quatro_programas_na_ordem`, `ids_unicos`, `build_programa_deterministico`, `dias_e_ordens_consistentes`, `faixas_de_reps_parseaveis_pelo_motor`, `limites_de_series_e_descanso`, `sem_exercicio_repetido_no_mesmo_dia`, `ppl_tem_seis_dias`, `demais_programas_tem_quatro_dias`, `id_desconhecido_retorna_null`, `plano_local_sem_ia_e_com_revisao_em_4_semanas` |
| `app/src/test/java/com/treinos/app/ui/programs/ProgramCatalogAssetTest.kt` | `todos_exercise_ids_existem_no_asset`, `nome_musculos_equipamento_batem_com_asset` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `aplicar_programa_salva_como_main_e_desativa_anterior`, `aplicar_programa_preserva_extras_e_historico` |
| `app/src/test/java/com/treinos/app/ui/screens/GenerateScreenTest.kt` | `secao_programas_mostra_quatro_cards`, `toque_em_usar_programa_dispara_callback`, `cancelar_confirmacao_nao_aplica` |
| `app/src/test/java/com/treinos/app/ui/StringParityTest.kt` | (existente; cobre as chaves novas automaticamente) |

## Verificação manual

1. Ativar modo avião e limpar os dados do app; abrir "Gerar treino" e conferir
   a seção "Programas prontos" com os 4 cards e a descrição de cada um.
2. Aplicar o PPL; conferir na Home o plano novo selecionado e os 6 dias; iniciar
   um dia e registrar uma série.
3. Com um main ativo, aplicar o 5/3/1: conferir o diálogo de confirmação, o
   main antigo desativado, um extra ativo preservado e o histórico intacto.
4. Aplicar o GZCLP e o PHUL e conferir 4 dias em cada.
5. Exportar backup, importar e conferir que o programa aplicado volta.
6. Caminho de erro: cancelar a confirmação → nada muda; girar a tela com a
   confirmação aberta → estado preservado.
7. Trocar o idioma para English e conferir a seção; voltar para Português.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Conteúdo prescritivo**: os programas são referências conhecidas, com faixas
  conservadoras e RPE alvo; o usuário pode trocar exercícios (H3-41) e o app não
  altera nada sozinho. Aprovar o conteúdo com revisão humana antes de lançar.
- **Divergência com o catálogo**: um id removido do asset quebraria o template;
  `ProgramCatalogAssetTest` falha no gate antes do release.
- **Duplicação com o `build_local_plan` do backend**: são caminhos
  independentes; o app não chama o backend e o backend não conhece os templates.
- **Substituição do `main`**: confirmação explícita, extras e histórico
  preservados. Rollback = reverter o commit; nenhum dado é migrado e o plano
  aplicado continua sendo um plano normal editável/excluível.
- **Sem dependência nova**: nenhum artefato é adicionado.
