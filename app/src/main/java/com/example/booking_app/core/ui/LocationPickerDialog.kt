package com.example.booking_app.core.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.GeoPoint

/** Shared picker entry point. Results belong to the parent Fragment, including after recreation. */
object LocationPickerDialog {
    internal const val RESULT_KEY = "core_location_picker_result"
    private const val TAG = "core_location_picker"

    fun bind(fragment: Fragment, onSelected: (GeoPoint) -> Unit) {
        fragment.childFragmentManager.setFragmentResultListener(RESULT_KEY, fragment.viewLifecycleOwner) { _, result ->
            onSelected(GeoPoint(result.getDouble("latitude"), result.getDouble("longitude")))
        }
    }

    fun show(fragment: Fragment, initial: GeoPoint?) {
        val manager = fragment.childFragmentManager
        if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
        LocationPickerFragment().apply {
            arguments = Bundle().apply {
                putString("latitude", initial?.latitude?.toString().orEmpty())
                putString("longitude", initial?.longitude?.toString().orEmpty())
            }
        }.show(manager, TAG)
    }
}
