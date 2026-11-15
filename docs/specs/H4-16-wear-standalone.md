# H4-16 — Modo standalone do relógio

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-16` |
| MoSCoW | `C` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |
| Depende de | H3-20 (fila real de comandos) e H4-15 (edição de carga/reps) |

## Objetivo

Treinar a sessão inteira só com o relógio — sem o celular por perto — e o que
foi registrado chega ao celular quando os dois se reconectarem, sem duplicar
série.

## Contexto (estado atual verificado)

- O relógio **não tem banco**: `WearStateStore` guarda o último estado e a fila
  em `SharedPreferences` (`wear/.../data/WearStateStore.kt` L11–50) e o módulo
  `:wear` não depende de Room (`wear/build.gradle.kts` L73–109).
- O celular publica **só o exercício atual** (`WearSessionMapper.toWearState`,
  L40–63) via `DataClient` (`WearSyncRepository.publish`, L31–43); o relógio
  nunca recebe a lista completa do treino.
- O relógio só envia comandos unitários (`WorkoutWearActivity.sendCommand`,
  L97–124) e a fila reenvia no `onPeerConnected`
  (`WearSyncService.resendPendingCommands`, L70–97).
- O celular deduplica por `commandId` (`WearSyncRepository.onCommandReceived`
  L45–50 + `CommandDedupe`), que é a base para um sync idempotente.
- O manifesto do `:wear` declara `com.google.android.wearable.standalone =
  false` (`wear/src/main/AndroidManifest.xml`): o app se declara dependente do
  celular.
- O `WearConnectionWatcher` já expõe `connected: StateFlow<Boolean>`
  (`wear/.../ui/WearConnection.kt` L22–23), que decide o modo da tela.
- A publicação do estado começa em `syncWearState()`/`publishWearState`
  (`AppViewModel.kt` L647–660), chamada por `startSession` (L1295) e pelo
  `combine(inProgressSession, _exerciseIndex)` (L597–607).

## Fora de escopo

- Baixar/gerenciar a biblioteca de planos no relógio: o snapshot é da **sessão
  do dia**, não do plano inteiro.
- Criar treino ou editar plano no relógio.
- Sincronizar histórico completo, backup, perfil ou mídia pelo relógio.
- Corrida/mobilidade standalone com métricas de sensor (H4-17).
- Foreground service de saúde: a sessão é reconstruída do Room ao reabrir a
  tela; **sem** `FOREGROUND_SERVICE_HEALTH`, `BODY_SENSORS` ou
  `ACTIVITY_RECOGNITION` (a H2-2 recusou sensores).
- Resolver conflito de séries editadas nos dois lados além do append
  idempotente.
- Migrar a fila da H3-20 de `SharedPreferences` para Room (fica como está).
- Notificações, tiles, complicações ou watchface.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `wear/build.gradle.kts` | Plugin `org.jetbrains.kotlin.kapt` `1.9.24` + `androidx.room:room-runtime:2.6.1`, `room-ktx:2.6.1` e `kapt("androidx.room:room-compiler:2.6.1")`; `kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }` |
| `wear/schemas/` (novo) | Schema exportado do Room, versionado (mesmo padrão de `app/schemas`) |
| `wear/src/main/java/com/treinos/wear/data/WearDatabase.kt` (novo) | `@Database(version = 1)` com `WearSessionEntity`, `WearSessionExerciseEntity`, `WearSetLogEntity` e `WearStandaloneDao` |
| `wear/src/main/java/com/treinos/wear/data/WearStandaloneStore.kt` (novo) | Salvar snapshot, ler a sessão ativa, gravar série local pendente, listar pendentes e marcar sincronizada |
| `wear/src/main/java/com/treinos/wear/ui/WearStandaloneSession.kt` (novo) | Reducer puro da sessão offline: avançar exercício/série, registrar série, calcular `restEndsAt`, pular descanso e finalizar |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Modo standalone quando `connected == false`: usa o snapshot do Room + reducer local em vez do `WearWorkoutState`; botão "Iniciar treino de hoje" quando houver snapshot |
| `wear/src/main/java/com/treinos/wear/data/WearSyncService.kt` | No `onPeerConnected`, além da fila da H3-20, envia o lote de séries pendentes em `PATH_SYNC_BATCH` |
| `wear/src/main/AndroidManifest.xml` | `com.google.android.wearable.standalone` passa a `true` |
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | Novos DTOs `WearSessionSnapshot`, `WearSessionExercise`, `WearSyncBatch`, `WearLoggedSet` e as constantes `PATH_SESSION_SNAPSHOT`/`PATH_SYNC_BATCH` |
| `app/src/main/java/com/treinos/app/data/wear/WearSessionMapper.kt` | `toSnapshot(session): WearSessionSnapshot` com a lista completa de exercícios |
| `app/src/main/java/com/treinos/app/data/wear/WearSyncRepository.kt` | `publishSnapshot(snapshot)`, `observeSyncBatches(): Flow<WearSyncBatch>` e dedupe de `clientSetId` |
| `app/src/main/java/com/treinos/app/data/wear/WearCommandListenerService.kt` | Receber `PATH_SYNC_BATCH` (path novo, sem tocar no `PATH_COMMAND` atual) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `applyWearSyncBatch(batch)`: aplica as séries via `repository.logSet` de forma idempotente e finaliza se `batch.finished`; publicar o snapshot em `syncWearState()` |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | Chave `wear_synced_set_ids` (dedupe do lote, mesmo padrão de `wear_recent_commands`) |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | Round-trip dos DTOs novos + payload antigo |
| `wear/src/test/java/com/treinos/wear/ui/WearStandaloneSessionTest.kt` (novo) | Reducer offline |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `toSnapshot` |
| `app/src/test/java/com/treinos/app/data/wear/WearSyncBatchTest.kt` (novo) | Dedupe e aplicação do lote |

## Modelo de dados / contrato

```text
:protocol — aditivo; DTOs novos com protocolVersion = 1 e paths novos:
  PATH_SESSION_SNAPSHOT = "/treinos/session-snapshot"  (celular → relógio, DataClient)
  PATH_SYNC_BATCH       = "/treinos/sync-batch"        (relógio → celular, MessageClient)

  @Serializable data class WearSessionSnapshot(
    protocolVersion: Int = 1, sessionId: Long, dayName: String,
    modality: String = "strength", startedAt: Long,
    exercises: List<WearSessionExercise> = emptyList())

  @Serializable data class WearSessionExercise(
    exerciseIndex: Int, sessionExerciseId: Long, exerciseName: String,
    plannedSets: Int, targetReps: String = "", restSeconds: Int = 90)

  @Serializable data class WearSyncBatch(
    protocolVersion: Int = 1, sessionId: Long, batchId: String,
    finished: Boolean = false, sets: List<WearLoggedSet> = emptyList())

  @Serializable data class WearLoggedSet(
    clientSetId: String, sessionExerciseId: Long, exerciseIndex: Int,
    setNumber: Int, reps: Int? = null, weightKg: Double? = null,
    rpe: Double? = null, loggedAt: Long)

Compatibilidade (paths novos, sem mexer no contrato da H2-2):
  celular antigo  → ignora o path novo: WearCommandListenerService só trata
                    "/treinos/command" (L13) e descarta o resto.
  relógio antigo  → não tem listener do snapshot; segue no modo conectado.
  protocolVersion != 1 no lote → o celular ignora e o relógio mantém pendente.
  Os dois APKs saem juntos no gate; a janela de mistura é só entre installs.

Room no :wear (banco novo, versão 1, schema em wear/schemas):
  wear_sessions(id PK, dayName, modality, startedAt, status, restEndsAt?)
  wear_session_exercises(id PK, sessionId FK→wear_sessions,
                         exerciseIndex, exerciseName, plannedSets,
                         targetReps, restSeconds)
  wear_set_logs(id PK autogen, clientSetId UNIQUE, sessionExerciseId,
                exerciseIndex, setNumber, reps?, weightKg?, rpe?,
                loggedAt, pending: Boolean)
  Sem migration (o banco nasce na v1); fallbackToDestructiveMigration NÃO é usado.

Sessão offline (reducer puro WearStandaloneSession):
  estado: sessionId, modality, exerciseIndex, setNumber, plannedSets,
          restEndsAt: Long?, status, pendingCount
  ações:  Start(snapshot), LogSet(reps, weightKg, now), SkipRest(now),
          Finish(now), Tick(now)
  restante do descanso = max(0, restEndsAt - now)
```

## Regras e casos de borda

- Snapshot da mesma `sessionId` substitui o anterior e **preserva** as séries
  locais ainda pendentes.
- Snapshot de sessão diferente com séries pendentes: as pendentes continuam
  guardadas por `sessionId` e são enviadas mesmo que a sessão ativa tenha
  mudado; o celular ignora lote de sessão diferente da ativa e reenvia o
  estado atual (mesma regra da H2-2).
- `clientSetId` é UUID gerado no relógio no momento do log; reenviar o mesmo
  lote não duplica (dedupe persistido no celular).
- Série logada offline → `pending = true`; depois que o celular confirma o
  lote (envio com ≥ 1 node OK), `pending = false`.
- Sem snapshot no Room e sem conexão → a tela mostra "Sem treino no relógio" e
  o botão "Atualizar" (não quebra).
- Relógio reiniciado no meio da sessão → exercício/série/`restEndsAt` vêm do
  Room; o descanso é recalculado de `restEndsAt - now`.
- `restEndsAt` no passado → descanso zerado.
- Finalizar offline → sessão `finished` no Room; o lote com `finished = true`
  é enviado ao reconectar e o celular aplica as séries e finaliza a sessão.
- Sessão já finalizada no celular quando o lote chega → o lote é rejeitado e o
  relógio limpa as pendentes daquela sessão (sem duplicar histórico).
- Snapshot com 0 exercícios (corrida/mobilidade) → a tela de corrida/mobilidade
  aparece e finaliza offline; sem séries.
- Room com versão inesperada/corrompido → **sem** `fallbackToDestructiveMigration`:
  a abertura falha e a tela mostra erro; nunca apaga dados em silêncio.
- Cap de segurança: acima de 200 séries pendentes, a mais antiga sai (a fila da
  H3-20 continua separada, com cap 20).

## Critérios de aceite

1. **Dado** o celular inicia um treino de força com o relógio pareado,
   **quando** o snapshot chega, **então** o relógio guarda **todos** os
   exercícios no Room.
2. **Dado** o relógio com o snapshot e o celular fora de alcance, **quando** o
   usuário abre o app no pulso, **então** consegue navegar exercícios,
   registrar séries e ver o descanso sem o celular.
3. **Dado** uma série registrada no pulso offline, **quando** o celular
   reconecta, **então** a série é gravada no Room do celular exatamente uma
   vez.
4. **Dado** o relógio reiniciado no meio de uma sessão offline, **quando** a
   tela reabre, **então** o exercício/série e o descanso restante são
   restaurados do Room.
5. **Dado** o usuário finaliza a sessão offline, **quando** reconecta,
   **então** o celular finaliza a sessão com todas as séries do lote.
6. **Dado** o celular registra séries em paralelo na sessão ativa, **quando**
   o lote do relógio chega, **então** as séries do relógio são acrescentadas
   sem duplicar as do celular.
7. **Dado** um lote reenviado duas vezes, **quando** o celular processa,
   **então** nenhuma série duplica.
8. **Dado** `./scripts/verify.sh`, **então** `:protocol`, `:app` e `:wear`
   compilam, os testes JVM dos três rodam e o gate termina com a linha de
   sucesso.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | `round_trip_do_snapshot`, `round_trip_do_lote`, `snapshot_sem_campo_novo_decodifica`, `lote_versao_incompativel_e_detectado` |
| `wear/src/test/java/com/treinos/wear/ui/WearStandaloneSessionTest.kt` | `inicia_no_primeiro_exercicio`, `registra_serie_e_avanca`, `ultima_serie_avanca_exercicio`, `finaliza_ultimo_exercicio`, `rest_ends_at_calculado`, `rest_no_passado_zera`, `sem_snapshot_nao_quebra`, `snapshot_novo_mantem_pendentes`, `corrida_sem_exercicios_finaliza` |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `snapshot_inclui_todos_os_exercicios`, `snapshot_de_corrida_sem_exercicios` |
| `app/src/test/java/com/treinos/app/data/wear/WearSyncBatchTest.kt` | `aplica_lote_uma_vez`, `ignora_client_set_id_repetido`, `ignora_sessao_diferente`, `ignora_versao_incompativel`, `lote_finished_finaliza_sessao` |

O Room no `:wear` é wrapper fino de persistência; a lógica de sessão está no
reducer puro `WearStandaloneSession`, testado em JVM. Testar o DAO exigiria
Robolectric no `:wear` — mesma decisão de escopo da H3-20 (sem Robolectric no
módulo do relógio); a persistência é coberta pela `Verificação manual`.

## Verificação manual

1. Instalar `dist/treinos.apk` no celular e `dist/wear.apk` no relógio; parear.
2. Iniciar um treino no celular e abrir o relógio; confirmar que o snapshot
   gravou (todos os exercícios aparecem no modo offline).
3. Ativar modo avião no relógio (ou afastar o celular); registrar 2–3 séries,
   usar o descanso e finalizar.
4. Desativar o modo avião; conferir no celular que as séries chegaram **uma
   única vez** e que a sessão foi finalizada.
5. Repetir o passo 4 (reenvio do lote) e confirmar que não há séries
   duplicadas.
6. Reiniciar o relógio no meio de uma sessão offline e confirmar que a tela
   volta no exercício/série corretos com o descanso recalculado.
7. Caminho de erro: abrir o relógio sem nunca ter recebido snapshot e sem
   conexão → "Sem treino no relógio", sem crash.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Maior lift do horizonte:** Room + kapt novos no `:wear` deixam o build mais
  lento. Se estourar o tempo do gate, otimizar o cache — **não** remover o
  `:wear` do gate.
- **Duplicação de séries no sync:** mitigada por `clientSetId` único +
  dedupe persistido no celular (mesmo padrão do `CommandDedupe`).
- **Divergência de sessão** (celular finaliza enquanto o relógio treina
  offline): o lote de sessão já finalizada é rejeitado e o relógio limpa as
  pendentes; documentado nos casos de borda.
- **`standalone = true`** muda como o app é instalado/listado pelo Wear OS;
  reverter o manifesto se a distribuição reclamar.
- **Room sem migration:** o banco nasce na v1; qualquer mudança de schema
  futura exige migration explícita e teste — não ligar
  `fallbackToDestructiveMigration`.
- **Rollback:** reverter `wear/build.gradle.kts`, remover `wear/schemas` e os
  arquivos novos do `:wear`/`:protocol` no mesmo commit. O `SharedPreferences`
  da H3-20 continua sendo a fonte do modo conectado, então o relógio volta ao
  estado atual sem perda de dados do celular.
