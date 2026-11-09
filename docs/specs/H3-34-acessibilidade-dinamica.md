# H3-34 — Acessibilidade dinâmica e contraste real

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-34` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `concluída` (gate verde: `VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`) |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P3.4–3.7).

## Objetivo

TalkBack acompanha o treino em tempo real (o que muda é anunciado), nada
quebra com fonte 2x ou teclado aberto, e o contraste testado é o que a tela
realmente usa.

## Contexto (estado atual verificado)

- Um único `liveRegion` no app (`WorkoutScreen.kt:248`, marcos 60/30/10/0) —
  o **início** do descanso, a série registrada, o timer de mobilidade e as
  mensagens de Ajustes não são anunciados.
- Checkbox de modalidade sem rótulo associado (`GenerateScreen.kt:260-280` —
  TalkBack lê "desligado" sem dizer de quê); "Sessões por semana" com
  `isError` sem `supportingText` (`:287-300`).
- `onPrimaryContainer.copy(alpha = 0.75f)` em texto 12 sp
  (`HomeScreen.kt:559`) ≈ 3,9:1 claro / 3,3:1 escuro — falha WCAG AA;
  `ContrastTest.kt:8-35` cobre só 4 pares fixos (o par que falha está fora).
- RunWorkout e mobilidade sem `verticalScroll` (IME cobre o botão finalizar);
  `AddSetDialog` sem scroll corta em fonte 2x
  (`RunWorkoutScreen.kt:74-79`, `MobilityScreen.kt:189-194`,
  `WorkoutScreen.kt:417-473`).

## Fora de escopo

- Navegação por teclado externo/switch access; modo daltônico; TalkBack no
  Wear.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | `liveRegion` no início do descanso; anúncio "Série N salva — X reps, Y kg"; scroll no diálogo |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | `toggleable(role = Checkbox)` no Row; `supportingText` + filtro 1–7 |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | `liveRegion` nos marcos do timer; `verticalScroll` + `imePadding` |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | `verticalScroll` + `imePadding` |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | `liveRegion` nas mensagens de status |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | remover `alpha = 0.75f` (cor sólida) |
| `app/src/test/java/.../ContrastTest.kt` | estender aos pares realmente usados |

## Modelo de dados / contrato

Sem mudança de dados/rede. A regra de contraste vira teste: cada par
(foreground, background) usado em texto das telas precisa de razão ≥ 4,5:1
(≥ 3:1 para texto ≥ 18 sp), calculando o blend quando houver alpha.

## Regras e casos de borda

- Anúncios TalkBack: início do descanso ("Descanso de 90 s iniciado"), série
  salva ("Série 2 salva — 10 reps, 60 kg"), marcos do timer de mobilidade
  (60/30/10/0), mudança de mensagem em Ajustes — todos `liveRegion =
  Polite` (nada interrompe o usuário).
- Checkbox: o Row inteiro é tocável (`toggleable`) e o Checkbox recebe
  `clearAndSetSemantics` para não duplicar o estado; rótulo lido junto.
- Campo numérico "Sessões por semana": `supportingText` com a regra (1–7),
  filtro de entrada e erro anunciado; apagar o campo não "pula" de volta
  para 1 enquanto digita.
- Contraste: remover o alpha 0.75 (usar `onPrimaryContainer` sólido ou
  `outline`); estender `ContrastTest` para `error`/`onError`,
  `onSecondaryContainer`, `tertiary`, `errorContainer` de ReviewPlanCard e
  qualquer par com alpha que sobreviver.
- Scroll/IME: `verticalScroll(rememberScrollState())` + `imePadding()` nas
  duas Columns; conteúdo do `AlertDialog` de série com scroll.

## Critérios de aceite

1. **Dado** TalkBack ativo, **quando** o descanso inicia, **então** ouve
   "Descanso de N s iniciado" sem tocar em nada.
2. **Dado** TalkBack ativo, **quando** salva uma série, **então** ouve o que
   foi salvo (número, reps, carga).
3. **Dado** TalkBack ativo no formulário de geração, **quando** foca o
   checkbox "Musculação", **então** ouve o rótulo e o estado juntos.
4. **Dado** fonte 2x, **quando** abre a corrida e digita, **então** o botão
   "Finalizar corrida" continua alcançável (scroll + IME).
5. **Dado** o par de cores do selo "Gerado com IA", **quando** o
   ContrastTest roda, **então** ele cobre esse par e passa (≥ 4,5:1).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../ContrastTest.kt` | `par_selo_gerado_com_ia_passa`, `pares_error_e_tertiary_cobertos`, `alpha_blend_calculado` |
| `app/src/test/java/.../A11yTest.kt` | `checkbox_row_tem_role_e_rotulo`, `liveRegion_no_descanso_e_mobilidade`, `campo_sessoes_tem_supporting_text` |

## Verificação manual

1. TalkBack: fazer um treino completo ouvindo — início do descanso, série
   salva e fim de descanso anunciados.
2. Fonte 2x: corrida e mobilidade com teclado aberto → botões alcançáveis.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Estender ContrastTest pode revelar outros pares falhando — corrigir a cor
  (não o teste). Rollback por tela; o teste novo fica.
