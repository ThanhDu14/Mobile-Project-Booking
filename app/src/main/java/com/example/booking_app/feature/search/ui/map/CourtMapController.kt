package com.example.booking_app.feature.search.ui.map

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.booking_app.R
import com.example.booking_app.core.map.OsmdroidSetup
import com.example.booking_app.feature.search.domain.map.CourtMap
import com.example.booking_app.feature.search.domain.map.MapViewport
import com.example.booking_app.feature.search.domain.model.HomeVenue
import com.example.booking_app.feature.search.domain.model.LatLng
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Bản đồ tương tác của màn tìm sân (spec 021), bọc `MapView` của osmdroid.
 *
 * - Báo vùng đang xem qua [Listener.onViewportChanged], phân biệt người dùng kéo/phóng với lệnh từ code (FR-004).
 * - Vẽ marker quả cầu lông cho các sân được giao; marker đang chọn to hơn và đổi màu (FR-007).
 * - Chạm marker → [Listener.onMarkerTapped]; chạm vùng trống → [Listener.onEmptyTapped] (FR-010).
 * - Tile đầu tiên lỗi khi chưa có tile nào thành công → [Listener.onTilesFailed] để quay về danh sách (FR-020).
 */
class CourtMapController(
    host: FrameLayout,
    private val lifecycleOwner: LifecycleOwner,
    private val listener: Listener,
) : DefaultLifecycleObserver {

    interface Listener {
        fun onViewportChanged(viewport: MapViewport, byUser: Boolean)
        fun onMarkerTapped(venueId: String)
        fun onEmptyTapped()
        fun onTilesFailed()
    }

    private val mapView: MapView
    private var userGesture = false
    private var anyTileLoaded = false
    private var tilesFailed = false
    private var shownIds: List<String> = emptyList()
    private var shownSelected: String? = null

    private val tileHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MapTileProviderBase.MAPTILE_SUCCESS_ID -> anyTileLoaded = true
                MapTileProviderBase.MAPTILE_FAIL_ID -> if (!anyTileLoaded && !tilesFailed) {
                    tilesFailed = true
                    listener.onTilesFailed()
                }
            }
        }
    }

    init {
        val context = host.context
        OsmdroidSetup.configure(context)
        mapView = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = MIN_ZOOM
            maxZoomLevel = MAX_ZOOM
            setHorizontalMapRepetitionEnabled(false)
            setVerticalMapRepetitionEnabled(false)
            if (OsmdroidSetup.isNight(context)) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            tileProvider.tileRequestCompleteHandlers.add(tileHandler)
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    listener.onEmptyTapped()
                    return true
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }))
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = report()
                override fun onZoom(event: ZoomEvent?): Boolean = report()
            }, VIEWPORT_DELAY_MS))
        }
        trackGestures()
        host.addView(mapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /** Đặt vùng xem: vùng đã lưu (quay lại từ chi tiết sân) hoặc trung tâm TP.HCM (Clarifications). */
    fun restore(viewport: MapViewport?) {
        userGesture = false
        val center = viewport?.center ?: CourtMap.DEFAULT_CENTER
        mapView.controller.setZoom(viewport?.zoom ?: CourtMap.DEFAULT_ZOOM)
        mapView.controller.setCenter(GeoPoint(center.lat, center.lng))
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> report() }
    }

    /** Di chuyển có hiệu ứng tới [center] (vị trí của tôi, sân gần bạn). */
    fun focus(center: LatLng, zoom: Double) {
        userGesture = false
        mapView.controller.animateTo(GeoPoint(center.lat, center.lng), zoom, FOCUS_ANIMATION_MS)
    }

    /** Vùng đang xem; `null` trước lần bố cục đầu tiên. */
    fun viewport(): MapViewport? {
        if (mapView.width == 0 || mapView.height == 0) return null
        val box = mapView.boundingBox
        return MapViewport(box.latNorth, box.latSouth, box.lonEast, box.lonWest, mapView.zoomLevelDouble)
    }

    /** Vẽ marker cho [venues] (đã lọc theo vùng); bỏ qua nếu không có gì thay đổi. */
    fun render(venues: List<HomeVenue>, selectedId: String?) {
        val ids = venues.map { it.id }
        if (ids == shownIds && selectedId == shownSelected) return
        shownIds = ids
        shownSelected = selectedId
        mapView.overlays.removeAll { it is Marker }
        val context = mapView.context
        val normal = ContextCompat.getDrawable(context, R.drawable.ic_marker_shuttlecock)
        val selected = ContextCompat.getDrawable(context, R.drawable.ic_marker_shuttlecock_selected)
        // Marker đang chọn vẽ sau cùng để nằm trên các marker khác.
        venues.sortedBy { it.id == selectedId }.forEach { venue ->
            val position = venue.position ?: return@forEach
            mapView.overlays.add(Marker(mapView).apply {
                this.position = GeoPoint(position.lat, position.lng)
                icon = if (venue.id == selectedId) selected else normal
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setInfoWindow(null)
                title = venue.name
                setOnMarkerClickListener { _, _ ->
                    listener.onMarkerTapped(venue.id)
                    true
                }
            })
        }
        mapView.invalidate()
    }

    private fun report(): Boolean {
        viewport()?.let { listener.onViewportChanged(it, userGesture) }
        return true
    }

    /** Chạm/kéo của người dùng đánh dấu lần đổi vùng kế tiếp là "do người dùng". */
    @SuppressLint("ClickableViewAccessibility")
    private fun trackGestures() {
        mapView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) userGesture = true
            false
        }
    }

    override fun onResume(owner: LifecycleOwner) = mapView.onResume()
    override fun onPause(owner: LifecycleOwner) = mapView.onPause()
    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        mapView.tileProvider.tileRequestCompleteHandlers.remove(tileHandler)
        tileHandler.removeCallbacksAndMessages(null)
        mapView.onDetach()
    }

    private companion object {
        const val MIN_ZOOM = 10.0
        const val MAX_ZOOM = 19.0
        const val VIEWPORT_DELAY_MS = 250L
        const val FOCUS_ANIMATION_MS = 600L
    }
}
