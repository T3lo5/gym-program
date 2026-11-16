package com.treinos.app.data.csv

/** Formatos de import aceitos, detectados só pelo cabeçalho (H4-7). */
enum class CsvImportFormat { STRONG, HEVY }

/** Linha bruta do arquivo, com o número da linha no arquivo (1-based) para avisos. */
data class CsvImportRow(val lineNumber: Int, val raw: List<String>)

/** Resultado do parser: sessões agrupadas, linhas ignoradas e avisos. */
data class CsvImportPlan(
    val format: CsvImportFormat,
    val sessions: List<CsvSessionDraft>,
    val skippedRows: Int,
    val warnings: List<String>,
)

/**
 * Sessão a importar. [importRef] é a chave de deduplicação
 * (`"<formato>:<yyyy-MM-dd>:<título normalizado>"`); [startedAt] é epoch millis
 * no fuso do aparelho.
 */
data class CsvSessionDraft(
    val importRef: String,
    val startedAt: Long,
    val title: String,
    val notes: String,
    val durationSeconds: Long?,
    val exercises: List<CsvExerciseDraft>,
)

/** Exercício da sessão importada, na ordem de primeira aparição no arquivo. */
data class CsvExerciseDraft(
    val exerciseName: String,
    val exerciseOrder: Int,
    val sets: List<CsvSetDraft>,
)

/** Série importada; campo ilegível ou fora de faixa vira `null`, não erro. */
data class CsvSetDraft(
    val setNumber: Int,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val isWarmup: Boolean,
)

/** Contagens mostradas na pré-visualização, antes de gravar qualquer coisa. */
data class CsvImportPreview(
    val format: CsvImportFormat,
    val sessions: Int,
    val exercises: Int,
    val sets: Int,
    val duplicates: Int,
    val skippedRows: Int,
    val warnings: List<String>,
)

/** Estados do fluxo de importação de CSV na UI (espelha o backup JSON). */
sealed interface CsvImportState {
    data object Idle : CsvImportState
    data class Ready(val preview: CsvImportPreview, val plan: CsvImportPlan) : CsvImportState
    data object Importing : CsvImportState
    data class Error(val message: String) : CsvImportState
}

/** Resultado do import transacional: o que entrou e o que já existia. */
data class CsvImportResult(
    val sessionsImported: Int,
    val exercisesImported: Int,
    val setsImported: Int,
    val duplicates: Int,
)

/**
 * `exerciseId` determinístico para exercício vindo de CSV (H4-7, regra 12): o
 * catálogo do app não conhece o ID do concorrente, então o nome vira slug.
 */
internal fun csvExerciseSlug(name: String): String =
    CsvCodec.stripAccents(name.trim())
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifEmpty { "exercicio" }

/**
 * Título normalizado do `importRef` (H4-7, regra 9): minúsculas e espaços
 * colapsados — acentos são preservados de propósito.
 */
internal fun csvImportRefTitle(title: String): String =
    title.trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
