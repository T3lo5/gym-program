# H3-55 — Mobilidade: sessão sobrevive à morte do processo

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-55` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-13) |
| Implementação | `pendente` |

**Origem:** fora de escopo declarado da H3-31 ("mobilidade sobrevive a morte
do processo") + varredura de polimento de 2026-11-13. O
`MobilityScreenState` vive só em memória (`_mobilityState` no
`AppViewModel`, linha ~1502): matar o app no meio da rotina perde o
progresso e o tempo.

## Objetivo

Quem fecha (ou perde) o app no meio da mobilidade volta exatamente onde
parou — movimento atual e tempo restante — como o descanso já faz desde a
H3-26/H3-31.

## Fora de escopo

- Foreground service ou notificação para manter o timer fora do app.
- Wear (o relógio tem fluxo próprio).
- Corrida (timer da corrida segue o padrão atual).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | chaves novas: `mobilityRoutineId`, `mobilityMovementIndex`, `mobilityMovementDeadlineAtMillis`, `mobilityStartedAtMillis` |
| `app/src/main/java/com/treinos/app/ui/MobilitySessionEngine.kt` | função pura `resume(routine, saved, now): MobilitySessionState?` sobre a máquina de estados existente |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | gravar o deadline wall-clock a cada mudança de movimento/estado; no init, restaurar de `AppPreferences` quando a rotina existir no catálogo local; "Encerrar" limpa as chaves |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | ao retomar, mostra o estado restaurado (sem recomeçar do zero) e oferece "Recomeçar do início" |
| `app/src/test/java/com/treinos/app/ui/MobilityResumeTest.kt` (novo) | testes puros do `resume` |

## Modelo de dados / contrato

Mesmo padrão do descanso (H3-26): deadline em wall-clock
(`System.currentTimeMillis()`) em `AppPreferences` — sobrevive à morte do
processo. Nada em Room: a sessão só é registrada ao concluir, como hoje.

## Regras e casos de borda

- Deadline do movimento é wall-clock; ao retomar, o restante é
  `deadline − now` (mínimo 0).
- Movimento cujo deadline já passou: avança encadeando os vencidos até o
  movimento com tempo restante; se todos passaram, a sessão é considerada
  concluída ao reabrir (estado final, tempo real decorrido).
- Rotina removida do catálogo entre execuções → descarta o estado salvo e
  volta à tela inicial da mobilidade, sem crash.
- "Encerrar" limpa o estado persistido (não retoma).
- A transição de 5 s (H3-31) também é coberta pelo deadline.
- O timer do descanso (`restDeadlineAtMillis`) não é tocado.

## Critérios de aceite

1. **Dado** 3º movimento com 20 s restantes, **quando** matar o processo e
   reabrir 10 s depois, **então** retoma no 3º movimento com ~10 s.
2. **Dado** reabrir após o fim de dois movimentos, **quando** abrir,
   **então** os vencidos contam como feitos e a sessão segue do ponto
   válido (ou termina, se acabou).
3. **Dado** "Encerrar", **quando** reabrir, **então** nada retoma.
4. **Dado** rotina que não existe mais no catálogo, **quando** reabrir,
   **então** o estado salvo é descartado sem crash.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/MobilityResumeTest.kt` | `retoma_no_mesmo_movimento_com_restante`, `movimentos_vencidos_avancam`, `tudo_vencido_termina`, `rotina_ausente_descarta` |

## Verificação manual

1. Iniciar mobilidade, avançar dois movimentos, fechar o app pelo seletor,
   reabrir → estado retomado no movimento e tempo corretos.
2. Deixar passar o tempo de dois movimentos curtos e reabrir → vencidos
   contam como feitos.
3. "Encerrar" e reabrir → nada retoma.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Baixo. Chaves de prefs novas são aditivas (versões antigas as ignoram).
Rollback por `git revert`.
