# H3-20 — Robustez de conexão do relógio

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-20` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

## Objetivo

O pulso comunica de forma confiável: você **sabe** quando o celular está fora
de alcance, um toque não vira dois comandos e um comando dado sem conexão não
se perde — ele sai quando a conexão voltar.

## Contexto (estado atual verificado)

- **Borda 3 — sem selo de conexão:** `WorkoutWearScreen` não tem noção de
  alcance; nenhum código do módulo `:wear` consulta nodes/capability. O
  `WearTimerState.connected` existe no reducer mas ninguém o alimenta.
- **Borda 5 — sem debounce:** cada toque em "Concluir Série" gera um
  `WearCommand` com `commandId` UUID **novo** (`WorkoutWearActivity.kt`
  L81–85). O dedupe por `commandId` no celular
  (`AppPreferences.isRecentCommand` → `CommandDedupe`) não protege de toque
  duplo: IDs diferentes passam pelos dois toques e logam duas séries.
- **Borda 6 — fila que não é fila:** `WearStateStore` guarda **só IDs**
  (`enqueueCommand`, L44–49) e é chamado em `WearSyncService.onMessageReceived`
  (L85) — um caminho que não ocorre no fluxo atual (o celular não envia
  mensagem ao relógio; quem envia comando é o relógio, via
  `WorkoutWearActivity.sendCommand`). `loadPendingCommands`/
  `clearPendingCommands` não têm uso; nada é reenviado.
- **Dedupe do celular já existe e funciona** para reenvio do **mesmo** comando
  (`WearSyncRepository.onCommandReceived`, L45–50) — a fila real pode contar
  com ele.

## Fora de escopo

- Auto-connect/pairing, UI de pareamento ou permissões novas (a H2-2 não pede
  sensores; esta spec também não pede).
- O celular enviar mensagens ao relógio (estado continua indo por `DataClient`).
- Retry com backoff exponencial ou compressão da fila (FIFO com cap 20 basta
  para o uso).
- Sincronizar a fila entre processos do relógio além do `SharedPreferences`
  existente.
- Mudança no contrato `:protocol` (`commandId`/`kind` já bastam;
  `protocolVersion` continua `1`).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/res/values/wear.xml` (novo) | Declara a capability `treinos_phone` (`android_wear_capabilities`) — o celular anuncia que está alcançável |
| `wear/src/main/java/com/treinos/wear/ui/WearConnection.kt` (novo) | `WearConnectionWatcher`: consulta inicial `CapabilityClient.getCapability("treinos_phone", FILTER_REACHABLE)` + `addOnCapabilityChangedListener`, emitindo `connected: Boolean` |
| `wear/src/main/java/com/treinos/wear/ui/WearDebounce.kt` (novo) | `WearDebounce` puro: `canSend(nowMs)`/`markSent(nowMs)` com janela de 2000 ms e clock injetável |
| `wear/src/main/java/com/treinos/wear/data/WearCommandQueue.kt` (novo) | Fila pura de `WearCommand` (FIFO, cap 20, serialização JSON, `add`/`removeById`/`all`/`decode`) |
| `wear/src/main/java/com/treinos/wear/data/WearStateStore.kt` | Persiste a fila real sob chave nova (`wear_pending_commands_v2`); métodos antigos de IDs removidos com os call sites |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Selo "Sem conexão" quando `connected == false`; botões desabilitados 2 s após qualquer envio (`WearDebounce`); `sendCommand` enfileira antes de enviar e remove só com confirmação de ≥1 node |
| `wear/src/main/java/com/treinos/wear/data/WearSyncService.kt` | `onPeerConnected` reenvia a fila pendente (uma vez, removendo o que confirmar) além do `REQUEST_STATE` atual |
| `wear/src/test/java/com/treinos/wear/ui/WearDebounceTest.kt` (novo) | Janela de debounce |
| `wear/src/test/java/com/treinos/wear/data/WearCommandQueueTest.kt` (novo) | FIFO, cap, remoção, decode inválido |

## Modelo de dados / contrato

```text
Sem mudança em :protocol, Room, backup ou sync.

Capability (Data Layer):
  celular declara  res/values/wear.xml → android_wear_capabilities = ["treinos_phone"]
  relógio observa  CapabilityClient("treinos_phone", FILTER_REACHABLE)
  connected = existe nó alcançável com a capability

Debounce (puro):
  canSend(t) = t - lastSent >= 2000   (janela única para os três botões)

Fila (pura, persistida em SharedPreferences como JSON):
  add(cmd)        → FIFO, cap 20 (o mais antigo sai)
  removeById(id)  → remove o comando confirmado
  fluxo de envio: add → tenta enviar a todos os nodes conectados
                  ≥1 node confirmou → removeById
                  0 nodes ou exceção → permanece na fila
  reenvio: onPeerConnected → envia toda a fila pendente uma vez
           (o dedupe por commandId no celular torna o reenvio idempotente)

Chave nova em prefs: "wear_pending_commands_v2" (a antiga, de IDs, é ignorada —
os IDs antigos não têm payload e não podem ser reenviados).
```

## Regras e casos de borda

- **Selo:** "Sem conexão" aparece quando há sessão em andamento e
  `connected == false`; com tela de conclusão ou estado nulo, não há selo.
  Botões continuam clicáveis sem conexão (o comando fica na fila — isso é o
  comportamento desejado, não um bug).
- **Debounce:** um único bloqueio compartilhado — qualquer um dos três botões
  dispara a janela de 2 s para todos. Toque duplo em "Concluir Série" envia
  **um** comando. Janela expirada → novo envio permitido (comando novo, ID
  novo — correto, é outra série).
- **Fila com cap:** acima de 20 comandos pendentes, o mais antigo sai (o
  celular dedupe/rejeita comandos de sessão antiga de qualquer forma; a fila é
  para o intervalo entre toques, não para horas offline).
- **Fila e sessão trocada:** comando enfileirado de uma sessão que já
  finalizou → o celular responde reenviando o estado atual
  (`handleWearCommand` L417–420) e o `commandId` entra no dedupe — sem efeito
  colateral.
- **Decode inválido** na chave nova (dado corrompido) → fila tratada como
  vazia, sem crash.
- **`onPeerConnected` em rajada** → o reenvio é uma passada única por evento;
  comandos que falharem de novo permanecem para o próximo evento.
- Consultas `CapabilityClient`/envios usam `Tasks.await` fora da main thread
  (padrão já usado no `sendCommand` atual, que roda em corotina de scope).

## Critérios de aceite

1. **Dado** o celular fora de alcance (capability sem nós), **quando** há sessão em andamento no pulso, **então** o selo "Sem conexão" aparece; ao reconectar, o selo some sem reiniciar o app.
2. **Dado** o usuário tocando duas vezes seguidas em "Concluir Série", **quando** o segundo toque ocorre em menos de 2 s, **então** apenas um `LOG_SET` é enviado (o segundo toque é ignorado).
3. **Dado** o usuário tocando "Concluir Série" sem o celular por perto, **quando** o envio falha, **então** o comando fica na fila e é reenviado automaticamente no `onPeerConnected` seguinte, logando a série no celular exatamente uma vez (dedupe por `commandId`).
4. **Dado** uma fila com mais de 20 comandos pendentes, **quando** um novo é adicionado, **então** o mais antigo sai e a fila continua em 20.
5. **Dado** dados corrompidos na chave da fila, **quando** o `WearStateStore` carrega, **então** a fila é tratada como vazia, sem crash.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `wear/src/test/java/com/treinos/wear/ui/WearDebounceTest.kt` | `primeiro_toque_passa`, `segundo_toque_na_janela_bloqueia`, `janela_expirada_passa`, `janela_unica_para_todos_os_botoes` |
| `wear/src/test/java/com/treinos/wear/data/WearCommandQueueTest.kt` | `fifo_preserva_ordem`, `cap_vinte_descarta_mais_antigo`, `remove_por_command_id`, `decode_invalido_vira_fila_vazia`, `round_trip_json` |

O `WearStateStore` vira wrapper fino de `SharedPreferences` (sem Robolectric no
módulo `:wear`); a lógica testável está nos dois componentes puros. O watcher
de capability e o selo são verificados em `Verificação manual`.

## Verificação manual

1. Iniciar treino com o celular por perto: sem selo; logar uma série funciona.
2. Desligar o Bluetooth do celular (ou afastar): o selo "Sem conexão" aparece
   em alguns segundos; tocar "Concluir Série" → comando fica na fila.
3. religar o Bluetooth/aproximar: a fila é reenviada e a série aparece no
   celular uma única vez; o selo some.
4. Toque duplo rápido em "Concluir Série": apenas uma série é logada.
5. Caminho de erro: instalar o celular com APK antigo (sem a capability) → o
   selo mostra "Sem conexão" mesmo com BT ok (degradação documentada); nada
   quebra e os comandos seguem indo em tempo real.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **A capability exige APK novo do celular:** relógio novo + celular antigo
  mostra "Sem conexão" indevido (falso negativo). Mitigado: os dois APKs saem
  juntos no gate e a instalação é pareada no mesmo usuário. O selo é o único
  efeito — envio e fila funcionam independentes dele.
- **Reenvio pós-janela do dedupe:** se a fila reenviar um comando depois da
  janela de dedupe do celular, ele pode executar duas vezes. Mitigado pelo cap
  20 + reenvio só no `onPeerConnected` (janela de desconexão curta no uso
  real); o `CommandDedupe` continua sendo a barreira primária.
- `Tasks.await` em callback de listener pode travar se chamado na main thread —
  o watcher emite via corotina/`addOnSuccessListener`, nunca `Tasks.await` em
  callback do sistema.
- Rollback: reverter os arquivos do `:wear` e o `wear.xml` do celular no mesmo
  commit; nenhum schema, contrato ou dado do usuário é tocado.
