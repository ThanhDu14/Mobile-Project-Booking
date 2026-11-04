package com.example.booking_app.feature.booking.domain.model

data class RecurringSummary(
    val totalSessions: Int,
    val validSessions: List<RecurringSession>,
    val subtotalAmount: Long,
    val depositAmount: Long,
    val canProceed: Boolean = validSessions.size >= 4,
    val warningMessage: String? = null
) {
    val activeSessions: List<RecurringSession> get() = validSessions
    val isValid: Boolean get() = canProceed
}
