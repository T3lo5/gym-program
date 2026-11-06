package com.treinos.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import com.treinos.app.data.DeviceIdStore
import com.treinos.app.data.ProfileStore
import com.treinos.app.data.discovery.BackendUrlStore
import com.treinos.app.data.wear.CommandDedupe

/**
 * Preferências locais do app.
 *
 * O app funciona offline; o backend só é usado quando o usuário pede um treino
 * novo ou toca em "Sincronizar".
 */
class AppPreferences private constructor(
    private val prefs: SharedPreferences,
) : DeviceIdStore, BackendUrlStore, ProfileStore {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BACKEND_URL
        set(value) {
            prefs.edit().putString(KEY_BACKEND_URL, normalizeBackendUrl(value)).apply()
        }

    override var deviceId: String
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

    override var bodyWeightKg: Double?
        get() = if (prefs.contains(KEY_BODY_WEIGHT_KG)) prefs.getFloat(KEY_BODY_WEIGHT_KG, 0f).toDouble() else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_BODY_WEIGHT_KG).apply()
            } else {
                prefs.edit().putFloat(KEY_BODY_WEIGHT_KG, value.toFloat()).apply()
            }
        }

    override var heightCm: Int?
        get() = if (prefs.contains(KEY_HEIGHT_CM)) prefs.getInt(KEY_HEIGHT_CM, 0) else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_HEIGHT_CM).apply()
            } else {
                prefs.edit().putInt(KEY_HEIGHT_CM, value).apply()
            }
        }

    override var sex: String?
        get() = prefs.getString(KEY_SEX, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_SEX).apply()
            } else {
                prefs.edit().putString(KEY_SEX, value).apply()
            }
        }

    fun isRecentCommand(commandId: String): Boolean {
        val ids = prefs.getStringSet(KEY_WEAR_RECENT_COMMANDS, emptySet()) ?: emptySet()
        return CommandDedupe.isRecent(ids, commandId)
    }

    fun addRecentCommand(commandId: String) {
        val ids = (prefs.getStringSet(KEY_WEAR_RECENT_COMMANDS, emptySet()) ?: emptySet()).toMutableSet()
        CommandDedupe.addRecent(ids, commandId)
        prefs.edit().putStringSet(KEY_WEAR_RECENT_COMMANDS, ids).apply()
    }

    companion object {
        /** Nome do arquivo de prefs (vira treinos_prefs.xml no disco; rules de backup o citam). */
        const val PREFS_NAME = "treinos_prefs"

        /** Emulador Android acessa o host em 10.0.2.2. */
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000"

        /**
         * Aceita o endereço com ou sem esquema ("192.168.1.16:3000" é o formato
         * sugerido na tela de Ajustes). Sem esquema o OkHttp recusa a URL antes
         * de tentar a conexão, então assume HTTP — o backend da LAN não tem TLS.
         */
        internal fun normalizeBackendUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            val withScheme = if (hasScheme) trimmed else "http://$trimmed"
            return withScheme.trimEnd('/')
        }

        const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_BODY_WEIGHT_KG = "body_weight_kg"
        private const val KEY_HEIGHT_CM = "height_cm"
        private const val KEY_SEX = "sex"
        private const val KEY_WEAR_RECENT_COMMANDS = "wear_recent_commands"
    }
}
