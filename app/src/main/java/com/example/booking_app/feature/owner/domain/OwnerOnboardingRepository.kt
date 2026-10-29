package com.example.booking_app.feature.owner.domain

/** Read operations for onboarding data. All access is scoped to the signed-in applicant. */
interface OwnerOnboardingRepository {
    suspend fun getAccountState(): OwnerAccountState

    /** Returns matching records without choosing which one is current; that policy is unresolved. */
    suspend fun getApplicantApplications(): List<OwnerApplication>
}

/** Server boundary for the authoritative applicant status screen. */
interface OwnerApplicationStatusRepository {
    suspend fun getAuthoritativeStatus(): OwnerApplicationStatusSnapshot
}
