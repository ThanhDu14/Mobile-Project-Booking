package com.example.booking_app.feature.chat.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.network.DEFAULT_TIMEOUT_MS
import com.example.booking_app.core.network.FunctionErrors
import com.example.booking_app.feature.chat.data.dto.ChatActionRequest
import com.example.booking_app.feature.chat.data.dto.ChatActionResponse
import com.example.booking_app.feature.chat.data.dto.ChatFunctionJson
import com.example.booking_app.feature.chat.data.dto.ChatMediaRequest
import com.example.booking_app.feature.chat.data.dto.ChatMediaResponse
import com.example.booking_app.feature.chat.data.dto.ChatSendRequest
import com.example.booking_app.feature.chat.data.dto.ChatSendResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject

/** Edge Function `chat-send` (contracts/edge-functions-chat.md mục 1). */
interface ChatSendApi {
    suspend fun invoke(request: ChatSendRequest): ChatSendResponse
}

/** Edge Function `chat-action` (contracts/edge-functions-chat.md mục 2): openDirect, recall, report. */
interface ChatActionApi {
    suspend fun invoke(request: ChatActionRequest): ChatActionResponse
}

/** Edge Function `chat-media-url` (contracts/edge-functions-chat.md mục 3): upload, download, discard. */
interface ChatMediaUrlApi {
    suspend fun invoke(request: ChatMediaRequest): ChatMediaResponse
}

class SupabaseChatSendApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : ChatSendApi {

    override suspend fun invoke(request: ChatSendRequest): ChatSendResponse {
        val body = ChatFunctionJson.encodeToJsonElement(request)
        return functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_CHAT_SEND, body = body) }
            } catch (_: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            ChatFunctionJson.decodeFromString<ChatSendResponse>(response.bodyAsText())
        }
    }

    private companion object {
        const val FN_CHAT_SEND = "chat-send"
    }
}

class SupabaseChatActionApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : ChatActionApi {

    override suspend fun invoke(request: ChatActionRequest): ChatActionResponse {
        val body = ChatFunctionJson.encodeToJsonElement(request)
        return functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_CHAT_ACTION, body = body) }
            } catch (_: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            ChatFunctionJson.decodeFromString<ChatActionResponse>(response.bodyAsText())
        }
    }

    private companion object {
        const val FN_CHAT_ACTION = "chat-action"
    }
}

class SupabaseChatMediaUrlApi @Inject constructor(
    private val supabase: SupabaseClient,
    private val functionErrors: FunctionErrors,
) : ChatMediaUrlApi {

    override suspend fun invoke(request: ChatMediaRequest): ChatMediaResponse {
        val body = ChatFunctionJson.encodeToJsonElement(request)
        return functionErrors.invokeWithAuthRetry {
            val response = try {
                withTimeout(DEFAULT_TIMEOUT_MS) { supabase.functions.invoke(FN_CHAT_MEDIA_URL, body = body) }
            } catch (_: TimeoutCancellationException) {
                throw AppError.Timeout
            }
            ChatFunctionJson.decodeFromString<ChatMediaResponse>(response.bodyAsText())
        }
    }

    private companion object {
        const val FN_CHAT_MEDIA_URL = "chat-media-url"
    }
}
