# H6-5 — Telas de informação: medidas, gráficos, rascunhos e pulso

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-5` (Horizon 6 — Polimento UX/UI; ver [auditoria](../../auditoria-ux-ui-2026-11-18.md), P2 + P3.6) |
| MoSCoW | `S` |
| Versão alvo | `1.11.0` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-18) |
| Implementação | `pendente` |
| Depende de | Nada (coordenar Generate com H6-3 se strings mudarem; scroll do pulso independente do H6-2) |

## Objetivo

As telas de informação respondem na ordem certa (tendência antes do
formulário), os gráficos falam por si (último valor rotulado), nada se perde
na rotação (Medidas, Geração, Histórico) e a tela de força do pulso cabe na
tela — inclusive redonda e com fonte grande.

## Fora de escopo

- Mudar o que os gráficos **calculam** (só apresentação; `ProgressCharts.kt`
  puro intocado).
- Editar plano/programas (fora de polimento).
- Dependência nova de gráfico (continua `Canvas` próprio).
- DatePicker do registro avulso (canonicamente H6-4).
- Comportamento do descanso/timer no pulso (H6-2).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/MeasurementsScreen.kt` | Ordem: `MeasurementTrendCard` + delta + último peso no topo; `MeasurementFormCard` depois (`:123-136` hoje form é o item 1); campos do formulário em `rememberSaveable` (ou Saver único do rascunho, padrão `ActivityLogDraftSaver`) — hoje só `measuredDate` sobrevive (`:206-217,88-91`); linha de medida ganha "Editar" (reabre o formulário com prefill, mesma validação da criação, `updateMeasurement` no repositório); foto clicável abre visualizador em tela cheia (`:560-570`, thumbnail 72 dp hoje inerte) e, com ≥2 fotos, ação "Comparar" (duas fotos lado a lado por data) |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `updateMeasurement(id, input)` (edita em `body_measurements` preservando `measuredAt`/origem; sem duplicar) |
| `app/src/main/java/com/treinos/app/ui/components/ProgressChart.kt` | `ProgressLineChart`: rótulo do último ponto (valor + data abreviada), 2–3 gridlines de Y com valores, indicador "mostrando últimos 30" quando `points.size > MAX_DRAWN_POINTS` (`:26`, `takeLast` em `:75`); resumo de a11y (`chart_line_a11y`) mencionando o truncamento quando houver |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Campos do formulário em `rememberSaveable` (planKind, goal, dias, nível, equipamento, músculos, duração, `showMoreOptions`, `pendingProgram` — `:183-234,222,225` hoje em `remember`); rascunho persistido em `AppPreferences` a cada mudança (o `generateConfigJson` da H3-30 já persiste no submit — o draft antecipa isso) |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | `month`, `modalityFilter`, `selectedDate`, `expanded` em `rememberSaveable` com Savers (`YearMonth` → epochMonth; `LocalDate` → epochDay) (`:146-148,903` hoje em `remember`) |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Ordem dos itens: herói "Hoje" logo após o InProgressCard; streak e atalho de medidas depois do herói (`:211-257` vs `:309-313`); `StreakCard` clicável → abre Histórico no mês corrente (`:1106-1137`, hoje inerte) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `openHistoryAt(month)` (ou reuso do `backTarget`) para o drill-down do streak; `updateMeasurement` exposto |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Telas Strength (conectada `:464-600` e standalone `:737-921`) com `verticalScroll(rememberScrollState())` (ou `ScalingLazyColumn`); margem vertical extra em tela redonda (heurística `:310-312` hoje só aumenta padding horizontal) |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | `chart_truncated_format`, `measurement_edit`, `measurement_compare_photos`, `streak_open_history` — paridade obrigatória |

## Modelo de dados / contrato

- Room: `updateMeasurement` é UPDATE por id — nenhuma migration; backup/sync
  intocados (a medida editada segue o fluxo de sync existente).
- Fotos: o visualizador lê **apenas** `filesDir/progress-photos/` (H4-8);
  nada sai do aparelho, nada novo nas backup rules.
- Gráfico: nenhum dado novo — mesmo `ExerciseProgressSeries`/`MuscleBar`.

## Regras e casos de borda

1. **Ordem das Medidas**: com dados, a dobra inicial mostra tendência + delta
   + último peso; sem dados, `EmptyState` com CTA "Registrar medida" antes do
   formulário (o formulário continua acessível sempre).
2. **Editar medida**: prefill dos campos atuais; salvar reusa a validação da
   criação (`MeasurementValidator`); `measuredAt` não muda (editar não
   reordena a série temporal); foto pode ser trocada/removida como na criação.
3. **Rotação**: todos os campos de formulário (Medidas, Geração) e os 4
   estados do Histórico sobrevivem; o rascunho de Geração persistido sobrevive
   também a processo morto (mesmo padrão do `ActivityLogDraft` da H3-56).
4. **Gráfico**: gridlines em valores "redondos" derivados do intervalo
   (min/max dos pontos); rótulo do último ponto nunca sobrepõe a linha
   (deslocado acima/abaixo conforme espaço); truncamento avisado **apenas**
   quando `points.size > MAX_DRAWN_POINTS`.
5. **Streak**: clique abre o Histórico no mês corrente (não no mês do best);
   o card continua mostrando atual/melhor sem clique longo.
6. **Home**: nenhuma função sai — streak e medidas continuam na Home, só
   depois do herói; com geração pendente ativa, a ordem resumo → pendente →
   herói se mantém.
7. **Pulso**: scroll em Strength não muda o comportamento dos botões
   (debounce, editor, descanso); em redonda, margem vertical extra para o
   conteúdo não colar na borda; fonte 2x continua navegável.
8. Nenhuma função removida ou escondida atrás de mais toques.

## Critérios de aceite

1. **Dado** medidas registradas, **quando** abro a tela, **então** tendência,
   delta e último peso estão na primeira dobra; o formulário vem depois.
2. **Dado** uma medida registrada, **quando** toco "Editar", **então** o
   formulário abre preenchido, salvar atualiza a linha existente (sem
   duplicar) e a tendência recalcula.
3. **Dado** duas fotos de progresso, **quando** toco numa foto, **então** ela
   abre em tela cheia; com ≥2, "Comparar" mostra as duas por data.
4. **Dado** o formulário de medidas preenchido pela metade, **quando** giro o
   aparelho, **então** os campos continuam preenchidos.
5. **Dado** um gráfico de exercício com 31+ pontos no mês, **quando** abro,
   **então** o último ponto tem valor+data rotulados, há gridlines de Y e o
   aviso "mostrando últimos 30" aparece.
6. **Dado** o formulário de geração preenchido, **quando** giro o aparelho,
   **então** nada se perde; matando o app e reabrindo, o rascunho volta.
7. **Dado** o Histórico no mês passado com filtro de corrida, **quando** giro,
   **então** mês, filtro e dia selecionado permanecem.
8. **Dado** a Home com streak e histórico, **quando** abro, **então** o herói
   "Hoje" está logo após o treino em andamento; tocar no streak abre o
   Histórico no mês corrente.
9. **Dado** a tela de força do pulso em modo redondo com fonte 2x,
   **quando** rolo, **então** todos os elementos (inclusive "Finalizar
   Treino") são alcançáveis.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/MeasurementsScreenTest.kt` (novo, Robolectric) | `tendencia_antes_do_formulario`, `editar_medida_prefill_e_update_sem_duplicar`, `campos_sobrevivem_rotacao`, `foto_abre_visualizador` |
| `app/src/test/java/com/treinos/app/ui/ProgressChartTest.kt` | `ultimo_ponto_rotulado`, `gridlines_derivadas_do_intervalo`, `truncamento_avisado_acima_de_30` |
| `app/src/test/java/com/treinos/app/ui/GenerateScreenTest.kt` | `formulario_sobrevive_rotacao`, `rascunho_persistido_por_mudanca` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` | `mes_filtro_e_dia_sobrevivem_rotacao` |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | `heroi_antes_do_streak_e_medidas`, `streak_clicavel_abre_historico_mes_corrente` |
| `app/src/test/java/com/treinos/app/data/MeasurementRepositoryTest.kt` (ou o existente de medidas) | `update_preserva_measured_at_e_origem` |
| `wear/src/test/java/com/treinos/wear/ui/` | `tela_strength_rola_ate_finalizar` (Robolectric do `:wear`, infra do 1.5.3) |

## Verificação manual

1. Medidas: ordem nova, editar uma medida, abrir foto, comparar 2 fotos.
2. Rotação em Medidas/Geração/Histórico com campos e filtros ativos.
3. Gráfico com mês cheio (31 dias): rótulo, gridlines e aviso de truncamento.
4. Home: ordem nova com geração pendente ativa; tocar no streak.
5. Relógio redondo com fonte 2x: rolar a tela de força até o Finalizar.
6. Fonte 2x no celular: Medidas e Geração sem corte.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Edição de medida e dedupe**: `measuredAt` preservado evita reordenar a
  série temporal; sync segue o fluxo existente (UPDATE por id) — testado.
- **Ordem da Home** muda hábito: todas as funções continuam na Home; rollback
  = reverter a ordem dos `item{}`.
- **Draft de Geração persistido** pode restaurar estado indesejado depois de
  uma geração concluída — limpar o draft no submit bem-sucedido (mesmo
  comportamento do `ActivityLogDraft`).
- **Scroll no pulso** pode conflitar com o gesto de swipe do sistema — usar
  `verticalScroll` padrão Compose (comportamento já aceito no celular).
- Rollback por arquivo; nenhuma migration.
