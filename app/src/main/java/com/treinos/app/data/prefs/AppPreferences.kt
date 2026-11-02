package com.treinos.app.data.prefs

import android.content.Context
import java.util.UUID

/**
 * Preferências locais do app.
 *
 * O app funciona offline; o backend só é usado quando o usuário pede um treino
 * novo ou toca em "Sincronizar".
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("treinos_prefs", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKEND_URL
        set(value) {
            val normalized = value.trim().trimEnd('/')
            prefs.edit().putString(KEY_BACKEND_URL, normalized).apply()
        }

    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    var lastSyncAt: String?
        get() = prefs.getString(KEY_LAST_SYNC_AT, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_SYNC_AT, value).apply()
        }

    companion object {
        /** Emulador Android acessa o host em 10.0.2.2. */
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000"

        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}
