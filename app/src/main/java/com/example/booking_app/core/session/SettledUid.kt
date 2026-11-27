package com.example.booking_app.core.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * UID khi phiên đã chốt: `SignedIn` → uid, `SignedOut` → null. Bỏ qua `Loading`, `Initializing`,
 * `NeedsConsent`, `InitFailed` (MainActivity tự điều hướng các trạng thái đó), nên màn hình mở bằng
 * deep link lúc cold start không bị coi là khách, và tự nạp lại khi đăng nhập xong quay về.
 */
fun SessionManager.settledUid(): Flow<String?> = sessionState
    .filter { it is SessionState.SignedIn || it is SessionState.SignedOut }
    .map { (it as? SessionState.SignedIn)?.uid }
    .distinctUntilChanged()
