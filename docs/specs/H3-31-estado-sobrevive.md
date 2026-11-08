# H3-31 — Estado sobrevive a rotação, teclado e processo

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-31` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-08` |
| Implementação | `implementada` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P3.1, P3.9 e P3.10).

## Objetivo

Girar o celular ou sair do app no meio do preenchimento não perde o que foi
digitado nem o progresso da rotina — e corrida/mobilidade se comportam como
parte do plano, não como protótipos.

## Contexto (estado atual verificado)

- Zero `rememberSaveable` no módulo (grep): diálogo de série
  (`WorkoutScreen.kt:392-401`), campos de corrida (`RunWorkoutScreen.kt:46-50`),
  formulário de atividade (`LogActivityScreen.kt:57-70`) — rotação recria a
  Activity (manifest sem `configChanges`) e apaga tudo.
- Mobilidade: `selectedRoutine/currentMovementIndex/timeRemaining/elapsed`
  em `remember` (`MobilityScreen.kt:45-51`) — rotação volta para a lista de
  rotinas; `TRANSITION_SECONDS = 5` definido e nunca usado
  (`MobilityRoutine.kt:22`).
- Corrida: mostra só o `dayName` (`RunWorkoutScreen.kt:69`) — o alvo planejado
  não aparece; distância em metros (`:118-126`) quando corredor pensa em km;
  "Cancelar" só sai sem avisar (`:189-194`).

## Fora de escopo

- Persistir o timer de mobilidade através de morte do processo (perda
  aceitável; sessão de treino já sobrevive via SQLite).
- GPS/sensores de corrida (fora do roadmap por decisão de privacidade).
- Rascunho de registro avulso persistido em banco.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | campos do `AddSetDialog` em `rememberSaveable` |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | campos em `rememberSaveable`; alvo do plano no topo; distância em km |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | draft como data class com `Saver` |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | estado da rotina no `AppViewModel`; transição de 5 s entre movimentos |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `mobilityState` (rotina/índice/tempo/elapsed); `RunLog` aceita km |
| `app/src/main/java/com/treinos/app/ui/RunLog.kt` | parse de distância em km (pure) |

## Modelo de dados / contrato

Sem mudança de Room/rede. `RunLogInput.distanceKm` entra no lugar de metros;
a conversão `km → m` (×1000, uma casa decimal) é pura e testada. O estado de
mobilidade vive no `AppViewModel` (sobrevive rotação; morte de processo
reinicia a rotina — aceito).

## Regras e casos de borda

- Todo campo de formulário digitado sobrevive à rotação com o diálogo/tela
  aberto (`rememberSaveable`/`Saver`).
- Mobilidade: ao voltar sem finalizar, perguntar "Descartar rotina?" (hoje
  descarta em silêncio); transição de 5 s entre movimentos com contagem
  regressiva e toque para pular (`TRANSITION_SECONDS` passa a valer).
- Corrida: card no topo mostra o alvo do dia planejado (nome + duração/
  distância quando existirem no plano); campo "Distância (km)" aceita uma
  casa decimal e salva em metros; "Cancelar" vira "Descartar treino?" com
  confirmação (hoje é sair sem avisar).
- Geração já coberta pela H3-30 (config persistida); esta spec cobre os
  demais formulários.

## Critérios de aceite

1. **Dado** diálogo de série com 12 reps / 62,5 kg digitados, **quando** o
   usuário gira o aparelho, **então** os valores continuam no diálogo.
2. **Dado** rotina de mobilidade no 3º movimento, **quando** gira o aparelho,
   **então** a rotina continua do mesmo movimento com o tempo correto.
3. **Dado** dia de corrida planejado "5 km leve", **quando** abre a tela de
   corrida, **então** o alvo aparece no topo.
4. **Dado** distância digitada "5,2" (km), **quando** finaliza, **então** a
   sessão grava 5200 m e o histórico mostra "5,2 km".
5. **Dado** registro avulso preenchido pela metade, **quando** gira, **então**
   nada se perde.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../RunLogTest.kt` | `km_uma_casa_converte_metros`, `km_invalido_rejeitado` |
| `app/src/test/java/.../MobilityTransitionTest.kt` | `transicao_5s_entre_movimentos`, `pular_transicao_avanca` |
| `app/src/test/java/.../ActivityLogSaverTest.kt` | `draft_round_trip_saver` |
| `app/src/test/java/.../WorkoutScreenTest.kt` (Robolectric) | `dialogo_preserva_campos_apos_recreate` |

## Verificação manual

1. Preencher o diálogo de série, girar, conferir valores; salvar.
2. Mobilidade: girar no meio; voltar sem finalizar → pergunta de descarte.
3. Corrida: conferir alvo do plano; digitar km com vírgula.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- `Saver` de data class mal escrito pode perder campos silenciosamente — o
  teste de round-trip cobre. Rollback por tela (mudanças independentes).
