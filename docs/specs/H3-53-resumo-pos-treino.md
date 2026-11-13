# H3-53 — Resumo pós-treino

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-53` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-13) |
| Implementação | `pendente` |

**Origem:** varredura de polimento UX/UI de 2026-11-13. Hoje
`AppViewModel.finishWorkout()` (linha ~1295) só emite o snackbar "Treino
finalizado e salvo no aparelho." e volta para a Home — o fechamento do treino
não mostra o que foi feito.

## Objetivo

Ao finalizar o treino, a Home mostra imediatamente um card de conclusão com
o que foi feito (séries realizadas/planejadas, volume, duração), dispensável
com um toque.

## Fora de escopo

- Comparação com treinos anteriores, recordes e tendências (futuro).
- Compartilhar/exportar o resumo.
- Novas métricas no Histórico (a H3-28 já entrega as métricas lá).
- Persistir o resumo entre execuções do app (o card é efêmero por decisão).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/WorkoutSummary.kt` (novo) | função pura `WorkoutSummary.from(...)` sobre os dados da sessão já carregados no VM |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `finishWorkout()` captura os dados antes de finalizar e expõe `postWorkoutSummary: StateFlow<WorkoutSummary?>`; `dismissPostWorkoutSummary()` limpa |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | card/banner de conclusão no topo (padrão visual do card-herói da H3-28) com botão "Fechar" |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | chaves do card |
| `app/src/test/java/com/treinos/app/ui/WorkoutSummaryTest.kt` (novo) | testes puros |

## Modelo de dados / contrato

Puro Kotlin, sem Room novo e sem migration. Entrada: séries da sessão
(reps/carga/RPE), exercícios e `totalDurationSeconds` (gravado por
`WorkoutRepository.finishSession`, linha ~474).

```kotlin
data class WorkoutSummary(
    val setsDone: Int,
    val setsPlanned: Int?,      // null quando não há alvo do plano
    val volumeKg: Double,
    val durationSeconds: Long,
    val exercisesDone: Int,
    val exercisesTotal: Int,
)
```

## Regras e casos de borda

- Volume = Σ (carga × reps) das séries com carga > 0; séries sem carga contam
  como feitas e somam zero. Nunca NaN/Infinity.
- Duração = `totalDurationSeconds` da sessão finalizada; < 60 s mostra
  "menos de 1 min".
- `setsPlanned` vem do alvo do plano do dia; sem alvo (sessão híbrida sem
  contagem), mostra só as realizadas.
- O card aparece uma vez por treino finalizado; "Fechar" limpa; morte do
  processo não restaura o card.
- Finalizar com séries pendentes (confirmação da H3-27) mostra "X de Y".
- Mobilidade e corrida não geram card de séries (fora de escopo).

## Critérios de aceite

1. **Dado** treino com 8 de 10 séries, **quando** finalizar, **então** a
   Home mostra o card com "8 de 10 séries", volume e duração.
2. **Dado** card visível, **quando** tocar "Fechar" ou matar e reabrir o
   app, **então** o card não reaparece.
3. **Dado** treino sem nenhuma carga registrada, **quando** finalizar,
   **então** o volume mostra "0 kg" sem NaN.
4. **Dado** idioma English, **quando** finalizar, **então** o card está em
   inglês.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/WorkoutSummaryTest.kt` | `volume_soma_carga_x_reps`, `sem_carga_volume_zero`, `sem_alvo_mostra_so_realizadas`, `duracao_menor_que_um_minuto`, `exercicios_concluidos_contam_uma_vez` |

## Verificação manual

1. Treino real com algumas séries → Finalizar → conferir o card na Home
   (séries, volume, duração) e fechar.
2. Finalizar outro treino e matar o processo antes de olhar a Home → sem
   card.
3. Idioma English → card em inglês.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Baixo: função pura + card opcional na Home, nenhum dado novo. Rollback por
`git revert`.
