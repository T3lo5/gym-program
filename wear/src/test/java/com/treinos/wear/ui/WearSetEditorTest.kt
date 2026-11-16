package com.treinos.wear.ui

import com.treinos.protocol.WearCommandKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H4-15: editor puro de carga/reps do pulso — passos, limites, default e o
 * comando `LOG_SET` gerado com os valores editados.
 */
class WearSetEditorTest {

    private fun editorPadrao(
        lastWeightKg: Double? = null,
        lastReps: Int? = null,
        targetReps: String = "",
    ): WearSetEditorState =
        WearSetEditor.reduce(
            WearSetEditorState(0.0, WearSetEditor.DEFAULT_REPS),
            WearSetEditorAction.Reset(lastWeightKg, lastReps, targetReps),
        )

    @Test
    fun default_usa_ultima_serie() {
        val editor = editorPadrao(lastWeightKg = 20.0, lastReps = 8, targetReps = "8-12")
        assertEquals(20.0, editor.weightKg, 0.0)
        assertEquals(8, editor.reps)
    }

    @Test
    fun default_sem_ultima_usa_alvo() {
        val editor = editorPadrao(lastWeightKg = null, lastReps = null, targetReps = "8-12")
        // Sem última carga: 0,0 = sem carga (peso corporal).
        assertEquals(0.0, editor.weightKg, 0.0)
        assertEquals(8, editor.reps)
    }

    @Test
    fun default_sem_alvo_usa_oito() {
        val editor = editorPadrao(lastWeightKg = null, lastReps = null, targetReps = "")
        assertEquals(0.0, editor.weightKg, 0.0)
        assertEquals(WearSetEditor.DEFAULT_REPS, editor.reps)
    }

    @Test
    fun incremento_de_carga_em_2_5() {
        var editor = editorPadrao(lastWeightKg = 20.0, lastReps = 8, targetReps = "8-12")
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.IncrementWeight)
        assertEquals(22.5, editor.weightKg, 0.0)
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementWeight)
        assertEquals(20.0, editor.weightKg, 0.0)
    }

    @Test
    fun decremento_de_carga_nao_fica_negativo() {
        var editor = editorPadrao(lastWeightKg = 2.5, lastReps = 8, targetReps = "8-12")
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementWeight)
        assertEquals(0.0, editor.weightKg, 0.0)
        // Já em 0,0: continua travado em 0,0.
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementWeight)
        assertEquals(0.0, editor.weightKg, 0.0)
    }

    @Test
    fun carga_zero_vira_sem_carga() {
        val comando = WearSetEditorState(0.0, 8).toLogSetCommand(sessionId = 42, commandId = "cmd-1")
        // Peso corporal: nunca 0,0 gravado no celular.
        assertNull(comando.weightKg)
        assertEquals(8, comando.reps)
    }

    @Test
    fun incremento_de_reps() {
        var editor = WearSetEditorState(20.0, 8)
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.IncrementReps)
        assertEquals(9, editor.reps)
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementReps)
        assertEquals(8, editor.reps)
    }

    @Test
    fun decremento_de_reps_trava_em_um() {
        var editor = WearSetEditorState(20.0, 1)
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.DecrementReps)
        assertEquals(WearSetEditor.MIN_REPS, editor.reps)
    }

    @Test
    fun incremento_de_reps_trava_em_999() {
        var editor = WearSetEditorState(20.0, WearSetEditor.MAX_REPS)
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.IncrementReps)
        assertEquals(WearSetEditor.MAX_REPS, editor.reps)
    }

    @Test
    fun alvo_textual_pega_primeiro_inteiro() {
        assertEquals(8, editorPadrao(targetReps = "8-12").reps)
        assertEquals(10, editorPadrao(targetReps = "10-15").reps)
        // Sem inteiro no alvo: cai no default de 8.
        assertEquals(WearSetEditor.DEFAULT_REPS, editorPadrao(targetReps = "AMRAP").reps)
    }

    @Test
    fun reset_ao_trocar_de_exercicio() {
        // Usuário editou no exercício anterior: 22,5 kg × 9.
        var editor = WearSetEditorState(22.5, 9)
        // Trocou para um exercício sem última série e alvo "10": volta ao default.
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.Reset(null, null, "10"))
        assertEquals(0.0, editor.weightKg, 0.0)
        assertEquals(10, editor.reps)
        // Exercício com última série: default vem dela, não da edição anterior.
        editor = WearSetEditor.reduce(editor, WearSetEditorAction.Reset(30.0, 12, "8-12"))
        assertEquals(30.0, editor.weightKg, 0.0)
        assertEquals(12, editor.reps)
    }

    @Test
    fun comando_log_set_leva_os_valores() {
        val comando = WearSetEditorState(22.5, 9).toLogSetCommand(sessionId = 42, commandId = "cmd-1")
        assertEquals(WearCommandKind.LOG_SET, comando.kind)
        assertEquals(42, comando.sessionId)
        assertEquals("cmd-1", comando.commandId)
        assertEquals(9, comando.reps)
        assertEquals(22.5, comando.weightKg!!, 0.0)
        assertNull(comando.rpe)
        assertEquals(1, comando.protocolVersion)
    }
}
