# H6-4 — Consistência visual e componentes (continuação da H3-32)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-4` (Horizon 6 — Polimento UX/UI; ver [auditoria](../../auditoria-ux-ui-2026-11-18.md), P4) |
| MoSCoW | `S` |
| Versão alvo | `1.11.0` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-18) |
| Implementação | `pendente` |
| Depende de | Nada (coordenar o card de exercício com H6-2 — mesmo arquivo) |

## Objetivo

Um só sistema visual onde ele desviou: toda confirmação destrutiva usa o
mesmo componente, números de leitura rápida têm tamanho de leitura rápida,
só a ação da tela é primária, a cor de estado significa a mesma coisa em
qualquer tela e o estado vazio tem um padrão único — sem remover nenhuma
função.

## Fora de escopo

- Comportamento dos fluxos de confirmação (só o componente que os apresenta).
- Comportamento do descanso (pausa/estende/rolagem do superset — H6-2).
- Tradução/extração de strings (H6-3) — os literais citados aqui mudam de
  componente, não de texto.
- Criar design system novo, dependência nova ou mudar tokens existentes.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | `ConfirmationDialog` ganha `isDestructive = false` renderizado com `confirmTint = primary` (já suportado pela assinatura); novo `StatusColors` (mapa estado→cor único e testável: `inProgress = tertiary`, `success = primary`, `error = error`) |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | 3 AlertDialogs manuais → `ConfirmationDialog` (excluir série `:583-609`, finalizar `:615-640`, abandonar `:645-671`); contagem do descanso no RestBar para `headlineLarge` + frase de estado em `labelMedium`; ações secundárias do card (Trocar/Superset/Nota) em linha compacta de TextButton — Registrar/Repetir/Aplicar continuam como estão |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | 2 AlertDialogs → `ConfirmationDialog` (excluir plano `:490-513`, abandonar `:1068-1097`); selo "em andamento" usa `StatusColors.inProgress` (hoje `secondaryContainer`) |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | AlertDialog de descarte → `ConfirmationDialog` (`:510-539`); métricas de GPS ao vivo no padrão de número grande (headline + label) |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | AlertDialog de descarte → `ConfirmationDialog` (`:125-151`); botão "Usar" das rotinas vira `TonalButton` (`:239-244`) |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | "Usar este programa" nos 4 ProgramCards vira `TonalButton` (`:933-942`); remover o `padding(horizontal = 16.dp)` duplicado do card "perfil salvo" (`:823` vs `:398`) |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Selo/ponto "em andamento" usa `StatusColors.inProgress` (`:751`, `:953-954`); dia sem registro usa `EmptyState` (variante de mensagem) em vez de Text solto (`:278-293`); `RoundedCornerShape(8.dp)` literal → `MaterialTheme.shapes.small` (`:711`) |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Card de restauração com container `surfaceContainerHigh` (hoje `tertiaryContainer` decorativo, `:781-783`); "versão desconhecida" mantém `tertiary` como texto de aviso |
| `app/src/main/java/com/treinos/app/ui/screens/MeasurementsScreen.kt` | Card vazio artesanal → `EmptyState` com `action` (`:138-162`) |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | Campo "Quando" vira campo somente-leitura + `DatePicker`/`TimePicker` (padrão de `MeasurementsScreen.kt:260-277,388-415`), mantendo validação inline e o rascunho da H3-56 |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | Rótulos dos diálogos migrados (textos idênticos aos atuais); `empty_*` para os novos usos de `EmptyState` — paridade obrigatória |

## Modelo de dados / contrato

Nada muda: sem migration, sem contrato, sem backend. Mudança mecânica de
componentes e tokens com testes de regressão de tela.

## Regras e casos de borda

1. **Zero `AlertDialog` de confirmação fora do `ConfirmationDialog`** — grep
   por `AlertDialog(` em `ui/` só aceita ocorrências dentro de
   `Common.kt`/`SetEditDialog`/diálogos de formulário (não-confirmação).
2. **`Button` preenchido só na ação da tela**: herói "Iniciar"/CTA "Gerar"/
   ações primárias de formulário; botão dentro de card é `TonalButton` ou o
   card é clicável. Exceção documentada: `SetEditDialog` "Salvar" (diálogo).
3. **Cor de estado vem do mapa**: nenhum composable escolhe
   `secondaryContainer`/`tertiary` para "em andamento" por conta própria;
   `StatusColors` é a única fonte (testada).
4. **Número grande**: contagem de descanso, timer de mobilidade e métricas de
   GPS usam o mesmo par (número em `headlineLarge`/`displayMedium` + estado em
   `labelMedium`); o RestBar mantém `liveRegion`/`stateDescription` (H3-34).
5. **Ações do card de exercício**: nenhuma ação sai de existência — Trocar/
   Superset/Nota ficam alcançáveis em linha compacta; alvo de toque ≥ 48 dp.
6. **EmptyState único**: Home (sem plano), Medidas (sem medidas) e Histórico
   (dia sem registro) usam o componente com `action` onde há CTA.
7. **DatePicker no registro avulso**: campo somente-leitura abre o picker;
   digitação manual deixa de existir; o rascunho persistido (H3-56) guarda a
   data escolhida; validação e erro inline continuam.
8. Textos PT idênticos aos atuais (extração é da H6-3; aqui só migração de
   componente).

## Critérios de aceite

1. **Dado** qualquer ação destrutiva (excluir série, finalizar, abandonar,
   excluir plano, descartar corrida/rotina), **quando** a confirmação abre,
   **então** é o `ConfirmationDialog` com o mesmo visual e ordem de botões.
2. **Dado** o descanso em execução, **quando** olho a faixa de longe,
   **então** o número é o elemento dominante (≥ `headlineLarge`).
3. **Dado** a tela Generate com os 4 programas, **quando** olho a tela,
   **então** só o CTA "Gerar treino" é preenchido.
4. **Dado** o card de exercício, **quando** o exercício tem troca/superset/
   nota disponíveis, **então** as ações secundárias ficam em uma linha
   compacta e todas continuam funcionando.
5. **Dado** a Home e o Histórico, **quando** há sessão em andamento,
   **então** os dois usam a mesma cor para o estado.
6. **Dado** o registro avulso, **quando** toco no campo "Quando",
   **então** o DatePicker abre e a data escolhida persiste no rascunho.
7. **Dado** fonte 2x, **quando** abro o RestBar e as métricas de GPS,
   **então** nada corta e nada sai do alinhamento.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` | `confirmacoes_usam_confirmation_dialog`, `acoes_secundarias_em_linha_compacta` |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | `abandonar_usa_confirmation_dialog`, `selo_em_andamento_cor_do_mapa` |
| `app/src/test/java/com/treinos/app/ui/RunWorkoutScreenTest.kt` | `descartar_usa_confirmation_dialog` |
| `app/src/test/java/com/treinos/app/ui/MobilityScreenTest.kt` (novo se não houver) | `descartar_rotina_usa_confirmation_dialog`, `usar_rotina_e_tonal` |
| `app/src/test/java/com/treinos/app/ui/GenerateScreenTest.kt` | `programa_usa_tonal_button`, `perfil_salvo_sem_padding_duplo` |
| `app/src/test/java/com/treinos/app/ui/HistoryScreenTest.kt` | `dia_sem_registro_usa_emptystate` |
| `app/src/test/java/com/treinos/app/ui/components/StatusColorsTest.kt` (novo, JVM) | `mapa_de_cor_estavel_por_estado` |
| `app/src/test/java/com/treinos/app/ui/LogActivityScreenTest.kt` | `quando_abre_datepicker_e_persiste_no_rascunho` |

## Verificação manual

1. Percorrer todas as confirmações destrutivas e conferir o mesmo visual.
2. Treino em luz forte: contagem do descanso legível de longe.
3. Generate: hierarquia de botões (1 primário).
4. Card de exercício: todas as ações acessíveis na linha compacta.
5. Tema claro/escuro e dynamic color: cores de estado coerentes.
6. Registro avulso: escolher data pelo picker, girar, conferir rascunho.
7. Fonte 2x em RestBar/GPS/Mobilidade.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Diff mecânico grande** (7 diálogos + botões + cores): mudança de baixo
  risco, mas toque em arquivos compartilhados com H6-2 — implementar em
  sub-commits por achado e coordenar a ordem.
- **Migração de diálogo** pode mudar o gancho de teste (`onNodeWithText`) —
  os testes listados cobrem; nenhum comportamento muda.
- **DatePicker no LogActivity** mexe no rascunho persistido (H3-56): manter o
  codec existente e só trocar a origem do valor; rollback por arquivo.
