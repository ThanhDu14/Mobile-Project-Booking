package com.example.booking_app.feature.search.domain

import kotlinx.coroutines.flow.Flow

/** Sự kiện nhận dạng giọng nói (spec 022 US4). Chữ nhận được chỉ điền vào ô, không tự gửi. */
sealed interface VoiceEvent {
    /** Máy đã sẵn sàng nghe. */
    data object Ready : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    /** Âm lượng đang thu, chuẩn hóa về 0..1, để giao diện "nhún" theo giọng nói. */
    data class Level(val value: Float) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Error(val kind: VoiceError) : VoiceEvent
}

enum class VoiceError { NO_MATCH, NETWORK, BUSY, OTHER }

/**
 * Nhận dạng giọng nói tiếng Việt bằng dịch vụ có sẵn trên máy (research R15). Không gửi âm thanh
 * qua máy chủ của app; dịch vụ nhận dạng của hệ thống (thường là Google) xử lý.
 */
interface VoiceInput {
    fun isAvailable(): Boolean

    /** Nghe một lần; flow kết thúc sau [VoiceEvent.Final] hoặc [VoiceEvent.Error]. Hủy collect = dừng nghe. */
    fun listen(): Flow<VoiceEvent>
}
