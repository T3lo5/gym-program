# H2-2 — Wear OS MVP (Data Layer)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-2` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `concluída` |
| Depende de | H1-2 (execução por séries), H3-1 (testes de contrato) |

## Objetivo

Levar o treino em andamento para o relógio: o usuário vê o exercício atual, a
série, o alvo de repetições e o timer de descanso sem tirar o celular do bolso,
e consegue marcar a série no pulso.

## Fora de escopo

- Relógio standalone: o celular continua sendo a fonte de verdade do Room.
- Editar plano, trocar exercício ou criar treino no relógio.
- Watchface, tiles, complicações ou notificações.
- Login/pareamento manual: o pareamento é o do próprio Wear OS.
- Sincronizar histórico completo ou backup pelo relógio.
- iOS, Wear OS 2 e relógios sem Play Services.
- Migrar o app do celular para Compose Multiplatform.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `settings.gradle.kts` | Incluir `:protocol` e `:wear`; manter `:shared` fora do build |
| `settings.gradle.kts` (`pluginManagement`) | Resolver `org.jetbrains.kotlin.jvm` para `1.9.24` |
| `protocol/build.gradle.kts` | **Novo.** Kotlin/JVM 17 + `kotlinx-serialization` |
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | **Novo.** DTOs compartilhados celular ↔ relógio |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | **Novo.** Round-trip e compatibilidade |
| `app/build.gradle.kts` | `implementation(project(":protocol"))` |
| `app/src/main/java/com/treinos/app/data/wear/WearSyncRepository.kt` | **Novo.** `DataClient`/`MessageClient` + fila de comandos |
| `app/src/main/java/com/treinos/app/data/wear/WearSessionMapper.kt` | **Novo.** `SessionWithExercises` → `WearWorkoutState` |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | Construir o repositório no `AppContainer` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Publicar estado e consumir comandos do relógio |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | **Novo.** Mapeamento e bordas |
| `app/src/test/java/com/treinos/app/data/wear/WearCommandDedupeTest.kt` | **Novo.** Idempotência |
| `wear/build.gradle.kts` | Virar `com.android.application`, `applicationId`, remover `:shared`, usar `:protocol` |
| `wear/src/main/AndroidManifest.xml` | Remover `package=`, registrar `WearApplication` e `WearSyncService` |
| `wear/src/main/java/com/treinos/wear/WearApplication.kt` | **Novo.** Container mínimo |
| `wear/src/main/java/com/treinos/wear/data/WearStateStore.kt` | **Novo.** Último estado + fila local |
| `wear/src/main/java/com/treinos/wear/data/WearSyncService.kt` | **Novo.** `WearableListenerService` |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Corrigir imports e implementar a tela |
| `wear/src/main/java/com/treinos/wear/ui/WearTimer.kt` | **Novo.** Reducer puro do timer |
| `wear/src/test/java/com/treinos/wear/ui/WearTimerTest.kt` | **Novo.** Timer e reconexão |
| `scripts/ci-android.sh` | Compilar/testar `:protocol`, `:app` e `:wear` |
| `Dockerfile` | `COPY protocol ./protocol` |
| `dist/wear.apk` | **Novo artefato** no CI (além de `dist/treinos.apk`) |

Não edite `shared/` nem `app/src/main/java/com/treinos/shared/` (legado). O módulo
`:shared` continua fora do build; o `wear` deixa de depender dele.

## Modelo de dados / contrato

Módulo `:protocol`, pacote `com.treinos.protocol`. Tudo `@Serializable` com
`ignoreUnknownKeys = true`.

```kotlin
const val PATH_WORKOUT_STATE = "/treinos/workout-state"
const val PATH_COMMAND = "/treinos/command"

@Serializable
data class WearWorkoutState(
    val protocolVersion: Int = 1,
    val sessionId: Long,
    val dayName: String,
    val exerciseIndex: Int,
    val exerciseCount: Int,
    val exerciseName: String,
    val setNumber: Int,
    val plannedSets: Int,
    val targetReps: String,
    val lastWeightKg: Double? = null,
    val lastReps: Int? = null,
    val restSeconds: Int = 90,
    val restRemainingSeconds: Int? = null,
    val nextExerciseName: String? = null,
    val status: String, // in_progress | finished
    val updatedAt: Long,
)

@Serializable
data class WearCommand(
    val protocolVersion: Int = 1,
    val commandId: String, // UUID gerado no relógio
    val sessionId: Long,
    val kind: WearCommandKind,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
)

@Serializable
enum class WearCommandKind { LOG_SET, SKIP_REST, FINISH_WORKOUT, REQUEST_STATE }
```

Regras de transporte:

- Celular → relógio: `DataClient.putDataItem` em `PATH_WORKOUT_STATE` (o último
  valor sobrevive a desconexão/reboot do relógio).
- Relógio → celular: `MessageClient.sendMessage` em `PATH_COMMAND`.
- `WearSyncRepository` no celular expõe:
  ```kotlin
  fun publish(state: WearWorkoutState)
  fun observeCommands(): Flow<WearCommand>
  ```
- `WearSyncRepository` usa um `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
  interno e fecha em `AppContainer`? Não há `close()` no container; usar
  `NodeClient` e cancelar apenas no `onCleared` do ViewModel da tela. O
  repositório em si é stateless e pode viver com o processo.
- O relógio inicia com `REQUEST_STATE` quando a tela abre.
- O celular responde `REQUEST_STATE` com o estado atual ou `status = finished`
  se não houver sessão ativa.
- O relógio **não** grava Room; ele só envia comandos.

### Regras de comando

- `LOG_SET` no celular chama `AppViewModel.logSet(exercise, reps, weightKg, rpe)`
  com os valores recebidos. Se `reps`/`weightKg` vierem nulos, usar o último
  valor da sessão; se não houver, usar `plannedSets` do exercício e carga `null`.
- `SKIP_REST` chama `AppViewModel.skipRest()`.
- `FINISH_WORKOUT` chama `AppViewModel.finishWorkout()`.
- `commandId` é deduplicado no celular. Manter os últimos 50 IDs em
  `AppPreferences` (`wear_recent_commands`, JSON ou `StringSet`). Comando
  repetido é ignorado sem erro.
- Comando de `sessionId` diferente da sessão ativa é ignorado e o celular
  reenvia o estado atual.
- Se o celular estiver inalcançável, o relógio guarda até 20 comandos em
  `WearStateStore` e reenvia quando `onPeerConnected` disparar, na ordem.
- `protocolVersion` diferente de `1` → ignorar e mostrar “Atualize o app no
  celular”.

### Regras de estado

- `exerciseIndex` é 0-based; `setNumber` é 1-based.
- `nextExerciseName` é o próximo exercício incompleto ou `null` se não houver.
- `status = finished` quando `finishWorkout()` conclui; a tela mostra
  “Treino concluído” e um botão “Voltar ao início”.
- `lastWeightKg`/`lastReps` vêm da última série do exercício atual.
- `restRemainingSeconds` é o valor do celular no momento do envio; o relógio
  conta localmente a partir dele.

## Regras e casos de borda

1. Sem relógio pareado: o app do celular funciona normalmente, sem erro na UI.
2. Relógio pareado sem o app instalado: nenhum crash; o celular só não publica.
3. Celular e relógio desconectados: o relógio mostra o último estado com o selo
   “Sem conexão”; o timer continua local.
4. Ao reconectar, o relógio envia `REQUEST_STATE` e a fila pendente.
5. Série marcada no relógio duas vezes (toque duplo): o `commandId` diferente
   gera duas séries — a UI do relógio desabilita o botão por 2 s após o toque.
6. Sessão finalizada no celular enquanto o relógio está aberto: o próximo
   estado tem `status = finished` e o relógio limpa os botões.
7. Troca de exercício no celular: o relógio recebe `exerciseIndex` novo e
   reinicia o timer local com o `restRemainingSeconds` atual.
8. Exercício sem `targetReps` numérico: mostrar o texto cru, sem quebrar.
9. Nome de exercício muito longo: `maxLines = 2` + ellipsis.
10. Relógio reiniciado: o último estado vem do Data Layer; se não houver,
    envia `REQUEST_STATE`.
11. Sem permissão de notificação: o app não depende dela para funcionar.
12. `wear` não deve pedir `BODY_SENSORS`, `ACTIVITY_RECOGNITION` nem
    `FOREGROUND_SERVICE_HEALTH` nesta entrega: remover permissões não usadas
    (lint de permissões).
13. O celular só recebe comandos com o processo do app vivo (treino em
    andamento). Se o app for encerrado, o relógio mantém o último estado e
    reenvia a fila quando o app abrir de novo.

## Critérios de aceite

1. **Dado** um relógio pareado com o app instalado, **quando** o usuário inicia
   um treino no celular, **então** o relógio mostra dia, exercício 1, “Série
   1/N” e o alvo de repetições em até 3 s.
2. **Dado** o treino em andamento, **quando** o usuário registra uma série no
   celular, **então** o relógio atualiza a série e inicia o timer de descanso.
3. **Dado** o relógio com o botão “Concluir série”, **quando** o usuário toca,
   **então** o celular grava a série, o relógio avança e o timer começa.
4. **Dado** o relógio com “Pular descanso”, **quando** toca, **então** o
   `restRemaining` do celular vai a zero.
5. **Dado** o celular finaliza o treino, **quando** o relógio recebe o estado,
   **então** mostra “Treino concluído” e não permite mais comandos.
6. **Dado** o relógio sem conexão, **quando** o usuário toca em “Concluir
   série”, **então** o comando fica na fila e é aplicado **uma única vez** ao
   reconectar.
7. **Dado** o app do celular aberto sem relógio, **então** o fluxo atual
   funciona sem regressão.
8. **Dado** `./scripts/verify.sh`, **então** ele compila `:protocol`, `:app` e
   `:wear`, roda os testes JVM dos três e verifica a assinatura dos dois APKs.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | `round_trip_do_estado`, `round_trip_do_comando`, `ignora_chaves_desconhecidas`, `versao_incompativel_e_detectada` |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `mapeia_primeiro_exercicio`, `mapeia_ultimo_exercicio_sem_proximo`, `inclui_ultima_carga_e_reps`, `sessao_finalizada_vira_status_finished`, `ignora_sessao_sem_exercicios` |
| `app/src/test/java/com/treinos/app/data/wear/WearCommandDedupeTest.kt` | `ignora_command_id_repetido`, `aceita_command_ids_diferentes`, `limita_historico_a_50`, `ignora_sessao_diferente` |
| `wear/src/test/java/com/treinos/wear/ui/WearTimerTest.kt` | `decrementa_a_cada_segundo`, `para_em_zero`, `estado_novo_reinicia_timer`, `pular_descanso_zera_timer`, `sem_conexao_mantem_timer_local` |

Testes de Data Layer real exigem dois emuladores pareados; a verificação é
manual.

## Verificação manual

1. Instale `dist/treinos.apk` no celular e `dist/wear.apk` no relógio/emulador
   Wear (API 30+).
2. Pareie pelo app Wear OS do celular.
3. Inicie um treino no celular e abra “Treinos Watch” no relógio.
4. Confirme exercício, série, alvo e timer.
5. Registre uma série no celular; confirme o relógio.
6. Toque em “Concluir série” no relógio; confirme o Room/Histórico no celular.
7. Ative modo avião no relógio, toque em concluir, desative e confirme que a
   série foi aplicada uma única vez.
8. Finalize no celular e confirme a tela de conclusão no relógio.
9. Rode sem relógio pareado e confirme que o celular não mostra erro.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Build maior e mais lento:** `:wear` entra no gate; o CI passa a construir
  dois APKs. Se estourar o tempo, otimizar cache — não remover `:wear` do gate.
- **Play Services:** Data Layer exige Play Services no celular e no relógio.
  Emuladores precisam da imagem “Wear OS” com Play.
- **Duplicação de séries:** mitigada por `commandId` + botão desabilitado.
- **Divergência de timer:** o celular é a fonte de verdade; qualquer estado novo
  sobrescreve o timer local.
- **`shared` legado:** não reativar `:shared`; o `:protocol` é o novo contrato.
- **Rollback:** remover `:wear`/`:protocol` do `settings.gradle.kts`, do
  `ci-android.sh` e do `Dockerfile`; o app do celular volta ao estado atual.
  Nenhum dado do usuário é alterado.
