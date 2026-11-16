package com.treinos.app.ui

/**
 * Regras puras da nota do treino e do exercício (H4-11).
 *
 * A nota da sessão vive em `workout_sessions.notes` e a nota do exercício em
 * `workout_plan_exercises.notes` (campos que já existiam — sem migration). O
 * teto é o mesmo de `ActivityLog.MAX_NOTES_CHARS`: acima dele o botão Salvar
 * do diálogo fica desabilitado e nada é gravado.
 */
const val MAX_WORKOUT_NOTE_CHARS = 2000

/** Texto gravado já normalizado: trim nas pontas; string vazia limpa a nota. */
fun normalizeNote(text: String): String = text.trim()

/** Verdadeiro quando o texto passa do teto — bloqueia o Salvar no diálogo. */
fun noteLengthError(text: String): Boolean = text.length > MAX_WORKOUT_NOTE_CHARS
