# H3-57 — Predictive back (Android 14) na pilha de navegação

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-57` |
| MoSCoW | `C` |
| Spec aprovada em | `2026-11-14` |
| Implementação | `implementada (2026-11-14)` |

**Origem:** varredura de polimento UX/UI de 2026-11-13. O app já roda com
`targetSdk 34` e `activity-compose 1.8.2` (que traz `PredictiveBackHandler`),
mas não opta pelo gesto preditivo (`enableOnBackInvokedCallback` ausente no
manifest) e o back da pilha (H3-38) usa `BackHandler` clássico
(`TreinosApp.kt:195`) — no Android 14 o gesto não mostra a prévia animada do
sistema.

## Objetivo

No Android 13+, o gesto de voltar mostra a prévia animada do sistema (a tela
anterior aparece atrás) e o app continua decidindo quando voltar.

## Fora de escopo

- Animações de transição entre telas (a H3-33 já cuida do NavHost).
- Wear.
- Back dentro de diálogos/sheets (continua fechando o overlay primeiro).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/AndroidManifest.xml` | `android:enableOnBackInvokedCallback="true"` na `<application>` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | trocar o `BackHandler` da pilha (:195) por `PredictiveBackHandler` (API 33+) com fallback `BackHandler` para API < 33; navegar só quando o gesto completa |
| `app/src/main/java/com/treinos/app/MainActivity.kt` | conferir que não há `onBackPressed`/callback custom que conflite com a flag |

## Modelo de dados / contrato

Sem schema. Contrato de navegação: o gesto preditivo só popa a pilha quando o
usuário completa o gesto; cancelar (soltar fora da zona) não navega.

## Regras e casos de borda

- Pilha com mais de uma tela: gesto completo volta; gesto cancelado não
  navega.
- Pilha com uma tela (Home): handler desabilitado — o sistema decide (sair
  do app).
- Diálogos/sheets abertos continuam fechando primeiro (ordem atual dos
  handlers preservada).
- API < 33: comportamento idêntico ao atual (fallback clássico).

## Critérios de aceite

1. **Dado** Home→Histórico (emulador API 34 com gesto preditivo ativado),
   **quando** arrastar e soltar no meio, **então** nada acontece; completar
   o gesto, volta.
2. **Dado** API < 33, **quando** usar back, **então** comportamento igual ao
   de hoje.
3. **Dado** diálogo aberto, **quando** usar back, **então** fecha o diálogo
   (não a tela).

## Testes a criar

Nenhum teste puro novo (comportamento de sistema); o gate existente garante
build/lint e `NavigationBackTest` continua verde.

## Verificação manual

1. Emulador API 34 → Opções do desenvolvedor → ativar "Predictive back
   animations".
2. Navegar Home→Histórico→dia; arrastar back parcial (cancela) e completo
   (volta).
3. Repetir em API 28 (fallback) e com diálogo aberto.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

A flag é global: qualquer `onBackPressed`/`OnBackPressedCallback` custom
precisa ser conferido (MainActivity e TreinosApp são os únicos pontos do
app). Rollback: remover a flag e reverter o handler.
