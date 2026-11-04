# H1-6 — Fundação de dados: migração Room, backup v2 e sync

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H1-6` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-04` |
| Implementação | `concluída` |
| Verificação | `./scripts/verify.sh` verde em 2026-11-04: 70 testes JVM do `:app` (inclui os 3 do `MigrationTest`), `:protocol`, `:wear`, lint dos dois módulos, `assembleDebug`, `apksigner verify`, Ruff + 22 testes do backend |
| Depende de | nada para o código; o teste de migração usa Robolectric + `room-testing`, já presentes |

## Objetivo

Permitir que o schema local evolua sem apagar os dados do usuário e que backup
e sincronização continuem compatíveis, criando os campos que o registro avulso
(H2-5) e o plano híbrido (H2-6) vão usar.

## Fora de escopo

- UI de modalidade, registro avulso ou abas por modalidade (isso é H2-5).
- Geração de plano de corrida/natação ou mudança no prompt/catálogo.
- Redesenho do modelo de plano (o plano continua sendo o contêiner semanal).
- Merge/conflito de backup, criptografia ou compactação.
- Wear OS / protocolo do relógio.
- Migrar os diretórios legados `shared/` ou `com.treinos.shared/`.
- Apagar ou recriar o banco do backend. A migração é aditiva.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | Versão `2`; `MIGRATION_1_2`; `addMigrations(...)`; **remover** `fallbackToDestructiveMigration()` |
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Novos campos em `WorkoutDayEntity`, `WorkoutPlanExerciseEntity` e `WorkoutSessionEntity` com `@ColumnInfo(defaultValue = ...)` quando houver default |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `deleteSession(id)` para o registro avulso; nenhuma query nova de leitura é necessária |
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | Campos novos com defaults; `schemaVersion` passa a ser `2` na exportação |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | Mapear os campos novos nos dois sentidos |
| `app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt` | Aceitar `schemaVersion` `1` e `2`; validar enums no v2 |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | Exportar v2; importar v1 e v2 preservando os defaults |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | Campos novos em `SyncDayDto`, `SyncPlanExerciseDto`, `SyncSessionDto` e nos equivalentes `SyncDownload*` |
| `back/app/main.py` | Campos novos nos modelos Pydantic de sync (com defaults) |
| `back/app/database.py` | Colunas novas no `SCHEMA`; migração aditiva em `init_db()`; corrigir a query de dias em `get_sync_download_data` |
| `app/build.gradle.kts` | Publicar `app/schemas` nos assets do **variante `debug`** (não no source set `test`): para unit tests o AGP resolve os assets pelo merge do variante, `assets/debug/mergeDebugAssets` |
| `app/schemas/com.treinos.app.data.local.AppDatabase/2.json` | **Gerado** pelo build e versionado, como o `1.json` |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | **Novo.** Migração 1 → 2 preservando linhas |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | v1 e v2 |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | Versões suportadas e enums inválidos |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | Defaults de v1 e round-trip de v2 |
| `back/tests/test_sync_contract.py` | **Novo.** Payload v1/v2, download e migração do SQLite |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Room versão 2

Campos aditivos. Todos os existentes continuam com o mesmo nome e tipo.

| Entidade | Campo | Tipo | Default | Usado por |
|---|---|---|---|---|
| `WorkoutDayEntity` | `modality` | `String` | `"strength"` | H2-6 |
| `WorkoutDayEntity` | `weekday` | `Int?` | `null` | H2-6 (1 = segunda … 7 = domingo; `null` = rotativo) |
| `WorkoutPlanExerciseEntity` | `metricType` | `String` | `"reps_weight"` | H2-6 (`reps_weight`, `duration`, `distance_duration`) |
| `WorkoutPlanExerciseEntity` | `targetDurationSeconds` | `Int?` | `null` | H2-6 |
| `WorkoutPlanExerciseEntity` | `targetDistanceMeters` | `Double?` | `null` | H2-6 |
| `WorkoutPlanExerciseEntity` | `targetPaceSecondsPerKm` | `Int?` | `null` | H2-6 (corrida) |
| `WorkoutPlanExerciseEntity` | `targetHrZone` | `String?` | `null` | H2-6 |
| `WorkoutSessionEntity` | `modality` | `String` | `"strength"` | H2-5 |
| `WorkoutSessionEntity` | `source` | `String` | `"plan"` | H2-5 (`plan` ou `manual`) |
| `WorkoutSessionEntity` | `title` | `String` | `""` | H2-5 |
| `WorkoutSessionEntity` | `totalDistanceMeters` | `Double?` | `null` | H2-5 |
| `WorkoutSessionEntity` | `poolLengthMeters` | `Int?` | `null` | H2-5 (natação: 25, 50 ou `null`) |
| `WorkoutSessionEntity` | `stroke` | `String?` | `null` | H2-5 (natação: crawl, costas, peito, borboleta, misto, outro) |
| `WorkoutSessionEntity` | `sessionRpe` | `Double?` | `null` | H2-5 |

`planId` e `dayId` de `WorkoutSessionEntity` já têm default `0`; não mudam.
`SetLogEntity` não muda.

### Migração 1 → 2

```sql
ALTER TABLE workout_days ADD COLUMN modality TEXT NOT NULL DEFAULT 'strength';
ALTER TABLE workout_days ADD COLUMN weekday INTEGER;

ALTER TABLE workout_plan_exercises ADD COLUMN metricType TEXT NOT NULL DEFAULT 'reps_weight';
ALTER TABLE workout_plan_exercises ADD COLUMN targetDurationSeconds INTEGER;
ALTER TABLE workout_plan_exercises ADD COLUMN targetDistanceMeters REAL;
ALTER TABLE workout_plan_exercises ADD COLUMN targetPaceSecondsPerKm INTEGER;
ALTER TABLE workout_plan_exercises ADD COLUMN targetHrZone TEXT;

ALTER TABLE workout_sessions ADD COLUMN modality TEXT NOT NULL DEFAULT 'strength';
ALTER TABLE workout_sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'plan';
ALTER TABLE workout_sessions ADD COLUMN title TEXT NOT NULL DEFAULT '';
ALTER TABLE workout_sessions ADD COLUMN totalDistanceMeters REAL;
ALTER TABLE workout_sessions ADD COLUMN poolLengthMeters INTEGER;
ALTER TABLE workout_sessions ADD COLUMN stroke TEXT;
ALTER TABLE workout_sessions ADD COLUMN sessionRpe REAL;
```

Os `@ColumnInfo(defaultValue = ...)` das entidades devem bater com o SQL acima;
o teste de migração falha se o schema exportado divergir.

### Backup JSON

Exportar sempre `schemaVersion = 2`. Importar `1` e `2`.

```json
{
  "schemaVersion": 2,
  "plans": [
    {
      "days": [
        {
          "id": 10,
          "modality": "strength",
          "weekday": null,
          "exercises": [
            {
              "id": 100,
              "metricType": "reps_weight",
              "targetDurationSeconds": null,
              "targetDistanceMeters": null,
              "targetPaceSecondsPerKm": null,
              "targetHrZone": null
            }
          ]
        }
      ]
    }
  ],
  "sessions": [
    {
      "id": 50,
      "planId": 0,
      "dayId": 0,
      "modality": "swim",
      "source": "manual",
      "title": "Natação",
      "totalDistanceMeters": 1500.0,
      "poolLengthMeters": 25,
      "stroke": "crawl",
      "sessionRpe": 7.0,
      "notes": "8 x 100 m crawl"
    }
  ]
}
```

Compatibilidade:

| Cenário | Resultado |
|---|---|
| App novo exporta v2 | Arquivo com todos os campos |
| App novo importa v1 | Campos novos recebem os defaults; dados antigos preservados |
| App novo importa v2 | Round-trip exato |
| App antigo importa v2 | Rejeita com “versão não suportada” (documentado) |
| `schemaVersion` 3+ | Rejeita com mensagem clara |

### Sync app ↔ backend

Os DTOs ganham os mesmos campos, todos com default. O backend usa
`model_config` padrão do Pydantic, então payload antigo continua válido e
campos desconhecidos são ignorados.

Colunas novas no SQLite do backend:

- `synced_plan_days`: `modality`, `weekday`
- `synced_plan_exercises`: `metric_type`, `target_duration_seconds`,
  `target_distance_meters`, `target_pace_seconds_per_km`, `target_hr_zone`
- `synced_sessions`: `modality`, `source`, `title`, `total_distance_meters`,
  `pool_length_meters`, `stroke`, `session_rpe`

`init_db()` deve rodar uma migração aditiva depois do `CREATE TABLE IF NOT EXISTS`:
ler `PRAGMA table_info(<tabela>)` e executar `ALTER TABLE ... ADD COLUMN` apenas
para colunas ausentes. Banco novo nasce completo; banco antigo preserva linhas.

## Regras e casos de borda

1. Instalação nova cria o banco direto na versão 2, sem rodar a migração.
2. Upgrade de v1 com planos, dias, exercícios, sessões e séries preserva todas as
   linhas; os campos novos recebem `strength`/`plan`/`reps_weight`/vazio/`null`.
3. `fallbackToDestructiveMigration()` é removido: se faltar uma migração, o app
   falha explícita em vez de apagar dados.
4. Backup v1 importa sem erro e sem perder nada; os campos novos ficam nos
   defaults.
5. Backup v2 exportado e reimportado mantém `modality`, `source`, `title`,
   `totalDistanceMeters`, `poolLengthMeters`, `stroke`, `sessionRpe` e os alvos
   de exercício.
6. `schemaVersion` fora de `{1, 2}` é rejeitado com mensagem que mostra a versão
   encontrada e as suportadas.
7. No v2, `modality` fora de `{strength, run, swim, mobility, other}`, `source`
   fora de `{plan, manual}` ou `metricType` fora de
   `{reps_weight, duration, distance_duration}` é rejeitado na validação.
   `poolLengthMeters` fora de `{25, 50}` (ou `null`) e `stroke` fora de
   `{crawl, costas, peito, borboleta, misto, outro}` (ou `null`) também são
   rejeitados.
8. Sessão com `planId = 0` e `dayId = 0` sobrevive a exportar, importar,
   sincronizar e baixar do backend.
9. Sessão com `source = "manual"` e zero `SessionExerciseEntity` sobrevive ao
   round-trip; o histórico não pode assumir que existe exercício.
10. Backend com banco antigo sobe, adiciona as colunas e mantém os snapshots
    existentes; `get_sync_download_data` continua devolvendo os dias.
11. A query de dias em `get_sync_download_data` usa o `id` interno de
    `synced_plans`, não o `local_id`. Cenário de regressão: `local_id != id`.
12. App antigo enviando payload sem os campos novos é aceito; app novo enviando
    para backend antigo também (campos extras ignorados), mas o download não
    traz os campos e o app aplica defaults.
13. Backup v1 com `daysPerWeek` inválido continua sendo rejeitado pelas regras
    atuais; a mudança de versão não relaxa as validações existentes.

## Critérios de aceite

1. **Dado** um banco v1 com um plano, um dia, um exercício, uma sessão e uma
   série, **quando** o app atualiza para v2, **então** todas as linhas continuam
   existindo e os campos novos têm os defaults.
2. **Dado** um backup v1, **quando** o usuário importa, **então** o import
   conclui e os dados ficam iguais aos atuais.
3. **Dado** um backup v2 com uma sessão de natação avulsa, **quando** exporta,
   apaga tudo e importa, **então** a sessão volta com modalidade, distância, RPE
   e notas.
4. **Dado** um backup com `schemaVersion: 3`, **quando** o usuário tenta
   importar, **então** vê “versão não suportada” e nada é apagado.
5. **Dado** um backend com banco antigo, **quando** sobe, **então** as colunas
   novas existem e os snapshots anteriores continuam disponíveis no download.
6. **Dado** um plano com `local_id` diferente do `id` interno, **quando** o app
   baixa o snapshot, **então** os dias e exercícios vêm preenchidos.
7. **Dado** um payload de sync antigo (sem campos novos), **quando** o backend
   recebe, **então** responde `ok` e grava os defaults.
8. **Dado** um payload v2 com `modality: "swim"` e `source: "manual"`, **quando**
   sincroniza e baixa, **então** os valores voltam iguais.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_1_2_preserva_planos_e_sessoes`, `migracao_1_2_aplica_defaults`, `banco_novo_ja_e_v2` |
| `app/src/test/java/com/treinos/app/data/backup/BackupSerializationTest.kt` | `exporta_v2_com_campos_novos`, `importa_v1_com_defaults`, `round_trip_v2` |
| `app/src/test/java/com/treinos/app/data/backup/BackupValidatorTest.kt` | `aceita_v1`, `aceita_v2`, `rejeita_v3`, `rejeita_modality_invalida`, `rejeita_source_invalido`, `rejeita_metric_type_invalido`, `rejeita_piscina_invalida`, `rejeita_stroke_invalido` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMapperTest.kt` | `sessao_manual_com_plan_id_zero`, `sessao_com_piscina_e_stroke`, `dia_sem_weekday`, `exercicio_sem_alvos` |
| `back/tests/test_sync_contract.py` | `test_payload_v1_sem_campos_novos`, `test_payload_v2_com_campos_novos`, `test_download_round_trip`, `test_download_dias_com_local_id_diferente_do_id`, `test_migracao_aditiva_preserva_dados` |

`MigrationTest.kt` usa `RobolectricTestRunner`, `MigrationTestHelper` e o schema
`app/schemas/.../2.json`. Se o helper não funcionar no Robolectric, o teste
instrumentado equivalente vai para `app/src/androidTest/` e a spec deve ser
atualizada antes da implementação — não remova o teste.

## Verificação manual

1. Instale a versão atual (v1), crie um plano, faça uma sessão e registre séries.
2. Instale o build novo por cima, sem desinstalar; confirme plano e histórico.
3. Exporte um backup; confira `schemaVersion: 2` e os campos novos.
4. Importe um backup v1 antigo (fixture ou arquivo real) e confirme os dados.
5. Suba o backend com um `treinos.db` antigo; confira no download que os dias
   continuam vindo.
6. Sincronize uma sessão avulsa (`planId = 0`) e restaure pelo backend.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Perda de dados na migração:** o teste 1 → 2 é obrigatório; não remova
  `fallbackToDestructiveMigration()` sem a migração. Rollback de código não
  desfaz a versão do banco; o usuário deve exportar backup antes de testar.
- **Divergência de schema:** `@ColumnInfo(defaultValue = ...)` precisa bater com
  o SQL; o `MigrationTestHelper` detecta.
- **Backup v2 em app antigo:** rejeitado de propósito. Comunicar no README que
  backups v2 exigem app novo.
- **Backend sem migração:** `ALTER TABLE` só adiciona colunas; não apaga. Se
  falhar, o backend não sobe e o app continua offline.
- **Bug de `local_id`:** corrigir junto com o teste de regressão, senão o
  download pode voltar vazio mesmo com dados no backend.
