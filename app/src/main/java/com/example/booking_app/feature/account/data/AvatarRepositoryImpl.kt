package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.feature.account.domain.AvatarRepository
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class AvatarRepositoryImpl @Inject constructor(
    private val dataSource: SupabaseAvatarDataSource,
) : AvatarRepository {

    override suspend fun upload(path: String, jpeg: ByteArray) = call { dataSource.upload(path, jpeg) }

    override suspend fun delete(path: String) = call { dataSource.delete(path) }

    /** Lỗi kết nối của supabase-kt và quá 15 giây đều coi là mất mạng (010 AppError.Timeout). */
    private suspend fun call(block: suspend () -> Unit) = accountCall {
        try {
            withTimeout(TIMEOUT_MS) { block() }
        } catch (_: TimeoutCancellationException) {
            throw AppError.Timeout
        } catch (_: HttpRequestException) {
            throw AppError.Network
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
