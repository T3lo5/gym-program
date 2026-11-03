# H3-3 — Acessibilidade (TalkBack, fonte e contraste)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-3` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `pendente` |
| Depende de | H3-2 (tema/estados) |

## Objetivo

Permitir que o app seja usado com TalkBack, fonte grande e contraste alto, sem
perder informação nem ações — inclusive durante o treino.

## Fora de escopo

- Libras, audiodescrição ou leitor de tela próprio.
- Suporte a Switch Access além do que o Compose oferece por padrão.
- Tradução para outros idiomas.
- Redesign visual (H3-2).
- Certificação WCAG formal.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/A11y.kt` | **Novo.** Descrições e estados puros |
| `app/src/main/java/com/treinos/app/ui/Contrast.kt` | **Novo.** Luminância/contraste puro |
| `app/src/main/java/com/treinos/app/ui/Theme.kt` | Cores exportadas como ARGB + ajuste de contraste |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | Semântica de top bar, empty state e chips |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Semântica dos cards e botões |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Labels dos campos e chips |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Série, progresso, timer e diálogo |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Expandir/recolher e métricas |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Campos, switches e mensagens |
| `app/src/test/java/com/treinos/app/ui/A11yTest.kt` | **Novo.** Descrições e anúncios |
| `app/src/test/java/com/treinos/app/ui/ContrastTest.kt` | **Novo.** Contraste do tema |

Não edite `shared/`, `wear/` nem `com.treinos.shared/`.

## Modelo de dados / contrato

```kotlin
// A11y.kt — puro, sem Android
fun exerciseContentDescription(name: String): String
fun setContentDescription(setNumber: Int, reps: Int?, weightKg: Double?, rpe: Double?): String
fun progressStateDescription(completed: Int, planned: Int): String
fun restStateDescription(remaining: Int, total: Int): String
fun reviewStateDescription(dateLabel: String, due: Boolean): String
fun timerShouldAnnounce(remaining: Int): Boolean
fun decorativeContentDescription(): String? = null

// Contrast.kt — ARGB (0xAARRGGBB)
fun relativeLuminance(argb: Long): Double
fun contrastRatio(foregroundArgb: Long, backgroundArgb: Long): Double
```

Textos exatos (português):

- `exerciseContentDescription("Supino reto")` →
  `"Exercício Supino reto"`.
- `setContentDescription(2, 10, 40.0, 8.0)` →
  `"Série 2, 10 repetições, 40 quilos, RPE 8"` (omitir partes nulas).
- `progressStateDescription(2, 4)` → `"2 de 4 séries concluídas"`.
- `restStateDescription(45, 90)` → `"Descanso: 45 segundos restantes de 90"`.
- `reviewStateDescription("01/12/2026", true)` →
  `"Revisão atrasada desde 01/12/2026"`; se `false`,
  `"Próxima revisão em 01/12/2026"`.
- `timerShouldAnnounce` é `true` apenas em `60`, `30`, `10` e `0` (não a cada
  segundo).

Regras de UI:

- Todo `Icon`/`IconButton` sem texto precisa de `contentDescription`; ícones
  decorativos usam `null`.
- Linhas de série usam `Modifier.semantics(mergeDescendants = true)` com
  `stateDescription`/`contentDescription`, sem esconder o botão de ação.
- O card de exercício expõe `heading()` no nome.
- O progresso usa `progressBarRangeInfo` ou `stateDescription`; não depender só
  da barra colorida.
- Timer: `liveRegion = LiveRegionMode.Polite` apenas nos valores anunciáveis.
- Áreas tocáveis: mínimo `48.dp` (usar `minimumInteractiveComponentSize()`).
- Texto sempre em `sp`; nenhum card com altura fixa que corte fonte 2.0.
- Contraste mínimo: 4.5:1 para texto normal e 3:1 para texto grande/ícones.
- Cor nunca é o único indicador: “Concluído” tem texto + ícone.
- Ordem de foco: top bar → conteúdo principal → ações; não usar
  `traversalIndex` negativo.
- Teclado: `imeAction = Done` nos campos numéricos e foco volta ao botão.

## Regras e casos de borda

1. TalkBack em uma série sem reps/carga: descrição não deixa vírgulas soltas.
2. Exercício sem nome (usa `exerciseId`): descrição usa o fallback.
3. Timer em 0: anuncia “Descanso concluído” uma vez, não a cada recomposição.
4. Fonte 2.0: botões e cards crescem; nenhum texto corta.
5. Tema escuro: contraste do texto sobre `surface` e `primaryContainer` ≥ 4.5.
6. Ícone de check de série concluída: descrição “Série concluída” apenas se não
   houver texto redundante na mesma linha.
7. Snackbar é anunciada pelo TalkBack; não duplicar com `contentDescription`.
8. Diálogo de série prende o foco; ao fechar, o foco volta ao botão que abriu.
9. `Switch` de backend remoto tem `stateDescription` “Ligado/Desligado”.
10. Campos de peso/altura (H1-5) têm `label` e `supportingText` de erro; erro
    não é só borda vermelha.
11. Calendário do histórico: cada dia é um botão com descrição
    “3 de novembro, 1 treino”.
12. Nenhum `clickable` em `Text` sem `role = Role.Button`.

## Critérios de aceite

1. **Dado** TalkBack ativo, **quando** o usuário navega na Home, **então** ouve
   título, dias e botões na ordem visual, sem elementos sem nome.
2. **Dado** o treino em andamento, **quando** foca uma série, **então** ouve
   número, reps, carga e RPE.
3. **Dado** o descanso, **então** o timer não interrompe o TalkBack a cada
   segundo; anuncia só em 60/30/10/0.
4. **Dado** fonte do sistema em 2.0, **então** nenhuma tela principal corta
   texto ou esconde ação.
5. **Dado** tema claro e escuro, **então** `contrastRatio` do texto principal é
   ≥ 4.5.
6. **Dado** um ícone decorativo, **então** `contentDescription` é `null`.
7. **Dado** um botão só de ícone (voltar, expandir), **então** tem descrição.
8. **Dado** o diálogo de série, **então** o foco fica preso e volta ao fechar.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/A11yTest.kt` | `descricao_do_exercicio_inclui_nome`, `descricao_de_serie_completa`, `descricao_de_serie_sem_carga_nao_deixa_virgula_solta`, `progresso_anuncia_concluidas_e_total`, `descanso_anuncia_segundos`, `timer_anuncia_apenas_marcos`, `revisao_atrasada_muda_texto`, `icone_decorativo_tem_descricao_nula` |
| `app/src/test/java/com/treinos/app/ui/ContrastTest.kt` | `texto_principal_contraste_claro`, `texto_principal_contraste_escuro`, `texto_sobre_primary_container`, `preto_e_branco_tem_contraste_maximo` |

Testes de TalkBack real e fonte 2.0 são manuais (ou Robolectric, se H3-1 já
estiver implementado).

## Verificação manual

1. Ative TalkBack e percorra Home, Gerar, Treino, Histórico e Ajustes.
2. Confirme que nenhum botão é anunciado como “Botão” sem nome.
3. No treino, registre uma série e ouça o progresso e o descanso.
4. Ative fonte 2.0 e repita; confira cards, botões e diálogo.
5. Alterne tema claro/escuro e confira contraste visual.
6. Use o app com uma mão e confirme alvos de 48 dp.
7. Feche e reabra o diálogo de série usando só o TalkBack.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Excesso de anúncios:** timer e listas podem ficar verbosos; limitar
  `liveRegion` aos marcos.
- **Semântica duplicada:** `mergeDescendants` + `contentDescription` pode
  esconder ações; testar com TalkBack após cada mudança.
- **Contraste do tema:** ajustar cores pode afetar a identidade (H3-2); validar
  os dois temas.
- **Rollback:** remover `A11y.kt`/`Contrast.kt` e as semânticas adicionadas. Não
  há mudança de dados.
