package com.example.booking_app.core.pricing

/**
 * Đổi document `priceRules` sang [PriceRule]. Một mapper chung cho 020, 030, 040 thay vì mỗi spec một DTO.
 * Bản ghi thiếu `daysOfWeek`, `startTime`, `endTime` hoặc `pricePerHour` bị bỏ qua (`null`).
 */
object PriceRuleMapper {

    fun fromFirestore(id: String, data: Map<String, Any?>): PriceRule? {
        val days = (data["daysOfWeek"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val start = data["startTime"] as? String ?: return null
        val end = data["endTime"] as? String ?: return null
        val price = (data["pricePerHour"] as? Number)?.toLong() ?: return null
        val label = data["label"] as? String ?: "NORMAL"
        return PriceRule(id, days, start, end, price, label)
    }
}
