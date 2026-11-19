package com.example.booking_app.feature.chat.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.booking_app.core.chat.UnreadChatsReader
import com.example.booking_app.core.media.SignedUrlBatch
import com.example.booking_app.core.media.SignedUrlCache
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.data.AndroidChatLocalMediaStore
import com.example.booking_app.feature.chat.data.AndroidNetworkMonitor
import com.example.booking_app.feature.chat.data.ChatActionApi
import com.example.booking_app.feature.chat.data.ChatMediaUrlApi
import com.example.booking_app.feature.chat.data.ChatOutboxTransport
import com.example.booking_app.feature.chat.data.ChatSendApi
import com.example.booking_app.feature.chat.data.ChatStorageUploader
import com.example.booking_app.feature.chat.data.DefaultChatOutboxTransport
import com.example.booking_app.feature.chat.data.FirestoreChatRepository
import com.example.booking_app.feature.chat.data.NetworkMonitor
import com.example.booking_app.feature.chat.data.SupabaseChatActionApi
import com.example.booking_app.feature.chat.data.SupabaseChatMediaRepository
import com.example.booking_app.feature.chat.data.SupabaseChatMediaUrlApi
import com.example.booking_app.feature.chat.data.SupabaseChatSendApi
import com.example.booking_app.feature.chat.data.SupabaseChatStorageUploader
import com.example.booking_app.feature.chat.data.UnreadChatsReaderImpl
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import com.example.booking_app.feature.chat.domain.ChatMediaRepository
import com.example.booking_app.feature.chat.domain.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatOutboxDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatMediaSignedUrlCache

/** Hilt module cho spec 091 (T018). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: FirestoreChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindChatMediaRepository(impl: SupabaseChatMediaRepository): ChatMediaRepository

    @Binds
    @Singleton
    abstract fun bindUnreadChatsReader(impl: UnreadChatsReaderImpl): UnreadChatsReader

    @Binds
    abstract fun bindChatSendApi(impl: SupabaseChatSendApi): ChatSendApi

    @Binds
    abstract fun bindChatActionApi(impl: SupabaseChatActionApi): ChatActionApi

    @Binds
    abstract fun bindChatMediaUrlApi(impl: SupabaseChatMediaUrlApi): ChatMediaUrlApi

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindChatOutboxTransport(impl: DefaultChatOutboxTransport): ChatOutboxTransport

    @Binds
    @Singleton
    abstract fun bindChatLocalMediaStore(impl: AndroidChatLocalMediaStore): ChatLocalMediaStore

    @Binds
    @Singleton
    abstract fun bindChatStorageUploader(impl: SupabaseChatStorageUploader): ChatStorageUploader

    companion object {
        private const val MAX_CHAT_DOWNLOAD_BATCH = 30

        @Provides
        @Singleton
        @ChatOutboxDataStore
        fun provideChatOutboxDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("chat_outbox") }

        @Provides
        @Singleton
        @ChatMediaSignedUrlCache
        fun provideChatSignedUrlCache(media: ChatMediaRepository, clock: AppClock): SignedUrlCache =
            SignedUrlCache(
                fetch = { paths ->
                    // `chat-media-url download` giới hạn tối đa 30 path mỗi lần gọi.
                    val merged = LinkedHashMap<String, String>()
                    var earliestExpiry: java.time.Instant? = null
                    var lastError: Throwable? = null
                    for (chunk in paths.chunked(MAX_CHAT_DOWNLOAD_BATCH)) {
                        media.signedUrls(chunk).fold(
                            onSuccess = { batch ->
                                merged.putAll(batch.urls)
                                val exp = batch.expiresAt
                                if (exp != null && (earliestExpiry == null || exp.isBefore(earliestExpiry))) {
                                    earliestExpiry = exp
                                }
                            },
                            onFailure = { lastError = it },
                        )
                    }
                    if (merged.isEmpty() && lastError != null) {
                        Result.failure(lastError!!)
                    } else {
                        Result.success(SignedUrlBatch(merged, earliestExpiry))
                    }
                },
                clock = clock,
            )
    }
}
