// TrafficLightActivity.kt
package com.example.purramid.thepurramid.traffic_light

import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.R.attr.data
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivityTrafficLightBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel // For KEY_INSTANCE_ID
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrafficLightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrafficLightBinding

    companion object {
        private const val TAG = "TrafficLightActivity"
        const val ACTION_SHOW_SETTINGS = "com.example.purramid.traffic_light.ACTION_SHOW_SETTINGS"
        // Use constants from Service for SharedPreferences
        const val PREFS_NAME = TrafficLightService.PREFS_NAME_FOR_ACTIVITY
        const val KEY_ACTIVE_COUNT = TrafficLightService.KEY_ACTIVE_COUNT_FOR_ACTIVITY
        const val EXTRA_INSTANCE_ID = "extra_traffic_light_instance_id"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notify service
            notifyServiceMicrophonePermissionGranted()
        } else {
            // Permission denied, show explanation
            showMicrophonePermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrafficLightBinding.inflate(layoutInflater)
        setContentView(binding.root) // Must contain R.id.traffic_light_fragment_container
        Log.d(TAG, "onCreate - Intent Action: ${intent.action}")

        val instanceIdForFragment = intent.getIntExtra(EXTRA_INSTANCE_ID, 0)

        if (intent.action == ACTION_SHOW_SETTINGS) {
            showSettingsFragment(instanceIdForFragment) // Pass 0 if general, or specific ID
        } else {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeCount = prefs.getInt(KEY_ACTIVE_COUNT, 0)

            if (activeCount > 0) {
                Log.d(TAG, "Traffic Lights active ($activeCount), launching settings fragment.")
                showSettingsFragment(0) // Show general settings
            } else {
                Log.d(TAG, "No active Traffic Lights, requesting service to add a new one.")
                val serviceIntent = Intent(this, TrafficLightService::class.java).apply {
                    action = ACTION_ADD_NEW_TRAFFIC_LIGHT_INSTANCE
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                finish()
            }
        }

        // Check if coming from service to request permission
        if (intent.action == "REQUEST_MICROPHONE_PERMISSION") {
            requestMicrophonePermission()
        }
    }

    private fun requestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                notifyServiceMicrophonePermissionGranted()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show explanation before requesting
                MaterialAlertDialogBuilder(this)
                    .setTitle("Microphone Permission Required")
                    .setMessage("The Traffic Light needs microphone access for Responsive mode to detect sound levels in the classroom.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        finish()
                    }
                    .show()
            }
            else -> {
                // Request permission directly
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun notifyServiceMicrophonePermissionGranted() {
        val intent = Intent(this, TrafficLightService::class.java).apply {
            action = "MICROPHONE_PERMISSION_GRANTED"
        }
        startService(intent)
        finish()
    }

    private fun showMicrophonePermissionRationale() {
        Snackbar.make(
            binding.root,
            "Microphone permission is required for Responsive mode",
            Snackbar.LENGTH_LONG
        ).setAction("Settings") {
            // Open app settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }.show()
    }

    private fun showSettingsFragment(instanceId: Int) {
        if (supportFragmentManager.findFragmentByTag(TrafficLightSettingsFragment.TAG) == null) {
            Log.d(TAG, "Showing Traffic Light settings fragment for instance (or general): $instanceId")
            val fragment = TrafficLightSettingsFragment.newInstance().apply {
                // If your settings fragment needs to be aware of a specific instance ID
                // arguments = Bundle().apply { putInt(TrafficLightViewModel.KEY_INSTANCE_ID, instanceId) }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.traffic_light_fragment_container, fragment, TrafficLightSettingsFragment.TAG)
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent - Action: ${intent?.action}")
        if (intent?.action == ACTION_SHOW_SETTINGS) {
            val instanceIdForFragment = intent.getIntExtra(EXTRA_INSTANCE_ID, 0)
            showSettingsFragment(instanceIdForFragment)
        }
    }

    override fun onPause() {
        super.onPause()
        // Notify service that settings are closing
        notifyServiceSettingsClosed()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure notification even if onPause was skipped
        notifyServiceSettingsClosed()
    }

    private fun notifyServiceSettingsClosed() {
        // Use a broadcast or bind to service to notify
        val intent = Intent(this, TrafficLightService::class.java).apply {
            action = "SETTINGS_CLOSED"
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying service of settings closure", e)
        }
    }
}