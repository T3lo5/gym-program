package com.treinos.app.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.remote.isSyncSecretValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncSpaceTest {
    private lateinit var preferences: AppPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteSharedPreferences(AppPreferences.PREFS_NAME)
        preferences = AppPreferences(context)
    }

    @Test
    fun create_space_preserva_credenciais_privadas() {
        preferences.syncSpaceId = "11111111-1111-1111-1111-111111111111"
        preferences.syncSecret = "segredo-seguro-123456"
        preferences.syncJoined = true

        assertEquals("11111111-1111-1111-1111-111111111111", preferences.syncSpaceId)
        assertEquals("segredo-seguro-123456", preferences.syncSecret)
        assertTrue(preferences.syncJoined)
    }

    @Test
    fun limpar_espaco_nao_apaga_backend_local() {
        preferences.backendUrl = "http://192.168.1.16:3000"
        preferences.syncSpaceId = "11111111-1111-1111-1111-111111111111"
        preferences.syncSecret = "segredo-seguro-123456"
        preferences.syncJoined = true

        preferences.syncSpaceId = null
        preferences.syncSecret = null
        preferences.syncJoined = false

        assertNull(preferences.syncSpaceId)
        assertNull(preferences.syncSecret)
        assertFalse(preferences.syncJoined)
        assertEquals("http://192.168.1.16:3000", preferences.backendUrl)
    }

    @Test
    fun segredo_curto_e_rejeitado() {
        assertFalse(isSyncSecretValid("curto"))
        assertTrue(isSyncSecretValid("segredo-seguro-123456"))
    }
}
