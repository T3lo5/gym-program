package com.treinos.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendAuthTest {
    @Test
    fun auth_header_normaliza_espacos() {
        assertEquals("Bearer abc", authHeader("  abc  "))
        assertNull(authHeader(" "))
    }

    @Test
    fun url_remota_exige_https() {
        assertTrue(validateRemoteUrl("http://treinos.example.com") != null)
        assertNull(validateRemoteUrl("https://treinos.example.com/"))
    }

    @Test
    fun effective_url_prefere_remota_valida() {
        assertEquals(
            "https://treinos.example.com",
            effectiveBackendUrl(true, "https://treinos.example.com/", "http://10.0.2.2:3000"),
        )
        assertEquals(
            "http://10.0.2.2:3000",
            effectiveBackendUrl(false, "https://treinos.example.com", "http://10.0.2.2:3000"),
        )
    }
}
