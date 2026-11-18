# H5-2 — Geração assíncrona durável no backend

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H5-2` |
| MoSCoW | `M` |
| Versão alvo | `Horizon 5` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |
| Depende de | H3-40 |

## Objetivo

Garantir que uma geração solicitada sobreviva a reinício, migração, queda ou troca do processo backend, sem fila externa sempre ligada.

## Fora de escopo

- Redis, RabbitMQ, Celery ou outro worker externo.
- Múltiplos nós backend concorrentes.
- Notificação push de conclusão.
- Execução em segundo plano no celular.
- Repetir automaticamente uma geração que terminou em `failed`.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/database.py` | Estender `generation_jobs` com lease, tentativas, `availableAt`, `workerId` e índices. |
| `back/app/main.py` | Startup worker processa jobs pendentes; revive jobs interrompidos e renova lease. |
| `back/app/job_worker.py` | Novo módulo puro de lease/scheduler/reprocessamento. |
| `back/tests/test_generation_persistence.py` | Reinício, lease, retry, dedupe e recuperação. |
| `back/tests/test_generation_jobs.py` | Adaptar testes existentes ao reprocessamento durável. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Exibir estados persistidos sem perder o envelope do app. |

## Modelo de dados / contrato

```text
generation_jobs aditivo:
  attempt INTEGER NOT NULL DEFAULT 0
  max_attempts INTEGER NOT NULL DEFAULT 2
  available_at TEXT
  lease_owner TEXT
  lease_expires_at TEXT
  interrupted_at TEXT
  status: pending|running|done|failed|cancelled

Startup:
  running com lease expirado  -> pending
  pending                    -> reprocessar
  running com lease vigente  -> aguardar
  failed/cancelled/done      -> sem ação

Lease:
  claim: um worker pega o primeiro pending cujo available_at <= now
  renewal: antes do fim da chamada, renova o lease
  expiration: outro worker/reinício recoloca o job em pending
```

O contrato HTTP existente de H3-40 não muda. O backend continua encerrando o job como `done`, `failed` ou `cancelled` no SQLite.

## Regras e casos de borda

- Restart marca somente leases expirados como pendentes; não duplica jobs em execução de outra instância viva.
- Um job não pode ser executado por dois workers ao mesmo tempo.
- Falha temporária usa no máximo `max_attempts`; backoff cresce 5 s, 30 s e não excede o orçamento configurado.
- Cancelamento vence uma tentativa em voo; o resultado não é gravado como `done`.
- Job pendente mais antigo é processado primeiro; novo submit cancela o ativo anterior e mantém last-write-wins.
- Job com request/storage/catálogo inválido vira `failed` sem reprocessamento infinito.
- Shutdown espera o encerramento seguro ou deixa o lease expirar.
- Limpeza remove jobs terminalmente antigos somente por período configurado e nunca remove a geração feita no banco local.

## Critérios de aceite

1. **Dado** um job `pending`, **quando** o backend reinicia, **então** o startup o recoloca na fila e o conclui sem novo submit.
2. **Dado** um job `running` com lease expirado, **quando** outro processo inicia, **então** ele volta a `pending` e não duplica geração concluída.
3. **Dado** cancelamento durante uma tentativa, **quando** a task retorna, **então** o resultado não substitui `cancelled`.
4. **Dado** duas chamadas ao mesmo `jobId`, **quando** há reinício ou recovery, **então** a geração fica registrada uma única vez.
5. **Dado** falha temporária, **quando** há tentativa restante, **então** o job faz retry limitado; após o limite vira `failed`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_generation_persistence.py` | `test_startup_reprocessa_pending`, `test_running_lease_expirado_vira_pending`, `test_cancel_vence_tentativa`, `test_duplicate_job_idempotente`, `test_lease_impede_duplicacao`, `test_retry_limitado` |
| `back/tests/test_generation_jobs.py` | Ajustar status de reinício e cancelar job em execução |

## Verificação manual

1. Solicitar geração, matar o backend durante a IA e reiniciar; conferir que o mesmo job continua.
2. Solicitar cancelamento, reiniciar e confirmar que ele permanece cancelado.
3. Causar falha temporária controlada e conferir retry limitado.
4. Reiniciar com dois jobs pendentes e verificar ordem e ausência de duplicatas.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Um crash abrupto no meio da IA deixa o lease expirar; a geração pode ser repetida uma vez, com custo adicional.
- Persistir o request é necessário, mas não pode registrar chave da SenseNova ou token no job.
- Se recovery falhar, o app continua exibindo o envelope e a opção de tentar de novo.
- Reverter o worker deixa os campos aditivos no SQLite; os endpoints H3-40 continuam respondendo.
