package com.treinos.app.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {

    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        preferences = AppPreferences(context)
    }

    @Test
    fun url_sem_esquema_recebe_http() {
        preferences.backendUrl = "192.168.1.16:3000"

        assertEquals("http://192.168.1.16:3000", preferences.backendUrl)
    }

    @Test
    fun url_com_esquema_e_barra_final_e_normalizada() {
        preferences.backendUrl = " http://192.168.1.16:3000/ "

        assertEquals("http://192.168.1.16:3000", preferences.backendUrl)
    }

    @Test
    fun url_https_e_preservada() {
        preferences.backendUrl = "https://treinos.example.com"

        assertEquals("https://treinos.example.com", preferences.backendUrl)
    }

    @Test
    fun url_vazia_volta_para_o_padrao() {
        preferences.backendUrl = "   "

        assertEquals(AppPreferences.DEFAULT_BACKEND_URL, preferences.backendUrl)
    }

    @Test
    fun okhttp_aceita_a_url_normalizada() {
        preferences.backendUrl = "192.168.1.16:3000"

        assertNotNull("${preferences.backendUrl}/health".toHttpUrlOrNull())
    }
}
