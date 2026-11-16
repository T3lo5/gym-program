# H4-6 — Supersets (bi/tri-set)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-6` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |

## Objetivo

Executar dois ou mais exercícios em sequência como um bloco (bi-set/tri-set),
com o app mostrando o agrupamento e encurtando o descanso entre eles — ativando
o campo `isSupersetWithPrevious` que hoje existe no plano e não tem tela.

## Fora de escopo

- Criar supersets na geração do plano (`back/app/plan_builder.py:1351` continua
  emitindo `isSupersetWithPrevious: false`); o agrupamento é definido pelo
  usuário na execução.
- Superset no relógio: o contrato `:protocol` e `WearSessionMapper` não mudam; o
  pulso continua vendo exercícios soltos.
- Alternância válida só para a sessão atual: sem coluna em
  `session_exercises`, a flag vive no plano e vale para este e os próximos
  treinos (ver Modelo de dados).
- Editor de plano dedicado (a Home continua só exibindo o plano).
- Editor de superset no Histórico de sessões concluídas.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/workoutexecution/SupersetPlan.kt` (novo) | `SUPERSET_TRANSITION_REST_SECONDS = 15`, `SupersetGroup(exerciseIds, startIndex)`, `supersetGroups(exercises)`, `restAfterSet(exercises, exerciseId, restSeconds)`, `canJoinSuperset(exercises, exerciseId)` — funções puras |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `@Query("UPDATE workout_plan_exercises SET isSupersetWithPrevious = :enabled WHERE id = :id") suspend fun updateSupersetFlag(id: Long, enabled: Boolean)` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `suspend fun setSupersetWithPrevious(planExerciseId: Long, enabled: Boolean)` chamando o DAO |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `activeSessionPlanExercises: StateFlow<List<WorkoutPlanExerciseEntity>>` derivado de `activePlans` + `inProgressSession` (plano/dia da sessão); `toggleSupersetWithPrevious(sessionExerciseId, enabled)`; `logSet` (:1325 e :1347) troca `startRest(exercise.restSeconds)` por `startRest(restAfterSet(plano, exerciseId, exercise.restSeconds))` |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | parâmetros `planExercises: List<WorkoutPlanExerciseEntity> = emptyList()` e `onToggleSuperset: (Long, Boolean) -> Unit = { _, _ -> }`; `ExerciseCard` (:878) ganha `supersetHeader: String?`, `supersetLabel: String?`, `canToggleSuperset: Boolean`, `isSuperset: Boolean` e o botão de alternância |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | coleta `activeSessionPlanExercises` e liga `onToggleSuperset` ao ViewModel (:347) |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | `workout_superset_header`, `workout_superset_label`, `workout_superset_toggle_on`, `workout_superset_toggle_off`, `workout_superset_toggle_helper` — `StringParityTest` exige os dois arquivos |
| `app/src/test/java/com/treinos/app/ui/workoutexecution/SupersetPlanTest.kt` (novo) | testes da lógica pura |
| `app/src/test/java/com/treinos/app/data/local/WorkoutDaoTest.kt` | flag persistida |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `setSupersetWithPrevious` persiste |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` | rótulos e botão |

## Modelo de dados / contrato

```text
Sem migration e sem mudança de contrato.

O campo já existe e já viaja em todos os caminhos:
  WorkoutPlanExerciseEntity.isSupersetWithPrevious (Entities.kt:81)
  BackupPlanExercise.isSupersetWithPrevious (BackupDtos.kt:81)
  WorkoutExerciseDto.isSupersetWithPrevious (BackendDtos.kt:88/:159/:283)
  synced_plan_exercises.is_superset_with_previous (back/app/database.py:92)

Agrupamento (derivado, não persistido):
  exercícios ordenados por exerciseOrder;
  um exercício com isSupersetWithPrevious = true entra no grupo do anterior;
  grupo válido tem >= 2 exercícios; o primeiro do dia nunca inicia grupo
  (flag no primeiro é ignorada).

Descanso entre séries (restAfterSet):
  membro que NÃO é o último do grupo:
    transition = if (restSeconds <= 0) 0 else min(15, restSeconds)
  último membro do grupo, exercício sem grupo ou fora do plano:
    restSeconds do exercício (comportamento atual)

Alternância:
  escreve direto no plano (WorkoutPlanExerciseEntity) — vale para a sessão
  atual e para os próximos treinos; backup e sync já carregam o campo.
```

## Regras e casos de borda

- Grupo de 2 (bi-set) e de 3+ (tri-set/giant set): o descanso de transição vale
  para todos os membros, menos o último.
- Primeiro exercício do dia com a flag ligada (plano editado à mão): tratado
  como sozinho, sem grupo e sem crash.
- Flag ligada num exercício cujo anterior não está no dia (plano inconsistente):
  mesmo tratamento — sozinho.
- Desligar um membro do meio divide o grupo: `A, B(off), C(on)` → `A` sozinho
  (descanso do plano) e grupo `[B, C]`, com `B` pagando a transição de 15 s
  como não-último membro — a mesma regra geral de sempre (Modelo de dados +
  regra de borda), sem exceção. (Correção de 2026-11-16: a nota original "o
  descanso de `B` volta ao normal" contrariava a regra geral e foi removida;
  o teste `desligar_do_meio_divide_grupo` agora fixa também o descanso de `B`.)
- `restSeconds <= 0` (exercício sem descanso): transição é 0, nunca 15 — o
  plano manda.
- Exercício não encontrado no plano (sessão antiga/plano trocado): usa
  `restSeconds`, sem crash.
- Plano excluído ou regenerado no meio da sessão: `activeSessionPlanExercises`
  esvazia; a UI perde os rótulos e o descanso volta ao normal (degradação
  aceita).
- Rótulos: grupos recebem letras (`A`, `B`, …) e membros, `A1`, `A2`, `A3`, na
  ordem de execução; a letra é derivada do índice do grupo, sem persistência.
- O cabeçalho do grupo é renderizado **dentro** do card do primeiro membro, não
  como item extra da `LazyColumn` — o índice de rolagem do H3-27
  (`scrollTargetIndex = nextIndex + 1`) continua válido.
- O botão de alternância não aparece no primeiro exercício do dia
  (`canJoinSuperset` falso) e mostra o estado atual nos demais.
- Alternar ligado num exercício já ligado é no-op; o callback só é disparado
  quando o estado muda.
- Wear: um `LOG_SET` vindo do relógio (`handleWearCommand`, :628) passa pelo
  mesmo `logSet` e usa a mesma regra de descanso; o pulso não mostra o
  agrupamento.

## Critérios de aceite

1. **Dado** um plano com A e B (`isSupersetWithPrevious = true` em B), **quando**
   o treino abre, **então** os dois aparecem como "Supersérie A", com rótulos
   "A1" e "A2".
2. **Dado** esse treino, **quando** o usuário loga uma série de A (não último do
   grupo), **então** o descanso inicia em 15 s; **quando** loga a última série
   de B, **então** o descanso é o `restSeconds` do plano.
3. **Dado** um exercício sem superset (não é o primeiro do dia), **quando** o
   usuário toca em "Supersérie com o anterior", **então** o plano é gravado com
   `isSupersetWithPrevious = true`, a UI agrupa e o próximo treino do dia já vem
   agrupado.
4. **Dado** um exercício agrupado, **quando** o usuário desliga, **então** ele
   sai do grupo, os rótulos somem e o descanso segue a regra geral: sozinho
   (ou último do grupo restante) usa o `restSeconds` do plano; não-último do
   grupo restante paga a transição de 15 s.
5. **Dado** o primeiro exercício do dia, **quando** o treino abre, **então** o
   botão de superset não aparece.
6. **Dado** um tri-set A, B, C, **quando** o usuário loga séries de A e de B,
   **então** ambos usam 15 s; a última série de C usa o descanso normal.
7. **Dado** um plano restaurado de backup/sync com `isSupersetWithPrevious`,
   **quando** o treino abre, **então** o agrupamento aparece (o campo já viaja
   no backup e no DTO).
8. **Dado** um plano com o primeiro exercício marcado, **quando** o treino
   abre, **então** ele é tratado como sozinho, sem crash.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/workoutexecution/SupersetPlanTest.kt` | `grupo_de_dois`, `tri_set_tem_tres_membros`, `primeiro_marcado_e_ignorado`, `sem_flags_nao_ha_grupo`, `transicao_entre_membros`, `ultimo_membro_usa_resto_normal`, `resto_zero_nao_vira_transicao`, `resto_menor_que_transicao_e_respeitado`, `exercicio_fora_do_plano_usa_resto_normal`, `desligar_do_meio_divide_grupo`, `can_join_falso_no_primeiro` |
| `app/src/test/java/com/treinos/app/data/local/WorkoutDaoTest.kt` | `update_superset_flag_persiste` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `set_superset_with_previous_persiste` |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` | `superset_mostra_cabecalho_e_rotulos`, `botao_superset_ausente_no_primeiro`, `toggle_superset_dispara_callback` |

## Verificação manual

1. Usar um plano com 2 exercícios no dia, iniciar o treino e tocar em
   "Supersérie com o anterior" no segundo: os dois passam a mostrar
   "Supersérie A" com A1/A2.
2. Logar uma série do A1: o descanso inicia em 15 s; logar a última série do
   A2: o descanso é o do plano.
3. Sair da tela e voltar: o agrupamento continua (persistido no plano).
4. Iniciar um treino novo do mesmo dia: já vem agrupado.
5. Desligar o superset: os rótulos somem e o descanso volta ao normal.
6. Caminho de erro: abrir um plano antigo com o primeiro exercício marcado →
   abre sem crash e sem grupo.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Sem migration: o campo já existe; o rollback é remover `SupersetPlan.kt`, o
  botão/parâmetros e o método do ViewModel. Flags ligadas no plano ficam como
  dado inerte (nenhum leitor as usa) — sem corrupção.
- A alternância grava no plano, não só na sessão; o rótulo/helper precisa dizer
  isso com clareza para não surpreender. Alternância só-da-sessão exigiria
  coluna em `session_exercises` (migration), fora do escopo desta spec.
- O descanso de transição é decisão de produto (15 s); o `min` com o
  `restSeconds` do plano evita alongar descansos curtos.
- O agrupamento deriva do plano vivo; substituir/regenerar o plano no meio da
  sessão muda os rótulos e o descanso. Degradação aceita e documentada.
- Backup e sync não mudam de contrato (o campo já viaja), então não há risco de
  perda de dado novo.
