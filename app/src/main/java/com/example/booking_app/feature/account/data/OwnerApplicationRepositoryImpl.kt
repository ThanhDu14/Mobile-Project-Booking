package com.example.booking_app.feature.account.data

import com.example.booking_app.core.network.AppError
import com.example.booking_app.core.session.SessionManager
import com.example.booking_app.feature.account.domain.OwnerApplicationRepository
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class OwnerApplicationRepositoryImpl @Inject constructor(
    private val dataSource: AccountDataSource,
    private val sessionManager: SessionManager,
) : OwnerApplicationRepository {

    override fun observeLatest(): Flow<OwnerApplicationSummary?> = flow {
        val uid = sessionManager.currentUid() ?: throw AppError.NotSignedIn
        emitAll(dataSource.observeLatestOwnerApplication(uid))
    }
}
