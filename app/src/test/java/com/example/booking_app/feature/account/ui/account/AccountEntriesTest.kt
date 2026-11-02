package com.example.booking_app.feature.account.ui.account

import com.example.booking_app.core.session.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountEntriesTest {

    private val allLinks: (String) -> Boolean = { true }

    @Test
    fun `co provider password va moi deep link deu co thi hien du muc`() {
        val entries = accountEntries(listOf("password"), AppRole.PLAYER, allLinks)
        assertEquals(AccountEntry.entries.toSet(), entries)
    }

    @Test
    fun `khong co provider password thi an doi mat khau va doi email (FR-015)`() {
        for (providers in listOf(listOf("google.com"), listOf("phone"), emptyList())) {
            val entries = accountEntries(providers, AppRole.PLAYER, allLinks)
            assertFalse(AccountEntry.CHANGE_PASSWORD in entries)
            assertFalse(AccountEntry.CHANGE_EMAIL in entries)
            assertTrue(AccountEntry.LEGAL in entries)
        }
    }

    @Test
    fun `admin khong co muc xoa tai khoan (FR-018)`() {
        val entries = accountEntries(listOf("password"), AppRole.ADMIN, allLinks)
        assertFalse(AccountEntry.DELETE_ACCOUNT in entries)
        assertTrue(AccountEntry.DELETE_ACCOUNT in accountEntries(listOf("password"), AppRole.OWNER, allLinks))
    }

    @Test
    fun `deep link chua co destination thi muc bi an (R14)`() {
        val available = setOf(AccountEntry.LEGAL.deepLink, AccountEntry.FAVORITES.deepLink)
        val entries = accountEntries(listOf("password"), AppRole.PLAYER) { it in available }
        assertEquals(setOf(AccountEntry.LEGAL, AccountEntry.FAVORITES), entries)
    }

    @Test
    fun `deep link dung theo bang R14`() {
        assertEquals("smashnow://bookings/mine", AccountEntry.MY_BOOKINGS.deepLink)
        assertEquals("smashnow://dropin/mine", AccountEntry.MY_DROPINS.deepLink)
        assertEquals("smashnow://favorites", AccountEntry.FAVORITES.deepLink)
        assertEquals("smashnow://notifications/settings", AccountEntry.NOTIFICATION_SETTINGS.deepLink)
        assertEquals("smashnow://wallet/topup", AccountEntry.WALLET_TOPUP.deepLink)
        assertEquals("smashnow://legal/TERMS", AccountEntry.LEGAL.deepLink)
    }
}
