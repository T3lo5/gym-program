# H3-29 — Estados e erros padronizados

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-29` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-08` (aprovada pelo usuário antes da implementação) |
| Implementação | `concluída em 2026-11-08` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P3.2, P3.3, P3.8 e inventário tela × estado).

## Objetivo

Toda tela distingue carregando/vazio/conteúdo, e todo erro é acionável e
persiste até o usuário decidir — em vez de snackbar de 4 s que some.

## Contexto (estado atual verificado)

- `stateIn(WhileSubscribed, emptyList()/null)` faz Home e Histórico mostrarem
  empty state antes do Room emitir ("vazio falso") — `AppViewModel.kt:130-142,
  166-171`; `WorkoutScreen` é o único com padrão correto (`:110-138`).
- Snackbar único sem `actionLabel` e mesmo visual para sucesso e erro
  (`TreinosApp.kt:150-154`); todos os catch do ViewModel desaguam nele
  (`:649, 784, 824, 861, 954, 1134, 1159`).
- Corpo HTTP cru vai para a UI: `BackendClient.kt:79,84` (até 300 chars de
  JSON/HTML); `error.message` cru em ~18 pontos do ViewModel.
- `ErrorState` já existe em `components/Common.kt:179-194` e é subutilizado.
- RunWorkout com `session == null` renderiza formulário morto; "Finalizar"
  sem `sessionId` é no-op silencioso (`TreinosApp.kt:234`).

## Fora de escopo

- Retry automático/backup offline; telemetria de erros.
- Redesenho das telas (só o padrão de estado muda).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `UiState<T>` para planos/sessões; `UiError` persistente por ação; mapeamento de exceção |
| `app/src/main/java/com/treinos/app/ui/UiError.kt` (novo) | `uiErrorFrom(throwable, contexto)` puro |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | snackbar de sucesso; erros renderizados na tela de origem com "Tentar de novo" |
| `app/src/main/java/com/treinos/app/ui/components/Common.kt` | `ErrorState` ganha ação de retry (já existe o esqueleto) |
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | mensagens PT curtas; detalhe técnico só em Logcat |
| `app/src/main/java/com/treinos/app/ui/screens/*.kt` | consumir `UiState` (Home, History, RunWorkout) |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | erro de sync/download persistente com "Tentar de novo" (critério 2) |
| `app/src/main/java/com/treinos/app/ui/RestoreByDeviceId.kt` | mensagem acionável no `RestoreOutcome.Failed` |

## Modelo de dados / contrato

```text
sealed interface UiState<out T> { Loading; Empty; Content(data: T) }
data class UiError(val title: String, val message: String, val retryLabel: String?)
```

Sem mudança de contrato de rede. `BackendClient` passa a lançar exceções com
mensagem já traduzida; o corpo cru vai para `Log.w` e nunca para a UI.

## Regras e casos de borda

- Planos e sessões: `Loading` até o **primeiro** emit do Room; empty state só
  depois (mata o flash de "perdi meus treinos?").
- Erro de geração/sync/download/backup: estado de erro persistido na tela de
  origem (GenerateScreen passa a receber o erro) com "Tentar de novo"
  reexecutando a última ação; sucesso continua como snackbar.
- Mensagens de rede acionáveis ("Sem conexão com o servidor. Verifique o
  Wi-Fi e tente de novo."); HTTP 500/timeout/parse têm textos distintos e
  curtos; o detalhe (`body.take(300)`) vai para Logcat.
- RunWorkout com sessão inexistente → EmptyState "Sessão não encontrada" +
  voltar; Finalizar sem `sessionId` → erro visível, nunca no-op.
- Erro e sucesso nunca compartilham o mesmo estilo (verde só para sucesso).

## Critérios de aceite

1. **Dado** app recém-aberto com dados no banco, **quando** a Home compõe os
   primeiros frames, **então** mostra carregando — nunca o empty state.
2. **Dado** sync falhando por timeout, **quando** o usuário está em Ajustes,
   **então** vê erro persistente com "Tentar de novo" (não um snackbar de 4 s).
3. **Dado** backend respondendo 500 com JSON, **quando** a falha chega à UI,
   **então** a mensagem é curta e acionável; o corpo cru aparece só no
   Logcat.
4. **Dado** geração que falha, **quando** o usuário perde o feedback, **então**
   GenerateScreen ainda mostra o erro até ele tentar de novo ou voltar.
5. **Dado** corrida aberta sem sessão válida, **quando** a tela compõe,
   **então** mostra estado vazio com volta — não um formulário morto.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../UiErrorTest.kt` | `io_sem_rede_mensagem_acionavel`, `http_500_mensagem_curta_sem_corpo`, `timeout_distinto_de_sem_rede`, `erro_local_mantem_o_texto_da_acao_no_snackbar` |
| `app/src/test/java/.../HomeScreenTest.kt` (Robolectric) | `loading_antes_do_primeiro_emit`, `vazio_so_apos_emit` |
| `app/src/test/java/.../RunWorkoutScreenTest.kt` (Robolectric) | `session_null_mostra_vazio`, `loading_antes_da_sessao_chegar`, `sessao_valida_mostra_formulario` |

## Verificação manual

1. Ativar modo avião e sincronizar → erro persistente com retry; desativar e
   tentar de novo → sucesso.
2. Girar o aparelho durante erro → estado sobrevive (ViewModel).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Mudar `StateFlow<List<...>>` para `UiState` toca todas as telas — migrar
  tela a tela mantendo o gate verde por passo. Rollback por tela.
