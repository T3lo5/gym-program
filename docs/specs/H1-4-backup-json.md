# H1-4 — Backup JSON manual (exportar/importar)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-4` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `concluída` (lógica); a ligação dos botões/file pickers é concluída em H1-7 |

## Objetivo

Permitir que o usuário salve **todos** os planos, sessões e séries em um arquivo
JSON no próprio aparelho e restaure esse arquivo depois — inclusive após
desinstalar/reinstalar o app — sem depender do backend.

## Fora de escopo

- Merge/ conflito entre backup e dados atuais. O import **substitui tudo**.
- Criptografia, senha ou compactação do arquivo.
- Backup automático/agendado ou para nuvem.
- Exportar `backendUrl`, `lastSyncAt` ou qualquer segredo.
- Sincronizar o `deviceId` automaticamente na importação (isso é H1-7).
- Mídia de exercícios, dados do Wear OS ou preferências de UI.
- Migração de schema Room (o schema do banco **não muda**).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | **Novo.** DTOs `@Serializable` do arquivo |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | **Novo.** Entidades Room ↔ DTOs e listas planas |
| `app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt` | **Novo.** Validação pura + `BackupValidationResult` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | Inserções em lote e `replaceAllData(...)` transacional |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `exportBackup(...)` e `importBackup(...)` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Estados e ações de export/import |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Passar estados/callbacks para `SettingsScreen`; os file pickers são ligados em H1-7 |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Cards, confirmação e mensagens; a ligação dos file pickers é concluída em H1-7 |
| `app/src/test/java/com/treinos/app/data/backup/*Test.kt` | **Novos.** Serialização, mapper e validação |

Não altere `app/src/main/java/com/treinos/shared/` (legado) nem os módulos raiz
`shared/`/`wear/`.

## Modelo de dados / contrato

Arquivo JSON, `schemaVersion = 1`, UTF-8, identado com 2 espaços.

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-11-03T18:00:00Z",
  "app": {
    "packageName": "com.treinos.app",
    "versionName": "1.0.41",
    "versionCode": 41
  },
  "deviceId": "uuid-do-aparelho",
  "plans": [
    {
      "id": 1,
      "serverPlanId": null,
      "name": "Full Body A",
      "description": "",
      "goal": "hypertrophy",
      "daysPerWeek": 4,
      "isActive": true,
      "generatedByAi": false,
      "createdAt": 1760000000000,
      "updatedAt": 1760000000000,
      "version": 1,
      "nextReviewAt": "2026-12-01",
      "reviewAfterWeeks": 4,
      "source": "local",
      "days": [
        {
          "id": 10,
          "name": "Treino A",
          "dayOrder": 0,
          "notes": "",
          "isRestDay": false,
          "exercises": [
            {
              "id": 100,
              "exerciseId": "Barbell_Bench_Press",
              "exerciseName": "Supino reto",
              "primaryMuscles": "chest",
              "equipment": "barbell",
              "exerciseOrder": 0,
              "sets": 3,
              "targetReps": "8-12",
              "targetRpe": 8.0,
              "restSeconds": 90,
              "notes": "",
              "isSupersetWithPrevious": false
            }
          ]
        }
      ]
    }
  ],
  "sessions": [
    {
      "id": 50,
      "planId": 1,
      "dayId": 10,
      "planName": "Full Body A",
      "dayName": "Treino A",
      "status": "completed",
      "startedAt": 1760000000000,
      "endedAt": 1760003600000,
      "totalDurationSeconds": 3600,
      "notes": "",
      "exercises": [
        {
          "id": 500,
          "exerciseId": "Barbell_Bench_Press",
          "exerciseName": "Supino reto",
          "exerciseOrder": 0,
          "plannedSets": 3,
          "completedSets": 1,
          "restSeconds": 90,
          "startedAt": 1760000000000,
          "endedAt": null,
          "sets": [
            {
              "id": 5000,
              "setNumber": 1,
              "reps": 10,
              "weightKg": 40.0,
              "rpe": 8.0,
              "durationSeconds": null,
              "distanceMeters": null,
              "isCompleted": true,
              "isWarmup": false,
              "restTakenSeconds": 90,
              "completedAt": 1760000100000
            }
          ]
        }
      ]
    }
  ]
}
```

Regras do contrato:

- `schemaVersion` é obrigatório e deve ser exatamente `1`.
- `app.packageName` deve ser `com.treinos.app`.
- `deviceId` é **informativo**; o import não altera o `deviceId` local.
- `plans` e `sessions` podem ser vazios (`[]`).
- `exportedAt` em ISO-8601 UTC.
- Chaves desconhecidas são ignoradas na leitura (compatibilidade futura).
- `Json` config: `prettyPrint = true`, `encodeDefaults = true`,
  `ignoreUnknownKeys = true`, `explicitNulls = false`.

### IDs

O import **preserva os IDs originais**. O banco local é limpo antes e as
inserções acontecem em ordem de pai para filho:

```
plans → days → plan_exercises → sessions → session_exercises → set_logs
```

A limpeza usa a ordem inversa (a `deleteAllData()` existente já faz isso).
Room grava `id = 0` como auto-incremento e `id > 0` como valor explícito; após o
import, `sqlite_sequence` fica no maior ID inserido.

### API interna sugerida

```kotlin
// WorkoutRepository
suspend fun exportBackup(appVersionName: String, appVersionCode: Long): String
suspend fun importBackup(payload: BackupFile)

// AppViewModel
val backupImportState: StateFlow<BackupImportState>
val backupMessage: StateFlow<String?>

suspend fun exportBackupJson(): String
fun prepareImport(json: String)   // parse + validate -> Ready/Error
fun confirmImport()               // grava em transação
fun cancelImport()

sealed interface BackupImportState {
    data object Idle : BackupImportState
    data object Reading : BackupImportState
    data class Ready(val preview: BackupPreview, val payload: BackupFile) : BackupImportState
    data object Importing : BackupImportState
    data class Error(val message: String) : BackupImportState
}

data class BackupPreview(
    val plans: Int,
    val days: Int,
    val planExercises: Int,
    val sessions: Int,
    val sessionExercises: Int,
    val setLogs: Int,
)
```

## Regras e casos de borda

1. **Leitura limitada:** rejeitar arquivos acima de **25 MB** antes de decodificar.
2. **JSON inválido/truncado:** mensagem “Arquivo de backup inválido” e nenhum
   dado alterado.
3. **Versão desconhecida:** `schemaVersion != 1` → “Backup de versão não
   suportada (encontrada X; esperada 1)”.
4. **App errado:** `packageName != com.treinos.app` → erro claro.
5. **IDs duplicados** em qualquer nível → backup inválido.
6. **Referência órfã** (`day.planId`, `exercise.dayId`,
   `sessionExercise.sessionId`, `setLog.exerciseLogId`) → backup inválido.
7. **Sessão com `planId`/`dayId` inexistente:** permitido (sessões antigas podem
   apontar para plano apagado); não validar como órfão.
8. **Valores:** `setNumber >= 1`; `sets/plannedSets/completedSets >= 0`;
   `reps >= 0` quando presente; `weightKg >= 0` quando presente;
   `rpe` entre 1.0 e 10.0 quando presente; `daysPerWeek` entre 0 e 7.
9. **Backup vazio:** válido. Após confirmação, limpa os dados locais.
10. **Import é destrutivo:** o diálogo de confirmação mostra as contagens e o
    aviso “Os dados atuais serão substituídos”.
11. **Atomicidade:** qualquer falha durante a gravação faz rollback completo; a
    mensagem de erro aparece e os dados antigos continuam intactos.
12. **Cancelar** o seletor de arquivo ou o diálogo não altera nada.
13. **Exportar sem dados:** gera arquivo válido com listas vazias e informa
    “Nenhum dado para exportar” na UI.
14. **Offline:** export e import funcionam sem rede e sem backend.

## Critérios de aceite

1. **Dado** um banco com 1 plano, 1 dia, 2 exercícios, 1 sessão, 1 exercício de
   sessão e 2 séries, **quando** o usuário exporta, **então** o JSON contém
   `schemaVersion: 1` e todas as contagens/valores, com `deviceId` preenchido.
2. **Dado** o JSON do critério 1, **quando** o usuário limpa o app, seleciona o
   arquivo e confirma, **então** as mesmas contagens e IDs voltam ao Room.
3. **Dado** um JSON com `schemaVersion: 2`, **quando** o usuário tenta importar,
   **então** vê o erro de versão e o banco permanece inalterado.
4. **Dado** um JSON com uma referência órfã, **quando** confirma, **então** o
   import falha, a mensagem aponta o problema e o banco permanece inalterado.
5. **Dado** um backup vazio, **quando** confirma, **então** os dados locais são
   apagados e a UI mostra contagens zeradas.
6. **Dado** que o usuário cancela o seletor/diálogo, **então** nada muda.
7. **Dado** o app sem internet, **quando** exporta e importa, **então** o fluxo
   funciona igual.
8. **Dado** um arquivo de 30 MB, **quando** selecionado, **então** é rejeitado
   antes de ler o conteúdo.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | `round_trip_preserva_todos_os_campos`, `ignora_chaves_desconhecidas`, `rejeita_json_invalido`, `rejeita_schema_version_diferente` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | `entity_para_dto_e_volta`, `listas_planas_preservam_relacoes` |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | `backup_vazio_e_valido`, `rejeita_id_duplicado`, `rejeita_dia_orfao`, `rejeita_exercicio_orfao`, `rejeita_set_log_orfao`, `rejeita_set_number_invalido`, `rejeita_rpe_fora_da_faixa`, `aceita_sessao_sem_plano` |

Os testes são JVM puros (o gate roda `testDebugUnitTest`). A transação Room e o
rollback são cobertos pelo teste manual abaixo.

## Verificação manual

1. Rode o app, gere/crie um treino e registre 2 séries.
2. Ajustes → “Exportar backup” → salve `treinos-backup-*.json`.
3. Confira o arquivo: `schemaVersion`, `plans[].days[].exercises[]` e
   `sessions[].exercises[].sets[]` preenchidos.
4. Desinstale e reinstale o app (ou limpe os dados em Ajustes do Android).
5. Ajustes → “Importar backup” → selecione o arquivo → confira as contagens no
   diálogo → confirme.
6. Abra Home/Histórico e confirme plano, sessão e séries restaurados.
7. Repita o import com um JSON editado para `schemaVersion: 2` e confirme que
   aparece erro e nenhum dado muda.
8. Repita com um JSON com `days[0].planId = 999999` e confirme rollback.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Perda de dados no import:** mitigado por confirmação explícita + transação
  única. A UI deve sugerir exportar antes de importar.
- **OOM em arquivo gigante:** limite de 25 MB antes da leitura.
- **Backup corrompido:** validação antes de qualquer `DELETE`; erro não toca no
  banco.
- **Regressão de schema:** não há migration nesta entrega; qualquer mudança de
  entidade deve ser uma spec separada.
- **Rollback:** como o import é uma transação, falha = banco antigo intacto. Se
  o app fechar no meio, o SQLite desfaz a transação aberta.
