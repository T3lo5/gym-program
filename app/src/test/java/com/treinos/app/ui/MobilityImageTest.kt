package com.treinos.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.ui.MobilityRoutineTemplates
import com.treinos.app.ui.imageResForMovement
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobilityImageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun image_res_mapeia_movimentos_conhecidos() {
        assertNotNull(imageResForMovement("Gato-Camelo"))
        assertNotNull(imageResForMovement("Borboleta"))
        assertNotNull(imageResForMovement("Relaxamento"))
    }

    @Test
    fun movimento_sem_drawable_retorna_null() {
        assertNull(imageResForMovement("Movimento Inexistente"))
    }

    @Test
    fun todos_drawables_referenciados_existem_no_recursos() {
        val movements = MobilityRoutineTemplates.routines.flatMap { it.movements }
        val referenced = movements.mapNotNull { it.imageRes }
        assertTrue("Nenhum drawable referenciado", referenced.isNotEmpty())
        for (resId in referenced) {
            // getResourceEntryName lança exceção se o recurso não existir
            context.resources.getResourceEntryName(resId)
        }
    }

    @Test
    fun pelo_menos_um_movimento_por_rotina_tem_drawable() {
        for (routine in MobilityRoutineTemplates.routines) {
            val withImage = routine.movements.any { it.imageRes != null }
            assertTrue("Rotina ${routine.id} sem movimento com drawable local", withImage)
        }
    }
}
