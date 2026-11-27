package com.example.booking_app.feature.chat.domain.usecase

import com.example.booking_app.core.image.ImageCompressor
import com.example.booking_app.core.image.ImageTooLargeException
import com.example.booking_app.core.image.ImageUnreadableException
import com.example.booking_app.core.util.AppClock
import com.example.booking_app.feature.chat.domain.ChatError
import com.example.booking_app.feature.chat.domain.ChatLocalMediaStore
import com.example.booking_app.feature.chat.domain.ChatRepository
import com.example.booking_app.feature.chat.domain.model.ChatMessageRules
import com.example.booking_app.feature.chat.domain.model.OutboxEntry
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

/**
 * Sinh `messageId` UUID tất định, kiểm tra quy tắc nội dung và ảnh (AC 4.3: ảnh lỗi / > 2 MB sau nén
 * thì báo lỗi ngay, không đưa vào outbox), lưu bản nén vào bộ nhớ đệm nội bộ để chịu được tắt/mở app
 * lúc offline và tránh nén 2 lần, rồi đưa vào `ChatOutbox` (T028, T045, R1).
 */
class SendMessage @Inject constructor(
    private val repository: ChatRepository,
    private val imageCompressor: ImageCompressor,
    private val localMediaStore: ChatLocalMediaStore,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        chatId: String,
        rawText: String,
        localImageUri: String? = null,
        messageId: String = UUID.randomUUID().toString(),
    ): Result<OutboxEntry> {
        val trimmed = rawText.trim()
        val valid = if (localImageUri != null) {
            ChatMessageRules.isValidCaption(trimmed)
        } else {
            ChatMessageRules.isValidText(trimmed)
        }
        if (!valid) return Result.failure(ChatError.InvalidArgument())

        var persistedImageUri: String? = null
        if (localImageUri != null) {
            val jpeg = try {
                imageCompressor.compress(localImageUri)
            } catch (e: CancellationException) {
                throw e
            } catch (_: ImageTooLargeException) {
                return Result.failure(ChatError.ImageTooLarge())
            } catch (_: ImageUnreadableException) {
                return Result.failure(ChatError.ImageUnreadable())
            } catch (_: Exception) {
                return Result.failure(ChatError.ImageUnreadable())
            }
            persistedImageUri = try {
                localMediaStore.saveCompressed(messageId, jpeg)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return Result.failure(ChatError.ImageUnreadable())
            }
            // Nếu nguồn là file chụp tạm từ camera trong cacheDir/chat/ thì xóa bản gốc sau khi đã lưu bản nén.
            localMediaStore.deleteIfOwned(localImageUri)
        }

        val entry = OutboxEntry(
            messageId = messageId,
            chatId = chatId,
            text = trimmed,
            localImageUri = persistedImageUri,
            createdAtLocal = clock.now(),
        )
        return repository.send(entry)
            .onFailure { localMediaStore.deleteIfOwned(persistedImageUri) }
            .map { entry }
    }
}
