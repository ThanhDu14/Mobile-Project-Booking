package com.example.booking_app.feature.courtdetail.domain.model

import com.example.booking_app.core.schedule.TimeOfDay
import java.time.LocalDate

/** Khung giờ từ tìm kiếm (spec 030 R1). Chỉ có khi đủ ngày, giờ bắt đầu, giờ kết thúc hợp lệ và chưa qua. */
data class SearchWindow(val date: LocalDate, val start: TimeOfDay, val end: TimeOfDay)

/** Tham số vào màn hình chi tiết sân (Safe Args hoặc deep link `smashnow://venue/{venueId}`). */
data class CourtDetailArgs(val venueId: String, val window: SearchWindow?)
