# H6-1 — Correções de função morta na UI (P0 da auditoria 2026-11-18)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-1` (Horizon 6 — Polimento UX/UI; ver [auditoria](../../auditoria-ux-ui-2026-11-18.md)) |
| MoSCoW | `M` |
| Versão alvo | `1.10.1` (patch; se sair junto com feats do H6, `1.11.0`) |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-18) |
| Implementação | `pendente` |
| Depende de | Nada |

## Objetivo

Funções já entregues por specs anteriores voltam a funcionar de verdade na UI:
o usuário consegue ver os sinais de alerta da mobilidade, trocar o idioma do
app, ver o pedido de permissão em qualquer dia, iniciar o treino pelo pulso
com a sessão restaurada onde parou e registrar aquecimento sem contaminar a
série de trabalho.

## Fora de escopo

- Reestilizar visual de qualquer tela (isso é H6-4).
- Mudar o contrato `:protocol` além de **preencher** campos que já existem
  (`intervalPhase`/`intervalRemainingSeconds` são aditivos desde a H4-17,
  `protocolVersion` continua 1).
- Traduzir a camada de formatação/rotulagem (H6-3) — aqui só o seletor volta a
  aparecer; o que ele controla é da H6-3.
- Novo comportamento de edição de plano/medidas (H6-5).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | Text do toggle de segurança (`mobility_safety_show`/`_hide`) acionável: `Modifier.clickable(onToggleSafety)` + `minimumInteractiveComponentSize()` + `role = Role.Button` |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Card "Idioma" (`settings_language_title`) renderiza `LanguageSelector(current = currentAppLanguage(), onChange = applyAppLanguage)` — composables já existem (`:1445-1506`), só faltam ser chamados |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Bloco `if (!permissionsDismissed) { item { PermissionsCard(...) } }` dedentado para o nível da lista (fora do `if (todaySession != null)`); `item { TodayHeroCard(...) }` guardado por `if (todaySession !is TodaySession.Rest)` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `openLogActivity()`/`editLogActivity(sessionId)` setam `_backTarget.value = _screen.value` antes de navegar (padrão dos 5 demais `open*` em `:1284-1311`); `logSet` ramifica por `input.setType`: warmup dispara `startRest` de 30 s fixo e não entra no pré-preenchimento |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Pré-preenchimento e "Repetir última série" usam a última série de **trabalho** (`setType != "warmup"`); helper do diálogo cita o alvo quando existe |
| `wear/src/main/java/com/treinos/wear/ui/WearStandaloneSession.kt` | `Start` deriva `exerciseIndex`/`setNumber` das séries já registradas (pendentes + gravadas por `sessionExerciseId`) em vez de zerar; `restEndsAt` do snapshot recalcula o descanso (`max(0, restEndsAt - now)`) |
| `wear/src/main/java/com/treinos/wear/data/WearStandaloneStore.kt` | `loadSnapshot`/leitura de sessão devolve também o `restEndsAt` persistido (o `UPDATE` de `saveRestEndsAt` existe; falta o caminho de leitura) |
| `app/src/main/java/com/treinos/app/data/wear/WearSessionMapper.kt` | `toWearState`/`toSnapshot` preenchem `intervalPhase`/`intervalRemainingSeconds` a partir da sessão de corrida com intervalos (H4-17) |
| `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt` | Tela Modality (corrida) renderiza fase + contagem quando os campos vêm preenchidos |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | Chaves novas apenas se o helper do alvo precisar de rótulo (ex.: `workout_target_reps_helper`) — paridade obrigatória |

## Modelo de dados / contrato

Nenhuma migration e nenhuma mudança de contrato:

- `:protocol` intocado (os campos de intervalo já existem em `WearWorkoutState`,
  `protocolVersion = 1`); celular antigo que não os preenche continua
  compatível (`null` = sem intervalo, tela de corrida sem o bloco).
- Room (app e wear) intocado — a restauração do standalone lê colunas que já
  existem (`restEndsAt` em `wear_sessions`).

## Regras e casos de borda

1. **Toggle de segurança**: o texto alterna `mobility_safety_show`/`_hide` e o
   conteúdo só expande pelo toque; alvo ≥ 48 dp; estado inicial recolhido
   (como hoje).
2. **Idioma**: a seleção aplica per-app locale imediatamente (lógica existente
   em `applyAppLanguage`); `System` é opção; nada de texto livre.
3. **Permissões**: o card aparece sempre que `!permissionsDismissed`,
   independente de haver treino hoje; "Agora não" continua dispensando.
4. **Herói**: `TodaySession.Rest` não renderiza card nenhum (nem vazio); os
   demais estados (`Today`/`Next`) seguem como estão.
5. **Back do registro avulso**: `voltar()` após `openLogActivity`/`editLogActivity`
   retorna à tela de origem (Histórico quando veio dele); salvar também volta
   à origem (`saveActivity` já usa `backTarget` em `:2318` — o bug é só a
   ausência do set).
6. **Standalone**: reinício do relógio no meio da sessão retoma o exercício
   corrente (derivado das séries existentes por `sessionExerciseId`), o número
   da próxima série e o descanso restante (`restEndsAt` futuro); `restEndsAt`
   no passado → sem descanso, sem card fantasma; sem séries registradas →
   exercício 1, série 1 (comportamento atual preservado).
7. **Intervalos no pulso**: `intervalPhase`/`intervalRemainingSeconds` são
   publicados quando a sessão de corrida tem intervalo ativo; `null`/vazio
   mantém a tela como está; o pulso NÃO inicia nem pula intervalos (só exibe).
8. **Warmup**: registrar aquecimento dispara descanso fixo de 30 s (não o do
   plano); o pré-preenchimento do `SetEditDialog` e o "Repetir última série"
   passam a considerar apenas a última série com `setType != "warmup"`; se o
   exercício só tem warmups, o diálogo abre com o alvo do plano (cascata
   existente) e o botão "Repetir" fica desabilitado/ausente.
9. Nenhuma função removida: tudo que existe continua acessível.

## Critérios de aceite

1. **Dado** a tela de mobilidade, **quando** toco em "Ver sinais de alerta",
   **então** o conteúdo expande/recolhe e o TalkBack anuncia o papel de botão.
2. **Dado** o app em PT, **quando** seleciono English no card "Idioma" de
   Ajustes, **então** a UI troca na hora e a escolha sobrevive ao processo.
3. **Dado** hoje é dia de descanso, **quando** abro a Home com permissão
   pendente, **então** o card de permissões aparece.
4. **Dado** um plano sem weekday fixo (flexível), **quando** abro a Home,
   **então** nenhum card-herói vazio aparece no topo.
5. **Dado** que entrei no registro avulso a partir do Histórico, **quando**
   toco em Voltar (ou salvo), **então** retorno ao Histórico.
6. **Dado** uma sessão standalone em andamento no exercício 3 com descanso
   rodando, **quando** o relógio reinicia e reabro o app, **então** volto ao
   exercício 3, série correta e descanso restante.
7. **Dado** uma corrida intervalada em andamento, **quando** o pulso recebe o
   estado, **então** ele mostra a fase ("Tiro"/"Recuperação") e a contagem.
8. **Dado** um exercício com aquecimento registrado, **quando** abro o diálogo
   de série ou toco "Repetir última série", **então** os valores vêm da última
   série de trabalho (ou do alvo do plano), nunca do warmup; o descanso do
   warmup é 30 s.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | `heroi_nao_aparece_em_plano_flexivel`, `card_permissoes_aparece_em_dia_de_descanso` |
| `app/src/test/java/com/treinos/app/ui/SettingsScreenTest.kt` | `card_idioma_renderiza_seletor` |
| `app/src/test/java/com/treinos/app/ui/NavigationBackTest.kt` | `editar_registro_avulso_volta_para_origem` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | `repetir_ultima_serie_ignora_aquecimento`, `aquecimento_dispara_descanso_curto` |
| `wear/src/test/java/com/treinos/wear/ui/WearStandaloneSessionTest.kt` | `start_deriva_exercicio_e_serie_das_series_registradas`, `start_restaura_descanso_do_rest_ends_at`, `start_sem_series_comeca_no_primeiro_exercicio` |
| `app/src/test/java/com/treinos/app/data/wear/WearSessionMapperTest.kt` | `publica_intervalo_da_corrida`, `sem_intervalo_publica_nulo` |
| `wear/src/test/java/com/treinos/wear/ui/WearSessionKindTest.kt` (ou novo `WearRunScreenTest`) | `tela_corrida_renderiza_fase_e_contagem` |

## Verificação manual

1. Mobilidade: expandir/recolher sinais de alerta (toque e TalkBack).
2. Ajustes → Idioma: PT → EN → PT, conferir aplicação imediata e persistência.
3. Home em dia de descanso com permissão pendente: card visível; dispensar e
   conferir que não volta.
4. Criar plano flexível (sem weekday): Home sem herói vazio.
5. Histórico → editar registro avulso → Voltar: volta ao Histórico.
6. Relógio standalone: iniciar sessão, avançar 2 exercícios com descanso
   rodando, reiniciar o relógio, reabrir — mesma posição e descanso.
7. Corrida intervalada com o pulso pareado: fase e contagem no pulso.
8. Treino com aquecimento: pré-preenchimento e "Repetir" vêm da série de
   trabalho; descanso do warmup = 30 s.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Derivação do exercício corrente no standalone**: se a derivação por
  `sessionExerciseId` divergir do estado real (séries manuais fora de ordem),
  o fallback é o comportamento atual (exercício 1) — nunca crash; testes
  cobrem ordem e ausência.
- **Warmup com descanso fixo**: 30 s é decisão de produto registrada aqui;
  mudar o valor é edição de constante, sem contrato.
- **Rollback**: cada correção é independente e reversível por arquivo; nenhuma
  migration, nenhum contrato novo.
