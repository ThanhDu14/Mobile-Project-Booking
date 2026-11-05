package com.example.booking_app.feature.owner

import com.example.booking_app.feature.owner.domain.DropInEditableInput
import com.example.booking_app.feature.owner.domain.DropInValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DropInValidationTest {
    @Test
    fun acceptsPositiveCapacityAndZeroPriceAtCurrentOccupancy() {
        assertTrue(
            DropInValidation.validate(
                DropInEditableInput(capacity = 8, pricePerPerson = 0L),
                registeredCount = 8
            ).isEmpty()
        )
    }

    @Test
    fun rejectsCapacityBelowRegisteredPeople() {
        val errors = DropInValidation.validate(
            DropInEditableInput(capacity = 3, pricePerPerson = 25_000L),
            registeredCount = 4
        )

        assertEquals(listOf("capacity"), errors.map { it.field })
    }

    @Test
    fun rejectsNonPositiveCapacityAndNegativePrice() {
        val errors = DropInValidation.validate(
            DropInEditableInput(capacity = 0, pricePerPerson = -1L),
            registeredCount = 0
        )

        assertEquals(setOf("capacity", "pricePerPerson"), errors.map { it.field }.toSet())
    }

    @Test
    fun rejectsInvalidServerCountInsteadOfTreatingItAsEmpty() {
        val errors = DropInValidation.validate(
            DropInEditableInput(capacity = 6, pricePerPerson = 10_000L),
            registeredCount = -1
        )

        assertEquals(listOf("capacity"), errors.map { it.field })
    }
}
