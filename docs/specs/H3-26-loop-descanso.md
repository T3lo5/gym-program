# H3-26 — Loop do descanso: avisar, ficar visível e contar tempo real

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-26` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-07` (spec e implementação no mesmo commit, `4649848`) |
| Implementação | `concluída em 2026-11-07` (commit `4649848`) |

### Notas da implementação

- Os testes Robolectric de UI previstos em "Testes a criar" foram entregues com
  nomes diferentes em `WorkoutScreenTest`
  (`sem_serie_anterior_o_botao_repetir_nao_existe`,
  `descanso_zerado_entra_em_destaque_e_so_pular`,
  `descanso_pausado_mostra_continuar`); a lógica pura ficou em `RestTimerTest`
  e `FeedbackRuleTest` (pacote `ui`).
- `keepScreenOn` não tem teste automatizado: implementado em `ui/KeepScreenOn.kt`
  e aplicado em `WorkoutScreen`/`MobilityScreen` via `KeepScreenOnEffect`.

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P0.5, §P1.1–1.5 e 1.7).

## Objetivo

Durante o descanso — metade do tempo de treino — o app avisa quando acaba
(vibração/som), mantém o timer visível de qualquer ponto da lista, mantém a
tela acesa e conta tempo real; e registrar a série seguinte custa 1 toque
quando a carga não muda.

## Contexto (estado atual verificado)

- Timer por tick: `AppViewModel.kt:1016-1024` (`delay(1_000)` por iteração) —
  atrasa em Doze/tela apagada; ao zerar, nada acontece.
- `RestCard` é um `item` da `LazyColumn` (`WorkoutScreen.kt:186-194`) — rola
  para fora; único controle é "Pular" (`:267-272`); com `restSeconds = 0` o
  card "Descanso concluído" aparece após cada série.
- Zero `Vibrator`/`SoundPool`/`keepScreenOn`/`HapticFeedback` no módulo `app`.
- Registrar série = 2 toques + diálogo de 3 campos mesmo quando a carga não
  mudou (`WorkoutScreen.kt:374-379,391-400`); o Wear já usa a última série
  como fallback (`AppViewModel.kt:429-435`).

## Fora de escopo

- Notificação com o processo morto (o retorno com deadline persistido cobre a
  reabertura; notificação fica para item futuro).
- Editar o descanso do plano salvo; timer do relógio (H3-18 cobre o pulso).
- Cronômetro de corrida (H3-31).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/RestTimer.kt` (novo) | lógica pura: deadline, remaining, extend, pause/resume |
| `app/src/main/java/com/treinos/app/ui/Feedback.kt` (novo) | vibração + beep; regra pura de ringer → som |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `startRest`/`skipRest` + `pauseRest`/`extendRest`; persistir deadline |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | RestBar fixa acima do bottomBar; "Repetir última série"; háptico ao salvar |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | chaves `rest_deadline_at` (Long, 0 = sem descanso) e `rest_total` (Int) |
| `app/src/main/AndroidManifest.xml` | `<uses-permission android:name="android.permission.VIBRATE"/>` |

## Modelo de dados / contrato

Sem Room. Preferências: `rest_deadline_at` (epoch millis; 0 = sem descanso),
`rest_total` (segundos). `RestTimer` puro:

```text
start(total, now) -> deadline = now + total
remaining(deadline, now) = max(0, deadline - now)
extend(deadline, seconds) -> deadline + seconds
pause(remaining) -> estado pausado; resume(remaining, now) -> novo deadline
```

## Regras e casos de borda

- `startRest(0)` **não** inicia descanso: não grava estado, não mostra card.
- `remaining` é derivado de `deadline - now` (wall-clock); o tick de 1 s só
  atualiza a UI — a contagem não acumula atraso.
- "+15 s": `deadline += 15`, sem teto. Pausar guarda `remaining`; retomar
  recalcula o deadline.
- Ao zerar: vibração em waveform + beep curto; regra de som por ringer
  (`silent` → sem som, mantém vibração; `vibrate` → só vibração; `normal` →
  som + vibração) e destaque visual no card.
- Tela acesa (`FLAG_KEEP_SCREEN_ON`) enquanto houver sessão ativa, descanso
  ativo ou rotina de mobilidade rodando; liberado ao sair da tela.
- Háptico curto ao confirmar "Salvar série".
- Processo morto: ao abrir o app, `rest_deadline_at` no futuro restaura o
  descanso restante; no passado, é limpo.
- "Repetir última série (60 kg × 10)": grava em 1 toque com carga/reps/RPE da
  última série do exercício e dispara o descanso; escondido quando não há
  série anterior; o diálogo continua disponível para valores diferentes.

## Critérios de aceite

1. **Dado** descanso de 90 s ativo, **quando** o usuário rola a lista até o
   fim, **então** o timer continua visível (faixa fixa) com Pular/+15 s/Pausar.
2. **Dado** descanso zerando, **quando** `remaining` chega a 0, **então** o
   aparelho vibra e toca beep (modo normal) e o card entra em destaque.
3. **Dado** aparelho em modo silencioso, **quando** o descanso zera, **então**
   vibra sem tocar som.
4. **Dado** tela apagada por 60 s durante descanso de 90 s, **quando** o
   usuário volta, **então** o restante exibido corresponde ao tempo real
   (±2 s) — e a tela não apaga sozinha durante sessão ativa.
5. **Dado** descanso de 0 s no plano, **quando** registra série, **então**
   nenhum card de descanso aparece.
6. **Dado** processo morto com descanso em curso, **quando** reabre o app
   antes do deadline, **então** o descanso restante é restaurado.
7. **Dado** exercício com série anterior 60 kg × 10, **quando** toca
   "Repetir última série", **então** a série é registrada em 1 toque e o
   descanso começa.
8. **Dado** série salva, **quando** o diálogo fecha, **então** há feedback
   háptico curto.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../RestTimerTest.kt` | `remaining_derivado_de_deadline`, `extend_soma_sem_teto`, `pause_resume_recalcula_deadline`, `start_zero_nao_inicia` |
| `app/src/test/java/.../FeedbackRuleTest.kt` | `ringer_silent_sem_som`, `ringer_vibrate_so_vibracao`, `ringer_normal_som_e_vibracao` |
| `app/src/test/java/.../WorkoutScreenTest.kt` (Robolectric) | `rest_bar_fixa_com_descanso_ativo`, `repetir_ultima_serie_registra_1_toque`, `keep_screen_on_ligado_na_sessao` |

## Verificação manual

1. Treino real: registrar série → faixa de descanso fixa; tocar +15 s, pausar,
   retomar; deixar acabar com o celular no chão → vibra + beep.
2. Modo silencioso → repete o fim do descanso → vibra sem som.
3. Matar o app no meio do descanso e reabrir antes do deadline → descanso
   restaurado; reabrir depois → sem card fantasma.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- `VIBRATE` é permissão normal (sem prompt). Beep em modo silencioso seria o
  bug principal — coberto pela regra pura testada. Rollback: reverter os
  arquivos novos + `startRest` original; preferências novas são ignoradas
  pelo código antigo.
