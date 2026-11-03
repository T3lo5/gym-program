package com.treinos.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Preferências locais do app.
 *
 * O app funciona offline; o backend só é usado quando o usuário pede um treino
 * novo ou toca em "Sincronizar".
 */
class AppPreferences private constructor(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("treinos_prefs", Context.MODE_PRIVATE)
    )

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKEND_URL
        set(value) {
            val normalized = value.trim().trimEnd('/')
            prefs.edit().putString(KEY_BACKEND_URL, normalized).apply()
        }

    var deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }
        set(value) {
            prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        }

    var lastSyncAt: String?
        get() = prefs.getString(KEY_LAST_SYNC_AT, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_SYNC_AT, value).apply()
        }

    var bodyWeightKg: Double?
        get() = if (prefs.contains(KEY_BODY_WEIGHT_KG)) prefs.getFloat(KEY_BODY_WEIGHT_KG, 0f).toDouble() else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_BODY_WEIGHT_KG).apply()
            } else {
                prefs.edit().putFloat(KEY_BODY_WEIGHT_KG, value.toFloat()).apply()
            }
        }

    var heightCm: Int?
        get() = if (prefs.contains(KEY_HEIGHT_CM)) prefs.getInt(KEY_HEIGHT_CM, 0) else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_HEIGHT_CM).apply()
            } else {
                prefs.edit().putInt(KEY_HEIGHT_CM, value).apply()
            }
        }

    companion object {
        /** Emulador Android acessa o host em 10.0.2.2. */
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000"

        const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_BODY_WEIGHT_KG = "body_weight_kg"
        private const val KEY_HEIGHT_CM = "height_cm"
    }
}
