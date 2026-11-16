# H4-14 — Widget de tela inicial e tile do relógio

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-14` |
| MoSCoW | `C` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |
| Depende de | H3-35 (atalho de retomada), H3-20 (fila/estado do relógio) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 3). O
acesso rápido é o que o mercado mais usa; o app já publica o atalho dinâmico
"Retomar treino" (`AppViewModel.publishResumeShortcut`, H3-35) e já mantém o
estado do treino no relógio (`WearStateStore`, H3-20). Faltam a superfície do
launcher (widget) e a do pulso (tile), reusando esses dois mecanismos.

**Relacionado:** [H3-35](H3-35-ajustes-copy.md) define o extra
`extra_resume_session` e o `ShortcutInfoCompat`; [H3-20](H3-20-robustez-conexao-relogio.md)
define `WearStateStore`/`WearSyncService`. Esta spec **não** cria contrato novo
no `:protocol`.

## Objetivo

Iniciar o treino de hoje ou retomar o treino em andamento em 1 toque, direto da
tela inicial do celular (widget) ou do pulso (tile), sem abrir o app e navegar.

## Fora de escopo

- Modo standalone do relógio (H4-16): o tile **não** inicia nem registra treino
  sozinho; ele abre o app do relógio, que depende do celular.
- Editar carga/reps no pulso (H4-15) e complicação de watchface (o roadmap a
  mantém "Won't now").
- Widget configurável (tamanho, tema, qual plano) e múltiplos widgets.
- Notificação, atualização periódica em segundo plano ou `WorkManager`.
- Novo contrato no `:protocol`/backend; nenhuma conta, nenhuma nuvem.
- Sincronizar o widget com o relógio ou vice-versa.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | dependência `androidx.glance:glance-appwidget:1.1.0` |
| `app/src/main/java/com/treinos/app/widget/WidgetContent.kt` (novo) | `WidgetContent` + `widgetContent(inProgress, plan, todayWeekday)` puro (sem Android) |
| `app/src/main/java/com/treinos/app/widget/TodayWidget.kt` (novo) | `object TodayWidget : GlanceAppWidget` (`provideGlance`) e `TodayWidgetReceiver : GlanceAppWidgetReceiver` |
| `app/src/main/res/xml/today_widget_info.xml` (novo) | `appwidget-provider` do widget |
| `app/src/main/AndroidManifest.xml` | `<receiver>` do `TodayWidgetReceiver` com `APPWIDGET_UPDATE` e meta-data do provider |
| `app/src/main/java/com/treinos/app/ui/MainActivity.kt` | consts públicas `EXTRA_RESUME_SESSION`/`EXTRA_START_TODAY`; tratar `EXTRA_START_TODAY` em `onCreate`/`onNewIntent` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `startTodayWorkout()`; atualizar o widget quando `inProgressSession`/`activePlans` emitem; `publishResumeShortcut` passa a usar `MainActivity.EXTRA_RESUME_SESSION` |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | chaves do widget e `msg_no_workout_today` (paridade PT/EN obrigatória) |
| `app/src/test/java/com/treinos/app/widget/WidgetContentTest.kt` (novo) | testes JVM puros |
| `app/src/test/java/com/treinos/app/ui/StartTodayWorkoutTest.kt` (novo, Robolectric + Room em memória) | `startTodayWorkout` inicia a sessão do dia |
| `wear/build.gradle.kts` | `androidx.wear.tiles:tiles:1.4.0` + `androidx.wear.protolayout:protolayout-material:1.2.0` |
| `wear/src/main/java/com/treinos/wear/tile/WearTileModel.kt` (novo) | `WearTileModel` + `wearTileModel(state, nowMillis)` puro (sem Android) |
| `wear/src/main/java/com/treinos/wear/tile/WorkoutTileService.kt` (novo) | `TileService` do pulso; lê `WearStateStore` e expõe `requestUpdate(context)` |
| `wear/src/main/AndroidManifest.xml` | `<service>` do tile com `BIND_TILE_PROVIDER` |
| `wear/src/main/java/com/treinos/wear/data/WearSyncService.kt` | pedir update do tile depois de `stateStore.saveState(...)` |
| `wear/src/main/res/values/strings.xml` e `wear/src/main/res/values-en/strings.xml` | chaves do tile (paridade PT/EN obrigatória) |
| `wear/src/test/java/com/treinos/wear/tile/WearTileModelTest.kt` (novo) | testes JVM puros |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Dependências novas (versões fixas e justificativa)

- `androidx.glance:glance-appwidget:1.1.0` (estável, 2024-05-01) — API oficial
  de AppWidgets com Compose; compatível com `compileSdk 34`, `minSdk 26`,
  Kotlin 1.9.24 e Compose BOM `2024.04.01` (Compose 1.6.8) já usados no
  `:app`. Traz `GlanceAppWidget`/`GlanceAppWidgetReceiver`; o Compose runtime
  já existe, então é a única dependência nova. Alternativa descartada:
  `RemoteViews` + XML, que exige mais código e não reusa Compose.
- `androidx.wear.tiles:tiles:1.4.0` (estável, 2024-05-01) — API oficial de
  tiles do Wear; compatível com `minSdk 30` do `:wear`.
  `androidx.wear.protolayout:protolayout-material:1.2.0` fornece
  `MaterialTheme`/`Text`/`Button` do tile. Alternativa descartada:
  `androidx.glance:glance-wear-tiles`, que forçaria uma segunda stack Glance no
  `:wear`; o tile nativo é estável e menor.

### Contrato puro do widget (celular)

```kotlin
sealed interface WidgetContent {
    data class Resume(val dayName: String) : WidgetContent
    data class Start(val dayName: String, val modality: String) : WidgetContent
    data class Next(val dayName: String) : WidgetContent
    data object Rest : WidgetContent
    data object NoPlan : WidgetContent
}

fun widgetContent(
    inProgress: SessionWithExercises?,
    plan: WorkoutPlanWithDetails?,
    todayWeekday: Int,
): WidgetContent
```

Intent do widget (reusa o atalho da H3-35):

- `Resume` → `Intent(context, MainActivity::class.java)` com
  `MainActivity.EXTRA_RESUME_SESSION = true`.
- `Start` → `Intent(context, MainActivity::class.java)` com
  `MainActivity.EXTRA_START_TODAY = true`.
- `Next`/`Rest`/`NoPlan` → `Intent(context, MainActivity::class.java)` (abre a
  Home, sem iniciar nada).

`TodayWidget.provideGlance` lê o Room por
`(context.applicationContext as TreinosApplication).container.repository`
(`observeSessions().first()`, `observeActivePlans().first()`). O widget usa o
plano principal (`plan.kind == "main"`); sem principal, o primeiro plano ativo.

### Contrato puro do tile (relógio)

```kotlin
sealed interface WearTileModel {
    data class Active(
        val dayName: String,
        val exerciseName: String,
        val setNumber: Int,
        val plannedSets: Int,
    ) : WearTileModel
    data object Idle : WearTileModel
}

const val WEAR_TILE_STALE_AFTER_MS = 12L * 60L * 60L * 1000L

fun wearTileModel(state: WearWorkoutState?, nowMillis: Long): WearTileModel
```

`WorkoutTileService.onTileRequest` lê
`(application as WearApplication).stateStore.loadState()` e monta o tile com
`LoadAction` (abre `WorkoutWearActivity`, a launcher do `:wear`). O tile não
inicia sessão: sem estado ativo, o toque abre o app do relógio, que pede o
estado ao celular (`WearCommandKind.REQUEST_STATE`).

## Regras e casos de borda

Widget:

1. `inProgress != null` → `Resume` (rótulo "Retomar treino" + `dayName`); tem
   prioridade sobre o treino de hoje.
2. Sem sessão em andamento e `plan == null` → `NoPlan` (rótulo "Abrir
   Treinos").
3. Sem sessão em andamento, com plano, e
   `todaySessionSelector(plan.days, todayWeekday)`:
   - `Today` → `Start` (rótulo "Iniciar treino de hoje" + nome do dia);
   - `Next` → `Next` (rótulo "Próximo: <dia>"); tocar só abre o app;
   - `Rest` → `Rest` (rótulo "Hoje é descanso"); tocar só abre o app.
4. `todayWeekday = LocalDate.now().get(ChronoField.DAY_OF_WEEK)` (1=seg…7=dom),
   o mesmo esperado por `todaySessionSelector` (H3-28).
5. `dayName` vazio é repassado como veio (o modelo puro não resolve string de
   recurso); a UI do widget resolve o fallback com
   `R.string.widget_workout_fallback` ("Treino"/"Workout") e nunca renderiza
   vazio. `modality` é preservado para o rótulo/ícone.
6. `provideGlance` nunca lança: sem plano/sem sessão cai em `NoPlan`; falha de
   leitura do Room também cai em `NoPlan`.
7. Atualização: `TodayWidget.updateAll(context)` é chamado quando
   `inProgressSession` emite e quando `activePlans` emite (início, fim,
   abandono, import/geração de plano); o `GlanceAppWidgetReceiver.onUpdate`
   cobre o refresh do launcher. A chamada é protegida por `runCatching` para
   não derrubar o app nem os testes quando não há host de widget.
8. O widget não pede permissão nova, não usa rede e não altera dados.

Tile:

9. `state == null`, `state.status != "in_progress"` ou
   `nowMillis - state.updatedAt > WEAR_TILE_STALE_AFTER_MS` → `Idle` (rótulo
   "Abrir Treinos").
10. Caso contrário → `Active` com `dayName`, `exerciseName` e
    `setNumber`/`plannedSets`; tocar abre `WorkoutWearActivity`.
11. `updatedAt` no futuro (relógio adiantado) não é stale:
    `nowMillis - updatedAt` negativo não supera o limite → `Active`.
12. `setNumber`/`plannedSets` inválidos não quebram: o modelo devolve
    `setNumber.coerceAtLeast(1)` e `plannedSets.coerceAtLeast(0)`; a UI do tile
    mostra "N de M" e trata `plannedSets == 0` como "—".
13. Atualização: `WorkoutTileService.requestUpdate(context)` é chamado depois
    de `WearStateStore.saveState(...)` (`WearSyncService.onDataChanged`) e
    quando o relógio envia `FINISH_WORKOUT` (`WorkoutWearActivity`); a chamada
    é protegida por `runCatching`.
14. O tile não inicia treino por conta própria (isso é H4-16) e não acessa a
    rede; só lê o último estado local.

## Critérios de aceite

1. **Dado** um treino em andamento, **quando** o widget é renderizado,
   **então** mostra "Retomar treino" e o toque abre a tela de treino (mesmo
   extra do atalho H3-35).
2. **Dado** nenhum treino em andamento e um plano com sessão hoje, **quando**
   o widget é renderizado, **então** mostra "Iniciar treino de hoje" e o toque
   inicia a sessão do dia.
3. **Dado** nenhum plano, **quando** o widget é renderizado, **então** mostra
   "Abrir Treinos" e o toque abre a Home.
4. **Dado** um dia de descanso com próxima sessão, **quando** o widget é
   renderizado, **então** mostra "Próximo: <dia>" e o toque só abre a Home
   (não inicia).
5. **Dado** o relógio com estado `in_progress` recente, **quando** o tile é
   aberto, **então** mostra o dia, o exercício e "N de M séries".
6. **Dado** o relógio sem estado ou com estado mais antigo que 12 h, **quando**
   o tile é aberto, **então** mostra "Abrir Treinos" e o toque abre o app do
   relógio.
7. **Dado** que o treino termina no celular, **quando** o estado do relógio é
   atualizado, **então** o tile é atualizado (ou fica `Idle` após a janela de
   12 h).
8. **Dado** modo avião, **quando** o widget é renderizado, **então** ele
   funciona (lê o Room local); o tile mostra o último estado conhecido.
9. **Dado** o app recém-instalado (sem plano), **quando** o widget é
   adicionado, **então** não há crash.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/widget/WidgetContentTest.kt` | `sessao_em_andamento_vence_treino_de_hoje`, `sem_sessao_com_treino_hoje_inicia`, `sem_sessao_dia_de_descanso_mostra_proximo`, `plano_sem_weekday_mostra_descanso`, `sem_plano_mostra_abrir_app`, `plano_principal_tem_prioridade`, `day_name_vazio_preserva_modality` |
| `app/src/test/java/com/treinos/app/ui/StartTodayWorkoutTest.kt` (Robolectric) | `inicia_sessao_do_dia_de_hoje`, `sem_plano_mostra_mensagem`, `dia_de_descanso_nao_inicia` |
| `wear/src/test/java/com/treinos/wear/tile/WearTileModelTest.kt` | `sem_estado_e_idle`, `estado_in_progress_recente_e_ativo`, `estado_antigo_e_idle`, `status_finished_e_idle`, `updated_at_futuro_nao_e_stale`, `set_number_e_planned_sets_zerados_nao_quebram` |

## Verificação manual

1. Adicionar o widget na tela inicial com um plano que tem treino hoje → mostra
   "Iniciar treino de hoje"; tocar → o treino do dia abre direto.
2. Iniciar um treino e voltar para a tela inicial → o widget passa a "Retomar
   treino"; tocar → volta para a tela de treino.
3. Finalizar/abandonar o treino → o widget volta para "Iniciar treino de hoje"
   (ou "Abrir Treinos" sem plano).
4. No relógio, abrir a gaveta de tiles com um treino em andamento → tile
   mostra dia/exercício/série; tocar → abre o app do relógio.
5. Sem treino em andamento (ou após 12 h) → tile mostra "Abrir Treinos".
6. Caminho de erro: instalação nova, sem plano → adicionar o widget não quebra
   e mostra "Abrir Treinos"; ativar modo avião e repetir 1–5.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Dependência nova (Glance):** aumenta tempo de build e tamanho do APK.
  Versão fixa `1.1.0`; o gate completo (build limpo + lint + assinatura) cobre
  a integração.
- **Widget desatualizado:** se o processo morrer, o widget mostra o último
  render até o launcher pedir `onUpdate`; as emissões de
  `inProgressSession`/`activePlans` cobrem o caso normal.
- **Plano selecionado não persistido:** `_selectedPlanId` é só memória; o
  widget usa o plano principal. Documentado e aceito nesta versão.
- **Tile obsoleto após finalizar no celular:** hoje o celular não publica
  estado terminal (ao finalizar, `_wearState.value = null` sem `publish`); a
  janela de 12 h limita o tile obsoleto. Publicar um estado terminal fica para
  H4-15/H4-16.
- **Corrida de cold start:** `startTodayWorkout()` espera `activePlan` com
  `withTimeoutOrNull(5_000)`; se o plano não carregar, mostra a mensagem de
  "sem plano" em vez de travar.
- **Rollback:** remover `WidgetContent.kt`, `TodayWidget.kt`, o XML do
  provider, o receiver do manifest, as dependências, `WearTileModel.kt`,
  `WorkoutTileService.kt`, o service do manifest do wear e as strings. Nenhum
  dado do usuário é criado ou apagado; reverter não exige migration nem
  restauração de backup.
