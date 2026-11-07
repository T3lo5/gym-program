# H3-32 — Componentes e consistência visual

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-32` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P4.4–4.8, P3.4 e parte de P4.11).

## Objetivo

Um conjunto pequeno de componentes compartilhados elimina as 10 cópias de
"botão ocupado", distingue sucesso de erro, dá identidade visual de treino
(ícones) e uniformiza imagem/espaçamento — sem mudar comportamento.

## Contexto (estado atual verificado)

- Par `Row + CircularProgressIndicator(18.dp) + Text("…")` copiado ~10 vezes
  (`SettingsScreen.kt:209-217 … 584-589`, `GenerateScreen.kt:644-650`,
  `LogActivityScreen.kt:227-233`), com cor de spinner inconsistente.
- Todas as mensagens de status de Ajustes em `primary` — sucesso e falha
  idênticos (`SettingsScreen.kt:220-226, 338-344, 513-519, 641-647`).
- 7 ícones no app inteiro; Home navega por `TextButton` no TopAppBar
  (`HomeScreen.kt:107-111`); player de mobilidade todo texto
  (`MobilityScreen.kt:231-285`).
- "Em andamento" pintado com `error` (`HistoryScreen.kt:587-592`).
- `ExerciseImage.kt:40-45`: `AsyncImage` sem `placeholder`/`error`/`crossfade`
  e sem `clip`; `ExerciseDetailSheet.kt:82-88` exibe músculos/equipamento em
  inglês cru apesar de `muscleLabel`/`equipmentLabel` existirem
  (`Formatters.kt:67-103`); `size(240.dp)` vence `fillMaxWidth` (`:67-71`).
- Ritmo de lista `spacedBy(4.dp)` no Generate vs 12.dp no resto
  (`GenerateScreen.kt:188`); 13 `maxLines = 1` sem `overflow` na Home;
  chips de filtro do histórico em `Row` sem scroll (`HistoryScreen.kt:194-212`).

## Fora de escopo

- Navegação por ícones da Home (bottom bar é a H3-38).
- Paleta/dynamic color/motion (H3-33); strings/i18n (H3-37); contraste
  (H3-34).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | `BusyButton`, `StatusText(text, isSuccess)` |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseImage.kt` | `clip(shapes.small)`, placeholder, `error` → fallback, `crossfade`, letra escalada pelo tamanho |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseDetailSheet.kt` | `muscleLabel`/`equipmentLabel`; `fillMaxWidth().aspectRatio(1f)`; `ContentScale` uniforme |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | substituir os 8 pontos por `BusyButton`; mensagens via `StatusText` |
| `app/src/main/java/com/treinos/app/ui/screens/{GenerateScreen,LogActivityScreen}.kt` | substituir por `BusyButton` |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | ícones PlayArrow/Pause + SkipNext no player |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | ícone de add em "Registrar série" |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | "Em andamento" em `tertiary`; chips em `FlowRow` |
| `app/src/main/java/com/treinos/app/ui/screens/{HomeScreen,GenerateScreen}.kt` | `overflow = Ellipsis` (linha única intencional) / remover `maxLines` de nomes; `spacedBy(12.dp)` |

## Regras e casos de borda

- `BusyButton(enabled, busyText, idleText, onClick)`: spinner 18 dp com cor
  derivada de `LocalContentColor` (fim da inconsistência); desabilitado
  enquanto ocupado.
- `StatusText`: sucesso = `primary` + ícone check; erro = `error` + ícone de
  alerta; usado nas 4 mensagens de Ajustes (classificação no ViewModel ou por
  convenção da chamada).
- Ícones sempre com `contentDescription` (ou `null` quando decorativos junto
  de texto visível).
- Fallback de letra do `ExerciseImage`: `titleMedium` até 48 dp,
  `headlineMedium` acima; estado de erro da rede cai no fallback (hoje fica
  um buraco branco).
- `maxLines = 1` só onde a linha única é intencional (sempre com ellipsis);
  nome de dia/exercício quebra linha.

## Critérios de aceite

1. **Dado** qualquer botão ocupado do app, **quando** carrega, **então** o
   visual é idêntico em todas as telas (mesmo componente).
2. **Dado** falha de conexão em Ajustes, **quando** a mensagem aparece,
   **então** ela é vermelha com ícone — distinta do sucesso.
3. **Dado** imagem de exercício sem rede, **quando** o download falha,
   **então** aparece o fallback de letra (nunca quadrado vazio).
4. **Dado** detalhe de exercício, **quando** abre, **então** músculos e
   equipamento aparecem em português e a imagem ocupa a largura.
5. **Dado** fonte 2x na Home, **quando** um nome é longo, **então** quebra
   linha (ou ellipsis onde intencional) — nunca corte no meio do glifo.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../A11yTest.kt` | `iconbuttons_tem_content_description`, `status_text_erro_distinto` |
| `app/src/test/java/.../HistoryScreenTest.kt` | `chips_em_flowrow_nao_transbordam` |
| `app/src/test/java/.../ExerciseImageTest.kt` (Robolectric) | `erro_de_rede_mostra_fallback_letra` |

## Verificação manual

1. Ajustes: conectar com sucesso e com falha → cores distintas.
2. Modo avião + abrir detalhe de exercício → fallback de letra.
3. Fonte 2x: percorrer Home/Histórico → sem texto cortado.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Substituição mecânica de 10 pontos: fazer por tela com gate entre passos.
  Rollback trivial (componentes novos sem outros chamadores).
