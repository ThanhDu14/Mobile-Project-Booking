package com.example.booking_app.feature.notification.data

import com.example.booking_app.feature.notification.domain.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    companion object {
        @dagger.Provides fun session(manager: com.example.booking_app.core.session.SessionManager): NotificationSession =
            object : NotificationSession {
                override val uids = manager.sessionState.map { (it as? com.example.booking_app.core.session.SessionState.SignedIn)?.uid }
            }
    }
    @Binds abstract fun routes(impl: FirestoreNotificationRouteResolver): NotificationRouteResolver
    @Binds abstract fun inbox(impl: FirestoreNotificationRepository): NotificationRepository
    @Binds abstract fun preferences(impl: FirestoreNotificationPreferencesRepository): NotificationPreferencesRepository
    @Binds abstract fun cache(impl: DataStoreNotificationPreferenceCache): NotificationPreferenceCache
}

/** Platform bindings stay available when UI tests replace the feature repositories. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationDeviceModule {
    @Binds abstract fun installation(impl: NotificationDevicePrefs): com.example.booking_app.core.session.InstallationIdentity
    @Binds abstract fun registrationGuard(impl: NotificationTokenCoordinator): com.example.booking_app.core.session.FcmRegistrationGuard
}
