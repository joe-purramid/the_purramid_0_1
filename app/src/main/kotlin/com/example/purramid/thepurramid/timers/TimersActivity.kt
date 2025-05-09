// TimersActivity.kt
package com.example.purramid.thepurramid.timers

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.timers.service.ACTION_START_STOPWATCH // Import actions
import com.example.purramid.thepurramid.timers.service.EXTRA_TIMER_ID
import com.example.purramid.thepurramid.timers.service.TimersService
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint // Add Hilt EntryPoint if it needs injections later
class TimersActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TimersActivity"
        // Simple counter for demo purposes. Replace with robust ID management.
        private val timerIdCounter = AtomicInteger(1)

        private fun getNextTimerId(): Int {
            // TODO: Implement proper ID fetching/management (e.g., query DAO for next available ID)
            return timerIdCounter.getAndIncrement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout needed if finishing immediately
        // setContentView(R.layout.activity_timers)
        Log.d(TAG, "onCreate - Launching Timers Service")

        // Generate or retrieve a unique ID for this timer instance
        // For now, just generate a new one each time the activity is launched
        val newTimerId = getNextTimerId()

        // Start the service - Default to starting a Stopwatch
        startTimerService(newTimerId)

        // Finish the activity immediately after starting the service
        finish()
    }

    private fun startTimerService(timerId: Int) {
        Log.d(TAG, "Requesting start for service instance ID: $timerId")
        val serviceIntent = Intent(this, TimersService::class.java).apply {
            // Default action - start a stopwatch
            action = ACTION_START_STOPWATCH
            putExtra(EXTRA_TIMER_ID, timerId)
            // If starting countdown, add duration:
            // action = ACTION_START_COUNTDOWN
            // putExtra(EXTRA_DURATION_MS, 60000L) // e.g., 1 minute
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

}