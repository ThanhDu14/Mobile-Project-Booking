package com.example.booking_app.feature.courtdetail.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration as AndroidConfiguration
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.booking_app.BuildConfig
import com.example.booking_app.R
import com.example.booking_app.databinding.ViewCourtDetailMapBinding
import com.example.booking_app.feature.courtdetail.domain.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/**
 * Bản đồ nhỏ không tương tác ở mục "Vị trí" (research R7, FR-010, FR-013).
 *
 * - Cấu hình osmdroid: user agent riêng của SmashNow (xem [USER_AGENT]), cache tile trong `cacheDir/osmdroid` (không cần quyền bộ nhớ).
 * - Tắt cuộn, phóng to, xoay; mức phóng 16, marker quả cầu lông ở giữa; chế độ tối đảo màu tile.
 * - `onResume`/`onPause`/`onDetach` của `MapView` theo [lifecycleOwner] (truyền `viewLifecycleOwner`).
 * - Tile đầu tiên tải lỗi trước khi có tile nào thành công (mất mạng, chưa có cache) → [onTilesFailed] để Fragment
 *   ẩn bản đồ, giữ địa chỉ và nút "Chỉ đường".
 */
class VenueMapController(
    private val binding: ViewCourtDetailMapBinding,
    private val lifecycleOwner: LifecycleOwner,
    private val onTilesFailed: () -> Unit,
) : DefaultLifecycleObserver {

    private val mapView: MapView
    private var anyTileLoaded = false
    private var failed = false

    private val tileHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MapTileProviderBase.MAPTILE_SUCCESS_ID -> anyTileLoaded = true
                MapTileProviderBase.MAPTILE_FAIL_ID -> if (!anyTileLoaded && !failed) {
                    failed = true
                    onTilesFailed()
                }
            }
        }
    }

    init {
        val context = binding.root.context
        configureOsmdroid(context)
        mapView = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isFlingEnabled = false
            isTilesScaledToDpi = true
            isClickable = false
            isFocusable = false
            if (isNight(context)) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            tileProvider.tileRequestCompleteHandlers.add(tileHandler)
        }
        binding.mapHost.addView(
            mapView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        installShield()
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /** Đặt marker ở giữa tại [point]; gọi lại khi tọa độ đổi (listener `venues/{id}`). */
    fun show(point: LatLng, venueName: String) {
        val geo = GeoPoint(point.lat, point.lng)
        binding.mapCard.contentDescription =
            binding.root.context.getString(R.string.court_detail_cd_map, venueName)
        mapView.overlays.removeAll { it is Marker }
        mapView.overlays.add(
            Marker(mapView).apply {
                position = geo
                icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_marker_shuttlecock)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
            },
        )
        mapView.controller.setZoom(ZOOM)
        mapView.controller.setCenter(geo)
        mapView.invalidate()
    }

    override fun onResume(owner: LifecycleOwner) = mapView.onResume()

    override fun onPause(owner: LifecycleOwner) = mapView.onPause()

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        mapView.tileProvider.tileRequestCompleteHandlers.remove(tileHandler)
        tileHandler.removeCallbacksAndMessages(null)
        mapView.onDetach()
    }

    /** Lớp phủ nuốt mọi chạm: bản đồ không tương tác, NestedScrollView vẫn chặn thao tác kéo để cuộn. */
    @SuppressLint("ClickableViewAccessibility")
    private fun installShield() {
        binding.mapShield.setOnTouchListener { _, _ -> true }
    }

    private companion object {
        const val ZOOM = 16.0

        /**
         * Chính sách tile của OSM yêu cầu user agent nhận diện được ứng dụng. Tên gói chung như `com.example.*`
         * (và mặc định của osmdroid) bị chặn: máy chủ trả ảnh "403 Access blocked" với mã 200.
         */
        val USER_AGENT = "SmashNow/${BuildConfig.VERSION_NAME} (Android; +https://github.com/ThanhDu14/Mobile-Project-Booking)"

        fun configureOsmdroid(context: Context) {
            Configuration.getInstance().apply {
                userAgentValue = USER_AGENT
                val base = File(context.cacheDir, "osmdroid")
                osmdroidBasePath = base
                osmdroidTileCache = File(base, "tiles")
            }
        }

        fun isNight(context: Context): Boolean =
            context.resources.configuration.uiMode and AndroidConfiguration.UI_MODE_NIGHT_MASK ==
                AndroidConfiguration.UI_MODE_NIGHT_YES
    }
}
