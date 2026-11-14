# H3-56 — Rascunho do registro avulso sobrevive à morte do processo

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-56` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-13) |
| Implementação | `concluída` (2026-11-14) |

**Origem:** fora de escopo declarado da H3-31 + varredura de polimento de
2026-11-13. O rascunho de `LogActivityScreen` (`ActivityLogDraft` +
`ActivityLogDraftSaver`, linha ~115) sobrevive a rotação, mas não à morte do
processo: quem estava registrando natação/corrida/alongamento avulso e
perdeu o app redigita tudo.

## Objetivo

O formulário do registro avulso volta preenchido com o último estado digitado
quando o app é reaberto após a morte do processo.

## Fora de escopo

- Múltiplos rascunhos (só o último estado).
- Rascunho de geração (a H3-30 já persiste o fallback) e wear.
- Lembrete para completar o registro depois.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | chaves do rascunho (serialização JSON simples ou campos planos) |
| `app/src/main/java/com/treinos/app/ui/screens/LogActivityScreen.kt` | inicializar `draft` do rascunho persistido quando não há `prefill`; salvar a cada mudança; limpar ao salvar com sucesso ou excluir |
| `app/src/main/java/com/treinos/app/ui/ActivityLog.kt` | helper puro de (de)serialização do rascunho |
| `app/src/test/java/com/treinos/app/ui/ActivityLogSaverTest.kt` | novos casos de ida e volta |

## Modelo de dados / contrato

Mesmo padrão do descanso (H3-26): estado em `AppPreferences`, sobrevive à
morte do processo. Nada em Room — o registro só vira sessão ao salvar.

## Regras e casos de borda

- Rascunho único e global (o último estado digitado), sobrescrito a cada
  mudança.
- Limpo ao: salvar com sucesso; confirmar "Excluir" do rascunho.
- `prefill` (editar/copiar último) vence sobre o rascunho salvo — o
  comportamento atual da H3-31 é preservado.
- A data escolhida não avança sozinha ao reabrir dias depois.
- Rascunho ilegível (versão futura com campos novos) → descartado com
  defaults, nunca crash.

## Critérios de aceite

1. **Dado** rascunho com modalidade natação e 1.000 m, **quando** matar o
   processo e reabrir, **então** os campos voltam preenchidos.
2. **Dado** rascunho salvo, **quando** concluir o registro, **então** o
   rascunho some.
3. **Dado** "Editar registro" de uma sessão antiga, **quando** abrir,
   **então** o prefill vence (o rascunho não vaza para a edição).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/ActivityLogSaverTest.kt` | `codec_ida_e_volta_preserva_campos`, `codec_entrada_vazia_devolve_null`, `codec_entrada_corrompida_devolve_null`, `codec_campos_novos_tem_default`, `codec_modalidade_desconhecida_cai_em_other`, `saver_e_codec_estao_em_sincronia` |

## Verificação manual

1. Preencher o registro avulso pela metade, fechar o app pelo seletor,
   reabrir → campos preservados.
2. Salvar o registro → reabrir a tela → formulário limpo.
3. Editar um registro antigo → prefill correto.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Baixo; aditivo. Rollback por `git revert`; nenhum dado novo.

## Implementação (2026-11-14)

- `ActivityLogDraft` tornou-se `@Serializable`; `ActivityModalitySerializer`
  codifica a enum pelo `storageValue` (valor desconhecido cai em `OTHER`).
- `ActivityLogDraftCodec` (em `ActivityLog.kt`) é o par de
  `encode`/`decode` tolerante (`ignoreUnknownKeys`, entrada vazia/corrompida
  devolve `null`).
- `AppPreferences.activityDraftV1` (chave `activity_draft_v1`) persiste o JSON
  do rascunho; sobrevive à morte do processo.
- `LogActivityScreen`: sem `prefill`, o `draft` inicial lê o rascunho persistido
  (`activityLogDraftFrom(prefill, persistedDraft)`); um
  `LaunchedEffect` com `snapshotFlow { draft }.debounce(300 ms)` reescreve o
  rascunho a cada mudança. A rotação continua usando `ActivityLogDraftSaver`.
- `AppViewModel.saveActivity` e `deleteLogActivity` limpam
  `preferences.activityDraftV1 = null` ao concluir; `editLogActivity` também
  limpa (o `prefill` vence sobre o rascunho).
- Testes em `ActivityLogSaverTest.kt`: round-trip do `Saver`, round-trip do
  codec, entrada vazia/corrompida, campos novos com default, modalidade
  desconhecida e sincronia entre `Saver` e `codec`.

## Verificação

```bash
./scripts/verify.sh
# VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura
```
