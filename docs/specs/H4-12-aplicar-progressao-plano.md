# H4-12 — Aplicar sugestão de progressão ao plano (opt-in)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-12` |
| MoSCoW | `C` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |

## Objetivo

Transformar a sugestão do `ProgressionEngine` em alvo do plano com um toque,
para o usuário parar de redigitar a carga a cada série — sem que o app altere o
plano sozinho.

## Fora de escopo

- Aplicar sem toque, em lote para todos os exercícios ou automaticamente ao
  abrir a tela.
- Mudar séries ou descanso do plano: só a **carga-alvo** e a **faixa de reps**.
- Tela de edição de plano fora do treino (não existe).
- Cálculo de 1RM/percentual (H4-1/H4-5): a sugestão continua vindo do histórico
  real da H3-5.
- Aplicar no relógio: o Wear continua recebendo só a sessão em execução.
- Desfazer com um toque (undo): reaplicar depois de o histórico mudar é o
  caminho de correção; o alvo é editável pela próxima aplicação.
- Mudar as regras do `ProgressionEngine` ou o texto das sugestões.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `WorkoutPlanExerciseEntity` ganha `val targetWeightKg: Double? = null` (coluna aditiva `targetWeightKg REAL`) |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `MIGRATION_9_10` (`ALTER TABLE workout_plan_exercises ADD COLUMN targetWeightKg REAL`); `version = 10`; registrar em `addMigrations(...)`. A fila do Horizon 4 consome 6→7 (H4-4), 7→8 (H4-7) e 8→9 (H4-8) antes; o número segue o `version` real no momento da implementação |
| `app/schemas/com.treinos.app.data.local.AppDatabase/<versão>.json` | schema exportado da versão nova (fonte única versionada) |
| `app/src/main/java/com/treinos/app/ui/progression/ProgressionApplication.kt` | **Novo.** Puro: `applyProgressionToPlanExercise(exercise, suggestion): WorkoutPlanExerciseEntity?` e `applyRepsSuggestion(currentTargetReps, suggestedReps): String` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `applyProgressionToPlanExercise(dayId, exerciseOrder, exerciseId, suggestion): ProgressionApplyResult`; `saveGeneratedPlan` mapeia `targetWeightKg`; `mapSnapshot` restaura; `toSyncDto` envia; `buildPlanDetails`/`SyncPlanExerciseDto` com o campo |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `WorkoutExerciseDto`, `SyncPlanExerciseDto` e `SyncDownloadPlanExerciseDto` ganham `targetWeightKg: Double? = null` (aditivo) |
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | `BackupPlanExercise` ganha `targetWeightKg: Double? = null` (aditivo, **sem bump** de `schemaVersion`, precedente H3-7/H3-45) |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | `toDto`/`toEntity` mapeiam `targetWeightKg` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `applyProgression(exercise: SessionExerciseWithSets)` (1 toque), `applicableProgressions: StateFlow<Set<Long>>` (ids de exercício da sessão com sugestão e par de plano válido) e `planTargetWeights: StateFlow<Map<Long, Double>>` (id do exercício da sessão → carga-alvo) |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Linha de sugestão no `ExerciseCard` com o botão "Aplicar ao plano"; `SetEditDialog` pré-preenche com a carga-alvo do plano antes da sugestão; novos parâmetros `applicableProgressions`, `planTargetWeights` e `onApplyProgression` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Passa os três estados/callback novos para `WorkoutScreen` |
| `back/app/database.py` | Coluna aditiva `target_weight REAL` em `synced_plan_exercises` (CREATE TABLE + `_ensure_columns` + INSERT/SELECT) |
| `back/app/main.py` | DTOs de sync com `targetWeightKg` opcional nos dois sentidos |
| `app/src/main/res/values/strings.xml` | Strings do botão, do rótulo de alvo e do snackbar |
| `app/src/main/res/values-en/strings.xml` | Mesmas chaves (paridade cobrada por `StringParityTest`) |
| `app/src/test/java/com/treinos/app/ui/progression/ProgressionApplicationTest.kt` | **Novo.** Regras puras de aplicação |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Migration aditiva (próximo par livre) preservando linhas |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | Aplicar grava o alvo; exercício trocado não casa |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | Botão, toque único e pré-preenchimento |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` / `BackupMapperTest.kt` | Round-trip e ausência de `targetWeightKg` |
| `back/tests/test_sync_contract.py` | Round-trip de `targetWeightKg` no snapshot |

Não edite `shared/` nem `com.treinos.shared/` (legado). Sem dependência nova.

## Modelo de dados / contrato

Room na versão 10 (cadeia da fila: 6→7 H4-4, 7→8 H4-7, 8→9 H4-8, 9→10 esta),
aditiva (sem destructive fallback):

```sql
ALTER TABLE workout_plan_exercises ADD COLUMN targetWeightKg REAL;
-- NULL = sem alvo definido (comportamento atual)
```

```kotlin
// Entities.kt
data class WorkoutPlanExerciseEntity(
    // ...campos atuais...
    val targetReps: String = "8-12",
    val targetRpe: Double? = null,
    val targetWeightKg: Double? = null,   // NOVO: carga-alvo aplicada pelo usuário
    // ...
)

// ProgressionApplication.kt — puro, sem Android/Room
fun applyProgressionToPlanExercise(
    exercise: WorkoutPlanExerciseEntity,
    suggestion: ProgressionSuggestion,
): WorkoutPlanExerciseEntity?   // null quando não há o que aplicar

fun applyRepsSuggestion(currentTargetReps: String, suggestedReps: Int): String

// WorkoutRepository.kt
sealed interface ProgressionApplyResult {
    data class Applied(val targetWeightKg: Double?, val targetReps: String) : ProgressionApplyResult
    data object NothingToApply : ProgressionApplyResult   // action == NONE ou sem valor
    data object ExerciseNotInPlan : ProgressionApplyResult // dia/ordem/exercício não casam
}

suspend fun applyProgressionToPlanExercise(
    dayId: Long,
    exerciseOrder: Int,
    exerciseId: String,
    suggestion: ProgressionSuggestion,
): ProgressionApplyResult
```

Backup JSON (schemaVersion continua 3) e sync app↔backend, aditivos nos dois
sentidos:

```text
BackupPlanExercise.targetWeightKg: Double?   // ausente em arquivos antigos → null
SyncPlanExerciseDto.targetWeightKg: Double?  // default null
SyncDownloadPlanExerciseDto.targetWeightKg: Double?
WorkoutExerciseDto.targetWeightKg: Double?   // plano vindo da geração/revisão
```

Regras da função pura:

- `suggestion.action == NONE` → `null` (nada a aplicar).
- `suggestedWeightKg != null` → `exercise.copy(targetWeightKg = suggestedWeightKg)`
  (a faixa de reps não muda).
- senão, `suggestedReps != null` → `exercise.copy(targetReps =
  applyRepsSuggestion(exercise.targetReps, suggestedReps))` (a carga-alvo não
  muda).
- ambos nulos → `null`.
- `applyRepsSuggestion`:
  - faixa `"a-b"` → desloca os dois extremos por `delta = suggestedReps - b`:
    `a' = max(1, a + delta)`, `b' = max(a', b + delta)`, devolve `"a'-b'"`;
  - alvo único `"n"` → devolve `suggestedReps.toString()`;
  - texto que não casa (`"AMRAP"`, vazio) → devolve o valor atual sem mudar.

## Regras e casos de borda

- O botão "Aplicar ao plano" só aparece quando: existe sugestão com
  `action != NONE` **e** o exercício da sessão casa com um exercício do plano do
  dia pelo mesmo `exerciseOrder` **e** mesmo `exerciseId`.
- Um toque aplica e grava; nenhum caminho aplica sem o toque. Abrir a tela, ver
  a sugestão ou registrar série não altera o plano.
- Aplicar é idempotente: tocar de novo com a mesma sugestão grava o mesmo valor.
- Exercício trocado só nesta sessão (H3-41 `THIS_SESSION`): o `exerciseId` da
  sessão difere do plano no mesmo `exerciseOrder` → `ExerciseNotInPlan`, nada é
  gravado e o botão não aparece.
- Plano `extra` excluído durante a sessão: o dia some → `ExerciseNotInPlan`,
  mensagem de erro e nada gravado.
- `suggestedWeightKg` fora de `2.5..500.0` não acontece (o motor já limita); se
  acontecer, o valor é gravado como veio (sem clamp duplicado).
- O `SetEditDialog` pré-preenche a carga nesta ordem: última série registrada →
  `targetWeightKg` do plano → sugestão. O valor digitado pelo usuário sempre
  prevalece.
- Aplicar não altera `sets`, `restSeconds`, `targetRpe`, séries já registradas
  nem o histórico; não dispara `syncWearState`.
- Restore/sync antigos sem o campo → `targetWeightKg = null` (comportamento
  atual). Backup novo em app antigo é ignorado por `ignoreUnknownKeys`.
- Migration aditiva (hoje 6→7; na fila do Horizon 4 vira 9→10): todas as linhas
  sobrevivem com `targetWeightKg = null`; o número da migration acompanha o
  `version` real do `AppDatabase.kt` (H4-4/H4-7/H4-8 podem ter consumido 6→7,
  7→8 e 8→9 antes).
- Aplicar não mexe em `nextReviewAt` nem no ciclo do plano.
- Snackbar de sucesso cita o exercício e o alvo (`"Alvo do plano: 42,5 kg em
  Supino"`); falha usa `uiErrorMessage`.

## Critérios de aceite

1. **Dado** um exercício com sugestão `INCREASE` de 42,5 kg e nenhuma série
   registrada, **quando** o usuário toca "Aplicar ao plano", **então**
   `targetWeightKg = 42.5` é gravado e o diálogo da próxima série já abre com
   42,5 kg.
2. **Dado** um exercício de peso corporal com sugestão `+1 rep` em alvo "8-12",
   **quando** aplica, **então** `targetReps` vira "9-13" e nenhum peso é gravado.
3. **Dado** o botão visível, **quando** o usuário apenas registra séries sem
   tocar nele, **então** o plano não muda.
4. **Dado** um exercício trocado só nesta sessão, **então** o botão não aparece
   e um toque forçado devolve `ExerciseNotInPlan` sem gravar.
5. **Dado** um banco Room v6 com planos, **quando** o app atualiza para v7,
   **então** as linhas sobrevivem e `targetWeightKg` é `null`.
6. **Dado** um backup exportado com `targetWeightKg = 45.0`, **quando**
   importado, **então** o valor volta; backup antigo sem o campo importa com
   `null`.
7. **Dado** um snapshot de sync com `targetWeightKg`, **quando** baixado e
   restaurado, **então** o valor volta; snapshot antigo vira `null`.
8. **Dado** sugestão `NONE`, **então** não há botão nem gravação.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/progression/ProgressionApplicationTest.kt` | `none_nao_aplica`, `aplica_carga_sugerida_sem_mexer_em_reps`, `aplica_reps_em_faixa_deslocando_os_extremos`, `aplica_reps_em_alvo_unico`, `faixa_desconhecida_nao_muda_reps`, `decrease_aplica_peso_menor`, `ambos_nulos_nao_aplica` |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_aditiva_preserva_linhas_e_target_weight_nulo` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `aplicar_progressao_grava_target_weight_por_ordem`, `aplicar_progressao_nao_casa_exercicio_trocado`, `aplicar_progressao_sem_sugestao_nao_grava`, `aplicar_progressao_sem_dia_falha_sem_gravar` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | `botao_aplicar_aparece_com_sugestao_aplicavel`, `sem_sugestao_nao_mostra_botao`, `exercicio_trocado_nao_mostra_botao`, `toque_dispara_callback_uma_vez`, `dialogo_prefere_target_do_plano_sobre_a_sugestao` |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | `target_weight_round_trip`, `target_weight_ausente_vira_null` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | `target_weight_mapeado_dto_entity` |
| `back/tests/test_sync_contract.py` | `test_target_weight_round_trip`, `test_snapshot_sem_target_weight_vira_null` |

## Verificação manual

1. Registrar duas sessões de um exercício no topo da faixa com RPE confortável
   para gerar a sugestão de aumento.
2. Abrir o próximo treino, conferir a linha "Sugestão: …" e tocar em "Aplicar ao
   plano"; conferir o snackbar e que o diálogo da próxima série abre com a carga
   aplicada.
3. Registrar uma série sem tocar no botão e conferir que o plano não mudou.
4. Exercício de peso corporal: aplicar `+1 rep` e conferir a faixa deslocada
   ("8-12" → "9-13").
5. Trocar um exercício só nesta sessão e conferir que o botão não aparece.
6. Exportar backup, importar e conferir o alvo preservado; sincronizar com o
   backend e restaurar, conferindo o mesmo.
7. Atualizar o app com um banco v6 e conferir que os planos abrem normalmente.
8. Caminho de erro: excluir o plano `extra` em outra tela e tentar aplicar →
   mensagem de erro e nada gravado.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Migration aditiva**: com `NULL`, sem destruição; `MigrationTest` cobre o par
  no gate. O número segue a fila canônica (H4-4 6→7, H4-7 7→8, H4-8 8→9, esta
  9→10) e acompanha o `version` real do `AppDatabase.kt` no merge. Rollback =
  reverter o commit; o banco novo não abre em app antigo — downgrade exige
  restore de backup (mesma situação das migrações anteriores).
- **Alvo desatualizado**: o usuário aplica uma sugestão e o histórico muda
  depois; o alvo é uma escolha explícita e o diálogo continua pré-preenchendo a
  última série primeiro, então o alvo não sobrescreve o que o usuário acabou de
  registrar.
- **Escopo maior que o estimado**: a mudança atravessa Room, backup, sync e
  backend; se o tempo apertar, o corte seguro é lançar sem o sync do campo
  (mantendo backup + Room), registrando o débito — nunca perder a migration.
- **Consumidores de `WorkoutPlanExerciseEntity`**: o campo novo é opcional e
  aditivo; `saveGeneratedPlan`, `mapSnapshot`, `BackupMapper` e os DTOs de sync
  precisam ser atualizados no mesmo commit para o valor não sumir no
  backup/restore. Grep por `targetRpe` antes de concluir para achar todos os
  pontos que mapeiam o exercício do plano.
- **Sem dependência nova**: nenhum artefato é adicionado.
