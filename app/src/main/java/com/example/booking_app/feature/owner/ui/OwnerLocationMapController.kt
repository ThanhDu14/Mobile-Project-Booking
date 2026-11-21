package com.example.booking_app.feature.owner.ui

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.booking_app.R
import com.example.booking_app.core.map.OsmdroidSetup
import com.example.booking_app.databinding.ViewOwnerLocationPreviewBinding
import com.google.firebase.firestore.GeoPoint
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

/** Uses the same provider, cache, user agent, night mode and marker as Features 021/030. */
class OwnerLocationMapController(
    private val binding: ViewOwnerLocationPreviewBinding,
    private val owner: LifecycleOwner,
    onPick: () -> Unit,
) : DefaultLifecycleObserver {
    private var point: GeoPoint? = null
    private var loaded = false
    private val tileHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MapTileProviderBase.MAPTILE_SUCCESS_ID -> { loaded=true;binding.retryMap.isVisible=false;binding.mapStatus.isVisible=point==null;if(point==null)binding.mapStatus.setText(R.string.owner_map_unselected) }
                MapTileProviderBase.MAPTILE_FAIL_ID -> if(!loaded) {binding.mapStatus.setText(R.string.owner_map_failed);binding.mapStatus.isVisible=true;binding.retryMap.isVisible=true}
            }
        }
    }
    private val map: MapView
    init {
        val context=binding.root.context
        OsmdroidSetup.configure(context)
        map=MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK);setMultiTouchControls(false)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi=true;isFlingEnabled=false;isClickable=false;isFocusable=false
            if(OsmdroidSetup.isNight(context))overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(13.0);controller.setCenter(org.osmdroid.util.GeoPoint(10.7769,106.7009))
            tileProvider.tileRequestCompleteHandlers.add(tileHandler)
        }
        binding.mapHost.addView(map,ViewGroup.LayoutParams(-1,-1))
        binding.mapShield.setOnClickListener {onPick()}
        binding.retryMap.setOnClickListener {
            loaded=false;binding.mapStatus.setText(R.string.owner_map_loading);binding.mapStatus.isVisible=true;binding.retryMap.isVisible=false
            map.tileProvider.clearTileCache();map.invalidate()
        }
        owner.lifecycle.addObserver(this)
    }
    fun show(location: GeoPoint?, label: String) {
        binding.mapShield.contentDescription=binding.root.context.getString(R.string.owner_map_preview_description,label.ifBlank {binding.root.context.getString(R.string.owner_venue_info)})
        if(point==location)return
        point=location
        map.overlays.removeAll {it is Marker}
        location?.let { geo ->
            val p=org.osmdroid.util.GeoPoint(geo.latitude,geo.longitude)
            map.overlays.add(Marker(map).apply {
                position=p;icon=ContextCompat.getDrawable(map.context,R.drawable.ic_marker_shuttlecock)
                setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);setInfoWindow(null)
            })
            map.controller.setZoom(16.0);map.controller.setCenter(p)
        }
        if(loaded) {binding.mapStatus.isVisible=location==null;if(location==null)binding.mapStatus.setText(R.string.owner_map_unselected)}
        map.invalidate()
    }
    fun setEditable(value: Boolean) {binding.mapShield.isEnabled=value}
    override fun onResume(owner: LifecycleOwner) = map.onResume()
    override fun onPause(owner: LifecycleOwner) = map.onPause()
    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this);map.tileProvider.tileRequestCompleteHandlers.remove(tileHandler)
        tileHandler.removeCallbacksAndMessages(null);map.onDetach()
    }
}
