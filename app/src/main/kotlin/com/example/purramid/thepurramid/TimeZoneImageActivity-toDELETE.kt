// TimeZoneImageActivity.kt
package com.example.thepurramid0_1

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import java.util.TimeZone

class TimeZoneImageActivity : AppCompatActivity() {

    private lateinit var timeZoneMapView: ImageView
    private lateinit var sharedPreferences: SharedPreferences
    private var timeZoneMapBitmapWidth: Int = 0
    private var timeZoneMapBitmapHeight: Int = 0

    // Replace with your actual mapping logic
    private val timeZoneMapping = mapOf(
        // Example: Map a region (defined by approximate pixel coordinates) to a time zone ID
        Pair(0f..200f, 0f..100f) to "America/Los_Angeles",
        Pair(201f..400f, 0f..100f) to "America/Denver",
        Pair(401f..600f, 0f..100f) to "America/New_York",
        // ... add more mappings based on your image
        Pair(601f..800f, 101f..200f) to "Europe/London",
        // ... and so on
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_zone_image) // Create this layout

        sharedPreferences = getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        timeZoneMapView = findViewById(R.id.timeZoneMapView)

        // Load your custom map image
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.world_time_zones) // Replace with your image

        timeZoneMapView.setImageBitmap(bitmap)

        // Get the dimensions of the bitmap after the view is laid out
        timeZoneMapView.doOnLayout {
            timeZoneMapBitmapWidth = bitmap.width
            timeZoneMapBitmapHeight = bitmap.height
        }

        timeZoneMapView.setOnTouchListener { _, event ->
            val x = event.x
            val y = event.y

            // Normalize touch coordinates to the bitmap's dimensions
            val normalizedX = x / timeZoneMapView.width * timeZoneMapBitmapWidth
            val normalizedY = y / timeZoneMapView.height * timeZoneMapBitmapHeight

            // Iterate through your time zone mapping to find a match
            for ((region, timeZoneId) in timeZoneMapping) {
                val xRange = region.first
                val yRange = region.second

                if (normalizedX in xRange && normalizedY in yRange) {
                    // Time zone found!
                    saveTimeZone(timeZoneId)
                    finish()
                    return@setOnTouchListener true // Consume the touch event
                }
            }

            // If no time zone is found at the touch point
            // Optionally provide feedback to the user
            return@setOnTouchListener false
        }

        // Optionally load and display the currently selected time zone on the image
        loadCurrentTimeZone()
    }

    private fun saveTimeZone(timeZoneId: String) {
        with(sharedPreferences.edit()) {
            putString("time_zone_id", timeZoneId)
            apply()
        }
    }

    private fun loadCurrentTimeZone() {
        val currentTimeZoneId = sharedPreferences.getString("time_zone_id", TimeZone.getDefault().id)
        // You might want to visually indicate the selected time zone on your custom image here.
        // This could involve drawing an overlay or highlighting a region.
        // The complexity depends on how you want to represent the selection.
    }
}