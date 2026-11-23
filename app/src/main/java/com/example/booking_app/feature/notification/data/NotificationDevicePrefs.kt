package com.example.booking_app.feature.notification.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TokenMetadata(val uid: String? = null, val hash: String? = null, val rotationRequired: Boolean = true)
interface TokenMetadataStore {
    fun read(): TokenMetadata
    fun write(value: TokenMetadata)
}

/** Device-only metadata. Never stores a raw token; excluded from backup/device transfer. */
@Singleton
class NotificationDevicePrefs @Inject constructor(@ApplicationContext context: Context) : TokenMetadataStore, com.example.booking_app.core.session.InstallationIdentity {
    private val prefs = context.getSharedPreferences("notification_device_prefs", Context.MODE_PRIVATE)
    override fun read() = TokenMetadata(prefs.getString("lastRegisteredUid", null), prefs.getString("tokenHash", null), prefs.getBoolean("rotationRequired", true))
    override fun write(value: TokenMetadata) {
        check(prefs.edit().putString("lastRegisteredUid", value.uid).putString("tokenHash", value.hash)
            .putBoolean("rotationRequired", value.rotationRequired).commit())
    }
    @Synchronized override fun installationId(): String = prefs.getString("installationId", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("installationId", it).commit())
    }
}
