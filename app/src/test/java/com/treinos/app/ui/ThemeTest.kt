package com.treinos.app.ui

import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.ui.modalityAccentFor
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeTest {

    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        preferences = AppPreferences(context)
    }

    @Test
    fun modality_accent_mapa_unico() {
        // Uma única fonte mapeia a cor de cada modalidade (regra H6-7 #3).
        val strength = modalityAccentFor("strength")
        val run = modalityAccentFor("run")
        val mobility = modalityAccentFor("mobility")
        val swim = modalityAccentFor("swim")
        val desconhecida = modalityAccentFor("desconhecida")

        assertEquals(Color(0xFF00C853), strength)
        assertEquals(Color(0xFFE65100), run)
        assertEquals(Color(0xFF3D5AFE), mobility)
        assertEquals(Color(0xFF7C4DFF), swim)
        // Desconhecido cai no strength.
        assertEquals(strength, desconhecida)
        // As quatro são distintas entre si.
        assertNotEquals(strength, run)
        assertNotEquals(run, mobility)
        assertNotEquals(mobility, swim)
    }

    @Test
    fun dynamic_color_default_desligado() {
        // H6-7 regra 5: o default é desligado — identidade própria da marca.
        assertFalse(preferences.useDynamicColor)
    }

    @Test
    fun toggle_persiste() {
        // H6-7: o toggle é por instalação, persiste em prefs.
        preferences.useDynamicColor = true
        assertTrue(preferences.useDynamicColor)

        preferences.useDynamicColor = false
        assertFalse(preferences.useDynamicColor)
    }
}
