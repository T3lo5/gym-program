# H4-11 — Notas por exercício durante o treino

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-11` |
| MoSCoW | `C` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |

## Objetivo

Registrar observações do treino em andamento — dor, ajuste de execução, como a
série se sentiu — sem sair da tela de treino, tanto para a sessão inteira
quanto para cada exercício.

## Fora de escopo

- Migration ou coluna nova no Room: usa os campos `notes` que já existem em
  `WorkoutSessionEntity` e `WorkoutPlanExerciseEntity`.
- Editar notas de sessões já concluídas pelo Histórico (o Histórico continua
  somente leitura para notas).
- Notas em corrida e mobilidade (`RunWorkoutScreen`/`MobilityScreen`): só
  musculação.
- Nota por série (`set_logs` não tem `notes`).
- Notas no relógio (Wear): o `WearSessionMapper` não transporta notas.
- Rich text, anexos, foto ou áudio.
- Mostrar a nota do exercício (que pertence ao plano) no Histórico: o Histórico
  mostra a nota da sessão; a nota do exercício aparece na tela de treino e nas
  próximas sessões do mesmo dia.
- Editar `WorkoutDayEntity.notes` (nota do dia) por esta tela.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/NoteValidation.kt` | **Novo.** Lógica pura: `MAX_WORKOUT_NOTE_CHARS = 2000`, `normalizeNote(text): String` (trim) e `noteLengthError(text): Boolean` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `updateSessionNotes(sessionId: Long, notes: String)` (grava `WorkoutSessionEntity.notes` via `dao.updateSession`) e `updatePlanExerciseNotes(dayId: Long, exerciseOrder: Int, notes: String)` (acha o `WorkoutPlanExerciseEntity` do dia por `exerciseOrder` e grava via `dao.updatePlanExercise`) — ambos `withContext(Dispatchers.IO)` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | Reusa `getExercisesForDay`, `updatePlanExercise` e `updateSession`; nenhum método novo é necessário |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `updateSessionNotes(notes: String)` (usa `_activeSessionId`/`inProgressSession`) e `updateExerciseNotes(sessionExercise: SessionExerciseEntity, notes: String)` (usa `session.dayId` + `exercise.exerciseOrder`); erros caem em `uiErrorMessage` |
| `app/src/main/java/com/treinos/app/ui/components/NoteEditDialog.kt` | **Novo.** `NoteEditDialog(key, title, initialNote, onDismiss, onConfirm)` com `TextField` multilinha, contador de caracteres e ações Cancelar/Salvar/Limpar |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Card de resumo ganha "Nota do treino" (texto ou "Adicionar nota"); `ExerciseCard` ganha "Nota do exercício" (mostra a nota atual e abre o diálogo); novos parâmetros `onEditSessionNote: (String) -> Unit` e `onEditExerciseNote: (SessionExerciseEntity, String) -> Unit` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Liga os dois callbacks a `viewModel::updateSessionNotes` e `viewModel::updateExerciseNotes` |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | `SessionCard` mostra `session.session.notes` quando não vazio (hoje só o `ActivityCard` de registro avulso mostra) |
| `app/src/main/res/values/strings.xml` | Strings das notas (`workout_note_*`, `history_note_*`) |
| `app/src/main/res/values-en/strings.xml` | Mesmas chaves (paridade cobrada por `StringParityTest`) |
| `app/src/test/java/com/treinos/app/ui/NoteValidationTest.kt` | **Novo.** Limite, trim e texto vazio |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | Persistência das duas notas |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | Abrir/salvar/cancelar o diálogo de nota |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` | Sessão com e sem nota |

Não edite `shared/` nem `com.treinos.shared/` (legado). Sem dependência nova,
sem migration e sem mudança de backend.

## Modelo de dados / contrato

Sem schema novo. Campos reutilizados:

```text
workout_sessions.notes        TEXT NOT NULL DEFAULT ''   // nota da sessão (WorkoutSessionEntity.notes)
workout_plan_exercises.notes  TEXT NOT NULL DEFAULT ''   // nota do exercício no plano (WorkoutPlanExerciseEntity.notes)
```

Contrato do repositório (o `notes` chega já normalizado pelo chamador):

```kotlin
suspend fun updateSessionNotes(sessionId: Long, notes: String)

/**
 * Casa o exercício do plano pelo `exerciseOrder` dentro do `dayId` — o
 * `exerciseOrder` da sessão é copiado do plano no startSession e não muda na
 * troca de escopo THIS_SESSION (H3-41). Não achou o dia/exercício ⇒ erro e
 * nada é gravado.
 */
suspend fun updatePlanExerciseNotes(dayId: Long, exerciseOrder: Int, notes: String)
```

Normalização pura (`NoteValidation.kt`):

```kotlin
const val MAX_WORKOUT_NOTE_CHARS = 2000

fun normalizeNote(text: String): String = text.trim()

fun noteLengthError(text: String): Boolean = text.length > MAX_WORKOUT_NOTE_CHARS
```

Backup e sync não mudam: `BackupMapper` e `WorkoutRepository.toSyncDto` já
levam `notes` de sessão e de exercício do plano; `SyncPlanExerciseDto` e
`SyncSessionDto` já têm o campo.

## Regras e casos de borda

- Nota da sessão: gravada em `workout_sessions.notes`; sobrevive a rotação e à
  morte do processo (é Room, não estado de tela).
- Nota do exercício: gravada em `workout_plan_exercises.notes` do dia atual,
  casada por `exerciseOrder`; por ser do plano, aparece de novo nas próximas
  sessões do mesmo dia.
- O texto é normalizado com `trim` antes de gravar; string vazia limpa a nota.
- Limite de 2000 caracteres (mesmo teto de `ActivityLog.MAX_NOTES_CHARS`);
  acima disso o botão Salvar fica desabilitado e o contador mostra o excesso —
  nada é gravado.
- Cancelar o diálogo não grava; fechar por fora (back/tap fora) equivale a
  cancelar.
- Salvar a mesma nota duas vezes é idempotente.
- Editar nota não altera séries, progresso, `completedSets` nem o estado do
  relógio (`syncWearState` não é necessário: o Wear não mostra notas).
- Exercício trocado só nesta sessão (H3-41 `THIS_SESSION`): a nota vai para o
  slot do plano com o mesmo `exerciseOrder` (é o exercício que vale nas próximas
  sessões); a UI rotula "Nota do exercício no plano".
- Sem sessão ativa (`_activeSessionId` nulo e sem `in_progress`), o ViewModel
  emite mensagem de erro e não grava.
- Dia/exercício não encontrado no plano (ex.: plano `extra` excluído durante a
  sessão) ⇒ erro claro e nada é gravado.
- A nota da sessão aparece no Histórico assim que a sessão entra na lista,
  inclusive antes de finalizar (status `in_progress`).
- Sessões antigas sem nota continuam sem a linha (nada de espaço vazio).
- Rotação com o diálogo aberto: o alvo é guardado por id em `rememberSaveable`
  (padrão H3-31) e o rascunho do texto usa a `key` do diálogo, sem herdar de
  outro exercício.

## Critérios de aceite

1. **Dado** um treino de musculação em andamento, **quando** o usuário abre
   "Nota do treino", digita "ombro esquerdo incomodou" e salva, **então** a nota
   persiste no Room e aparece no card da sessão no Histórico.
2. **Dado** um exercício do dia, **quando** o usuário salva "puxar a barra até o
   peito", **então** a nota aparece sob o exercício na tela de treino e numa
   sessão futura do mesmo dia.
3. **Dado** o diálogo de nota aberto, **quando** o usuário cancela, **então**
   nada é gravado.
4. **Dado** um texto com 2001 caracteres, **então** o botão Salvar fica
   desabilitado com aviso e nada é gravado.
5. **Dado** um texto só com espaços, **quando** salva, **então** a nota fica
   vazia (limpa).
6. **Dado** que o processo morre logo após salvar, **quando** o app reabre,
   **então** as duas notas continuam.
7. **Dado** um exercício trocado só nesta sessão (H3-41), **quando** o usuário
   salva a nota, **então** ela é gravada no slot do plano de mesmo
   `exerciseOrder` e o histórico não muda.
8. **Dado** um backup exportado com notas de sessão, **quando** importado,
   **então** as notas voltam (mapper já existente).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/NoteValidationTest.kt` | `trim_antes_de_gravar`, `texto_so_espacos_vira_vazio`, `limite_de_2000_aceito`, `acima_de_2000_bloqueia` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `update_session_notes_persiste_e_faz_trim`, `update_session_notes_vazia_limpa`, `update_plan_exercise_notes_casa_por_ordem`, `update_plan_exercise_notes_sem_dia_falha_sem_gravar`, `update_plan_exercise_notes_apos_troca_this_session_usa_ordem` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | `nota_da_sessao_abre_e_salva`, `nota_do_exercicio_abre_e_salva`, `cancelar_nao_grava`, `contador_bloqueia_acima_do_limite`, `nota_do_plano_aparece_sob_o_exercicio` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` | `sessao_com_nota_mostra_a_nota`, `sessao_sem_nota_nao_mostra` |
| `app/src/test/java/com/treinos/app/ui/StringParityTest.kt` | (existente; cobre as chaves novas automaticamente) |

## Verificação manual

1. Iniciar um treino de musculação; abrir "Nota do treino", salvar um texto e
   conferir que ele volta ao reabrir o diálogo.
2. Salvar uma nota em um exercício; conferir o texto sob o exercício e que ele
   aparece de novo ao iniciar outra sessão do mesmo dia.
3. Finalizar o treino e conferir a nota da sessão no Histórico.
4. Cancelar um diálogo e conferir que a nota anterior não mudou.
5. Colar um texto longo (> 2000) e conferir o bloqueio e o contador.
6. Girar a tela com o diálogo aberto e conferir que o alvo e o rascunho
   sobrevivem.
7. Caminho de erro: excluir o plano `extra` em outra tela e tentar salvar a nota
   do exercício da sessão → mensagem de erro e nada é gravado.
8. Trocar o idioma para English e conferir os rótulos.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Semântica da nota do exercício**: ela pertence ao plano e reaparece em
  sessões futuras; quem quer algo só daquela sessão usa a nota da sessão. O
  rótulo "Nota do exercício no plano" deixa isso explícito.
- **Troca de exercício THIS_SESSION**: a nota continua no slot do plano
  (`exerciseOrder`), não no substituto; documentado e coberto por teste.
- **Limite de 2000**: o Room guarda TEXT sem limite, mas o teto evita colar
  blocos gigantes e mantém o backup enxuto.
- **Rollback**: reverter o commit remove a UI e os métodos; as notas já
  gravadas continuam nos campos existentes e não quebram backup/sync. Nenhum
  dado do usuário é apagado.
- **Sem dependência nova**: nenhum artefato é adicionado.
