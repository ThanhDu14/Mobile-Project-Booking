package com.example.booking_app.feature.dropin.domain.usecase

import com.example.booking_app.feature.dropin.domain.model.DropInRegistration
import com.example.booking_app.feature.dropin.domain.model.DropInSession
import kotlinx.coroutines.flow.Flow

/*
 * Nguồn dữ liệu riêng của màn hình vé (US3, T048). Tách khỏi DropInRepositories.kt để không đụng hợp đồng chung;
 * bản cài ở `data/FirestoreDropInTicketSources.kt`, bind trong `data/di/DropInTicketModule.kt`.
 */

/** Vé đọc từ cache của Firestore; [session] `null` khi buổi chưa có trong cache. */
data class CachedDropInTicket(
    val registration: DropInRegistration,
    val session: DropInSession?,
)

/** Đọc vé bằng `Source.CACHE` để hiện ngay (< 1 giây) kể cả khi mất mạng (FR-015, research R7). */
fun interface ReadCachedDropInTicket {
    /** `null` khi chưa từng mở vé này trên máy, là khách, hoặc đọc cache lỗi. Không ném lỗi. */
    suspend operator fun invoke(sessionId: String): CachedDropInTicket?
}

/** Có mạng hay không; phát giá trị hiện tại ngay khi bắt đầu nghe. */
fun interface ObserveConnectivity {
    operator fun invoke(): Flow<Boolean>
}

/** Tọa độ của cơ sở (`venues/{venueId}.location`) cho nút "Chỉ đường" (AC 3.2). */
data class VenueLocation(val latitude: Double, val longitude: Double)

fun interface GetVenueLocation {
    /** `null` khi cơ sở chưa có tọa độ hoặc đọc lỗi (ví dụ cơ sở đã ẩn). Không ném lỗi. */
    suspend operator fun invoke(venueId: String): VenueLocation?
}
