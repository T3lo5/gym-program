# H1-7 — Correções de restauração, backup e Ajustes

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-7` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-04` |
| Implementação | `concluída` |
| Verificação | `./scripts/verify.sh` verde em 2026-11-04: 85 testes JVM do `:app` (inclui `RestoreDownloadTest` 8, `BackupImportTest` 3 e `RestoreDeviceIdTest` 4), `:protocol`, `:wear`, lint dos dois módulos, `assembleDebug`, `apksigner verify`, Ruff + 22 testes do backend |
| Depende de | H1-6 (schema v2, backup v2 e sync aditivo) |

### Notas de implementação

- A costura de testabilidade criou `BackendApi` (implementado por
  `BackendClient`), `SnapshotDownloader` e `DeviceIdStore` (implementado por
  `AppPreferences`), permitindo testar a restauração sem rede e, no caso de
  `RestoreDeviceIdTest`, sem Robolectric.
- `WorkoutRepository.downloadSync(deviceId: String)` ficou **sem** o argumento
  com default que a spec esboçava: Kotlin proíbe default em método que
  `override` de uma interface, então quem chama passa o `deviceId`
  explicitamente (`AppViewModel.downloadNow` usa `preferences.deviceId`).
- A atomicidade usa o `@Transaction` já existente em `WorkoutDao.replaceAllData`
  (operação "ou reutilizar `replaceAllData`" prevista na spec); nenhum DAO novo
  foi criado.
- `app/build.gradle.kts` + `scripts/verify.sh` ganharam `ROBOLECTRIC_REPO_URL`:
  o Maven Central devolvia HTTP 429 para o `android-all` e derrubava o gate por
  infra. Sem a variável o comportamento é o de antes (repo1), então o CI do
  GitHub não muda.
- `restSeconds` de exercício de sessão continua fora do sync — lacuna documentada
  de H1-6, a ser tratada em H3-1.

## Objetivo

Corrigir os caminhos de restauração e backup que hoje podem perder dados, mudar
a identidade do aparelho sem sucesso ou simplesmente não fazer nada:

- os botões **Exportar backup** e **Importar backup** estão ligados a callbacks
  vazios em `TreinosApp.kt`;
- `WorkoutRepository.downloadSync()` apaga o banco local antes de inserir e não
  usa transação; um snapshot vazio ou uma falha no meio apaga o histórico;
- `AppViewModel.restoreByDeviceId(...)` muda o `deviceId` antes de confirmar o
  download e não tem tela;
- `WorkoutRepository.importBackup()` não aplica o `deviceId` que veio no arquivo;
- a tela de Ajustes ainda diz que “não é feito download de dados do servidor”.

O resultado esperado é: restaurar e importar são operações explícitas,
confirmadas, atômicas e que nunca apagam dados sem um snapshot válido.

## Fora de escopo

- Novas modalidades, registro avulso ou plano híbrido (H2-5/H2-6).
- Merge entre snapshot remoto e dados locais; a restauração continua
  substituindo o banco.
- Backup automático, agendado ou em nuvem.
- Criptografia, compressão ou senha no backup.
- Autenticação/token no backend (H3-4).
- Redesenho visual da tela de Ajustes (H3-2).
- Migração de schema, backup v2 e campos de modalidade (H1-6).
- Recuperar dados de um arquivo corrompido; o arquivo é rejeitado.
- Exportar/importar pelo Wear OS.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Implementar os file pickers de exportação/importação; passar `isRestoring`, `restoreDeviceId`, `restoreMessage` e callbacks para `SettingsScreen`; obter versão do app via `PackageManager` |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Campo “Restaurar de outro aparelho”; diálogo de confirmação; texto correto sobre download; exibir mensagem de restauração |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `downloadNow()` e `restoreByDeviceId()` usando resultado tipado; persistir `deviceId` só depois do sucesso; limpar estados |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `downloadSync(deviceId)` com validação antes de apagar, transação única e resultado `Restored`/`Empty`; `importBackup()` aplica `deviceId` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | Método `@Transaction replaceAllFromSync(...)` (ou reutilizar `replaceAllData`) para substituir tudo atomicamente |
| `app/src/test/java/com/treinos/app/data/RestoreDownloadTest.kt` | **Novo.** Snapshot vazio, transação, rollback, plano ativo e sessão avulsa |
| `app/src/test/java/com/treinos/app/data/backup/BackupImportTest.kt` | **Novo.** `deviceId` aplicado, ausente e importação inválida |
| `app/src/test/java/com/treinos/app/ui/RestoreDeviceIdTest.kt` | **Novo.** Validação e persistência do ID com fakes |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Resultado do download

```kotlin
sealed interface SyncDownloadResult {
    data class Restored(
        val planCount: Int,
        val sessionCount: Int,
    ) : SyncDownloadResult

    data object Empty : SyncDownloadResult
}

// WorkoutRepository
suspend fun downloadSync(deviceId: String = preferences.deviceId): SyncDownloadResult
```

Regras:

- A chamada de rede acontece **antes** de qualquer escrita no Room.
- `data.plans.isEmpty() && data.sessions.isEmpty()` → `Empty`; o banco local
  **não** é tocado.
- Snapshot não vazio → mapear para entidades, normalizar exatamente um plano
  ativo e substituir tudo em **uma única transação**.
- Qualquer exceção durante o mapeamento/inserção → rollback; o banco local
  permanece exatamente como estava.
- `Restored.planCount` e `Restored.sessionCount` vêm do snapshot aplicado.

### Restauração por outro `deviceId`

```kotlin
// AppViewModel
fun restoreByDeviceId(deviceId: String)
```

Regras:

- `deviceId` em branco → mensagem “Informe um ID de aparelho válido”; nenhuma
  chamada de rede.
- O ID digitado é usado **só na chamada** de download; `preferences.deviceId`
  só é atualizado depois de `Restored`.
- `Empty` ou erro → `preferences.deviceId` continua o anterior.
- Sucesso → `preferences.deviceId = deviceId`, `lastSyncAt` recarregado e
  mensagem com a contagem de planos/sessões.
- Enquanto `isRestoring` for `true`, um segundo toque é ignorado.

### Importação de backup

```kotlin
suspend fun importBackup(payload: BackupFile)
```

Regras:

- O backup continua sendo validado antes de qualquer escrita.
- A substituição usa `replaceAllData(...)` (já transacional).
- Depois do sucesso, se `payload.deviceId` não for branco, aplicar em
  `preferences.deviceId`; se for branco, manter o ID atual.
- Se a validação ou a escrita falhar, nem os dados nem o `deviceId` mudam.
- A mensagem de sucesso informa que o backup foi importado e que o ID do
  aparelho foi restaurado, quando aplicável.

### Exportação e importação na UI

| Ação | Contrato |
|---|---|
| Exportar | `ActivityResultContracts.CreateDocument("application/json")`; escrever o JSON retornado por `exportBackupJson(...)` no URI escolhido |
| Importar | `ActivityResultContracts.OpenDocument()` com `arrayOf("application/json")`; ler o texto e chamar `prepareImport(json)` |
| Cancelar picker | Nenhuma escrita, nenhuma mensagem de erro |
| Falha ao escrever | Mensagem de erro; não deixar arquivo parcial no destino |
| Versão no backup | `PackageManager.getPackageInfo(...)` (`versionName` e `longVersionCode`/`versionCode`) |

### Texto da tela de Ajustes

Substituir:

> “Não é feito download de dados do servidor.”

por um texto que explique as duas operações:

> “**Enviar ao backend** grava um snapshot no servidor. **Restaurar do backend**
> baixa o snapshot do `deviceId` e **substitui** os dados locais. Exporte um
> backup JSON antes de restaurar.”

## Regras e casos de borda

1. Exportar com o picker cancelado → nada acontece.
2. Exportar com sucesso → arquivo JSON válido, mensagem de confirmação.
3. Falha ao escrever no URI → mensagem de erro; o backup anterior no destino,
   se existir, não deve ser corrompido. Escrever primeiro em `cacheDir` e só
   copiar para o URI depois de gerar o JSON completo.
4. Importar arquivo inválido → `BackupImportState.Error`; dados e `deviceId`
   intactos.
5. Importar arquivo válido → pré-visualização; só substitui após
   “Substituir tudo”.
6. Cancelar a pré-visualização → nada muda.
7. Importar backup com `deviceId` → aplica o ID depois do sucesso.
8. Importar backup sem `deviceId`/em branco → mantém o ID atual.
9. Restaurar por ID em branco → erro inline, sem rede.
10. Restaurar por ID com backend offline → erro; `deviceId` e dados locais
    intactos.
11. Restaurar por ID com snapshot vazio → mensagem “Nenhum dado encontrado”;
    dados locais e `deviceId` intactos.
12. Restaurar por ID com snapshot válido → confirmação explícita antes de
    substituir; depois, dados e `deviceId` atualizados.
13. Restaurar pelo botão atual (`downloadNow`) com snapshot vazio → mensagem
    “Nenhum dado encontrado; seus dados locais foram mantidos”.
14. Restaurar pelo botão atual com snapshot válido → confirmação explícita;
    substituição atômica.
15. Snapshot só com planos (sem sessões) → válido.
16. Snapshot só com sessões avulsas (`planId = 0`, `dayId = 0`) → válido.
17. Snapshot com dia/exercício/sessão órfãos ou IDs `0`/negativos → rejeitar
    **antes** de apagar, com mensagem clara.
18. Snapshot com IDs duplicados → rejeitar antes de apagar.
19. Falha simulada no meio da inserção → rollback; o banco local continua com
    os dados anteriores.
20. Após restauração, exatamente um plano fica ativo; se nenhum vier ativo, o
    primeiro vira ativo.
21. Sessão avulsa (`planId = 0`) sobrevive à restauração.
22. Dois toques em “Restaurar” → o segundo é ignorado enquanto
    `isDownloading`/`isRestoring` for `true`.
23. A tela de Ajustes não pode afirmar que não há download.
24. Nenhuma operação de restauração/importação altera o schema do Room; isso é
    H1-6.

## Critérios de aceite

1. **Dado** que o usuário toca em “Exportar backup”, **quando** escolhe um
   arquivo, **então** o JSON é gravado e uma mensagem confirma.
2. **Dado** que o usuário toca em “Importar backup”, **quando** escolhe um JSON
   válido, **então** vê a pré-visualização e só substitui após confirmar.
3. **Dado** um backup com `deviceId`, **quando** importa com sucesso, **então**
   o app passa a usar esse ID.
4. **Dado** um backup sem `deviceId`, **quando** importa, **então** o ID atual é
   mantido.
5. **Dado** um backup inválido, **quando** tenta importar, **então** vê erro e
   os dados locais continuam iguais.
6. **Dado** um `deviceId` de outro aparelho com snapshot, **quando** restaura e
   confirma, **então** os dados voltam e o ID é persistido.
7. **Dado** um `deviceId` errado, **quando** restaura, **então** vê “Nenhum dado
   encontrado” e o ID anterior continua valendo.
8. **Dado** um snapshot vazio, **quando** restaura, **então** os dados locais
   são mantidos.
9. **Dado** uma falha no meio da inserção, **quando** restaura, **então** o
   banco local continua com os dados anteriores.
10. **Dado** que o usuário abre Ajustes, **então** o texto explica envio,
    restauração e o risco de substituição.
11. **Dado** que o app está offline, **então** exportar/importar backup
    continuam funcionando; restaurar do backend mostra erro.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/RestoreDownloadTest.kt` | `snapshot_vazio_nao_apaga_dados_locais`, `snapshot_valido_substitui_dados`, `falha_no_meio_faz_rollback`, `restauracao_deixa_um_plano_ativo`, `sessao_avulsa_preservada`, `snapshot_com_referencia_orfa_e_rejeitado`, `snapshot_com_id_duplicado_e_rejeitado` |
| `app/src/test/java/com/treinos/app/data/backup/BackupImportTest.kt` | `importa_backup_aplica_device_id`, `backup_sem_device_id_mantem_device_id_atual`, `importacao_invalida_nao_altera_dados_nem_device_id` |
| `app/src/test/java/com/treinos/app/ui/RestoreDeviceIdTest.kt` | `id_vazio_nao_chama_rede`, `sucesso_persiste_device_id`, `falha_mantem_device_id_anterior`, `snapshot_vazio_mantem_device_id_anterior` |

`RestoreDownloadTest` e `BackupImportTest` usam Room em memória com
Robolectric, como H1-6. `RestoreDeviceIdTest` usa fakes de repositório e
preferências; não precisa de Android.

## Verificação manual

1. Abra **Ajustes** e confira o texto novo sobre envio/restauração.
2. Toque em **Exportar backup**, escolha um arquivo e confirme que o JSON foi
   gravado.
3. Toque em **Importar backup**, escolha o arquivo, confira a pré-visualização,
   cancele e confirme que nada mudou.
4. Importe de novo e confirme “Substituir tudo”; confira planos, histórico e
   séries.
5. Use um backup com `deviceId` de outro aparelho; confirme que o ID em Ajustes
   muda após a importação.
6. Preencha “Restaurar de outro aparelho” com um ID válido e confirme; confira
   os dados e o ID persistido.
7. Repita com um ID inválido e com o backend desligado; confira que o ID e os
   dados locais não mudam.
8. Sincronize um `deviceId` sem snapshot e tente restaurar; confira que os dados
   locais permanecem.
9. Exporte um backup, desinstale, reinstale e importe; confira a recuperação
   completa.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Transação e mapeamento de IDs:** o maior risco é inserir filhos com o pai
  errado. Mitigação: testes de restauração com plano, dia, exercício, sessão e
  série; validar referências antes de apagar.
- **Snapshots antigos com IDs 0:** rejeitar antes de apagar em vez de tentar
  adivinhar; o usuário recebe erro claro e mantém os dados.
- **File picker/SAF:** permissões e URIs variam por fabricante. Mitigação:
  escrever em `cacheDir` e copiar para o URI; testar em device físico.
- **Mudança de `deviceId` na importação:** pode surpreender. Mitigação:
  mensagem explícita e possibilidade de ver o ID em Ajustes.
- **Restauração vazia deixar de limpar o banco:** é intencional. Para limpar
  tudo, o usuário desinstala ou usa o reset do app; não usar snapshot vazio como
  reset.
- **Rollback:** reverter a UI para os callbacks anteriores não apaga dados; a
  transação garante que uma falha não deixa o banco pela metade. Backups JSON
  exportados continuam válidos.
