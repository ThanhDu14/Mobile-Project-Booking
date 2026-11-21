package com.example.booking_app.core.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.example.booking_app.R
import com.example.booking_app.core.location.VenueArea
import com.example.booking_app.core.map.OsmdroidSetup
import com.example.booking_app.databinding.DialogLocationPickerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

/** Saved dialog state + FragmentResult keep an unconfirmed pin through configuration/process recreation. */
class LocationPickerFragment : DialogFragment() {
    private var binding: DialogLocationPickerBinding? = null
    private var map: MapView? = null
    private var marker: Marker? = null
    private var settingFields = false
    private var tileLoaded = false
    private val tileHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val current = binding ?: return
            when (msg.what) {
                MapTileProviderBase.MAPTILE_SUCCESS_ID -> { tileLoaded = true; current.mapStatus.isVisible = false; current.retryMap.isVisible = false }
                MapTileProviderBase.MAPTILE_FAIL_ID -> if (!tileLoaded) { current.mapStatus.setText(R.string.owner_map_failed); current.mapStatus.isVisible = true; current.retryMap.isVisible = true }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // osmdroid handles map gestures and the accessible coordinate inputs remain available.
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        OsmdroidSetup.configure(context)
        val ui = DialogLocationPickerBinding.inflate(layoutInflater).also { binding = it }
        val state = savedInstanceState ?: arguments
        val latitude = state?.getString("latitude").orEmpty()
        val longitude = state?.getString("longitude").orEmpty()
        ui.latitude.setText(latitude); ui.longitude.setText(longitude)
        val initial = point(latitude, longitude)
        val view = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            if (OsmdroidSetup.isNight(context)) overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(state?.getDouble("zoom", 15.0) ?: 15.0)
            controller.setCenter(if (state?.containsKey("centerLat") == true) GeoPoint(state.getDouble("centerLat"),state.getDouble("centerLng")) else initial ?: GeoPoint(10.7769,106.7009))
            contentDescription = getString(R.string.owner_map_picker_hint)
            tileProvider.tileRequestCompleteHandlers.add(tileHandler)
            setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
                false
            }
        }
        map = view
        ui.mapHost.layoutParams.height = (context.resources.configuration.screenHeightDp.coerceAtMost(700) * 0.4f * context.resources.displayMetrics.density).toInt()
        ui.mapHost.addView(view, ViewGroup.LayoutParams(-1,-1))
        val pin = Marker(view).apply {
            icon = ContextCompat.getDrawable(context,R.drawable.ic_marker_shuttlecock)
            setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragStart(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) { choose(marker.position,center=false) }
            })
        }
        marker = pin
        view.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { p ?: return false; choose(p,center=false); return true }
            override fun longPressHelper(p: GeoPoint?) = singleTapConfirmedHelper(p)
        }))
        initial?.let { pin.position=it;view.overlays.add(pin) }
        listOf(ui.latitude,ui.longitude).forEach { input -> input.doAfterTextChanged {
            if (!settingFields) point(ui.latitude.text.toString(),ui.longitude.text.toString())?.let { setPin(it,center=true) }
        } }
        ui.retryMap.setOnClickListener {
            tileLoaded=false;ui.mapStatus.setText(R.string.owner_map_loading);ui.mapStatus.isVisible=true;ui.retryMap.isVisible=false
            view.tileProvider.clearTileCache();view.invalidate()
        }
        return MaterialAlertDialogBuilder(context).setTitle(R.string.owner_map_picker_hint).setView(ui.root)
            .setNegativeButton(R.string.common_cancel,null).setPositiveButton(R.string.owner_save_location,null).create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(-1)?.setOnClickListener {
            val ui=binding ?: return@setOnClickListener
            val p=point(ui.latitude.text.toString(),ui.longitude.text.toString())
            if (p==null) { ui.latitude.error=getString(R.string.owner_location_invalid);return@setOnClickListener }
            if (!VenueArea.contains(p.latitude,p.longitude)) { ui.latitude.error=getString(R.string.owner_location_outside_vietnam);return@setOnClickListener }
            parentFragmentManager.setFragmentResult(LocationPickerDialog.RESULT_KEY,Bundle().apply {putDouble("latitude",p.latitude);putDouble("longitude",p.longitude)})
            dismiss()
        }
    }
    override fun onResume() { super.onResume();map?.onResume() }
    override fun onPause() { map?.onPause();super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.let {outState.putString("latitude",it.latitude.text.toString());outState.putString("longitude",it.longitude.text.toString())}
        map?.let {outState.putDouble("centerLat",it.mapCenter.latitude);outState.putDouble("centerLng",it.mapCenter.longitude);outState.putDouble("zoom",it.zoomLevelDouble)}
    }
    override fun onDestroyView() {
        map?.tileProvider?.tileRequestCompleteHandlers?.remove(tileHandler);tileHandler.removeCallbacksAndMessages(null)
        map?.onDetach();map=null;marker=null;binding=null
        super.onDestroyView()
    }
    private fun choose(point: GeoPoint, center: Boolean) {
        val ui=binding ?: return
        ui.latitude.error=null;ui.longitude.error=null
        settingFields=true;ui.latitude.setText(point.latitude.toString());ui.longitude.setText(point.longitude.toString());settingFields=false
        setPin(point,center)
    }
    private fun setPin(point: GeoPoint, center: Boolean) {
        val view=map ?: return;val pin=marker ?: return
        pin.position=point;if(pin !in view.overlays)view.overlays.add(pin)
        if(center)view.controller.setCenter(point)
        view.invalidate()
    }
    private fun point(latitude: String, longitude: String): GeoPoint? {
        val lat=latitude.replace(',','.').toDoubleOrNull() ?: return null
        val lng=longitude.replace(',','.').toDoubleOrNull() ?: return null
        return if(lat.isFinite()&&lng.isFinite()&&lat in -85.0..85.0&&lng in -180.0..180.0) GeoPoint(lat,lng) else null
    }
}
