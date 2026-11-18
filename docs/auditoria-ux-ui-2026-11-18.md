# Auditoria UX/UI — Treinos (celular + relógio)

**Data:** 2026-11-18 · **Método:** 4 auditorias paralelas somente-leitura (loop de execução, navegação/telas de informação, design system, acessibilidade/estados/Wear), com verificação cruzada das alegações direto no código. Nenhuma linha alterada. **Referências de mercado:** taxonomia pública de features do Hevy (líder da categoria) + convenções de Strong/Strava/Apple Fitness, Material 3 e Wear OS design. A pesquisa web ao vivo ficou indisponível (saldo da API de busca); as convenções foram verificadas por fetch direto e domínio dos apps de referência.

**Contexto:** desde a auditoria de [2026-11-07](auditoria-ux-ui-2026-11-07.md) entraram e foram entregues H3-26..H3-59, H4-1..H4-18 e H5-1..H5-6 — o app cobre as features básicas do mercado. Esta rodada procura o que o **uso real e a onda H4 deixaram para trás na apresentação**, sob o princípio do ROADMAP §3.5 ("rápido de usar entre séries") e da regra do dono: **polir sem cortar função**.

---

## Síntese executiva

**O que está bom (e deve ser mantido):** a fundação aguentou a onda H4 — faixa de descanso fixa com wall-clock persistido, registro em 1 toque com pré-preenchimento em cascata, série editável na linha, troca de exercício sem sair da sessão, PR decidido antes de gravar, supersets com rótulo e descanso de transição, estados Loading/Empty/Error padronizados, gráficos nunca como único canal (resumo textual), tokens centralizados com zero cor hardcoded, motion com propósito, paridade PT/EN testada nas strings, alvos de toque consistentes.

**Os 4 grandes problemas desta rodada:**

1. **Funções entregues que estão mortas ou mudas na UI.** O seletor de idioma da H3-37 existe em código e nunca é chamado; o toggle "Ver sinais de alerta" da mobilidade não é clicável; o card de permissões só aparece em dia de treino (aninhamento acidental); o plano flexível renderiza um card-herói vazio; editar registro avulso devolve à Home em vez da origem; a restauração do standalone volta ao exercício 1 e perde o descanso (`restEndsAt` gravado nunca é lido); os intervalados da corrida (H4-17) chegam ao protocolo do pulso mas nunca são publicados nem renderizados; e o aquecimento contamina a "última série" (pré-preenche o diálogo e o "Repetir" grava warmup como série de trabalho).
2. **O loop de execução perdeu detalhes na onda H4.** O alvo de reps existe no plano, na Home e no relógio — mas não no card do treino. Superset não guia ao próximo membro. Descanso zerado não aceita "+15 s". Série extra com valores próprios fica inalcançável depois de cumprir o planejado. PR batido celebra pouco (selo pequeno + háptico). Pausar/estender o descanso no celular não chega ao relógio.
3. **A promessa bilíngue quebra numa camada inteira.** Com idioma English: TalkBack fala português (A11y.kt nasce em PT), a corrida GPS exibe "Rastreando corrida"/"Ritmo:" hardcoded, rótulos de músculo/equipamento, meses, dias da semana e mensagens de validação ficam em PT — e `appLocale` foi criado para isso e não é usado em lugar nenhum.
4. **Consistência visual com desvios concentrados.** 7 confirmações destrutivas manuais fora do `ConfirmationDialog` (justamente nos fluxos mais destrutivos), 3 padrões de estado vazio, cor de "em andamento" instável entre telas, 5 botões primários na mesma tela (Generate), pilha de 5 botões full-width no card de exercício, timer de descanso ilegível de longe (16 sp) enquanto o player de mobilidade usa `displayLarge` para o mesmo papel.

---

## P0 — Funções entregues que não funcionam na UI (bugs, vêm antes de qualquer polimento)

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 0.1 | **Toggle de segurança da mobilidade é morto**: o texto "Ver sinais de alerta" não tem `clickable` — `onToggleSafety` é recebido e nunca invocado; o conteúdo (dor, formigamento, trauma) é inalcançável para sempre. | `MobilityScreen.kt:198` (Text sem handler), `:99`, `:159`; único `clickable` do arquivo em `:354` | Tornar o Text acionável (`clickable` + `minimumInteractiveComponentSize` + role Button) | P |
| 0.2 | **Seletor de idioma morto**: o card "Idioma" promete o seletor (H3-37, critério 2) mas só renderiza título+descrição; `LanguageSelector`/`currentAppLanguage`/`applyAppLanguage` são código morto (zero chamadas). | `SettingsScreen.kt:1316-1332` vs `:1465-1506` | Renderizar `LanguageSelector` dentro do card; o código já existe | P |
| 0.3 | **Card de permissões só existe em dia de treino**: o bloco `if (!permissionsDismissed)` ficou aninhado dentro de `if (todaySession != null)` (indentação quebrada denuncia acidente); em dia de descanso o pedido nunca aparece na Home. | `HomeScreen.kt:312-348` (comentário em `:314-315` indica intenção contrária) | Dedentar o bloco para o nível da lista | P |
| 0.4 | **Plano flexível renderiza card-herói vazio**: `todaySessionSelector` devolve `Rest` e a Home chama `TodayHeroCard` mesmo assim (branch `Rest -> Unit`) — um `primaryContainer` vazio no topo; a spec H3-28 diz "com Rest a Home não mostra herói". | `HomeScreen.kt:312-313,666-667`; `WeeklySchedule.kt:46-48,68` | Guardar o item com `if (todaySession !is TodaySession.Rest)` | P |
| 0.5 | **Editar registro avulso volta para a Home**: `openLogActivity`/`editLogActivity` não definem `_backTarget` — `voltar()` e `saveActivity` caem em `?: Screen.Home`; o comentário H3-38 promete retorno à origem. | `AppViewModel.kt:2255-2277`, `:2317-2318`, `:1317-1322`; os 5 demais `open*` setam backTarget (`:1284-1311`) | Setar `_backTarget.value = _screen.value` nos dois `open*` + teste do par editar→voltar | P |
| 0.6 | **Standalone restaura no exercício 1 e perde o descanso**: `Start` sempre reconstrói `exerciseIndex = 0`, `restEndsAt = null`; o `restEndsAt` gravado no Room nunca é lido (só `UPDATE`), contrariando o doc do store e o critério 4 da H4-16. | `WearStandaloneSession.kt:82-94`; `WearStandaloneStore.kt:56-59` vs `:83-85`; `WorkoutWearActivity.kt:88-107` | Persistir/derivar `exerciseIndex`/`setNumber` (pendentes por exercício) e recalcular o descanso de `restEndsAt`; teste em `WearStandaloneSessionTest` | M |
| 0.7 | **Intervalados no pulso: protocolo carrega, ninguém publica**: `intervalPhase`/`intervalRemainingSeconds` existem no `:protocol` (H4-17), mas o `WearSessionMapper` não preenche e a tela Modality do relógio não renderiza — o corredor não vê "Tiro — 30s" no pulso. | `WearProtocol.kt:37-38`; `WearSessionMapper.kt:40+`; `WorkoutWearActivity.kt:620-637` | Publicar os campos no mapper e renderizar fase+contagem na tela de corrida do pulso | P |
| 0.8 | **Aquecimento contamina a "última série"**: registrar warmup dispara o descanso cheio e o warmup vira a última série — pré-preenche o diálogo e o "Repetir última série" grava a carga de aquecimento como série de trabalho `normal`. | `AppViewModel.kt:1880-1890` (sem ramificação por `setType`); `WorkoutScreen.kt:464,484-496,1239-1253` | Pré-preenchimento/repetir usam a última série de **trabalho**; warmup dispara descanso curto fixo (30–45 s) ou nenhum | M |

## P1 — Loop de execução entre séries

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 1.1 | **Alvo de reps invisível na execução**: `targetReps` existe na sessão, é exibido na Home e no relógio, mas o card mostra só "N séries planejadas • descanso de Xs". | `Entities.kt:166`; `HomeScreen.kt:983-985`; `WorkoutWearActivity.kt:489-491` vs `WorkoutScreen.kt:1172-1176` | Mostrar o alvo no cabeçalho do card (e no helper do diálogo) quando não vazio | P |
| 1.2 | **Descanso zerado não aceita mais tempo**: no estado "concluído" o RestBar esconde "+15 s"/"Pausar"; `extendRest` vira no-op (deadline já limpo ao zerar). "Não estou pronto" não tem resposta. | `WorkoutScreen.kt:984,1061-1080`; `AppViewModel.kt:2508,2559-2560` | Manter "+15 s" (e reiniciar) no estado concluído, reconstruindo o deadline a partir do total | P |
| 1.3 | **Pausar/estender no celular não chega ao relógio**: `pauseRest`/`resumeRest`/`extendRest` não republicam o estado — o pulso continua contando de onde parou. | `AppViewModel.kt:2528-2568` vs `:1062-1068,1891,2524`; `WearTimer.kt:48-59` | Republicar nas transições (incluir "pausado" na chave do relógio) | M |
| 1.4 | **Superset não leva ao próximo membro**: a série do A1 inicia o descanso de transição, mas nada rola nem aponta o A2 — o usuário caça o card seguinte na mão a cada transição. | `AppViewModel.kt:1888-1890`; `SupersetPlan.kt:60-70`; `WorkoutScreen.kt:415-426,1257-1263` | Rolar para o próximo membro (reusar `scrollTargetIndex`) e/ou "A seguir: A2" no RestBar | M |
| 1.5 | **Série extra com valores próprios fica inalcançável**: cumprido o planejado, o botão principal vira "Exercício concluído — próximo" e o único caminho para o diálogo é esse botão; drop set/AMRAP de fechamento com carga diferente não registra. | `WorkoutScreen.kt:1257-1283,390,1241-1253` | Manter o avanço e adicionar ação compacta "+ série" | P |
| 1.6 | **PR batido celebra pouco**: háptico + selo `labelMedium` dentro do card; o anúncio de "série salva/PR" é texto invisível de 0 dp. | `WorkoutScreen.kt:500-515,1177-1185,264-272` | Banner/snackbar efêmero "Novo recorde: 80 kg × 5" no salvamento | P |
| 1.7 | **Rotação desfaz estado da tela de treino**: `prSessionExercises` (selo de PR da H4-1) e os flags de confirmação em `remember` — girar apaga o selo e fecha confirmações destrutivas; o diálogo de série sobrevive (padrão H3-31 inconsistente). | `WorkoutScreen.kt:163,170-172,177-178` vs `:159-160,184-185` | `rememberSaveable` nos flags/ids; derivar o selo das séries vs `personalRecords` | P |
| 1.8 | **Anilhas e rampa escondidas a duas navegações**: o atalho só existe dentro do diálogo, colapsado; a linha da série gravada mostra só "60 kg" — a convenção pede a quebra visível na carga. | `SetEditDialog.kt:218-227`; `WorkoutScreen.kt:1365-1381` | Quebra por lado inline sob o campo de carga quando `barbell` (e na linha da série) | P |
| 1.9 | **Nenhum marcador do exercício corrente nem rolagem ao reabrir**: só atenua concluídos; retomar um treino na metade abre a lista no topo. | `WorkoutScreen.kt:1132-1135,209-212` | Borda/tonalidade no corrente + rolar até ele na entrada | P |
| 1.10 | **Troca de exercício: confirmar exige rolar até o fim**: busca, candidatos, escopo e botões no mesmo LazyColumn; "Confirmar" é o último item. | `WorkoutScreen.kt:916-964` | Fixar escopo + Cancelar/Confirmar em rodapé fora da lista | P |
| 1.11 | **Standalone: "Concluir série" sem debounce** — o modo conectado usa `WearDebounce` (2 s), o standalone grava direto a cada toque; toque duplo registra duas séries (e sincroniza as duas). | `WorkoutWearActivity.kt:871-882` vs `:287-293,563-578` | Reusar o debounce (janela menor, ex. 1 s) no standalone | P |
| 1.12 | **Fim do descanso no pulso é silencioso**: o timer zera sem háptico e o texto some; sem liveRegion no restante — o celular anuncia (RestBar 60/30/10/0), o relógio ficou atrás. | `WearTimer.kt:30`; `WorkoutWearActivity.kt:455-462,551-562,861-870` | Háptico + anúncio "Descanso encerrado" ao zerar nos dois modos; `liveRegion` nos marcos | P |

## P2 — Navegação e telas de informação

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 2.1 | **Medidas: formulário de 9 campos antes da tendência** — o card de entrada ocupa a primeira dobra; gráfico, delta e última leitura ficam abaixo. | `MeasurementsScreen.kt:123-136` | Reordenar: tendência + delta + último peso no topo; formulário depois | P |
| 2.2 | **Gráficos sem eixo, sem valores e sem último valor destacado** — só a linha; a janela de 30 pontos trunca em silêncio (mês com 31 dias de treino perde o ponto mais antigo sem aviso). | `ProgressChart.kt:74-103,26`; `HistoryScreen.kt:433`; `ExerciseHistorySheet.kt:131` | Rotular o último ponto (valor + data) e 2–3 gridlines de Y; indicar "mostrando últimos 30" quando cortar | M |
| 2.3 | **Gerar: o formulário inteiro morre na rotação** — todos os campos em `remember`; a config só persiste no submit (o padrão de rascunho persistido da H3-56 existe e não foi aplicado aqui). | `GenerateScreen.kt:183-234,222,225`; `AppViewModel.kt:1428,1481` | `rememberSaveable` + draft em `AppPreferences` a cada mudança | M |
| 2.4 | **Histórico: mês, filtro, dia selecionado e expansão somem na rotação** — no mesmo arquivo onde séries/diálogos já usam `rememberSaveable` (H3-54). | `HistoryScreen.kt:146-148,903` vs `:907-909` | `rememberSaveable` (Saver de `YearMonth`/epochDay) nos 4 estados | P |
| 2.5 | **Home: streak e atalho de medidas empurram o herói "Hoje"** para baixo — com card de geração pendente ativo, a ação primária do dia sai da primeira dobra. | `HomeScreen.kt:211-257` vs `:309-313` | Herói logo após o InProgressCard; streak e medidas depois | P |
| 2.6 | **Registro avulso: data em texto livre e distância em metros** — "dd/MM/yyyy HH:mm" digitado à mão (Medidas já usa DatePicker) e metros para uma corrida que o resto do app trata em km (H3-31). | `LogActivityScreen.kt:238-246,280-290`; `strings.xml:442`; `Formatters.kt:13` | DatePicker/TimePicker no "Quando"; aceitar km com conversão no save (corrida) | M |
| 2.7 | **Medidas: sem edição e foto sem tela cheia** — a linha só oferece excluir (corrigir = excluir e redigir tudo); a foto é thumbnail de 72 dp não clicável, sem comparar antes/depois. | `MeasurementsScreen.kt:524-590,560-570` | Editar com prefill; foto clicável → visualizador (+ "comparar 2 fotos" quando ≥2) | M |
| 2.8 | **Streak sem drill-down** — o card mostra sequência atual/melhor mas não é clicável nem aponta para o relatório Mês/Ano ou calendário. | `HomeScreen.kt:1106-1137`; relatório em `HistoryScreen.kt:308-319,516-574` | Card clicável → abre Histórico no mês corrente | P |

## P3 — Acessibilidade, i18n e estados

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 3.1 | **TalkBack não é localizado**: todas as descrições nascem em PT em `A11y.kt` ("Exercício", "Série N", "Descanso: X segundos restantes", "Revisão atrasada desde") e em ", série concluída" concatenado — com EN, todo o percurso TalkBack fala português. | `A11y.kt:12-32`; `WorkoutScreen.kt:1161,1204-1209`; consumo em `HomeScreen.kt:1152`, `HistoryScreen.kt:1152` | Mover templates para `strings.xml` (padrão H3-52); atualizar `A11yTest` | M |
| 3.2 | **Camada de formatação/rotulagem fora de recursos**: rótulos de músculo/equipamento ("Peito", "Halteres"), mês do navegador, dias da semana do calendário, "N sessões/semana", erros do gerador ("Peso inválido.") e contentDescription do fallback de imagem — `appLocale` existe e não é usado em lugar nenhum. | `Formatters.kt:18,53-106,164-167`; `CalendarMonth.kt:13,40-44`; `WeeklySchedule.kt:91-94`; `ProfileValidator.kt:43-82`; `ExerciseImage.kt:55,74`; `ExerciseDetailSheet.kt:115,124` | Mover para `strings.xml`/resolver com `appLocale`; apagar o morto `startsWith`-por-idioma | M |
| 3.3 | **Corrida GPS 100% PT fora de recursos** — "Rastreando corrida", "Ritmo:", "Aquecimento/Tiro/Recuperação", "Parar/Iniciar rastreamento"; validadores puros com mensagens PT que viram `supportingText`; roteamento de erro por `startsWith("Distância")` acoplado ao texto. | `RunWorkoutScreen.kt:254-263,362-398`; `RunLog.kt:35,43,48`; `ActivityLog.kt:116-204`; `ComplementaryValidator.kt:67` | Extrair para recursos com código de erro (enum→recurso) em vez de casar prefixo; cobrir no `StringParityTest` | M |
| 3.4 | **Wear i18n de formatação**: `formatarCarga` força vírgula e " kg" fora de recursos (EN mostra "20,0 kg"); `formatLastSet` idem; a corrida usa `"%.1f".format(...)` com locale do sistema — inconsistente. | `WorkoutWearActivity.kt:924-928,622-636`; `WearFormat.kt:17-21` | Formatar com `Locale`/`NumberFormat` e unidade via recurso; teste em `WearFormatTest` com EN | P |
| 3.5 | **Calendário do Histórico**: data com `Locale.forLanguageTag("pt-BR")` fixo ("5 de novembro" em EN) e "treino(s)" concatenado; dia sem treino é `clearAndSetSemantics` (invisível ao TalkBack) e "hoje" só se distingue por cor/negrito. | `HistoryScreen.kt:703-705,716-729,691-696` | Formatter com locale do app + `<plurals>`; semântica mínima no dia atual ("Hoje, sem treino") | P |
| 3.6 | **Tela de força do pulso sem scroll**: pilha de ~11 elementos excede a área útil; em tela redonda o "Finalizar Treino" sai da tela. Nenhum `verticalScroll`/`ScalingLazyColumn` em todo o módulo wear. | `WorkoutWearActivity.kt:464-600,737-921`; heurística `:310-312` | `verticalScroll` nas telas Strength (ou `ScalingLazyColumn`); margem vertical extra em redonda; testar com fonte 1.5x/2x | M |
| 3.7 | **Ajustes: Switch "backend remoto" sem rótulo próprio** — o Text é irmão, não mesclado; TalkBack anuncia só "ativado/desativado". | `SettingsScreen.kt:568-584` | `toggleable(role = Switch)` na Row com `stateDescription` | P |
| 3.8 | **Literais PT remanescentes**: Mobilidade ("Temporizador …", "Movimento X de Y", "esquerdo/direito" apesar de `mobility_side_*` existirem), Ajustes ("Espaço:", "Segredo:"), plural caseiro no herói ("s"/"(s)"). | `MobilityScreen.kt:274,320,325,235`; `SettingsScreen.kt:439,444`; `HomeScreen.kt:640` | Extrair para recursos/plurais; reusar `mobility_side_left/right` | M |

## P4 — Design system e consistência visual

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 4.1 | **7 confirmações destrutivas manuais fora do `ConfirmationDialog`** — excluir série/finalizar/abandonar (Workout), excluir plano/abandonar (Home), descartar corrida (Run), descartar rotina (Mobility) — enquanto 8 telas usam o componente. | `WorkoutScreen.kt:583-609,615-640,645-671`; `HomeScreen.kt:490-513,1068-1097`; `RunWorkoutScreen.kt:510-539`; `MobilityScreen.kt:125-151` | Migrar os 7 para `ConfirmationDialog` (`isDestructive=false` cobre o "finalizar") | P |
| 4.2 | **Timer de descanso ilegível de longe**: contagem em `titleMedium` (16 sp) dentro de uma frase; o player de mobilidade usa `displayLarge` para o mesmo papel; métricas de GPS da corrida em Text sem estilo. | `WorkoutScreen.kt:1034-1038` vs `MobilityScreen.kt:349-355,373-377`; `RunWorkoutScreen.kt:368-382` | Extrair o restante para `headlineLarge`/`displayMedium` com a frase de estado em label; alinhar as métricas da corrida | P |
| 4.3 | **Inflação de botão primário**: 4 ProgramCards com Button preenchido + CTA fixo = 5 primários na mesma tela; padrão se repete em cards de dia (Home) e rotinas (Mobilidade). | `GenerateScreen.kt:933-942` + `:330-389`; `HomeScreen.kt:646-651,1006-1011,1375-1380`; `MobilityScreen.kt:239-244` | Preenchido só no CTA principal; "Usar"/"Iniciar" em card vira `TonalButton` (ou card clicável com chevron) | M |
| 4.4 | **Pilha de ações secundárias no card de exercício**: até 5 botões full-width empilhados (Registrar, Repetir, Aplicar, Trocar, Superset) + nota + sugestão competindo com o primário. | `WorkoutScreen.kt:1257-1337` | Manter Registrar/Repetir visíveis; agrupar Trocar/Superset/Nota em linha compacta de TextButton ou menu ⋮ (sem remover função) | M |
| 4.5 | **Cor sem significado estável para "em andamento"**: Home usa `secondaryContainer`, Histórico usa `tertiary`; em Ajustes `tertiary` vira "versão desconhecida". | `HomeScreen.kt:1026-1028` vs `HistoryScreen.kt:751,953-954`; `SettingsScreen.kt:687,781-783` | Mapa estado→cor (em andamento = `tertiary` sempre, sucesso = `primary`) aplicado nos 4 pontos | P |
| 4.6 | **Três padrões de estado vazio**: componente `EmptyState`, card artesanal (Home/Medidas) e texto solto (dia sem registro). | `Common.kt:227-250` vs `HomeScreen.kt:260-281`, `MeasurementsScreen.kt:138-162`, `HistoryScreen.kt:278-293` | Reusar `EmptyState` nos três casos (com `action` onde há CTA) | P |
| 4.7 | **Espaçamentos fora do padrão em pontos isolados**: corrida sem `contentPadding` horizontal (conteúdo colado nas bordas); card "perfil salvo" com duplo recuo de 32 dp. | `RunWorkoutScreen.kt:282-288`; `GenerateScreen.kt:823` vs `:398` | `PaddingValues(16.dp)` na corrida; remover o padding horizontal duplicado em Generate | P |
| 4.8 | **Entrada de data inconsistente entre formulários gêmeos**: Registro avulso usa texto livre; Medidas usa `DatePicker` em campo somente-leitura. | `LogActivityScreen.kt:238-245` vs `MeasurementsScreen.kt:260-277,388-415` | Adotar o padrão de Medidas no Registro avulso, mantendo a validação inline | M |

## Quick wins (impacto alto, esforço P — ordenados por ganho/esforço)

1. **Seletor de idioma na UI** (0.2) — o código existe; compor e testar.
2. **Toggle de segurança clicável** (0.1) — segurança de saúde inalcançável hoje.
3. **Alvo de reps no card do treino** (1.1) — dado já existe, 1 linha de UI + helper do diálogo.
4. **Card de permissões fora do aninhamento** (0.3) e **herói vazio do plano flexível** (0.4) — guards de 1 linha.
5. **Back target do registro avulso** (0.5) — 2 linhas + teste.
6. **"+15 s" no descanso concluído** (1.2) — reconstruir deadline a partir do total.
7. **"+ série" após cumprir o planejado** (1.5) — ação compacta que abre o diálogo.
8. **`rememberSaveable` nos flags do Workout e nos 4 estados do Histórico** (1.7, 2.4).
9. **Intervalados publicados e renderizados no pulso** (0.7) — mapper + 1 bloco de UI.
10. **Streak clicável → Histórico** (2.8) e **reordenar Medidas** (2.1).
11. **Migrar os 7 diálogos para `ConfirmationDialog`** (4.1) — elimina variação de rótulo/cor/ordem.
12. **Mapa de cor de estado** (4.5) e **`EmptyState` nos 3 padrões** (4.6).

## Vereditos por eixo

- **Loop de execução:** *base excelente, detalhes soltos na onda H4* — o que a onda adicionou (tipos de série, superset, PR, anilhas, notas) integra-se ao diálogo, mas cada um deixou uma quina: warmup contamina, superset não guia, alvo some, descanso zerado não estende, PR celebra pouco. São ajustes de costura, não refitos.
- **Navegação/informação:** *arquitetura certa, acabamento desigual* — pilha, bottom bar, herói e drill-down existem e funcionam; os desvios são rotação (3 telas), ordem de dobra (Medidas, Home) e gráficos sem rótulo de valor.
- **Visual:** *um sistema com exceções concentradas* — tokens e componentes respeitados na base; o desvio mora nos diálogos manuais, nos botões primários inflados e nas cores de estado instáveis.
- **A11y/i18n/Wear:** *paridade prometida e não entregue numa camada inteira* — strings testadas, mas A11y.kt, corrida GPS, formatters e rotulagem de domínio ficaram fora; no pulso, falta scroll, háptico de fim de descanso e a restauração honesta do standalone.

## Próxima etapa sugerida (fila de specs — Horizon 6: Polimento UX/UI)

Por AGENTS.md, itens de roadmap exigem spec antes de implementar. Fatiamento proposto (ordem de execução):

1. **H6-1 — Correções de função morta na UI (P0)**: 0.1–0.8. São bugs de função já entregue (alguns de spec anterior) — podem ir como lote `fix:` com testes, sem spec formal, ou numa spec única se o dono preferir rastreio.
2. **H6-2 — Loop de execução: alvo, aquecimento e descanso** (P1.1–1.12): alvo no card, warmup ≠ trabalho, +15 s no zerado, pausa/estende → relógio, superset → próximo membro, "+ série", PR banner, anilhas inline, marcador do corrente, rodapé da troca, debounce e háptico do pulso.
3. **H6-3 — i18n e TalkBack completos** (P3.1–3.5, 3.7, 3.8): A11y.kt, corrida GPS, formatters/rotulagem com `appLocale`, calendário, switch, literais remanescentes.
4. **H6-4 — Consistência visual e componentes** (P4.1–4.8): `ConfirmationDialog` ×7, tipografia de timer/métricas, TonalButton em cards, pilha de ações do card, cor de estado, `EmptyState`, espaçamentos, DatePicker no registro avulso.
5. **H6-5 — Telas de informação** (P2.1–2.8 + P3.6): Medidas (ordem, edição, foto em tela cheia), gráficos com eixo/último valor, drafts saveable (Generate/Histórico), ordem da Home, streak drill-down, scroll e fonte 2x no pulso.

Cada spec sai de `_TEMPLATE.md` com os testes listados; gate `./scripts/verify.sh` verde a cada item.
