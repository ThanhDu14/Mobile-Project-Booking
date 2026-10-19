package com.example.booking_app.core.network

/** Lấy Firebase ID token cho Edge Function và supabase-kt (Third-party Auth). */
interface IdTokenProvider {
    /** Ném [AppError.NotSignedIn] khi đang ở chế độ khách. */
    suspend fun getToken(forceRefresh: Boolean = false): String
}
