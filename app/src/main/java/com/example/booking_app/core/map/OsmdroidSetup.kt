package com.example.booking_app.core.map

import android.content.Context
import android.content.res.Configuration as AndroidConfiguration
import com.example.booking_app.BuildConfig
import org.osmdroid.config.Configuration
import java.io.File

/** Cấu hình osmdroid dùng chung cho bản đồ ở chi tiết sân (030) và bản đồ tìm sân (021). */
object OsmdroidSetup {
    /**
     * Chính sách tile của OSM yêu cầu user agent nhận diện được ứng dụng. Tên gói chung như `com.example.*`
     * (và mặc định của osmdroid) bị chặn: máy chủ trả ảnh "403 Access blocked" với mã 200.
     */
    val USER_AGENT = "SmashNow/${BuildConfig.VERSION_NAME} (Android; +https://github.com/ThanhDu14/Mobile-Project-Booking)"

    /** Đặt user agent và cache tile trong `cacheDir/osmdroid` (không cần quyền bộ nhớ). Gọi lại nhiều lần vô hại. */
    fun configure(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = USER_AGENT
            val base = File(context.cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
        }
    }

    /** Chế độ tối: đảo màu tile cho dịu mắt. */
    fun isNight(context: Context): Boolean =
        context.resources.configuration.uiMode and AndroidConfiguration.UI_MODE_NIGHT_MASK ==
            AndroidConfiguration.UI_MODE_NIGHT_YES
}
