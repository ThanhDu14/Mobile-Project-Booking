package com.example.booking_app.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSessionPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionPrefs {

    override val welcomeSeen: Flow<Boolean> = dataStore.data.map { it[WELCOME_SEEN] ?: false }

    override suspend fun setWelcomeSeen() {
        dataStore.edit { it[WELCOME_SEEN] = true }
    }

    override suspend fun pendingSignup(): PendingSignup? {
        val prefs = dataStore.data.first()
        val uid = prefs[PENDING_UID] ?: return null
        return PendingSignup(uid, prefs[PENDING_NAME].orEmpty(), prefs[PENDING_TERMS_AT] ?: 0L)
    }

    override suspend fun savePendingSignup(pendingSignup: PendingSignup) {
        dataStore.edit {
            it[PENDING_UID] = pendingSignup.uid
            it[PENDING_NAME] = pendingSignup.name
            it[PENDING_TERMS_AT] = pendingSignup.termsAt
        }
    }

    override suspend fun clearPendingSignup() {
        dataStore.edit {
            it.remove(PENDING_UID)
            it.remove(PENDING_NAME)
            it.remove(PENDING_TERMS_AT)
        }
    }

    override suspend fun lastVerificationEmailAt(): Long? = dataStore.data.first()[LAST_VERIFICATION_EMAIL_AT]

    override suspend fun setLastVerificationEmailAt(at: Long) {
        dataStore.edit { it[LAST_VERIFICATION_EMAIL_AT] = at }
    }

    override suspend fun lastPasswordResetAt(): Long? = dataStore.data.first()[LAST_PASSWORD_RESET_AT]

    override suspend fun setLastPasswordResetAt(at: Long) {
        dataStore.edit { it[LAST_PASSWORD_RESET_AT] = at }
    }

    override suspend fun clearLastPasswordResetAt() {
        dataStore.edit { it.remove(LAST_PASSWORD_RESET_AT) }
    }

    override suspend fun clearOnSignOut() {
        dataStore.edit {
            it.remove(PENDING_UID)
            it.remove(PENDING_NAME)
            it.remove(PENDING_TERMS_AT)
            it.remove(LAST_VERIFICATION_EMAIL_AT)
        }
    }

    private companion object {
        val PENDING_UID = stringPreferencesKey("pending_signup_uid")
        val PENDING_NAME = stringPreferencesKey("pending_signup_name")
        val PENDING_TERMS_AT = longPreferencesKey("pending_signup_terms_at")
        val LAST_VERIFICATION_EMAIL_AT = longPreferencesKey("last_verification_email_at")
        val LAST_PASSWORD_RESET_AT = longPreferencesKey("last_password_reset_at")
        val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
    }
}
