// ScreenShadeActivity.kt
package com.example.purramid.thepurramid

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import android.widget.Toast

class ScreenShadeActivity : Activity() {

    private val launchImageChooser = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Log.d("ScreenShadeActivity", "Image selected: $uri")
            sendImageUriToService(uri)
        } else {
            Log.d("ScreenShadeActivity", "No image selected")
        }
    }

    private val launchImageChooserReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenShadeService.ACTION_LAUNCH_IMAGE_CHOOSER) {
                Log.d("ScreenShadeActivity", "Received ACTION_LAUNCH_IMAGE_CHOOSER")
                val pendingIntent = intent.getParcelableExtra<android.app.PendingIntent>(ScreenShadeService.EXTRA_PENDING_INTENT)
                try {
                    pendingIntent?.send()
                } catch (e: android.app.PendingIntent.CanceledException) {
                    Log.e("ScreenShadeActivity", "PendingIntent for image chooser was cancelled", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_shade) // Inflate the layout

        Log.d("ScreenShadeActivity", "onCreate called")

        // Register BroadcastReceiver to listen for the image chooser launch request
        val filter = IntentFilter(ScreenShadeService.ACTION_LAUNCH_IMAGE_CHOOSER)
        registerReceiver(launchImageChooserReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(launchImageChooserReceiver)
    }

    private fun sendImageUriToService(uri: android.net.Uri) {
        val serviceIntent = Intent(this, ScreenShadeService::class.java).apply {
            action = ScreenShadeService.ACTION_IMAGE_SELECTED
            putExtra(ScreenShadeService.EXTRA_IMAGE_URI, uri)
        }
        startService(serviceIntent)
    }
}