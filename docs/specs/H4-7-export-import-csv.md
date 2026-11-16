# H4-7 — Exportar CSV e importar de Strong/Hevy

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-7` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` (pedido do dono de implementar a fila de specs) |
| Implementação | `concluída em 2026-11-15` |
| Depende de | H1-4 (mapper do backup, `BackupMapper`/`BackupDtos`), H1-7 (SAF `CreateDocument`/`OpenDocument` ligados) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 2). O item
estava como "Won't now" e um app local-first sem porta de saída contraria a
promessa de não-lock-in: hoje o único caminho de saída é o JSON proprietário da
H1-4, que nenhum concorrente lê.

**Relacionado:** [H1-4](H1-4-backup-json.md) é a fonte do mapper e do padrão de
pré-visualização/confirmação; [H1-7](H1-7-restore-backup-fixes.md) é o padrão de
file picker (`ActivityResultContracts.CreateDocument`/`OpenDocument`). Esta spec
**não** altera o JSON nem o fluxo de backup.

## Objetivo

O usuário leva os treinos e as séries registradas para fora do app em um CSV que
abre no Excel/Sheets, e traz para dentro do app o CSV exportado pelo Strong ou
pelo Hevy, vendo uma pré-visualização com contagens antes de confirmar — sem
conta, sem nuvem e sem backend.

## Fora de escopo

- Exportar planos (dias/exercícios planejados) em CSV: o formato de
  concorrente é de sessões registradas, não de plano. Plano continua saindo só
  pelo backup JSON (H1-4).
- Importar CSV que **não** seja do Strong ou do Hevy (formatos desconhecidos
  são rejeitados com mensagem, não "chutados").
- Importar plano/programa do concorrente; só sessões, exercícios e séries.
- Merge inteligente, sincronização contínua ou reimportação automática.
- Exportar/importar medidas corporais (H4-8) ou dados do Health Connect (H4-9).
- Exportar para Google Sheets / nuvem (segue "Won't now" no roadmap).
- Alterar o JSON de backup, o `:protocol` ou o backend.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/csv/CsvCodec.kt` | **Novo.** Parser e writer CSV puros (`parseCsv`, `writeCsvRow`, `escapeCsvField`): delimitador detectado, aspas, aspas escapadas, CRLF/LF, BOM. |
| `app/src/main/java/com/treinos/app/data/csv/CsvDtos.kt` | **Novo.** `CsvImportFormat`, `CsvImportRow`, `CsvImportPreview`, `CsvImportPlan`, `CsvSessionDraft`, `CsvSetDraft`, `CsvImportState`. |
| `app/src/main/java/com/treinos/app/data/csv/WorkoutCsvExporter.kt` | **Novo.** `exportSessionsCsv(sessions: List<SessionWithExercises>): String` — uma linha por série, cabeçalho fixo. |
| `app/src/main/java/com/treinos/app/data/csv/StrongHevyCsvParser.kt` | **Novo.** `detectFormat(header: List<String>): CsvImportFormat?` e `parse(rows: List<List<String>>): CsvImportPlan` — mapas de coluna por formato, datas, números, agrupamento por sessão. |
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | `WorkoutSessionEntity` ganha `importRef: String? = null` (chave de deduplicação de importação; `null` para sessões criadas no app). |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `MIGRATION_7_8` (aditiva): `ALTER TABLE workout_sessions ADD COLUMN importRef TEXT` + índice único `index_workout_sessions_importRef`. Registrar em `addMigrations(...)`; `version = 8`. A H4-4 (antes na fila) levou o banco a 7 com `MIGRATION_6_7`; o número acompanha o `version` real do `AppDatabase.kt` na ordem de merge. |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `@Query("SELECT * FROM workout_sessions WHERE importRef = :ref LIMIT 1") suspend fun getSessionByImportRef(ref: String): WorkoutSessionEntity?`; `@Insert` de sessão já existe. |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `exportSessionsCsv(): String`; `importSessionsCsv(plan: CsvImportPlan, deviceId: String): CsvImportResult` (transacional, aditivo, idempotente por `importRef`). |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `csvImportState: StateFlow<CsvImportState>`, `csvMessage: StateFlow<String?>`, `prepareCsvImport(text: String)`, `confirmCsvImport()`, `cancelCsvImport()`, `clearCsvMessage()`, `exportSessionsCsv(): String`. |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Dois launchers novos (`CreateDocument("text/csv")` e `OpenDocument` com `arrayOf("text/*", "text/comma-separated-values", "application/csv")`), no mesmo padrão de `exportLauncher`/`importLauncher`; passar estados/callbacks à `SettingsScreen`. |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Botões "Exportar CSV" e "Importar CSV (Strong/Hevy)" + card de pré-visualização/confirmação na seção Backup. |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | Títulos, descrição, pré-visualização, avisos de formato/linhas ignoradas e mensagens de sucesso/erro (paridade obrigatória). |
| `app/src/test/java/com/treinos/app/data/csv/CsvCodecTest.kt` | **Novo.** Parser/writer puros. |
| `app/src/test/java/com/treinos/app/data/csv/WorkoutCsvExporterTest.kt` | **Novo.** Export determinístico. |
| `app/src/test/java/com/treinos/app/data/csv/StrongHevyCsvParserTest.kt` | **Novo.** Detecção de formato, datas, números, agrupamento e erros. |
| `app/src/test/java/com/treinos/app/data/csv/CsvImportTest.kt` (Robolectric, Room em memória) | **Novo.** Import aditivo, idempotente e atômico. |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Caso da `MIGRATION_7_8` (coluna + índice). |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### CSV de export (formato "Treinos", versionado pelo cabeçalho)

UTF-8 **com BOM** (`\uFEFF`), delimitador `;`, decimal com vírgula, quebra
`\r\n`, uma linha por série, cabeçalho exato:

```text
Data;Hora;Treino;Exercício;Série;Reps;Carga (kg);RPE;Aquecimento;Duração (s);Distância (m);Notas
2026-11-10;07:32;Treino A;Supino reto;1;10;40;8;Não;;;Primeira série
2026-11-10;07:33;Treino A;Supino reto;2;8;42,5;8,5;Não;;;
```

- `Data` = `LocalDate` do `startedAt` no fuso do aparelho (`sessionDay`), ISO
  `yyyy-MM-dd`; `Hora` = `HH:mm` local.
- `Treino` = `session.title` se não vazio, senão `session.dayName`, senão
  `planName`, senão `"Treino"`.
- `Série` = `setLog.setNumber`; `Reps`/`Carga (kg)`/`RPE` vazios quando o campo
  é `null`; `Aquecimento` = `Sim`/`Não`.
- `Duração (s)`/`Distância (m)` só para séries de corrida/natação; `Notas` =
  `session.notes` (repetida em todas as linhas da sessão).
- Sessões exportadas: **todas** as que têm pelo menos uma série registrada
  (`isCompleted = true`), inclusive `in_progress` (o usuário pode querer o
  parcial), ordenadas por `startedAt` asc e, dentro da sessão, por
  `exerciseOrder` e `setNumber`.
- Sessão sem nenhuma série → não gera linha (não há o que exportar).
- Campos com `;`, `"`, `\n` ou `\r` são citados com `"` e `"` interno vira `""`.

### Formatos de import aceitos

`CsvImportFormat` é detectado **só pelo cabeçalho** (case-insensitive, sem
acento, espaços normalizados):

| Formato | Colunas obrigatórias | Colunas opcionais |
|---|---|---|
| `STRONG` | `Date`, `Workout Name`, `Exercise Name`, `Set Order`, `Weight`, `Reps` | `Duration`, `Distance`, `Seconds`, `Notes`, `RPE`, `Workout Duration` |
| `HEVY` | `title`, `start_time`, `exercise_title`, `set_index`, `weight_kg`, `reps` | `end_time`, `description`, `duration_seconds`, `distance_km`, `rpe` |

- Delimitador detectado na primeira linha: `,` ou `;` (o que tiver mais
  ocorrências fora de aspas). BOM é removido antes.
- Data do Strong: `yyyy-MM-dd` ou `dd/MM/yyyy`; hora opcional `HH:mm` ou
  `HH:mm:ss` na mesma coluna ou em coluna separada. Sem hora → `00:00` local.
- `start_time` do Hevy: ISO-8601 com offset (`2026-11-10T07:32:00+00:00` ou
  `Z`); convertido para `Instant`.
- Números aceitam `.` ou `,` como decimal e são normalizados para `Double`.
- Linhas com `Exercise Name`/`exercise_title` vazio ou `Set Order`/`set_index`
  não numérico são **ignoradas** e contadas em `skippedRows` (não abortam).
- Linhas com a mesma chave de sessão (data + nome do treino) são agrupadas numa
  única `CsvSessionDraft`, com exercícios na ordem de primeira aparição e séries
  ordenadas por `setNumber`.

### Contrato interno

```kotlin
enum class CsvImportFormat { STRONG, HEVY }

data class CsvImportRow(val lineNumber: Int, val raw: List<String>)

data class CsvImportPlan(
    val format: CsvImportFormat,
    val sessions: List<CsvSessionDraft>,
    val skippedRows: Int,
    val warnings: List<String>,
)

data class CsvSessionDraft(
    val importRef: String,          // "strong:<data>:<nome normalizado>" | "hevy:<data>:<nome>"
    val startedAt: Long,            // epoch millis no fuso do aparelho
    val title: String,
    val notes: String,
    val durationSeconds: Long?,     // da coluna de duração da sessão, quando houver
    val exercises: List<CsvExerciseDraft>,
)

data class CsvExerciseDraft(
    val exerciseName: String,
    val exerciseOrder: Int,
    val sets: List<CsvSetDraft>,
)

data class CsvSetDraft(
    val setNumber: Int,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val isWarmup: Boolean,
)

data class CsvImportPreview(
    val format: CsvImportFormat,
    val sessions: Int,
    val exercises: Int,
    val sets: Int,
    val duplicates: Int,   // já existem no banco pelo importRef
    val skippedRows: Int,
    val warnings: List<String>,
)

sealed interface CsvImportState {
    data object Idle : CsvImportState
    data class Ready(val preview: CsvImportPreview, val plan: CsvImportPlan) : CsvImportState
    data object Importing : CsvImportState
    data class Error(val message: String) : CsvImportState
}
```

### Room

- `WorkoutSessionEntity.importRef: String? = null`; índice **único**
  `index_workout_sessions_importRef`. SQLite trata `NULL`s como distintos, então
  as sessões criadas no app (todas com `null`) não colidem.
- `MIGRATION_7_8` só faz `ALTER TABLE ... ADD COLUMN` e `CREATE UNIQUE INDEX`;
  nenhuma tabela é recriada e nenhum dado existente é tocado.
- Import é **aditivo**: nunca chama `deleteAllData()`/`replaceAllData()`. O
  `importRef` é a chave de idempotência — reimportar o mesmo arquivo não
  duplica sessões.

## Regras e casos de borda

1. **Limite de leitura:** reusar `BackupSizeGuard.readLimited` com o mesmo teto
   de 25 MB (`BackupValidator.MAX_FILE_BYTES`); arquivo maior é rejeitado antes
   de decodificar.
2. **Formato desconhecido:** cabeçalho sem as colunas obrigatórias de `STRONG`
   nem de `HEVY` → `CsvImportState.Error("Formato não reconhecido. Exporte do
   Strong ou do Hevy.")`; nada é gravado.
3. **CSV vazio ou só cabeçalho:** `sessions = 0`; a UI mostra "Nenhum treino
   encontrado no arquivo" e o botão de confirmar fica desabilitado.
4. **Linha inválida** (sem nome de exercício, `set_index` não numérico, data
   ilegível): ignorada e somada em `skippedRows`; a pré-visualização mostra
   "N linhas ignoradas". Se **todas** as linhas forem inválidas, é erro (não
   pré-visualização vazia).
5. **Valores fora da faixa:** `reps < 0` → `null`; `weightKg < 0` → `null`;
   `rpe` fora de `1.0..10.0` → `null`; `distanceMeters < 0` → `null`. A linha
   continua válida (o dado ruim é descartado, não a série).
6. **Carga zero é válida** (peso corporal): `weightKg = 0.0` é preservado e
   exportado como `0`.
7. **Duração/distância:** `duration_seconds`/`Seconds` e `distance_km`/
   `Distance` só entram quando a coluna existe; `distance_km` é convertido para
   metros (`× 1000`).
8. **Fuso horário:** o Hevy traz `Instant`; o Strong traz data/hora local. No
   import do Strong, a hora local é interpretada no fuso atual do aparelho
   (`ZoneId.systemDefault()`); a sessão é gravada com esse epoch millis.
9. **Deduplicação:** `importRef` é `"<formato>:<yyyy-MM-dd>:<título
   normalizado>"` (minúsculas, espaços colapsados). Sessão com o mesmo
   `importRef` já no banco é contada em `duplicates` e **não** é reinserida.
10. **Atomicidade:** a gravação de todas as sessões do plano acontece em uma
    única transação (`@Transaction` no DAO). Falha no meio → rollback completo;
    nenhuma sessão parcial e nenhum `importRef` órfão.
11. **Aditivo:** planos, sessões e séries existentes **não** são apagados nem
    alterados; o import só insere.
12. **Exercício novo:** o nome do exercício vem do CSV; o `exerciseId` é
    derivado de forma determinística (`"csv_" + slug(nome)`), porque o catálogo
    do app não conhece o ID do concorrente. Séries de um mesmo nome na mesma
    sessão caem no mesmo `SessionExerciseEntity`.
13. **Export sem dados:** gera CSV válido só com o cabeçalho e a UI informa
    "Nenhum treino para exportar".
14. **Cancelar** o seletor de arquivo ou o diálogo de confirmação não altera
    nada.
15. **Offline:** export e import funcionam sem rede, sem backend e sem conta.
16. **Determinismo do export:** mesma entrada → mesmo texto (ordenação estável,
    sem `Locale` implícito: decimal sempre com vírgula, separador de milhar
    nunca).

## Critérios de aceite

1. **Dado** um banco com 1 sessão concluída de "Treino A" com 2 séries (10×40 e
   8×42,5), **quando** o usuário exporta CSV, **então** o arquivo tem o
   cabeçalho exato, BOM, `;` como delimitador e 2 linhas com
   `2026-11-10;...;40;...` e `...;42,5;...`.
2. **Dado** o CSV exportado no critério 1, **quando** o usuário o importa de
   volta em um banco vazio, **então** a pré-visualização mostra 1 sessão, 1
   exercício e 2 séries, e o banco fica com os mesmos valores.
3. **Dado** um CSV do Strong com 3 sessões, **quando** o usuário seleciona o
    arquivo, **então** a pré-visualização mostra o formato `STRONG` e as
    contagens corretas antes de qualquer gravação.
4. **Dado** um CSV do Hevy com `start_time` em ISO-8601 com offset, **quando**
    importa, **então** o `startedAt` gravado corresponde ao mesmo instante.
5. **Dado** o mesmo arquivo importado duas vezes, **quando** confirma a segunda
    vez, **então** a pré-visualização mostra 0 sessões novas e todas em
    `duplicates`, e o banco continua com a mesma contagem.
6. **Dado** um CSV com uma linha sem nome de exercício, **quando** importa,
    **então** a linha é ignorada, `skippedRows = 1` aparece na pré-visualização
    e as demais entram.
7. **Dado** um CSV com cabeçalho arbitrário ("foo,bar"), **quando** importa,
    **então** aparece o erro de formato não reconhecido e o banco não muda.
8. **Dado** que o usuário cancela o seletor de arquivo, **então** nada muda.
9. **Dado** um banco com dados existentes, **quando** importa um CSV de outra
    pessoa, **então** as sessões antigas continuam intactas (import aditivo).
10. **Dado** o app em modo avião, **quando** exporta e importa, **então** o
    fluxo funciona igual.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/csv/CsvCodecTest.kt` | `detecta_ponto_e_virgula`, `detecta_virgula`, `campo_com_aspas_e_escapado`, `campo_com_quebra_de_linha`, `campo_com_delimitador_interno`, `remove_bom`, `aceita_crlf_e_lf`, `linha_com_numero_de_colunas_errado_e_ignorada`, `writer_cita_quando_necessario` |
| `app/src/test/java/com/treinos/app/data/csv/WorkoutCsvExporterTest.kt` | `cabecalho_exato`, `uma_linha_por_serie`, `decimal_com_virgula`, `campos_nulos_ficam_vazios`, `aquecimento_sim_nao`, `ordena_por_data_e_serie`, `sessao_sem_serie_nao_gera_linha`, `export_vazio_so_cabecalho`, `determinismo_mesma_entrada_mesmo_texto` |
| `app/src/test/java/com/treinos/app/data/csv/StrongHevyCsvParserTest.kt` | `detecta_strong`, `detecta_hevy`, `rejeita_cabecalho_desconhecido`, `agrupa_linhas_na_mesma_sessao`, `ordena_series_por_set_number`, `data_strong_iso_e_brasileira`, `start_time_hevy_com_offset`, `numeros_com_virgula_e_ponto`, `linha_sem_exercicio_e_ignorada`, `rpe_fora_da_faixa_vira_nulo`, `carga_zero_preservada`, `distance_km_vira_metros`, `csv_vazio_gera_plano_vazio`, `todas_invalidas_e_erro` |
| `app/src/test/java/com/treinos/app/data/csv/CsvImportTest.kt` (Robolectric) | `import_insere_sessao_exercicio_e_series`, `import_e_aditivo_e_nao_apaga_existentes`, `reimportar_nao_duplica`, `import_ref_derivado_do_formato_e_data`, `falha_no_meio_faz_rollback` |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_6_7_adiciona_import_ref_e_indice` |

Os testes de `CsvCodec`, `WorkoutCsvExporter` e `StrongHevyCsvParser` são JVM
puros (o gate roda `testDebugUnitTest`); `CsvImportTest` usa Room em memória
(Robolectric), como `BackupImportTest`.

## Verificação manual

1. Registre um treino com 2 exercícios e 3 séries no total.
2. Ajustes → Backup → "Exportar CSV" → salve `treinos-*.csv`.
3. Abra o arquivo no Excel/Sheets: confira o cabeçalho, a vírgula decimal e as
   3 linhas.
4. Exporte um treino do Strong (ou use um arquivo de exemplo) e um do Hevy.
5. Ajustes → "Importar CSV (Strong/Hevy)" → selecione o arquivo do Strong →
   confira a pré-visualização (formato, sessões, exercícios, séries, ignoradas)
   → confirme.
6. Abra o Histórico e confira as sessões importadas com séries e cargas.
7. Repita o mesmo import e confirme que a pré-visualização mostra tudo como
   duplicado e nada é criado.
8. Repita com o arquivo do Hevy e confira o horário das sessões.
9. Selecione um `.txt` renomeado para `.csv` e confirme o erro de formato, com
   os dados intactos.
10. Cancele o seletor e confirme que nada mudou.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Dependência nova:** nenhuma. O parser/writer é Kotlin puro (sem Apache
  Commons CSV/OpenCSV); o file picker já existe pela H1-7.
- **Formato de concorrente muda sem aviso:** a detecção é por cabeçalho e o
  parser tolera colunas extras; um cabeçalho que não casa é rejeitado com
  mensagem, nunca interpretado "no chute". Se o Strong/Hevy mudar, o ajuste é
  local ao `StrongHevyCsvParser` e coberto pelos testes de detecção.
- **Import duplicando treinos:** mitigado pelo `importRef` único + índice
  `UNIQUE` + contagem de `duplicates` na pré-visualização. O índice é a trava
  final: mesmo um bug de contagem não grava duas linhas com o mesmo `importRef`.
- **Migration 7→8:** aditiva (`ADD COLUMN` + `CREATE UNIQUE INDEX`), sem
  recriar tabela; o teste de migração e o schema exportado (`app/schemas/8.json`)
  cobrem o caminho. Se o índice único falhar por dados antigos, o índice é só
  sobre `importRef` (todas `NULL` no banco existente), então não há colisão.
- **Excel abrindo errado:** BOM + `;` + vírgula decimal é o formato que o Excel
  pt-BR espera; o import aceita `,` e `.` para não depender do Excel.
- **Rollback:** remover `data/csv/`, os launchers e o card da `SettingsScreen`.
  A coluna `importRef` pode ficar (aditiva, ignorada) ou ser removida numa
  migration futura; nenhum treino é apagado ao reverter a feature.

