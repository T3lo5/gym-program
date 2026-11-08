# H3-33 — Tema, motion e formatação de números

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-33` |
| MoSCoW | `S` |
| Spec aprovada em | `aprovada em 2026-11-08` |
| Implementação | `concluída` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P4.1, P4.2, P4.3 e P4.10).

## Objetivo

O app nasce na cor certa no dark mode, transita entre telas com direção de
navegação, e números são formatados igual em qualquer aparelho.

## Contexto (estado atual verificado)

- `Theme.kt:82-88`: paleta fixa, sem dynamic color; `themes.xml` define
  `windowBackground` claro e **não existe** `res/values-night/` — flash branco
  em toda abertura fria no dark mode; cores mortas em `colors.xml:26-29`.
- `Motion.kt` é código morto (zero chamadores; grep confirmado) —
  `TreinosApp.kt:182` usa o fade padrão do `AnimatedContent`.
- Números locale-dependentes: `formatWeight` com `String.format`
  (`WorkoutScreen.kt:498-502`), `trimNumber` duplicada em duas telas
  (`HomeScreen.kt:790-794`, `HistoryScreen.kt:671-674`), IMC
  (`GenerateScreen.kt:545`) — enquanto o histórico é pt-BR fixo
  (`Formatters.kt:135-138`): o mesmo app mistura "7,5" e "7.5".
- `formatRunPace`/`formatRunDistance` (`RunLog.kt:49-53`) duplicam os
  canônicos `formatPacePerKm`/`formatDistanceMeters` (`Formatters.kt:108,118`).
- `strings.xml` com 57 strings que nenhuma tela usa (zero `stringResource`).

## Fora de escopo

- Extração de strings para i18n (H3-37 — aqui só se **deleta** o recurso
  morto); novo branding; layout para tablet.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/res/values-night/themes.xml` (novo) | `windowBackground` escuro igual ao `DarkColors.background` |
| `app/src/main/res/values/{colors,strings}.xml` | deletar recursos sem uso (manter só o que `themes.xml` consome) |
| `app/src/main/java/com/treinos/app/ui/Theme.kt` | dynamic color em SDK ≥ 31 com fallback para a paleta verde |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `transitionSpec` dirigido por `motionFor`/`motionDurationMillis` |
| `app/src/main/java/com/treinos/app/ui/Formatters.kt` | `formatDecimal(valor, casas)` e `formatKg` com `Locale("pt","BR")` fixo |
| `app/src/main/java/com/treinos/app/ui/screens/{WorkoutScreen,HomeScreen,HistoryScreen,GenerateScreen}.kt` | migrar para os formatadores canônicos |
| `app/src/main/java/com/treinos/app/ui/RunLog.kt` | deletar `formatRunPace`/`formatRunDistance` (usar os canônicos) |

## Modelo de dados / contrato

Sem mudança de dados/rede. `formatDecimal` é puro e determinístico
(independe do locale do aparelho); assinatura:

```text
formatDecimal(value: Double, casas: Int): String   // "7,5" sempre
formatKg(value: Double?): String                   // "62,5 kg" / "—"
```

## Regras e casos de borda

- Dark mode: a janela nasce escura (sem flash); dynamic color só em SDK ≥ 31
  e **sem** toggle — segue o papel de parede do sistema; SDK < 31 mantém a
  paleta verde atual (ContrastTest continua válido para o fallback).
- Motion: FORWARD (Home → destino) desliza da direita; BACKWARD desliza da
  esquerda; duração vem de `motionDurationMillis` (respeita o animator scale
  do sistema). Se a direção não for decidível, fade atual.
- Todo número exibido (carga, volume, IMC, séries) passa pelos formatadores
  canônicos com locale fixo pt-BR — inclusive quando o aparelho está em
  inglês (decisão: dados de treino são pt-BR no app; revisitar em H3-37).
- Recursos deletados só os comprovadamente mortos (grep `R.string`/`R.color`
  = 0 usos).

## Critérios de aceite

1. **Dado** dark mode ativo, **quando** o app abre a frio, **então** a janela
   nasce escura (sem flash branco).
2. **Dado** aparelho Android 12+, **quando** o papel de parede muda,
   **então** as cores do app seguem o Material You; em Android < 12 a paleta
   verde é mantida.
3. **Dado** navegação Home → Histórico → voltar, **quando** a transição
   roda, **então** o deslize respeita a direção e o animator scale.
4. **Dado** aparelho com locale inglês, **quando** exibe carga 7.5,
   **então** mostra "7,5" (igual ao histórico).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../FormattersTest.kt` | `format_decimal_locale_fixo`, `format_kg_null_traco`, `pace_e_distancia_canonicos` |
| `app/src/test/java/.../MotionTest.kt` | `forward_desliza_da_direita`, `backward_desliza_da_esquerda` (mapeamento puro) |

## Verificação manual

1. Dark mode: fechar e reabrir o app → sem flash.
2. Mudar o papel de parede (Android 12+) → cores seguem.
3. Locale do aparelho em inglês → carga/volume com vírgula.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Dynamic color muda a identidade visual em Android 12+ — ContrastTest
  precisa cobrir o esquema dinâmico ou ser condicionado ao fallback
  (decidir na implementação e documentar no teste). Rollback: remover o
  branch de dynamic color mantém o resto.
