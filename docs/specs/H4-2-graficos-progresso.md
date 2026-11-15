# H4-2 — Gráficos de progresso (exercício e volume por músculo)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-2` |
| MoSCoW | `M` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |
| Depende de | H3-5 (melhor série por sessão), H3-28 (Histórico), H3-3/H3-34 (acessibilidade) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 1). O app
tem `HistoryMetrics` (agregação) e `ProgressionEngine` (melhor série por
sessão), mas nenhuma tendência visível; não há um único `Canvas` no código.

**Relacionado:** [H4-1](H4-1-recordes-pessoais-1rm.md) fornece o 1RM estimado
(usado como métrica opcional); [H4-3](H4-3-historico-por-exercicio.md) reusa os
componentes de gráfico no drill-down.

## Objetivo

O usuário enxerga, no Histórico, a evolução de um exercício ao longo das
sessões (carga ou volume) e a distribuição de volume/séries por músculo na
semana, sem instalar nada, sem rede e sem dependência nova.

## Fora de escopo

- Biblioteca de gráficos (MPAndroidChart, Vico etc.): desenho em Compose
  `Canvas`, sem dependência nova.
- Zoom, pan, tooltip interativo, exportar imagem ou animação de entrada.
- Gráficos de corrida/mobilidade (distância, pace, FC) — só musculação.
- Drill-down por exercício e lista de sessões (H4-3).
- Períodos arbitrários ou comparativo entre meses; o recorte é o já existente
  (mês selecionado) e a semana corrente.
- Alterar `HistoryMetrics`, o motor de progressão ou o schema Room.
- Backend, conta, sincronização ou telemetria.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/history/ProgressCharts.kt` | **Novo.** Agregações puras: série por exercício, barras por músculo e lista de exercícios com histórico. |
| `app/src/main/java/com/treinos/app/ui/history/HistoryMetrics.kt` | Tornar `muscleKeysFor` `internal` (hoje privado) para as barras usarem a mesma regra de divisão de volume entre músculos. Sem mudança de comportamento de `buildHistoryMetrics`. |
| `app/src/main/java/com/treinos/app/ui/components/ProgressChart.kt` | **Novo.** Composables `ProgressLineChart` e `ProgressBarChart` em `Canvas`, com semântica de acessibilidade. |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Seção "Evolução do exercício" (seletor + linha) e "Volume por músculo na semana" (barras), no lugar/ao lado do ranking atual. |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | Títulos, rótulos de métrica, estados vazios e descrições de acessibilidade (paridade obrigatória). |
| `app/src/test/java/com/treinos/app/ui/history/ProgressChartsTest.kt` | **Novo.** Testes JVM das agregações. |
| `app/src/test/java/com/treinos/app/ui/ProgressChartTest.kt` (Robolectric) | **Novo.** Semântica e estado vazio dos gráficos. |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` (Robolectric) | Seções de gráfico presentes e seletor de exercício. |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Sem mudança de banco, rede ou preferências. Tudo derivado de
`List<SessionWithExercises>` + `muscleByExerciseId` (já passado à
`HistoryScreen`):

```kotlin
enum class ExerciseChartMetric { BEST_SET_KG, VOLUME_KG }

data class ProgressPoint(
    val sessionId: Long,
    val startedAtMillis: Long,
    val label: String,      // data curta do treino, ex.: "10/11"
    val value: Double,
)

data class ExerciseProgressSeries(
    val exerciseId: String,
    val metric: ExerciseChartMetric,
    val points: List<ProgressPoint>,   // ordem cronológica crescente
)

data class MuscleBar(
    val muscle: String,     // rótulo já traduzido por muscleLabel
    val volumeKg: Double,
    val sets: Int,
)

data class ExerciseOption(
    val exerciseId: String,
    val name: String,
    val sessionCount: Int,
)

fun exerciseProgressSeries(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
    metric: ExerciseChartMetric,
    from: LocalDate? = null,
    to: LocalDate? = null,
): ExerciseProgressSeries

fun muscleVolumeBars(
    sessions: List<SessionWithExercises>,
    muscleByExerciseId: Map<String, String>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): List<MuscleBar>

fun exercisesWithHistory(
    sessions: List<SessionWithExercises>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): List<ExerciseOption>
```

Composables (contrato de UI):

```kotlin
@Composable
fun ProgressLineChart(
    points: List<ProgressPoint>,
    valueLabel: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
)

@Composable
fun ProgressBarChart(
    bars: List<MuscleBar>,
    valueLabel: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
)
```

`valueLabel` é resolvido na composição (ex.: `formatKg`/`formatVolumeKg`);
os gráficos não formatam nem traduzem por conta própria.

## Regras e casos de borda

1. Só sessões `status == "completed"` e `SessionKind.STRENGTH` entram nas duas
   agregações; corrida e mobilidade são ignoradas.
2. Só séries `isCompleted = true` e `isWarmup = false` contam.
3. `BEST_SET_KG`: valor do ponto = maior `weightKg` finito e `> 0` entre as
   séries válidas da sessão para o exercício; séries sem carga não geram ponto.
4. `VOLUME_KG`: valor do ponto = `Σ (reps × weightKg)` das séries válidas com
   `reps != null` e `weightKg` finito; séries sem carga somam `0`.
5. Um ponto por sessão concluída que contenha o exercício, ordenados por
   `startedAt` crescente; sessões sem o exercício não geram ponto.
6. `points.size < 2` → a UI mostra "Poucos dados para um gráfico" e **não**
   desenha a linha (um ponto isolado não é tendência).
7. `exercisesWithHistory` lista os exercícios presentes no período, com
   `sessionCount`; ordena por `sessionCount` desc, depois `name` asc
   (case-insensitive), depois `exerciseId`. Nome vazio cai para o `exerciseId`.
8. `muscleVolumeBars`: o volume de cada série é dividido igualmente entre os
   músculos do exercício (mesma regra de `buildHistoryMetrics`); exercício sem
   músculo conhecido cai em `OTHERS_MUSCLE_LABEL` ("Outros"). `sets` conta a
   série inteira em **cada** músculo (não divide), para o usuário ver o esforço
   por grupo.
9. Barras ordenadas por `volumeKg` desc; empate por `muscle` asc. Barras com
   `volumeKg == 0` e `sets == 0` são omitidas.
10. Período (`from`/`to`) é inclusivo e opcional; `null` significa sem limite.
    A linha usa o mês selecionado no Histórico; as barras usam a semana
    corrente (segunda a domingo de `today`).
11. Lista vazia, período sem dados ou exercício ausente → listas vazias, sem
    exceção.
12. Valores não finitos (`NaN`, `Infinity`) são descartados; nunca entram no
    desenho.
13. Escala do gráfico: eixo Y começa em `0` e vai até o maior valor (mínimo
    visual de `1.0` para evitar divisão por zero); eixo X distribui os pontos
    uniformemente quando há menos de 2.
14. **Acessibilidade:** cada gráfico tem `contentDescription` com um resumo
    textual (primeiro valor, último valor, quantidade de pontos/período), e as
    barras têm descrição por músculo; o gráfico é decoração reforçada, nunca o
    único canal — os números continuam disponíveis no ranking textual.
15. Nada é calculado ao abrir a tela além do que já é derivado de `sessions`;
    modo avião não altera o resultado.

## Critérios de aceite

1. **Dado** três sessões concluídas com o mesmo exercício (40, 42,5 e 45 kg de
   melhor série), **quando** o Histórico abre com esse exercício selecionado,
   **então** a linha mostra 3 pontos em ordem cronológica com os valores 40,
   42,5 e 45.
2. **Dado** uma sessão `in_progress` com o exercício, **então** ela não aparece
   no gráfico.
3. **Dado** uma série de aquecimento com carga maior, **então** ela não entra no
   melhor da sessão nem no volume.
4. **Dado** a métrica "Volume", **então** o ponto é a soma de `reps × carga` das
   séries válidas da sessão.
5. **Dado** um exercício com apenas uma sessão no período, **então** aparece
   "Poucos dados para um gráfico" e nenhuma linha é desenhada.
6. **Dado** a semana corrente com volume de Peito e Costas, **então** as barras
   aparecem ordenadas por volume decrescente, com o rótulo do músculo.
7. **Dado** um exercício sem músculo no mapa, **então** o volume dele aparece na
   barra "Outros".
8. **Dado** a semana sem treinos, **então** o gráfico de barras mostra estado
   vazio, sem crash.
9. **Dado** TalkBack ativo, **quando** o foco chega ao gráfico, **então** é lida
   uma descrição com o resumo dos dados.
10. **Dado** modo avião, **quando** o Histórico abre, **então** os gráficos
    renderizam normalmente.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/history/ProgressChartsTest.kt` | `serie_por_exercicio_ordena_cronologico`, `ignora_sessao_em_andamento`, `ignora_corrida_e_mobilidade`, `ignora_warmup`, `melhor_serie_pega_maior_carga`, `volume_soma_reps_vezes_carga`, `serie_sem_carga_fora_do_melhor`, `menos_de_dois_pontos_nao_desenha`, `exercicios_com_historico_ordena_por_contagem`, `barras_por_musculo_ordenadas`, `volume_dividido_entre_musculos`, `sets_contados_por_musculo`, `exercicio_sem_musculo_vai_para_outros`, `periodo_inclusivo`, `valores_nao_finitos_descartados`, `listas_vazias_sem_excecao` |
| `app/src/test/java/com/treinos/app/ui/ProgressChartTest.kt` (Robolectric) | `linha_tem_descricao_de_acessibilidade`, `barras_tem_descricao_por_musculo`, `grafico_vazio_mostra_estado` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` (Robolectric) | `historico_mostra_seletor_de_exercicio`, `historico_mostra_barras_por_musculo` |

## Verificação manual

1. Registre três treinos do mesmo exercício com cargas diferentes.
2. Abra o Histórico, selecione o exercício e confira a linha subindo na ordem
   das sessões.
3. Alterne a métrica para "Volume" e confira que o ponto muda para a soma das
   séries.
4. Confira as barras "Volume por músculo na semana" com os grupos treinados.
5. Selecione um exercício com um único registro e confira o aviso de poucos
   dados (sem linha).
6. Navegue para um mês sem treinos e confira o estado vazio.
7. Ative o TalkBack e percorra os gráficos: confira o resumo lido.
8. Ative o modo avião e repita os passos 2–6.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Dependência nova:** nenhuma. O desenho usa `androidx.compose.foundation`
  (`Canvas`, `drawLine`, `drawRect`, `drawPath`) e `Path`, já presentes pelo
  Compose; não há artefato/versão a adicionar. Se algum dia for preciso texto no
  canvas, usar `rememberTextMeasurer` do próprio Compose.
- **Performance:** a agregação roda em memória sobre as sessões já carregadas
  pelo Room; limitar o número de pontos desenhados (ex.: últimos 30) evita
  custo por frame. Sem I/O novo.
- **Leitura errada do gráfico:** eixo Y sempre começa em zero e o valor exato
  continua nos cards e no ranking textual; o gráfico é apoio, não a única
  fonte.
- **Acessibilidade:** gráfico sem descrição é inacessível; a regra 14 e o teste
  de semântica são obrigatórios. Não usar `clearAndSetSemantics` que apague o
  resumo.
- **Rollback:** remover `ProgressCharts.kt`, `ProgressChart.kt` e as seções da
  `HistoryScreen`; `muscleKeysFor` volta a ser privado. Nenhum dado é criado ou
  apagado.
