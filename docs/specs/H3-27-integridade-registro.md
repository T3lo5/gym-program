# H3-27 — Integridade do registro: corrigir, confirmar e fechar sessões

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-27` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P0.1–0.3, §P1.6 e 1.8).

## Objetivo

O que está registrado reflete o que o usuário de fato fez: série pode ser
corrigida, valor inválido nunca é salvo em silêncio, finalizar/abandonar são
ações conscientes — e a sessão de mobilidade fecha de verdade em vez de criar
uma sessão fantasma eterna na Home.

## Contexto (estado atual verificado)

- `parseDecimal` transforma `"6.0.5"` em `null` e a série é salva **sem
  carga**, sem aviso (`WorkoutScreen.kt:486-487`); o submit valida só reps
  (`:403-410`); RPE aceita qualquer número (`:452-465`).
- Linhas de série não são clicáveis — sem editar/excluir
  (`WorkoutScreen.kt:329-371`; grep sem `updateSet`/`deleteSet` na UI).
- "Finalizar treino" encerra em 1 toque, sem confirmação nem desfazer
  (`WorkoutScreen.kt:98-105`, `AppViewModel.kt:851-864`).
- **Sessão fantasma:** `startWorkout` cria sessão `in_progress` e navega para
  mobilidade (`AppViewModel.kt:780`), mas `finishPlannedMobility(0,0,…)`
  (`:881-891`) chama `logPlannedMobility`, que **insere outra** sessão
  `completed` (`WorkoutRepository.kt:304-327`) — a `in_progress` nunca fecha;
  `TreinosApp.kt:268-273` não passa `planId`/`dayId`. Não existe UI para
  abandonar sessão (`deleteSession` só roda em `replaceAllData`,
  `WorkoutDao.kt:171-192`).
- `nextExercise()`/`isCurrentExerciseCompleted()` são código morto
  (`AppViewModel.kt:1036-1062`); o botão continua "Registrar série N+1" após
  cumprir o planejado.

## Fora de escopo

- Editar séries de sessões já concluídas (histórico).
- RPE automático ou sugestão de RPE; editar o plano durante a sessão.
- Migração de Room (as mudanças são aditivas em DAO/métodos).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | validação no diálogo; linhas clicáveis; confirmação de finalizar; botão "Exercício concluído — próximo" |
| `app/src/main/java/com/treinos/app/ui/SetValidation.kt` (novo) | regras puras de carga/reps/RPE |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `updateSet`/`deleteSet`/`abandonWorkout`; `finishPlannedMobility` fecha a sessão aberta; ligar `nextExercise` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `updateSetLog`, `deleteSetLog` (por id) |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `updateSet`/`deleteSet`/`deleteSession`; `finishPlannedMobilitySession(sessionId, routineId, elapsed)` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | passar `planId`/`dayId` da sessão ativa para `MobilityScreen` |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | ação "Abandonar" no InProgressCard (com confirmação) |

## Modelo de dados / contrato

Sem migration. DAO novos (aditivos):

```text
updateSetLog(log: WorkoutSetLogEntity)            -- por id, preserva sessionId
deleteSetLog(setId: Long)
finishPlannedMobilitySession(sessionId, routineId, elapsedSeconds)
  -- UPDATE da sessão in_progress: status=completed, endedAt, duration
```

## Regras e casos de borda

- Carga: campo não vazio deve parsear (vírgula ou ponto) e satisfazer
  `0 < peso ≤ 500`; inválido → erro no diálogo, **nada é salvo**.
- Reps: inteiro `1..100`. RPE: vazio = `null`; preenchido deve ser `1..10`
  (uma casa decimal permitida).
- Editar série: tocar na linha registrada abre o diálogo pré-preenchido;
  salvar = `updateSetLog`. Excluir série: gesto/ação com confirmação;
  o progresso do exercício é recalculado (derivado, sem agregado persistido).
- "Finalizar treino": diálogo "Finalizar treino? N de M séries registradas"
  com [Cancelar | Finalizar]; Cancelar não altera nada.
- "Abandonar treino": disponível no Workout (top bar) e no InProgressCard
  (overflow); confirmação → `deleteSession` + estado do Wear limpo
  (`syncWearState` após abandono).
- Mobilidade planejada: finalizar atualiza a sessão `in_progress` existente
  (nunca insere segunda sessão); o atalho "Mobilidade" sem plano continua
  inserindo sessão `completed` avulsa (comportamento atual).
- Botão do exercício vira "Exercício concluído — próximo" quando as séries
  planejadas estão cumpridas (`calculateProgress` já existe); tocar avança
  para o próximo exercício e rola a lista até ele; exercícios concluídos
  ficam visualmente atenuados.

## Critérios de aceite

1. **Dado** carga `"6.0.5"`, **quando** salva a série, **então** o diálogo
   mostra erro de carga e nada é gravado.
2. **Dado** RPE `12`, **quando** salva, **então** erro "RPE deve ser 1–10".
3. **Dado** série registrada 60 kg × 10, **quando** toca na linha e muda para
   62,5 kg, **então** o histórico e o progresso refletem 62,5 kg.
4. **Dado** treino com 12/16 séries, **quando** toca "Finalizar treino",
   **então** vê o resumo e pode cancelar sem efeito.
5. **Dado** treino em andamento, **quando** escolhe "Abandonar treino" e
   confirma, **então** a sessão some da Home e do histórico.
6. **Dado** dia de mobilidade do plano, **quando** inicia e finaliza a
   rotina, **então** existe **uma** sessão `completed` (a `in_progress` foi
   fechada) e o card "Treino em andamento" desaparece.
7. **Dado** exercício com todas as séries planejadas feitas, **quando** olha
   o botão, **então** ele diz "Exercício concluído — próximo" e avança ao
   tocar.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../SetValidationTest.kt` | `carga_invalida_rejeitada`, `carga_virgula_aceita`, `reps_fora_de_faixa`, `rpe_vazio_null`, `rpe_fora_de_faixa`, `peso_acima_de_500` |
| `app/src/test/java/.../WorkoutDaoTest.kt` | `update_set_log_preserva_sessao`, `delete_set_log` |
| `app/src/test/java/.../WorkoutRepositorySessionTest.kt` | `mobilidade_planejada_fecha_sessao_unica`, `abandonar_remove_sessao` |
| `app/src/test/java/.../WorkoutScreenTest.kt` (Robolectric) | `finalizar_exige_confirmacao`, `linha_de_serie_abre_edicao` |

## Verificação manual

1. Treino real: errar carga de propósito → erro no diálogo; corrigir uma
   série salva; excluir outra.
2. Iniciar mobilidade de um dia do plano → finalizar → conferir na Home que
   não há "Treino em andamento" eterno e no histórico que há 1 sessão.
3. Abandonar um treino pela Home → sessão some.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- `deleteSetLog`/`updateSetLog` tocam dados de produção: testes de DAO em
  Room in-memory antes de ligar a UI. A correção da mobilidade muda o número
  de sessões criadas — o teste `mobilidade_planejada_fecha_sessao_unica`
  travará regressão. Rollback: reverter DAO/repository é seguro (métodos
  novos não têm outros chamadores).
