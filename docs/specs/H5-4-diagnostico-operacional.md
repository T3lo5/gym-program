# H5-4 — Diagnóstico operacional do backend sob demanda

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H5-4` |
| MoSCoW | `S` |
| Versão alvo | `Horizon 5` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída em 2026-11-18` |
| Depende de | H3-29, H3-40, H3-4 |

## Objetivo

Dar ao usuário uma forma clara de saber por que geração, sync ou backend falharam, sem divulgar dados ou logs sensíveis.

## Fora de escopo

- Telemetria comercial, analytics ou contas.
- Painel de administração público.
- Métricas por usuário ou armazenamento de texto de treino.
- Tracing distribuído.
- Envio automático de logs para terceiros.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/main.py` | Endpoint autenticado de diagnóstico agregado. |
| `back/app/diagnostics.py` | Novo módulo puro de contagem, estado do catálogo e verificação de banco. |
| `back/tests/test_diagnostics.py` | Redação, estados de backend/job/sync e ausência de dados. |
| `app/src/main/java/com/treinos/app/data/BackendDiagnostics.kt` | Novo DTO e mapper de saúde operacional. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Último resultado de geração/sync e capacidade remota. |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Card “Diagnóstico” com ação copiar/limpar sem dados pessoais. |

## Modelo de dados / contrato

```text
GET /v1/diagnostics
Authorization: Bearer <token>

200 {
  "status": "healthy|degraded",
  "timestamp": "<ISO>",
  "catalogLoaded": true,
  "databaseWritable": true,
  "generation": {
    "pending": 0,
    "running": 0,
    "failed24h": 0
  },
  "sync": {
    "lastAcceptedAt": "<ISO|null>",
    "mutations24h": 0
  }
}
```

O endpoint não retorna `request_json`, `result_json`, `deviceId`, URL pública, chave IA ou conteúdo de plano.

## Regras e casos de borda

- Sem API token em modo LAN, diagnóstico pode exigir apenas localhost/LAN.
- Com API token ativo, `/v1/diagnostics` exige Bearer.
- `/health` continua público e mínimo.
- Contadores são agregados e não identificam usuário/dispositivo.
- Falha de banco retorna `degraded`, nunca detalhes de caminho ou SQL.
- Card no app não registra token ou dados do backup.
- Ação “Copiar diagnóstico” gera apenas texto técnico redigido.
- Logs continuam no backend; não são enviados pelo app.

## Critérios de aceite

1. **Dado** diagnóstico autorizado, **quando** chamado, **então** informa catálogo, banco e contadores agregados.
2. **Dado** token errado, **quando** chamado, **então** responde 401.
3. **Dado** payload do endpoint, **quando** auditado, **então** não contém IDs de dispositivo, requests ou resultados.
4. **Dado** banco indisponível, **quando** o diagnóstico roda, **então** marca estado degradado sem caminho interno.
5. **Dado** UI no app, **quando** o usuário copia o diagnóstico, **então** nenhuma chave ou URL sensível é incluída.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_diagnostics.py` | `test_diagnostics_redigido`, `test_requires_token`, `test_database_unavailable`, `test_aggregate_counts_only`, `test_health_mantem_payload_minimo` |
| `app/src/test/java/com/treinos/app/data/BackendDiagnosticsTest.kt` | `serializa_sem_segredos`, `estado_degradado`, `mensagem_acao` |

## Verificação manual

1. Abrir Ajustes com backend remoto disponível e conferir diagnóstico agregado.
2. Copiar diagnóstico e conferir ausência de token, deviceId e dados de treino.
3. Encerrar SQLite ou configurar estado degradado controlado e confirmar mensagem segura.
4. Remover o token e confirmar que o diagnóstico não vaza em modo público.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Métricas podem virar conteúdo sensível se contarem por device; a spec fixa agregação global.
- Diagnóstico não substitui backup nem restauração.
- Reverter o endpoint não afeta `/health`, geração ou sync.
