package com.example.booking_app.feature.booking.data.mapper

import com.example.booking_app.feature.booking.ui.HoldToPaymentArgs
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Builds standard Firestore booking document for feature 040/050 slice,
 * fully conforming to docs/design/database/README.md §4.5 and BookingManagementMapper.
 */
object BookingDocumentBuilder {

    private val ZONE_VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    fun build(
        args: HoldToPaymentArgs,
        paymentMethod: String,
        userId: String,
        nowMillis: Long,
        venuePhone: String? = null,
        useServerTimestamp: Boolean = true
    ): Map<String, Any?> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(args.slotIds.isNotEmpty()) { "slotIds must not be empty" }

        val fullAddress = args.venueAddress.ifEmpty {
            if (args.districtName.isNotEmpty()) "Quận ${args.districtName}, TP. Hồ Chí Minh" else "TP. Hồ Chí Minh"
        }

        val venueSnapshot = hashMapOf<String, Any?>(
            "name" to args.venueName.ifEmpty { "Cơ sở ${args.venueId}" },
            "address" to fullAddress,
            "phone" to venuePhone,
            "photoUrl" to args.photoUrl.ifEmpty { null }
        )

        // Parse courtDetails to map courtId/badge -> (courtName, courtSubtotal)
        // Format: "$courtBadge|$courtName|$surfaceType|$dateText|$timeRangeText|$subtotal"
        data class CourtDetailInfo(val courtName: String, val subtotal: Long)
        val courtDetailMap = mutableListOf<CourtDetailInfo>()
        for (detail in args.courtDetails) {
            val parts = detail.split("|")
            val rawName = if (parts.size > 1) parts[1] else "Sân"
            val surface = if (parts.size > 2) parts[2] else ""
            val name = if (surface.isNotEmpty()) "$rawName · $surface" else rawName
            val subtotal = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
            courtDetailMap.add(CourtDetailInfo(name, subtotal))
        }

        // Group slots by courtId (first part of slotId: {courtId}_{date}_{startTime})
        val courtGroups = args.slotIds.groupBy { it.split("_")[0] }
        val items = mutableListOf<Map<String, Any?>>()

        if (courtDetailMap.isNotEmpty() && courtDetailMap.size == courtGroups.size) {
            var index = 0
            for ((courtId, courtSlots) in courtGroups) {
                val detailInfo = courtDetailMap[index]
                val sortedSlots = courtSlots.sorted()
                val n = sortedSlots.size
                val basePrice = detailInfo.subtotal / n
                val remainder = detailInfo.subtotal % n

                sortedSlots.forEachIndexed { slotIdx, slotId ->
                    val (startMillis, endMillis) = parseSlotTimes(slotId, nowMillis)
                    val price = if (slotIdx == n - 1) basePrice + remainder else basePrice
                    items.add(
                        hashMapOf(
                            "slotId" to slotId,
                            "courtId" to courtId,
                            "courtName" to detailInfo.courtName,
                            "startAt" to Timestamp(Date(startMillis)),
                            "endAt" to Timestamp(Date(endMillis)),
                            "price" to price
                        )
                    )
                }
                index++
            }
        } else {
            // Fallback: divide subtotalAmount equally among all slots, remainder to the last slot
            val totalSlots = args.slotIds.size
            val basePrice = args.subtotalAmount / totalSlots
            val remainder = args.subtotalAmount % totalSlots

            args.slotIds.sorted().forEachIndexed { slotIdx, slotId ->
                val (startMillis, endMillis) = parseSlotTimes(slotId, nowMillis)
                val courtId = slotId.split("_")[0]
                val price = if (slotIdx == totalSlots - 1) basePrice + remainder else basePrice
                val defaultName = if (courtDetailMap.isNotEmpty()) courtDetailMap[0].courtName else "Sân $courtId"
                items.add(
                    hashMapOf(
                        "slotId" to slotId,
                        "courtId" to courtId,
                        "courtName" to defaultName,
                        "startAt" to Timestamp(Date(startMillis)),
                        "endAt" to Timestamp(Date(endMillis)),
                        "price" to price
                    )
                )
            }
        }

        // Guarantee total preservation: sum(items.price) == args.subtotalAmount
        val currentSum = items.sumOf { (it["price"] as Long) }
        if (currentSum != args.subtotalAmount && items.isNotEmpty()) {
            val diff = args.subtotalAmount - currentSum
            val last = items.last() as HashMap<String, Any?>
            last["price"] = (last["price"] as Long) + diff
        }

        val minStartMillis = items.minOf { (it["startAt"] as Timestamp).toDate().time }
        val maxEndMillis = items.maxOf { (it["endAt"] as Timestamp).toDate().time }
        val bookingStart = Timestamp(Date(minStartMillis))
        val bookingEnd = Timestamp(Date(maxEndMillis))

        val subtotal = args.subtotalAmount
        val discount = 0L
        val total = subtotal

        val depositAmount: Long
        val paidAmount: Long
        val remainingAmount: Long
        val paymentMethodEnum = when (paymentMethod.uppercase()) {
            "PAY_AT_VENUE", "AT_VENUE" -> "AT_VENUE"
            "DEPOSIT" -> "DEPOSIT"
            "WALLET", "MOCK_WALLET" -> "MOCK_WALLET"
            else -> "AT_VENUE"
        }
        val paymentStatus: String

        when (paymentMethodEnum) {
            "AT_VENUE" -> {
                depositAmount = 0L
                paidAmount = 0L
                remainingAmount = total
                paymentStatus = "UNPAID"
            }
            "DEPOSIT" -> {
                val raw = (total * 30L) / 100L
                val dep = ((raw + 500L) / 1000L) * 1000L
                depositAmount = dep
                paidAmount = dep
                remainingAmount = total - dep
                paymentStatus = "DEPOSITED"
            }
            "MOCK_WALLET" -> {
                depositAmount = 0L
                paidAmount = total
                remainingAmount = 0L
                paymentStatus = "PAID"
            }
            else -> error("Invalid paymentMethod $paymentMethod")
        }

        val approvalDeadline = Timestamp(Date(minOf(nowMillis + 60 * 60 * 1000L, minStartMillis)))
        val holdExpiresAt = Timestamp(Date(args.holdExpiresAtMillis))
        val nowTimestamp = if (useServerTimestamp) FieldValue.serverTimestamp() else Timestamp(Date(nowMillis))

        val doc = hashMapOf<String, Any?>(
            "userId" to userId,
            "venueId" to args.venueId,
            "venueSnapshot" to venueSnapshot,
            "items" to items,
            "startAt" to bookingStart,
            "endAt" to bookingEnd,
            "status" to "PENDING",
            "subtotal" to subtotal,
            "discount" to discount,
            "total" to total,
            "depositAmount" to depositAmount,
            "paidAmount" to paidAmount,
            "remainingAmount" to remainingAmount,
            "paymentMethod" to paymentMethodEnum,
            "paymentStatus" to paymentStatus,
            "recurringGroupId" to null,
            "qrToken" to null,
            "checkedInAt" to null,
            "holdExpiresAt" to holdExpiresAt,
            "approvalDeadline" to approvalDeadline,
            "cancelReason" to null,
            "cancelledBy" to null,
            "cancelledAt" to null,
            "completedAt" to null,
            "createdAt" to nowTimestamp,
            "updatedAt" to nowTimestamp
        )

        return doc
    }

    private fun parseSlotTimes(slotId: String, fallbackNowMillis: Long): Pair<Long, Long> {
        val parts = slotId.split("_")
        if (parts.size >= 3) {
            val dateStr = parts[1]
            val timeStr = parts[2].padStart(4, '0')
            try {
                val start = calculateSlotStartMillis(dateStr, timeStr)
                return Pair(start, start + 30 * 60 * 1000L)
            } catch (_: Exception) {
            }
        }
        return Pair(fallbackNowMillis, fallbackNowMillis + 30 * 60 * 1000L)
    }

    fun calculateSlotStartMillis(dateStr: String, startTimeStr: String): Long {
        val cleanDate = dateStr.replace("-", "")
        val year = cleanDate.substring(0, 4).toInt()
        val month = cleanDate.substring(4, 6).toInt()
        val day = cleanDate.substring(6, 8).toInt()
        val hour = startTimeStr.substring(0, 2).toInt()
        val minute = startTimeStr.substring(2, 4).toInt()
        return ZonedDateTime.of(
            year, month, day, hour, minute, 0, 0,
            ZONE_VIETNAM
        ).toInstant().toEpochMilli()
    }
}
