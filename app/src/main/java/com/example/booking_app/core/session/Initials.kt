package com.example.booking_app.core.session

/**
 * Chữ cái đầu cho vòng tròn khi chưa có ảnh: tên Việt đặt tên riêng ở cuối nên lấy hai từ cuối
 * ("Lê Hoàng Nam" → "HN", wireframe 011 buoc1); một từ thì lấy một chữ; tên rỗng thì chuỗi rỗng.
 * Dùng chung cho avatar ở header (spec 011 US8) và trang Tài khoản.
 */
fun initialsOf(displayName: String): String =
    displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .takeLast(2).joinToString("") { it.first().toString() }.uppercase()
