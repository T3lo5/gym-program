# H6-3 — i18n e TalkBack completos (a camada que o seletor controla)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-3` (Horizon 6 — Polimento UX/UI; ver [auditoria](../../auditoria-ux-ui-2026-11-18.md), P3) |
| MoSCoW | `S` |
| Versão alvo | `1.11.0` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-18) |
| Implementação | `pendente` |
| Depende de | H6-1 (seletor de idioma visível) para o teste manual em EN; implementável antes |

## Objetivo

Com o idioma English, **tudo** fica em inglês — incluindo o que o TalkBack
fala, a corrida com GPS, as datas do calendário e as mensagens de validação —
fechando a promessa de paridade da H3-37/H3-52 que hoje quebra numa camada
inteira fora de recursos.

## Fora de escopo

- Traduzir o catálogo de exercícios (H3-36 já cobre `namePt`/`instructionsPt`).
- Mudar a copy em português (só extrair; texto idêntico).
- O seletor de idioma em si (H6-1) e a formatação visual de números grandes
  (H6-4).
- Backend (mensagens de API já chegam traduzidas pelo `uiErrorMessage`).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/A11y.kt` | Templates de descrição deixam de nascer em PT: funções passam a receber os trechos resolvidos (ou o template) de `strings.xml` — `exerciseContentDescription`, `setContentDescription`, `progressStateDescription`, `restStateDescription`, `reviewStateDescription`; `formatNumber` sai do arquivo (vira parâmetro formatado) |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | Chaves novas: `a11y_exercise_format`, `a11y_set_format`, `a11y_reps_format`, `a11y_kg_format`, `a11y_rpe_format`, `a11y_progress_format`, `a11y_rest_state_format`, `a11y_review_due_format`, `a11y_review_next_format`, `a11y_set_completed_suffix`, rótulos de músculo/equipamento (`muscle_*`/`equipment_*`), `home_sessions_per_week_format`, `history_day_a11y_format` (plurais), `run_*` (rastreio/ritmo/fases), mensagens de validação movidas para recursos |
| `app/src/main/java/com/treinos/app/ui/Formatters.kt` | Formatação de **data** usa `appLocale` (`DateTimeFormatter.ofLocalizedDate/…`); números de carga/volume mantêm locale fixo pt-BR (decisão documentada abaixo) |
| `app/src/main/java/com/treinos/app/ui/CalendarMonth.kt` | Nome do mês e dias da semana via `appLocale` (`TextStyle`/`DateTimeFormatter` com locale) |
| `app/src/main/java/com/treinos/app/ui/WeeklySchedule.kt` | Rótulos de músculo do resumo saem das constantes PT para recursos (chamador resolve) |
| `app/src/main/java/com/treinos/app/ui/ProfileValidator.kt` | Validador retorna código (`ProfileErrorKind` enum) e a UI resolve o recurso; `Result` mantém compatibilidade |
| `app/src/main/java/com/treinos/app/ui/RunLog.kt`, `ActivityLog.kt`, `ComplementaryValidator.kt` | Mesmo padrão: erro vira enum/const de código; a tela resolve o recurso (fim do `startsWith("Distância")`) |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | Bloco de GPS ("Rastreando corrida", "Ritmo:", fases, "Parar/Iniciar rastreamento") para recursos; roteamento de erro por código |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Data do dia com locale do app + `<plurals>` na descrição; dia sem treino com semântica mínima ("Hoje, sem treino") em vez de `clearAndSetSemantics` |
| `app/src/main/java/com/treinos/app/ui/ExerciseImage.kt` + `ExerciseDetailSheet.kt` | `contentDescription` do fallback e rótulos de músculo/equipamento via recursos |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Switch "backend remoto" com `Modifier.toggleable(role = Role.Switch)` na Row + `stateDescription`; "Espaço:"/"Segredo:" para recursos |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | "Temporizador …" do liveRegion, "Movimento X de Y", lados reusando `mobility_side_left/right` |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Plural caseiro do herói → `<plurals>` |
| `wear/src/main/java/com/treinos/wear/ui/WearFormat.kt` + `WorkoutWearActivity.kt` | `formatWeightKg`/`formatLastSet`/`formatarCarga` com `NumberFormat` do locale e unidade via recurso; corrida do pulso com `"%.1f".format(locale)` explícito |
| `wear/src/main/res/values/strings.xml` + `values-en/strings.xml` | Unidade "kg" e rótulos de fase se faltarem |

## Modelo de dados / contrato

- Nenhuma migration, nenhum contrato novo. A única mudança de assinatura é
  interna: validadores puros passam a retornar **código de erro** (enum) em
  vez de texto; os `Result`/`Validation` existentes ganham o campo sem quebrar
  chamadores (o texto atual continua disponível para quem já usa em teste).
- `appLocale` (`Formatters.kt:18`) passa a ser a fonte de locale para data e
  formatação localizável; é atualizado pelo per-app locale existente (H3-37).

## Regras e casos de borda

1. **Rótulos = recursos; datas = locale.** Texto de UI vem de `strings.xml`
   (paridade por `StringParityTest`); formatação de data/hora usa `appLocale`;
   **números de treino (carga/volume/1RM) mantêm locale fixo pt-BR** — decisão
   preservada da H3-33/H4-9 ("62,5 kg" é convenção do usuário), documentada no
   `Formatters.kt`.
2. **Validador puro não conhece `Context`.** Todo validador retorna código
   (`enum`); a UI resolve o recurso. Proibido casar mensagem por prefixo
   (`startsWith`) — o roteamento de erro da corrida passa a usar o código.
3. **Plurais sempre em `<plurals>`** quando a quantidade flexiona (dia do
   histórico, herói da Home).
4. **TalkBack**: nenhum template de descrição nasce em PT no código; os
   testes de A11y rodam em pt-BR **e** en-US onde a paridade importa.
5. **Wear**: formatação numérica segue o locale do app (EN → "20.0 kg");
   unidade via recurso; `"%.1f".format(...)` sempre com `Locale` explícito.
6. **Dia sem treino no calendário**: continua sem card clicável, mas com
   semântica mínima anunciável ("Hoje, sem treino" quando for hoje).
7. Nenhuma função removida; textos PT idênticos aos atuais.

## Critérios de aceite

1. **Dado** o idioma English, **quando** o TalkBack lê o card de exercício,
   **então** anuncia "Exercise Squat", "Set 2, 10 repetitions, 60 kilograms".
2. **Dado** o idioma English, **quando** corro com GPS, **então** a tela mostra
   "Tracking run", "Pace:", "Warm-up/Sprint/Recovery" — nada em português.
3. **Dado** o idioma English, **quando** abro o Histórico, **então** o mês é
   "November 2026" e os dias "Mon…Sun".
4. **Dado** peso inválido no formulário de geração em EN, **quando** salvo,
   **então** o erro inline aparece em inglês.
5. **Dado** o TalkBack ativo, **quando** foco o switch "backend remoto",
   **então** anuncia o rótulo + estado ("Backend remoto, ativado").
6. **Dado** o relógio em EN, **quando** vejo a última série, **então** mostra
   "20.0 kg × 10 reps".
7. **Dado** o dia de hoje sem treino no calendário, **quando** o TalkBack
   passa por ele, **então** anuncia "Hoje, sem treino" (ou "Today, no
   workouts").

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/StringParityTest.kt` | cobre as chaves novas (paridade automática existente) |
| `app/src/test/java/com/treinos/app/ui/A11yTest.kt` | `descricoes_resolvidas_do_recurso_en` (Robolectric locale en) |
| `app/src/test/java/com/treinos/app/ui/RunLogTest.kt` | ajustados ao enum de erro (`distancia_invalida_codigo`, `pace_invalido_codigo`) |
| `app/src/test/java/com/treinos/app/ui/ActivityLogTest.kt` | códigos de erro do registro avulso |
| `app/src/test/java/com/treinos/app/ui/ProfileValidatorTest.kt` | `erro_por_codigo_nao_por_texto` |
| `app/src/test/java/com/treinos/app/ui/CalendarMonthTest.kt` | `mes_e_dias_seguem_app_locale` |
| `app/src/test/java/com/treinos/app/ui/FormattersTest.kt` | `data_localizada_en`, `carga_mantem_pt_br` |
| `wear/src/test/java/com/treinos/wear/ui/WearFormatTest.kt` | `format_last_set_locale_en`, `formatar_carga_locale_en` |
| `app/src/test/java/com/treinos/app/ui/SettingsScreenTest.kt` | `switch_remoto_anuncia_rotulo_e_estado` |

## Verificação manual

1. Trocar para English e percorrer: treino (TalkBack), corrida com GPS,
   Histórico (calendário e descrições), Geração (erro de peso), Ajustes
   (switch e sync space), Mobilidade, Registro avulso.
2. Voltar para PT e conferir que a copy não mudou.
3. TalkBack: percorrer o treino completo (série salva, descanso, PR).
4. Relógio em EN: última série, carga do editor, corrida.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Validadores mudando de contrato** quebram testes existentes que esperam
  texto — ajuste mecânico, coberto pela lista acima; rollback por arquivo.
- **`appLocale` não atualizado em algum caminho** (processo morto, shortcut):
  o default cai no locale do aparelho — degradação aceitável, não crash.
- **Strings dinâmicas em funções puras sem `Context`**: resolver o template na
  composição (padrão H3-52 do RestBar) — nunca `Context` dentro de lógica.
- Rollback: cada arquivo é independente; nenhuma migration.
