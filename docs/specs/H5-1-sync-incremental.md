# H5-1 — Sincronização incremental e idempotente

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H5-1` |
| MoSCoW | `M` |
| Versão alvo | `Horizon 5` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída em 2026-11-18` |
| Depende de | H1-6, H1-7, H3-15, H3-40 |

## Objetivo

Fazer a sincronização enviar apenas mudanças locais, receber apenas mudanças novas e repetir um envio sem duplicar ou apagar dados.

## Fora de escopo

- Login, contas, grupos familiares ou cobrança.
- Sincronização contínua em segundo plano.
- Editor colaborativo ou merge campo a campo.
- Alterar o formato de backup JSON.
- Sincronizar mídia de perfil/fotos de progresso pelo backend.
- Substituir o restore completo da H1-7.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Adicionar metadados de revisão local nas entidades sincronizáveis e entradas de exclusão tombstone. |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | Inserir/alterar/excluir pelas mudanças recebidas em transações idempotentes. |
| `app/src/main/java/com/treinos/app/data/SyncOutbox.kt` | Novo outbox puro local: fila de mutações pendentes, cursor recebido e dedupe de `mutationId`. |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | Trocar o snapshot manual por um envio incremental e aplicar o lote recebido. |
| `app/src/main/java/com/treinos/app/data/remote/BackendApi.kt` | Novos métodos `syncChanges` e `syncChangesStatus` mantendo os métodos atuais de compatibilidade. |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | DTOs aditivos de outbox, cursor, operação, revisão e exclusão. |
| `back/app/database.py` | Tabelas `sync_mutations` e `sync_devices` com cursor por dispositivo/espaço, sequências e tombstones. |
| `back/app/main.py` | Endpoints aditivos `/v1/sync/changes` e `/v1/sync/changes/status`. |
| `back/tests/test_sync_incremental.py` | Contrato, cursor, dedupe, exclusão e conflitos determinísticos. |
| `app/src/test/java/com/treinos/app/data/SyncOutboxTest.kt` | Codificação, dedupe, retry e rollback da fila. |
| `app/src/test/java/com/treinos/app/data/SyncMergeTest.kt` | Merge de alteração local/remota e preservação de dados. |

## Modelo de dados / contrato

```text
POST /v1/sync/changes
{
  "syncSpaceId": "<uuid>",
  "deviceId": "<device>",
  "baseCursor": "<cursor>",
  "mutations": [
    {
      "mutationId": "<uuid>",
      "entity": "plan|session|set_log|exercise|body_measurement",
      "entityId": "<id>",
      "operation": "upsert|delete",
      "updatedAt": "<epoch-ms>",
      "payload": { ... }
    }
  ]
}

200 {
  "accepted": ["<mutationId>"],
  "ignored": ["<mutationId>"],
  "cursor": "<cursor>",
  "changes": [<SyncMutation>]
}

GET /v1/sync/changes?syncSpaceId=<uuid>&deviceId=<device>&cursor=<cursor>
200 { "cursor": "<cursor>", "changes": [<SyncMutation>] }

Regra de revisão:
  updatedAt é uma hora local do dispositivo normalizada pelo relógio do servidor.
  entidade remota com updatedAt maior vence.
  empate usa mutationId em ordem lexicográfica crescente.
  delete vence upsert se updatedAt >= 0 e tiver revisão igual ou maior.
```

O app mantém outbox persistente em Room. Um item só sai da fila após confirmação do backend. A mudança recebida é aplicada em uma transação. `/v1/sync` e `/v1/sync/download` continuam como fallback até o contrato novo ficar estável.

## Regras e casos de borda

- Mutação reenviada com o mesmo `mutationId` nunca cria entidade duas vezes.
- Mutação rejeitada por revisão antiga não apaga uma entidade nova.
- Exclusão inexistente é uma operação idempotente bem-sucedida.
- Payload inválido no upsert não altera dados locais nem avança o cursor.
- Cursor ausente inicia uma reconstrução controlada; não substitui dados localmente sem confirmação.
- Falha HTTP mantém todo o outbox e `lastSyncAt` anterior.
- App fechado no meio da aplicação recebe o mesmo lote novamente pelo cursor e não duplica.
- Plano ou sessão removida propaga uma tombstone; filhos são removidos em cascata controlada.
- Dados locais criados fora da área sincronizável permanecem intactos.

## Critérios de aceite

1. **Dado** uma alteração local, **quando** a sincronização termina, **então** apenas essa mutação é enviada e o servidor persiste uma revisão.
2. **Dado** uma mutação reenviada duas vezes, **quando** o servidor processa, **então** ela é aceita uma vez.
3. **Dado** uma exclusão seguida de sincronização, **quando** outro dispositivo baixa as mudanças, **então** a entidade é removida uma vez.
4. **Dado** conflito entre dispositivo A e B, **quando** ambos sincronizam, **então** vence deterministicamente a revisão maior.
5. **Dado** falta de rede no meio do lote, **quando** o app reabre, **então** o lote continua no outbox e `lastSyncAt` não avança além do cursor confirmado.
6. **Dado** backend antigo, **quando** o app usa o contrato novo, **então** recebe erro acionável e mantém os dados locais.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_sync_incremental.py` | `test_mutation_round_trip`, `test_mutation_idempotente`, `test_delete_tombstone`, `test_cursor_incremental`, `test_updated_at_empate`, `test_payload_invalido_nao_avanca_cursor` |
| `app/src/test/java/com/treinos/app/data/SyncOutboxTest.kt` | `mutation_id_persistido`, `sucesso_remove_da_fila`, `falha_preserva_fila`, `cursor_corrompido_usa_fallback` |
| `app/src/test/java/com/treinos/app/data/SyncMergeTest.kt` | `revisao_maior_vence`, `empate_usa_mutation_id`, `delete_vence`, `aplicacao_em_lote_atomica` |

## Verificação manual

1. Criar, editar e excluir uma sessão em um dispositivo, sincronizar e confirmar no segundo.
2. Desligar a rede após o envio iniciado e repetir; conferir que não há duplicatas.
3. Alterar o mesmo objeto em dois dispositivos fora de rede e sincronizar; conferir a regra determinística.
4. Matar o app após baixar parte do lote e reabrir; conferir continuidade pelo cursor.
5. Confirmar que nenhum dado local é apagado após falha de rede ou payload inválido.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Conflitos silenciosos podem surpreender; a regra last-write-wins precisa ser comunicada na tela de sincronização.
- O relógio usa IDs locais: o contrato deve respeitar `clientSetId` e não usar apenas PK para dedupe.
- A migração incremental requer transições aditivas no Room e no SQLite; reverter a fila no app não apaga dados locais.
- `/v1/sync` e `/v1/sync/download` permanecem até o novo fluxo ser verde.
