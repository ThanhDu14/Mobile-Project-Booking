package com.example.booking_app.feature.admin

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Explicit local-only source set: never starts production application observers. */
class AdminLocalTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, "dagger.hilt.android.testing.HiltTestApplication", context)
}
