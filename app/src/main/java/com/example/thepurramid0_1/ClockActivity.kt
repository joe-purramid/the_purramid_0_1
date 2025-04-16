// ClockActivity.kt
package com.example.thepurramid0_1

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class ClockActivity : AppCompatActivity() {

    private lateinit var clockView: ClockView
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resetButton: Button
    private lateinit var settingsButton: Button

    private lateinit var clockFaceSVGImageView: SVGImageView
    private lateinit var hourHandSVGImageView: SVGImageView
    private lateinit var minuteHandSVGImageView: SVGImageView
    private lateinit var secondHandSVGImageView: SVGImageView

    private var currentClockId: Int = -1

    private val overlayPermissionResultLauncher = (this as AppCompatActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _: ActivityResult ->
        if (Settings.canDrawOverlays(this@ClockActivity)) {
            (this@ClockActivity).startClockOverlayService()
        } else {
            Toast.makeText(
                this@ClockActivity,
                "Overlay permission denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionResultLauncher.launch(intent)
        } else {
            startClockOverlayService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock)

        currentClockId = intent.getIntExtra("clock_id", -1) // Example: Getting from Intent

        clockView = findViewById(R.id.clockView)
        clockFaceSVGImageView = findViewById(R.id.clockFaceImageView) as SVGImageView
        hourHandSVGImageView = findViewById(R.id.hourHandImageView) as SVGImageView
        minuteHandSVGImageView = findViewById(R.id.minuteHandImageView) as SVGImageView
        secondHandSVGImageView = findViewById(R.id.secondHandImageView) as SVGImageView
        playButton = findViewById(R.id.playButton)
        pauseButton = findViewById(R.id.pauseButton)
        resetButton = findViewById(R.id.resetButton)
        settingsButton = findViewById(R.id.settingsButton) // Get reference to your settings button

        clockView.setClockId(currentClockId) // Set the clock ID

        playButton.setOnClickListener {
            clockView.playTime()
        }
        pauseButton.setOnClickListener {
            clockView.pauseTime()
        }
        resetButton.setOnClickListener {
            clockView.resetTime()
        }
        settingsButton.setOnClickListener {
            clockView.launchSettings()
        }
        
        // Request overlay permission when the activity is created
        requestOverlayPermission()
        
        // Load initial clock mode from SharedPreferences
        val sharedPreferences = getSharedPreferences("clock_settings", MODE_PRIVATE)
        val isDigital = sharedPreferences.getString("clock_mode", "digital") == "digital"

        if (!isDigital) {
            clockView.setAnalogImageViews(clockFaceImageView, hourHandImageView, minuteHandImageView, secondHandImageView)
            clockFaceImageView?.visibility = View.VISIBLE
            hourHandImageView?.visibility = View.VISIBLE
            minuteHandImageView?.visibility = View.VISIBLE
            secondHandImageView?.visibility = View.VISIBLE
        } else {
            clockFaceImageView?.visibility = View.GONE
            hourHandImageView?.visibility = View.GONE
            minuteHandImageView?.visibility = View.GONE
            secondHandImageView?.visibility = View.GONE
        }

        // Start the background clock overlay service
        val serviceIntent = Intent(this, ClockOverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun startClockOverlayService() {
        val serviceIntent = Intent(this, ClockOverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent) // Consider making it a foreground service
    }

    override fun onResume() {
        super.onResume()
        clockView.playTime() // Ensure the clock resumes if it was paused
        clockView.updateSettings() // Apply any settings changes when the activity resumes
    }

    override fun onPause() {
        super.onPause()
        clockView.pauseTime() // Pause the clock updates when the activity is in the background
    }

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(this, ClockOverlayService::class.java)
        stopService(serviceIntent)
    }
}