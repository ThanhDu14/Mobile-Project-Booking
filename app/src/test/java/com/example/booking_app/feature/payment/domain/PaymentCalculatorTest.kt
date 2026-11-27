package com.example.booking_app.feature.payment.domain

import com.example.booking_app.feature.payment.domain.model.PaymentOption
import com.example.booking_app.feature.payment.domain.model.Voucher
import com.example.booking_app.feature.payment.domain.model.VoucherScope
import com.example.booking_app.feature.payment.domain.model.VoucherType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Công thức tiền spec 050 (research R7) chạy trên bảng ca test chung `payment-calc-cases.json`
 * (specs/050-payment-voucher/contracts, nạp vào classpath test qua app/build.gradle.kts).
 * Edge Function chạy cùng file này ở `supabase/functions/_shared/payment-calc.test.ts`.
 */
class PaymentCalculatorTest {

    private data class Case(
        val id: String,
        val subtotal: Long,
        val voucher: Voucher?,
        val discount: Long,
        val total: Long,
        val depositAmount: Long,
        val remainingAfterDeposit: Long,
        val isFree: Boolean,
        val amountDue: Map<PaymentOption, Long>,
    )

    private fun loadCases(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("payment-calc-cases.json")
            ?: error("Không thấy payment-calc-cases.json trên classpath test")
        val root = Json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonObject
        return root.getValue("cases").jsonArray.map { element ->
            val c = element.jsonObject
            val expected = c.getValue("expected").jsonObject
            Case(
                id = c.str("id"),
                subtotal = c.long("subtotal"),
                voucher = c["voucher"].takeIf { it != null && it != JsonNull }?.jsonObject?.let { v ->
                    Voucher(
                        code = "TEST-${c.str("id")}",
                        scope = VoucherScope.VENUE,
                        type = VoucherType.valueOf(v.str("type")),
                        value = v.long("value"),
                        maxDiscount = v["maxDiscount"]?.jsonPrimitive?.longOrNull,
                    )
                },
                discount = expected.long("discount"),
                total = expected.long("total"),
                depositAmount = expected.long("depositAmount"),
                remainingAfterDeposit = expected.long("remainingAfterDeposit"),
                isFree = expected.getValue("isFree").jsonPrimitive.boolean,
                amountDue = expected.getValue("amountDue").jsonObject
                    .mapKeys { PaymentOption.valueOf(it.key) }
                    .mapValues { it.value.jsonPrimitive.long },
            )
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

    @Test
    fun `bang ca test co du 6 dong cua quickstart`() {
        val ids = loadCases().map { it.id }
        assertTrue(ids.containsAll(listOf("Q1", "Q2", "Q3", "Q4", "Q5", "Q6")))
    }

    @Test
    fun `moi ca ra dung giam gia, tong, coc, con lai va mien phi`() {
        loadCases().forEach { c ->
            val b = PaymentCalculator.breakdown(c.subtotal, c.voucher)
            assertEquals("${c.id} subtotal", c.subtotal, b.subtotal)
            assertEquals("${c.id} discount", c.discount, b.discount)
            assertEquals("${c.id} total", c.total, b.total)
            assertEquals("${c.id} depositAmount", c.depositAmount, b.depositAmount)
            assertEquals("${c.id} remainingAfterDeposit", c.remainingAfterDeposit, b.remainingAfterDeposit)
            assertEquals("${c.id} isFree", c.isFree, b.isFree)
        }
    }

    @Test
    fun `so phai tra dung theo tung cach tra`() {
        loadCases().forEach { c ->
            val b = PaymentCalculator.breakdown(c.subtotal, c.voucher)
            assertEquals("${c.id} đủ 3 cách trả", PaymentOption.entries.toSet(), c.amountDue.keys)
            c.amountDue.forEach { (option, expected) ->
                assertEquals("${c.id} amountDue($option)", expected, b.amountDue(option))
            }
        }
    }
}
