// TrafficLightActivity.kt
package com.example.purramid.thepurramid.traffic_light

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color // For highlight example
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivityTrafficLightBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger

@AndroidEntryPoint
class TrafficLightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrafficLightBinding
    private val viewModel: TrafficLightViewModel by viewModels()

    private var instanceId: Int = -1

    companion object {
        private val M_INSTANCE_ID_COUNTER = AtomicInteger(0)
        const val EXTRA_INSTANCE_ID = "com.example.purramid.traffic_light.INSTANCE_ID"
        const val ACTION_SHOW_SETTINGS = "SHOW_SETTINGS"

        fun getNextInstanceId(): Int {
            // TODO: Implement proper instance ID management if needed
            // For now, just increment. Needs coordination if multiple activities start services.
            return M_INSTANCE_ID_COUNTER.getAndIncrement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrafficLightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("TrafficLightActivity", "onCreate")

        instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, -1)
        if (instanceId == -1) {
            // This path might be taken if launched directly from MainActivity
            // We need a way to manage and assign unique IDs for new instances.
            // For now, let's assume a new one is needed if not provided.
            // A better approach involves a manager or checking existing service instances.
            instanceId = getNextInstanceId() // Generate a new ID
            Log.d("TrafficLightActivity", "No Instance ID in Intent, generated new one: $instanceId")
            // Start the service for the new instance
            startTrafficLightService(instanceId)
        } else {
            Log.d("TrafficLightActivity", "Activity created/recreated for instance ID: $instanceId")
            // Service should already be running for this ID if Activity is recreated
            // Check if the intent action is to show settings immediately
            if (intent.action == ACTION_SHOW_SETTINGS) {
                showSettingsFragment()
            }
        }

        // Decide whether to finish the activity after starting the service
        // If it only launches the service, finish() is appropriate.
        // If it also hosts settings, keep it alive. Let's keep it alive for now.
        // finish()
    }

    private fun startTrafficLightService(idToStart: Int) {
        Log.d("TrafficLightActivity", "Requesting start for service instance ID: $idToStart")
        val serviceIntent = Intent(this, TrafficLightService::class.java).apply {
            action = ACTION_START_TRAFFIC_LIGHT
            putExtra(EXTRA_INSTANCE_ID, idToStart)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // Handle intent actions (like showing settings) if Activity is already running
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        if (intent?.action == ACTION_SHOW_SETTINGS) {
            Log.d("TrafficLightActivity", "onNewIntent: Received ACTION_SHOW_SETTINGS for instance $instanceId")
            showSettingsFragment()
        }
    }

    // Also check in onResume in case the activity was paused and resumes with the action
    override fun onResume() {
        super.onResume()
        if (intent?.action == ACTION_SHOW_SETTINGS) {
            Log.d("TrafficLightActivity", "onResume: Received ACTION_SHOW_SETTINGS for instance $instanceId")
            showSettingsFragment()
            // Clear the action after handling to prevent re-triggering
            intent.action = null
        }
    }

    private fun showSettingsFragment() {
        // Ensure the fragment isn't already shown
        if (supportFragmentManager.findFragmentByTag(TrafficLightSettingsFragment.TAG) == null) {
            Log.d("TrafficLightActivity", "Showing settings fragment for instance $instanceId")
            TrafficLightSettingsFragment.newInstance().show(
                supportFragmentManager, TrafficLightSettingsFragment.TAG
            )
            // Notify the ViewModel that settings are open
            viewModel.setSettingsOpen(true)
        }
    }

    private fun startTrafficLightService(idToStart: Int) {
        Log.d("TrafficLightActivity", "Requesting start for service instance ID: $idToStart")
        val serviceIntent = Intent(this, TrafficLightService::class.java).apply {
            action = ACTION_START_TRAFFIC_LIGHT
            putExtra(EXTRA_INSTANCE_ID, idToStart)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // Handle intent actions (like showing settings) if Activity is already running
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        if (intent?.action == ACTION_SHOW_SETTINGS) {
            Log.d("TrafficLightActivity", "onNewIntent: Received ACTION_SHOW_SETTINGS for instance $instanceId")
            showSettingsFragment()
        }
    }

    // Also check in onResume in case the activity was paused and resumes with the action
    override fun onResume() {
        super.onResume()
        if (intent?.action == ACTION_SHOW_SETTINGS) {
            Log.d("TrafficLightActivity", "onResume: Received ACTION_SHOW_SETTINGS for instance $instanceId")
            showSettingsFragment()
            // Clear the action after handling to prevent re-triggering
            intent.action = null
        }
    }

    private fun showSettingsFragment() {
        // Ensure the fragment isn't already shown
        if (supportFragmentManager.findFragmentByTag(TrafficLightSettingsFragment.TAG) == null) {
            Log.d("TrafficLightActivity", "Showing settings fragment for instance $instanceId")
            TrafficLightSettingsFragment.newInstance().show(
                supportFragmentManager, TrafficLightSettingsFragment.TAG
            )
            // Notify the ViewModel that settings are open
            viewModel.setSettingsOpen(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TrafficLightActivity", "onDestroy for instance ID: $instanceId")
        // Note: Don't stop the service here unless this activity *always* means the overlay should close.
    }
}