# H2-3 — Download e restauração do backend

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-3` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-04` |
| Implementação | `concluída` |
| Origem | retroativa — documenta comportamento já presente no código |
| Depende de | H1-1 (identidade por `deviceId`), H1-4 (backup local) |

## Objetivo

Baixar o snapshot completo que o backend guardou para o `deviceId` e restaurá-lo
no aparelho, para recuperar planos, sessões e séries depois de limpar os dados
ou reinstalar o app.

## Fora de escopo

- Merge entre snapshot remoto e dados locais. A restauração substitui o banco.
- Download automático/agendado.
- Download seletivo por plano ou período.
- Autenticação/token (H3-4).
- Resolver conflitos de edição entre aparelhos.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | `syncDownload(deviceId)` |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `SyncDownloadDto` e DTOs filhos |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `downloadSync()` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `downloadNow()`, estado `isDownloading`, mensagem |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Botão “Restaurar do backend” e mensagem |
| `back/app/main.py` | `GET /v1/sync/download` |
| `back/app/database.py` | `get_sync_download_data(device_id)` |
| `back/tests/test_sync_contract.py` | **Novo** em H1-6; cobre o contrato de download |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

```http
GET /v1/sync/download?deviceId=<uuid>
```

```json
{
  "deviceId": "uuid",
  "plans": [{ "localId": 1, "name": "...", "isActive": true }],
  "days": [{ "id": 10, "planId": 1, "localId": 10, "name": "Treino A" }],
  "planExercises": [{ "id": 100, "dayId": 10, "exerciseId": "..." }],
  "sessions": [{ "id": 50, "localId": 50, "planLocalId": 1 }],
  "sessionExercises": [{ "id": 500, "sessionId": 50, "localId": 500 }],
  "setLogs": [{ "id": 5000, "sessionExerciseId": 500, "localId": 5000 }]
}
```

Regras:

- O app busca o snapshot, apaga o banco local (`deleteAllData`) e reinsere tudo.
- Planos são inseridos primeiro; `planId` e `dayId` das sessões são remapeados
  para os novos IDs do Room.
- Exatamente um plano fica ativo depois da restauração.
- Sessões com `planId = 0` (registro avulso) continuam válidas.
- Se o snapshot vier vazio, o app mostra “Nenhum dado encontrado” e o banco local
  fica vazio — o usuário deve ter backup JSON antes.

## Regras e casos de borda

1. Backend desligado/URL errada → erro de conexão; o banco local só é apagado
   depois que o download retorna com sucesso.
2. Snapshot vazio → banco local vazio; mensagem explícita.
3. Snapshot com plano ativo → esse plano vira o ativo; sem ativo, o primeiro.
4. Snapshot com dias/exercícios órfãos → são ignorados ou causam erro claro; não
   criar plano pela metade.
5. Sessão sem plano → preservada.
6. Série sem exercício de sessão → não deve ser inserida; validação no backend.
7. Download concorrente → segundo toque é ignorado enquanto `isDownloading`.
8. Falha no meio da inserção → hoje o banco pode ficar parcial; o caminho
   recomendado é exportar backup antes e usar a transação única de H1-7.
9. Texto da tela de Ajustes que diz “Não é feito download” está desatualizado e
   é corrigido em H1-7.

## Critérios de aceite

1. **Dado** que o backend tem snapshot do `deviceId`, **quando** toca em
   “Restaurar do backend”, **então** planos, sessões e séries voltam.
2. **Dado** que o snapshot tem um plano ativo, **então** a tela inicial mostra
   esse plano.
3. **Dado** que o snapshot tem uma sessão avulsa (`planId = 0`), **então** ela
   aparece no histórico.
4. **Dado** que o backend está desligado, **então** o app mostra erro e não apaga
   os dados locais.
5. **Dado** que o snapshot está vazio, **então** o app avisa e o banco fica
   vazio.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_sync_contract.py` | `test_download_retorna_snapshot`, `test_download_sem_dados`, `test_download_dias_com_local_id_diferente_do_id` |
| `app/src/test/java/com/treinos/app/data/remote/SyncDownloadMapperTest.kt` | `mapeia_planos_dias_exercicios`, `remapeia_plan_id_das_sessoes`, `preserva_sessao_sem_plano` |
| `app/src/test/java/com/treinos/app/data/local/RestoreDaoTest.kt` | `restauracao_deixa_um_plano_ativo`, `restauracao_substitui_dados_antigos` |

Os testes de Room ficam em H3-1; o teste de contrato do backend entra em H1-6.

## Verificação manual

1. Sincronize um plano com sessões.
2. Limpe os dados do app (ou reinstale).
3. Configure a URL e toque em “Restaurar do backend”.
4. Confira plano ativo, histórico e séries.
5. Repita com o backend desligado e confirme que os dados locais permanecem.
6. Crie um `deviceId` sem snapshot e confirme a mensagem de vazio.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Restauração não atômica:** apagar e reinserir em passos separados pode deixar
  o banco parcial se o app morrer no meio. Mitigação: backup JSON antes; a
  transação única é implementada em H1-7.
- **Snapshot vazio destrutivo:** apaga o local. Mitigação: aviso claro e backup
  antes.
- **Contrato divergente:** o teste de contrato de H1-6 precisa cobrir os campos
  novos.
- **Rollback:** desativar o botão de download; os dados já restaurados
  permanecem.
