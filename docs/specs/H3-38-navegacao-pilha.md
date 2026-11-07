# H3-38 — Navegação em pilha e retomada rápida

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-38` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P2.2 e P2.8).

## Objetivo

"Voltar" volta para onde o usuário estava, salvar uma edição devolve à
origem, e os destinos principais ficam ao alcance do polegar (bottom bar) —
com retomada de treino em 1 toque pelo launcher.

## Contexto (estado atual verificado)

- Navegação é um enum no ViewModel com um único `BackHandler` que sempre vai
  para Home (`TreinosApp.kt:167-172`); `onBack = goHome` em Histórico,
  LogActivity e Ajustes (`:246, :261, :290`).
- Salvar uma edição vinda do Histórico abandona o usuário na Home
  (`AppViewModel.kt:952`) enquanto excluir volta ao Histórico (`:968`) — o
  "Voltar" promete voltar e faz outra coisa.
- Histórico/Ajustes são `TextButton` no topo da Home (`HomeScreen.kt:108-109`)
  — a pior zona de alcance com uma mão.
- Manifest sem deep link/shortcut (`AndroidManifest.xml:23-31`).

## Fora de escopo

- Migração para Navigation Compose (grafo, args tipados, deep links de
  conteúdo) — pilha mínima própria basta no tamanho atual; Wear.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `backTarget: Screen` (origem) definido ao navegar; pós-ação retorna à origem |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `BackHandler` usa `backTarget`; `NavigationBar` nas telas de hub |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | remove TextButtons de Histórico/Ajustes do TopAppBar |
| `app/src/main/AndroidManifest.xml` + `res/xml/shortcuts.xml` | shortcut dinâmico "Retomar treino" (o mesmo da H3-35) |
| `app/src/test/java/...` | testes abaixo |

## Modelo de dados / contrato

Sem mudança de dados/rede. Pilha mínima no ViewModel:

```text
navegar(destino)  -> backTarget = tela atual (quando aplicável)
voltar()          -> backTarget ?: Home
posAcao(origem)   -> Screen(backTarget ?: origem)
```

Shortcut: id `resume_workout` (H3-35 define o contrato do intent).

## Regras e casos de borda

- Bottom bar com 3 destinos — **Treinar** (Home), **Histórico**, **Ajustes** —
  visível apenas nessas telas; execução de treino, geração e registro avulso
  não têm bottom bar (foco total).
- "Voltar" da top bar e o back do sistema retornam à **origem** da navegação;
  salvar edição de registro avulso retorna ao Histórico quando veio dele;
  excluir permanece no Histórico (como hoje).
- Home é a raiz: back na Home sai do app (padrão); back no treino mantém o
  comportamento de `leaveWorkout` (com aviso).
- O estado da pilha (`backTarget`) vive no ViewModel — sobrevive rotação.
- Shortcut "Retomar treino" abre a tela de treino direto (sem passar pela
  Home); publicado/removido conforme `inProgressSession`.

## Critérios de aceite

1. **Dado** usuário no Histórico que abriu o registro avulso, **quando**
   salva a edição, **então** volta ao Histórico (não à Home).
2. **Dado** qualquer tela de hub, **quando** olha o rodapé, **então** há
   bottom bar com Treinar/Histórico/Ajustes; na execução de treino, **não** há.
3. **Dado** treino em andamento, **quando** toca o shortcut do launcher,
   **então** a tela de treino abre direto.
4. **Dado** navegação Home → Histórico → Ajustes → back ×3, **quando** volta,
   **então** percorre Ajustes → Histórico → Home (ordem de origem).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../NavigationBackTest.kt` | `back_retorna_a_origem`, `salvar_edicao_volta_ao_historico`, `back_target_sobrevive_a_nada` (default Home) |
| `app/src/test/java/.../HomeScreenTest.kt` (Robolectric) | `bottom_bar_nos_hubs`, `sem_bottom_bar_no_treino` |

## Verificação manual

1. Histórico → editar registro → salvar → está no Histórico.
2. Percorrer os 3 destinos pela bottom bar; abrir treino → bottom bar some.
3. Toque longo no ícone do app com treino em andamento → shortcut retoma.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Adicionar `NavigationBar` muda o padding das telas de hub — conferir
  insets (Scaffold). Rollback: `backTarget` default `Home` reproduz o
  comportamento atual.
