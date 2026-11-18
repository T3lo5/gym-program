# H4-18 — Iniciar o treino de hoje pelo relógio

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-18` |
| MoSCoW | `C` |
| Versão alvo | `1.9.0` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída` |
| Depende de | Fix do canal wear (`publish()` ligado + `REQUEST_STATE` respondendo `idle`) — implementado em 2026-11-18 com regressão em `WearPublishTest`/`WearScreenKindTest` |

## Objetivo

O usuário começa o treino direto do pulso, sem tirar o celular do bolso: o
relógio mostra o treino de hoje (estado `idle` publicado pelo celular) e um
toque em "Iniciar treino de hoje" cria a sessão no celular e troca o pulso para
a tela de execução.

## Fora de escopo

- Métricas de corrida no pulso (GPS/FC — H4-17, Won't now).
- Editar séries/reps no modo standalone (H4-15 cobre o conectado).
- Corrida/mobilidade iniciadas pelo relógio: o `START_TODAY` inicia a sessão do
  dia seja qual for a modalidade, mas a execução de corrida/mobilidade no pulso
  segue a H3-19 (tela de "em andamento" + finalizar).
- Widgets/complicações novos; tiles continuam regidos pela H4-14 (o tile nunca
  inicia treino).
- Sincronização multi-dispositivo (H5-5/H5-6).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | `WearCommandKind.START_TODAY` (valor novo no enum — aditivo) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `handleWearCommand`: ramo `START_TODAY` reusando `startTodayWorkout()`; resposta sempre publicada (estado novo ou `idle`) |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Tela `idle` com botão "Iniciar treino de hoje" (só quando `dayName` não vazio) enviando `START_TODAY` + `REQUEST_STATE` |
| `wear/src/main/res/values/strings.xml` e `values-en/strings.xml` | Rótulo do botão + `contentDescription` |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | Round-trip do comando `START_TODAY` |
| `app/src/test/java/com/treinos/app/ui/WearPublishTest.kt` | Casos novos do `START_TODAY` |
| `wear/src/test/java/com/treinos/wear/ui/WearScreenKindTest.kt` | Mapeamento de tela (se extraído pelo fix) |

## Modelo de dados / contrato

```text
WearCommand.kind = START_TODAY   (protocolVersion 1, sessionId = -1)
```

- Compatibilidade: celular antigo recebe `kind` desconhecido →
  `Json.decodeFromString` falha no enum → `processarComando` loga e descarta
  (comportamento já seguro em `WearCommandListenerService`).
- Relógio antigo nunca envia `START_TODAY` — nada a fazer no celular.
- `protocolVersion` continua `1`; nenhum campo novo em `WearWorkoutState`.

## Regras e casos de borda

1. `START_TODAY` sem sessão em andamento e com treino hoje → celular cria a
   sessão (`startTodayWorkout()`), o que publica o estado `in_progress` pelo
   caminho existente; o pulso sai da tela idle sozinho.
2. `START_TODAY` sem sessão e hoje é descanso/sem plano → celular publica
   `idle` de novo (sem criar sessão); o pulso permanece na tela idle.
3. `START_TODAY` com sessão **já** em andamento → celular NÃO cria segunda
   sessão; publica o estado atual da sessão ativa (mesma resposta do
   `REQUEST_STATE`).
4. Toque duplo no pulso → debounce de 2 s da H3-20 continua valendo; no
   celular, o guarda de `startTodayWorkout()` (`inProgressSession != null`)
   impede sessão duplicada mesmo sem debounce.
5. Corrida/mobilidade de hoje → `START_TODAY` inicia a sessão normalmente; o
   pulso recebe o estado com `modality` correspondente e mostra a tela de
   "em andamento" da H3-19.
6. Falha de entrega da mensagem → o pulso permanece idle; sem fila para
   `START_TODAY` (ação de usuário presente, não estado) — o usuário toca de
   novo.

## Critérios de aceite

1. **Dado** plano com sessão hoje e sem sessão em andamento, **quando** o
   relógio envia `START_TODAY`, **então** o celular cria a sessão e publica
   `WearWorkoutState` com `status = "in_progress"` e `dayName` do dia.
2. **Dado** hoje é descanso, **quando** o relógio envia `START_TODAY`,
   **então** nenhuma sessão é criada e o celular publica `status = "idle"`.
3. **Dado** sessão em andamento, **quando** o relógio envia `START_TODAY`,
   **então** o número de sessões `in_progress` no Room continua 1 e o pulso
   recebe o estado atual.
4. **Dado** celular com app antigo (sem o ramo `START_TODAY`), **quando** o
   relógio envia o comando, **então** o celular loga e descarta sem crash.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/WearPublishTest.kt` | `start_today_sem_sessao_inicia_e_publica_in_progress`, `start_today_em_dia_de_descanso_responde_idle`, `start_today_com_sessao_ativa_nao_duplica_e_publica_estado_atual` |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | round-trip `START_TODAY` (encode/decode dos dois lados) |
| `wear/src/test/java/com/treinos/wear/ui/WearScreenKindTest.kt` | `idle` mapeia tela idle; status desconhecido mapeia idle (herdado do fix) |

## Verificação manual

1. Celular com plano de hoje e sem sessão; relógio mostra "Sem treino em
   andamento — Hoje: <dia>"; toque em "Iniciar treino de hoje" → pulso passa a
   mostrar o exercício 1 e o celular registra a sessão.
2. Dia de descanso: toque não cria sessão; pulso segue idle.
3. Com sessão já em andamento no celular: toque não duplica; pulso mostra a
   sessão em curso.
4. Fechar o app do celular, tocar "Iniciar" no pulso, reabrir o celular: a
   sessão existe (comando acorda o `WearableListenerService`).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Sessão duplicada por corrida de toques:** guarda do `startTodayWorkout()`
  + dedupe por `commandId` no celular.
- **Relógio/celular fora de versão:** comando desconhecido é descartado com
  log nos dois lados; nenhuma migration.
- **Rollback:** remover o ramo do `handleWearCommand` e o botão; o enum extra
  no protocolo é inerte para quem não o envia.
