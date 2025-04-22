// PurramidApplication.kt
package com.example.purramid.thepurramid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PurramidApplication : Application() {
    // No further code needed here for basic Hilt setup
    // onCreate() can be used for other app-wide initializations if needed
}