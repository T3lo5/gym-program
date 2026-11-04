# H1-2 — Execução por séries (não circuito)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-2` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-04` |
| Implementação | `concluída` |
| Origem | retroativa — documenta comportamento já presente no código |
| Depende de | nada |

## Objetivo

Executar o treino exercício por exercício, registrando cada série com reps,
carga e RPE, com descanso entre séries e retomada segura se o app for fechado no
meio.

## Fora de escopo

- Circuito/estação com troca automática entre exercícios.
- Cronômetro de isometria ou cardio (duração/distância).
- Sugestão automática de carga (H3-5).
- Edição retroativa de séries já gravadas.
- Wear OS (H2-2, já coberto à parte).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `WorkoutSessionEntity`, `SessionExerciseEntity`, `SetLogEntity` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `insertSession`, `insertSessionExercises`, `insertSetLog`, `incrementCompletedSets`, `maxSetNumber`, `setExerciseEndedAt` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `startSession`, `logSet`, `finishSession` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `startWorkout`, `logSet`, `finishWorkout`, `startRest`, `skipRest`, `currentExercise` |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Registro de série, descanso e navegação |
| `app/src/main/java/com/treinos/app/ui/ExerciseProgress.kt` | `calculateProgress`, `progressText`, `areAllSetsCompleted`, `nextIncompleteExercise` |
| `app/src/test/java/com/treinos/app/ui/ExerciseProgressTest.kt` | Testes da lógica pura |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

```kotlin
// Sessão
WorkoutSessionEntity(
    planId, dayId, planName, dayName,
    status = "in_progress" | "completed",
    startedAt, endedAt, totalDurationSeconds, notes,
)

// Exercício da sessão
SessionExerciseEntity(
    sessionId, exerciseId, exerciseName, exerciseOrder,
    plannedSets, completedSets, restSeconds, startedAt, endedAt,
)

// Série
SetLogEntity(
    exerciseLogId, setNumber, reps, weightKg, rpe,
    durationSeconds, distanceMeters,
    isCompleted, isWarmup, restTakenSeconds, completedAt,
)
```

Regras:

- `startSession(planId, dayId)` copia os exercícios do plano para
  `session_exercises`, com `plannedSets` e `restSeconds`.
- Cada `logSet` insere um `SetLogEntity`, incrementa `completedSets` e atualiza
  `endedAt` do exercício.
- O próximo `setNumber` é `MAX(setNumber) + 1`; séries fora de ordem não quebram.
- `calculateProgress` normaliza `plannedSets` para pelo menos 1 e limita
  `completedSets` ao planejado.
- `nextIncompleteExercise` retorna o primeiro exercício com progresso incompleto.
- `finishSession` grava `status = "completed"`, `endedAt` e
  `totalDurationSeconds`.
- Fechar o app no meio mantém a sessão `in_progress`; “Retomar treino” volta para
  ela.

## Regras e casos de borda

1. Plano sem exercícios → não inicia sessão; mensagem de erro.
2. Dia que não pertence ao plano ativo → não inicia.
3. `plannedSets = 0` → progresso trata como 1.
4. `completedSets` maior que o planejado → limitado ao planejado na exibição.
5. Série com reps/carga vazias → aceita como registro parcial; RPE opcional.
6. Descanso usa `restSeconds` do exercício; “Pular descanso” zera o timer.
7. Duas sessões em andamento ao mesmo tempo → o app usa a primeira encontrada;
   iniciar outra não é bloqueado hoje (limitação conhecida).
8. Finalizar sem registrar todas as séries → permitido; a sessão fica
   `completed` com o que foi registrado.
9. Sessão finalizada não volta para `in_progress`.
10. Histórico mostra séries por exercício e duração total.

## Critérios de aceite

1. **Dado** um dia com 3 exercícios de 3 séries, **quando** inicia, **então** a
   tela mostra o primeiro exercício e “1/3 séries”.
2. **Dado** que o usuário registra uma série, **então** o contador vai para
   “2/3”, a série é gravada e o descanso começa.
3. **Dado** que registra a última série de um exercício, **então** o app avança
   para o próximo exercício incompleto.
4. **Dado** que fecha o app no meio, **quando** reabre, **então** vê “Treino em
   andamento” e consegue retomar.
5. **Dado** que finaliza, **então** a sessão aparece no histórico com duração e
   séries.
6. **Dado** séries registradas fora de ordem, **então** o próximo número é o
   maior + 1.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/ExerciseProgressTest.kt` | `sem_series_o_proximo_numero_e_1`, `proximo_numero_e_o_maior_set_number_mais_um`, `series_registradas_fora_de_ordem_nao_quebram_o_proximo_numero`, `progresso_respeita_o_limite_de_series_planejadas`, `zero_series_planejadas_e_normalizado_para_pelo_menos_uma`, `proximo_exercicio_incompleto_ignora_os_concluidos`, `texto_de_progresso_usa_a_serie_atual_e_o_total_planejado` |
| `app/src/test/java/com/treinos/app/data/local/SessionDaoTest.kt` | `inicia_sessao_copia_exercicios`, `registra_serie_incrementa_contador`, `finaliza_sessao_grava_duracao` |

Os testes de `ExerciseProgressTest` já existem; os de `SessionDaoTest` ficam
para H3-1 (Room em JVM com Robolectric).

## Verificação manual

1. Gere um plano e inicie um dia.
2. Registre séries e confira o contador e o descanso.
3. Feche o app no meio e reabra; retome o treino.
4. Finalize e confira o histórico.
5. Registre uma série sem carga e outra sem RPE; confirme que são aceitas.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Duas sessões em andamento:** o modelo não impede; a UI escolhe a primeira.
  Mitigação futura: bloquear novo início enquanto houver `in_progress`.
- **Dados parciais:** finalizar sem completar é permitido; o histórico mostra o
  que foi feito, sem inventar séries.
- **Timer em background:** o descanso pode atrasar se o sistema matar o app; o
  valor é recalculado ao voltar.
- **Rollback:** a sessão e as séries já gravadas continuam válidas; remover a UI
  de execução não apaga o histórico.
