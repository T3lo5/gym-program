# H3-30 — Geração sem atrito: defaults, progresso honesto e cancelar

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-30` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P2.4, P2.6 e parte de P3.2).

## Objetivo

Gerar um plano custa 3 toques por padrão, mostra progresso honesto com
cancelamento, e o resultado/motivo do fallback fica visível até o usuário
decidir o que fazer.

## Contexto (estado atual verificado)

- Nada pré-selecionado: `modalityDrafts` com `included = false` para todas
  (`GenerateScreen.kt:154-158`); formulário único de ~14 seções com CTA no
  fundo (`:588-656`); hint "Inclua pelo menos uma modalidade" longe dos
  checkboxes que o causam (`:577-586`).
- Durante a geração: só um spinner de 18 dp no botão (`:644-651`) com
  `readTimeout` de 90 s (`BackendClient.kt:95`) — sem tempo, sem estágio, sem
  cancelar; o motivo do fallback (H3-21) chega como snackbar de ~4 s na Home
  (`AppViewModel.kt:641-647`).
- Goal duplicado: a UI nunca edita `draft.goal` (sempre "hypertrophy",
  `:160-178`) enquanto o chip global vai no request
  (`AppViewModel.kt:622-638`) — o prompt recebe dois objetivos divergentes.
- Seções "(musculação)" (objetivo/nível) sempre visíveis, mesmo em plano só
  de corrida/mobilidade (`GenerateScreen.kt:351,369`).

## Fora de escopo

- Streaming real de estágios do backend (sem SSE); editar o plano antes de
  salvar; seleção por nome (H3-25); wear.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | defaults; CTA fixo; "Mais opções" colapsável; seções condicionais; overlay de progresso |
| `app/src/main/java/com/treinos/app/ui/GenerateConfig.kt` (novo) | data class + codec JSON puro da última configuração |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | persistir/restaurar config; `cancelGenerate`; `lastFallbackReason` |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | chaves `generate_config_v1` (JSON) e `last_fallback_reason` (String) |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | selo "Gerado localmente" passa a citar o motivo persistido |

## Modelo de dados / contrato

- `generate_config_v1`: JSON com modalidades/dias/duração/nível/equipamento/
  foco/perfil — codec puro com `ignoreUnknownKeys` (versão no nome da chave).
- `last_fallback_reason`: string do motivo (vazia quando o último plano veio
  da IA); limpa a cada geração bem-sucedida via IA.
- Contrato de rede não muda (o goal da modalidade de força passa a ser o do
  chip — campo já existente em `modalities[].goal`).

## Regras e casos de borda

- Pré-incluir "Musculação 4x" por padrão; ao abrir a tela, restaurar a última
  configuração salva (se existir); config persistida a cada geração
  bem-sucedida.
- CTA fixo (sempre visível no fim da tela, estilo bottom bar) com o hint de
  validação ao lado — o usuário não precisa rolar para saber por que não pode
  gerar.
- Perfil/sexo/revisão/ambiente dentro de "Mais opções" (colapsado por
  padrão); seções de força (objetivo, nível, equipamento, foco, duração)
  ocultas quando nenhuma modalidade de força está inclusa.
- `modalityToDto` envia o goal do chip na modalidade de força (remove o
  "hypertrophy" fixo); backend `build_hybrid_user_prompt`/local passam a
  receber um objetivo coerente — sem mudança de schema.
- Overlay de geração: tempo decorrido em segundos + "A IA pode levar até
  90 s" + botão **Cancelar** (cancela a coroutine, `isGenerating = false`,
  nada é salvo).
- Motivo do fallback: persistido e exibido no resultado da geração e no selo
  do plano na Home até a próxima geração da IA.

## Critérios de aceite

1. **Dado** primeira abertura sem config salva, **quando** abre "Gerar",
   **então** "Musculação 4x" já está inclusa e o CTA está habilitado.
2. **Dado** geração em curso, **quando** 10 s passam, **então** o overlay
   mostra o tempo decorrido e Cancelar interrompe sem salvar plano.
3. **Dado** fallback local, **quando** o plano é salvo, **então** o selo na
   Home cita o motivo ("resposta da IA sem IDs válidos") até a próxima
   geração da IA.
4. **Dado** só corrida + mobilidade inclusas, **quando** o formulário compõe,
   **então** seções de força não aparecem.
5. **Dado** usuário escolheu "Força" no chip, **quando** o request sai,
   **então** `modalities[].goal` da força é "strength" (contrato validado nos
   dois lados).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../GenerateConfigTest.kt` | `round_trip_config`, `config_corrompida_cai_no_default`, `musculacao_default_incluida` |
| `app/src/test/java/.../ContractTest.kt` | `goal_da_modalidade_forca_segue_chip` |
| `app/src/test/java/.../GenerateScreenTest.kt` (Robolectric) | `cta_fixo_e_hint_juntos`, `secoes_forca_ocultas_sem_forca`, `overlay_mostra_tempo_e_cancela` |
| `back/tests/test_contract_v2.py` | `goal_modalidade_forca_aceito` |

## Verificação manual

1. Gerar um plano → conferir defaults, overlay com tempo, cancelar no meio.
2. Desligar o backend → gerar → fallback com motivo visível na Home.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Mudar o goal enviado pode alterar planos gerados pela IA — o teste de
  contrato dos dois lados trava o formato; o comportamento do prompt já
  documenta o objetivo global (H3-23 intocado). Rollback: reverter
  `modalityToDto` e o overlay independentemente.
