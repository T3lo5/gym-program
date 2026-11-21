# DESIGN.md — Sistema de Design do Treinos App

Fonte central de verdade para todas as decisões de interface do aplicativo.
Qualquer agente (humano ou IA) que implemente uma tela nova deve ler este
documento antes de escrever código de UI. Não invente cores, espaçamentos ou
tipografia: use os tokens e componentes definidos aqui.

---

## 1. Direção visual e personalidade

**Nome da identidade:** Treinos — Escuro Energético

A interface transmite: modernidade, clareza, consistência, energia e motivação,
simplicidade de utilização e confiança.

Inspirada em apps fitness modernos (Strava, Hevy, Strong):
- **Tema escuro como identidade principal** (primeira abertura, splash, marketing).
- **Tema claro** continua existindo e é acessível (WCAG AA).
- **Acentos saturados por modalidade** (verde força, laranja corrida, azul mobilidade, roxo natação).
- **Números grandes como protagonistas** (carga, reps, descanso, volume, ritmo, distância).
- **Superfícies tonais, não bordas:** cards por `surfaceContainer*` com elevação mínima.
- **Nada de aparência de dashboard administrativo:** sem grades cinzas, sem barras laterais, sem caixas de texto genéricas.

---

## 2. Paleta de cores (tokens)

Todas as cores vivem em `app/src/main/java/com/treinos/app/ui/Theme.kt`
(`ThemeColors` + `LightColors` / `DarkColors`). Nenhuma cor hardcoded nas telas.

| Token | Claro | Escuro (identidade) | Uso |
|-------|-------|---------------------|-----|
| `surface` | `#FAFAFA` | `#0E0E10` | Fundo geral |
| `surfaceContainer` | `#F1F8E9` | `#1A1A24` | Cards secundários |
| `surfaceContainerHigh` | — | `#232330` | Cards elevados / herói |
| `primary` | `#00C853` | `#00E676` | Ação primária, sucesso |
| `onPrimary` | `#FFFFFF` | `#003300` | Texto sobre primary |
| `primaryContainer` | `#DCFCE7` | `#1B5E20` | Herói, estado ativo |
| `onPrimaryContainer` | `#006400` | `#DCFCE7` | Texto em primaryContainer |
| `secondary` | `#00BFA5` | `#00BFA5` | Ação secundária |
| `onSecondary` | `#FFFFFF` | `#FFFFFF` | Texto sobre secondary |
| `secondaryContainer` | `#CCFBF1` | `#004D40` | Cards secundários tonais |
| `onSecondaryContainer` | `#004D40` | `#CCFBF1` | Texto em secondaryContainer |
| `tertiary` (energia) | `#FFFF6F00` | `#FFFF9F00` | Acento de energia, alvos importantes |
| `error` | `#B3261E` | `#EF5350` | Destrutivo, erro |
| `onError` | `#FFFFFF` | `#000000` | Texto sobre error |
| `errorContainer` | `#EF9A9A` | `#9A3A3A` | Estado de erro suave |
| `onErrorContainer` | `#4D0000` | `#FFFFFF` | Texto em errorContainer |
| `onSurface` | `#1C1B1F` | `#E6E1E5` | Texto principal |
| `onSurfaceVariant` | `#49494F` | `#CAC4D0` | Texto secundário, legendas |
| `outlineVariant` | — | `#333340` | Divisórias, campos de formulário |

### Acento por modalidade (único lugar)

Definido em `ModalityAccent` (`Theme.kt`). Consumido por chips, heróis, gráficos
e badges. **Proibido escolher cor de modalidade ad hoc na tela.**

| Modalidade | Cor | Uso |
|------------|-----|-----|
| strength | `#00C853` | Musculação |
| run | `#E65100` | Corrida |
| mobility | `#3D5AFE` | Mobilidade / alongamento |
| swim | `#7C4DFF` | Natação |

Acesso: `modalityAccentFor(kind: String): Color`.

### Dynamic color

Toggle "Usar cores do sistema" em Ajustes, default **desligado**. Quando ligado
(SDK ≥ 31), substitui a paleta da marca pelo Material You. Quando desligado,
a identidade da marca (escuro, energético) se mantém. Não vai no backup.

---

## 3. Tipografia e hierarquia

Definida em `TreinosTypography` (`Theme.kt`). Nunca fonte menor que `labelMedium`.

| Token | Tamanho | Peso | Linha | Uso |
|-------|---------|------|-------|-----|
| `displayLarge` | 57sp | Bold | 64sp | Números grandes (timer, carga, volume) |
| `displayMedium` | 45sp | Bold | 52sp | Números médios |
| `headlineLarge` | 32sp | SemiBold | 40sp | Títulos de tela / herói |
| `headlineMedium` | 28sp | SemiBold | 36sp | Títulos de card importante |
| `titleLarge` | 22sp | SemiBold | 28sp | Títulos de seção |
| `titleMedium` | 16sp | SemiBold | 24sp | Subtítulos |
| `bodyLarge` | 16sp | Regular | 24sp | Texto de corpo destaque |
| `bodyMedium` | 14sp | Regular | 20sp | Texto padrão |
| `bodySmall` | 12sp | Regular | 16sp | Legenda, auxílio |
| `labelLarge` | 14sp | Medium | 20sp | Chips, rótulos de estado |
| `labelMedium` | 12sp | Medium | 16sp | Legendas pequenas |

**Regra obrigatória:** números de leitura rápida (carga, reps, descanso, volume,
ritmo, distância) **sempre** da escala `display*` ou `headline*`.

---

## 4. Escala de espaçamento

| Token | Valor | Uso |
|-------|-------|-----|
| `space2xs` | 4dp | Elementos muito próximos dentro de um componente |
| `spaceXs` | 8dp | Ícone + texto, chips agrupados |
| `spaceS` | 12dp | Espaçamento padrão entre itens de lista |
| `spaceM` | 16dp | Padrão de padding de cards |
| `spaceL` | 20dp | Padding de cards importantes (herói, resumo) |
| `spaceXL` | 24dp | Empty states, seções grandes |
| `spaceXXL` | 32dp | Margens de seção, espaço entre blocos principais |

**Regra:** padronizar `spacedBy(12.dp)` em listas verticais; `16.dp` de padding
em cards padrão; `20.dp` em cards de destaque.

---

## 5. Sistema de bordas e arredondamentos

| Token | Valor | Uso |
|-------|-------|-----|
| `shapeSmall` | 8dp | Ícones, badges pequenos, fallback de imagem |
| `shapeMedium` | 12dp | Cards padrão, botões, campos de formulário |
| `shapeLarge` | 16dp | Cards importantes (herói, resumo pós-treino) |
| `shapeFull` | 50% | Avatares circulares |

Definido em `TreinosShapes` (`Theme.kt`).

---

## 6. Elevação e profundidade

- Superfícies tonais, **não bordas**: cards usam `surfaceContainer*` com cor de
  superfície, não elevação alta.
- Elevação mínima apenas em: `Surface(tonalElevation = 3.dp)` para faixas fixas
  (RestBar, bottom bar).
- Contornos só em campos de formulário (`OutlinedTextField`, 1dp, `outlineVariant`).
- Hierarquia vem de cor de superfície + tamanho, nunca de sombra pesada.

---

## 7. Ícones

- Biblioteca: `material-icons-core` (já no projeto). Não adicionar
  `material-icons-extended`.
- Ícones específicos do player de mobilidade: vetores locais em `AppIcons.kt`
  (Pause, SkipNext).
- Todo ícone interativo: `minimumInteractiveComponentSize()` (mínimo 48dp de
  alvo de toque).
- Ícones sempre com `contentDescription` (ou `null` quando decorativo com
  label visível ao lado).
- Tamanho padrão: 24dp; 18dp em chips de ação rápida.

---

## 8. Botões e elementos interativos

| Variante | Uso | Visual |
|----------|-----|--------|
| `Button` (preenchido, primary) | CTA principal (Iniciar, Gerar, Salvar) | `primaryContainer`, texto `onPrimaryContainer` |
| `TonalButton` | Ação secundária importante | `secondaryContainer` |
| `OutlinedButton` | Ação secundária leve, destrutiva leve | borda `outlineVariant` 1dp |
| `TextButton` | Cancelar, fechar, ação terciária | texto `primary`, sem fundo |
| `IconButton` | Navegação, fechar, mais opções | ícone 24dp, `minimumInteractiveComponentSize` |
| `BusyButton` | Carregamento inline | spinner 18dp + texto, desabilitado enquanto busy |

**Regra obrigatória:** máximo **1 botão primário preenchido por tela**. Outros
primários viram `TonalButton` ou card clicável com chevron.

---

## 9. Cards e superfícies

| Tipo | Forma | Superfície | Padding | Uso |
|------|-------|------------|---------|-----|
| Card padrão | `shapeMedium` | `surfaceContainer` | 16dp | Lista, métrica, exercício |
| Card destaque | `shapeLarge` | `primaryContainer` / `secondaryContainer` | 20dp | Herói, resumo pós-treino |
| Card secundário | `shapeMedium` | `surfaceContainerHigh` | 16dp | Config, conjunto de opções |

**Regras:**
- Nenhum card sem padding.
- `shapeSmall` para ícones de fallback de imagem (`ExerciseImage`).
- Cards de estado vazio reusam `EmptyState` (único padrão — eliminar os 3
  padrões atuais: `EmptyState`, card artesanal, texto solto).

---

## 10. Navegação

- **Bottom bar** nos 4 hubs: Treinar (0), Histórico (1), Ajustes (2),
  Progresso/Gamificação (3). Já implementada em `BottomNavigationBar.kt`.
- Ícones específicos por tela (não genéricos): `PlayArrow` (treinar),
  `History` (histórico), `Settings` (ajustes), `Favorite`/`Star` (progresso).
- Pilha mínima origem→destino via `backTarget` (ViewModel) — manter.
- `TopAppBar` (`ScreenTopBar` em `Common.kt`) com título + ícone de voltar
  apenas quando necessário.
- Navegação por `AnimatedContent` entre telas (`TreinosApp.kt`), direção
  definida por `Motion.kt` (`ScreenMotion`).

---

## 11. Campos e formulários

- `OutlinedTextField` com `shapeMedium`, borda `outlineVariant` (1dp), label
  acima do campo.
- Erro inline: `supportingText` em cor de erro, `isError = true`.
- Submit bloqueado enquanto houver erro de validação.
- Data: `DatePicker`/`TimePicker` (padrão de Medidas) — **nunca** texto livre
  para data.
- Número: teclado numérico correto (`KeyboardType.Number`).
- `maxLines = 1` + `TextOverflow.Ellipsis` onde linha única é intencional.
- Nunca `maxLines = 1` sem `overflow`.

---

## 12. Estados de interação

| Estado | Componente | Visual |
|--------|------------|--------|
| Loading | `LoadingState` | Spinner centralizado, aparece após 150ms |
| Vazio | `EmptyState` | Título + descrição + CTA opcional |
| Erro | `ErrorState` | Título (cor error), mensagem, "Tentar de novo" |
| Sucesso | Snackbar | Ícone check + mensagem curta, `StatusText(isSuccess=true)` |
| Erro persistente | `ErrorState` + retry | Título error, ação de retry |

**Regras:**
- Erro nunca é snackbar transitório de 4s sem "Tentar de novo".
- `ErrorState` é visual exclusivo de erro; sucesso continua no snackbar.
- `StatusText` para mensagens de Ajustes (sucesso = `primary` + check,
  erro = `error` + alerta).

---

## 13. Tema claro e escuro

- Escuro é o padrão (identidade da marca).
- Claro continua correto e acessível.
- `values-night/themes.xml` com `windowBackground` escuro (`#0E0E10`) — sem
  flash branco no dark mode.
- Dynamic color atrás do toggle, default desligado.
- Todo componente novo testado nos dois temas.
- Zero cor hardcoded fora de `Theme.kt`.

---

## 14. Componentes fundamentais (Common.kt)

Todos em `app/src/main/java/com/treinos/app/ui/components/Common.kt`,
mantendo as mesmas APIs para que chamadores não mudem de comportamento.

1. `ScreenTopBar(title, onBack, actions)` — top bar padrão.
2. `SectionTitle(text)` — título de seção (`titleLarge`, cor primary).
3. `MetricCard(label, value, detail)` — número protagonista em
   `headlineMedium`/`displayMedium`.
4. `LoadingState` — spinner + descrição de acessibilidade.
5. `ErrorState(title, message, onRetry)` — erro persistente e acionável.
6. `EmptyState(title, description, action)` — vazio com CTA.
7. `StatusText(text, isSuccess, announce)` — sucesso/erro com ícone.
8. `BusyButton(busy, idleText, busyText, onClick)` — carregamento inline.
9. `ConfirmationDialog(...)` — confirmação destrutiva padronizada.
10. `ModalityBadge(kind, label)` — chip de acento por modalidade.
11. `ChipGroup(options, selected, onToggle)` — filtros em `FlowRow`.
12. `KeyValueRow(label, value)` — linha label/valor.
13. `RestBar(...)` — faixa fixa de descanso (fora da lista rolável).
14. `ExerciseImage(...)` — imagem com fallback tonal da modalidade.
15. `ExerciseDetailSheet(...)` — detalhe do exercício em modal bottom sheet.

---

## 15. Regras obrigatórias (checklist por tela)

1. ✅ Usar apenas tokens de `Theme.kt` — zero cor hardcoded.
2. ✅ Máximo 1 botão primário preenchido por tela.
3. ✅ Números de leitura rápida em escala `display*`/`headline*`.
4. ✅ Espaçamento: `spacedBy(12.dp)` listas, `16.dp` padding cards, `20.dp`
   destaque.
5. ✅ Forma: `shapeMedium` padrão, `shapeLarge` destaque, `shapeSmall` ícones.
6. ✅ Todo ícone interativo com `minimumInteractiveComponentSize()`.
7. ✅ Todo ícone com `contentDescription` (ou `null` se decorativo).
8. ✅ Data via `DatePicker`/`TimePicker`, nunca texto livre.
9. ✅ `maxLines = 1` sempre com `TextOverflow.Ellipsis`.
10. ✅ `ErrorState` para erro persistente; snackbar só para sucesso/transitório.
11. ✅ `EmptyState` sempre com CTA.
12. ✅ Teste nos dois temas (claro e escuro).
13. ✅ Contraste WCAG AA em todos os pares de cor.
14. ✅ Acento por modalidade só via `ModalityAccent`.

---

## 16. Anti-patterns (evitar)

- ❌ Cor hardcoded em qualquer tela.
- ❌ Mais de 1 botão primário preenchido na mesma tela.
- ❌ `maxLines = 1` sem `TextOverflow.Ellipsis`.
- ❌ Data em texto livre (sem DatePicker).
- ❌ Erro como snackbar de 4s sem ação de retry.
- ❌ 3 padrões diferentes de estado vazio (usar `EmptyState` sempre).
- ❌ 7 confirmações destrutivas manuais fora do `ConfirmationDialog`.
- ❌ Espaçamentos fora do padrão (4dp no Generate vs 12dp no resto).
- ❌ Ícone sem `minimumInteractiveComponentSize()`.
- ❌ Cor de modalidade escolhida ad hoc na tela (usar `ModalityAccent`).
- ❌ Número importante (carga, descanso, volume) em `bodyMedium` ou menor.

---

## 17. Exemplo de aplicação (card de exercício)

```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cabeçalho: nome do exercício + badge de modalidade
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Supino reto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ModalityBadge(kind = "strength", label = "Academia")
        }
        // Número protagonista: carga
        Text(
            text = formatKg(60.0), // "60,0 kg"
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        // Linha secundária
        KeyValueRow(
            label = "Séries",
            value = "4 × 10",
        )
    }
}
```

---

## 18. Arquivos que implementam o design system

| Arquivo | Responsabilidade |
|---------|------------------|
| `app/src/main/java/com/treinos/app/ui/Theme.kt` | Cores, tipografia, formas, `ModalityAccent`, `TreinosTheme` |
| `app/src/main/java/com/treinos/app/ui/Contrast.kt` | `StatusColors` (mapa estado→cor), utilitários de contraste |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | Componentes compartilhados (ScreenTopBar, MetricCard, etc.) |
| `app/src/main/java/com/treinos/app/ui/components/AppIcons.kt` | Ícones vetores locais (mobility player) |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseImage.kt` | Imagem de exercício com fallback tonal |
| `app/src/main/java/com/treinos/app/ui/Motion.kt` | Direção e duração de transição de tela |
| `app/src/main/res/values/themes.xml` + `values-night/` | Window background por tema |
| `app/src/main/res/values/colors.xml` | Cores de window background |

---

## 19. Aprovações necessárias

Antes de iniciar a implementação, confirmar com o dono:

1. **Tema escuro como identidade padrão** (não apenas values-night).
2. **Paleta de cores proposta** (verde `#00C853`/`#00E676`, energia laranja `#FFFF6F00`/`#FFFF9F00`, acentos por modalidade).
3. **Dynamic color desligado por default** (toggle em Ajustes).
4. **Nomes dos tokens** e valores concretos (cores, espaçamento, tipografia).
5. **Escopo da Fase 1 do plano de implementação** (fundação visual + tokens).

---

## 5. PLANO DE IMPLEMENTAÇÃO

### Fase 1 — Fundação visual e tokens (Risco: Baixo)

**Objetivo:** Definir a paleta, tipografia, espaçamento, formas e `ModalityAccent` em `Theme.kt`; adicionar `values-night/themes.xml` com window background escuro; toggle dynamic color default desligado.

**Arquivos afetados:**
- `app/src/main/java/com/treinos/app/ui/Theme.kt`
- `app/src/main/java/com/treinos/app/ui/Contrast.kt`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/themes.xml`

**Critérios de conclusão:**
- `ContrastTest` verde nos dois temas.
- Nenhuma cor hardcoded vazando para telas (auditada).
- App abre com tema escuro por padrão.

---

### Fase 2 — Componentes compartilhados (Risco: Baixo)

**Objetivo:** Redesenhar `Common.kt` na nova base (MetricCard, StatusText, BusyButton, EmptyState, ErrorState, ModalityBadge, ChipGroup, ScreenTopBar, SectionTitle) mantendo APIs. Redesenhar `ExerciseImage` e `ExerciseDetailSheet`.

**Arquivos afetados:**
- `app/src/main/java/com/treinos/app/ui/components/Common.kt`
- `app/src/main/java/com/treinos/app/ui/components/ExerciseImage.kt`
- `app/src/main/java/com/treinos/app/ui/components/ExerciseDetailSheet.kt`

**Critérios de conclusão:**
- Todos os componentes usam tokens do tema.
- `EmptyState` é o único padrão de estado vazio.
- `ConfirmationDialog` é o único padrão de confirmação destrutiva.

---

### Fase 3 — Navegação (Risco: Médio)

**Objetivo:** Alinhar BottomNavigationBar com ícones específicos, TopAppBar consistente, transições de tela via Motion.kt.

**Arquivos afetados:**
- `app/src/main/java/com/treinos/app/ui/components/BottomNavigationBar.kt`
- `app/src/main/java/com/treinos/app/ui/components/Common.kt` (ScreenTopBar)
- `app/src/main/java/com/treinos/app/ui/TreinosApp.kt`
- `app/src/main/java/com/treinos/app/ui/Motion.kt`

**Critérios de conclusão:**
- Ícones específicos por tela (não genéricos).
- Transições com direção (FORWARD/BACKWARD/NONE).
- Pilha origem→destino preservada.

---

### Fase 4 — Telas principais (Risco: Médio-Alto)

**Objetivo:** Redesenhar Home, Workout, RunWorkout, Mobility sobre a nova base.

**Arquivos afetados:**
- `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt`

**Critérios de conclusão:**
- Herói "Hoje" em superfície tonal da modalidade com número grande.
- RestBar com contagem `displayMedium`.
- Números de leitura rápida em escala display/headline.
- Nenhuma função removida ou movida.

---

### Fase 5 — Telas secundárias (Risco: Médio)

**Objetivo:** Redesenhar Generate, History, Settings, LogActivity, Measurements, Gamification.

**Arquivos afetados:**
- `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/MeasurementsScreen.kt`
- `app/src/main/java/com/treinos/app/ui/screens/GamificationScreen.kt`

**Critérios de conclusão:**
- Máximo 1 botão primário por tela.
- Formulários com DatePicker/TimePicker, erro inline.
- Generate com CTA fixo em pílula cheia.
- Settings com seções colapsáveis e toggle dynamic color.

---

### Fase 6 — Wear OS e acabamento (Risco: Médio)

**Objetivo:** Alinhar paleta do pulso, scroll em telas de força, háptico de fim de descanso, splash/ícone coerentes.

**Arquivos afetados:**
- `wear/src/main/java/com/treinos/wear/ui/WorkoutWearActivity.kt`
- `wear/src/main/java/com/treinos/wear/ui/WearTimer.kt`
- `app/src/main/res/` (splash, ícone)

**Critérios de conclusão:**
- Wear com mesma identidade (escuro, números grandes, acento por modalidade).
- Leitura ao sol — contraste alto sobre preto puro.
- Nenhuma dependência nova adicionada.

---

### Fase 7 — Revisão final e gate (Risco: Baixo)

**Objetivo:** Rodar `./scripts/verify.sh` completo, ajustar testes, correção de resíduos visuais.

**Critérios de conclusão:**
- `./scripts/verify.sh` verde.
- `DESIGN.md` seguido em todas as telas.
- Lista de anti-patterns verificada (nenhum presente).

---

## Lista de decisões que precisam da sua aprovação

1. **Tema escuro como identidade padrão** do app (primeira abertura, splash, marketing), com tema claro como alternativa acessível.
2. **Paleta de cores** proposta (verde marca `#00C853`/`#00E676`, energia `#FFFF6F00`/`#FFFF9F00`, acentos por modalidade).
3. **Dynamic color desligado por default** (toggle em Ajustes, SDK 31+).
4. **Valores concretos dos tokens** (cores, espaçamento, tipografia, formas) como definidos acima.
5. **Escopo da Fase 1** (fundação visual + tokens, sem mudar telas ainda).

Aguardo sua aprovação da direção visual antes de iniciar a implementação da Fase 1.
