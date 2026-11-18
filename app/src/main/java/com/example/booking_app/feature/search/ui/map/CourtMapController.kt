package com.example.booking_app.feature.search.ui.map

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import com.example.booking_app.feature.search.domain.map.MarkerCluster
import com.example.booking_app.feature.search.domain.map.ScreenPoint
import com.google.android.material.color.MaterialColors
import org.osmdroid.util.BoundingBox
import kotlin.math.roundToInt
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
    private var shownKey: Any? = null
    private var myLocationMarker: Marker? = null

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

    /**
     * Vẽ marker cho [venues] (đã lọc theo vùng). Marker chồng nhau ở mức phóng hiện tại gộp thành cụm có số lượng
     * (FR-003); sân đang chọn luôn vẽ riêng. Bỏ qua nếu danh sách, sân chọn và mức phóng không đổi.
     */
    fun render(venues: List<HomeVenue>, selectedId: String?) {
        val zoom = mapView.zoomLevelDouble
        val key = Triple(venues.map { it.id }, selectedId, (zoom * 10).roundToInt())
        if (key == shownKey && mapView.width > 0) return
        shownKey = key
        mapView.overlays.removeAll { it is Marker }
        val context = mapView.context
        val normal = ContextCompat.getDrawable(context, R.drawable.ic_marker_shuttlecock)
        val selected = ContextCompat.getDrawable(context, R.drawable.ic_marker_shuttlecock_selected)
        val byId = venues.filter { it.position != null }.associateBy { it.id }

        val others = byId.values.filter { it.id != selectedId }
        val clusters = if (mapView.width == 0) {
            others.map { MarkerCluster(listOf(it.id), 0f, 0f) }
        } else {
            val projection = mapView.projection
            val point = Point()
            CourtMap.cluster(others.map { venue ->
                projection.toPixels(venue.position!!.toGeoPoint(), point)
                ScreenPoint(venue.id, point.x.toFloat(), point.y.toFloat())
            }, CLUSTER_RADIUS_DP * context.resources.displayMetrics.density)
        }
        for (cluster in clusters) {
            val members = cluster.venueIds.mapNotNull(byId::get)
            if (cluster.isSingle) mapView.overlays.add(venueMarker(members.first(), normal))
            else mapView.overlays.add(clusterMarker(members))
        }
        // Marker đang chọn vẽ sau cùng để nằm trên các marker khác; chấm vị trí của tôi giữ qua mỗi lần vẽ lại.
        byId[selectedId]?.let { mapView.overlays.add(venueMarker(it, selected)) }
        myLocationMarker?.let(mapView.overlays::add)
        mapView.invalidate()
    }

    /** Chấm "vị trí của tôi" (FR-011); `null` thì bỏ chấm. Chỉ vẽ trên máy, không lưu (FR-015). */
    fun showMyLocation(point: LatLng?) {
        val current = myLocationMarker
        if (point == null) {
            current?.let(mapView.overlays::remove)
            myLocationMarker = null
        } else if (current == null) {
            myLocationMarker = Marker(mapView).apply {
                position = point.toGeoPoint()
                icon = myLocationDot()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                title = mapView.context.getString(R.string.map_my_location)
                setOnMarkerClickListener { _, _ -> true }
            }.also(mapView.overlays::add)
        } else {
            current.position = point.toGeoPoint()
        }
        mapView.invalidate()
    }

    /** Chấm tròn màu chính có viền trắng và quầng mờ, kiểu chấm vị trí quen thuộc. */
    private fun myLocationDot(): BitmapDrawable {
        val context = mapView.context
        val density = context.resources.displayMetrics.density
        val size = (DOT_SIZE_DP * density).roundToInt()
        val color = MaterialColors.getColor(mapView, androidx.appcompat.R.attr.colorPrimary)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        paint.color = color
        paint.alpha = 60
        canvas.drawCircle(center, center, center, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(center, center, 9 * density, paint)
        paint.color = color
        canvas.drawCircle(center, center, 6.5f * density, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun venueMarker(venue: HomeVenue, icon: android.graphics.drawable.Drawable?) = Marker(mapView).apply {
        position = venue.position!!.toGeoPoint()
        this.icon = icon
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        setInfoWindow(null)
        title = venue.name
        setOnMarkerClickListener { _, _ ->
            listener.onMarkerTapped(venue.id)
            true
        }
    }

    /** Cụm: chạm thì phóng vào vùng của cụm; đã ở mức phóng tối đa thì chọn sân đầu tiên để vuốt thẻ qua cụm. */
    private fun clusterMarker(members: List<HomeVenue>) = Marker(mapView).apply {
        val points = members.map { it.position!!.toGeoPoint() }
        position = points.first()
        icon = clusterIcon(members.size)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        title = mapView.context.getString(R.string.map_cd_cluster, members.size)
        setOnMarkerClickListener { _, _ ->
            if (mapView.zoomLevelDouble >= mapView.maxZoomLevel - 0.5) {
                listener.onMarkerTapped(members.first().id)
            } else {
                userGesture = true
                val border = (CLUSTER_RADIUS_DP * 2 * mapView.context.resources.displayMetrics.density).roundToInt()
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), true, border)
            }
            true
        }
    }

    /** Vòng tròn màu chính, viền màu nền chữ, số sân ở giữa. */
    private fun clusterIcon(count: Int): BitmapDrawable {
        val context = mapView.context
        val density = context.resources.displayMetrics.density
        val size = (CLUSTER_SIZE_DP * density).roundToInt()
        val fill = MaterialColors.getColor(mapView, androidx.appcompat.R.attr.colorPrimary)
        val onFill = MaterialColors.getColor(mapView, com.google.android.material.R.attr.colorOnPrimary)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = size / 2f
        paint.color = onFill
        canvas.drawCircle(radius, radius, radius, paint)
        paint.color = fill
        canvas.drawCircle(radius, radius, radius - 2.5f * density, paint)
        paint.color = onFill
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 14 * density
        paint.typeface = Typeface.DEFAULT_BOLD
        val label = if (count > 99) "99+" else count.toString()
        canvas.drawText(label, radius, radius - (paint.descent() + paint.ascent()) / 2, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun LatLng.toGeoPoint() = GeoPoint(lat, lng)

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
        /** Hai marker cách nhau dưới khoảng này trên màn hình thì gộp (marker rộng 36dp). */
        const val CLUSTER_RADIUS_DP = 40f
        const val CLUSTER_SIZE_DP = 40f
        const val DOT_SIZE_DP = 32f
    }
}
