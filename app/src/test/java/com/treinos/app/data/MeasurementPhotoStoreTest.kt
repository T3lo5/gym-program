package com.treinos.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-8: filesystem das fotos de progresso — pasta única em
 * `filesDir/progress-photos/`, nome `<yyyy-MM-dd-HHmm>-<random>.jpg` (regra 8)
 * e exclusão tolerante a arquivo ausente (regra 7).
 */
@RunWith(RobolectricTestRunner::class)
class MeasurementPhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cria_diretorio_e_arquivo() {
        val file = MeasurementPhotoStore.newPhotoFile(context)

        val dir = MeasurementPhotoStore.photosDir(context)
        assertTrue("photosDir precisa existir", dir.exists())
        assertEquals(dir, file.parentFile)
        assertEquals(MeasurementPhotoStore.PHOTO_DIR_NAME, dir.name)
    }

    @Test
    fun nome_segue_data_e_hora() {
        val file = MeasurementPhotoStore.newPhotoFile(context)

        assertTrue(
            "Nome esperado <yyyy-MM-dd-HHmm>-<random>.jpg; veio ${file.name}",
            file.name.matches(Regex("""\d{4}-\d{2}-\d{2}-\d{4}-[0-9a-f]{8}\.jpg""")),
        )
    }

    @Test
    fun delete_remove_arquivo() {
        val file = MeasurementPhotoStore.newPhotoFile(context)
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(file.exists())

        MeasurementPhotoStore.deletePhoto(context, file.absolutePath)

        assertFalse(file.exists())
    }

    @Test
    fun delete_arquivo_ausente_nao_falha() {
        // newPhotoFile garante nome livre, mas o arquivo nunca foi escrito.
        val file = MeasurementPhotoStore.newPhotoFile(context)
        assertFalse(file.exists())

        // Regra 7: arquivo já ausente não é erro; caminho em branco é ignorado.
        MeasurementPhotoStore.deletePhoto(context, file.absolutePath)
        MeasurementPhotoStore.deletePhoto(context, "")
        assertFalse(file.exists())
    }
}
