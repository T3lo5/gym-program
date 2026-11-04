# H3-2 — Polimento visual e animações

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-2` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `pendente` |
| Depende de | H1-4, H2-4 (telas finais) |

## Objetivo

Dar identidade e acabamento ao app: tema coerente, transições suaves entre
telas e estados de carregamento/vazio/erro que não pareçam “tela quebrada”.

## Fora de escopo

- Redesign completo, nova navegação ou troca do Material 3.
- Animações Lottie, vídeo, splash complexa ou biblioteca nova.
- Tema dinâmico (Material You) — a identidade é fixa.
- Ícone do app, logo ou assets de marketing.
- Testes de screenshot pixel-perfect.
- Alterar regras de negócio ou textos legais.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/Theme.kt` | `Typography`, `Shapes`, cores claras/escuras, `TreinosTheme` |
| `app/src/main/java/com/treinos/app/ui/Motion.kt` | **Novo.** Direção/duração de transição (puro) |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `AnimatedContent` entre telas + `SnackbarHost` |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | `LoadingState`, `ErrorState`, `EmptyState` com ação, skeleton |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Cards com elevação/forma e estado de revisão |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Loading no botão, erro inline e resumo |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Progresso animado, timer sem “pulo” e hierarquia |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Cards e calendário com espaçamento consistente |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Seções, divisores e estados de ação |
| `app/src/main/res/values/themes.xml` | Janela e barras do sistema coerentes |
| `app/src/test/java/com/treinos/app/ui/MotionTest.kt` | **Novo.** Regras de transição |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Tokens de motion (puros, sem Android):

```kotlin
enum class ScreenMotion { FORWARD, BACKWARD, NONE }

fun motionFor(from: Screen, to: Screen): ScreenMotion
fun motionDurationMillis(animatorScale: Float): Int
fun shouldShowLoading(elapsedMs: Long): Boolean
```

Regras:

- `Home` é a raiz. Sair de `Home` para `Generate`, `History`, `Settings` ou
  `Workout` é `FORWARD`; voltar para `Home` é `BACKWARD`.
- Trocar entre `History` e `Settings` (via Home) sempre passa por `Home`;
  não há transição lateral direta.
- Mesma tela → `NONE`.
- Duração: `FORWARD/BACKWARD = 250 ms`, reduzida para 150 ms se o sistema
  estiver em “remover animações”. `animatorScale <= 0f` → `0 ms` (sem animação).
- Deslocamento máximo: `12.dp`; alpha de `0f` a `1f`.
- `shouldShowLoading` só retorna `true` após `150 ms` para evitar flash em
  operações rápidas.

Tokens visuais em `Theme.kt`:

```kotlin
internal val TreinosTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

internal val TreinosShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)
```

- Manter a paleta verde/teal atual; ajustar apenas contraste e superfícies.
- `TreinosTheme` aplica `TreinosTypography`, `TreinosShapes` e
  `surfaceContainer` coerente nos dois temas.
- Sem `dynamicColor` para a identidade não mudar por aparelho.

## Regras e casos de borda

1. Animação nunca atrasa o toque: o botão responde antes de a transição
   terminar.
2. `animatorScale = 0` desliga animações (acessibilidade).
3. Timer de descanso não anima o número a cada segundo; apenas o progresso.
4. Loading aparece só depois de 150 ms; se a operação terminar antes, não
   pisca.
5. Botão em loading fica desabilitado e mostra spinner + texto, sem mudar de
   largura.
6. Erro de geração/sincronização aparece em `Snackbar`, com ação “Tentar de
   novo” quando fizer sentido.
7. Confirmação destrutiva (importar backup) continua em `AlertDialog`; nunca
   vira snackbar.
8. Estado vazio sempre tem um CTA (gerar, sincronizar, buscar treino).
9. Textos longos usam `maxLines` + `TextOverflow.Ellipsis`; não esticam cards.
10. Tema escuro: superfícies e texto legíveis; nenhum preto puro sobre branco.
11. Fonte do sistema em 2.0 não corta conteúdo (ver H3-3).
12. Não introduzir dependência de animação; usar `androidx.compose.animation`
    (já vem com a BOM).

## Critérios de aceite

1. **Dado** que o usuário toca em “Histórico” na Home, **quando** a tela entra,
   **então** há slide/fade de 250 ms e o botão continua respondendo.
2. **Dado** “Remover animações” ativado no Android, **então** a troca de tela é
   instantânea.
3. **Dado** que o usuário toca em “Gerar treino”, **então** o botão vira
   spinner + “Gerando…” e não pode ser tocado de novo.
4. **Dado** que a geração falha, **então** aparece snackbar com a mensagem e o
   formulário continua preenchido.
5. **Dado** um histórico vazio, **então** aparece `EmptyState` com CTA.
6. **Dado** o tema escuro, **então** todas as telas mantêm contraste e não há
   texto invisível.
7. **Dado** um nome de exercício de 60 caracteres, **então** ele é truncado com
   reticências sem quebrar o card.
8. **Dado** o timer de descanso, **então** só a barra anima; o número muda uma
   vez por segundo.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/MotionTest.kt` | `home_para_history_e_forward`, `history_para_home_e_backward`, `generate_para_home_e_backward`, `mesma_tela_nao_anima`, `escala_zero_desliga_animacao`, `loading_so_aparece_apos_150ms` |

A aparência e o tema são verificados manualmente (ou por teste Robolectric se
H3-1 já estiver implementado).

## Verificação manual

1. Navegue por todas as telas nos temas claro e escuro e confira transições.
2. Ative “Remover animações” no Android e repita.
3. Gere um treino com backend desligado e confira loading + erro.
4. Abra Histórico vazio e confirme o CTA.
5. Aumente a fonte do sistema e confira cards e botões.
6. Gire o aparelho e confira que o estado não se perde.
7. Confira que o timer de descanso não “treme” a cada segundo.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Animação atrapalhar o treino:** durações curtas e sem animação no timer.
- **Regressão de layout:** testar em tela pequena (360 dp) e fonte 2.0.
- **Escopo infinito de “polimento”:** esta spec lista os tokens e os estados;
  qualquer redesign maior exige outra spec.
- **Rollback:** reverter `Theme.kt`, `Motion.kt`, `TreinosApp.kt` e os
  componentes para a versão anterior; nenhum dado do usuário é afetado.
