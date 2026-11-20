package com.example.booking_app.core.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.EditText
import com.example.booking_app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** A real map pin picker; coordinates also work when map tiles cannot load. */
object LocationPickerDialog {
    fun show(context: Context, initial: GeoPoint?, onSelected: (GeoPoint) -> Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val map = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            val visibleInitial=initial?.takeIf { it.latitude in -85.0..85.0 }
            controller.setCenter(org.osmdroid.util.GeoPoint(visibleInitial?.latitude ?: 10.7769, visibleInitial?.longitude ?: 106.7009))
            contentDescription = context.getString(R.string.owner_map_picker_hint)
        }
        column.addView(map, LinearLayout.LayoutParams(-1,(300 * context.resources.displayMetrics.density).toInt()))
        val lat = EditText(context).apply { hint = context.getString(R.string.owner_latitude); inputType = 12290; setText(initial?.latitude?.toString().orEmpty()) }
        val lng = EditText(context).apply { hint = context.getString(R.string.owner_longitude); inputType = 12290; setText(initial?.longitude?.toString().orEmpty()) }
        column.addView(lat); column.addView(lng)
        val marker = Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
        initial?.let { marker.position = org.osmdroid.util.GeoPoint(it.latitude,it.longitude); map.overlays.add(marker) }
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun longPressHelper(point: org.osmdroid.util.GeoPoint?) = singleTapConfirmedHelper(point)
            override fun singleTapConfirmedHelper(point: org.osmdroid.util.GeoPoint?): Boolean {
                point ?: return false
                lat.setText(point.latitude.toString()); lng.setText(point.longitude.toString())
                marker.position = point
                if (marker !in map.overlays) map.overlays.add(marker)
                map.invalidate()
                return true
            }
        }))
        val dialog = MaterialAlertDialogBuilder(context).setTitle(R.string.owner_map_picker_hint).setView(column)
            .setNegativeButton(R.string.common_cancel,null).setPositiveButton(R.string.owner_save_location,null).create()
        dialog.setOnShowListener {
            map.onResume()
            dialog.getButton(-1).setOnClickListener {
                val latitude = lat.text.toString().toDoubleOrNull()
                val longitude = lng.text.toString().toDoubleOrNull()
                if (latitude == null || !latitude.isFinite() || latitude !in -90.0..90.0) lat.error = context.getString(R.string.owner_location_invalid)
                else if (longitude == null || !longitude.isFinite() || longitude !in -180.0..180.0) lng.error = context.getString(R.string.owner_location_invalid)
                else if (!com.example.booking_app.core.location.VenueArea.contains(latitude, longitude)) lat.error = context.getString(R.string.owner_location_outside_vietnam)
                else { onSelected(GeoPoint(latitude,longitude)); dialog.dismiss() }
            }
        }
        dialog.setOnDismissListener { map.onPause(); map.onDetach() }
        dialog.show()
    }
}
