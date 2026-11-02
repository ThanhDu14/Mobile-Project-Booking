package com.example.booking_app.core.session

import com.example.booking_app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class RootGraphTest {

    @Test
    fun `chu san theo che do dang dung`() {
        assertEquals(R.id.nav_owner, rootGraphFor(AppRole.OWNER, AppMode.OWNER))
        assertEquals(R.id.nav_player, rootGraphFor(AppRole.OWNER, AppMode.PLAYER))
    }

    @Test
    fun `nguoi choi va admin khong phu thuoc che do`() {
        AppMode.entries.forEach { mode ->
            assertEquals(R.id.nav_player, rootGraphFor(AppRole.PLAYER, mode))
            assertEquals(R.id.nav_admin, rootGraphFor(AppRole.ADMIN, mode))
        }
    }
}
