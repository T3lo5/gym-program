# H6-7 — Modernização visual: escuro + energético, redesenho completo

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-7` (Horizon 6; pedido do dono em 2026-11-18 — "visual mais moderno") |
| MoSCoW | `S` |
| Versão alvo | `1.12.0` |
| Spec aprovada em | `2026-11-18` (aprovada pelo dono — pedido de visual mais moderno) |
| Implementação | `concluída` |
| Depende de | H6-2, H6-4 e H6-5 concluídas (evita retrabalho nas mesmas telas) |

## Objetivo

O app ganha uma identidade visual **escura, energética e de treino sério** —
no estilo de Strava/Hevy: tema escuro como identidade, tipografia grande e
numerada, acento de cor por modalidade, superfícies tonais em vez de bordas —
redesenhando todas as telas sobre a nova base **sem remover nem esconder
nenhuma função**.

## Fora de escopo

- Mudar comportamento, fluxo ou função de qualquer tela (isso é H6-1..H6-6).
- Trocar de toolkit (segue Compose + Material 3; nada de Compose Multiplatform
  ou lib de UI nova).
- Novas funções visuais que dependam de dado novo (ex.: anel de progresso
  semanal já existe como dado? só se o dado já existir).
- Backend, Room, contrato — nada disso muda aqui.

## Direção de design (decisões do dono registradas)

1. **Escuro primeiro**: o tema escuro é a identidade (não só o `values-night`
   de hoje); o tema claro continua existindo e segue acessível, mas o escuro
   é o "rosto do app" (splash, ícone, marketing, screenshots).
2. **Energético**: acentos saturados usados com propósito — um acento por
   modalidade, números grandes em cor de destaque, gradiente sutil no herói.
3. **Números protagonistas**: carga, volume, descanso, ritmo e distância são
   os maiores elementos das telas (consistente com H6-4).
4. **Superfícies tonais, não bordas**: cards por `surfaceContainer*` com
   elevation mínima; contornos só em campos de formulário.
5. **Dynamic color vira escolha**: o padrão é a paleta da marca; "Usar cores
   do sistema" vira toggle em Ajustes (SDK 31+), default **desligado**.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/Theme.kt` | Paleta escura redefinida (fundo `#0E0E10`, superfícies `surfaceContainer*` em degraus, primary verde da marca saturado, tertiary como acento de energia); `ColorScheme` por modalidade (`ModalityAccent`: strength/run/mobility/swim) exposto via `CompositionLocal`; dynamic color atrás do toggle |
| `app/src/main/java/com/treinos/app/ui/Type.kt` (novo ou dentro de Theme.kt) | Escala tipográfica com números protagonistas: `displayLarge/medium` para timers/carga/volume, `titleLarge` mais pesado nos títulos, `labelLarge` para chips/estados |
| `app/src/main/java/com/treinos/app/ui/Contrast.kt` | Pares novos conferidos (acento por modalidade × fundo, número destaque × superfície) — WCAG AA mantido |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | `MetricCard`, `StatusText`, `BusyButton`, `EmptyState`, `ChipGroup` redesenhados na nova base (mesmas APIs — chamadores não mudam de comportamento); `ModalityBadge` novo (chip de acento por modalidade) |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Herói "Hoje" em destaque máximo (superfície tonal da modalidade, número grande de séries/volume, gradiente sutil); streak e medidas em cards compactos; chips de plano em pílulas |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Card de exercício com número de carga/séries protagonista, cabeçalho com `ModalityBadge`, RestBar com contagem `displayMedium` (H6-4) sobre superfície da modalidade, progresso da sessão como barra fina no topo |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Calendário com dias em pílulas e "hoje" em acento; métricas em `MetricCard` grandes; gráficos com gridlines mais visíveis (H6-5) e cor de série por modalidade |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Programas prontos em cards com capa tonal por objetivo; formulário com seções mais respiro; CTA fixo em pílula cheia |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | Métricas de GPS grandes (ritmo/distância em `displayMedium`), mapa/rota em destaque quando houver, fase do intervalo com acento da corrida |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | Player com número `displayLarge` (já próximo do alvo), acento de mobilidade no progresso |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Seções com cabeçalhos menores e cards tonais; toggle "Usar cores do sistema" |
| `app/src/main/java/com/treinos/app/ui/screens/MeasurementsScreen.kt`, `LogActivityScreen.kt` | Mesma base (cards tonais, números grandes, acentos) |
| `app/src/main/java/com/treinos/app/ui/ExerciseImage.kt` | Placeholder tonal da modalidade em vez de letra cinza |
| `app/src/main/res/values/themes.xml` + `values-night/themes.xml` | Splash/windowBackground coerentes com a nova paleta |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` + `AppPreferences` | `useDynamicColor` (default `false`) |
| `wear/src/main/java/com/treinos/wear/ui/` | Paleta do pulso alinhada (escuro já é padrão no Wear); acento por modalidade nos textos-chave |
| Testes | `ContrastTest` estendido; testes de tela existentes continuam verdes (comportamento não muda) |

## Modelo de dados / contrato

Nada: sem migration, sem contrato, sem backend. `AppPreferences.useDynamicColor`
é chave nova de prefs (sem bump de schema de backup — prefs não vão no backup).

## Fases (gate verde ao fim de cada uma, padrão H3-37)

1. **Fase 1 — Fundação**: paleta escura + escala tipográfica + formas +
   `ModalityAccent` + toggle de dynamic color. Telas mudam de cara sem mudar
   de layout. Gate + `ContrastTest` estendido.
2. **Fase 2 — Componentes**: `Common.kt` + `ExerciseImage` + `ModalityBadge`
   na nova base. Gate.
3. **Fase 3 — Telas de treino**: Home, Workout, Run, Mobility. Gate.
4. **Fase 4 — Telas de informação**: History, Generate, Measurements,
   LogActivity, Settings. Gate.
5. **Fase 5 — Pulso e acabamento**: wear alinhado, splash/ícone coerentes,
   varredura de resíduos (cantos/paddings fora do padrão). Gate completo.

## Regras e casos de borda

1. **Nenhuma função muda de lugar ou desaparece** — o redesenho é de
   apresentação; os testes de tela existentes (Home/History/Workout/Generate/
   Settings/Measurements/LogActivity/Run/Mobility) continuam verdes com
   ajustes apenas de gancho visual.
2. **Contraste AA em tudo** — acento por modalidade só onde o par passa no
   `ContrastTest`; número de destaque nunca abaixo de 4.5:1 sobre a
   superfície (texto grande ≥ 3:1 quando ≥ 18 sp bold).
3. **Um acento por modalidade**, mapeado em um único lugar
   (`ModalityAccent`), consumido por chips/herói/gráficos/badges — proibido
   escolher cor de modalidade ad hoc na tela.
4. **Escuro é o padrão, claro continua correto** — todo componente novo é
   testado nos dois temas; nada de cor hardcoded fora de `Theme.kt` (regra
   já auditada como mantida).
5. **Dynamic color desligado por default**: ligado, substitui a paleta da
   marca (comportamento atual); desligado, identidade própria — o toggle é
   por instalação, sem sync/backup.
6. **Tipografia**: números de leitura rápida sempre da escala display/headline
   (H6-4); nunca fonte menor que `labelMedium`; `maxLines`/`overflow` onde a
   linha única é intencional (regra da auditoria 2026-11-07 3.4).
7. **Motion**: as transições existentes (H3-2) continuam; novos elementos
   entram com fade/slide curto respeitando `animatorScale`.
8. **Wear**: leitura ao sol — acentos com contraste alto sobre preto puro;
   nada de depender de cor sozinho (sempre texto + cor).

## Critérios de aceite

1. **Dado** o app recém-instalado, **quando** abro pela primeira vez,
   **então** o tema escuro da marca é o rosto do app e a Home mostra o herói
   com acento da modalidade de hoje.
2. **Dado** o toggle "Usar cores do sistema" desligado (default), **quando**
   uso o app em SDK 31+, **então** a paleta da marca se mantém; ligado,
   volta o dynamic color atual.
3. **Dado** um treino de corrida, **quando** abro Home/treino/histórico,
   **então** o acento de corrida é o mesmo nos três lugares.
4. **Dado** qualquer tela, **quando** comparo antes/depois, **então** todas
   as ações, dados e caminhos continuam existindo (checklist por tela na
   verificação manual).
5. **Dado** o tema claro, **quando** uso o app, **então** nada quebra e o
   contraste passa (ContrastTest estendido).
6. **Dado** fonte 2x e tela pequena, **quando** percorro as telas,
   **então** nada corta (regras H3-34/H6-5 preservadas).
7. **Dado** o pulso, **quando** treino, **então** a identidade é a mesma
   (escuro, números grandes, acento da modalidade).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/ContrastTest.kt` | pares novos: acento por modalidade × superfícies, número destaque × fundo, claro e escuro |
| `app/src/test/java/com/treinos/app/ui/ThemeTest.kt` (novo, JVM/Robolectric) | `modality_accent_mapa_unico`, `dynamic_color_default_desligado`, `toggle_persiste` |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | herói com acento da modalidade (gancho de teste), funções intactas |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | RestBar/estilos novos sem quebrar hooks existentes |
| `app/src/test/java/com/treinos/app/ui/SettingsScreenTest.kt` | `toggle_cores_do_sistema_aparece_e_persiste` |
| Testes de tela existentes | continuam verdes (ajuste mecânico de gancho quando o visual muda o texto/semântica) |

## Verificação manual (checklist por tela — nada pode faltar)

1. **Home**: herói, chips de plano, streak, medidas, permissões, revisão,
   deload, resumo pós-treino, geração pendente, rodapé sem plano.
2. **Treino**: alvo, séries, descanso, superset, troca, notas, anilhas, PR,
   abandonar/finalizar, editar/excluir série.
3. **Histórico**: calendário, filtros, métricas, gráficos, drill-down,
   relatório Mês/Ano, edição de séries.
4. **Geração**: programas, config restaurada, complementar, "Mais opções",
   overlay, erro com retry.
5. **Corrida/Mobilidade/Registro avulso**: métricas, intervalos, formulários.
6. **Ajustes/Medidas**: todas as seções, backup, sync, idioma, permissões,
   medidas (ordem/edição/foto).
7. **Temas**: escuro (padrão), claro, dynamic color ligado/desligado, fonte
   2x, TalkBack nos elementos redesenhados.
8. **Pulso**: força/corrida/mobilidade/standalone/tile no novo visual.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Regressão visual em massa**: fases com gate verde isolam o problema;
  cada fase é um commit reversível.
- **Contraste do acento energético falhar AA**: o `ContrastTest` estendido
  bloqueia o merge da fase — ajustar o tom do acento, nunca baixar o teste.
- **Testes de tela quebrando por gancho visual**: ajuste mecânico; se um
  teste quebra por comportamento, é bug do redesenho — corrigir o código.
- **Dynamic color default desligado** muda o app para quem gostava do
  esquema do sistema: o toggle mantém a opção (1 toque).
- Rollback: cada fase reverte isoladamente; nenhuma migration/contrato.
