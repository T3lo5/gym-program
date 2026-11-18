# H5-6 — Wear sync robusto e resolução de conflitos

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H5-6` |
| MoSCoW | `C` |
| Versão alvo | `Horizon 5` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída em 2026-11-18` |
| Depende de | H3-20, H4-15, H4-16, H5-1 |

## Objetivo

Preservar séries standalone do relógio e conciliar sessões registradas no celular, relógio e outros dispositivos sem duplicar séries ou sobrescrever a edição mais recente.

## Fora de escopo

- Histórico completo no relógio.
- Criar plano no relógio.
- Corrida/mobilidade com sensores no Wear.
- Dashboard remoto exclusivo do relógio.
- Merge automático de sessões iniciadas separadamente com mesmos horários.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `protocol/src/main/kotlin/com/treinos/protocol/WearProtocol.kt` | Revisão de sessão/lote aditiva e campos de origem, edição e cancelamento. |
| `wear/src/main/java/com/treinos/wear/data/WearDatabase.kt` | Metadados de revisão e origem local das séries. |
| `wear/src/main/java/com/treinos/wear/data/WearSyncService.kt` | Lotes maiores com cursor local, lote órfão e confirmação persistente. |
| `app/src/main/java/com/treinos/app/data/wear/WearSyncRepository.kt` | Aplicar lote pelo `clientSetId`, revisão e origem. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Preservar sessão ativa e reconciliar finalização quando houver conflito. |
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | Compatibilidade e campos aditivos. |
| `wear/src/test/java/com/treinos/wear/ui/WearStandaloneSessionTest.kt` | Conflito, reenvio e reinício. |
| `app/src/test/java/com/treinos/app/data/wear/WearSyncBatchTest.kt` | Revisões, origem e finalização concorrente. |

## Modelo de dados / contrato

```text
WearLoggedSet aditivo:
  originDeviceId: "<device>"
  revision: "<updated-at>|<mutation-id>"
  deleted: Boolean = false

WearSyncBatch aditivo:
  batchId
  cursor
  sessionId
  finished
  sets

Regra:
  series sempre keyed por clientSetId
  reenviar o mesmo clientSetId não altera/duplica
  edição posterior preserva revision maior
  empate usa originDeviceId lexicográfico
  lote de sessão diferente da ativa não substitui o snapshot atual
```

## Regras e casos de borda

- Lotes pendentes continuam acima de reconexão celular.
- Série criada no celular e editada no relógio usa `clientSetId` compartilhado.
- Série editada nos dois lados vence pela revisão determinística.
- Sessão já finalizada no celular não aceita reabertura pelo relógio.
- Finalização simultânea resulta em uma única sessão concluída.
- Protocolo novo é aditivo; relógio/celular antigos ignoram campos desconhecidos.
- Acima de 200 séries pendentes, o item mais antigo é descartado e registrado em diagnóstico local.
- Cap da fila de comandos H3-20 permanece separado.

## Critérios de aceite

1. **Dado** série registrada offline no relógio, **quando** reconecta, **então** chega uma vez ao celular.
2. **Dado** reenvio do mesmo lote, **quando** processado duas vezes, **então** nenhuma série duplica.
3. **Dado** edição da mesma série em celular e relógio, **quando** reconciliadas, **então** vence a revisão definida pela spec.
4. **Dado** celular finalizado enquanto relógio treina offline, **quando** o lote chega, **então** não reabre nem altera a sessão já concluída.
5. **Dado** reinício do relógio, **quando** a sessão é restaurada, **então** pendências e revisões sobrevivem.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `protocol/src/test/kotlin/com/treinos/protocol/WearProtocolTest.kt` | `lote_aditivo_round_trip`, `campos_novos_ignorados_por_old`, `cursor_round_trip` |
| `wear/src/test/java/com/treinos/wear/ui/WearStandaloneSessionTest.kt` | `revisao_sobrevive_restart`, `lote_cap`, `edicao_duplica`, `finalizacao_concorrente` |
| `app/src/test/java/com/treinos/app/data/wear/WearSyncBatchTest.kt` | `aplica_origin_device_once`, `revisao_maior_vence`, `sessao_finalizada_rejeita_lote`, `batch_repetido_idempotente` |

## Verificação manual

1. Treinar offline no relógio e reconectar; confirmar ausência de duplicata.
2. Editar carga/reps no celular e relógio na mesma série; sincronizar e conferir vencedor.
3. Finalizar no celular enquanto o relógio está offline e depois reconectar.
4. Reiniciar o relógio no meio da sessão e confirmar pendências restauradas.
5. Enviar mais de 200 séries em teste controlado e conferir cap/diagnóstico.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Conflitos do relógio podem parecer ausência de sincronização se a revisão estiver incorreta.
- A migração aditiva precisa preservar `clientSetId` existente.
- Reverter o lote aditivo não remove o Room do relógio nem a fila existente.
