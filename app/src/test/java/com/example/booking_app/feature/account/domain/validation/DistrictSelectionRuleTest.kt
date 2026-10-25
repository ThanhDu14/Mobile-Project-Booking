package com.example.booking_app.feature.account.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistrictSelectionRuleTest {

    @Test
    fun `0 den 5 khu vuc khong trung la hop le`() {
        assertTrue(DistrictSelectionRule.isValid(emptyList()))
        assertTrue(DistrictSelectionRule.isValid(listOf("q1")))
        assertTrue(DistrictSelectionRule.isValid(listOf("q1", "q3", "q10", "thu-duc", "binh-thanh")))
    }

    @Test
    fun `6 khu vuc hoac trung la khong hop le (account_error_district_max)`() {
        assertFalse(DistrictSelectionRule.isValid(listOf("q1", "q3", "q10", "thu-duc", "binh-thanh", "go-vap")))
        assertFalse(DistrictSelectionRule.isValid(listOf("q1", "q1")))
    }

    @Test
    fun `chon them chi duoc khi chua du 5`() {
        assertTrue(DistrictSelectionRule.canAdd(listOf("q1", "q3", "q10", "thu-duc")))
        assertFalse(DistrictSelectionRule.canAdd(listOf("q1", "q3", "q10", "thu-duc", "binh-thanh")))
    }
}
