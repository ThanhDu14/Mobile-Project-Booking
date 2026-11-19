package com.example.booking_app.feature.chat.data

import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatMediaRepository
import com.example.booking_app.feature.chat.domain.model.ChatSignedUrls
import com.example.booking_app.feature.chat.domain.model.UploadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Khung ban đầu cho T018; cài đặt đầy đủ ở T045 (US4). */
@Singleton
class SupabaseChatMediaRepository @Inject constructor() : ChatMediaRepository {
    override fun upload(chatId: String, messageId: String, uri: String): Flow<UploadState> =
        flowOf(UploadState.Failed(ChatError.Unknown("NOT_IMPLEMENTED")))

    override suspend fun signedUrls(paths: List<String>): Result<ChatSignedUrls> =
        Result.success(ChatSignedUrls(emptyMap(), null))

    override suspend fun discard(chatId: String, messageId: String): Result<Unit> = Result.success(Unit)
}
