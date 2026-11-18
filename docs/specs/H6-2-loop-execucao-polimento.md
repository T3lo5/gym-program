# H6-2 — Loop de execução: alvo, aquecimento e descanso de ponta a ponta

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-2` (Horizon 6 — Polimento UX/UI; ver [auditoria](../../auditoria-ux-ui-2026-11-18.md), P1) |
| MoSCoW | `M` |
| Versão alvo | `1.11.0` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-18) |
| Implementação | `pendente` |
| Depende de | H6-1 (aquecimento ≠ série de trabalho) |

## Objetivo

Fechar as quinas que a onda H4 deixou no loop entre séries: o usuário vê o
alvo do plano no card, consegue estender o descanso mesmo depois de zerado,
o relógio acompanha pausa/estende, o superset guia ao próximo membro, ainda
dá para registrar série extra com valores próprios, o PR é celebrado no
momento e o pulso protege contra toque duplo e avisa o fim do descanso.

## Fora de escopo

- Tipografia/apresentação do RestBar e das métricas de GPS (H6-4).
- Aquecimento como série de trabalho e descanso fixo de warmup (H6-1).
- Novo contrato `:protocol` além do campo aditivo necessário para a pausa
  (ver Regras 3); `protocolVersion` continua 1.
- Reordenar/remove qualquer ação existente do card (H6-4 cuida da densidade
  visual; aqui as ações continuam todas acessíveis).
- Scroll das telas do pulso (H6-5).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Alvo no cabeçalho do card e no helper do `SetEditDialog`; "+15 s"/"Reiniciar" no RestBar concluído; "+ série" compacta quando o exercício cumpriu o planejado; marcador visual do exercício corrente + rolagem na entrada; rodapé fixo (escopo + Cancelar/Confirmar) na folha de troca; `rememberSaveable` nos flags (`setToDelete`, `showFinishConfirm`, `showAbandonConfirm`, `showSwapConfirm`, `swapTargetId`) e selo de PR derivado das séries |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `extendRest` reconstrói deadline quando o descanso já zerou; `pauseRest`/`resumeRest`/`extendRest` republicam o estado do relógio; rolar ao próximo membro do superset ao registrar série não-última; banner de PR no salvamento |
| `app/src/main/java/com/treinos/app/ui/components/SetEditDialog.kt` | Quebra de anilhas inline sob o campo de carga quando `barbell` (reusa `plateBreakdown`/`WarmupRamp` da H4-5; o atalho colapsável continua) |
| `app/src/main/java/com/treinos/app/ui/SupersetPlan.kt` | (se faltar) helper `nextMemberOf(group, exerciseOrder)` para a rolagem — regra pura testada |
| `wear/src/main/java/com/treinos/wear/ui/WearTimer.kt` | Chave de sincronização inclui o estado de pausa (ex.: `...:restRemainingSeconds:paused`); `Tick` chegando a 0 expõe o evento para háptico/anúncio |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Háptico + anúncio "Descanso encerrado" ao zerar (modo conectado e standalone); `liveRegion` no restante nos marcos (como no celular); debounce (janela de 1 s) no "Concluir série" do standalone |
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | Campo aditivo `restPaused: Boolean = false` em `WearWorkoutState` (se a dedupe por chave não bastar — decidir na implementação; `ignoreUnknownKeys` cobre os dois lados) |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | `workout_extra_set`, `workout_rest_restarted`, `wear_rest_finished_announcement`, banner de PR (`workout_pr_banner`) e "A seguir: %1$s" — paridade obrigatória |

## Modelo de dados / contrato

- Room: nenhuma migration (PR derivado das séries já gravadas; alvo já está em
  `SessionExerciseEntity.targetReps`).
- `:protocol`: no máximo um campo aditivo (`restPaused`, default `false`);
  celular antigo ignora (`ignoreUnknownKeys`), relógio antigo nunca o envia.
- Backup/sync: intocados.

## Regras e casos de borda

1. **Alvo**: o cabeçalho do card mostra `targetReps` quando não vazio (mesmo
   texto da Home); o helper do diálogo cita "Alvo: 8-10" acima do campo; vazio
   = nada aparece (comportamento atual).
2. **"+15 s" no concluído**: com `remaining <= 0`, o RestBar mantém "+15 s" e
   ganha "Reiniciar" (reinicia o total original); `extendRest` quando
   `restDeadlineAtMillis <= 0` e não pausado reconstrói o deadline a partir de
   `restTotalSeconds` (ou 15 s se o total também foi limpo) e religa o loop.
3. **Pausa no relógio**: `pauseRest` publica o estado com
   `restRemainingSeconds` congelado + pausado; `resumeRest`/`extendRest`
   republicam; a chave do relógio muda com o estado de pausa, então o pulso
   para de contar (pausado) ou reinicia (retomada/estende) sem duplicar.
4. **Superset**: ao registrar série de membro não-último do grupo, a tela rola
   até o card do próximo membro (reusa `scrollTargetIndex`; item 0 é o
   resumo) e o RestBar mostra "A seguir: <nome>"; membro último/fora do grupo
   não rola.
5. **"+ série"**: quando o exercício cumpriu o planejado, o botão principal
   continua "Exercício concluído — próximo" e uma ação compacta "+ série"
   abre o `SetEditDialog` vazio (sem avançar índice, sem pré-preencher do
   planejado — pré-preenche da última série de trabalho, regra H6-1).
6. **PR banner**: ao salvar série que bate PR, além do háptico e do selo, um
   banner/snackbar efêmero mostra "Novo recorde: <carga> kg × <reps>";
   dispensável; não reaparece a cada série (só quando o PR é novo).
7. **Estado sobrevive a rotação**: `prSessionExercises` passa a ser derivado
   das séries da sessão vs `personalRecords` (sobrevive a processo morto) e os
   flags de confirmação vão para `rememberSaveable` (ids/Boolean — Bundle
   friendly); o diálogo de série já sobrevive (H3-31) e continua.
8. **Anilhas inline**: com equipamento `barbell` e carga carregável, a quebra
   por lado aparece sob o campo de carga no diálogo (e, opcionalmente, na
   linha da série gravada); campo vazio/inválido esconde sem crash (mesma
   guarda do atalho atual).
9. **Exercício corrente**: o primeiro exercício com séries pendentes ganha
   borda/tonalidade distinta; ao entrar na tela (ou retomar), a lista rola
   até ele uma vez.
10. **Troca de exercício**: busca/candidatos rolam; escopo + Cancelar/Confirmar
    ficam fixos no rodapé da folha (fora do LazyColumn); nada muda no
    comportamento de troca.
11. **Debounce do standalone**: "Concluir série" no modo standalone usa
    `WearDebounce(windowMillis = 1_000)`; o modo conectado mantém os 2 s;
    os botões +/− do editor continuam sem debounce (H4-15).
12. **Fim do descanso no pulso**: `Tick` chegando a 0 dispara háptico e o
    texto "Descanso encerrado" permanece visível (não some); `liveRegion`
    Polite nos marcos 60/30/10/0 (mesma regra do celular, `timerShouldAnnounce`).

## Critérios de aceite

1. **Dado** um exercício com `targetReps = "8-10"`, **quando** abro o treino,
   **então** o card mostra o alvo e o diálogo pré-preenchido o cita.
2. **Dado** o descanso zerado ("Descanso concluído"), **quando** toco "+15 s",
   **então** a contagem reinicia com 15 s e o estado volta a "em execução".
3. **Dado** o descanso em execução no celular, **quando** pauso no celular,
   **então** o pulso congela a contagem; ao retomar, o pulso reinicia do
   restante.
4. **Dado** um superset A1→A2, **quando** registro a série do A1, **então** a
   lista rola até o A2 e o RestBar indica "A seguir: A2".
5. **Dado** o exercício com todas as séries planejadas registradas, **quando**
   quero registrar um drop set com outra carga, **então** a ação "+ série"
   abre o diálogo sem avançar de exercício.
6. **Dado** uma série que bate PR, **quando** salvo, **então** háptico + selo
   + banner "Novo recorde: 80 kg × 5" (uma vez por PR).
7. **Dado** o selo de PR visível, **quando** giro o aparelho, **então** o selo
   e as confirmações abertas sobrevivem.
8. **Dado** equipamento barbell e carga 100 kg, **quando** o campo de carga
   está preenchido no diálogo, **então** a quebra "Por lado: 20 + 20 kg"
   aparece sem abrir o atalho.
9. **Dado** a sessão retomada na metade, **quando** abro a tela, **então** o
   exercício corrente está destacado e a lista rola até ele.
10. **Dado** a folha de troca com 8 candidatos, **quando** seleciono o 2º,
    **então** Cancelar/Confirmar estão visíveis sem rolar a lista.
11. **Dado** dois toques rápidos em "Concluir série" no standalone, **então**
    apenas uma série é registrada.
12. **Dado** o descanso chegando a zero no pulso, **então** háptico dispara e
    "Descanso encerrado" fica visível e anunciado.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/RestTimerTest.kt` (ou o existente do RestTimer) | `extend_apos_zerar_reconstrui_deadline`, `reiniciar_restaura_total_original` |
| `app/src/test/java/com/treinos/app/ui/SupersetPlanTest.kt` | `proximo_membro_do_grupo`, `ultimo_membro_nao_tem_proximo` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | `alvo_de_reps_visivel_no_card`, `serie_extra_abre_dialogo_sem_avancar`, `pr_banner_efemero_no_salvamento`, `confirmacoes_sobrevivem_rotacao`, `troca_rodape_fixo_sem_rolagem` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` (a11y/rolagem) | `exercicio_corrente_destacado_e_rolado_na_entrada` |
| `app/src/test/java/com/treinos/app/ui/components/SetEditDialogTest.kt` (novo, Robolectric) | `anilhas_inline_sob_o_campo_para_barbell`, `sem_inventario_nao_mostra_quebra` |
| `wear/src/test/java/com/treinos/wear/ui/WearTimerTest.kt` | `chave_inclui_pausado`, `pausa_congela_e_retomada_reinicia`, `tick_zero_expoe_evento_de_fim` |
| `wear/src/test/java/com/treinos/wear/ui/WearStandaloneSessionTest.kt` | `concluir_serie_respeita_debounce_1s` |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `publica_rest_pausado` (se o campo aditivo entrar) |

## Verificação manual

1. Treino com alvo definido: conferir card, diálogo e Home coerentes.
2. Deixar o descanso zerar e tocar "+15 s"; depois "Reiniciar".
3. Pausar/retomar/estender com o pulso pareado: o pulso acompanha.
4. Superset A1→A2: registrar no A1 e conferir rolagem + "A seguir".
5. Cumprir o planejado e registrar drop set via "+ série".
6. Bater PR: banner + selo + háptico; girar o aparelho e conferir o selo.
7. Barbell 100 kg: quebra inline no diálogo.
8. Retomar treino na metade: destaque + rolagem ao corrente.
9. Folha de troca com catálogo cheio: botões visíveis sem rolar.
10. Standalone: toque duplo em "Concluir série" registra 1 série; descanso
    zerando no pulso vibra e anuncia.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Republicação do descanso a cada transição** pode gerar mensagens extras —
  a dedupe por chave (`WearTimer.syncFromState`) e a fila da H3-20 absorvem;
  se o campo `restPaused` entrar no protocolo, é aditivo e reversível.
- **Derivação do selo de PR** muda de "memória de sessão" para "derivado do
  Room": se a derivação divergir (séries editadas no Histórico), o selo pode
  reaparecer — aceitável e testado; rollback = voltar ao `remember`.
- **Diff grande no WorkoutScreen** (vários achados no mesmo arquivo):
  implementar em sub-commits por achado; cada um é independente e reversível.
