package com.example.booking_app.feature.notification

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.booking_app.R
import com.example.booking_app.feature.notification.ui.*
import dagger.hilt.android.AndroidEntryPoint

/** Debug-only host for instrumentation; never included in release. */
@AndroidEntryPoint
class NotificationTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = android.widget.FrameLayout(this).apply { id = R.id.notification_test_container }
        setContentView(content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(content.id, when {
                intent.getBooleanExtra("navigation", false) -> androidx.navigation.fragment.NavHostFragment.create(R.navigation.nav_main)
                intent.getBooleanExtra("settings", false) -> NotificationSettingsFragment()
                else -> NotificationFragment()
            })
            .commitNow()
    }
}
