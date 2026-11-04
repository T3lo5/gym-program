# H1-1 — Recuperação por `deviceId`

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-1` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-04` |
| Implementação | `concluída` |
| Origem | retroativa — documenta comportamento já presente no código |
| Depende de | H1-4 (backup local) para o caminho de emergência |

## Objetivo

Depois de reinstalar o app ou trocar de aparelho, o usuário consegue recuperar
planos, sessões e séries a partir do snapshot que o backend guardou para o
`deviceId` dele, sem criar conta e sem login.

## Fora de escopo

- Autenticação, senha ou login. `deviceId` **não** é autenticação.
- Transferir dados entre aparelhos digitando o ID de outro aparelho pela UI
  **neste item**. A função `AppViewModel.restoreByDeviceId(...)` existe, mas só
  é ligada à tela em H1-7; aqui o fluxo efetivo é “Restaurar do backend” com o
  ID local.
- Merge/conflito entre o snapshot remoto e os dados locais. O download
  **substitui** o banco local.
- Criptografia, compressão ou backup automático.
- Mídia de exercícios e dados do Wear OS.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | `deviceId` persistido em `SharedPreferences`; UUID gerado no primeiro acesso |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `deviceId`, `restoreByDeviceId(...)`, `downloadNow()` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `downloadSync()` |
| `app/src/main/java/com/treinos/app/data/remote/BackendClient.kt` | `syncDownload(deviceId)` |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Exibe o ID do aparelho e o botão “Restaurar do backend” |
| `back/app/main.py` | `GET /v1/sync/download` |
| `back/app/database.py` | `get_sync_download_data(device_id)` |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

```http
GET /v1/sync/download?deviceId=<uuid>
```

Resposta: `SyncDownloadDto` com listas planas de planos, dias, exercícios,
sessões, exercícios de sessão e séries.

```kotlin
// AppPreferences
var deviceId: String   // UUID; gerado se ausente

// BackendClient
suspend fun syncDownload(deviceId: String): SyncDownloadDto
```

Regras:

- O `deviceId` é gerado uma vez e persistido; reinstalar o app gera um novo ID.
  O backup JSON **contém** o `deviceId` no arquivo, mas a importação atual não o
  aplica às preferências. Para restaurar do backend, use o mesmo aparelho/ID;
  trocar o ID pelo backup é uma melhoria fora do escopo desta spec.
- O download apaga o banco local e reinsere o snapshot, preservando os IDs
  originais quando possível e remapeando `planId`/`dayId`.
- Depois da restauração, exatamente um plano fica ativo.
- Se o backend não tiver dados para o ID, o app mostra “Nenhum dado encontrado”.
- O app continua funcionando offline; o download só acontece ao tocar no botão.

## Regras e casos de borda

1. `deviceId` em branco → mensagem “Informe um ID de aparelho válido” e nenhuma
   chamada de rede.
2. Backend desligado/URL errada → mensagem de erro; os dados locais atuais
   permanecem até a chamada de download começar.
3. Snapshot vazio → mensagem “Nenhum dado encontrado”; o download atual
   **substitui** o banco local por vazio. O usuário deve exportar backup JSON
   antes de restaurar.
4. Snapshot com plano ativo → esse plano vira o ativo; se nenhum estiver ativo,
   o primeiro da lista vira ativo.
5. IDs locais do snapshot são remapeados para os novos IDs do Room; sessões e
   séries continuam ligadas ao plano/dia corretos.
6. Sessão sem plano (`planId = 0`) é preservada como veio.
7. Duas restaurações simultâneas → a segunda é ignorada enquanto
   `isDownloading`/`isRestoring` estiver verdadeiro.
8. `deviceId` é identificador de restore, não segredo: quem souber o ID consegue
   baixar o snapshot. Não expor o backend publicamente sem token (H3-4).
9. O texto da tela de Ajustes que diz “Não é feito download de dados do
   servidor” está desatualizado em relação ao botão “Restaurar do backend” e
   é corrigido em H1-7.

## Critérios de aceite

1. **Dado** que o app foi reinstalado e o backend tem snapshot do mesmo
   `deviceId`, **quando** o usuário toca em “Restaurar do backend”, **então**
   planos, sessões e séries voltam e o app mostra a contagem de planos.
2. **Dado** um `deviceId` sem snapshot, **quando** restaura, **então** vê
   “Nenhum dado encontrado” e entende que o banco local foi substituído.
3. **Dado** um snapshot com dois planos, **quando** restaura, **então** apenas um
   fica ativo e a tela inicial mostra esse plano.
4. **Dado** que o backend está desligado, **quando** tenta restaurar, **então**
   vê erro de conexão e o app continua utilizável offline.
5. **Dado** que o usuário nunca sincronizou, **quando** reinstala, **então** o
   backend não tem dados e o caminho recomendado é o backup JSON (H1-4).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/remote/SyncDownloadMapperTest.kt` | `mapeia_planos_dias_e_exercicios`, `mapeia_sessao_sem_plano`, `preserva_ids_quando_possivel` |
| `app/src/test/java/com/treinos/app/data/local/RestoreDaoTest.kt` | `restauracao_deixa_um_plano_ativo`, `restauracao_substitui_dados_antigos` |
| `back/tests/test_sync_contract.py` | `test_download_retorna_snapshot`, `test_download_sem_dados`, `test_download_por_device_id` |

Enquanto esses testes não existem, a cobertura de Room/download fica em H3-1.
Não marque H3-1 como concluída sem eles.

## Verificação manual

1. Gere um plano, faça uma sessão e sincronize.
2. Anote o `deviceId` exibido em Ajustes.
3. Desinstale e reinstale o app (ou limpe os dados).
4. Configure a mesma URL do backend e toque em “Restaurar do backend”.
5. Confirme plano, histórico e séries.
6. Teste com o backend desligado e confirme a mensagem de erro.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Download destrutivo:** o snapshot remoto substitui o banco local, inclusive
  quando vem vazio. Mitigação: exportar backup JSON antes; evoluir para
  restauração transacional/atômica em H1-7.
- **`deviceId` não é auth:** qualquer um com o ID baixa o snapshot. Mitigação:
  H3-4 (token) antes de expor o backend.
- **Perda do ID ao reinstalar:** sem backup JSON, o ID antigo se perde. Mitigação:
  H1-4 e exibir o ID em Ajustes.
- **Rollback:** remover o botão de download; os dados locais existentes não são
  afetados.
