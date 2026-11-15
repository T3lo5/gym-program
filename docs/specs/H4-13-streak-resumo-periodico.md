# H4-13 — Streak/consistência e resumo mensal/anual

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-13` |
| MoSCoW | `C` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |
| Depende de | H4-2 (recorte de período no Histórico), H4-1 (`PersonalRecords.kt` para a linha de PRs) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 3). O
Histórico tem calendário e métricas agregadas (`buildHistoryMetrics`, H3-28),
mas nenhum recorte periódico nem leitura de consistência; Hevy expõe
consistency, monthly report e year in review. Os dados já existem em
`WorkoutSessionEntity`/`SetLogEntity`, então o trabalho é de leitura derivada.

**Relacionado:** [H4-1](H4-1-recordes-pessoais-1rm.md) define o motor canônico
de PR (`personalRecord`, `isPersonalRecord`, `recordSetsFor`). Esta spec **não**
reimplementa a comparação de recorde: reusa essas funções para contar os PRs do
período. [H4-2](H4-2-graficos-progresso.md) entrega os gráficos no Histórico; o
card desta spec convive com eles, sem alterar o calendário.

## Objetivo

O usuário vê na Home há quantos dias treina sem falhar (sequência atual e
melhor sequência) e, no Histórico, um resumo do mês ou do ano com treinos
concluídos, volume, dias ativos, média por semana e PRs batidos no período —
tudo calculado do histórico local, offline e sem conta.

## Fora de escopo

- Notificação/lembrete para não quebrar a sequência; metas, badges ou
  gamificação; compartilhar o resumo.
- Nova tabela, coluna ou migration no Room: tudo é derivado de
  `List<SessionWithExercises>` a cada leitura.
- Alterar o calendário ou as métricas mensais da H3-28
  (`MetricsCards`/`VolumeByMuscle` continuam mensais).
- Gráficos de tendência (H4-2) e drill-down por exercício (H4-3).
- Recorte por semana, trimestre ou intervalo customizado; streak por
  modalidade (corrida e mobilidade contam igual à musculação).
- Backend, nuvem, exportação ou contrato do `:protocol`.
- Recalcular 1RM/PR com fórmula diferente da H4-1 (aqui só se consome).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/history/PeriodReport.kt` (novo) | `ReportPeriod`, `StreakSummary`, `PeriodReport`, `buildStreak(...)`, `buildPeriodReport(...)`; funções puras, sem Android |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | toggle Mês/Ano e novo composable privado `PeriodReportCard`; o calendário e `MetricsCards` não mudam |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | novo composable privado `StreakCard`, renderizado a partir de `sessions` (já recebido pela tela) |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | chaves do card de streak e do relatório (paridade PT/EN obrigatória) |
| `app/src/test/java/com/treinos/app/ui/history/PeriodReportTest.kt` (novo) | testes JVM das funções puras |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` (Robolectric) | card de streak na Home |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` (Robolectric) | card do relatório e toggle Mês/Ano |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Sem mudança de Room, rede, preferências ou `:protocol`. Todo o contrato é puro,
derivado de `List<SessionWithExercises>` e reusa `HistoryMetrics`/`buildHistoryMetrics`
(H3-28) e `personalRecord`/`isPersonalRecord`/`recordSetsFor`/`RecordSet`/`OneRepMaxFormula`
(H4-1, `app/src/main/java/com/treinos/app/ui/progression/PersonalRecords.kt`):

```kotlin
enum class ReportPeriod { MONTH, YEAR }

data class StreakSummary(
    val currentDays: Int,
    val bestDays: Int,
    val lastCompletedDate: LocalDate?,
)

data class PeriodReport(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val metrics: HistoryMetrics,   // buildHistoryMetrics(sessions, muscleByExerciseId, from, to)
    val activeDays: Int,
    val newPrCount: Int,
    val sessionsPerWeek: Double,
)

/** Datas com pelo menos uma sessão concluída; duplicatas contam uma vez. */
fun buildStreak(completedDates: Set<LocalDate>, today: LocalDate): StreakSummary

fun buildPeriodReport(
    sessions: List<SessionWithExercises>,
    muscleByExerciseId: Map<String, String>,
    period: ReportPeriod,
    reference: LocalDate,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): PeriodReport
```

`reference` é o mês exibido no calendário (`month.atDay(1)` no
`HistoryScreen`): o relatório mensal acompanha a navegação do calendário e o
anual usa o ano de `reference`.

## Regras e casos de borda

1. Streak e métricas do relatório contam apenas sessões
   `status == STATUS_COMPLETED` (`"completed"`); `in_progress` não entra em
   treinos, volume, dias ativos nem média. Exceção: `newPrCount` segue a regra
   11 da H4-1 e considera também séries de sessões em andamento.
2. Datas no fuso do sistema via `sessionDay` (mesma regra do
   `buildHistoryMetrics`): 23:30 de 31/12 continua sendo 31/12.
3. `buildStreak` recebe um `Set<LocalDate>` (duplicatas contam uma vez).
   Datas futuras (`> today`) são descartadas antes de qualquer cálculo.
   `currentDays`: se `today` está no conjunto, conta para trás a partir de hoje;
   senão, se `today.minusDays(1)` está no conjunto, conta para trás a partir de
   ontem; senão `0`.
4. `bestDays`: maior sequência consecutiva do conjunto; conjunto vazio → `0`.
   `lastCompletedDate`: maior data do conjunto; vazio → `null`.
5. `MONTH`: `from = firstDayOfMonth(reference.year, reference.monthValue)`,
   `to = lastDayOfMonth(reference.year, reference.monthValue)`. `YEAR`:
   `from = LocalDate.of(reference.year, 1, 1)`,
   `to = LocalDate.of(reference.year, 12, 31)`.
6. `metrics = buildHistoryMetrics(sessions, muscleByExerciseId, from, to)` —
   reaproveita a agregação existente (séries/reps/volume/duração por
   modalidade), inclusive a divisão de volume entre músculos.
7. `activeDays` = número de datas distintas com sessão concluída dentro de
   `from..to`.
8. `newPrCount` = número de exercícios com série válida no período cujo melhor
   candidato do período vence o recorde de todos os tempos **anterior a
   `from`**, pela regra canônica da H4-1 (`isPersonalRecord`). O candidato é o
   `RecordSet` de maior 1RM estimado do período (desempate por carga, depois
   reps, depois `sessionStartedAt`), obtido de `recordSetsFor(sessionsNoPeriodo, exerciseId)`.
   Primeiro registro válido conta como PR (regra 8 da H4-1); empate não conta
   (regra 4). Corrida, natação e mobilidade não geram PR (H4-1).
9. `sessionsPerWeek = completedCount / (diasDoPeriodo / 7.0)`, arredondado a 1
   casa; nunca `NaN`/`Infinity`. Período sem dias → `0.0`.
10. Lista vazia → `PeriodReport` com métricas zeradas, `activeDays = 0`,
    `newPrCount = 0`, `sessionsPerWeek = 0.0`; `StreakSummary(0, 0, null)`.
    Nenhuma entrada lança exceção.
11. O toggle Mês/Ano do Histórico é independente do calendário: navegar o
    calendário muda a `reference` do relatório mensal; o anual usa o ano da
    `reference`. Trocar para Ano não altera o calendário.
12. O `StreakCard` da Home só aparece quando `bestDays > 0` (usuário com pelo
    menos um treino concluído); sem histórico, a Home fica como está.
13. `newPrCount` usa Epley por padrão (H4-1); Brzycki fica disponível pelo
    parâmetro `formula` e é testado.
14. Nada é gravado; abrir Home/Histórico não altera dados e funciona em modo
    avião.

## Critérios de aceite

1. **Dado** treinos concluídos em 3 dias consecutivos terminando hoje,
   **quando** a Home abre, **então** o card mostra sequência atual `3` e melhor
   `3`.
2. **Dado** treinos em 5 dias consecutivos na semana passada e nenhum nos
   últimos 2 dias, **quando** a Home abre, **então** a sequência atual é `0` e
   a melhor é `5`.
3. **Dado** um mês com 8 treinos concluídos e 12.000 kg, **quando** o Histórico
   mostra o relatório do mês, **então** aparecem 8 treinos, 12.000 kg, dias
   ativos e média por semana.
4. **Dado** o relatório do mês, **quando** o toggle "Ano" é selecionado,
   **então** o relatório agrega de 1º de janeiro a 31 de dezembro do ano do mês
   exibido.
5. **Dado** um exercício sem histórico antes do mês e uma série válida no mês,
   **quando** o relatório é calculado, **então** `newPrCount` conta 1; repetir
   a mesma carga × reps no mês não conta outro PR.
6. **Dado** nenhum treino, **quando** Home/Histórico abrem, **então** não há
   crash: o card de streak não aparece e o relatório mostra zeros.
7. **Dado** o idioma English, **então** os cards estão em inglês.
8. **Dado** modo avião, **então** streak e relatório funcionam sem rede.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/history/PeriodReportTest.kt` | `streak_tres_dias_consecutivos`, `streak_sem_treino_hoje_mantem_ate_ontem`, `streak_quebrada_zera_atual_mantem_melhor`, `streak_datas_duplicadas_contam_uma_vez`, `streak_conjunto_vazio`, `streak_ignora_datas_futuras`, `relatorio_mes_limites`, `relatorio_ano_limites`, `relatorio_conta_so_concluidas`, `relatorio_dias_ativos`, `relatorio_media_por_semana`, `relatorio_vazio_nao_quebra`, `pr_primeiro_registro_conta`, `pr_empate_nao_conta`, `pr_melhora_1rm_conta`, `pr_considera_sessao_em_andamento`, `pr_formula_brzycki_muda_resultado`, `relatorio_volume_por_musculo_reaproveitado`, `relatorio_virada_de_ano_local` |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` (Robolectric) | `card_streak_aparece_com_historico`, `card_streak_ausente_sem_treino` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` (Robolectric) | `relatorio_mes_mostra_metricas`, `toggle_ano_agrega_ano` |

## Verificação manual

1. Fazer 3 treinos em dias seguidos → Home mostra "Sequência: 3 dias".
2. Deixar 2 dias sem treinar → sequência atual 0, melhor 3.
3. Histórico → relatório do mês com treinos, volume, dias ativos, média e PRs;
   alternar para Ano e conferir a soma do ano.
4. Navegar o calendário para um mês antigo → o relatório mensal acompanha o mês
   exibido; o calendário e as métricas mensais continuam iguais.
5. Idioma English → cards em inglês.
6. Caminho de erro: instalação nova (banco vazio) → sem crash, sem card de
   streak, relatório zerado.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Divergência com a H4-1:** `newPrCount` depende de `isPersonalRecord`/
  `recordSetsFor` (H4-1). Se a H4-1 mudar fórmula ou ranking, o relatório muda
  junto — por isso o cálculo reusa as funções dela em vez de reimplementar a
  comparação.
- **Fuso horário:** as datas derivam do fuso do sistema; mudar o fuso do
  aparelho pode mover uma sessão de dia (mesmo comportamento do calendário da
  H3-28).
- **Streak "injusto":** dias de descanso planejado quebram a sequência. É
  intencional nesta versão (consistência = treinar todo dia); considerar os
  dias de descanso do plano fica para uma evolução futura.
- **Custo de UI:** o relatório anual agrega a lista inteira de sessões; usar
  `remember(sessions, period, reference)` evita recálculo a cada recomposição e
  o volume local é pequeno.
- **Rollback:** remover `PeriodReport.kt`, os dois cards e as strings; nenhum
  dado é criado ou apagado, sem migration nem restauração de backup.
