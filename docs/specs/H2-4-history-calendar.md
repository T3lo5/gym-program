# H2-4 — Histórico com calendário e métricas

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-4` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `concluída` |
| Depende de | H1-2 (séries), H1-3 (opcional, para músculo) |

## Objetivo

Transformar o histórico em uma visão de evolução: calendário dos dias treinados,
totais do período (treinos, séries, volume, duração) e volume por músculo, sem
sair do aparelho.

## Fora de escopo

- Exportar CSV/Planilhas (item separado do roadmap).
- Comparação com amigos, ranking ou nuvem.
- Gráficos sofisticados/zoom ou biblioteca de charts.
- Alterar o schema Room ou criar tabela de métricas.
- Buscar o catálogo no backend para descobrir músculo.
- Editar/excluir sessões antigas.
- Relatório por exercício individual (a lista expandida já mostra as séries).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/history/HistoryMetrics.kt` | **Novo.** Cálculo puro de totais e volume |
| `app/src/main/java/com/treinos/app/ui/history/CalendarMonth.kt` | **Novo.** Grade do mês e dias com treino |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Calendário, cards, filtro por dia e estados vazios |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | `MetricCard` reutilizável |
| `app/src/main/java/com/treinos/app/ui/Formatters.kt` | `formatVolumeKg`, `formatMonthYear`, `formatDateShort` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Passar `activePlan`/exercícios para o mapeamento de músculo |
| `app/src/test/java/com/treinos/app/ui/history/HistoryMetricsTest.kt` | **Novo.** Regras de agregação |
| `app/src/test/java/com/treinos/app/ui/history/CalendarMonthTest.kt` | **Novo.** Grade e dias marcados |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Tudo derivado de `List<SessionWithExercises>` (já existe em
`data/local/Entities.kt`). Nenhuma query nova é obrigatória; a tela recebe a
lista que o `AppViewModel.sessions` já expõe.

```kotlin
data class HistoryMetrics(
    val sessionCount: Int,
    val completedCount: Int,
    val totalSets: Int,
    val totalReps: Int,
    val totalVolumeKg: Double,
    val totalDurationSeconds: Long,
    val volumeByMuscle: Map<String, Double>, // rótulo -> kg
)

fun buildHistoryMetrics(
    sessions: List<SessionWithExercises>,
    muscleByExerciseId: Map<String, String>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): HistoryMetrics

fun buildMonthGrid(
    year: Int,
    month: Int,                 // 1..12
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): List<LocalDate?>            // 42 células; null = fora do mês

fun sessionDates(sessions: List<SessionWithExercises>): Set<LocalDate>
```

Regras de cálculo:

- Considerar apenas sessões com `status == "completed"` nas métricas. Sessões
  `in_progress` aparecem no calendário com marcador diferente, mas não somam.
- Série conta se `isCompleted = true` e `isWarmup = false`.
- `totalReps` soma `reps` não nulo das séries válidas.
- `totalVolumeKg` soma `reps * weightKg` apenas quando **ambos** existem;
  arredondar para 1 casa na exibição, não no cálculo.
- `totalDurationSeconds` soma `session.totalDurationSeconds` das concluídas.
- `volumeByMuscle` divide o volume da série igualmente entre os músculos do
  exercício (string `primaryMuscles` separada por vírgula). Exercício fora do
  mapa → chave `Outros`.
- `muscleByExerciseId` é montado no `TreinosApp` a partir de
  `activePlan.days.flatMap { it.exercises }`, usando `exerciseId` →
  `muscleLabel(primaryMuscles.substringBefore(',').trim())`. O mesmo
  `exerciseId` em planos antigos usa o mapa do plano ativo; se não existir,
  cai em `Outros`.
- `from`/`to` são inclusivos e no fuso `ZoneId.systemDefault()`.
- `formatVolumeKg`: `< 1000` → `"850 kg"`; `>= 1000` → `"1,2 t"` com uma casa.

## Regras e casos de borda

1. Nenhuma sessão → calendário do mês atual, métricas zeradas e `EmptyState`
   com ação “Gerar treino”.
2. Mês sem sessões → grade normal, sem marcadores, métricas do mês zeradas.
3. Sessão em andamento não entra nas métricas; no calendário usa um ponto oco.
4. Série sem reps ou sem carga não soma volume, mas conta em `totalSets` se
   `isCompleted`.
5. Warmup não conta em séries, reps nem volume (mas continua visível no
   detalhe expandido).
6. Volume por músculo não pode ser contado duas vezes: dividir igualmente.
7. `primaryMuscles` vazio → `Outros`.
8. Nome de músculo desconhecido → usar `muscleLabel` (fallback devolve o texto
   original).
9. Virada de mês/ano: 31/12 → 01/01 sem erro.
10. Fuso: uma sessão às 23:30 de 31/12 conta no dia 31/12 local.
11. Mês futuro: navegação permitida, mas o botão “próximo” desabilita depois do
    mês atual.
12. Tocar num dia com sessão filtra a lista; tocar de novo limpa o filtro.
13. Tocar num dia vazio não abre detalhe e não muda a lista.
14. Texto com fonte grande (H3-3) não pode cortar os cards; usar `wrapContent`.

## Critérios de aceite

1. **Dado** 2 sessões concluídas em dias diferentes, **quando** abre o
   histórico, **então** os dois dias aparecem marcados no calendário e o card
   mostra “2 treinos”.
2. **Dado** uma sessão com 3 séries válidas (10×40, 8×40, 12×30), **então**
   `totalVolumeKg = 1080.0` e `totalSets = 3`.
3. **Dado** uma série marcada como warmup, **então** ela não entra nas métricas.
4. **Dado** uma sessão em andamento, **então** ela não soma nas métricas, mas
   aparece no dia com marcador diferente.
5. **Dado** um exercício de `chest`, `back`, **então** o volume é dividido 50%
   para cada e aparece em “Peito” e “Costas”.
6. **Dado** um `exerciseId` que não está no plano ativo, **então** o volume vai
   para “Outros”.
7. **Dado** o mês atual com treinos, **quando** toca em um dia, **então** a
   lista mostra só as sessões daquele dia; tocar de novo limpa.
8. **Dado** um mês sem treinos, **então** a grade aparece e o estado vazio é
   exibido sem erro.
9. **Dado** 31/12 e 01/01, **quando** navega, **então** o ano muda
   corretamente.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/history/HistoryMetricsTest.kt` | `conta_apenas_sessoes_concluidas`, `ignora_series_warmup`, `soma_volume_apenas_com_reps_e_carga`, `conta_series_sem_carga`, `divide_volume_entre_musculos`, `mapeia_exercicio_desconhecido_para_outros`, `soma_duracao_de_sessoes_concluidas`, `filtra_por_periodo_inclusivo`, `lida_com_lista_vazia` |
| `app/src/test/java/com/treinos/app/ui/history/CalendarMonthTest.kt` | `gera_grade_de_seis_semanas`, `primeiro_dia_da_semana_e_segunda`, `marca_dias_com_sessao`, `mes_de_fevereiro_nao_estoura`, `virada_de_ano`, `mes_futuro_nao_e_bloqueado` |

A UI do calendário é verificada manualmente; se H3-1 já estiver implementado,
adicionar um teste Robolectric em `HistoryScreenTest` (não obrigatório nesta
spec).

## Verificação manual

1. Registre treinos em dias diferentes (pode alterar a data do aparelho).
2. Abra Histórico e confira os dias marcados no mês.
3. Confira “Treinos”, “Séries”, “Volume” e “Duração” contra o detalhe expandido.
4. Toque num dia e confirme o filtro; toque de novo e confirme a limpeza.
5. Navegue para o mês anterior e para o próximo (desabilitado no mês atual).
6. Confira um treino com exercício de peito + costas e veja a divisão do volume.
7. Aumente a fonte do sistema para 2.0 e confira que nada corta.
8. Ative o modo avião e confirme que tudo continua funcionando.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Volume enganoso:** peso corporal e máquinas sem carga não entram no volume;
  a UI deve rotular “volume com carga registrada”.
- **Músculo desconhecido:** cai em “Outros”; não bloqueia o histórico.
- **Performance:** a lista já é carregada inteira pelo ViewModel; agregações são
  O(n) em memória. Se o histórico crescer muito, uma spec futura move para
  queries SQL — não nesta entrega.
- **Fuso:** sempre usar o fuso local; não usar UTC para agrupar dias.
- **Rollback:** remover `HistoryMetrics`/`CalendarMonth` e voltar a
  `HistoryScreen` para a lista simples. Nenhum dado do usuário muda.
