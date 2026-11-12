# H3-18 — Timer de descanso contando no pulso

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-18` |
| MoSCoW | `M` |
| Spec aprovada em | `aprovada` (proposta de 2026-11-07) |
| Implementação | `concluída` |

## Objetivo

O descanso conta **no relógio**, segundo a segundo, sem depender de o celular
reenviar estado — o número na tela do pulso anda sozinho entre as séries.

## Contexto (estado atual verificado)

- **O reducer existe e está testado, mas ninguém o usa:** `WearTimer.reduce`
  (`wear/.../ui/WearTimer.kt`) implementa Tick/Reset/Skip/SetConnection com 5
  testes em `WearTimerTest` — e `WorkoutWearScreen` não o referencia.
- **A tela é estática:** `WorkoutWearScreen` mostra
  `"Descanso: ${state.restRemainingSeconds ?: 0}s"` (`WorkoutWearActivity.kt`
  L127 e L155–161). O número só muda quando chega estado novo do celular.
- **O celular só publica em eventos discretos:** `AppViewModel` publica o
  estado no `combine(inProgressSession, _exerciseIndex)` (L399–409) e em
  `syncWearState()` chamado de `startSession`/`logSet`/comandos (L777, L822,
  L844, L419, L423). Nada publica a cada segundo.
- **Race que impede o timer de começar:** `logSet` chama `startRest(...)` e em
  seguida `syncWearState()`. `startRest` (L1012–1025) só grava
  `_restRemaining` **dentro da corotina lançada** — no momento do publish o
  valor ainda é o antigo (`null`/`0`). O relógio nunca recebe o descanso da
  série recém-logada.
- **Pular descanso no celular não avisa o pulso:** `skipRest()` (L1027–1032)
  zera `_restRemaining` e não publica — o relógio continua contando um
  descanso que o celular já cancelou.

## Fora de escopo

- Alarme/vibração no fim do descanso (exige permissão/feedback do relógio;
  item separado).
- Editar a duração do descanso no pulso.
- Mudança no contrato `:protocol` (`restSeconds`/`restRemainingSeconds` já
  existem; `protocolVersion` continua `1`).
- Contar descanso com a tela do relógio fechada (o tick vive no Compose, em
  foreground).
- Mudar a mecânica de descanso do celular (`WorkoutScreen` intocado).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `wear/src/main/java/com/treinos/wear/ui/WearTimer.kt` | Nova função pura `syncFromState(timer, incoming, lastKey)` que decide Reset/Skip a partir de um `WearWorkoutState` e devolve o novo estado + chave sincronizada |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | `WorkoutWearScreen` passa a manter `WearTimerState` (reducer + `LaunchedEffect` de tick de 1 s) e renderizar o descanso do timer local; botão "Pular Descanso" envia `SKIP_REST` **e** aplica `Skip` local |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `startRest` grava `_restRemaining.value = duration` sincronamente antes do loop; `skipRest()` chama `syncWearState()` ao final |
| `wear/src/test/java/com/treinos/wear/ui/WearTimerTest.kt` | Casos novos da sincronização com estado do celular |

## Modelo de dados / contrato

```text
Sem mudança de contrato (:protocol, Room, backup, sync).

Regra de sincronização (pura, testável em JVM):
  restKey = "sessionId:exerciseIndex:setNumber:restRemainingSeconds"

  Ao chegar um WearWorkoutState novo:
    restRemainingSeconds > 0  e restKey != lastKey → Reset(restRemainingSeconds), lastKey = restKey
    restRemainingSeconds > 0  e restKey == lastKey → nada (republicação do mesmo descanso;
                                                      o timer local continua de onde está)
    restRemainingSeconds nulo/0 e timer rodando   → Skip (descanso acabou ou foi pulado no celular)
    caso contrário                                → estado do timer inalterado

  Tick local: a cada 1 s enquanto isRunning, reducer existente (para em 0, não fica negativo).
```

## Regras e casos de borda

- Republicação do **mesmo** descanso (ex.: resposta a `REQUEST_STATE` durante o
  descanso, ou novo estado do mesmo evento) **não reinicia** o timer — a chave
  inclui `setNumber`, que muda a cada série logada, e `restRemainingSeconds`
  cheio do início do descanso.
- Dois descansos consecutivos com a mesma duração (90 s duas vezes) reiniciam
  corretamente: o `setNumber` difere entre as séries.
- `SKIP_REST` vindo do próprio pulso: a tela aplica `Skip` local imediato (o
  celular não publica de volta nesse fluxo) e envia o comando — o estado novo
  do celular, quando chegar, é idempotente (`restRemainingSeconds` nulo/0).
- Usuário pula o descanso **no celular** → com o `syncWearState()` novo, chega
  estado com `restRemainingSeconds` nulo/0 → o pulso zera (hoje continua
  contando).
- Timer chega a 0 → para (`isRunning = false`), sem ficar negativo (reducer
  já cobre, teste existente `para_em_zero`).
- Sem conexão: o tick é local e continua (`WearTimerState.connected` não
  afeta o tick — teste existente `sem_conexao_mantem_timer_local`); a conexão
  em si é a H3-20.
- Sessão finalizada (`status = "finished"`) ou estado nulo → tela de "Treino
  Concluído!", timer irrelevante.

## Critérios de aceite

1. **Dado** um descanso de 90 s publicado pelo celular, **quando** o pulso exibe a tela, **então** o número diminui 1 a cada segundo até 0, sem receber estado novo.
2. **Dado** o usuário logando a série seguinte no celular, **quando** o estado novo chega com `restRemainingSeconds = 90` e chave diferente, **então** o timer reinicia em 90.
3. **Dado** o timer contando e uma republicação do mesmo descanso (mesma chave), **quando** o estado chega, **então** o timer NÃO reinicia e continua de onde está.
4. **Dado** o usuário tocando em "Pular Descanso" no pulso, **quando** o comando é enviado, **então** o número zera imediatamente na tela do pulso.
5. **Dado** o timer contando e o usuário pulando o descanso no celular, **quando** o estado novo chega com descanso nulo/0, **então** o timer do pulso zera.
6. **Dado** o usuário logando uma série no celular, **quando** o estado é publicado logo após `logSet`, **então** o `restRemainingSeconds` recebido é o descanso cheio da série nova (race da `startRest` eliminada).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `wear/src/test/java/com/treinos/wear/ui/WearTimerTest.kt` | `sincroniza_novo_descanso_pelo_estado`, `mesmo_descanso_nao_reinicia_timer`, `republicacao_mesma_chave_mantem_restante`, `descanso_nulo_zera_timer_rodando`, `chave_muda_com_set_number` (dois descansos de 90 s reiniciam) |

Os ajustes no `AppViewModel` (`startRest` síncrono, `skipRest` publicando) são
orquestração de ViewModel sem teste JVM hoje; a lógica nova testável
(`syncFromState`) está toda no módulo `:wear` em JVM. Comportamento do celular
verificado em `Verificação manual`.

## Verificação manual

1. Iniciar treino de força com o relógio pareado; logar uma série no celular:
   o pulso mostra o descanso cheio e ele **conta sozinho** até 0.
2. Logar a série seguinte: o timer reinicia no valor do descanso do novo
   exercício.
3. Tocar "Pular Descanso" no pulso: zera na hora; o celular encerra o descanso.
4. Tocar "Pular descanso" no celular durante a contagem: o pulso zera junto.
5. Durante o descanso, tocar "Atualizar" no pulso (`REQUEST_STATE`): o timer
   não reinicia.
6. Caminho de erro: celular fora de alcance durante o descanso → o tick
   continua local até 0, sem crash.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Tick em Compose (`LaunchedEffect` + `delay`) é o único trecho não coberto por
  teste JVM no `:wear` (o módulo não tem Robolectric); a lógica de decisão está
  toda em `syncFromState`, pura. Risco de drift do tick (1 s ± jitter) é
  aceitável para descanso de treino.
- A regra de chave pode reiniciar o timer em um caso raro: usuário avança de
  exercício no celular **durante** o descanso (índice muda, descanso nulo →
  Skip; o próximo descanso chega com chave nova → Reset). Comportamento
  desejado.
- Rollback: reverter os três arquivos não toca schema, contrato nem backup —
  revert limpo por item.
