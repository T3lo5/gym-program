# H4-3 — Histórico por exercício (drill-down)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-3` |
| MoSCoW | `M` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` |
| Implementação | `concluída em 2026-11-15` |
| Depende de | H4-1 (PR/1RM), H4-2 (gráficos), H3-54 (edição de séries no Histórico) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 1). O
Histórico agrega por período; não há como responder "como este exercício
evoluiu?" sem percorrer os cards de sessão um a um.

**Implementação (2026-11-15):** gate verde. Notas do que ficou diferente do
texto original: (1) a ação secundária no Histórico é um `TextButton` "Ver
histórico" no fim de cada linha de exercício do card expandido, com
`contentDescription` "Ver histórico do exercício" — sem ícone novo, porque o
app usa `material-icons-core`, que não tem ícone de histórico; (2) o
fechamento do `ExerciseDetailSheet` ao abrir o histórico acontece dentro do
`openExerciseHistory` no ViewModel (regra 16), cobrindo os dois pontos de
entrada sem lógica extra no `TreinosApp`; (3) o candidato a recorde
progressivo de cada sessão é a melhor série da regra 4 (maior carga, empate
por reps), comparado pela regra de `isPersonalRecord` da H4-1, e o
`allTimeRecord` sai direto de `personalRecord` da H4-1 sobre as sessões
concluídas de força — empate de 1RM não é PR novo, igual ao detalhe; (4) a
lista da folha usa `Column` com scroll (padrão de `ExerciseDetailSheet` e
`SessionSetsEditSheet`) em vez de `LazyColumn` — o histórico típico é curto,
a agregação é derivada no ViewModel e o gráfico já limita a janela desenhada
a 30 pontos.

**Relacionado:** [H4-1](H4-1-recordes-pessoais-1rm.md) fornece
`PersonalRecord`; [H4-2](H4-2-graficos-progresso.md) fornece
`ExerciseProgressSeries` e os composables `ProgressLineChart`/`ProgressBarChart`.
Esta spec **não** reimplementa PR nem gráfico: apenas os reúne por exercício.

## Objetivo

A partir do Histórico ou do detalhe de um exercício, o usuário abre uma visão
focada naquele exercício com todas as sessões, a melhor série, o recorde
pessoal e a tendência, sem sair do app e sem perder o filtro/contexto do
Histórico.

## Fora de escopo

- Editar ou excluir séries dentro desta visão: a edição continua na folha
  "Editar séries" da H3-54, no card da sessão.
- Criar/excluir sessões, aplicar progressão (H4-12) ou alterar o plano.
- Comparar dois exercícios, exportar, compartilhar ou imprimir.
- Novos gráficos além do de linha já definido na H4-2; sem pizza/rosca.
- Corrida, natação e mobilidade: a visão é de musculação (reps/carga).
- Backend, conta ou sincronização.
- Nova tela no `Screen`/pilha de navegação: a visão é uma folha
  (`ModalBottomSheet`), como `ExerciseDetailSheet` e `SessionSetsEditSheet`.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/history/ExerciseHistory.kt` | **Novo.** Agregação pura por exercício: resumo por sessão, recorde e série de pontos. |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseHistorySheet.kt` | **Novo.** `ModalBottomSheet` com recorde, gráfico e lista de sessões. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `_exerciseHistoryId`, `exerciseHistoryId`, `openExerciseHistory(exerciseId)`, `closeExerciseHistory()` e `exerciseHistory: StateFlow<ExerciseHistory?>` derivado de `sessions`. |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Hospedar `ExerciseHistorySheet` quando `exerciseHistoryId != null`; fechar o `ExerciseDetailSheet` ao abrir o histórico. |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Ação secundária "Ver histórico do exercício" em cada linha de exercício do card expandido (novo callback `onOpenExerciseHistory`). |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseDetailSheet.kt` | Botão "Ver histórico completo" (novo callback `onOpenHistory`), visível quando houver exercício. |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | Títulos, rótulos, "sem sessões" e descrições de acessibilidade (paridade obrigatória). |
| `app/src/test/java/com/treinos/app/ui/history/ExerciseHistoryTest.kt` | **Novo.** Testes JVM da agregação. |
| `app/src/test/java/com/treinos/app/ui/ExerciseHistorySheetTest.kt` (Robolectric) | **Novo.** Conteúdo da folha, PR e estado vazio. |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` (Robolectric) | Ação de abrir o histórico a partir do exercício. |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Sem mudança de banco, rede ou preferências. Derivado de
`List<SessionWithExercises>`:

```kotlin
data class ExerciseSessionSummary(
    val sessionId: Long,
    val startedAtMillis: Long,
    val bestSetWeightKg: Double?,
    val bestSetReps: Int?,
    val volumeKg: Double,
    val sets: Int,
    val estimated1RmKg: Double?,
    val isPersonalRecord: Boolean,   // recorde naquele ponto da linha do tempo
)

data class ExerciseHistory(
    val exerciseId: String,
    val exerciseName: String,
    val sessions: List<ExerciseSessionSummary>, // mais recente primeiro
    val allTimeRecord: PersonalRecord?,          // H4-1
    val series: ExerciseProgressSeries,          // H4-2, métrica BEST_SET_KG
)

fun buildExerciseHistory(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
    exerciseName: String = "",
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): ExerciseHistory
```

O contrato de PR (`PersonalRecord`, `isPersonalRecord`) e o de série
(`ExerciseProgressSeries`, `ProgressPoint`) vêm de H4-1/H4-2 e **não** são
redefinidos aqui. `exerciseName` vazio é preenchido pelo primeiro
`SessionExerciseEntity.exerciseName` não vazio do próprio histórico; sem nome,
cai para o `exerciseId`.

## Regras e casos de borda

1. Só entram sessões `status == "completed"` e `SessionKind.STRENGTH`; sessões
   `in_progress` ficam de fora (a sessão atual aparece na tela de treino).
2. Só séries `isCompleted = true` e `isWarmup = false`; `reps` em `1..100`.
3. `sessions` (resumo) vem ordenado por `startedAt` **decrescente** — o mais
   recente primeiro, como o usuário lê histórico.
4. `bestSetWeightKg`/`bestSetReps`: maior carga finita `> 0`; empate por maior
   reps; sem carga, a série com mais reps. `estimated1RmKg` usa a fórmula
   escolhida (Epley por padrão).
5. `volumeKg` = `Σ (reps × weightKg)` das séries válidas; `sets` = quantidade
   de séries válidas.
6. `isPersonalRecord` é um **recorde progressivo**: percorrendo as sessões em
   ordem cronológica, a sessão é PR quando sua melhor série supera o melhor de
   todas as anteriores (regra de `isPersonalRecord` da H4-1). A primeira sessão
   válida é PR. Sessões posteriores que não superam não são PR.
7. `allTimeRecord` = `personalRecord(sessions, exerciseId)` (H4-1); `null` sem
   histórico válido.
8. `series` = `exerciseProgressSeries(sessions, exerciseId, BEST_SET_KG)`
   (H4-2), com os mesmos pontos do gráfico.
9. Exercício sem nenhuma sessão concluída → `sessions` vazio, `allTimeRecord`
   `null`; a folha mostra estado vazio e não desenha gráfico.
10. Exercício com uma única sessão → lista com 1 item e o aviso de "poucos
    dados" do gráfico (H4-2), sem quebrar.
11. Sessão sem séries válidas do exercício não entra no resumo (não gera linha
    com zeros).
12. Sessões com o mesmo `startedAt` mantêm ordem estável por `sessionId` desc.
13. Abrir/fechar a folha não grava nada; modo avião não altera o resultado.
14. A folha é rolável e não cobre a barra de navegação; segue o padrão de
    `ModalBottomSheet` já usado no app (`skipPartiallyExpanded = true`).
15. **Acessibilidade:** a folha tem título com o nome do exercício; cada linha
    de sessão tem descrição única (data + melhor série + volume); o gráfico
    reusa a descrição da H4-2.
16. Abrir o histórico a partir do `ExerciseDetailSheet` fecha o detalhe antes
    (não empilhar duas folhas).

## Critérios de aceite

1. **Dado** um exercício com três sessões concluídas, **quando** o usuário abre
   "Ver histórico do exercício" no Histórico, **então** a folha lista as três
   sessões da mais recente para a mais antiga, com melhor série e volume.
2. **Dado** o mesmo exercício, **então** o topo da folha mostra o "Melhor de
   todos os tempos" (carga × reps e 1RM estimado) da H4-1.
3. **Dado** que a segunda sessão superou a primeira, **então** ela aparece com
   o selo de recorde; a terceira, se não superou, não aparece.
4. **Dado** o gráfico de evolução, **então** ele mostra os pontos das sessões em
   ordem cronológica (H4-2), com o aviso quando houver menos de dois pontos.
5. **Dado** o `ExerciseDetailSheet` aberto, **quando** o usuário toca em "Ver
   histórico completo", **então** o detalhe fecha e a folha de histórico abre
   para o mesmo exercício.
6. **Dado** um exercício nunca treinado, **então** a folha mostra estado vazio
   ("Nenhuma sessão registrada") e nenhum gráfico.
7. **Dado** uma sessão em andamento com o exercício, **então** ela não aparece
   na lista nem no recorde.
8. **Dado** séries de aquecimento, **então** elas não entram na melhor série,
   no volume nem no PR.
9. **Dado** TalkBack ativo, **quando** a folha abre, **então** o título e as
   linhas de sessão são anunciados com data e melhor série.
10. **Dado** modo avião, **quando** a folha abre, **então** tudo funciona sem
    rede.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/history/ExerciseHistoryTest.kt` | `lista_sessoes_mais_recente_primeiro`, `ignora_sessao_em_andamento`, `ignora_corrida_e_mobilidade`, `ignora_warmup_e_series_invalidas`, `melhor_serie_por_carga_desempata_reps`, `volume_soma_reps_vezes_carga`, `recorde_progressivo_marca_apenas_quem_superou`, `primeira_sessao_valida_e_recorde`, `all_time_record_usa_h4_1`, `serie_de_pontos_igual_ao_grafico`, `exercicio_sem_sessoes_retorna_vazio`, `uma_sessao_nao_quebra_grafico`, `sessao_sem_series_validas_nao_entra`, `nome_vazio_cai_para_exercise_id`, `empate_de_data_ordena_por_id` |
| `app/src/test/java/com/treinos/app/ui/ExerciseHistorySheetTest.kt` (Robolectric) | `folha_mostra_melhor_de_todos_os_tempos`, `folha_lista_sessoes_com_recorde`, `folha_sem_sessoes_mostra_estado_vazio`, `folha_mostra_grafico_com_dois_pontos` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` (Robolectric) | `acao_abre_historico_do_exercicio` |

## Verificação manual

1. Registre três treinos do mesmo exercício, aumentando a carga em um deles.
2. No Histórico, expanda uma sessão e toque em "Ver histórico do exercício".
3. Confira a lista da mais recente para a mais antiga, com melhor série,
   volume e o selo de recorde na sessão que superou.
4. Confira o topo "Melhor de todos os tempos" e o gráfico de evolução.
5. Abra o detalhe do exercício (imagem/instruções) e use "Ver histórico
   completo": confirme que o detalhe fecha e a folha abre no mesmo exercício.
6. Abra o histórico de um exercício nunca treinado e confira o estado vazio.
7. Ative o TalkBack e percorra a folha: confira título, sessões e gráfico.
8. Ative o modo avião e repita os passos 2–6.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Duas folhas empilhadas:** abrir o histórico a partir do detalhe sem fechar o
  detalhe pode deixar duas `ModalBottomSheet` sobrepostas; a regra 16 e o
  fechamento no `TreinosApp` evitam isso.
- **Performance:** o drill-down agrega em memória as sessões já carregadas; para
  históricos longos, limitar a lista renderizada (ex.: `LazyColumn`) e não
  recalcular por frame (derivar no ViewModel, não dentro do composable).
- **Duplicação de PR/gráfico:** reusar `PersonalRecords.kt` (H4-1) e
  `ProgressCharts.kt` (H4-2); não copiar fórmulas. Se H4-1/H4-2 mudarem, o
  drill-down acompanha pelo contrato.
- **Escopo de edição:** manter a edição de séries na H3-54; adicionar edição
  aqui duplicaria validações e o fluxo do relógio.
- **Rollback:** remover `ExerciseHistory.kt`, `ExerciseHistorySheet.kt`, o
  estado no ViewModel e os dois pontos de entrada; nenhum dado é criado ou
  apagado.
