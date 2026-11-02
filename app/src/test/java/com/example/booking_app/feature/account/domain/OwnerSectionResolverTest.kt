package com.example.booking_app.feature.account.domain

import com.example.booking_app.core.session.AppRole
import com.example.booking_app.feature.account.domain.model.OwnerApplicationSummary
import com.example.booking_app.feature.account.domain.model.OwnerSection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Bảng research R6 (FR-008, FR-013). */
class OwnerSectionResolverTest {

    private val submittedAt = Instant.parse("2026-10-20T03:00:00Z")
    private val pendingApp = OwnerApplicationSummary("PENDING", submittedAt, null)
    private val rejectedApp = OwnerApplicationSummary("REJECTED", submittedAt, "Ảnh giấy phép bị mờ")

    private fun resolve(
        role: AppRole,
        status: String,
        revokedReason: String? = null,
        latest: OwnerApplicationSummary? = null,
    ) = OwnerSectionResolver.resolve(role, status, revokedReason, latest)

    @Test
    fun `admin thi an ca muc, du ownerStatus la gi`() {
        listOf("NONE", "PENDING", "APPROVED", "REJECTED").forEach {
            assertEquals(OwnerSection.Hidden, resolve(AppRole.ADMIN, it, "lý do", rejectedApp))
        }
    }

    @Test
    fun `nguoi choi chua nop va chua tung bi thu hoi thi Dang ky lam chu san`() {
        assertEquals(OwnerSection.CanApply, resolve(AppRole.PLAYER, "NONE"))
    }

    @Test
    fun `nguoi choi NONE co ly do thu hoi thi Revoked kem ly do`() {
        assertEquals(OwnerSection.Revoked("Báo cáo sai sự thật"), resolve(AppRole.PLAYER, "NONE", "Báo cáo sai sự thật"))
    }

    @Test
    fun `nguoi choi PENDING thi Pending kem ngay nop cua ho so gan nhat`() {
        assertEquals(OwnerSection.Pending(submittedAt), resolve(AppRole.PLAYER, "PENDING", latest = pendingApp))
    }

    @Test
    fun `PENDING chua doc duoc ho so (Rules chan hoac dang tai) thi Pending khong co ngay`() {
        assertEquals(OwnerSection.Pending(null), resolve(AppRole.PLAYER, "PENDING"))
    }

    @Test
    fun `nguoi choi REJECTED thi Rejected kem ly do tu choi`() {
        assertEquals(OwnerSection.Rejected("Ảnh giấy phép bị mờ"), resolve(AppRole.PLAYER, "REJECTED", latest = rejectedApp))
        assertEquals(OwnerSection.Rejected(null), resolve(AppRole.PLAYER, "REJECTED"))
    }

    @Test
    fun `chu san APPROVED thi Approved`() {
        assertEquals(OwnerSection.Approved, resolve(AppRole.OWNER, "APPROVED"))
    }

    @Test
    fun `lech tam thoi claim OWNER ma ownerStatus con PENDING thi Approved vi claim la nguon quyen`() {
        assertEquals(OwnerSection.Approved, resolve(AppRole.OWNER, "PENDING", latest = pendingApp))
    }

    @Test
    fun `lech nguoc lai ownerStatus APPROVED ma claim chua OWNER thi van la cho duyet, chua cho quan ly`() {
        assertEquals(OwnerSection.Pending(null), resolve(AppRole.PLAYER, "APPROVED"))
    }
}
