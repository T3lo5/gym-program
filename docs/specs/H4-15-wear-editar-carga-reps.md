# H4-15 — Wear: editar carga/reps no pulso

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-15` |
| MoSCoW | `C` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |
| Depende de | H3-20 (fila real de comandos) |

## Objetivo

Ajustar a carga e as repetições da próxima série direto no pulso, sem tirar o
celular do bolso, e o valor ajustado é exatamente o que o celular grava.

## Contexto (estado atual verificado)

- O contrato **já carrega** os valores: `WearCommand` tem `reps`, `weightKg` e
  `rpe` (`protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt`
  L31–39), mas `WorkoutWearActivity.sendCommand` cria o comando só com
  `commandId`/`sessionId`/`kind` (L97–102) — os três campos vão nulos.
- O celular **já aceita** valores explícitos: `AppViewModel.handleWearCommand`
  usa `command.reps ?: ultimoSet?.reps` e `command.weightKg ?: ultimoSet?.weightKg`
  (L628–641). Hoje o `?:` sempre cai no último valor porque o pulso manda nulo.
- A tela do pulso só mostra "Última" e o botão "Concluir Série"
  (`WorkoutWearScreen`, `WorkoutWearActivity.kt` L217–262); não há controle de
  edição.
- A fila da H3-20 já transporta **qualquer** `WearCommand` com payload
  (`WearCommandQueue`, `WearStateStore.savePendingCommands`,
  `WearSyncService.resendPendingCommands` L70–97) e reenvia no
  `onPeerConnected`; o dedupe por `commandId` no celular
  (`WearSyncRepository.onCommandReceived` L45–50) torna o reenvio idempotente.
- `WearDebounce` (janela de 2 s) já protege os botões de toque duplo
  (`WorkoutWearActivity.kt` L137–155).

## Fora de escopo

- Editar uma série **já registrada** (corrigir a série anterior): o `updateSet`
  do celular (H3-27, `AppViewModel.kt` L1401) não é acionado por comando do
  relógio nesta entrega.
- Editar RPE no pulso — o campo existe no protocolo, mas a UI de RPE fica no
  celular.
- Editar o alvo do plano (`targetReps`): o pulso edita o valor da série, não o
  plano.
- Teclado numérico no relógio: a edição é por passos (+/−).
- Mudança em `:protocol` (`protocolVersion` continua `1`) ou em
  `WearWorkoutState`.
- Corrida/mobilidade (a tela não tem série; H3-19).
- Persistir um valor editado e não enviado entre aberturas da tela.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `wear/src/main/java/com/treinos/wear/ui/WearSetEditor.kt` (novo) | Estado puro `WearSetEditorState(weightKg: Double, reps: Int)` (carga `0.0` = sem carga), ações `IncrementWeight`/`DecrementWeight`/`IncrementReps`/`DecrementReps`/`Reset(lastWeightKg, lastReps, targetReps)` e `toLogSetCommand(sessionId, commandId)`; passos de 2,5 kg e 1 rep |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | `WorkoutWearScreen` ganha os controles de carga/reps; `onLogSet` passa `reps`/`weightKg`; `sendCommand(sessionId, kind, reps, weightKg)` preenche o `WearCommand` |
| `wear/src/main/res/values/strings.xml` e `values-en/strings.xml` | Rótulos "Carga", "Reps", "+" e "−" e as descrições de acessibilidade |
| `wear/src/test/java/com/treinos/wear/ui/WearSetEditorTest.kt` (novo) | Passos, limites, default e comando gerado |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | Round-trip do `WearCommand` de `LOG_SET` com `reps`/`weightKg` preenchidos (sem mudança de schema) |

## Modelo de dados / contrato

```text
Sem mudança em :protocol (protocolVersion continua 1).
  WearCommand já tem:
    reps: Int? = null
    weightKg: Double? = null
    rpe: Double? = null
  O LOG_SET do pulso passa a preencher reps/weightKg (antes nulos) e o celular
  deixa de cair no fallback da última série.

Editor (puro, em WearSetEditor.kt):
  estado: weightKg: Double  (0.0 = sem carga / peso corporal)
          reps: Int
  passos: carga ± 2.5 kg, mínimo 0.0 (0.0 → envia null)
          reps  ± 1, mínimo 1, máximo 999
  default ao abrir ou trocar de exercício:
    weightKg = último peso do exercício (WearWorkoutState.lastWeightKg) ?: 0.0
    reps     = últimas reps (WearWorkoutState.lastReps)
               ?: primeiro inteiro de targetReps
               ?: 8
  comando gerado:
    WearCommand(kind = LOG_SET, sessionId = ..., reps = editor.reps,
                weightKg = if (editor.weightKg == 0.0) null else editor.weightKg,
                rpe = null)
  carga sempre com 1 casa decimal (2,5 kg)
```

## Regras e casos de borda

- Primeira série do exercício (sem `lastWeightKg`/`lastReps`): carga `0,0`
  (sem carga) e reps do primeiro inteiro de `targetReps`; sem alvo numérico,
  `8`.
- Carga `0,0` → `weightKg = null` no comando (peso corporal), nunca `0.0`
  gravado.
- Decremento de carga abaixo de `0,0` → trava em `0,0`; incremento de reps
  acima de `999` → trava em `999`; decremento de reps abaixo de `1` → trava em
  `1`.
- `targetReps` textual (`"8-12"`, `"AMRAP"`, `""`) → primeiro inteiro
  encontrado; sem inteiro, `8`.
- Troca de exercício (`exerciseIndex` novo no `WearWorkoutState`) → o editor
  reseta para o default daquele exercício.
- Edição sem conexão → o comando com os valores entra na fila da H3-20 e é
  reenviado no `onPeerConnected`; o celular aplica uma única vez (dedupe por
  `commandId`).
- Toque duplo em "Concluir Série" → o `WearDebounce` de 2 s continua
  bloqueando; o editor **não** é limpo antes do envio.
- Valor editado e não enviado (o usuário sai da tela) → não persiste; ao
  voltar, o editor parte do default do estado atual.
- Valor não finito (`NaN`/`Infinity`) não pode ser produzido pelo editor; se
  chegar por qualquer motivo, é ignorado e vale o default.
- Corrida/mobilidade → nenhum controle de carga/reps é exibido.

## Critérios de aceite

1. **Dado** um treino de força em andamento com última série `20 kg × 8`,
   **quando** a tela do pulso abre, **então** o editor mostra `20,0 kg` e
   `8 reps`.
2. **Dado** o editor em `20,0 kg`, **quando** o usuário toca "+" de carga,
   **então** mostra `22,5 kg`; tocar "−" volta a `20,0 kg`.
3. **Dado** o editor em `8 reps`, **quando** o usuário toca "+" de reps,
   **então** mostra `9`; "−" volta a `8`.
4. **Dado** o editor em `22,5 kg` e `9 reps`, **quando** o usuário toca
   "Concluir Série" com conexão, **então** o celular grava a série com
   `22,5 kg` e `9 reps` (não os valores da última série).
5. **Dado** o editor em `0,0 kg` (exercício sem última carga), **quando** o
   usuário conclui a série, **então** o celular grava `weightKg = null`
   (peso corporal).
6. **Dado** o pulso sem conexão com o celular, **quando** o usuário conclui
   uma série editada, **então** o comando com os valores fica na fila e é
   aplicado **uma única vez** ao reconectar.
7. **Dado** uma sessão de corrida/mobilidade, **quando** o pulso abre,
   **então** nenhum controle de carga/reps aparece.
8. **Dado** `./scripts/verify.sh`, **então** `:wear:testDebugUnitTest` roda os
   testes do editor e o gate termina com a linha de sucesso.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `wear/src/test/java/com/treinos/wear/ui/WearSetEditorTest.kt` | `default_usa_ultima_serie`, `default_sem_ultima_usa_alvo`, `default_sem_alvo_usa_oito`, `incremento_de_carga_em_2_5`, `decremento_de_carga_nao_fica_negativo`, `carga_zero_vira_sem_carga`, `incremento_de_reps`, `decremento_de_reps_trava_em_um`, `incremento_de_reps_trava_em_999`, `alvo_textual_pega_primeiro_inteiro`, `reset_ao_trocar_de_exercicio`, `comando_log_set_leva_os_valores` |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | `round_trip_do_log_set_com_carga_e_reps` |

A tela em si (botões +/− e integração com a fila) é verificada em
`Verificação manual`; a lógica testável está no editor puro.

## Verificação manual

1. Instalar `dist/treinos.apk` no celular e `dist/wear.apk` no relógio; parear.
2. Iniciar um treino de força no celular e abrir o app no relógio.
3. Ajustar carga e reps com +/− e tocar "Concluir Série".
4. Conferir no celular (tela do treino e Histórico) que a série tem os valores
   editados, não os da última série.
5. Repetir no primeiro exercício (sem última série) e conferir o default
   (alvo numérico ou `8`).
6. Ativar modo avião no relógio, editar e concluir; religar e conferir que a
   série apareceu **uma única vez** no celular.
7. Caminho de erro: exercício com alvo `"AMRAP"` e sem última série → o editor
   abre em `8 reps`, sem crash.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Gravar valor errado por engano:** mitigado pelo rótulo "Última" visível e
  pelo default igual ao último valor; o usuário vê o número antes de concluir.
- **Comando reenviado pela fila aplicar valores antigos:** o payload carrega os
  valores e o dedupe por `commandId` (H3-20) garante aplicação única.
- **Acessibilidade no pulso:** botões pequenos; usar
  `minimumInteractiveComponentSize` e `contentDescription` (padrão já usado na
  tela).
- **Rollback:** reverter os arquivos do `:wear` e as strings no mesmo commit.
  Nenhum schema, backup, sync ou contrato é tocado — o protocolo não muda.
