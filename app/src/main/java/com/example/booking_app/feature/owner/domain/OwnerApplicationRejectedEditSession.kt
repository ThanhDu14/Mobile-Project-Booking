package com.example.booking_app.feature.owner.domain

/** Result of applying a server status while a rejected draft is being edited. */
sealed interface RejectedEditResult {
    data object Ready : RejectedEditResult
    data object Conflict : RejectedEditResult
}

/**
 * Keeps applicant-owned edits separate from the last server read.
 * It never changes server status and never invents a version field.
 */
class OwnerApplicationRejectedEditSession(
    initialDraft: OwnerApplicationDraft,
    val rejectReason: String?,
) {
    var draft: OwnerApplicationDraft = initialDraft
        private set

    fun updateDraft(updated: OwnerApplicationDraft) {
        draft = updated
    }

    fun applyAuthoritativeStatus(status: OwnerApplicationReviewStatus): RejectedEditResult =
        if (status == OwnerApplicationReviewStatus.REJECTED) RejectedEditResult.Ready
        else RejectedEditResult.Conflict
}
