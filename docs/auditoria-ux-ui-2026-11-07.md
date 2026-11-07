# Auditoria UX/UI — Treinos (celular)

**Data:** 2026-11-07 · **Método:** 4 auditorias paralelas somente-leitura (fluxo de execução, navegação/IA, design system, acessibilidade/robustez), com verificação cruzada das alegações-chave direto no código. Nenhuma linha de código foi alterada.

**Contexto:** o app está funcional e já entregou polimento visual (H3-2) e acessibilidade básica (H3-3). Esta auditoria procura o que **ainda** falta, sob o princípio do ROADMAP §3.5: *"rápido de usar entre séries (toques mínimos, alvos grandes, leitura à luz do sol)"*.

---

## Síntese executiva

**O que está bom (e deve ser mantido):** alvos de toque grandes e consistentes, teclado numérico correto, pré-preenchimento da última série e da sugestão de progressão, dado da sessão robusto em SQLite com retomada manual, zero cor hardcoded nas telas, tipografia 100% via escala M3, semântica de acessibilidade acima da média (`stateDescription`, `heading`, `minimumInteractiveComponentSize`), mensagens de rede em pt-BR acionáveis, validação por campo em ActivityLog/RunLog, fallback da IA com motivo traduzido (H3-21).

**Os 3 grandes problemas:**

1. **O loop do descanso é mudo e cego.** O app é bom para *registrar* e mudo para *acompanhar*: o timer rola para fora da tela, não avisa quando acaba (sem som, sem vibração, sem notificação), a tela apaga no meio do descanso, não pausa nem estende, morre em processo morto e conta por tick (atrasa em Doze). É a metade do tempo de treino que o produto ignora.
2. **A Home não responde "o que eu treino hoje?".** O treino do dia fica abaixo da dobra (~700+dp de cards antes), o card mais destacado (ActivePlanCard) é metadado não-acionável, nada marca "hoje", a navegação é um hub único em que "voltar" sempre vai para Home e Histórico/Ajustes são TextButtons no topo — a pior zona de alcance com uma mão.
3. **Confiança nos dados.** Série registrada não pode ser corrigida, carga inválida vira `null` silenciosamente (e contamina o motor de progressão), "Finalizar treino" é destrutivo em 1 toque, a mobilidade cria sessão fantasma que nunca fecha, erros são snackbars de ~4s, e rotação apaga formulários inteiros (zero `rememberSaveable` no módulo).

---

## P0 — Bugs funcionais com impacto direto de UX

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 0.1 | **Sessão fantasma de mobilidade**: `startSession` cria sessão `in_progress`; ao finalizar, `logPlannedMobility` insere **outra** sessão `completed` — a original nunca fecha. Sem UI para "abandonar treino" (`deleteSession` só roda em `replaceAllData`). | `TreinosApp.kt:268-273` (não passa `planId`/`dayId`), `AppViewModel.kt:780,881-891`, `WorkoutRepository.kt:304-327` | Passar `planId`/`dayId` reais e fechar a sessão aberta; ação "Abandonar treino" na Home/Workout | M |
| 0.2 | **Carga inválida vira `null` silenciosamente**: `"6.0.5"` → `parseDecimal` → `null`, série salva sem carga, sem aviso; RPE sem validação 1–10. O erro contamina histórico e progressão para sempre. | `WorkoutScreen.kt:486-487,403-410,452-465` | Validar no diálogo e recusar inválido com mensagem; nunca salvar `null` silencioso | P |
| 0.3 | **"Finalizar treino" destrutivo em 1 toque**, sem confirmação nem desfazer. | `WorkoutScreen.kt:98-105`, `AppViewModel.kt:851-864` | Confirmação com resumo ("12/16 séries") ou snackbar "Desfazer" | P |
| 0.4 | **Timer de mobilidade exibe "1 min" durante 60 segundos** (`formatDuration(90)` = "1 min") no formato mais importante da tela. | `MobilityScreen.kt:216-220`, `Formatters.kt:30-38` | Usar `formatMinuteSecond` (já existe, `Formatters.kt:132`) | P |
| 0.5 | **Timers contam por tick, não por relógio**: `delay(1_000)` por iteração atrasa em Doze/tela apagada — descanso de 90s vira 110s reais. | `AppViewModel.kt:1016-1024`, `MobilityScreen.kt:159-176` | Ancorar em wall-clock (deadline + derivar restante) | P |

## P1 — O loop do descanso (o coração do produto)

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 1.1 | **Fim do descanso é silencioso** — zero `Vibrator/SoundPool/Notification` no módulo. O momento em que o app deve falar com o usuário é o único em que ele não olha a tela. | `AppViewModel.kt:1016-1024` | Vibrar + beep curto ao zerar (respeitando modo silencioso); notificação local se em background | P |
| 1.2 | **Timer não é persistente na tela e só sabe "Pular"**: RestCard é um `item` da LazyColumn (rola para fora); sem pausar, +15s, reiniciar; o `ExerciseDetailSheet` cobre o timer; com `restSeconds = 0` o card "Descanso concluído" aparece inutilmente após cada série. | `WorkoutScreen.kt:186-194,267-272` | Faixa fixa (overlay acima do "Finalizar") com contagem grande, Pular/+15s/pausar; esconder quando descanso = 0 | M |
| 1.3 | **A tela apaga no meio do descanso e do alongamento** — zero `keepScreenOn` no módulo. | `MainActivity.kt:11-29` | Manter tela acesa enquanto houver sessão/descanso/rotina ativa | P |
| 1.4 | **Processo morto: sessão sobrevive, descanso morre** (nada em `SavedStateHandle`); o app reabre na Home e exige toque manual para retomar. | `AppViewModel.kt:173,345-349,115-116` | Persistir deadline do descanso; ao abrir com sessão `in_progress`, oferecer retorno direto | M |
| 1.5 | **Gesto mais frequente do app (registrar série) não tem atalho de 1 toque**: 2 toques + diálogo de 3 campos quando a carga não muda (caso mais comum). O Wear já faz o equivalente. | `WorkoutScreen.kt:374-379,391-400` | Botão primário "Repetir última série (60kg × 10)" que grava em 1 toque e dispara o descanso | P/M |
| 1.6 | **Série registrada não pode ser corrigida** (linhas não clicáveis, sem editar/excluir). | `WorkoutScreen.kt:329-371` | Tocar na série para editar/excluir | M |
| 1.7 | **Sem háptico nem confirmação perceptível ao registrar série** (só um Check estático, sem `animateItem`). | `WorkoutScreen.kt:365-369` | Vibração curta + animação de check na linha/contagem | P |
| 1.8 | **Nenhuma noção de "exercício atual"**: `nextExercise()/isCurrentExerciseCompleted()` são código morto; lista plana sem destaque; o botão continua "Registrar série N+1" após cumprir o planejado. | `AppViewModel.kt:1036-1062`, `WorkoutScreen.kt:196-203` | Destacar/colapsar concluídos, rolar até o corrente, botão vira "Exercício concluído — próximo" | M |

## P2 — Navegação, Home e geração

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 2.1 | **O treino do dia fica abaixo da dobra e nada marca "hoje"**: ordem fixa InProgress → chips → ActivePlanCard → revisão → deload → TabRow → DayCards; `defaultModalityTab` cai na aba de *amanhã* quando hoje é descanso. | `HomeScreen.kt:121-247`, `WeeklySchedule.kt:41-44` | Card-herói "Hoje — <sessão>" com "Iniciar" direto; badge "Hoje"; em descanso, "Hoje é descanso — próximo: X" | M |
| 2.2 | **Navegação sem pilha**: BackHandler único → `goHome()`; salvar edição vinda do Histórico abandona o usuário na Home (`AppViewModel.kt:952`) enquanto excluir volta ao Histórico (`:968`); Histórico/Ajustes só via TextButton no topo. | `TreinosApp.kt:167-172,246,261,290`, `HomeScreen.kt:108-109` | Pilha mínima origem→destino (ou Navigation Compose); bottom bar Treinar/Histórico/Ajustes | M |
| 2.3 | **Geração: até ~90s de spinner de 18dp sem cancelar**; motivo do fallback (H3-21) chega como snackbar de ~4s na Home. | `GenerateScreen.kt:644-651`, `BackendClient.kt:95`, `AppViewModel.kt:641-647` | Overlay com estágios + tempo decorrido + Cancelar; motivo do fallback persistente no selo "Gerado localmente" (`HomeScreen.kt:557`) | M |
| 2.4 | **Primeiro treino exige formulário de ~14 seções com CTA no fundo e nada pré-selecionado**; hint de validação longe da causa. | `GenerateScreen.kt:154-158,577-586,588-656` | Pré-incluir "Musculação 4x" (ou última config); colapsar perfil/sexo/revisão em "Mais opções"; CTA sticky | M |
| 2.5 | **Home acumula ~15 itens com ênfase invertida**: 3 OutlinedButton empilhados + 3 parágrafos permanentes; destrutivo ("Excluir plano complementar") no corpo da lista. | `HomeScreen.kt:259-319,175-187` | Ações secundárias compactas/menu; rodapé só na primeira execução; destrutivo no overflow do card | P/M |
| 2.6 | **Controles com efeito ambíguo**: objetivo/nível "(musculação)" sempre visíveis mesmo em plano só de corrida; `modalityToDto` envia `goal` fixo "hypertrophy" enquanto o chip global vai no prompt — o chip pode ser ignorado justamente quando o usuário escolhe "Força". | `GenerateScreen.kt:351,369,160-178`, `AppViewModel.kt:622-638` | Esconder seções de força quando nenhuma modalidade de força está inclusa; alinhar o goal no DTO | P |
| 2.7 | **Histórico: lista filtrada ~1 tela abaixo dos filtros**; tocar num dia não muda nada na área visível. | `HistoryScreen.kt:148-255` | Chips de modalidade + chip do dia logo após o calendário; com dia selecionado, mostrar a lista imediatamente | P |
| 2.8 | **Sem deep link/shortcut para retomar treino** (manifest só MAIN/LAUNCHER). | `AndroidManifest.xml:23-31` | Shortcut dinâmico "Retomar treino" quando `inProgressSession != null` | P |
| 2.9 | **Ajustes: 6 cards flat de infraestrutura com peso visual igual**; o import destrutivo ("Substituir tudo") é a única ação crítica **sem** AlertDialog; dica de emulador (`10.0.2.2`) vazou para produção. | `SettingsScreen.kt:166-649,596-624,176-178` | Agrupar Sincronização/Backup/Avançado (colapsável); AlertDialog no import; dica para docs | M |

## P3 — Estados, erros e robustez

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 3.1 | **Rotação/processo morto perdem TODO estado local de UI** (zero `rememberSaveable` no módulo): diálogo de série, formulário de geração, campos de corrida, formulário de atividade, timer de mobilidade. | `WorkoutScreen.kt:392-401`, `GenerateScreen.kt:138-158`, `RunWorkoutScreen.kt:46-50`, `LogActivityScreen.kt:57-70`, `MobilityScreen.kt:45-51` | `rememberSaveable` nos formulários; timer de mobilidade no ViewModel | M |
| 3.2 | **Erro = snackbar transitório de ~4s, sem "Tentar de novo", sem estilo de erro**; GenerateScreen não recebe estado de erro — perdido o snackbar, a tela volta ao normal sem explicação. | `TreinosApp.kt:150-154`, catches em `AppViewModel.kt:649,784,824,861,954,1134,1159` | Snackbar com `actionLabel` + `SnackbarDuration.Long`, ou persistir erro na tela (o `ErrorState` de `Common.kt:179-194` já existe e está subutilizado) | M |
| 3.3 | **Erros técnicos expostos**: corpo HTTP cru (até 300 chars de JSON/HTML) e `error.message` cru em ~18 pontos. | `BackendClient.kt:79,84`, `AppViewModel.kt:157,649,704,1116,1212…` | Mapear exceção → mensagem acionável; logar o detalhe em vez de exibir | P |
| 3.4 | **Fonte 1.3x/2.0x: 13 `maxLines = 1` sem `overflow` na Home** (corta no meio do glifo); chips de filtro do histórico em `Row` sem scroll — inalcançáveis em 2x. | `HomeScreen.kt:165,389,403-474,678-736`, `HistoryScreen.kt:194-212` | `TextOverflow.Ellipsis` onde linha única é intencional; `FlowRow`/scroll nos chips | P |
| 3.5 | **Conteúdo de execução não rola**: RunWorkout e mobilidade são `Column(fillMaxSize)` sem `verticalScroll` (IME cobre o botão finalizar); o AlertDialog de série corta em fonte 2x. | `RunWorkoutScreen.kt:74-79`, `MobilityScreen.kt:189-194`, `WorkoutScreen.kt:417-473` | `verticalScroll` + `imePadding()`; scroll no conteúdo do diálogo | P |
| 3.6 | **TalkBack além do label: só 1 `liveRegion` no app** — início do descanso, série registrada e timer de mobilidade não são anunciados; checkbox de modalidade sem rótulo associado (TalkBack lê "desligado" sem dizer de quê). | `WorkoutScreen.kt:248`, `GenerateScreen.kt:260-300` | `liveRegion` Polite no descanso desde o início; anúncio "Série N salva"; `toggleable(role=Checkbox)` no Row inteiro | M |
| 3.7 | **Contraste: rótulo com `alpha 0.75` falha WCAG AA nos dois temas** (~3,9:1 claro / ~3,3:1 escuro); `ContrastTest` cobre só 4 pares fixos — o par que falha está fora dele. | `HomeScreen.kt:559`, `ContrastTest.kt:8-35` | Remover o alpha; estender o teste aos pares realmente usados (com blend do alpha) | P |
| 3.8 | **"Vazio falso" no arranque**: Home e Histórico mostram empty state antes do Room emitir (flash de "perdi meus treinos?"). `WorkoutScreen` é o único com o padrão certo. | `AppViewModel.kt:130-142,166-171` | Flag `isLoading` (ou `UiState` Loading/Empty/Content) | P |
| 3.9 | **Mobilidade: estado todo em `remember`, dia planejado desconectado, sem transição entre movimentos** (`TRANSITION_SECONDS = 5` definido e nunca usado). | `MobilityScreen.kt:45-51,182-185`, `MobilityRoutine.kt:22` | Passar `planId/dayId/routineId`; estado no ViewModel; pausa de transição com sinal | M |
| 3.10 | **Corrida é um formulário pós-treino**: sem alvo planejado visível, sem cronômetro, distância em metros (corredor pensa em km), "Cancelar" só sai sem avisar. | `RunWorkoutScreen.kt:69,118-126,189-194` | Mostrar alvo do plano, aceitar km, cronômetro mínimo, preservar campos | M |

## P4 — Design system e polimento

| # | Achado | Evidência | Sugestão | Esforço |
|---|--------|-----------|----------|---------|
| 4.1 | **Dynamic color ausente + flash branco no dark mode** (`windowBackground` claro, sem `values-night/`); status bar verde-700 (XML) contrasta com TopAppBar surface (Compose). | `Theme.kt:82-88`, `themes.xml:3-8` | `values-night/themes.xml` ou `enableEdgeToEdge()`; dynamic color em SDK ≥ 31 com fallback verde | P |
| 4.2 | **Motion.kt é código morto** — a transição de tela é o fade padrão do `AnimatedContent`. | `TreinosApp.kt:182`, `Motion.kt:3-16` (zero chamadores) | Ligar `transitionSpec` com `motionFor`/`motionDurationMillis` — ou deletar o arquivo | P |
| 4.3 | **Números locale-dependentes + formatadores duplicados**: `formatWeight`/`trimNumber` (×2)/IMC com `String.format` (locale do aparelho) enquanto o histórico é pt-BR fixo; `formatRunPace`/`formatRunDistance` paralelos aos canônicos. | `WorkoutScreen.kt:498-502`, `HomeScreen.kt:790-794`, `HistoryScreen.kt:671-674`, `GenerateScreen.kt:545`, `RunLog.kt:49-53` | Centralizar `formatDecimal`/`formatKg` com locale fixo em `Formatters.kt`; deletar duplicados | M |
| 4.4 | **"Em andamento" pintado com a cor de erro** no histórico; **todas as mensagens de Ajustes são verdes** — sucesso e falha idênticos. | `HistoryScreen.kt:587-592`, `SettingsScreen.kt:220-226,338-344,513-519,641-647` | "Em andamento" em `tertiary`/`onSurfaceVariant`; `StatusText(success)` em Common.kt com ícone check/erro | P |
| 4.5 | **Padrão "botão ocupado" copiado ~10 vezes** (spinner 18dp + texto), com cor de spinner inconsistente entre usos. | `SettingsScreen.kt:209-217…584-589`, `GenerateScreen.kt:644-650`, `LogActivityScreen.kt:227-233` | `BusyButton(enabled, busyText, idleText, onClick)` em Common.kt | P |
| 4.6 | **O app quase não usa ícones** (7 usos no app inteiro); Home navega por TextButton no TopAppBar; player de mobilidade é todo texto. | `HomeScreen.kt:107-111`, `MobilityScreen.kt:231-285` | `IconButton(History/Settings)` na Home; `PlayArrow/Pause/SkipNext` no player | P |
| 4.7 | **ExerciseImage sem placeholder/erro/cantos arredondados**: URL falha = buraco branco; fallback da letra com tamanho fixo em container de 36dp e 240dp; `ExerciseDetailSheet` exibe músculos/equipamento em inglês cru ("chest", "barbell") apesar de `muscleLabel`/`equipmentLabel` existirem; `size(240.dp)` vence `fillMaxWidth`. | `ExerciseImage.kt:40-45`, `ExerciseDetailSheet.kt:64-93` | `SubcomposeAsyncImage` com fallback em `Error`, `clip(shapes.small)`, `crossfade`; aplicar os labels; `aspectRatio(1f)` | P/M |
| 4.8 | **Semântica de cor dos cards e ritmo vertical variam por tela** (4 tratamentos de card em Ajustes; `spacedBy(4.dp)` no Generate vs 12.dp no resto). | `SettingsScreen.kt:166,348-352,432-436,535-539`, `GenerateScreen.kt:188` | Regra escrita de containers; padronizar `spacedBy(12.dp)` | M |
| 4.9 | **Jargão exposto**: "backend", "snapshot", "deviceId", "_treinos._tcp", "10.0.2.2", "Benchmark 5 km (s)" (segundos!), RPE sem explicação. | `HomeScreen.kt:140-142`, `GenerateScreen.kt:311-312`, `SettingsScreen.kt:176-178,228,246-248`, `LogActivityScreen.kt:149` | Traduzir para linguagem do usuário; 5k aceitar mm:ss; helper de RPE | P |
| 4.10 | **strings.xml é morto** (73 strings, zero `stringResource` no módulo) — todo texto hardcoded em 8 arquivos. | grep `R.string`/`stringResource` → 0 | Decidir: extrair strings ou deletar o recurso morto (inclui `rest_timer_bg`/`work_timer_bg` sem uso) | P |

---

## Quick wins (impacto alto, esforço P — ordenados por ganho/esforço)

1. **Vibrar/beep no fim do descanso + háptico ao salvar série** — maior ganho por linha de código no contexto academia (1.1, 1.7).
2. **`keepScreenOn` durante sessão/descanso/mobilidade** (1.3).
3. **Confirmação ou "Desfazer" no "Finalizar treino"** (0.3).
4. **`formatMinuteSecond` no timer de mobilidade** — 1 linha (0.4).
5. **`overflow = Ellipsis` nos 13 `maxLines` da Home + `FlowRow` nos chips do histórico** (3.4).
6. **`rememberSaveable` no diálogo de série e nos formulários** (3.1, parte).
7. **DatePicker/TimePicker no campo "Quando" do registro avulso** (hoje é texto livre `dd/MM/yyyy HH:mm` validado só ao salvar — `LogActivityScreen.kt:122-131`).
8. **`values-night/themes.xml`** — mata o flash branco no dark mode (4.1, parte).
9. **Ligar o Motion.kt no `AnimatedContent` — ou deletá-lo** (4.2).
10. **"Em andamento" fora do vermelho + `StatusText` sucesso/erro em Ajustes** (4.4).
11. **Pré-incluir "Musculação" no formulário de geração** — primeiro treino cai de 4 toques + rolagem para 3 (2.4, parte).
12. **Reordenar o Histórico**: chips logo após o calendário (2.7).

## Inventário tela × loading / vazio / erro

| Tela | Loading | Vazio | Erro |
|---|---|---|---|
| Home | ⚠️ flash de vazio falso | ✅ sem plano | ❌ snackbar 4s |
| Generate | ⚠️ spinner 18dp, 90s, sem cancelar | n/a | ❌ nada persiste na tela |
| Workout | ✅ "Carregando treino…" | ✅ EmptyState | ❌ snackbar |
| RunWorkout | ❌ session null → formulário morto | ❌ | ⚠️ validação inline ✅; salvar → snackbar |
| History | ⚠️ flash de vazio falso | ✅ EmptyState | ❌ snackbar |
| LogActivity | ⚠️ isSaving só no botão | n/a | ⚠️ validação por campo ✅ |
| Mobility | n/a | ✅ lista de rotinas | ❌ timer some na rotação |
| Settings | ✅ spinners por ação | ✅ "Nenhum backend encontrado" | ⚠️ erro e sucesso na mesma cor |
| Detalhe exercício | ⚠️ AsyncImage sem placeholder | ✅ fallback de letra | ❌ falha de imagem = quadro em branco |

## Vereditos por eixo

- **Fluxo de execução:** *ok* — fundação aguenta, mas o loop do descanso (metade do tempo de treino) é cego e mudo. Corrigir 1.1–1.5 e 0.2 antes de qualquer polimento.
- **Navegação/IA:** *previsível porém custosa* — hub único com back que sempre vai para Home; a IA é o ponto mais honesto do produto, o problema é a moldura (formulário longo, spinner de 90s, fallback fugaz).
- **Visual:** *esqueleto polido, detalhes caseiros* — zero cor hardcoded e tipografia M3 limpa, mas sem um único ícone de treino, motion nunca ligado, sucesso/falha na mesma cor e copy-paste visível (`trimNumber` ×2, spinner ×10, DayCard/ModalityDayCard gêmeos de ~90 linhas).
- **A11y/robustez:** *básico sólido, dinamismo esquecido* — o que ficou fora do H3-3 é justamente o que muda durante o treino (timer, série registrada, mensagens); e zero `rememberSaveable` é o risco estrutural.

## Próxima etapa sugerida

Por AGENTS.md, itens de roadmap exigem spec em `docs/specs/` antes de implementar. Sugestão de fatiamento em specs:

1. **Spec "loop do descanso"** (P0.5 + P1.1–1.4 + 1.7): vibração/beep, faixa fixa com pausar/+15s, keepScreenOn, wall-clock, deadline persistido. Maior valor por esforço do app inteiro.
2. **Spec "integridade do registro"** (P0.2, P0.3, P1.6): validar carga/RPE, editar/excluir série, confirmar finalização, abandonar treino (+ fechar a sessão fantasma de mobilidade, P0.1).
3. **Spec "Home do dia"** (P2.1, P2.5, P2.7): card-herói "Hoje", badge, reordenação do Histórico.
4. **Spec "estados e erros"** (P3.2, P3.3, P3.8 + inventário): padronizar loading/vazio/erro com o padrão do WorkoutScreen.
5. Quick wins podem ir num único PR de polimento (itens 4–12 da lista acima), cada um com teste JVM quando extrair lógica.
