# H4-8 — Peso corporal e medidas ao longo do tempo

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-8` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |
| Depende de | H4-2 (gráfico `ProgressLineChart`), H1-4 (backup JSON), H3-10 (rules de backup do Android) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 2). Hoje o
peso é um valor estático em `AppPreferences.bodyWeightKg`, lido só para montar o
prompt da IA (`back/app/main.py`, linhas 758–762 e 799–803); não existe entidade
de medida nem tendência.

**Relacionado:** [H4-2](H4-2-graficos-progresso.md) entrega
`ProgressLineChart`/`ProgressPoint` — esta spec **reusa** o componente e o tipo
em vez de criar outro gráfico. [H1-4](H1-4-backup-json.md) é o contrato de
backup que ganha a lista aditiva de medidas.

## Objetivo

O usuário registra peso e medidas ao longo do tempo, vê a tendência em um
gráfico e guarda fotos de progresso que nunca saem do aparelho — tudo offline,
sem conta e sem nuvem.

## Fora de escopo

- Sincronizar peso/medidas com o backend ou com a IA: o `bodyWeightKg` do
  perfil continua sendo o valor usado no prompt, e esta feature **não** o
  altera automaticamente (o usuário pode copiar o último registro para o perfil
  se quiser; isso é um botão explícito, não um efeito colateral).
- Ler peso/passos do Health Connect (H4-9).
- Fotos na nuvem, galeria remota, backup das fotos no JSON ou upload de
  qualquer tipo.
- Composição corporal por bioimpedância, dobras cutâneas, IMC automático ou
  interpretação clínica.
- Exportar medidas em CSV (o CSV da H4-7 é de treinos/séries).
- Metas, alertas, lembretes ou notificações de pesagem.
- Alterar `ProfileValidator`, `GenerateScreen` ou o contrato do backend.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | **Novo** `@Entity(tableName = "body_measurements")` `BodyMeasurementEntity`. |
| `app/src/main/java/com/treinos/app/data/local/AppDatabase.kt` | `BodyMeasurementEntity::class` em `entities`; `MIGRATION_8_9` (`CREATE TABLE body_measurements` + índice em `measuredAt`); `version = 9`. A H4-4 e a H4-7 (antes na fila) levaram o banco a 8; o número acompanha o `version` real do `AppDatabase.kt` no momento da implementação. |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `observeMeasurements(): Flow<List<BodyMeasurementEntity>>`, `getAllMeasurements()`, `insertMeasurement(...)`, `deleteMeasurement(id)`, `deleteAllMeasurements()`; `replaceAllData(...)` ganha `measurements: List<BodyMeasurementEntity>` e passa a limpá-las/reinseri-las. |
| `app/src/main/java/com/treinos/app/data/MeasurementValidator.kt` | **Novo.** Validação pura (`MeasurementValidator.validate`) + `MeasurementValidationResult`. |
| `app/src/main/java/com/treinos/app/ui/measurements/MeasurementTrend.kt` | **Novo.** Funções puras: `weightTrendPoints(...): List<ProgressPoint>` (reusa o tipo da H4-2), `latestMeasurement(...)`, `trendDeltaKg(...)`. |
| `app/src/main/java/com/treinos/app/data/MeasurementPhotoStore.kt` | **Novo.** `photosDir(context)`, `newPhotoFile(context)`, `deletePhoto(context, path)`, `exists(path)`; grava em `filesDir/progress-photos/`. |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `observeMeasurements()`, `addMeasurement(...)`, `deleteMeasurement(id)`, `exportBackup(...)` inclui `measurements`, `importBackup(...)` aplica a lista quando presente. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `measurements: StateFlow<List<BodyMeasurementEntity>>`, `measurementMessage`, `saveMeasurement(draft)`, `deleteMeasurement(id)`, `attachMeasurementPhoto(uri)`, `copyLatestWeightToProfile()`. |
| `app/src/main/java/com/treinos/app/ui/Screen.kt` (ou o `enum class Screen` em `AppViewModel.kt`) | Novo destino `Measurements`. |
| `app/src/main/java/com/treinos/app/ui/screens/MeasurementsScreen.kt` | **Novo.** Gráfico de tendência (`ProgressLineChart`), lista de medidas, formulário de entrada, foto e exclusão. |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Navegação para `Measurements`, coleta de estado e launchers de câmera/galeria. |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Atalho "Peso e medidas" (e último peso, se houver). |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Card "Peso e medidas" abrindo a tela. |
| `app/src/main/java/com/treinos/app/data/backup/BackupDtos.kt` | `BackupFile.measurements: List<BackupMeasurement>? = null` e **novo** `BackupMeasurement`. |
| `app/src/main/java/com/treinos/app/data/backup/BackupMapper.kt` | `toDto(BodyMeasurementEntity)` e `toEntity(BackupMeasurement)`. |
| `app/src/main/java/com/treinos/app/data/backup/BackupValidator.kt` | `SUPPORTED_VERSIONS` passa a incluir `4`; validação das medidas (faixas, `measuredAt > 0`, IDs duplicados). |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `exportBackup` grava `schemaVersion = 4` e preenche `measurements`. |
| `app/src/main/res/xml/backup_rules.xml` e `app/src/main/res/xml/data_extraction_rules.xml` | `<exclude domain="file" path="progress-photos/" />` em **todas** as seções (cloud-backup e device-transfer) e no `full-backup-content`. |
| `app/src/main/res/xml/file_paths.xml` | Novo `<files-path name="progress_photos" path="progress-photos/" />` para a câmera escrever pelo `FileProvider`. |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | Títulos, rótulos de campo, estados vazios, aviso de privacidade das fotos e mensagens (paridade obrigatória). |
| `app/src/test/java/com/treinos/app/data/MeasurementValidatorTest.kt` | **Novo.** Faixas e campos. |
| `app/src/test/java/com/treinos/app/ui/measurements/MeasurementTrendTest.kt` | **Novo.** Tendência e delta. |
| `app/src/test/java/com/treinos/app/data/backup/BackupMeasurementTest.kt` | **Novo.** Serialização, mapper e compatibilidade v1–v3. |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | Caso da `MIGRATION_8_9` (tabela + índice). |
| `app/src/test/java/com/treinos/app/data/local/BackupRulesTest.kt` | Caso de `progress-photos/` excluído da nuvem e do device-transfer. |
| `app/src/test/java/com/treinos/app/data/MeasurementPhotoStoreTest.kt` (Robolectric) | **Novo.** Diretório, criação e exclusão de arquivo. |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Room — `body_measurements` (tabela nova, aditiva)

```kotlin
@Entity(tableName = "body_measurements", indices = [Index("measuredAt")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis da medição; informado pelo usuário (permite registrar ontem). */
    val measuredAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipCm: Double? = null,
    /** Caminho absoluto do arquivo em filesDir/progress-photos/; null = sem foto. */
    val photoPath: String? = null,
    val notes: String = "",
    /** "manual" (esta spec) | "health_connect" (H4-9). */
    val source: String = "manual",
)
```

`MIGRATION_8_9` (aditiva, sem recriar tabela; acompanha o `version` real do
`AppDatabase.kt` — a H4-4 e a H4-7 vêm antes na fila):

```sql
CREATE TABLE IF NOT EXISTS body_measurements (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    measuredAt INTEGER NOT NULL,
    weightKg REAL, bodyFatPercent REAL,
    waistCm REAL, chestCm REAL, armCm REAL, thighCm REAL, hipCm REAL,
    photoPath TEXT, notes TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT 'manual'
);
CREATE INDEX IF NOT EXISTS index_body_measurements_measuredAt ON body_measurements (measuredAt);
```

### Backup JSON — `schemaVersion = 4` (compatível com 1, 2 e 3)

```json
{
  "schemaVersion": 4,
  "measurements": [
    {
      "id": 1,
      "measuredAt": 1762800000000,
      "weightKg": 82.4,
      "bodyFatPercent": 18.5,
      "waistCm": 84.0,
      "chestCm": null,
      "armCm": null,
      "thighCm": null,
      "hipCm": null,
      "photoPath": "progress-photos/2026-11-10-0732.jpg",
      "notes": "",
      "source": "manual"
    }
  ]
}
```

Regras de compatibilidade:

- `measurements` é **opcional** (`null` quando o arquivo é v1/v2/v3). Backup
  antigo importado num app novo → as medidas locais **continuam** (mesma regra
  do `profile` na H1-4: ausente = "o arquivo não fala disso").
- `measurements: []` explícito → limpa as medidas locais (o arquivo fala que
  não há nenhuma).
- `photoPath` é gravado como caminho **relativo** a `filesDir` (ex.:
  `progress-photos/2026-11-10-0732.jpg`). No import, o caminho é remontado
  contra o `filesDir` do aparelho; se o arquivo não existir, a medida continua
  válida e a UI mostra "Foto não encontrada neste aparelho" — o backup **não**
  carrega a imagem.
- `SUPPORTED_VERSIONS = setOf(1, 2, 3, 4)`.

### Validação pura

```kotlin
data class MeasurementValidationResult(val valid: Boolean, val message: String?)

object MeasurementValidator {
    const val MIN_WEIGHT_KG = 30.0
    const val MAX_WEIGHT_KG = 300.0
    const val MIN_BODY_FAT = 0.0
    const val MAX_BODY_FAT = 100.0
    const val MIN_CIRCUMFERENCE_CM = 0.0
    const val MAX_CIRCUMFERENCE_CM = 300.0

    fun validate(
        measuredAt: Long,
        weightKg: Double?,
        bodyFatPercent: Double?,
        waistCm: Double?,
        chestCm: Double?,
        armCm: Double?,
        thighCm: Double?,
        hipCm: Double?,
        nowMillis: Long,
    ): MeasurementValidationResult
}
```

### Tendência (reusa a H4-2)

```kotlin
// ui/measurements/MeasurementTrend.kt
fun weightTrendPoints(
    measurements: List<BodyMeasurementEntity>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): List<ProgressPoint>          // ProgressPoint(sessionId = id, startedAtMillis, label = "dd/MM", value = kg)

fun latestMeasurement(measurements: List<BodyMeasurementEntity>): BodyMeasurementEntity?
fun trendDeltaKg(measurements: List<BodyMeasurementEntity>): Double?   // último − primeiro do período
```

`ProgressLineChart` é o componente da H4-2
(`app/src/main/java/com/treinos/app/ui/components/ProgressChart.kt`); esta spec
não cria outro gráfico.

## Regras e casos de borda

1. **Faixas:** `weightKg` entre 30 e 300; `bodyFatPercent` entre 0 e 100;
   circunferências entre 0 e 300; `NaN`/`Infinity` sempre inválidos. Fora da
   faixa → erro inline no campo, nada é gravado.
2. **Pelo menos um valor:** uma medida com todos os campos numéricos `null` é
   inválida ("Informe ao menos um valor") — não se cria registro vazio.
3. **Data:** `measuredAt > 0` e `measuredAt <= now + 5 min` (tolerância de
   relógio). Futuro além disso é rejeitado ("Data no futuro").
4. **Ordenação:** a lista é sempre exibida por `measuredAt` **desc**; o gráfico
   por `measuredAt` **asc**.
5. **Empate de data:** duas medidas no mesmo dia são permitidas (o usuário pode
   pesar de manhã e à noite); não há chave única por dia.
6. **Gráfico:** `points.size < 2` → "Poucos dados para um gráfico" e nenhuma
   linha (mesma regra da H4-2, regra 6).
7. **Exclusão:** apagar uma medida com `photoPath` também apaga o arquivo da
   foto (`MeasurementPhotoStore.deletePhoto`); arquivo já ausente não é erro.
8. **Foto:** gravada em `filesDir/progress-photos/` com nome
   `<yyyy-MM-dd-HHmm>-<random>.jpg`. A foto **nunca** é enviada para nuvem,
   backend ou backup JSON.
9. **Backup automático do Android:** `progress-photos/` é excluído de
   `cloud-backup`, `device-transfer` e `full-backup-content` — a garantia de
   privacidade é verificada por teste, não só por comentário.
10. **Import de backup v4 com `measurements`:** substitui todas as medidas
    locais dentro da mesma transação de `replaceAllData`; falha → rollback
    completo (nem dados nem medidas mudam).
11. **Import de backup v1/v2/v3:** `measurements == null` → medidas locais
    intactas.
12. **Import com `photoPath` de outro aparelho:** o caminho é remontado; se o
    arquivo não existir, a medida entra sem foto e a UI avisa. Nenhum erro
    fatal.
13. **Perfil:** `copyLatestWeightToProfile()` é ação explícita do usuário e só
    então grava `ProfileStore.bodyWeightKg` com o último peso; registrar uma
    medida **não** altera o perfil sozinho.
14. **Lista vazia:** tela mostra estado vazio com CTA "Registrar primeira
    medida"; nenhum crash.
15. **Offline:** tudo funciona sem rede, sem backend e sem conta.

## Critérios de aceite

1. **Dado** o app sem nenhuma medida, **quando** o usuário abre "Peso e
   medidas", **então** vê o estado vazio e o gráfico não é desenhado.
2. **Dado** que o usuário registra 82,4 kg hoje, **então** a lista mostra o
   registro e o perfil (`bodyWeightKg`) **não** muda sozinho.
3. **Dado** três medidas em datas diferentes (82,4 / 82,0 / 81,5 kg), **quando**
   a tela abre, **então** a linha mostra 3 pontos em ordem cronológica e o delta
   exibido é −0,9 kg.
4. **Dado** o usuário informa peso de 500 kg, **então** aparece o erro de faixa,
   nada é gravado e o botão de salvar continua desabilitado.
5. **Dado** o usuário tenta salvar sem nenhum valor, **então** aparece "Informe
   ao menos um valor".
6. **Dado** o usuário anexa uma foto, **então** o arquivo existe em
   `filesDir/progress-photos/` e a lista mostra a miniatura.
7. **Dado** que o usuário apaga a medida com foto, **então** o registro some e o
   arquivo da foto deixa de existir.
8. **Dado** um backup v4 com 2 medidas, **quando** o usuário restaura em um app
   limpo, **então** as 2 medidas voltam com os mesmos valores e `measuredAt`.
9. **Dado** um backup v3 (sem o campo `measurements`), **quando** o usuário
   restaura sobre um banco com 3 medidas, **então** as 3 medidas locais
   continuam.
10. **Dado** uma medida com foto, **quando** o usuário faz backup do aparelho
    (Google One) ou transfere para outro aparelho, **então** a pasta
    `progress-photos/` não é enviada.
11. **Dado** o app em modo avião, **quando** o usuário registra e apaga medidas,
    **então** tudo funciona igual.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/MeasurementValidatorTest.kt` | `aceita_peso_minimo_e_maximo`, `rejeita_peso_fora_da_faixa`, `rejeita_nan_e_infinito`, `aceita_gordura_0_e_100`, `rejeita_gordura_acima_de_100`, `aceita_circunferencia_0_e_300`, `rejeita_circunferencia_acima_de_300`, `rejeita_medida_sem_nenhum_valor`, `rejeita_data_zero_ou_negativa`, `rejeita_data_no_futuro`, `aceita_data_de_hoje` |
| `app/src/test/java/com/treinos/app/ui/measurements/MeasurementTrendTest.kt` | `pontos_em_ordem_cronologica`, `rotulo_data_curta`, `ignora_medida_sem_peso`, `periodo_inclusivo`, `menos_de_dois_pontos_nao_gera_tendencia`, `delta_ultimo_menos_primeiro`, `delta_nulo_com_menos_de_dois`, `lista_vazia_sem_excecao`, `latest_pega_a_mais_recente` |
| `app/src/test/java/com/treinos/app/data/backup/BackupMeasurementTest.kt` | `round_trip_preserva_medidas`, `medida_com_campos_nulos_serializa`, `backup_v3_sem_measurements_decodifica_como_nulo`, `v4_com_lista_vazia_limpa_medidas`, `v3_nao_limpa_medidas_locais`, `rejeita_id_duplicado_de_medida`, `rejeita_peso_de_medida_fora_da_faixa`, `mapper_entity_para_dto_e_volta` |
| `app/src/test/java/com/treinos/app/data/local/MigrationTest.kt` | `migracao_7_8_cria_tabela_body_measurements`, `migracao_7_8_cria_indice_measured_at` |
| `app/src/test/java/com/treinos/app/data/local/BackupRulesTest.kt` | `fotos_de_progresso_ficam_fora_da_nuvem`, `fotos_de_progresso_ficam_fora_do_device_transfer` |
| `app/src/test/java/com/treinos/app/data/MeasurementPhotoStoreTest.kt` (Robolectric) | `cria_diretorio_e_arquivo`, `nome_segue_data_e_hora`, `delete_remove_arquivo`, `delete_arquivo_ausente_nao_falha` |

`MeasurementValidatorTest`, `MeasurementTrendTest` e `BackupMeasurementTest` são
JVM puros; os demais usam Robolectric (Room em memória / filesystem de teste),
como `BackupImportTest` e `BackupRulesTest`.

## Verificação manual

1. Abra Home → "Peso e medidas": confira o estado vazio.
2. Registre 82,4 kg hoje; registre 82,0 kg amanhã (ou ajuste a data) e 81,5 kg
   depois. Confira a linha com 3 pontos e o delta.
3. Tente salvar 500 kg e confirme o erro inline sem gravação.
4. Anexe uma foto de progresso e confira a miniatura na lista.
5. Apague a medida com foto e confirme que o arquivo sumiu (via
   `adb shell run-as com.treinos.app ls files/progress-photos`).
6. Ajustes → "Exportar backup" → confira `schemaVersion: 4` e o bloco
   `measurements` no JSON.
7. Limpe os dados do app, importe o backup e confira as medidas restauradas.
8. Edite o JSON para `schemaVersion: 3` e remova `measurements`; importe e
   confirme que as medidas locais continuam.
9. Ative o modo avião e repita os passos 2–5.
10. Confira em Ajustes do Android → Backup que a pasta de fotos não entra no
    backup em nuvem (e leia o aviso de privacidade na tela).

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Dependência nova:** nenhuma. `ProgressLineChart` vem da H4-2 (Compose
  `Canvas`); a foto usa `ActivityResultContracts.TakePicture`/`PickVisualMedia`
  e `FileProvider`, já presentes (`androidx.activity`, `androidx.core`). Coil
  (`io.coil-kt:coil-compose:2.7.0`) já está no `app/build.gradle.kts` e carrega
  o arquivo local.
- **Privacidade da foto:** o risco é a foto escapar para a nuvem. Mitigado por
  `<exclude domain="file" path="progress-photos/" />` nas três seções de rules,
  com teste dedicado em `BackupRulesTest`; a foto também não entra no JSON.
- **Migration 8→9:** `CREATE TABLE`/`CREATE INDEX` aditivos, sem tocar nas
  tabelas existentes; coberta pelo teste de migração e pelo schema exportado
  (`app/schemas/9.json`).
- **Backup v4 quebrando apps antigos:** não quebra — o decoder da H1-4 usa
  `ignoreUnknownKeys = true`, então um app 1.4.x lê um v4 e ignora
  `measurements` (perde as medidas, mas não falha). O caminho inverso (v3 no app
  novo) preserva as medidas locais pela regra 11.
- **Conflito de `schemaVersion` com a H4-12:** a H4-12 adiciona
  `BackupPlanExercise.targetWeightKg` **sem** bump (precedente H3-7/H3-45) e
  mantém o `schemaVersion` em 3; esta spec sobe para 4. O bump é
  responsabilidade de uma única spec do lote: se a H4-12 entrar antes, ela
  continua sem bump e esta sobe para 4; se a ordem inverter, o número final é
  combinado pelo agente principal no merge. O campo aditivo da H4-12 funciona
  igual em v3 e v4.
- **Perda de foto no import:** esperada e documentada; a medida sobrevive sem a
  imagem, com aviso na UI. Nunca falha o import por causa disso.
- **Rollback:** remover `MeasurementsScreen`, `MeasurementTrend`,
  `MeasurementValidator`, `MeasurementPhotoStore` e as entradas de navegação; a
  tabela `body_measurements` pode ficar (aditiva, ignorada) ou sair numa
  migration futura. Nenhum treino é afetado.

